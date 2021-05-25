package io.github.nuclearfarts.mcgradle.mapping.loader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import io.github.nuclearfarts.mcgradle.mapping.MappingKey;
import io.github.nuclearfarts.mcgradle.util.Util;
import net.fabricmc.mapping.reader.v2.TinyMetadata;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.IMappingProvider;

public abstract class TinyMappingLoader implements MappingLoader {
	private TinyTree tinyTree;
	private final Set<MappingKey> provides = new HashSet<>();
	
	protected abstract TinyTree loadMappings(String mcVersion);
	protected abstract String transformNamespace(String namespace);
	protected abstract boolean allowNamespace(String namespace);
	
	@Override
	public Set<MappingKey> provides(String mcVersion) {
		ensureLoaded(mcVersion);
		return provides;
	}
	
	@Override
	public IMappingProvider load(MappingKey key, String mcVersion) {
		ensureLoaded(mcVersion);
		return Util.create(tinyTree, key.src, key.target, true);
	}
	
	private void ensureLoaded(String mcVersion) {
		if(tinyTree == null) {
			tinyTree = loadMappings(mcVersion);
			if(tinyTree != null) {
				initPermutations();
			}
		}
	}
	
	private void initPermutations() {
		TinyMetadata meta = tinyTree.getMetadata();
		for(String src : meta.getNamespaces()) {
			if(allowNamespace(src)) {
				src = transformNamespace(src);
				for(String dst : meta.getNamespaces()) {
					if(allowNamespace(dst)) {
						dst = transformNamespace(dst);
						if(!src.equals(dst)) {
							provides.add(new MappingKey(src, dst));
						}
					}
				}
			}
		}
	}
	
	protected TinyTree loadMappingsMaybeFromJar(File from) {
		if(from.getName().endsWith(".jar")) {
			try(FileSystem zipFs = FileSystems.newFileSystem(from.toPath(), getClass().getClassLoader())) {
				Path p = zipFs.getPath("mappings", "mappings.tiny");
				try(BufferedReader r = Files.newBufferedReader(p)) {
					return TinyMappingFactory.loadWithDetection(r);
				}
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			try(BufferedReader r = Files.newBufferedReader(from.toPath())) {
				return TinyMappingFactory.loadWithDetection(r);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}
}
