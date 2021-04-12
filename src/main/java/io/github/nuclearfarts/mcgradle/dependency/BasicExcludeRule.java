package io.github.nuclearfarts.mcgradle.dependency;

import java.util.Map;
import java.util.Objects;

import org.gradle.api.artifacts.ExcludeRule;

public final class BasicExcludeRule implements ExcludeRule {
	private final String group, module;
	public BasicExcludeRule(String group, String module) {
		this.group = group;
		this.module = module;
	}
	
	public BasicExcludeRule(Map<String, String> map) {
		group = map.get(GROUP_KEY);
		module = map.get(MODULE_KEY);
	}
	
	@Override
	public String getGroup() {
		return group;
	}

	@Override
	public String getModule() {
		return module;
	}
	
	@Override
	public int hashCode() {
		return group.hashCode() ^ module.hashCode();
	}
	
	@Override
	public boolean equals(Object other) {
		if(!(other instanceof ExcludeRule)) return false;
		ExcludeRule er = (ExcludeRule) other;
		return Objects.equals(er.getGroup(), group) && Objects.equals(er.getModule(), module);
	}
}
