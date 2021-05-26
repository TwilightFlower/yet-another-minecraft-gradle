package io.github.nuclearfarts.mcgradle.mapping;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;

import io.github.nuclearfarts.mcgradle.mapping.loader.IntermediaryMappingLoader;
import io.github.nuclearfarts.mcgradle.mapping.loader.MappingLoader;
import io.github.nuclearfarts.mcgradle.mapping.loader.YarnLoader;
import io.github.nuclearfarts.mcgradle.util.Pair;

public class MappingsExtension {
	public String obf = "official";
	public String interm = "intermediary";
	public String named;
	private final Project proj;
	private int cfgId = 0;
	List<MappingLoader> loaders = new ArrayList<>();
	
	public MappingsExtension(Project proj) {
		this.proj = proj;
	}
	
	public void yarn(Action<? super Yarn> act) {
		Yarn y = new Yarn();
		act.execute(y);
		if(y.resolve == null) {
			throw new RuntimeException("Yarn mappings require an artifact source");
		}
		Pair<String, File> res = loadDep(y.resolve);
		String ver = y.version != null ? y.version : res.left;
		YarnLoader yl = new YarnLoader(res.right, ver);
		loaders.add(yl);
		named = "yarn-" + ver;
	}
	
	public void yarn(Object dep) {
		Pair<String, File> res = loadDep(dep);
		YarnLoader yl = new YarnLoader(res.right, res.left);
		loaders.add(yl);
		named = "yarn-" + res.left;
	}
	
	public void intermediary() {
		loaders.add(new IntermediaryMappingLoader(proj));
	}
	
	private Pair<String, File> loadDep(Object depO) {
		String cfgN = "__mappings_extension_internal_" + cfgId++;
		Configuration cfg = proj.getConfigurations().create(cfgN);
		Dependency dep = proj.getDependencies().add(cfgN, depO);
		return new Pair<>(dep.getVersion(), cfg.getSingleFile());
	}
	
	public class Yarn {
		public Object resolve;
		public String version;
	}
}
