package io.github.nuclearfarts.mcgradle.sources;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.function.BiConsumer;
import java.util.jar.Manifest;

import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider;

import io.github.nuclearfarts.mcgradle.util.Util;
import net.fabricmc.fernflower.api.IFabricResultSaver;

public class FFFileOps implements IBytecodeProvider, IFabricResultSaver, Closeable, AutoCloseable {
	private final Map<Path, FileSystem> fileSystems = new ConcurrentHashMap<>();
	private final BiConsumer<String, int[]> lmapConsumer;
	private final CountDownLatch latch = new CountDownLatch(1);
	private final Path archiveDir;
	
	public FFFileOps(BiConsumer<String, int[]> lmapConsumer, Path archiveDir) {
		this.lmapConsumer = lmapConsumer;
		this.archiveDir = archiveDir;
		try {
			Files.createDirectories(archiveDir);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	@Override
	public byte[] getBytecode(String externalPath, String internalPath) throws IOException {
		FileSystem zipFs = resolveFs(Paths.get(externalPath), false);
		Path inner = zipFs.getPath(internalPath);
		try(InputStream in = new BufferedInputStream(Files.newInputStream(inner))) {
			return Util.readStream(in);
		}
	}

	@Override
	public void close() throws IOException {
		List<IOException> exceptions = new ArrayList<>();
		for(FileSystem fs : fileSystems.values()) {
			try {
				fs.close();
			} catch(IOException e) {
				exceptions.add(e);
			}
		}
		if(!exceptions.isEmpty()) {
			IOException rootCause = exceptions.get(0);
			for(int i = 1; i < exceptions.size(); i++) {
				rootCause.addSuppressed(exceptions.get(i));
			}
			throw rootCause;
		}
	}
	
	public void await() throws InterruptedException {
		// this is an awful awful hack
		latch.await();
	}

	@Override
	public void saveFolder(String path) {
		try {
			Files.createDirectories(Paths.get(path));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void copyFile(String source, String path, String entryName) {
		try {
			Files.copy(Paths.get(source), Paths.get(path, entryName), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
		lmapConsumer.accept(qualifiedName, mapping);
		try(Writer w = Files.newBufferedWriter(Paths.get(path, entryName), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			w.write(content);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void createArchive(String path, String archiveName, Manifest manifest) {
		try {
			FileSystem fs = resolveFs(archiveDir.resolve(path).resolve(archiveName), true);
			if(manifest != null) {
				Path metaInf = fs.getPath("META-INF");
				Files.createDirectories(metaInf);
				try(OutputStream out = new BufferedOutputStream(Files.newOutputStream(metaInf.resolve("MANIFEST.MF"), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
					manifest.write(out);
				}
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void saveDirEntry(String path, String archiveName, String entryName) {
		FileSystem zipFs = resolveFs(archiveDir.resolve(path).resolve(archiveName), false);
		Path p = zipFs.getPath(entryName);
		try {
			Files.createDirectories(p);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void copyEntry(String source, String path, String archiveName, String entry) {
		FileSystem zipFsSrc = resolveFs(Paths.get(source), false);
		Path src = zipFsSrc.getPath(entry);
		FileSystem zipFsDest = resolveFs(archiveDir.resolve(path).resolve(archiveName), false);
		Path dst = zipFsDest.getPath(entry);
		try {
			Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
		FileSystem zipFs = resolveFs(archiveDir.resolve(path).resolve(archiveName), false);
		try(Writer w = Files.newBufferedWriter(zipFs.getPath(entryName), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			w.write(content);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void closeArchive(String path, String archiveName) {
		latch.countDown();
	}

	@Override
	public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content, int[] mapping) {
		lmapConsumer.accept(qualifiedName, mapping);
		FileSystem zipFs = resolveFs(archiveDir.resolve(path).resolve(archiveName), false);
		try(Writer w = Files.newBufferedWriter(zipFs.getPath(entryName), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
			w.write(content);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		//System.out.println(String.format("SCE(m) path %s arcN %s qalN &% entN %s", path, archiveName, qualifiedName, entryName));
	}
	
	private FileSystem resolveFs(Path fsPath, boolean create) {
		fsPath = fsPath.toAbsolutePath();
		FileSystem zipFs = fileSystems.get(fsPath);
		if(zipFs == null) {
			try {
				zipFs = fileSystems.computeIfAbsent(fsPath, extPath -> {
					try {
						FileSystem fs = FileSystems.getFileSystem(Util.jarFsUri(extPath));
						if(fs.isOpen()) {
							return fs;
						}
					} catch (FileSystemNotFoundException e) {}
					try {
						if(create) {
							return FileSystems.newFileSystem(Util.jarFsUri(extPath), Util.FS_ENV, getClass().getClassLoader());
						} else {
							return FileSystems.newFileSystem(extPath, getClass().getClassLoader());
						}
					} catch (IOException e) {
						throw new Util.LambdasSuckException(e);
					}
				});
			} catch(Util.LambdasSuckException e) {
				throw new RuntimeException("error loading file for decompilation: " + fsPath, e.exc);
			}
		}
		return zipFs;
	}
}
