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

import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.mercury.Mercury;
import org.cadixdev.mercury.remapper.MercuryRemapper;

import com.google.common.collect.ImmutableMap;

import io.github.nuclearfarts.mcgradle.util.DirectoryCopyFileVisitor;
import io.github.nuclearfarts.mcgradle.util.Util;
import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.IMappingProvider.Member;
import net.fabricmc.tinyremapper.TinyRemapper;

public class Remapper {
	private static final Map<MappingKey.Loaded, MappingSet> LORENZ_CACHE = new HashMap<>();
	private static final Map<String, ?> FS_ENV = ImmutableMap.of("create", true);
	
	public static Path remapSource(MappingKey.Loaded mappings, Path inJar, Set<File> context) {
		String mappedName = inJar.getFileName().toString().replace(".jar", String.format("-mapped-%s-%s.jar", mappings.target, mappings.mc));
		Path out = inJar.resolveSibling(mappedName);
		try {
			Files.deleteIfExists(out);
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		if(Files.notExists(out)) {
			Path tmp;
			try {
				tmp = Files.createTempDirectory("src-tmp-" + inJar.getFileName());
			} catch (IOException e1) {
				throw new RuntimeException("could not create temp dir", e1);
			}
			Util.extract(inJar, tmp);
			
			Mercury m = new Mercury();
			for(File f : context) {
				m.getClassPath().add(f.toPath());
			}
			m.getProcessors().add(MercuryRemapper.create(resolveLorenz(mappings)));
			try(FileSystem outFs = FileSystems.newFileSystem(Util.jarFsUri(out), FS_ENV, Remapper.class.getClassLoader())) {
				Path outRoot = outFs.getPath("/");
				m.rewrite(tmp, outRoot);
			} catch (Exception e) {
				throw new RuntimeException("Error remapping source from " + inJar, e);
			}
		}
		return out;
	}
	
	private static MappingSet resolveLorenz(MappingKey.Loaded mappings) {
		return LORENZ_CACHE.computeIfAbsent(mappings, key -> {
			Lorenzifier lz = new Lorenzifier();
			key.get().load(lz);
			return lz.mappings;
		});
	}
	
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
	
	private static class Lorenzifier implements IMappingProvider.MappingAcceptor {
		final MappingSet mappings = MappingSet.create();
		
		@Override
		public void acceptClass(String srcName, String dstName) {
			mappings.getOrCreateClassMapping(srcName).setDeobfuscatedName(dstName);
		}
		
		@Override
		public void acceptMethod(Member method, String dstName) {
			mappings.getOrCreateClassMapping(method.owner).getOrCreateMethodMapping(method.name, method.desc).setDeobfuscatedName(dstName);
		}
		
		@Override
		public void acceptMethodArg(Member method, int lvIndex, String dstName) {
			mappings.getOrCreateClassMapping(method.owner).getOrCreateMethodMapping(method.name, method.desc).getOrCreateParameterMapping(lvIndex).setDeobfuscatedName(dstName);
		}
		
		@Override
		public void acceptMethodVar(Member method, int lvIndex, int startOpIdx, int asmIndex, String dstName) {
			// noop, lorenz does not support local vars
		}
		
		@Override
		public void acceptField(Member field, String dstName) {
			mappings.getOrCreateClassMapping(field.owner).getOrCreateFieldMapping(field.name, field.desc).setDeobfuscatedName(dstName);
		}
	}
}
