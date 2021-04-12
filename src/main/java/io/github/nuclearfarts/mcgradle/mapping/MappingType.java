package io.github.nuclearfarts.mcgradle.mapping;

import java.io.IOException;
import java.nio.file.Files;
import java.util.function.BiFunction;
import io.github.nuclearfarts.mcgradle.McPluginInstance;
import net.fabricmc.mapping.tree.TinyMappingFactory;
import net.fabricmc.mapping.tree.TinyTree;

public enum MappingType {
	MOJMAP((intermediary, data) -> {
		throw new UnsupportedOperationException("mojmap is not yet implemented");
	}),
	// note: it is recommended to special-case yarn to reduce the number of FS ops.
	YARN((intermediary, data) -> {
		try {
			// Yarn is pre-merged.
			return TinyMappingFactory.loadWithDetection(Files.newBufferedReader(data.resolveYarn().toPath()));
		} catch (IOException e) {
			throw new RuntimeException("exception loading yarn", e);
		}
	});
	private final BiFunction<TinyTree, McPluginInstance, TinyTree> mappingsLoader;
	
	private MappingType(BiFunction<TinyTree, McPluginInstance, TinyTree> mappingsLoader) {
		this.mappingsLoader = mappingsLoader;
	}
}
