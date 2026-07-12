# Shaderpack Patches

Combatant includes Iris shaderpack patch manifests for a small set of shaderpacks. These patches are used to integrate Combatant visual features with shaderpack pipelines without requiring users to edit shaderpack files manually.

Patch manifests are registered from:

```text
src/main/resources/assets/combatant/shaders/iris-patches/index.json
```

Patch files live under:

```text
src/main/resources/assets/combatant/shaders/iris-patches/
```

## Supported Patch Manifests

| Shaderpack | Manifest id | Targeted shaderpack version | Manifest verified Minecraft | Manifest verified Iris | Modrinth |
| --- | --- | --- | --- | --- | --- |
| Complementary Shaders - Reimagined | `complementary_reimagined.r5` | `r5.6.1` | `1.21.11` | `1.10.7+1.21.11-fabric` | https://modrinth.com/shader/complementary-reimagined/version/r5.6.1 |
| Photon Shaders | `photon.v1_3b` | `v1.3b` | `1.21.11` | `1.10.7+1.21.11-fabric` | https://modrinth.com/shader/photon-shader/version/v1.3b |

The client currently targets Minecraft 26.2. The manifest verification fields describe the shaderpack versions and loader environment the patch payloads were last structurally checked against. A shaderpack may advertise broader Minecraft compatibility on Modrinth than the specific manifest verification entry inside Combatant.

## Patched Features

Complementary Reimagined `r5.6.1` patches currently cover:

- fullbright;
- WorldTweaks fog;
- translucent SSR fog;
- underwater fog;
- shaderpack motion blur suppression.

Photon `v1.3b` patches currently cover:

- fullbright;
- WorldTweaks fog;
- underwater fog;
- shaderpack motion blur suppression.

## How Patch Matching Works

Combatant first gates patch application by the active Iris shaderpack name, then checks structural probes against the loaded expanded GLSL.

This means:

- the shaderpack name must match the manifest's `packNameRegex`;
- the expected GLSL target paths must exist;
- patch markers prevent duplicate injection;
- newer shaderpack versions may work if their structure is still compatible, but they are not treated as verified until the manifest is updated.

## Validation

Use the Gradle validation task to compile bundled patch manifests against a shaderpack zip:

```powershell
.\gradlew.bat validateIrisPatches -Pshaderpack=<path-to-shaderpack.zip>
```

Run this when updating a patch manifest, changing patch payloads, or bumping the verified shaderpack version.

## Adding Or Updating A Patch

1. Add or update the manifest under `src/main/resources/assets/combatant/shaders/iris-patches/<shaderpack>/<version>/manifest.json`.
2. Add the manifest path to `src/main/resources/assets/combatant/shaders/iris-patches/index.json`.
3. Keep `verifiedShaderpackVersions`, `verifiedMinecraftVersions`, and `verifiedIrisVersions` aligned with the shaderpack zip actually tested.
4. Run `validateIrisPatches` against the target shaderpack zip.
5. Test in-game with Iris enabled.
