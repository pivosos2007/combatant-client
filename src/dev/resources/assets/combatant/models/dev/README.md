# Imported-model dev fixture

`boombox.gltf` and its external buffer/textures are the Khronos glTF Sample Assets **BoomBox**
model. This is the default full PBR + normal + emissive + Photon integration fixture. Its original
centimetre-scale coordinates are preserved; `ImportedModelTest` defaults to scale `100` so it appears
at a useful Minecraft-world size. Resource file names were normalized to lowercase for Minecraft
identifier compatibility; model and image contents are otherwise the upstream glTF variant.

- Source: https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/BoomBox
- License: CC0-1.0 / public domain
- Upstream payload SHA-256:
  - `boombox.bin`: `f6c61a95af3e0e2462a9ac01d1e681706149452e0191930e0591a81d50b5b944`
  - `boombox_base_color.png`: `099816a7afc5f6690494313ac8039806fd6d5b84179126a808b2678aaab3563a`
  - `boombox_normal.png`: `c9a7904e7f25246ac47f86c337cfd4ec8e103fff83e07d3af472e5c620ec6f27`
  - `boombox_occlusion_roughness_metallic.png`: `496704a4836ff364dc4441f41651edb25178498926c859933e33df42d6361412`
  - `boombox_emissive.png`: `e9970da7010591b73070151fe5039a158413499e38300d14106e367472c03b5b`

## Additional format acceptance fixtures

`backend_sample.obj` + `.mtl` + `.png`: an OBJ quad with negative indices, UVs and
PBR MTL properties, imported by the shared `ModelImporters` registry.

`combatant:geckolib/models/dev/backend_sample.geo.json`: GeckoLib 5 Blockbench
geometry with a rotated child bone and matching
`combatant:textures/dev/backend_sample.png`. GeckoLib is bundled as a nested Fabric mod in the Combatant JAR. The dev `ImportedModelTest`
module's `asset` setting accepts all three formats. For OBJ and GeckoLib models,
set the module scale to ~1; the historical glTF boombox fixture uses scale 100.

Blockbench should export **GeckoLib .geo.json**, not native `.bbmodel` projects.
By default, this importer searches `foo.png` beside `foo.geo.json` and
`textures/<path>/foo.png` for assets in `geckolib/models/<path>`.
For a nonstandard texture location, set a top-level `"combatant:texture"` field
containing its fully qualified Minecraft resource identifier. If no image can be
located, the mesh uses a white material rather than failing to load.

Current scene submission uses the shared CPU geometry, material and GPU residency pipeline. Animation clip
playback is not yet implemented even for glTF, so GeckoLib `.animation.json`
tracks are not imported or played by this backend; this is a geometry/material
backend, not a GeckoLib animation-controller replacement.
