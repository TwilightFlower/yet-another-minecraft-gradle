package io.github.nuclearfarts.mcgradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.github.nuclearfarts.mcgradle.mapping.Remapper;
import io.github.nuclearfarts.mcgradle.meta.LauncherMeta;
import io.github.nuclearfarts.mcgradle.meta.VersionInfo;
import io.github.nuclearfarts.mcgradle.util.Util;
import net.fabricmc.stitch.merge.JarMerger;

public class McPluginInstance implements Remapper.YarnProvider {
	private static final URL LAUNCHERMETA = Util.uncheckedUrl("https://launchermeta.mojang.com/mc/game/version_manifest.json");
	private static LauncherMeta launcherMeta;
	private static final Map<String, VersionInfo> VERSION_CACHE = new HashMap<>();
	private static final Gson CACHED_VERSION_GSON = new GsonBuilder()
			.registerTypeAdapter(VersionInfo.class, new VersionInfo.Deserializer())
			.registerTypeAdapter(VersionInfo.class, new VersionInfo.Serializer())
			.create();
	private static final Gson LAUNCHERMETA_GSON = new GsonBuilder()
			.registerTypeAdapter(LauncherMeta.class, new LauncherMeta.Deserializer())
			.registerTypeAdapter(VersionInfo.class, new VersionInfo.MojangDeserializer())
			.create();
	public Path cacheRoot;
	public Project project;
	public McPluginExtension ext;
	public Remapper remapper;
	public Configuration yarnConf;
	public Configuration intermediaryConf;
	public Configuration mcDeps;
	private boolean intermediaryResolved = false;
	private boolean libsAdded = false;
	private final Set<Dependency> remap = new HashSet<>();
	
	public File getMinecraft(String mappings) {
		Path versionCache = cacheRoot.resolve("minecraft").resolve(ext.version);
		Path mcLoc = versionCache.resolve(mappings + "-merged.jar");
		Path lineMapped = versionCache.resolve(mappings + "-merged-lmap.jar");
		if(Files.exists(lineMapped)) {
			return lineMapped.toFile();
		}
		ensureMcExists(mappings, versionCache);
		return mcLoc.toFile();
	}
	
	public File getMappedMinecraft() {
		String mappings = "yarn-" + getYarnVersion();
		return getMinecraft(mappings);
	}
	
	public File getMcSources() {
		Path mc = getMappedMinecraft().toPath();
		return mc.getParent().resolve(mc.getFileName().toString().replace(".jar", "-src.jar")).toFile();
	}
	
	public void queueForRemapping(Dependency dep) {
		remap.add(dep);
	}
	
	private void ensureMcExists(String mappings, Path versionCache) {
		try {
			Files.createDirectories(versionCache);
		} catch (IOException e) {
			throw new RuntimeException("Could not create cache directory", e);
		}
		Path mcLoc = versionCache.resolve(mappings + "-merged.jar");
		if(Files.exists(mcLoc)) {
			return; // this makes our job real easy
		}
		Path intermediaryMergedLoc = versionCache.resolve("intermediary-merged.jar");
		if(Files.exists(intermediaryMergedLoc)) {
			remapper.remapToDev("intermediary", intermediaryMergedLoc, mcLoc);
			copyAssets(mcLoc);
			return;
		}
		Path obfMergedLoc = versionCache.resolve("obf-merged.jar");
		if(!Files.exists(obfMergedLoc)) {
			downloadAndMerge(versionCache);
		}
		System.out.println("official -> int");
		try {
		remapper.remap("official", "intermediary", obfMergedLoc, intermediaryMergedLoc);
		} catch(Throwable t) {
			t.printStackTrace();
		}
		System.out.println("int -> named");
		remapper.remapToDev("intermediary", intermediaryMergedLoc, mcLoc);
		return;
	}
	
	private void copyAssets(Path to) {
		
	}
	
	private void downloadAndMerge(Path versionCache) {
		Path serverLoc = versionCache.resolve("obf-server.jar");
		Path clientLoc = versionCache.resolve("obf-client.jar");
		if(!Files.exists(clientLoc)) {
			VersionInfo info = getVersionInfo();
			Util.download(Util.uncheckedUrl(info.getClientJarUrl()), clientLoc);
		}
		if(!Files.exists(serverLoc)) {
			VersionInfo info = getVersionInfo();
			Util.download(Util.uncheckedUrl(info.getServerJarUrl()), serverLoc);
		}
		
		try(JarMerger merger = new JarMerger(clientLoc.toFile(), clientLoc.toFile(), versionCache.resolve("obf-merged.jar").toFile())) {
			merger.enableSnowmanRemoval();
			merger.enableSyntheticParamsOffset();
			merger.merge();
		} catch (IOException e) {
			throw new RuntimeException("error merging jars", e);
		}
	}
	
	public VersionInfo getVersionInfo() {
		return VERSION_CACHE.computeIfAbsent(ext.version, ver -> {
			Path cache = cacheRoot.resolve("minecraft").resolve(ext.version).resolve("version-info.json");
			if(Files.exists(cache)) {
				try(Reader r = Files.newBufferedReader(cache)) {
					return CACHED_VERSION_GSON.fromJson(r, VersionInfo.class);
				} catch (IOException e) {
					throw new RuntimeException("exception reading cached version info", e);
				}
			} else {
				loadLauncherMeta();
				VersionInfo info = launcherMeta.getVersion(ver);
				try(Writer w = Files.newBufferedWriter(cache)) {
					CACHED_VERSION_GSON.toJson(info, w);
				} catch (IOException e) {
					System.err.println("Exception caching version info: ");
					e.printStackTrace();
				}
				return info;
			}
		});
	}
	
	public File resolveYarn() {
		return yarnConf.getSingleFile();
	}
	
	public String getYarnVersion() {
		return yarnConf.getResolvedConfiguration().getResolvedArtifacts().iterator().next().getModuleVersion().getId().getVersion();
	}
	
	public File resolveIntermediary() {
		if(!intermediaryResolved) {
			project.getDependencies().add("intermediary", "net.fabricmc:intermediary:" + ext.version + ":v2");
		}
		intermediaryResolved = true;
		return intermediaryConf.getSingleFile();
	}
	
	public void loadLibs() {
		if(!libsAdded) {
			VersionInfo info = getVersionInfo();
			for(String lib : info.getLibraries()) {
				project.getDependencies().add("mcDeps", lib);
			}
			for(String lib : info.getNatives(Util.getOs())) {
				project.getDependencies().add("mcDeps", lib);
			}
			libsAdded = true;
			mcDeps.setCanBeResolved(true);
		}
	}
	
	private void loadLauncherMeta() {
		if(launcherMeta == null) {
			try(InputStream inStream = LAUNCHERMETA.openStream()) {
				Reader r = new InputStreamReader(inStream);
				launcherMeta = LAUNCHERMETA_GSON.fromJson(r, LauncherMeta.class);
			} catch (IOException e) {
				throw new RuntimeException("exception accessing launchermeta", e);
			}
		}
	}
}
