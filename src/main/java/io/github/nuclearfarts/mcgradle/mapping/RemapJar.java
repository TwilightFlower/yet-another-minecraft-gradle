package io.github.nuclearfarts.mcgradle.mapping;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.gradle.api.DefaultTask;
import org.gradle.api.plugins.JavaPlugin;

import io.github.nuclearfarts.mcgradle.McPluginExtension;

public class RemapJar extends DefaultTask {
	private final List<File> jarsToRemap = new ArrayList<>();
	
	public RemapJar() {
		doLast(t -> {
			Remapper r = getProject().getExtensions().getByType(McPluginExtension.class).getPluginDataInternal().remapper;
			for(File f : jarsToRemap) {
				if(f.isFile()) {
					doRemap(r, f);
				} else if(f.isDirectory()) {
					for(File f2 : f.listFiles()) {
						if(f2.isFile()) {
							doRemap(r, f);
						}
					}
				}
			}
		});
	}
	
	public RemapJar remap(File f) {
		jarsToRemap.add(f);
		return this;
	}
	
	private void doRemap(Remapper r, File f) {
		Path prodJar = f.toPath();
		Path devJar = prodJar.resolveSibling(prodJar.getFileName().toString().replace(".jar", "-dev.jar"));
		try {
			Files.move(prodJar, devJar, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new RuntimeException("error remapping built jar", e);
		}
		
		r.remapWithModContext("named", "intermediary", devJar, prodJar, getProject().getConfigurations().getByName(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME));
	}
}
