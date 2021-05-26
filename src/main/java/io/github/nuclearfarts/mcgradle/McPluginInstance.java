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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import io.github.nuclearfarts.mcgradle.mapping.MappingKey;
import io.github.nuclearfarts.mcgradle.mapping.MappingStore;
import io.github.nuclearfarts.mcgradle.mapping.Mappings;
import io.github.nuclearfarts.mcgradle.mapping.Remapper;
import io.github.nuclearfarts.mcgradle.meta.LauncherMeta;
import io.github.nuclearfarts.mcgradle.meta.VersionInfo;
import io.github.nuclearfarts.mcgradle.util.Pair;
import io.github.nuclearfarts.mcgradle.util.Util;
import net.fabricmc.stitch.merge.JarMerger;

public class McPluginInstance {
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
	public List<Pair<Function<MappingKey.Loaded, String>, File>> inputSourceProviders = new ArrayList<>();
	private Map<String, File> inputSources;
	public Path cacheRoot;
	public Project project;
	public McPluginExtension ext;
	public Configuration mcDeps;
	public Mappings mappingLoader;
	private MappingStore mappingStore;
	private boolean libsAdded = false;
	
	public MappingStore getUsedMappings() {
		if(mappingStore == null) {
			mappingStore = mappingLoader.loadMain();
		}
		return mappingStore;
	}
	
	public File getMinecraft() {
		Path versionCache = cacheRoot.resolve("minecraft").resolve(ext.version);
		Path mcLoc = versionCache.resolve(getMinecraftName() + ".jar");
		Path lineMapped = versionCache.resolve(getMinecraftName() + "-lmap.jar");
		if(Files.exists(lineMapped)) {
			return lineMapped.toFile();
		}
		ensureMcExists(versionCache);
		return mcLoc.toFile();
	}
	
	public File getIntermediaryMinecraft() {
		Path versionCache = cacheRoot.resolve("minecraft").resolve(ext.version);
		Path mcLoc = versionCache.resolve(getMinecraftName(getUsedMappings().officialToIntermediary) + ".jar");
		ensureMcExists(versionCache);
		return mcLoc.toFile();
	}
	
	public String getMinecraftName(MappingKey.Loaded mappings, boolean merged) {
		return mappings.target + (merged ? "-merged" : "");
	}
	
	public String getMinecraftName(MappingKey.Loaded mappings) {
		return getMinecraftName(mappings, true);
	}
	
	public String getMinecraftName() {
		return getMinecraftName(getUsedMappings().intermediaryToDev, true);
	}
	
	public File getMcSources() {
		Path mc = getMinecraft().toPath();
		return mc.resolveSibling(getMinecraftName() + "-lmap-src.jar").toFile();
	}
	
	private void ensureMcExists(Path versionCache) {
		try {
			Files.createDirectories(versionCache);
		} catch (IOException e) {
			throw new RuntimeException("Could not create cache directory", e);
		}
		Path mcLoc = versionCache.resolve(getMinecraftName(getUsedMappings().intermediaryToDev) + ".jar");
		if(Files.exists(mcLoc)) {
			return; // this makes our job real easy
		}
		Path intermediaryMergedLoc = versionCache.resolve("intermediary-merged.jar");
		if(Files.exists(intermediaryMergedLoc)) {
			Remapper.remap(getUsedMappings().intermediaryToDev, intermediaryMergedLoc, mcLoc);
			copyAssets(mcLoc);
			return;
		}
		Path obfMergedLoc = versionCache.resolve("obf-merged.jar");
		if(!Files.exists(obfMergedLoc)) {
			downloadAndMerge(versionCache);
		}
		System.out.println("official -> int");
		Remapper.remap(getUsedMappings().officialToIntermediary, obfMergedLoc, intermediaryMergedLoc);
		System.out.println("int -> named");
		Remapper.remap(getUsedMappings().intermediaryToDev, intermediaryMergedLoc, mcLoc);
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
			try {
				Files.createDirectories(cache.getParent());
			} catch (IOException e1) { }
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
	
	public File getInputSource(String s) {
		if(inputSources == null) {
			inputSources = new HashMap<>();
			MappingKey.Loaded mappings = mappingStore.intermediaryToDev;
			for(Pair<Function<MappingKey.Loaded, String>, File> p : inputSourceProviders) {
				inputSources.put(p.left.apply(mappings), p.right);
			}
		}
		return inputSources.get(s);
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
