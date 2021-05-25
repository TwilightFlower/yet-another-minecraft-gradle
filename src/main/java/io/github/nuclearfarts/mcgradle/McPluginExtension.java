package io.github.nuclearfarts.mcgradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;

import io.github.nuclearfarts.mcgradle.dependency.MinecraftDependency;

public class McPluginExtension extends MinecraftDependency {
	public boolean useMojmap = false; // not yet implemented
	public String version;
	Configuration libraries;
	McPluginInstance data;
	
	public McPluginExtension(Project project, McPluginInstance data) {
		super(project, data);
	}
	
	public McPluginInstance getPluginDataInternal() {
		return data;
	}
	
	public Configuration getLibraries() {
		return libraries;
	}
	
	@Override
	public String getVersion() {
		return version;
	}
	
	public Dependency mod(Object dep) {
		data.project.getDependencies().add("mod_internal_unmapped", dep);
		return data.project.getDependencies().add("mod_internal_mapped", dep);
	}
}
