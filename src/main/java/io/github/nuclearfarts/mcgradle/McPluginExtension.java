package io.github.nuclearfarts.mcgradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import io.github.nuclearfarts.mcgradle.dependency.MinecraftDependency;

public class McPluginExtension extends MinecraftDependency {
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
}
