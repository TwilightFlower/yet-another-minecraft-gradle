package io.github.nuclearfarts.mcgradle.mapping.loader;

import java.io.File;
import net.fabricmc.mapping.tree.TinyTree;

public class YarnLoader extends TinyMappingLoader {
	private final File from;
	private final String yarnVer;
	
	public YarnLoader(File from, String yarnVer) {
		this.from = from;
		this.yarnVer = yarnVer;
	}
	
	@Override
	protected TinyTree loadMappings(String mcVersion) {
		return loadMappingsMaybeFromJar(from);
	}

	@Override
	protected String transformNamespace(String namespace) {
		if("named".equals(namespace)) {
			return "yarn-" + yarnVer;
		}
		return namespace;
	}
	
	@Override
	protected String detransformNamespace(String transformed) {
		if(("yarn-" + yarnVer).equals(transformed)) {
			return "named";
		}
		return transformed;
	}

	@Override
	protected boolean allowNamespace(String namespace) {
		return !"official".equals(namespace);
	}
}
