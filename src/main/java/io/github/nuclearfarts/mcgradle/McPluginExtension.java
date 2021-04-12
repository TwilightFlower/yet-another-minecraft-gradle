package io.github.nuclearfarts.mcgradle;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import io.github.nuclearfarts.mcgradle.dependency.MinecraftDependency;
import io.github.nuclearfarts.mcgradle.mapping.MappingType;

public class McPluginExtension extends MinecraftDependency {
	public boolean useMojmap = false; // not yet implemented
	public String version;
	public Configuration libraries;
	McPluginInstance data;
	
	public McPluginExtension(Project project, McPluginInstance data) {
		super(project, data);
	}
	
	public McPluginInstance getPluginDataInternal() {
		return data;
	}
	
	public MappingType getMappingsType() {
		return useMojmap ? MappingType.MOJMAP : MappingType.YARN;
	}

	@Override
	public String getVersion() {
		return version;
	}
}
