package io.github.nuclearfarts.mcgradle.meta;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

public final class VersionInfo {
	private String serverJarUrl;
	private String serverMojmapUrl;
	private String clientJarUrl;
	private String clientMojmapUrl;
	private String assetsUrl;
	private String assetsId;
	private final Map<String, Set<String>> natives = new HashMap<>();
	private final Set<String> libraries = new HashSet<>();
	
	private VersionInfo() {}
	
	public String getServerJarUrl() {
		return serverJarUrl;
	}

	public String getServerMojmapUrl() {
		return serverMojmapUrl;
	}

	public String getClientJarUrl() {
		return clientJarUrl;
	}

	public String getClientMojmapUrl() {
		return clientMojmapUrl;
	}

	public String getAssetsId() {
		return assetsId;
	}

	public String getAssetsUrl() {
		return assetsUrl;
	}
	
	public Set<String> getLibraries() {
		return libraries;
	}
	
	public Set<String> getNatives(String os) {
		return natives.getOrDefault(os, Collections.emptySet());
	}

	public static final class MojangDeserializer implements JsonDeserializer<VersionInfo> {
		@Override
		public VersionInfo deserialize(JsonElement jsonE, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			VersionInfo info = new VersionInfo();
			JsonObject json = jsonE.getAsJsonObject();
			JsonObject dls = json.getAsJsonObject("downloads");
			info.serverJarUrl = urlOf("server", dls);
			info.clientJarUrl = urlOf("client", dls);
			info.serverMojmapUrl = urlOf("server-mappings", dls);
			info.clientMojmapUrl = urlOf("client-mappings", dls);
			for(JsonElement e : json.getAsJsonArray("libraries")) {
				JsonObject lib = e.getAsJsonObject();
				String name = lib.get("name").getAsString();
				info.libraries.add(name);
				JsonObject natives = lib.getAsJsonObject("natives");
				if(natives != null) {
					for(Map.Entry<String, JsonElement> entry : natives.entrySet()) {
						info.natives.computeIfAbsent(entry.getKey(), s -> new HashSet<>()).add(name + ":" + entry.getValue().getAsString());
					}
				}
			}
			JsonObject assets = json.getAsJsonObject("assetIndex");
			info.assetsId = assets.get("id").getAsString();
			info.assetsUrl = assets.get("url").getAsString();
			return info;
		}
		
		private String urlOf(String name, JsonObject dls) {
			JsonObject dl = dls.getAsJsonObject(name);
			if(dl == null) {
				return null;
			}
			JsonElement url = dl.get("url");
			if(url == null) {
				return null;
			}
			return url.getAsString();
		}
	}
	
	public static final class Deserializer implements JsonDeserializer<VersionInfo> {
		@Override
		public VersionInfo deserialize(JsonElement jsonE, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			VersionInfo info = new VersionInfo();
			JsonObject json = jsonE.getAsJsonObject();
			info.serverJarUrl = context.deserialize(json.get("serverJar"), String.class);
			info.serverMojmapUrl = context.deserialize(json.get("serverMappings"), String.class);
			info.clientJarUrl = context.deserialize(json.get("clientJar"), String.class);
			info.clientMojmapUrl = context.deserialize(json.get("clientMappings"), String.class);
			info.assetsId = context.deserialize(json.get("assetsId"), String.class);
			info.assetsUrl = context.deserialize(json.get("assetsUrl"), String.class);
			info.libraries.addAll(context.deserialize(json.get("libraries"), Set.class));
			JsonObject natives = json.getAsJsonObject("natives");
			for(Map.Entry<String, JsonElement> e : natives.entrySet()) {
				info.natives.put(e.getKey(), context.deserialize(e.getValue(), Set.class));
			}
			return info;
		}
	}
	
	public static final class Serializer implements JsonSerializer<VersionInfo> {
		@Override
		public JsonElement serialize(VersionInfo info, Type typeOfSrc, JsonSerializationContext context) {
			JsonObject json = new JsonObject();
			json.addProperty("serverJar", info.serverJarUrl);
			json.addProperty("serverMappings", info.serverMojmapUrl);
			json.addProperty("clientJar", info.clientJarUrl);
			json.addProperty("clientMappings", info.clientMojmapUrl);
			json.addProperty("assetsId", info.assetsId);
			json.addProperty("assetsUrl", info.assetsUrl);
			json.add("libraries", context.serialize(info.libraries));
			json.add("natives", context.serialize(info.natives));
			return json;
		}
	}
}