# Building Guidelines

This document describes how to build Combatant Client from source and how to handle the native runtime files used by the project.

## Requirements

- Java 25 JDK
- Git
- Windows
- IntelliJ IDEA
- Windows with CMake and a C++ toolchain only if you need to rebuild the Windows MediaPlayerInfo native library

The project currently targets:

- Minecraft 26.2
- Fabric Loader 0.19.3
- Fabric API 0.152.2+26.2
- Sodium, preferably `sodium-fabric-0.9.0+mc26.2`

Download the recommended Sodium build from Modrinth:

```text
https://modrinth.com/mod/sodium/version/mc26.2-0.9.0-fabric
```

Use the Gradle wrapper from the repository. Do not install a separate Gradle version manually.

## Supported Development Environment

The supported development environment is currently **IntelliJ IDEA on Windows**.

Other editors or operating systems may work for parts of the Java/Fabric build, but they are not the maintained path right now. Two current project details are tied to this setup:

- the `@UsedImplicitly` annotation is used to mark API and reflective entry points for IntelliJ inspections;
- MSDF assets for SVG icons are generated through a PowerShell script instead of being stored as a separate committed asset set.

If you build outside Windows, expect to adjust the MSDF generation step or provide equivalent generated resources locally.

## Normal Build

Build the release jar:

```powershell
.\gradlew.bat clean build
```

The main jar is written to:

```text
build/libs/
```

For a development build that includes the `src/dev` source set and dev metadata:

```powershell
.\gradlew.bat buildDev
```

For a local Minecraft run configuration:

```powershell
.\gradlew.bat runClient
```

Optional runtime mods can be included in the dev runtime with Gradle properties:

```powershell
.\gradlew.bat runClient -PwithOptionalModRuntime=true
.\gradlew.bat runClient -PwithViaFabricPlusRuntime=true
```

## Javet V8 Natives

Combatant uses Javet for the UI script runtime. The Java dependency is bundled by Gradle, and V8 native binaries are resolved separately from:

```text
com.caoccao.javet:javet-v8-windows-x86_64
com.caoccao.javet:javet-v8-linux-x86_64
com.caoccao.javet:javet-v8-linux-arm64
com.caoccao.javet:javet-v8-macos-x86_64
com.caoccao.javet:javet-v8-macos-arm64
```

The current Javet version is defined in `build.gradle`.

Release builds do not require committing Javet native binaries manually. `processResources` runs `unpackJavetNatives`, which copies the resolved native files into generated resources under:

```text
javet/natives/
```

For debugging, or if you need a checked local copy under `src/main/resources/javet/natives`, run:

```powershell
.\gradlew.bat syncJavetNatives
```

The committed README at `src/main/resources/javet/natives/README.txt` intentionally explains this. The actual `.dll`, `.so`, and `.dylib` files are ignored by `.gitignore`.

If Javet fails at runtime, check:

- the build was produced through Gradle, not by manually zipping classes;
- `processResources` ran successfully;
- the produced jar contains `javet/natives/<platform native file>`;
- the platform is one of the bundled desktop targets (Windows x86_64, Linux
  x86_64/ARM64, or macOS x86_64/ARM64). Javet does not currently publish a
  Windows ARM64 V8 artifact.

## Font MSDF Assets

Custom UI text uses compact MSDF atlases for the selected Combatant family.
Missing Unicode runs are delegated to Combatant's bundled Noto-backed Minecraft
font provider. It covers European Latin (including German and Latvian), CJK
(Han, Japanese kana and Korean Hangul), RTL scripts and major Indic/Southeast
Asian scripts. Minecraft creates GPU glyph pages only for characters actually
encountered instead of keeping the full font collection in texture memory.

The bundled icon/symbol atlases can be regenerated after changing their TTF
sources with:

```powershell
.\gradlew.bat generateFontMsdf
```

## MediaPlayerInfo Native Code

Combatant includes a modified Windows native bridge based on Redstonecrafter0/MediaPlayerInfo.

Java integration lives in:

```text
src/main/java/combatant/client/util/media/
src/main/java/combatant/client/util/media/impl/win/
```

Native source lives in:

```text
src/main/native/mediainfo/
```

The runtime DLL resource loaded by `WindowsMediaPlayerInfo` is:

```text
src/main/resources/mediaplayerinfo/natives/win/MediaPlayerInfo.dll
```

If you change `src/main/native/mediainfo/main.cpp` or `main.h`, rebuild the DLL with CMake on Windows, then replace the resource DLL above.

Example build flow:

```powershell
cd src\main\native\mediainfo
cmake -S . -B build -A x64
cmake --build build --config Release
```

Copy the resulting `MediaPlayerInfo.dll` from the CMake build output into:

```text
src/main/resources/mediaplayerinfo/natives/win/MediaPlayerInfo.dll
```

Then run:

```powershell
.\gradlew.bat clean build
```

When editing the native bridge, keep the JNI names in sync with the Java classes:

- `combatant.client.util.media.impl.win.WindowsMediaPlayerInfo`
- `combatant.client.util.media.impl.win.WindowsMediaSession`

If a Java native method signature changes, update the exported JNI function name and parameters in `main.h` and `main.cpp`.

## Publishing To Maven Local

Addon projects can compile against Combatant after publishing it to Maven local:

```powershell
.\gradlew.bat publishToMavenLocal
```

This publishes the `pivosos2007:combatant:<version>` artifact using the version from `gradle.properties`.

## Publishing To GitHub Packages

Combatant can publish the same Maven artifact to GitHub Packages:

```text
pivosos2007:combatant:<version>
```

Repository:

```text
https://maven.pkg.github.com/pivosos2007/combatant-client
```

Publishing is configured in `build.gradle` under the `GitHubPackages` Maven repository.

In GitHub Actions, the included `Publish GitHub Packages` workflow uses the repository `GITHUB_TOKEN` and can be run manually from the Actions tab or triggered by publishing a GitHub Release.

For local publishing, use a GitHub personal access token with `write:packages`:

```powershell
$env:GITHUB_ACTOR = "<github-username>"
$env:GITHUB_TOKEN = "<token-with-write-packages>"
.\gradlew.bat publishMavenJavaPublicationToGitHubPackagesRepository
```

Do not commit tokens or put them in `gradle.properties`.

## Notes

- Generated directories such as `build/`, `.gradle/`, `run/`, and `logs/` must not be committed.
- `local-maven/`, downloaded shaderpack zips, and local debug assets are ignored.
- `to-do-list` is intentionally ignored and should stay local.
