package io.github.nuclearfarts.mcgradle.mapping.loader;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import net.fabricmc.mapping.tree.TinyTree;

public class IntermediaryMappingLoader extends TinyMappingLoader {
	private final Project proj;
	
	public IntermediaryMappingLoader(Project proj) {
		this.proj = proj;
	}
	
	@Override
	protected TinyTree loadMappings(String mcVersion) {
		Configuration cfg = proj.getConfigurations().create("_intermediary");
		proj.getDependencies().add("_intermediary", "org.quiltmc:intermediary:" + mcVersion);
		return loadMappingsMaybeFromJar(cfg.getSingleFile());
	}
}
