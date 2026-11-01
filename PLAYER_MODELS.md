# PlayerModel and community collections

Visuals → Cosmetic → PlayerModel → VisualPreview. The module, renderer, animation and selector stay in Combatant.
Models, texture variants, icons and fitting profiles live in separate addons or resource packs.
The supplied collection is `../sc-armor-addon`: 28 geometries and 44 appearances, with 256×256 icons.
The client no longer bundles this artwork or relies on a local source directory.

## Quality

- Maximum texture dimension: 512, 1024, 2048 (default), Original (up to 4096).
- Filtering: Nearest or Linear (default), applied to both minification and magnification.
- Mipmaps: on by default; complete chain down to 1×1. Disabled uploads only the selected base resolution.

PNG decode, resolution reduction and mip generation run on the loader thread. Mips use alpha-weighted
linear-light color averaging; odd-size images retain their last rows/columns. GPU uploads use RenderPearl's
backend-neutral API and the texture's sampler. Changes select a new texture cache key, while geometry is reused.
The same texture is used in the world and VisualPreview. These settings are persisted with the module.

## Add a collection (format 1)

Create `assets/<your_namespace>/combatant/player_models/<collection>.json` in a Fabric resource addon
or resource pack. The loader discovers all namespaces automatically. No Java API, module registration,
client fork or hardcoded list is required. A resource-only Fabric addon needs `fabric.mod.json` and its
`assets` directory; install its JAR alongside Combatant. The separate collection project provides the build.
For an ordinary resource pack, use the pack metadata required by the target Minecraft version.

```json
{
  "format": 1,
  "entries": [
    {
      "id": "armor",
      "name": "Armor",
      "model": "models/armor/model.obj",
      "atlas": "textures/armor/base.png",
      "texture": "textures/armor/base.png",
      "icon": "textures/armor/icons/base.png",
      "profile": "models/armor/fitting.json"
    },
    {
      "id": "armor_blue",
      "name": "Armor · Blue",
      "model": "models/armor/model.obj",
      "atlas": "textures/armor/base.png",
      "texture": "textures/armor/blue.png",
      "icon": "textures/armor/icons/blue.png",
      "profile": "models/armor/fitting.json"
    }
  ]
}
```

The full appearance IDs become `<your_namespace>:armor` and `<your_namespace>:armor_blue`.
`id`, `model`, `atlas` and `texture` are required. `name`, `icon` and `profile` are optional.
Unqualified resources resolve in the catalog's namespace; explicit `namespace:path` references are supported.
Appearance IDs must belong to the catalog namespace. `atlas` must match the default texture referenced by
OBJ/MTL; the loader substitutes `texture` for the selected appearance. Use one atlas per model.
Variants share the same model path and fitting profile. Different profile paths get separate geometry cache entries.
Use PNG textures up to 4096×4096 and icons up to 256×256.

Each catalog is limited to 256 entries and 512 KiB. Duplicate IDs, invalid paths and unsupported versions reject
that catalog with a logged diagnostic; other valid catalogs continue loading. Pack priority can override the
same catalog resource. Distinct catalog files merge in sorted resource-ID order; duplicate IDs across them
reject the later catalog. An empty collection is supported.
Resource reload and the selector's Reload collection action invalidate discovery and geometry/texture caches.
Without addons, the selector displays installation guidance and rendering stays vanilla.
Saved unavailable model IDs remain in config, so re-enabling the pack restores the selection.
Legacy unqualified IDs migrate only when they match exactly one installed appearance.

## Authoring geometry

Supported replacement format: rigid OBJ groups with UVs and normals, one shared armor atlas.
Provide head, chest, both upper arms and both thighs, using standard group names `headModel`, `bodyModel`,
`leftArmModel`, `rightArmModel`, `leftLegModel`, `rightLegModel`, or map your own group names in the profile.
Every additional group must map to a bone. Keep disconnected rigid attachments in the appropriate group;
use profile blend regions for a hose that bridges moving body parts.
The complete replacement limit is 500,000 vertices. Models do not automatically acquire authored skeletal
animations or arbitrary multi-texture materials. Transparent surfaces use material names containing `glass`.

## Rendering and fitting

The existing OBJ importer, material bridge and geometry compiler feed the existing player rig CPU renderer. Submission uses Minecraft custom geometry, textures and render types, compatible with the shared OpenGL/Vulkan rendering path; no direct OpenGL API is introduced. Geometry is cached across texture variants; live resources have bounded caches and texture cleanup waits until deferred snapshots release ownership.

Only the local player's third-person render state receives a replacement snapshot. An asset must finish importing, compiling and uploading before vanilla geometry is suppressed. The entity renderer still handles transforms, invisibility, overlays, outlines, shadows and nameplates. Armor, trims, capes, wings, custom head geometry, ears and stuck-projectile geometry are suppressed for the replaced state; held items remain in their existing layer. First-person hands are unchanged. Each submission freezes its own pose. ViewModel is not required.

All OBJ faces and material groups are preserved. Source coordinates already use Minecraft model pixels and +Y down. Arm and leg surfaces connect to forearm/shin bones. Small disconnected plates stay rigid; long connected surfaces crossing a joint blend continuously. Torso attachments, including cylinders and tubes, keep their original group attachment by default.

`fitting.json` supports exact source-group bone overrides and bounded flexible regions. Coordinates below are in source pixels. A region blends between the two rig bones along its endpoint vector, and must encompass the entire flexible surface including its attachments. It does not add or delete faces. Example schema (not a validated profile for a particular hose):

```json
{
  "bones": {"bodyModel": "CHEST"},
  "blends": [{
    "group": "bodyModel",
    "fromBone": "CHEST", "toBone": "HEAD",
    "min": [-6, -8, -7], "max": [6, 1, 8],
    "from": [0, 1, 0], "to": [0, -8, 0]
  }]
}
```

Bone overrides disable automatic limb articulation for that source group. Pack authors can tune profiles without changing OBJ geometry. The supplied collection retains source attachments; flexible hose regions remain model-specific authoring work. Source atlases are unchanged on disk. Runtime resolution and filtering are controlled by the module.


## Selector implementation

Production components are under `ui/api/components/player-model-spectrum/`, ported from the supplied local
native Adobe Spectrum 2 kit's foundation, tabs, button and action-button. Cards retain its surface/state
geometry with an image/list layout. Colors are passed from Theme each frame, with readable accent foreground;
font family is Combatant Onest. No dev imports or additional UI framework are required.
Camera hints are constrained to the middle scene area, outside the selector and settings.

## Validation commands

```powershell
.\gradlew.bat test --tests 'combatant.client.features.playermodel.*'
.\gradlew.bat test --tests 'combatant.client.features.playermodel.*' -PplayerModelAddon=D:/ownmod/sc-armor-addon/src/main/resources
node tools/dev/test_player_model_selector.mjs
```

The external collection integration test is opt-in; core tests do not depend on a sibling checkout or bundled art.
It imports all models, preserves face counts and checks rig weights, PNGs and icon limits.
No game launch/capture is part of this task, per the user's instruction. Visual parity is not asserted.
