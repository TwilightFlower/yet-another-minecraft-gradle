package io.github.nuclearfarts.mcgradle.mapping;

public class MappingStore {
	public final MappingKey.Loaded officialToIntermediary, intermediaryToDev, devToIntermediary;
	
	public MappingStore(MappingKey.Loaded o2i, MappingKey.Loaded i2d, MappingKey.Loaded d2i) {
		officialToIntermediary = o2i;
		intermediaryToDev = i2d;
		devToIntermediary = d2i;
	}
}
