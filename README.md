# yet-another-minecraft-gradle
Loader-agnostic gradle plugin for working with Minecraft.

## Basic Use
First, in `settings.gradle` you will need to declare the repositories where YAMG and its dependencies can be found. This is done in the `pluginManagement` block, like so:
```groovy
pluginManagement {
  repositories {
    mavenCentral()
    maven {url = "https://maven.fabricmc.net"}
  }
}```
(Note: Since I currently lack a maven, I have not included anywhere YAMG itself can be found in this example.)

Then, in `build.gradle`, you will need to specify the version of minecraft, yarn mappings (support for other mapping types is planned), and repositories to resolve Minecraft's dependencies (and the mappings), like so:
```groovy
repositories {
  mavenCentral()
  maven {url = "https://maven.fabricmc.net"}
  maven {url = "https://libraries.minecraft.net"} // Mojang maven -- the only place some of Minecraft's dependencies can be found
}

minecraft {
  version = "1.16.5"
}

dependencies {
  yarn "net.fabricmc:yarn:1.16.5+build.6"
}
```

You'll probably also want to actually add minecraft and its dependencies to the classpath, like this:
```groovy
dependencies {
  implementation minecraft
  implementation minecraft.libraries
}
```

If you want to view the source code of Minecraft, simply run the `genSources` task. Note that you'll have to increase the amount of memory available to Gradle by putting `org.gradle.jvmargs="-Xmx4G"` in `gradle.properties` in order for this to not run out of memory and crash.

## Depending on Other Mods

Since mods are compiled to intermediary (note: this is not actually implemented yet, but it's at the top of the to-do list), YAMG provides a way to remap them so that they'll run in your development environment. The syntax is simply:
```groovy
dependencies {
  implementation(mod("com.example:example:1.0.0"))
}
```
