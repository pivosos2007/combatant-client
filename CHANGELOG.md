# Changelog

All notable changes to **Combatant Client** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [0.3.0 — in progress] - 2026-10-17

### Platform
- Synced with upstream `origin/26.3`: Combatant now targets **Minecraft 26.3**. Local shaders declare explicit SPIR-V interface locations; the UI material-primitive batch binds its `UIGlassFrame` uniform (fixed a first-frame crash).
- **StorageESP** (Visuals → ESP): per-type toggle, colour and tracer for chests, trapped/ender chests, shulkers (optional dye colour), barrels, spawners, furnaces, hoppers/droppers/dispensers and enchanting tables; box/outline/fill modes, max distance, and dimming of containers you already opened this session.

### Unified Automation Runtime (`combatant.client.automation`)
- **Control leases**: `ActionCoordinator` arbitrates rotation, movement keys, jump, sneak, sprint, hotbar, inventory and interaction by named priority tiers (`ActionPriority`, same scale as `RotationManager`). Preemption notifies the loser; ties keep the current holder; slot leases taken directly through `InventorySwap` are respected.
- **Tasks and actions**: `AutomationTask` + composable actions (`Sequence`, `Selector`, `Parallel`, `Retry`, `Timeout`, `Guard`) and primitives (`Rotate`, `Move`, `Jump`, `Sneak`, `PlaceBlock`, `BreakBlock`, `UseItem`, `InteractEntity`, `Inventory`, `Navigate`). Actions run before the movement packet and release every backend on cancel.
- **Lifecycle**: tasks are cancelled on module disable, world change, disconnect, respawn and panic/unhook (new `WorldChangeEvent`). Player WASD/jump/sneak and physical mouse look take control back from tasks that yield to manual input.
- **Placement**: `BlockPlacer` now plans with `PlacementPlanner` and places through `PlaceBlockAction`; a position is only dropped once the server acknowledges the prediction sequence and the block is there (`PlacementTracker`, `PlacementAckMixin`). Rejected cells retry with backoff. Surround, SelfTrap, AutoTrap, HoleFill and AutoWeb build their own `PlacementConstraints` from their own settings.
- **Navigation**: `NavigationProvider` API with the built-in walker (A*/route follower) and an optional Baritone adapter loaded only when Baritone is installed.
- **Migrated**: HoleSnap (runtime `MoveAction`), AutoTool (hotbar lease instead of raw slot selection), shared `MovementKeys` replaces three WASD helpers.
- **SpearAssist**: vanilla already refuses a jab below full charge (`Minecraft.startAttack` -> `cannotAttackWithItem(stack, 0)`), so the redundant `respect_cooldown` toggle and its `MinecraftMixin` hook were removed. New: charge HUD (charge-up bar, Dismount/Knockback/Damage windows with remaining time, predicted stab damage), reach band marker (too close / in range / too far, creative-aware) and optional auto re-charge through the vanilla release path. Rules live in the tested `SpearKinetics`.

### Printer (optional Litematica)
- New `Printer` module builds the active Litematica placements through the shared runtime (placement, restock, optional navigation, orientation rules, block-model preview). Litematica is an optional dependency and is never loaded when absent.

### Config Persistence
- Hot configs are written atomically (temp file + move) with a `.bak` of the last valid file, corrupt files are quarantined and recovered, writes are coalesced, unknown keys are preserved, and pending writes flush on shutdown and panic.
- `.cbcfg` profiles record the Combatant version that saved them; older profiles load unchanged.

### Removed Anticheat Presets
- The per-module anticheat preset system (`anticheat_mode` setting, `AntiCheatPreset`, `AntiCheatPresetDefaults`) is removed. Every module keeps the behaviour of its former default preset and uses its settings exactly as set.

### New Modules
- **Combat**: `StaticHitboxes` (standing-size ray-cast boxes), `HoverTotem` (hover a totem in an inventory to equip it), `ShiftTap` (W-tap / sneak-tap after hits via runtime leases), `AnchorMacro` and `DoubleAnchor` (place, charge and detonate anchors with self-damage safety, built from runtime actions; new `InteractBlockAction` and `Actions.defer`). Anchor self-damage prediction (AnchorMacro and `AutoAnchor` on existing anchors) no longer lets the anchor shield the player from its own blast.
- **Movement**: `AutoJump`, `Spider` (vanilla/jump), `NoWeb`, `LongJump` (boost/damage). `Parkour` gains `EdgeJump` aliases; `Freeze` gains `AirStuck` alias and hold-key mode.
- **Player**: `Nuker` (filtered, ordered breaking through `BreakBlockAction`), `LockSlots` (blocks drop/move/swap of locked slots, hotbar tint), `ClickThrough`, `NoEntityTrace`, `QuickUse` (keybind item use with swap-back), `EnderChestPlus` (cached contents in tooltip and `@echest`), `AutoInvTotem` (strict open-inventory totem refill), `ShulkerDropper`, `TunnelBaseFinder` (1x2 tunnel with hazard pauses and storage/spawner alerts).
- **Misc**: `AutoLeave`, `AutoAccept`, `AutoAuth`, `ServerRPSpoofer`, `DeathCoordinates` (chat + Xaero waypoint), `AntiCheatDetect` (ping-pattern heuristic), `AntiVanish`, `AutoRtp`, `AutoTpa`, `CordSnapper`.
- **Visuals**: `Skeleton`, `PopChams`, `BedrockEsp`, `BlockNotifier`. `Tracers` arrows gain distance colouring; item physics gains random heading and tumble speed; `LightFinder` gains a strict below-Y=0 mode.

