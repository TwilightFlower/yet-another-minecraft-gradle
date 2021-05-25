package io.github.nuclearfarts.mcgradle.mapping.loader;

import java.util.Set;

import io.github.nuclearfarts.mcgradle.mapping.MappingKey;
import net.fabricmc.tinyremapper.IMappingProvider;

public interface MappingLoader {
	Set<MappingKey> provides(String mcVersion);
	IMappingProvider load(MappingKey key, String mcVersion);
}
