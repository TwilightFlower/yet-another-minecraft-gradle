package io.github.nuclearfarts.mcgradle.dependency;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.FileCollectionDependency;
import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.artifacts.dependencies.SelfResolvingDependencyInternal;
import org.gradle.api.tasks.TaskDependency;

import io.github.nuclearfarts.mcgradle.McPluginInstance;

// the internal version is necessary or it crashes
public abstract class MinecraftDependency implements SelfResolvingDependencyInternal, FileCollectionDependency {
	private final Project project;
	private final McPluginInstance data;
	private Set<File> files;

	public MinecraftDependency(Project project, McPluginInstance data) {
		this.project = project;
		this.data = data;
	}
	
	@Override
	public void because(String arg0) { }

	@Override
	public boolean contentEquals(Dependency arg0) {
		return this == arg0;
	}

	@Override
	public Dependency copy() {
		return this;
	}

	@Override
	public String getGroup() {
		return "com.mojang";
	}

	@Override
	public String getName() {
		return "minecraft";
	}

	@Override
	public String getReason() {
		return null;
	}

	@Override
	public Set<File> resolve() {
		return resolve(true);
	}

	@Override
	public Set<File> resolve(boolean arg0) {
		if(files == null) {
			files = new HashSet<>();
			files.add(data.getMappedMinecraft());
			data.loadLibs();
			project.getConfigurations().getByName("mcDeps").getResolvedConfiguration(); // force resolution
		}
		
		return files;
	}

	@Override
	public FileCollection getFiles() {
		return project.files(resolve().toArray());
	}

	@Override
	public TaskDependency getBuildDependencies() {
		return t -> Collections.emptySet();
	}

	@Override
	public ComponentIdentifier getTargetComponentId() {
		return new ComponentIdentifierImpl();
	}
}
