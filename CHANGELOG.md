# Changelog

All notable changes to **Combatant Client** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

---

## [0.3.0 + community changes] - 2026-10-17: changes compared to upstream `pivosos2007/combatant-client` 26.3

Based on upstream `26.3` (Minecraft 26.3 port, 0.3.0). 90 commits, 166 files, about 20.9k lines added. Every change below was tested against a local Paper server running GrimAC 2.3.74 using a scripted scenario harness (controls, movement, combat, player, schematic and preset scenarios). Unless noted, that testing happened on 26.2 before the 26.3 merge. The 26.3 merge itself compiles and passes the unit tests.

### Minecraft 26.3
- Merged upstream's 26.3 port and ported the code that exists only in this branch to the new APIs: `InputKeys`/`CombatantInput` key state, `Blaze3D.openUri`, `RedstoneWireBlock`, the new `swing()` signature, the `PrimitiveTopology` package move and the new `TextureTarget` constructor.
- AirJump and Timer use upstream's new input API and keep the Grim preset checks.

### Anticheat presets (GrimAC 2.3.74 / vanilla)
- New `anticheat_mode` setting (`CUSTOM`, `GRIM`, `VANILLA`) on combat, movement and player modules (KillAura, AutoCrystal, SpeedMine, Criticals, Timer, NoSlow, Speed, Sprint, Strafe, TargetStrafe, NoFall, Step, AirJump, ReverseStep, Surround, SelfTrap, AutoTrap and more). It clamps reach, rates and modes to what Grim accepts.
- **Criticals:** the Grim mode turns packet crits into real jump crits, and the Grim packet mode no longer sends an extra illegal flying packet.
- **Timer:** the Grim mode never runs faster than 1x.
- **NoSlow:**
  - The Grim mode routes to `grim_tick` and resets its state on enable.
  - The item-use multiplier is applied before diagonal compensation, so vanilla diagonal physics stay intact.
  - Sprint is only kept with forward input and the sprint key held.
  - The dead offhand bypass is removed.
- **KillAura:** fixed the SNAP rotation oscillating while Criticals waits for a crit. It no longer attacks while the player is using an item.
- **AutoCrystal:**
  - With the Grim preset, it places only once the last sent rotation already hits the clicked block (fixes Grim RotationPlace).
  - Instant place and instant break are left out of the Grim preset.
- **Surround/SelfTrap:**
  - Crystals blocking a placement are broken from the start-of-tick hook.
  - Support chains reach up to three blocks, so SelfTrap `head` builds its own pillar in open ground.
- **Start-of-tick timing:** BlockPlacer places before the movement packet. AirPlace, AutoMount and AutoBreed run from the start-of-tick hook. The hotbar lease restore is sent at the start of the tick (fixes Grim Post flags).

### Scaffold
- Clicks satisfy the raycast geometry GrimAC checks. Scaffold aims at its solved strict rotation, not at the click point seen from the current eye.
- **Rotation:** the sent yaw stays continuous and away from a half turn off the player's own yaw (AimModulo360). Scaffold turns in one step when smoothing would arrive too late.
- **Movement prediction:** cadence is counted in ticks (default 20 blocks/s), and the jump tick is predicted. Scaffold follows the player's real path on diagonals, reads the movement direction from the keys, and the fall-off prediction now returns the correct support face.
- Telly, same-Y, diagonal, jump-telly and falling-telly modes run without Grim flags.

### Schematic builder (new)
- **Loading:** reads Litematica `.litematic`, Sponge `.schem` (v2/v3) and vanilla structure `.nbt` files. Every format goes through a file-size cap and an NBT heap quota, and malformed files are rejected with clear errors.
- **Modules and menu:** new `SchematicProjection` and `SchematicBuilder` modules, plus a schematic menu (`@schematic` command). The menu has:
  - a file list with format chips, search and an empty-search state;
  - an orbitable 3D model view with a layer slider;
  - layer and all materials, with have/missing counts;
  - a problems list and redstone-parts view.
- **Placement:**
  - Cells are placed farthest first, and side clicks are aimed clear of the stair/slab half boundary.
  - A click is held until the rotation reproduces the planned state. Look-dependent blocks are planned by yaw, including 16-step rotations for signs, banners, heads and skulls.
  - Sneak-clicks are planned for blocks with a use action. Corner stairs, fence arms and wall sides are counted as shapes the neighbours give.
  - Stacked pieces (double slabs, candles, pickles, eggs, petals, snow layers) are filled in. Ceiling-hung blocks wait for the block above.
  - The sign editor is closed automatically.
  - Only the hotbar and main inventory count as usable materials.
  - Restocking from the inventory happens only while standing still (Grim MultiActionsC).
- **Redstone:** judged by functional state and built in phases, so machines don't fire half-built. The look stays free for piston placement.
- **Walker:** walks, climbs and jump-pillars to out-of-reach cells.
  - Pathfinding uses A* with line-of-sight smoothing and cached terrain (refreshed every 30 s). Plain blocks are placed on the move.
  - It walks through open doors and open trapdoors, plans swimming, and refills unloaded sections.
  - Helper blocks are kept across toggles, and it never pillars with needed build material.
