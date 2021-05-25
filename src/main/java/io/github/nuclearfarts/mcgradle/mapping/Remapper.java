package io.github.nuclearfarts.mcgradle.mapping;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.gradle.api.artifacts.Configuration;

import io.github.nuclearfarts.mcgradle.util.DirectoryCopyFileVisitor;
import io.github.nuclearfarts.mcgradle.util.Util;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyRemapper;

public class Remapper {
	public static void remap(MappingKey.Loaded mappings, Path inJar, Path outJar) {
		IMappingProvider provider = mappings.get();
		remap(inJar, outJar, provider);
	}
	
	public static void remapWithModContext(MappingKey.Loaded mappings, Path inJar, Path outJar, Set<File> classpath) {
		IMappingProvider provider = mappings.get();
		remap(inJar, outJar, provider, classpath);
	}
	
	private static void remap(Path inJar, Path outJar, IMappingProvider provider, Set<File> classpath) {
		TinyRemapper remapper = TinyRemapper.newRemapper().withMappings(provider).build();
		remapper.readInputs(inJar);
		for(File f : classpath) {
			remapper.readClassPath(f.toPath());
		}
		
		try(FileSystem outFs = FileSystems.newFileSystem(Util.jarFsUri(outJar), Util.FS_ENV)) {
			remapper.apply((path, clazz) -> {
				Path classOut = outFs.getPath(path + ".class");
				try {
					Files.createDirectories(classOut.getParent());
					Files.write(classOut, clazz, StandardOpenOption.CREATE);
				} catch (IOException e) {
					e.printStackTrace();
					throw new RuntimeException("error writing remapped class", e);
				}
			});
			remapper.finish();
			
			try(FileSystem inFs = FileSystems.newFileSystem(inJar, Remapper.class.getClassLoader())) {
				Path in = inFs.getPath("/");
				Path out = outFs.getPath("/");
				Files.walkFileTree(in, new DirectoryCopyFileVisitor(in, out, p -> !p.toString().endsWith(".class")));
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("remapping error", e);
		}
	}
	
	private static void remap(Path inJar, Path outJar, IMappingProvider provider) {
		TinyRemapper remapper = TinyRemapper.newRemapper().rebuildSourceFilenames(true).withMappings(provider).build();
		remapper.readInputs(inJar);
		
		try(FileSystem outFs = FileSystems.newFileSystem(Util.jarFsUri(outJar), Util.FS_ENV)) {
			remapper.apply((path, clazz) -> {
				Path classOut = outFs.getPath(path + ".class");
				try {
					Files.createDirectories(classOut.getParent());
					Files.write(classOut, clazz, StandardOpenOption.CREATE);
				} catch (IOException e) {
					e.printStackTrace();
					throw new RuntimeException("error writing remapped class", e);
				}
			});
			remapper.finish();
			
			try(FileSystem inFs = FileSystems.newFileSystem(inJar, Remapper.class.getClassLoader())) {
				Path in = inFs.getPath("/");
				Path out = outFs.getPath("/");
				Files.walkFileTree(in, new DirectoryCopyFileVisitor(in, out, p -> !p.toString().endsWith(".class")));
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("remapping error", e);
		}
	}
}
