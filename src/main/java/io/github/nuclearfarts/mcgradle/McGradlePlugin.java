package io.github.nuclearfarts.mcgradle;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.plugins.JavaPluginExtension;
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
import io.github.nuclearfarts.mcgradle.mapping.RemapSourcesJar;
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
		proj.getConfigurations().create("sourceRemapPath");
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
			
		});
		
		JavaPluginExtension java = proj.getExtensions().getByType(JavaPluginExtension.class);
		java.withSourcesJar();
		
		Jar sourcesJar = (Jar) proj.getTasks().getByName("sourcesJar");
		RemapSourcesJar remapSourcesJar = proj.getTasks().create("remapSourcesJar", RemapSourcesJar.class);
		remapSourcesJar.dependsOn(sourcesJar);
		sourcesJar.finalizedBy(remapSourcesJar);
		/*sourcesJar.doFirst(j -> {
			sourcesJar.getArchiveClassifier().set(sourcesJar.getArchiveClassifier().get() + "dev");
		});*/
		
		EclipseModel model = proj.getExtensions().getByType(EclipseModel.class);
		model.getClasspath().getFile().whenMerged(cp1 -> {
			Set<File> remapClasspath = new HashSet<>();
			remapClasspath.addAll(proj.getConfigurations().getByName("mod_internal_unmapped").resolve());
			remapClasspath.addAll(proj.getConfigurations().getByName("mcDeps").resolve());
			remapClasspath.addAll(proj.getConfigurations().getByName("sourceRemapPath").resolve());
			remapClasspath.addAll(proj.getConfigurations().getByName("runtimeClasspath").resolve());
			remapClasspath.add(data.getIntermediaryMinecraft());
			Set<File> toRemap = proj.getConfigurations().getByName("mod_internal_mapped").resolve();
			//System.out.println(toRemap);
			Classpath cp = (Classpath) cp1;
			for(ClasspathEntry e : cp.getEntries()) {
				if(e.getKind().equals("lib")) {
					AbstractLibrary lib = (AbstractLibrary) e;
					File libF = lib.getLibrary().getFile();
					//System.out.println(String.format("%s %s", libF, toRemap.contains(libF)));
					if(libF.equals(data.getMinecraft())) {
						File mcSrc = data.getMcSources();
						if(mcSrc.exists()) {
							lib.setSourcePath(cp.fileReference(mcSrc));
						}
					} else if(toRemap.contains(libF)) {
						File inputSrc = data.getInputSource(libF.getName());
						try {
							Path remapped = Remapper.remapSource(data.getUsedMappings().intermediaryToDev, inputSrc.toPath(), remapClasspath);
							lib.setSourcePath(cp.fileReference(remapped.toFile()));
						} catch(RuntimeException ex) {
							System.err.println("Error remapping source for " + libF.getName() + ": " + ex.getCause().getCause().getLocalizedMessage());
							lib.setSourcePath(cp.fileReference(inputSrc));
						}
					}
				}
			}
		});
		
		proj.getConfigurations().getByName("mod_internal_mapped").getAttributes().attribute(REMAP, false);
		proj.afterEvaluate(p -> {
			File nonDev = jar.getArchiveFile().get().getAsFile();
			jar.getArchiveClassifier().set(jar.getArchiveClassifier().get() + "dev");
			remapJar.remap(jar.getArchiveFile().get().getAsFile(), nonDev);
			
			File nonDevSrc = sourcesJar.getArchiveFile().get().getAsFile();
			sourcesJar.getArchiveClassifier().set(sourcesJar.getArchiveClassifier().get() + "dev");
			remapSourcesJar.remap(sourcesJar.getArchiveFile().get().getAsFile(), nonDevSrc);
			// now that we know our yarn version we can do this
			proj.getDependencies().registerTransform(ModTransformer.class, t -> {
				Set<File> unmappedPath = new HashSet<>();
				unmappedPath.addAll(proj.getConfigurations().getByName("mod_internal_unmapped").resolve());
				unmappedPath.add(data.getIntermediaryMinecraft());
				t.getFrom().attribute(REMAP, true);
				t.getTo().attribute(REMAP, false);
				t.getParameters().setMappingKey(data.getUsedMappings().intermediaryToDev);
				t.getParameters().setUnmappedFiles(unmappedPath);
			});
			// load the MC libraries.
			data.loadLibs();
			//System.out.println(proj.getConfigurations().getByName("__mod_internal_hack_0").getResolvedConfiguration().getFiles());
			//System.out.println(data.inputSources);
		});
	}
}
