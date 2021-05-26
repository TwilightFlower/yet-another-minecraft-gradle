package io.github.nuclearfarts.mcgradle.mapping;

import java.io.Serializable;

import net.fabricmc.tinyremapper.IMappingProvider;

public class MappingKey {
	public final String src, target;
	private final int hashCode;
	
	public MappingKey(String s, String t) {
		src = s;
		target = t;
		hashCode = s.hashCode() ^ t.hashCode();
	}
	
	@Override
	public int hashCode() {
		return hashCode;
	}
	
	@Override
	public boolean equals(Object other) {
		if(!(other instanceof MappingKey)) return false;
		MappingKey o = (MappingKey) other;
		return o.hashCode == hashCode && o.src.equals(src) && o.target.equals(target);
	}
	
	@Override
	public String toString() {
		return src + " -> " + target;
	}
	
	@SuppressWarnings("serial")
	public static class Loaded implements Serializable {
		public final String src, target, mc;
		private final int hashCode;
		
		Loaded(String s, String t, String m) {
			src = s;
			target = t;
			mc = m;
			hashCode = s.hashCode() ^ t.hashCode() ^ m.hashCode();
		}
		
		@Override
		public int hashCode() {
			return hashCode;
		}
		
		@Override
		public boolean equals(Object other) {
			if(!(other instanceof Loaded)) return false;
			Loaded o = (Loaded) other;
			return o.hashCode == hashCode && o.src.equals(src) && o.target.equals(target) && o.mc.equals(mc);
		}
		
		@Override
		public String toString() {
			return String.format("%s -> %s (%s)", src, target, mc);
		}
		
		public IMappingProvider get() {
			return Mappings.CACHE.get(this);
		}
	}
}