## 0.4.0

> [!WARNING]
> HUD scale baselines have changed. Existing HUD configs will likely need adjustment after updating: review element scales and positions, especially TargetHud, Inventory, Itemizer, and compact statistics. A scale of `1.0` now represents the default size of the affected elements.

### Dependencies

| Required component | Version |
| --- | --- |
| Minecraft | 26.3 |
| Java | 25 |
| Fabric Loader | 0.19.5 or newer |
| Fabric API | 0.163.0+26.3 |
| Sodium | 0.9.2 for Minecraft 26.3 |
| GeckoLib | 5.5.7 or newer; 5.5.7 is bundled with Combatant |

Javet 6.0.3 and its platform-specific V8 natives are bundled; no separate installation is needed.

### Optional dependencies and integrations

The versions below are the current integration targets. Use the Minecraft 26.3 Fabric builds.

| Optional mod | Version | Integration |
| --- | --- | --- |
| Iris | 1.11.7+26.3-fabric | Shaderpacks and shaderpack rendering integration |
| ImmediatelyFast | 1.17.1+26.3-fabric | Rendering compatibility |
| ViaFabricPlus | 5.1.1 | Protocol-aware combat and interactions |
| Entity Culling | 1.11.3 | Visibility compatibility for shader ESP |
| Xaero's Minimap | 26.6.0 | Minimap, waypoints, and map UI |
| Xaero's World Map | 1.47.0 for Fabric 26.3 | World-map UI and waypoint integration |
| XaeroLib | As required by the installed Xaero mods | Shared Xaero UI integration |
| Litematica | 0.29.1 | Printer and active schematic placements |
| MaLiLib | 0.30.2 | Required when using Litematica |
| SeedCrackerX | 2.16.2 | Seed recovery, generation profiles, and shared seed state |
| More Culling | No version pinned | Optional culling compatibility hook |
| Sodium Extra | No version pinned | Suggested optional companion mod |

### Schematics and automation

- Added a **Schematics** library with search, material counts, a rotatable 3D preview, layer selection, and `@schematic` access.
- Added readers for Litematica `.litematic`, Sponge `.schem` v1–v3, and vanilla structure `.nbt` files. Schematics support origin offsets, rotation, and mirroring.
- Added **SchematicProjection** to mark missing, incorrect, and matching blocks in the world.
- Added **SchematicBuilder** with layer-by-layer placement, block-state orientation, walking, climbing, bridging, nearby-chest restocking, and optional removal of incorrect blocks. Temporary supports can be removed after use.
- Added redstone construction phases, follow-up block-state adjustments, and reporting for blocked or approximate placements.
- Added **Printer** for placing blocks from an active Litematica schematic. Placement range, blocks per tick, delays, navigation, and restocking are configurable. Litematica is required.
- Added a shared action scheduler for movement, rotation, inventory, and interaction tasks. Actions support sequencing, parallel execution, retries, and timeouts. Owned controls are released on cancellation, module disable, disconnect, world change, and panic.
- Added placement tracking against server block updates, bounded placement retries, and a built-in walking navigator.

### Combat

