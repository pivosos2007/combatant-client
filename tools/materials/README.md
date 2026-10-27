# Combatant UI PBR materials

The five solid material sets remain opt-in. Existing Ice and the Ice Liquid Glass
visual now use real PBR maps. Theme Editor, MediaPlayer and DynamicIsland are unchanged.

## Sources and license

All five sets were supplied as ambientCG 2K PNG downloads. Files are CC0 1.0:
[ambientCG license statement](https://docs.ambientcg.com/license/),
[full license](../../THIRD_PARTY_LICENSES/ambientCG-CC0-1.0.txt).
Upstream describes these sets as procedural assets; they are genuine downloaded
PBR sets, not new synthetic Combatant replacements or claimed photographic scans.

| Combatant | Source | Official download |
|---|---|---|
| Graphite | [Metal027](https://ambientcg.com/view?id=Metal027) | https://ambientcg.com/get?file=Metal027_2K-PNG.zip |
| Soft-touch Polymer | [Plastic012B](https://ambientcg.com/view?id=Plastic012B) | https://ambientcg.com/get?file=Plastic012B_2K-PNG.zip |
| Satin Titanium | [Metal032](https://ambientcg.com/view?id=Metal032) | https://ambientcg.com/get?file=Metal032_2K-PNG.zip |
| Brushed Metal | [Metal061B](https://ambientcg.com/view?id=Metal061B) | https://ambientcg.com/get?file=Metal061B_2K-PNG.zip |
| Ceramic | [Porcelain001](https://ambientcg.com/view?id=Porcelain001) | https://ambientcg.com/get?file=Porcelain001_2K-PNG.zip |

[SOURCE_AUDIT.md](SOURCE_AUDIT.md) records dimensions, original sample precision,
per-channel ranges/means, edge/interior differences and SHA-256 for every supplied file.
Indexed PNG palettes are decoded before interpreting values. In particular, the
Plastic and Porcelain albedos are indexed RGB, not grayscale intensities.

Graphite has fine powder relief. Polymer has both fine pores and visible scratches.
Metal032 has very small normal variations and smooth tinted albedo. Metal061B has
mottling and irregular scratches, not a strong uniform brushing pattern: the
controllable directional BRDF supplies anisotropy without fabricating a scratch map.
Porcelain has nearly uniform white albedo and extremely subtle normals. NormalGL
is used; NormalDX is redundant with the opposite green-channel convention.
Edge discontinuity is comparable to ordinary neighboring-pixel variation; no
edge inpainting, crop or procedural replacement is applied.

## Shipped layout and quality

`src/main/resources/assets/combatant/textures/ui/pbr/<material>/` contains two KTX1 files:

| File | GPU format | Channels |
|---|---|---|
| `normal_rough_height.ktx` | RGBA16_UNORM | RG: OpenGL tangent normal XY; B: roughness; A: height |
| `color_metal.ktx` | RGBA8_UNORM | RGB: sRGB albedo; A: linear metallic mask |
| `ice/color_ao.ktx` | RGBA8_UNORM | RGB: sRGB ice albedo; A: linear AO (replaces color_metal for ice) |

Both retain the original **2048 x 2048** base resolution, with all 12 mip levels
inside each file. There are **12 material files**, not separate PNGs per mip.
Normal and height precision stays 16-bit. Eight-bit source roughness is carried
in the same 16-bit container; this does not invent source precision. Albedo and
metallic retain their source eight-bit precision (constant binary metallic masks
remain exact). Normal Z is reconstructed from normalized positive-hemisphere XY.

Mip generation averages albedo in linear light, preserves straight RGB even where
the metallic data channel is zero, averages unit normal vectors and renormalizes,
and folds lost normal variance into squared roughness. Height and metallic are
averaged as linear data. NormalGL Y is converted to the UI's downward Y convention
once in the shader; rotation applies to UVs, micro normals and anisotropy together.
KTX records `S=r,T=d`. Texture sampling uses REPEAT in both axes with linear
mip filtering, without atlas gutters or cross-material bleeding.

The shader explicitly decodes albedo and display-encoded scene RGB to linear for
lighting, then encodes its output for the existing UI UNORM composition path.
The source alpha channels contain material data, never surface opacity. Surface
opacity still comes from paint and SDF coverage. The scene pass writes straight
RGB through the existing translucent blend into its own transparent target;
the later composite consumes that target as premultiplied alpha.

Full mip chains add one third to base-level storage. Each resident material uses
approximately **64 MiB**, or **384 MiB** if all six are used. This explicitly
favors source resolution/precision over aggressive UI memory reduction. Sets load
once on first use; resource reload refreshes previously requested sets only.
Client shutdown and backend changes release GPU objects. The JAR compresses the
uncompressed KTX byte streams; no texture processing tool runs in the client.

All 28 originals, including DX duplicates, are preserved locally in
`assets-source/ui-pbr/`, excluded from the client JAR and ignored in Git. Runtime
keeps every useful source signal: Color, NormalGL, Roughness, Displacement, and
Metalness where supplied. Graphite deliberately defaults to dielectric powder
coating even though the source Metal027 metallic mask is constant white. Metal032's
mask is also constant white; Metal061B's rare nonmetallic defects are preserved.
Polymer and Ceramic have no source metallic map and use zero. The deleted
`material_micro_atlas.png` is not restored.

Ice uses the original [Ice 0002](https://www.texturecan.com/details/283/) 2K maps.
The complete archive contains 16-bit PNG OpenGL/DirectX normals and height, plus
8-bit JPG color, roughness and AO. Runtime retains OpenGL normals, height, color,
roughness and AO in two packed files. The six originals are preserved locally in
`assets-source/ui-pbr/ice/`; DirectX normals and the constant opaque normal alpha
are redundant. AO remains independent from color and is sampled as linear data.
The contact sheet [packed-preview.png](packed-preview.png) shows all six sets,
left to right in the source table order followed by ice; rows are albedo,
reconstructed normal RGB, roughness and height. This preview is 8-bit; runtime
normal/height data is 16-bit. Ice edge differences are approximately 1.1–1.8 times
interior differences; no invented edge corrections were applied.

## Java API and GPU payload

```java
var base = UiSurfaceMaterialSpec.BRUSHED_METAL;
var pbr = base.withMicrotexture(base.texture()
        .withTileSize(256f)
        .withNormalStrength(0.8f)
        .withHeightStrength(0.12f)
        .withRoughnessVariation(0.7f)
        .withMetallic(1f)
        .withSpecularStrength(0.8f)
        .withTintStrength(0.3f)
        .withAnisotropy(0.8f)
        .withDirection(30f))
    .withParameters(base.detailScale(), 0.45f, base.bevelStrength(), 0f)
    .withLighting(UiMaterialLightingSpec.CAPTURED
        .withStrength(0.65f).withSamplingRadius(20f));
```

Paint RGB is the tint; `tintStrength=0` preserves source albedo, `1` uses paint RGB.
`detailScale` remains the legacy procedural-detail control. PBR uses independent
`tileSizePx` in surface-local logical pixels, so resizing doesn't stretch the maps.
All values are finite and clamped by Java. Existing four-argument texture and
legacy surface constructors remain available; the original five presets remain
untextured until explicitly enabled. Ice always requires its PBR set.
`base.texture()` supplies material-specific
defaults; `UiMaterialTextureSpec.DEFAULT` is a generic dielectric profile.

| Payload | Meaning |
|---|---|
| Params5.zw | scene strength, sample radius (0..1, 0..32 logical px) |
| Params6 | mode, legacy detailScale, roughness (0..1), pointer response |
| Params8 | tile (16..1024), normal strength (0..2), height strength (0..1), roughness variation (0..1) |
| Params9 | metallic (0..1), specular (0..2), tint blend (0..1), anisotropy (0..0.95; metals) |
| Params10.x | direction in degrees, wrapped to [0,360) |

Powder coating suppresses gloss and uses rough diffuse response; polymer has a
wrapped diffuse response and a broad soft specular lobe; metals use colored
Fresnel and anisotropic GGX; ceramic adds a controlled dielectric coat. Height
perturbs the lighting normal, not merely the material's brightness. Framebuffer
lighting samples a roughness/direction/normal-dependent low-frequency scene probe.
It remains a screen-space approximation, not a physical environment cubemap.

Every draw lowering path binds both PBR samplers and UIGlassFrame. All four
textured/untextured/scene combinations support analytic clipping and share one
vertex layout. Requested PBR has **no procedural fallback**: missing/malformed
maps, failed GPU allocation/upload, or unavailable required scene resources raise
an explicit rendering error. Explicitly selected untextured legacy modes remain
separate modes; they are never substituted for failed PBR. Scene reads use shared
snapshots and a separate material output; no read/write feedback is introduced.
Consecutive compatible nonoverlapping scene surfaces share a batch/capture.
Different maps, scene sources, clipping or overlapping surfaces split batches;
CURRENT_TARGET preserves painter ordering. CAPTURED reuses the world snapshot.

Ice shares one texture binding between solid and glass draws. Its source normal
drives dielectric highlights and glass displacement; source height contributes
to the normal, roughness controls the lobe, AO controls crack attenuation, and
source color controls tint/transmission. Procedural Voronoi/noise cracks were
removed. SDF edge shaping remains analytic. `UiIceMaterialSpec.withTexture(...)`
sets tile size, normal/height strength, roughness variation, specular, tint and
rotation for both `asSurface()` and `UiLiquidGlassMaterial.withIce(...)`.
The old eight-argument ice constructor remains available. Density scales tiling
relative to the default 2.2; seed translates UV without changing derivatives.
Ice tiling uses physical surface pixels, consistent with glass displacement.
Metallic and anisotropy apply to the five-material API, not dielectric ice.
Glass uses its existing shared clean/blur scene resources and does not add copies.
Solid ice also supports the existing optional scene lighting source/strength/radius.

## Reproduction and checks

Run the offline tool only when changing source assets:

```powershell
java -Xmx2G tools/materials/PrepareUiPbr.java assets-source/ui-pbr src/main/resources/assets/combatant/textures/ui/pbr tools/materials
.\gradlew.bat test --tests '*UiMaterial*' --tests '*UiIce*' --tests '*UiGlass*'
.\gradlew.bat build
```

The normal build consumes already packed resources. The retained checks cover
OpenGL/Vulkan shader compilation with and without analytic clipping, complete KTX
chains and formats, indexed-color regression, malformed-map rejection and API
ranges. No GPU unit-test framework or GLFW dependency is added. See the final
handoff for the checks actually run and runtime limitations.


## Agent A: opt-in draggable HUD surfaces (caju stage)

Implemented one `Surface material` selector for the 23 draggable HUD widgets that
render a configurable panel or background: Potions, Cooldowns, Admins, Keybinds,
Inventory, Statistics, FPS, Ping, TPS, Memory, Coordinates, SpeedBps, GameTime,
SystemTime, Armor, Itemizer, Triangulator, Radar, Scoreboard, ModuleList,
TimerIndicator, TargetHUD and HudNotifier. `Default` retains the original drawing
path. MediaPlayer and BetterChat retain their existing specialized optical/chat
paths rather than being implicitly re-skinned.

Selectable profiles are the existing unified materials: Graphite, SoftTouch,
Ceramic, SatinTitanium, BrushedMetal, Ice and PhosphorLED. No widget-specific
PBR shaders or exposed normal/roughness controls were added. Selected PBR sets
participate in the existing prewarm mechanism, without loading unused sets.

The Java-backed HUD widgets replace only their panel fill and leave the legacy
shadow, blur, strokes, text, icons and bar/indicator visuals unchanged. Shared
scripted widgets pass `materialMode` through their structural cache signatures;
changes in live text/values do not trigger a material re-bake. `rounded-corners`
material drawing reuses the common eight-vertex convex primitive with deduplicated
zero-radius corners and independently preserves legacy rounded stroke/blur.

**Geometry limitation:** the current eight-vertex material primitive approximates
large round corners with clipped diagonals and a soft SDF bevel; the default
(non-material) path continues using exact rounded-corner rendering. Pixel-perfect
true circular independent-corner material clipping requires a dedicated common
rounded-SDF material path; this stage does not claim that feature is complete.

**Scope:** no climate effects, BC/RHI changes, Theme Editor or theme example
modifications, and no forced use of any material. Shader correctness in the full
Minecraft client still needs visual verification.


## Agent A: weather-aware materials, chat clipping and precipitation film (cajp stage)

- `HudClimateSurface`: effective `Level.getRainLevel(1)` and `isRainingAt(eyePos)` honour active `WorldTweaks` weather overrides, while still rejecting cover and snow biomes for rain. Snow checks biome and same visibility/heightmap.
- `BetterChatRenderer`: the message scissor now encloses message **materials and glass bubbles**, not only text. The analytic shape clip remains authoritative for rounded corners.
- `ModulesMenuScreen`: the existing category/control/search Liquid Glass passes always sample the current climate visual, independently of the draggable HUD environmental toggle.
- `Renderer2D.climatePrimitive` / `UiMaterialClimateSpec`: new non-glass material film on the existing analytic material vertex layout. HUD material choices and JS rounded surface materials may use it without scene capture, extra textures, or RHI changes.
- Rain shader rewritten with variable-size droplets, continuous falling streaks and lens/rim response. Water-exit drops use separate size, velocity and population.

**Validation (cajp stage):** `UniformCodegen discover` completed (50 layouts), Mesa/OpenGL compiled the climate-glass fragment and all four material fragments, and four complete material vertex/fragment pairs linked. All changed Java sources passed JDK syntax parsing; `UiMaterialClimateSpec` compiled with `javac`. Full Gradle compile and in-client render tests were unavailable: the uploaded archive lacks `gradle/wrapper/gradle-wrapper.jar`. The generated patch includes JUnit input-validation tests, not yet executed in the Minecraft test runtime.

**Actual integration scope:** modules category controls, active indicator, panels and search glass respect the effective WorldTweaks-aware weather. HUD `Default` fills retain their original appearance and receive a procedural surface film only while scoped to an opted-in HUD. Glass layers retain one climate pass (no double painting). Scripted HUD shells with `materialMode` participate independently of their selected material. `UiBackdropRuntime` also applies climate to scoped scripted HUD Liquid Glass while preserving explicit ICE visuals. The film is SDF clipped and uses existing material vertex payload. No changes to BC textures, RHI layouts or Theme Editor.

**Open work:** precipitation is a deterministic analytic falling-drop/runnel approximation, not a persistent fluid simulation: true droplet merging, surface-specific contact angle and retained water masks still need implementation. Visual performance, scissor state with UI-underlay glass and rainfall intensity must be checked in-game.
