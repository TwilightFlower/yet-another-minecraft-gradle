package io.github.nuclearfarts.mcgradle.mapping;

import java.io.File;
import java.util.Set;

import org.gradle.api.artifacts.transform.CacheableTransform;
import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.api.artifacts.transform.TransformAction;
import org.gradle.api.artifacts.transform.TransformOutputs;
import org.gradle.api.artifacts.transform.TransformParameters;
import org.gradle.api.file.FileSystemLocation;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;

@CacheableTransform
public abstract class ModTransformer implements TransformAction<ModTransformer.Parameters> {
	@Classpath
	@InputArtifact
	public abstract Provider<FileSystemLocation> getInputArtifact();
	
	@Override
	public void transform(TransformOutputs outputs) {
		File loc = getInputArtifact().get().getAsFile();
		MappingKey.Loaded mappings = getParameters().getMappingKey();
		String fName = loc.toPath().getFileName().toString().replace(".jar", "-mapped-" + mappings.target + ".jar");
		File out = outputs.file(fName);
		Remapper.remapWithModContext(getParameters().getMappingKey(), loc.toPath(), out.toPath(), getParameters().getUnmappedFiles());
	}
	
	public interface Parameters extends TransformParameters {
		@Input
		MappingKey.Loaded getMappingKey();
		void setMappingKey(MappingKey.Loaded key);
		@Input
		Set<File> getUnmappedFiles();
		void setUnmappedFiles(Set<File> files);
	}
}
