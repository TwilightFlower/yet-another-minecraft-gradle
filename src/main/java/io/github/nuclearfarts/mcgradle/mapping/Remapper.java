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

import io.github.nuclearfarts.mcgradle.util.DirectoryCopyFileVisitor;
import io.github.nuclearfarts.mcgradle.util.Util;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.TinyRemapper;

public class Remapper {
	private static final Map<String, TinyTree> CACHE = new HashMap<>();
	private final YarnProvider data;
	
	public Remapper(YarnProvider instance) {
		data = instance;
	}
	
	public void remapToDev(String from, Path inJar, Path outJar) {
		TinyTree mappings = CACHE.computeIfAbsent(data.getYarnVersion(), ver -> {
			try(FileSystem fs = FileSystems.newFileSystem(data.resolveYarn().toPath(), getClass().getClassLoader())) {
				return TinyMappingFactory.loadWithDetection(Files.newBufferedReader(fs.getPath("mappings", "mappings.tiny")));
			} catch (IOException e) {
				throw new RuntimeException("error loading yarn", e);
			}
		});
		IMappingProvider provider = Util.create(mappings, from, "named", true);
		remap(inJar, outJar, provider);
	}
	
	public void remap(String from, String to, Path inJar, Path outJar) {
		TinyTree mappings = CACHE.computeIfAbsent(data.getYarnVersion(), ver -> {
			try(FileSystem fs = FileSystems.newFileSystem(data.resolveYarn().toPath(), getClass().getClassLoader())) {
				return TinyMappingFactory.loadWithDetection(Files.newBufferedReader(fs.getPath("mappings", "mappings.tiny")));
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException("error loading yarn", e);
			}
		});
		IMappingProvider provider = Util.create(mappings, from, to, true);
		remap(inJar, outJar, provider);
	}
	
	private void remap(Path inJar, Path outJar, IMappingProvider provider) {
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
			
			try(FileSystem inFs = FileSystems.newFileSystem(inJar, getClass().getClassLoader())) {
				Path in = inFs.getPath("/");
				Path out = outFs.getPath("/");
				Files.walkFileTree(in, new DirectoryCopyFileVisitor(in, out, p -> !p.toString().endsWith(".class")));
			}
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("remapping error", e);
		}
	}
	
	public interface YarnProvider {
		String getYarnVersion();
		File resolveYarn();
	}
}
