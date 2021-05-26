package io.github.nuclearfarts.mcgradle.mapping;

import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.artifacts.ResolvedConfiguration;
import org.gradle.api.artifacts.ResolvedDependency;

import io.github.nuclearfarts.mcgradle.McGradlePlugin;
import io.github.nuclearfarts.mcgradle.McPluginInstance;
import io.github.nuclearfarts.mcgradle.util.Pair;

public class ModDependencyExtension {
	private AtomicInteger counter = new AtomicInteger(0);
	private final McPluginInstance data;
	
	public ModDependencyExtension(McPluginInstance data) {
		this.data = data;
	}
	
	public Object mod(Object dep) {
		data.project.getDependencies().add("mod_internal_unmapped", dep);
		data.project.getDependencies().add("mod_internal_mapped", dep);
		
		int c = counter.getAndIncrement();
		String cfgN = "__mod_internal_hack_" + c;
		String cfgUnmappedN = "__mod_internal_hack_unmapped_" + c;
		Configuration hackCfg = data.project.getConfigurations().create(cfgN);
		Configuration unmappedHackCfg = data.project.getConfigurations().create(cfgUnmappedN);
		
		data.project.getDependencies().add(cfgUnmappedN, dep);
		data.project.getDependencies().add(cfgN, dep);
		
		hackCfg.getAttributes().attribute(McGradlePlugin.REMAP, false);
		
		ResolvedConfiguration resolved = unmappedHackCfg.getResolvedConfiguration();
		Set<ResolvedArtifact> moduleArtifacts = new HashSet<>();
		for(ResolvedDependency d : resolved.getFirstLevelModuleDependencies()) {
			moduleArtifacts.addAll(d.getAllModuleArtifacts());
		}
		
		for(ResolvedArtifact module : moduleArtifacts) {
			ModuleVersionIdentifier id = module.getModuleVersion().getId();
			String moduleString = id.getGroup() + ":" + id.getName() + ":" + id.getVersion();
			String classifier = module.getClassifier();
			if(classifier != null && classifier.isEmpty()) {
				classifier = null;
			}
			
			if(classifier != null) {
				moduleString += ":" + classifier;
			}
			moduleString += ":sources";
			Configuration resolveSrc = data.project.getConfigurations().create("__mod_internal_sources_" + counter.getAndIncrement());
			data.project.getDependencies().add(resolveSrc.getName(), moduleString);
			ResolvedConfiguration resolvedSrc = resolveSrc.getResolvedConfiguration();
			ResolvedDependency src = resolvedSrc.getFirstLevelModuleDependencies().iterator().next();
			File f = src.getModuleArtifacts().iterator().next().getFile();
			
			data.inputSourceProviders.add(new Pair<Function<MappingKey.Loaded, String>, File>(mappings -> {
				return module.getFile().getName().replace(".jar", "-mapped-" + mappings.target + ".jar");
			}, f));
		}
		return data.project.files(hackCfg);
	}
}
