package io.github.nuclearfarts.mcgradle;

import java.io.File;
import java.io.Serializable;
import java.util.function.Supplier;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.tasks.JavaExec;
import org.gradle.api.tasks.bundling.Jar;
import org.gradle.plugins.ide.eclipse.model.AbstractLibrary;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;

import com.google.common.collect.ImmutableMap;

import io.github.nuclearfarts.mcgradle.mapping.Mappings;
import io.github.nuclearfarts.mcgradle.mapping.MappingsExtension;
import io.github.nuclearfarts.mcgradle.mapping.ModTransformer;
import io.github.nuclearfarts.mcgradle.mapping.RemapJar;
import io.github.nuclearfarts.mcgradle.mapping.Remapper;
import io.github.nuclearfarts.mcgradle.sources.GenSources;

public class McGradlePlugin implements Plugin<Project> {
	public static final Attribute<Boolean> REMAP = Attribute.of("remap", Boolean.class);
	
	@Override
	public void apply(Project proj) {
		proj.apply(ImmutableMap.of("plugin", "java"));
		proj.apply(ImmutableMap.of("plugin", "eclipse"));
		proj.apply(ImmutableMap.of("plugin", "idea"));
		
		McPluginInstance data = new McPluginInstance();
		proj.getDependencies().getAttributesSchema().attribute(REMAP);
		proj.getDependencies().getArtifactTypes().getByName("jar").getAttributes().attribute(REMAP, true);
		
		
		proj.getExtensions().add("minecraft", new McPluginExtension(proj, data));
		proj.getExtensions().add("mappings", new MappingsExtension(proj));
		Configuration mcDeps = proj.getConfigurations().create("mcDeps").setTransitive(false);
		proj.getConfigurations().create("mod_internal_unmapped");
		proj.getConfigurations().create("mod_internal_mapped");
		mcDeps.setCanBeResolved(false);
		
		data.ext = proj.getExtensions().getByType(McPluginExtension.class);
		data.project = proj;
		data.cacheRoot = proj.getGradle().getGradleUserHomeDir().toPath().resolve("caches").resolve("mcgradle");
		data.ext.data = data;
		data.mcDeps = mcDeps;
		data.ext.libraries = mcDeps;
		data.mappingLoader = new Mappings(proj.getExtensions().getByType(MappingsExtension.class), data.ext);
		proj.getTasks().create("genSources", GenSources.class);
		
		Jar jar = (Jar) proj.getTasks().getAt("jar");
		
		RemapJar remapJar = proj.getTasks().create("remapJar", RemapJar.class);
		remapJar.dependsOn(jar);
		proj.getTasks().getByName("build").dependsOn(remapJar);
		jar.doFirst(j -> {
			jar.getArchiveClassifier().set(jar.getArchiveClassifier().get() + "-dev");
		});
		EclipseModel model = proj.getExtensions().getByType(EclipseModel.class);
		model.getClasspath().getFile().whenMerged(cp1 -> {
			Classpath cp = (Classpath) cp1;
			for(ClasspathEntry e : cp.getEntries()) {
				if(e.getKind().equals("lib")) {
					AbstractLibrary lib = (AbstractLibrary) e;
					File libF = lib.getLibrary().getFile();
					if(libF.equals(data.getMinecraft())) {
						File mcSrc = data.getMcSources();
						if(mcSrc.exists()) {
							lib.setSourcePath(cp.fileReference(mcSrc));
						}
					}
				}
			}
		});
		
		JavaExec runClient = proj.getTasks().create("runClient", JavaExec.class);
		runClient.dependsOn(proj.getConfigurations().getByName("runtimeClasspath"));
		runClient.setMain("net.minecraft.client.main.Main");
		runClient.args("--version", (Supplier<String>) () -> data.ext.version, "--accessToken", "NO_TOKEN");
		runClient.classpath(proj.getConfigurations().getByName("runtimeClasspath"));
		
		proj.getConfigurations().getByName("mod_internal_mapped").getAttributes().attribute(REMAP, false);
		proj.afterEvaluate(p -> {
			remapJar.remap(jar.getOutputs().getFiles().getSingleFile());
			// now that we know our yarn version we can do this
			proj.getDependencies().registerTransform(ModTransformer.class, t -> {
				t.getFrom().attribute(REMAP, true);
				t.getTo().attribute(REMAP, false);
				t.getParameters().setMappingKey(data.getUsedMappings().intermediaryToDev);
			});
			// load the MC libraries.
			data.loadLibs();
		});
	}
}
