package io.github.nuclearfarts.mcgradle.util;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Predicate;

public class DirectoryCopyFileVisitor extends SimpleFileVisitor<Path> {
	private final Path inDir, outDir;
	private final Predicate<Path> copyFilter;
	public DirectoryCopyFileVisitor(Path inDir, Path outDir, Predicate<Path> copyFilter) {
		this.inDir = inDir.toAbsolutePath();
		this.outDir = outDir;
		this.copyFilter = copyFilter;
	}
	
	@Override
	public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
		if(copyFilter.test(file)) {
			Path rel = inDir.relativize(file.toAbsolutePath());
			Path out = outDir.resolve(rel);
			Files.createDirectories(out.getParent());
			Files.copy(file, out, StandardCopyOption.REPLACE_EXISTING);
		}
		return FileVisitResult.CONTINUE;
	}
}
