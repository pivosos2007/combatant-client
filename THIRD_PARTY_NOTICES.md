# Third-party notices

This file covers source-derived or source-adapted components identified in this snapshot. Dependency metadata in `build.gradle` remains the authoritative inventory for external libraries resolved at build time.

## Combatant original code

Unless a file-level notice or an entry below states otherwise, Combatant source code is copyright (c) 2026 pivosos2007 and distributed under GNU GPL version 3. See `LICENSE`.

## LiquidBounce

- Upstream: https://github.com/CCBlueX/LiquidBounce
- Copyright: 2015-2025 CCBlueX
- Upstream license: GNU GPL v3 or later
- License text: `LICENSES/GPL-3.0-only.txt` (the GPLv3 license text; upstream's “or later” option is stated in its source notices)
- Scope: rotation/aiming utilities; Scaffold and placement helpers; AutoDodge and selected simulation/input helpers; projectile calculations; selected Blink/FakeLag/Backtrack/Velocity/combat/timer/protocol code. Exact files carry a `Combatant attribution: LiquidBounce-derived/adapted code` header.

## Meteor Client

- Upstream: https://github.com/MeteorDevelopment/meteor-client
- Copyright: Meteor Development
- License: GNU GPL version 3
- License text: `LICENSES/GPL-3.0-only.txt`
- Scope: identified portions of the early font/text, mesh, texture, vertex-format, render-pipeline and shader foundation, plus selected movement/event/packet-accessor code. Exact files carry a `Combatant attribution: Meteor Client-derived/adapted code` header.

## MediaPlayerInfo

- Upstream: https://github.com/Redstonecrafter0/MediaPlayerInfo
- Copyright: Redstonecrafter0 and contributors
- License: GNU Affero General Public License version 3 only
- License text: `LICENSES/AGPL-3.0-only.txt`
- Scope: `java/combatant/client/util/media/**`.

The AGPLv3 component and GPLv3 code may be combined under AGPLv3 section 13. The AGPL terms continue to apply to the MediaPlayerInfo-derived component, while the combined GPLv3 work remains governed by GPLv3 for its GPL-covered portions. Do not remove the AGPL file headers or the AGPL license copy when distributing the source or a corresponding binary.

## In-Game Account Switcher

- Upstream: https://github.com/The-Fireplace-Minecraft-Mods/In-Game-Account-Switcher
- Copyright: 2015-2022 The_Fireplace; 2021-2026 VidTu
- License: GNU LGPL version 3 or later
- License text: `LICENSES/LGPL-3.0.txt`
- Scope: Microsoft device-code/authentication flow in `MicrosoftDeviceCode.java` and `MicrosoftAuthService.java`.

## InvMove

- Upstream: https://github.com/PieKing1215/InvMove
- Copyright: PieKing1215 and contributors
- License: GNU LGPL version 3
- License text: `LICENSES/LGPL-3.0.txt`
- Scope: inventory movement behavior and screen classification in `InventoryMove.java` and `ScreenCatalog.java`.

## ExploitPreventer

- Upstream author: Niklas S.
- Original work copyright: 2025 Niklas S.
- License: MIT License
- License text: `LICENSES/MIT.txt`
- Scope: the files carrying the `Combatant attribution: ExploitPreventer-derived/adapted code` header under `features/security` and `mixins/security`.

## Sodium and Iris

The terrain vertex implementation contains a local source comment crediting Sodium's compact chunk-vertex packing and the Iris shaderpack extension concept. This notice records architectural/reference influence; this audit does not assert additional copied source beyond what the file itself states. Their own licenses and notices apply to their separately distributed projects and runtime dependencies.

## Hold My Items

- Upstream project: Hold My Items
- Project page: https://modrinth.com/mod/hold-my-items
- Port source: supplied `HMI 5.1.1 (1.21.11).jar`
- Upstream author: sapling (as declared by the supplied HMI 5.1.1 mod metadata)
- License basis for this adapted snapshot: CC0 1.0 Universal, as declared and bundled in the supplied jar
- License text copied verbatim from that jar: `THIRD_PARTY_LICENSES/HoldMyItems-CC0-1.0.txt`
- Scope: the HMI compatibility subsystem in `java/combatant/client/features/hmi/**`, its rendering mixins in `java/combatant/client/mixins/hmi/**`, and the JavaScript pose/model scripts in `resources/assets/minecraft/holdmyitems/**`.
- Porting note: the original Lua/LuaJ script-facing behavior was adapted to Combatant's existing Javet JavaScript stack and the Minecraft 26.2 item rendering pipeline. No LuaJ runtime is bundled by this port.

## Protean Clouds

- Upstream work: Protean clouds
- Author: nimitz (`@stormoid`)
- Source: https://www.shadertoy.com/view/3l23Rh
- License: Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported
- Scope: the adapted stationary world-space volume in `assets/combatant/shaders/visual_preview_clouds.frag`.
- Licensing contact: contact the upstream author for other licensing options.
