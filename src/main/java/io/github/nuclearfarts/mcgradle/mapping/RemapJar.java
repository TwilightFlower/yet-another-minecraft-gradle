package io.github.nuclearfarts.mcgradle.mapping;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.DefaultTask;
import io.github.nuclearfarts.mcgradle.McPluginExtension;

public class RemapJar extends DefaultTask {
	private final List<File> jarsToRemap = new ArrayList<>();
	
	public RemapJar() {
		doLast(t -> {
			MappingStore m = getProject().getExtensions().getByType(McPluginExtension.class).getPluginDataInternal().getUsedMappings();
			for(File f : jarsToRemap) {
				if(f.isFile()) {
					doRemap(m.devToIntermediary, f);
				} else if(f.isDirectory()) {
					for(File f2 : f.listFiles()) {
						if(f2.isFile()) {
							doRemap(m.devToIntermediary, f);
						}
					}
				}
			}
		});
	}
	
	public RemapJar remap(File f) {
		jarsToRemap.add(f);
		if(f.isFile()) {
			getOutputs().file(f);
		}
		getInputs().file(f);
		return this;
	}
	
	private void doRemap(MappingKey.Loaded m, File f) {
		Path devJar = f.toPath();
		Path prodJar = devJar.resolveSibling(devJar.getFileName().toString().replace("-dev.jar", ".jar"));
		try {
			Files.move(prodJar, devJar, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new RuntimeException("error remapping built jar", e);
		}
		
		Remapper.remapWithModContext(m, devJar, prodJar, getProject().getConfigurations().getByName("mod_internal_mapped").resolve());
	}
}