- Added **AnchorMacro** and **DoubleAnchor** for automated respawn-anchor placement and detonation sequences with damage checks.
- Added **AutoHitCrystal** to place obsidian, place an end crystal, and attack it. Supports single and repeated sequences, separate action delays, and hotbar restoration.
- Added **CrystalOptimizer** to hide attacked end crystals locally while awaiting server updates. Unconfirmed removals can be reverted; confirmation timing can use the current ping.
- Added **InstaCart** with rail and TNT-minecart placement, bow charging, target selection, and minimum target-damage and maximum self-damage thresholds.
- Added **Burrow** for jump-assisted defensive block placement with block priority, rotation settings, delayed slot restoration, and auto-disable.
- Added **PearlCatch** to sequence an ender pearl and wind charge, with configurable delay, downward aiming, inventory search, and slot restoration.
- Added **AntiMiss** to suppress manual attacks without a valid hit, including spear misses. An optional fake swing preserves the local hand animation for swords and axes without interrupting a spear's active use.
- Added **HitSelect** to filter attacks using target hurt time, hit probability, and delay.
- Added **JumpReset** with configurable damage-trigger conditions and cooldown. Added **ShiftTap** for timed sprint-reset inputs after attacks.
- Added **RightClickShield** for temporary shield switching when right-clicking with selected weapons. Added **HoverTotem** for moving totems from inventory slots to the selected destination on hover.
- Expanded **SpearAssist** with automatic recharging after kinetic attack windows expire and **Dual Attack** while using a spear in the offhand. Legit mode releases the spear before a main-hand attack; Keep Use preserves spear use.
- Expanded shared target filters with player relations, Bedwars teammates, unclassified players, armor state, hostile/neutral/friendly mobs, marked entity types, and invisible targets. Player, mob, and marked-type filters can be enabled independently.
- Reorganized target settings into separate groups, with irrelevant and conflicting options hidden. Updated **DefineTarget** to distinguish marked entity types from player relations.
- Changed **KillAura** attack scheduling around critical-hit eligibility and weapon cooldown. SNAP rotation remains engaged while an eligible attack is pending.
- Updated **Criticals** sprint handling, jump and landing recovery, and Vulcan 2.9.7 behavior for levitation and slow-falling effects.
- Changed server-visible swing and attack packet sequencing in automated combat and mining paths.
- Updated placement geometry, reach and rotation checks, and interaction ordering in **AutoCrystal**, **AutoAnchor**, **AutoBed**, **Surround**, **SelfTrap**, **AutoTrap**, **HoleFill**, and **AutoWeb**.
- Improved **ElytraTarget** firework timing, pursuit, and attack coordination with **KillAura**, **AttributeSwap**, and **SuperFirework**.
- Added **Mace Jumper** to **ElytraTarget**: restore flight after a smash, climb, turn, and dive again at a stable ground target. Repeat height, delay, count, target movement limits, and cycle timeout are configurable.
- Improved pursuit target retention during climbs and repeat dives. Added flight-route checks and short overpass routes around obstacles; submerged targets and targets without a clear approach are skipped.
- Improved automatic takeoff and post-smash flight recovery with headroom checks, retried jump/glide inputs, and coordination with pending equipment changes.
- Reworked emergency flight avoidance to account for momentum, rocket acceleration, collision clearance, and available escape routes. Emergency steering takes priority over normal pursuit and attack rotations.
- Reworked **AttributeSwap** mace preparation and equipment restoration around predicted contact and actual smash readiness. Added configurable preparation lead time in ElytraTarget and prevented regular KillAura attacks from interrupting an active elytra/mace cycle.

### Movement and player controls

- Added **EntityControl** with mounted-entity steering, boat heading modes, air control, and speed controls. Added **EntityFlight** with normal and Vulcan flight modes for ridden entities.
- Added **Jesus** for configurable movement on liquid surfaces and **LongJump** with slime, shulker, boat, and water-assisted launch modes.
- **TridentBoost** for trident propulsion, and **LevitCtrl** for levitation and slow-falling movement control.
- **AutoJump** for automatic jumping.
- Added **SnapTap** to prioritize the most recently pressed opposing movement key, independently for horizontal and vertical input.
- Changed **ElytraFly** firework use and inventory-switch sequencing, including deferred hotbar restoration. Updated **ElytraHelper** to coordinate with shared inventory actions.
- Unified **SuperFirework** acceleration between independent flight and ElytraTarget steering, so pursuit prediction uses the selected firework mode and speed.
- Improved coordination of firework use, hotbar changes, and chest-slot swaps during flight, including pending-action retries and restoration after interrupted attacks.
- Changed **SafeWalk** edge protection to use server-visible movement inputs, including handling for diagonal movement near edges.
- Fixed **LiquidInteract** placement targeting interfering with attacks: fluid-only hits no longer become mining requests for water or lava.
- Corrected **Timer** energy thresholds so boosts require enough energy for the next use and the HUD reports the same readiness condition.
- Updated **NoFall** landing selection and slime-block MLG planning.
- Added **Accurate Placement** to **NoDelay**, with rotation and no-rotation handling, input tracking, server confirmation options, and configurable reset behavior. Updated fast-break timing.
- Reworked **Offhand** fallback priorities, swap coordination, and inventory-based totem refilling while retaining existing health, threat, and consumption rules.
- Added **ClickThrough** to reach containers behind configured signs, banners, and item frames. Added **NoEntityTrace** to prioritize block targeting when using selected items.
- Added **EnderChestPlus** with cached ender-chest contents, item tooltips, and `@echest`.
- Added **ReBuffNotifier** for expiring potion effects and **ShulkerDropper** for queued shulker-box disposal with optional shop actions.

### HUD and interface

- Reworked the main-menu background with pointer-reactive honeycomb cells, cursor trails, crystal layers, and fluid/smoke animation. Added settings for color, intensity, bloom, and simulation quality.
- Extended HUD glass settings with refraction, chromatic aberration, magnification, bevel geometry, viscosity, optical thickness, refractive index, roughness, frost, and thin-film controls.
- Added HUD surface materials including graphite, soft-touch, ceramic, satin titanium, brushed metal, ice, phosphor LED, and Voronoi patterns.
- Added HUD material responses to rain, snow, frost, humidity, heat, and leaving water, using biome and world conditions.
- Added **KeyStrokes** with movement and mouse-key displays, clicks per second, press animations, and layout settings.
- Added **Gauges** with bar and segmented layouts for module-provided HUD states, replacing **TimerIndicator**. Readiness markers follow each module's current resource cost and limits.
- Updated layouts and material selection in **TargetHud**, **Armor**, **Inventory**, **Scoreboard**, **Radar**, and **ModuleList**.
- Normalized scale controls across TargetHud, Inventory, Itemizer, Triangulator, and compact statistics. Existing HUD layouts may need scale and position adjustments.
- Adjusted **BetterTooltips** default sizes and blur strength, updated **SwapTooltip** glass defaults, and added a glass background to ClickGUI search.
- Extended theme color usage and text handling across ClickGUI settings, manager screens, notifications, and tooltips. Added English and Russian strings for the new modules and controls.

