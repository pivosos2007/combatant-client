## 0.4.0

### Schematics and automation

- Added a **Schematics** library with search, material counts, a rotatable 3D preview, layer selection, and `@schematic` access.
- Added readers for Litematica `.litematic`, Sponge `.schem` v1–v3, and vanilla structure `.nbt` files. Schematics support origin offsets, rotation, and mirroring.
- Added **SchematicProjection** to mark missing, incorrect, and matching blocks in the world.
- Added **SchematicBuilder** with layer-by-layer placement, block-state orientation, walking, climbing, bridging, nearby-chest restocking, and optional removal of incorrect blocks. Temporary supports can be removed after use.
- Added redstone construction phases, follow-up block-state adjustments, and reporting for blocked or approximate placements.
- Added **Printer** for placing blocks from an active Litematica schematic. Placement range, blocks per tick, delays, navigation, and restocking are configurable. Litematica is required.
- Added a shared action scheduler for movement, rotation, inventory, and interaction tasks. Actions support sequencing, parallel execution, retries, and timeouts. Owned controls are released on cancellation, module disable, disconnect, world change, and panic.
- Added placement tracking against server block updates, bounded placement retries, a walking navigator, and an optional Baritone adapter.

### Combat

- Added **AnchorMacro** and **DoubleAnchor** for automated respawn-anchor placement and detonation sequences with damage checks.
- Added **AutoHitCrystal** to place obsidian, place an end crystal, and attack it. Supports single and repeated sequences, separate action delays, and hotbar restoration.
- Added **CrystalOptimizer** to hide attacked end crystals locally while awaiting server updates. Unconfirmed removals can be reverted; confirmation timing can use the current ping.
- Added **InstaCart** with rail and TNT-minecart placement, bow charging, target selection, and minimum target-damage and maximum self-damage thresholds.
- Added **Burrow** for jump-assisted defensive block placement with block priority, rotation settings, delayed slot restoration, and auto-disable.
- Added **PearlCatch** to sequence an ender pearl and wind charge, with configurable delay, downward aiming, inventory search, and slot restoration.
- Added **AntiMiss** to suppress manual attacks without a valid hit. An optional fake swing preserves the local hand animation.
- Added **HitSelect** to filter attacks using target hurt time, hit probability, and delay.
- Added **JumpReset** with configurable damage-trigger conditions and cooldown. Added **ShiftTap** for timed sprint-reset inputs after attacks.
- Added **RightClickShield** for temporary shield switching when right-clicking with selected weapons. Added **HoverTotem** for moving totems from inventory slots to the selected destination on hover.
- Expanded **SpearAssist** with charge and reach indicators, damage estimation from relative movement, configurable attack windows, and automatic recharging. The 0.3.0 implementation only exposed cooldown handling.
- Changed **KillAura** attack scheduling around critical-hit eligibility and weapon cooldown. SNAP rotation remains engaged while an eligible attack is pending.
- Updated **Criticals** sprint handling, jump and landing recovery, and Vulcan 2.9.7 behavior for levitation and slow-falling effects.
- Changed server-visible swing and attack packet sequencing in automated combat and mining paths.
- Updated placement geometry, reach and rotation checks, and interaction ordering in **AutoCrystal**, **AutoAnchor**, **AutoBed**, **Surround**, **SelfTrap**, **AutoTrap**, **HoleFill**, and **AutoWeb**.
- Adjusted **ElytraTarget** firework reboost timing and attack coordination. Pursuit prediction, obstacle avoidance, crystal avoidance, and mace support were already present in 0.3.0.

### Movement and player controls

- Added **EntityControl** with mounted-entity steering, boat heading modes, air control, and speed controls. Added **EntityFlight** with normal and Vulcan flight modes for ridden entities.
- Added **Jesus** for configurable movement on liquid surfaces and **LongJump** with slime, shulker, boat, and water-assisted launch modes.
- Added **Spider** for wall climbing, **NoWeb** for cobweb movement, **TridentBoost** for trident propulsion, and **LevitCtrl** for levitation and slow-falling movement control.
- Added **ClickTp** with mouse-button, range, and sneak requirements. Added **AutoJump** for automatic jumping.
- Added **SnapTap** to prioritize the most recently pressed opposing movement key, independently for horizontal and vertical input.
- Changed **ElytraFly** firework use and inventory-switch sequencing, including deferred hotbar restoration. Updated **ElytraHelper** to coordinate with shared inventory actions.
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
- Added **Gauges** with bar and segmented layouts for module-provided HUD states, replacing **TimerIndicator**.
- Updated layouts and material selection in **TargetHud**, **Armor**, **Inventory**, **Scoreboard**, **Radar**, and **ModuleList**.
- Extended theme color usage and text handling across ClickGUI settings, manager screens, notifications, and tooltips. Added English and Russian strings for the new modules and controls.

### Rendering, models, and sound

- Added an HDR scene-color target with tone-mapping and composition passes for Combatant rendering effects.
- Expanded **ReimaginedVisual** with configurable volumetric clouds, distant cloud layers, cirrus, cloud coverage and density, and sky colors derived from the active theme, WorldTweaks, or custom values.
- Added procedural rain with density and render-distance controls, retaining vanilla precipitation when the custom path is unavailable.
- Added **PlayerModel** for cosmetic models assigned separately to self, friends, enemies, staff, and other players. Added model transforms, texture filtering controls, and optional first-person hand replacement.
- Extended imported-model rigging and animations for movement, poses, and item-use actions.
- Added **PhysicSound** with underwater filtering, cave echo, and sound occlusion through the existing sound engine.
- Expanded shader rendering for block highlights, holes, jump circles, trails, targets, and totem effects.
- Updated scene-depth handling and Iris integration for world effects and imported models.

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
- Added import-size and nesting limits for shared configs and schematic files.

### Compatibility and internal changes

- Updated **Javet** from `5.0.9` to `6.0.3` and added Android ARM64 V8 native packaging and Android-specific runtime selection.
- Updated **Entity Culling** from `1.10.5` to `1.11.3` and adjusted **More Culling** compatibility hooks for changed method signatures.
- Updated the build configuration for **Sodium 0.9.2** and **Iris 1.11.7** on Minecraft 26.3.
- Changed shader and native-buffer cleanup during resource reloads and module shutdown.
- Reduced duplicate HUD layout and text measurements, repeated world-projection calculations, transient render allocations, and unused full-frame backdrop copies.
- Updated cleanup of queued packets, pending inventory actions, and automation controls after disconnects and module shutdown.

### Removed and consolidated

- Removed the global `AntiCheatPreset` system. Modules now expose their own protocol and rotation settings.
- Removed **AvoidHazards**.
- Replaced **TimerIndicator** with **Gauges**.
- Added static-hitbox options to the existing **Hitbox** module.