### Fixes
- **SchematicBuilder**: the mover's sneak is now paced by `SneakPacing` (held >= 2 ticks, starts >= 3 ticks apart) so rapid bridging / edge-sneak flips no longer trip Vulcan BadPackets K.

### Removed
- `SpeedMine` and `SpeedMineGameModeMixin` (`NoDelay` covers fast breaking).

## [Unreleased / Latest Krypton Updates] - 2026-10-09

### Fixes & Additions
- **Totem Pop Counter**: Deduplicated totem pop triggers to count once per totem pop instead of once per processing thread.
- **Speed**: Added a `BHOP` (Bunny Hop) mode that drives acceleration through input packets (`jump` and `sprint` bits) rather than directly injecting velocities. Covered by `BhopInputTest`.
- **SafeWalk**: Fixed edge-walking creep where holding a direction key slowly shifted the player off the block edge.
- **Blink**: Movement-only mode now holds movement packets reliably.

### Scaffold & Placement Engine Overhaul
- **Raycast Geometry**: Overhauled placement targeting so clicks satisfy GrimAC raycast geometry (aims at external block face positions from outside the block collision box).
- **Rotation Smoothing & Yaw Clamping**:
  - Constrained sent yaw angles to prevent 180° flick flags (`GrimAC AimModulo360`).
  - Aligned silent movement correction yaw quantization so diagonal movement remains natural without desyncing look angles.
  - Fast-snapped rotation in one step when smoothing latency would arrive after the placement window.
- **Tick-Based Placement Cadence**: Swapped placement timing from wall-clock nanoseconds to integer client ticks (defaulting to 20 blocks per second = 1 block per tick).
- **Trajectory & Jump Prediction**:
  - Added single-tick jump impulse prediction (`0.42` impulse + sprint boost) for accurate placement while jumping or telly-bridging.
  - Follows the player's real path on diagonal bridging rather than snapping to an ideal line when movement stabilization is disabled.
  - Fall-off prediction fixed to correctly return the supporting block face.
- **Input Handling**: Reads player movement direction directly from raw keyboard input rather than corrected movement inputs to prevent double-correction drift.

### Player & Automation Modules
- **Pre-Tick Placement Dispatch**:
  - `BlockPlacer`: Relocated block placement dispatch to before the movement packet (`start-of-tick` hook).
  - `InventorySwap`: Hotbar lease restoration now sends at the start of the tick, eliminating `Post` interaction flags.
  - `AirPlace`, `AutoMount`, and `AutoBreed`: Moved to start-of-tick hooks.
- **AutoBreed**: Now feeds all nearby breedable animals in one pass instead of stopping at the first.
- **AutoMine**: New module for automated block mining.
- **ChestStealer**: Fixed container inventory synchronization.
- **NoDelay**: Enforced minimum delays on block breaking, placing, right-clicking, and jumping under Grim.
- **AntiAFK, AutoWalk, AntiVoid, HoleSnap, AutoClicker**: Added utility modules for automation, safety, and combat assistance.
- **Surround & SelfTrap**: Refactored around a shared placement planner with options for `KTLeave` and `ProjectilePuncher`.

### Visuals & Finders
- **DonutSMP Finders**:
  - Ported `NetheriteFinder`, `ActivityFinder`, `ChunkFinder`, `SpawnerFinder`, `SusChunkFinder`, `OGChunkFinder`, `LightFinder`, and `ExplosionMarker`.
  - Added server seed mismatch detection in `NetheriteFinder` with neighbor chunk synchronization.
- **Camera Adjustments**: Added mouse-wheel adjustment to `Zoom` and `Freecam`.
- **ESP & Cosmetics**:
  - Improved `DropESP`, `BlockESP`, `Predictions`, and `ViewModel`.
  - Fixed `FakePlayer` absorption health setting having no effect.

### Rendering Engine & Performance
- **Sodium Compatibility**: Added support for Sodium 0.9.2 while retaining extended terrain stride rendering.
- **Iris Compatibility**: Prevented crash in `GlCommandEncoderMixin` when Iris is absent.
- **2D/3D Render Engine**:
  - Split JIT compilation size of `UiShapeRenderer` to improve startup compilation performance.
  - Added memory caches for script color parsing (`UiScriptColor`) and mesh arena mappings (`Blaze3dMeshArena`).
  - Eliminated per-draw allocations in visual baseline profiles.
  - Optimized health bar script reruns and dynamic island updates.

### Diagnostics & Developer Tooling
- **Command `@i18naudit`**: Developer command to dump all configuration setting key chains and identify missing English/Russian localization strings.
- **Command `@rotdbg`**: Developer command for real-time packet and rotation logging to `latest.log` for anticheat tuning.
- **Build Cleanliness**: Excluded `.omc` agent state, test server files, and stray root metadata files from release jar packaging via `.gitignore`.