- **Performance:**
  - The overlay culls interior same-status faces at rebuild (about 10x fewer vertices on solid regions) and runs an allocation-free frustum test.
  - The diff layout is reused across placements, and mesh jobs survive failures.
  - Huge schematics skip full-detail meshing.

### Visuals and performance
- **New shaders:** an animated glow surface shader for HoleESP, BlockESP, BlockHighlight and SpeedMine, and a procedural glow ring/sprite shader for JumpCircles, Trails and TargetESP.
- **ESP shader mode:** the blur and effect targets use RGBA16F, with an RGBA8 fallback, so glow gradients don't band.
- **Tracers arrows:** one batched mesh and one draw per frame, instead of a separate draw, matrix and colour per player.
- **Fewer allocations:** Trails, KillEffect and Predictions write vertex colours without allocating. BlockESP reuses its cached target box. Visual modules share a cached screen projection, tick-keyed entity/tracer caches and quad culling.
- Shutdown, post-process registration and setting bounds are hardened.

### Fixes
- Removed a stray `VULCAN` anticheat preset that had no labels and acted inconsistently. Saved configs that use it fall back to the default.
- SafeWalk no longer creeps off edges. Blink's movement-only mode holds packets reliably. The totem pop counter counts once per pop.
- Added missing ru_ru translations.

### Tests
- 40+ new JUnit test classes cover:
  - Schematic readers, diff, materials, placement, build order and state comparison.
  - The walker: pathfinding, smoothing, pillars, route following and terrain cache.
  - The 3D view (face meshing, picking, camera).
  - Surround planner, Scaffold geometry and prediction, NoSlow, KillAura rotation timing, Criticals Grim mode, Bhop input, AutoCrystal ray test, RestockGate and schematic face culling.

## [0.2.1 + community changes] - 2026-10-09 (earlier detail)

### Anticheat Compatibility & Presets (GrimAC 2.3.74 & Vanilla Baseline)
- **Centralized `AntiCheatPreset` System**:
  - Added `anticheat_mode` enum setting (`CUSTOM`, `GRIM`, `VANILLA`) across combat, movement, and player modules.
  - Defined canonical server interaction constants: `GRIM_ENTITY_RANGE` (3.0), `GRIM_BLOCK_RANGE` (4.5), alongside vanilla limits (`6.0` entity, `5.5` block).
- **Combat Preset Bypasses**:
  - **AutoAnchor & AutoBed**: Clamped block placement and detonation reach to 4.5 blocks; restricted interaction rate to at most one interaction per client tick to prevent interaction spam flags.
  - **AutoCrystal**: Enforced strict Grim distance bounds: 4.5 block placement range, 3.0 block wall range, 3.0 block break range, and 3.0 block break wall range.
  - **Criticals**: Under Grim preset, automatically converts packet tricks (`PACKET`, `NO_GROUND`, `VULCAN_297`) to legitimate `JUMP` criticals at vanilla motion (`0.42`), preventing `GroundSpoof`, `Simulation`, and `Timer` flags while preserving damage output. Added unit tests for Grim mode selection.
  - **KillAura & Reach**: Enforced strict `GRIM_ENTITY_RANGE` (3.0 blocks) attack clamping under Grim.
  - **Block Protection / Combat Traps**: `AutoTrap`, `AutoWeb`, `HoleFill`, `SelfTrap`, and `Surround` clamp block placement ranges to 4.5 blocks and wall ranges to 3.0 blocks, with crystal-clearing checks restricted to Grim bounds.
  - **Totem Pop Counter**: Deduplicated totem pop triggers to count once per totem pop instead of once per processing thread.
- **Movement Preset Bypasses**:
  - **Speed**: Added new legitimate `BHOP` (Bunny Hop) mode that drives acceleration through input packets (`jump` and `sprint` bits) rather than directly injecting velocities, passing Grim simulation checks cleanly. Covered by `BhopInputTest`.
  - **Sprint**: Added Grim-compliant sprint blockage checks to prevent omnidirectional sprinting flags when blinded, sneaking, exhausted/hungry, or colliding horizontally.
  - **NoSlow**: Wired into vanilla item-use slowdown; automatically swaps in `GRIM_TICK` under the Grim preset.
  - **Timer**: Caps timer speed to 1.0x under Grim, ceases ping/pong packet holding, and disables `Shift` tick accumulation to pass transaction order checks.
  - **AirJump & ReverseStep**: Safely disabled under Grim to prevent vertical velocity and falling simulation flags.
  - **Step**: Enforces legitimate step height limits (clamped within 0.6–5.0) and mode switching.
  - **NoFall**: Automatically maps ground-spoofing modes to `PACKET_JUMP` under Grim, maintaining `MLG` mode as a real-placement alternative.
  - **Strafe & TargetStrafe**: Automatically switches from Matrix velocity modification to Grim mode.
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
  - `SpeedMine`: Relocated to pre-tick hook, paced block breaks, and ensured required hand swing packets are sent before break completion.
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
