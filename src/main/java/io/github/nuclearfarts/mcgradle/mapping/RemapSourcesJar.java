package io.github.nuclearfarts.mcgradle.mapping;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.ConfigurationContainer;

import io.github.nuclearfarts.mcgradle.McPluginExtension;
import io.github.nuclearfarts.mcgradle.util.Pair;

public class RemapSourcesJar extends DefaultTask {
	// files: dev, nonDev
	private final List<Pair<File, File>> jarsToRemap = new ArrayList<>();
	
	public RemapSourcesJar() {
		doLast(t -> {
			MappingStore m = getProject().getExtensions().getByType(McPluginExtension.class).getPluginDataInternal().getUsedMappings();
			for(Pair<File, File> p : jarsToRemap) {
				doRemap(m.devToIntermediary, p.left, p.right);
			}
		});
	}
	
	public RemapSourcesJar remap(File dev, File prod) {
		jarsToRemap.add(new Pair<>(dev, prod));
		getOutputs().file(prod);
		getInputs().file(dev);
		return this;
	}
	
	private void doRemap(MappingKey.Loaded m, File dev, File prod) {
		Path devJar = dev.toPath();
		Path prodJar = prod.toPath();
		
		ConfigurationContainer cfgs = getProject().getConfigurations();
		Set<File> context = cfgs.getByName("compileClasspath").resolve();
		
		Path out = Remapper.remapSource(m, devJar, context);
		try {
			Files.move(out, prodJar, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new RuntimeException("error remapping built jar", e);
		}
		//Remapper.remapWithModContext(m, devJar, prodJar, getProject().getConfigurations().getByName("mod_internal_mapped").resolve());
	}
}
