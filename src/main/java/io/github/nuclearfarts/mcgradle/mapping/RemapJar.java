package io.github.nuclearfarts.mcgradle.mapping;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.DefaultTask;
import io.github.nuclearfarts.mcgradle.McPluginExtension;
import io.github.nuclearfarts.mcgradle.util.Pair;

public class RemapJar extends DefaultTask {
	private final List<Pair<File, File>> jarsToRemap = new ArrayList<>();
	
	public RemapJar() {
		doLast(t -> {
			MappingStore m = getProject().getExtensions().getByType(McPluginExtension.class).getPluginDataInternal().getUsedMappings();
			for(Pair<File, File> p : jarsToRemap) {
				doRemap(m.devToIntermediary, p.left, p.right);
			}
		});
	}
	
	public RemapJar remap(File dev, File prod) {
		jarsToRemap.add(new Pair<>(dev, prod));
		getOutputs().file(prod);
		getInputs().file(dev);
		return this;
	}
	
	private void doRemap(MappingKey.Loaded m, File dev, File prod) {
		Path devJar = dev.toPath();
		Path prodJar = prod.toPath();
		
		Remapper.remapWithModContext(m, devJar, prodJar, getProject().getConfigurations().getByName("mod_internal_mapped").resolve());
	}
}
