# Third-party notices

This file accompanies Combatant's source-level credits. File-level provenance headers identify adapted code where applicable.

## ambientCG UI material textures

Combatant bundles packed derivatives of ambientCG Metal027, Plastic012B, Metal032,
Metal061B and Porcelain001 (supplied 2K PNG sets), under CC0 1.0 Universal.
Source URLs, input SHA-256 fingerprints, channel layouts and conversion details:
`tools/materials/README.md` and `tools/materials/SOURCE_AUDIT.md`.
License: `THIRD_PARTY_LICENSES/ambientCG-CC0-1.0.txt`.
Runtime files: `assets/combatant/textures/ui/pbr/{graphite,soft_touch,satin_titanium,brushed_metal,ceramic}/*.ktx`.

## GFBS-glTF 1.5.1

Combatant includes source adapted from GFBS-glTF 1.5.1, commit `8906d4083c23b5d5bbf5191a0555d149a5272900`.
Copyright (c) 2026 LytharaLab. Licensed under MIT.
See `THIRD_PARTY_LICENSES/GFBS-glTF-MIT.txt`.

## JglTF 2.0.4

Combatant bundles JglTF parsing/model libraries by Marco Hutter / javagl.de.
Licensed under MIT. See `THIRD_PARTY_LICENSES/JglTF-MIT.txt`.

## Jackson 2.13.x

JglTF and the adapted glTF importer use Jackson JSON components.
Jackson is licensed under Apache License 2.0. See `THIRD_PARTY_LICENSES/Jackson-Apache-2.0.txt`.

Additional third-party components and provenance are documented in `CREDITS.md` and the existing files under `THIRD_PARTY_LICENSES/`.

## liquidGL 3.0.0

Combatant's reactive glass optics adapt the SDF deformation, edge-factor and refraction logic from
liquidGL 3.0.0, commit `88f681ab7035fd55b04f63edff1841e32c4199e9`, `scripts/liquidGL.js`.
Copyright (c) NaughtyDuk. Source code is licensed under MIT. No liquidGL assets are included.
See `THIRD_PARTY_LICENSES/liquidGL-MIT.txt`.

## Khronos glTF Sample Assets — BoomBox dev fixture

`dev/resources/assets/combatant/models/dev/boombox.gltf` and its associated buffer and textures
are the Khronos BoomBox sample asset. They are public-domain/CC0 material and are included only
in the development source set for imported PBR geometry testing.
Source: https://github.com/KhronosGroup/glTF-Sample-Assets/tree/main/Models/BoomBox
