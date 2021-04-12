package io.github.nuclearfarts.mcgradle.mapping;

import java.io.File;

import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;

import io.github.nuclearfarts.mcgradle.mapping.Remapper.YarnProvider;

@CacheableTransform
public abstract class ModTransformer implements TransformAction<ModTransformer.Parameters> {
	@Classpath
	@InputArtifact
	public abstract Provider<FileSystemLocation> getInputArtifact();
	
	@Override
	public void transform(TransformOutputs outputs) {
		File loc = getInputArtifact().get().getAsFile();
		String fName = loc.toPath().getFileName().toString().replace(".jar", "-mapped-yarn-" + getParameters().getYarnProvider().getYarnVersion() + ".jar");
		File out = outputs.file(fName);
		Remapper r = new Remapper(getParameters().getYarnProvider());
		r.remapToDev("intermediary", loc.toPath(), out.toPath());
	}
	
	public interface Parameters extends TransformParameters {
		@Input
		YarnProvider getYarnProvider();
		void setYarnProvider(YarnProvider provider);
	}
}
