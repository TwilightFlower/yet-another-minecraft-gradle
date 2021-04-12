package io.github.nuclearfarts.mcgradle;

import java.io.File;
import java.io.Serializable;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.attributes.Attribute;
import org.gradle.plugins.ide.eclipse.model.AbstractLibrary;
import org.gradle.plugins.ide.eclipse.model.Classpath;
import org.gradle.plugins.ide.eclipse.model.ClasspathEntry;
import org.gradle.plugins.ide.eclipse.model.EclipseModel;

import com.google.common.collect.ImmutableMap;

import io.github.nuclearfarts.mcgradle.mapping.ModTransformer;
import io.github.nuclearfarts.mcgradle.mapping.Remapper;
import io.github.nuclearfarts.mcgradle.mapping.Remapper.YarnProvider;
import io.github.nuclearfarts.mcgradle.sources.GenSources;

public class McGradlePlugin implements Plugin<Project> {
	public static final Attribute<Boolean> REMAP = Attribute.of("remap", Boolean.class);
	
	@Override
	public void apply(Project proj) {
		proj.apply(ImmutableMap.of("plugin", "java"));
		proj.apply(ImmutableMap.of("plugin", "eclipse"));
		proj.apply(ImmutableMap.of("plugin", "idea"));
		//proj.getDependencies().registerTransform(Trans, null);
		McPluginInstance data = new McPluginInstance();
		proj.getDependencies().getAttributesSchema().attribute(REMAP);
		proj.getDependencies().getArtifactTypes().getByName("jar").getAttributes().attribute(REMAP, true);
		
		
		proj.getExtensions().add("minecraft", new McPluginExtension(proj, data));
		Configuration yarn = proj.getConfigurations().create("yarn");
		Configuration intermediary = proj.getConfigurations().create("intermediary");
		Configuration mcDeps = proj.getConfigurations().create("mcDeps").setTransitive(false);
		proj.getConfigurations().create("mod");
		mcDeps.setCanBeResolved(false);
		
		data.ext = proj.getExtensions().getByType(McPluginExtension.class);
		data.yarnConf = yarn;
		data.intermediaryConf = intermediary;
		data.remapper = new Remapper(data);
		data.project = proj;
		data.cacheRoot = proj.getGradle().getGradleUserHomeDir().toPath().resolve("mcgradle");
		data.ext.data = data;
		data.mcDeps = mcDeps;
		data.ext.libraries = mcDeps;
		proj.getTasks().create("genSources", GenSources.class);
		EclipseModel model = proj.getExtensions().getByType(EclipseModel.class);
		model.getClasspath().getFile().whenMerged(cp1 -> {
			Classpath cp = (Classpath) cp1;
			for(ClasspathEntry e : cp.getEntries()) {
				if(e.getKind().equals("lib")) {
					AbstractLibrary lib = (AbstractLibrary) e;
					File libF = lib.getLibrary().getFile();
					if(libF.equals(data.getMappedMinecraft())) {
						File mcSrc = data.getMcSources();
						if(mcSrc.exists()) {
							lib.setSourcePath(cp.fileReference(mcSrc));
						}
					}
				}
			}
		});
		proj.getConfigurations().getByName("mod").getAttributes().attribute(REMAP, false);
		proj.afterEvaluate(p -> {
			// now that we know our yarn version we can do this
			proj.getDependencies().registerTransform(ModTransformer.class, t -> {
				t.getFrom().attribute(REMAP, true);
				t.getTo().attribute(REMAP, false);
				t.getParameters().setYarnProvider(new LoadedYarnProvider(data.getYarnVersion(), data.resolveYarn()));
			});
			// load the MC libraries.
			data.loadLibs();
		});
	}
	
	@SuppressWarnings("serial")
	private static final class LoadedYarnProvider implements YarnProvider, Serializable {
		private final String ver;
		private final File f;
		public LoadedYarnProvider(String ver, File f) {
			this.ver = ver;
			this.f = f;
		}
		
		@Override
		public String getYarnVersion() {
			return ver;
		}

		@Override
		public File resolveYarn() {
			return f;
		}
		
	}
}
