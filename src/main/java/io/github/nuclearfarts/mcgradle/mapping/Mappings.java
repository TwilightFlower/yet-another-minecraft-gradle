package io.github.nuclearfarts.mcgradle.mapping;

import java.util.HashMap;
import java.util.Map;

import io.github.nuclearfarts.mcgradle.McPluginExtension;
import io.github.nuclearfarts.mcgradle.mapping.loader.MappingLoader;
import net.fabricmc.tinyremapper.IMappingProvider;

public class Mappings {
	static final Map<MappingKey.Loaded, IMappingProvider> CACHE = new HashMap<>();
	private final MappingsExtension ext;
	private final McPluginExtension mcExt;
	
	public Mappings(MappingsExtension ext, McPluginExtension mcExt) {
		this.ext = ext;
		this.mcExt = mcExt;
	}
	
	public MappingStore loadMain() {
		MappingKey.Loaded o2i = load(new MappingKey(ext.obf, ext.interm));
		MappingKey.Loaded i2n = load(new MappingKey(ext.interm, ext.named));
		MappingKey.Loaded n2i = load(new MappingKey(ext.named, ext.interm));
		return new MappingStore(o2i, i2n, n2i);
	}
	
	public MappingKey.Loaded load(MappingKey key) {
		MappingKey.Loaded loadedKey = new MappingKey.Loaded(key.src, key.target, mcExt.getVersion());
		IMappingProvider p = CACHE.computeIfAbsent(loadedKey, k -> loadIt(k, key));
		return p != null ? loadedKey : null;
	}
	
	private IMappingProvider loadIt(MappingKey.Loaded key, MappingKey unversioned) {
		for(MappingLoader loader : ext.loaders) {
			if(loader.provides(key.mc).contains(unversioned)) {
				return loader.load(unversioned, key.mc);
			}
		}
		return null;
	}
}
