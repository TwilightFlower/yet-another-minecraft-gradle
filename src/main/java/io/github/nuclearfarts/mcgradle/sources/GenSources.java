package io.github.nuclearfarts.mcgradle.sources;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskAction;
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import com.google.common.collect.ImmutableMap;

import io.github.nuclearfarts.mcgradle.McPluginExtension;
import io.github.nuclearfarts.mcgradle.McPluginInstance;
import io.github.nuclearfarts.mcgradle.util.Util;

public class GenSources extends DefaultTask {
	// why is this explicit generic necessary
	private static final ImmutableMap<String, Object> OPTIONS = ImmutableMap.<String, Object>builder()
			.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1")
			.put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1")
			.put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1")
			.put(IFernflowerPreferences.THREADS, "8")
			.build();
	
	@TaskAction
	public void genSrc() {
		Project proj = getProject();
		McPluginInstance data = proj.getExtensions().getByType(McPluginExtension.class).getPluginDataInternal();
		File minecraft = data.getMinecraft();
		data.loadLibs();
		Map<String, int[]> lmaps = new ConcurrentHashMap<>();
		Path cacheDir = data.cacheRoot.resolve("minecraft").resolve(data.ext.version);
		Path ffWork = cacheDir.resolve("ff-work");
		try(FFFileOps provider = new FFFileOps(lmaps::put, ffWork)) {
			BaseDecompiler decompiler = new BaseDecompiler(provider, provider, OPTIONS, new PrintStreamLogger(new PrintStream(new File("decomp-output.txt"))));
			for(File lib : data.mcDeps.resolve()) {
				//System.out.println(lib);
				if(!lib.toString().contains("lwjgl")) {
					decompiler.addLibrary(lib); // ??? doesn't like lwjgl for some reason???
				}
			}
			decompiler.addSource(minecraft);
			decompiler.decompileContext();
			provider.await(); // awful hack.
		} catch (IOException | InterruptedException e) {
			throw new RuntimeException("error decompiling", e);
		}
		Path mcJar = minecraft.toPath();
		Path mcLmap = cacheDir.resolve(mcJar.getFileName().toString().replace(".jar", "-lmap.jar"));
		Path mcSrc = cacheDir.resolve(mcJar.getFileName().toString().replace(".jar", "-lmap-src.jar"));
		try {
			Files.copy(ffWork.resolve(mcJar.getFileName()), mcSrc, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new RuntimeException("error copying decompiler output", e);
		}
		//System.out.println(lmaps.keySet());
		try(FileSystem noLmapFs = FileSystems.newFileSystem(minecraft.toPath(), getClass().getClassLoader())) {
			try(FileSystem lmapFs = FileSystems.newFileSystem(Util.jarFsUri(mcLmap), Util.FS_ENV, getClass().getClassLoader())) {
				Path noLmapRoot = noLmapFs.getPath("/").toAbsolutePath();
				Path lmapRoot = lmapFs.getPath("/");
				Files.walk(noLmapRoot).forEach(p -> {
					if(Files.isRegularFile(p)) {
						Path relPath = noLmapRoot.relativize(p.toAbsolutePath());
						Path dstPath = lmapRoot.resolve(relPath);
						try {
							String binaryName = relPath.toString().substring(0, relPath.toString().length() - 6);
							if(p.toString().endsWith(".class") && lmaps.containsKey(binaryName)) {
								try(InputStream inStream = new BufferedInputStream(Files.newInputStream(p))) {
									ClassReader reader = new ClassReader(inStream);
									ClassWriter writer = new ClassWriter(0);
									ClassVisitor vis = new LinemapperTransformer(writer, lmaps.get(binaryName));
									reader.accept(vis, 0);
									byte[] clazz = writer.toByteArray();
									Files.createDirectories(dstPath.getParent());
									try(OutputStream outStream = Files.newOutputStream(dstPath, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
										outStream.write(clazz);
									}
								}
							} else {
								Files.createDirectories(dstPath.getParent());
								Files.copy(p, dstPath, StandardCopyOption.REPLACE_EXISTING);
							}
						} catch(IOException e) {
							throw new Util.LambdasSuckException(e);
						}
					}
				});
			}
		} catch (IOException e) {
			throw new RuntimeException("error linemapping", e);
		} catch (Util.LambdasSuckException e) {
			throw new RuntimeException("error linemapping", e.exc);
		}
	}
}
