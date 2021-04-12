package io.github.nuclearfarts.mcgradle.dependency;

import org.gradle.api.artifacts.component.ComponentIdentifier;

public class ComponentIdentifierImpl implements ComponentIdentifier {

	@Override
	public String getDisplayName() {
		return "Minecraft";
	}

}