### Rendering, models, and sound

- Added an HDR scene-color target with tone-mapping and composition passes for Combatant rendering effects.
- Expanded **ReimaginedVisual** with configurable volumetric clouds, distant cloud layers, cirrus, cloud coverage and density, and sky colors derived from the active theme, WorldTweaks, or custom values.
- Added procedural rain with density and render-distance controls, retaining vanilla precipitation when the custom path is unavailable.
- Added **PlayerModel** for cosmetic models assigned separately to self, friends, enemies, staff, and other players, with model transforms, texture filtering controls, optional first-person hand replacement, Iris integration, and shader ESP silhouettes.
- Extended imported-model rigging and animations for movement, poses, and item-use actions.
- Added **PhysicSound** with underwater filtering, cave echo, and sound occlusion through the existing sound engine.
- Expanded shader rendering for block highlights, holes, jump circles, trails, targets, and totem effects.
- Updated scene-depth handling and Iris integration for world effects and imported models.
- Improved world-overlay and wide-line precision at large coordinates, including high and low Y positions, by keeping geometry relative to the camera.

### ESP, finders, and seeds

- Added **StorageESP** with per-container filters, colors, tracers, distance limits, and dimming for previously opened containers.
- Added **Skeleton** for player-bone overlays, **BedrockEsp** for bedrock-area scanning, and **BlockNotifier** for nearby configured blocks.
- Expanded **ChunkFinder** checks for player activity, underground light, redstone changes, and spawners. Added glow and shader display options to finder rendering.
- Added a below-Y=0 filter to **LightFinder**.
- Added **OrePredictor** for seed-based diamond, emerald, gold, and ancient-debris locations, with optional marking of previously mined areas.
- Added optional **SeedCrackerX** integration, including `@seed`, shared seed state, generation-profile selection, and server-hash handling.
- Added local waypoint management through `@waypoint` and vanish detection in the existing **Admins** feature.

### Server utilities and configuration

- Added **AutoAccept** for trusted teleport requests, **AutoTpa** for repeated teleport requests with response matching, and **AutoRtp** for repeated random teleport attempts with distance and nearby-player conditions.
- Added **AutoLeave** with health, player-proximity, staff, and chat triggers. Added **DeathCoordinates** for recording death positions and **CordSnapper** for copying or sending formatted coordinates.
- Added **AntiCheatDetect** with confidence-based server checks, **NoInvLeak** for masking configured inventory and server information, and **ServerRPSpoofer** for configurable resource-pack response packets.
- Added **OrderDropper** for configurable order-menu item delivery and **PanicPay** for balance lookup and automatic payment commands.
- Added **AutoAuth** with trusted-server restrictions, configurable login/register commands, and retry limits. Password settings are excluded from config exports.
- Added share codes for profiles and module settings, import previews, file selection, and server/world-to-profile bindings.
- Added atomic configuration writes, `.bak` recovery, `.corrupt-*` quarantine for invalid files, coalesced saves, and shutdown flushing.
- Added configuration-directory migration with locking and recovery for interrupted moves. Saved profiles include their client version.
- Added automatic saving of edits to server/world-bound profiles. Pending edits are saved before switching profiles or worlds, while settings unavailable in the current session are preserved.
- Added import-size and nesting limits for shared configs and schematic files.

### Compatibility and internal changes

- Updated **Javet** from `5.0.9` to `6.0.3` and added Android ARM64 V8 native packaging and Android-specific runtime selection.
- Updated **Entity Culling** from `1.10.5` to `1.11.3` and adjusted **More Culling** compatibility hooks for changed method signatures.
- Changed shader and native-buffer cleanup during resource reloads and module shutdown.
- Reduced duplicate HUD layout and text measurements, repeated world-projection calculations, transient render allocations, and unused full-frame backdrop copies.
- Updated cleanup of queued packets, pending inventory actions, and automation controls after disconnects and module shutdown.

### Removed and consolidated

- Removed the global `AntiCheatPreset` system. Modules now expose their own protocol and rotation settings.
- Removed **AvoidHazards**.
- Replaced **TimerIndicator** with **Gauges**.
- Added static-hitbox options to the existing **Hitbox** module.
