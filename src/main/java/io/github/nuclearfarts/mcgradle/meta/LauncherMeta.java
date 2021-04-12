package io.github.nuclearfarts.mcgradle.meta;

import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.github.nuclearfarts.mcgradle.util.Util;

public final class LauncherMeta {
	private static final Gson VER_GSON = new GsonBuilder().registerTypeAdapter(VersionInfo.class, new VersionInfo.MojangDeserializer()).create();
	private final Map<String, VersionInfo> versions = new HashMap<>();
	private final Map<String, String> versionUrls = new HashMap<>();
	private LauncherMeta() { }
	
	public VersionInfo getVersion(String ver) {
		return versions.computeIfAbsent(ver, v -> {
			URL url = Util.uncheckedUrl(versionUrls.get(v));
			try {
				return VER_GSON.fromJson(new InputStreamReader(url.openStream()), VersionInfo.class);
			} catch (IOException e) {
				throw new RuntimeException("Exception downloading info for version " + v, e);
			}
		});
	}
	
	public static final class Deserializer implements JsonDeserializer<LauncherMeta> {
		@Override
		public LauncherMeta deserialize(JsonElement jsonE, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
			LauncherMeta meta = new LauncherMeta();
			JsonObject json = jsonE.getAsJsonObject();
			for(JsonElement e : json.get("versions").getAsJsonArray()) {
				JsonObject ver = e.getAsJsonObject();
				String id = ver.get("id").getAsString();
				meta.versionUrls.put(id, ver.get("url").getAsString());
			}
			return meta;
		}
	}
}
