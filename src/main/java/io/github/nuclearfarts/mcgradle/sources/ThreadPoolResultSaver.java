package io.github.nuclearfarts.mcgradle.sources;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.Manifest;

import net.fabricmc.fernflower.api.IFabricResultSaver;

/**
 * zipfs was being so slow that i did this
 */
public class ThreadPoolResultSaver implements IFabricResultSaver, Closeable, AutoCloseable {
	private final ExecutorService threadPool = Executors.newFixedThreadPool(2);
	private final IFabricResultSaver delegate;
	public ThreadPoolResultSaver(IFabricResultSaver delegate) {
		this.delegate = delegate;
	}
	
	
	@Override
	public void close() throws IOException {
		threadPool.shutdown();
		try {
			threadPool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		if(delegate instanceof Closeable) {
			((Closeable) delegate).close();
		}
	}

	@Override
	public void saveFolder(String path) {
		threadPool.execute(() -> delegate.saveFolder(path));
	}

	@Override
	public void copyFile(String source, String path, String entryName) {
		threadPool.execute(() -> delegate.copyFile(source, path, entryName));
	}

	@Override
	public void saveClassFile(String path, String qualifiedName, String entryName, String content, int[] mapping) {
		threadPool.execute(() -> delegate.saveClassFile(path, qualifiedName, entryName, content, mapping));
	}

	@Override
	public void createArchive(String path, String archiveName, Manifest manifest) {
		threadPool.execute(() -> delegate.createArchive(path, archiveName, manifest));
	}

	@Override
	public void saveDirEntry(String path, String archiveName, String entryName) {
		threadPool.execute(() -> delegate.saveDirEntry(path, archiveName, entryName));
	}

	@Override
	public void copyEntry(String source, String path, String archiveName, String entry) {
		threadPool.execute(() -> delegate.copyEntry(source, path, archiveName, entry));
	}

	@Override
	public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content) {
		threadPool.execute(() -> delegate.saveClassEntry(path, archiveName, qualifiedName, entryName, content));
	}

	@Override
	public void closeArchive(String path, String archiveName) {
		threadPool.execute(() -> delegate.closeArchive(path, archiveName));
	}

	@Override
	public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName, String content, int[] mapping) {
		threadPool.execute(() -> delegate.saveClassEntry(path, archiveName, qualifiedName, entryName, content, mapping));
	}

}
