# Credits and provenance

Combatant Client is an independently maintained project by **pivosos2007**. It is not affiliated with, endorsed by, or an official continuation of any project listed below.

Unless a file-level notice states otherwise, source files are part of Combatant Client, copyright (c) 2026 pivosos2007, and licensed under GNU GPL-3.0-only. Derived/adapted files identify both Combatant authorship and upstream lineage in their headers.

## LiquidBounce / CCBlueX

LiquidBounce is an upstream source or substantial implementation reference for parts of Combatant's rotation system, Scaffold, AutoDodge, movement/player simulation, projectile calculations, and selected combat/network/protocol behavior.

Combatant's implementations were ported to another Minecraft version and mapping set, integrated into a separate module/config/event architecture, and frequently redesigned or extended.

Upstream: https://github.com/CCBlueX/LiquidBounce

## Meteor Client / Meteor Development

Meteor Client served as the initial implementation reference for parts of Combatant's first rendering foundation, including identified text/font, mesh, texture, pipeline, vertex-format, full-screen rendering, and base shader files. A small number of movement/event/accessor files also retain Meteor lineage.

Combatant's current UI batching, renderer organization, post-processing, effects, scripting integration, frame lifecycle, and higher-level rendering systems were subsequently developed within Combatant and differ substantially from Meteor's architecture.

Upstream: https://github.com/MeteorDevelopment/meteor-client

## ThunderHack Recode

ThunderHack Recode is an upstream source or implementation reference for selected Combatant modules, utility code, and UI/HUD behavior. Combatant's affected code has been ported to a different Minecraft version and integrated into Combatant's own module, config, event, and rendering systems.

Upstream: https://github.com/Pan4ur/ThunderHack-Recode

## Other acknowledged upstream work

- **MediaPlayerInfo** by Redstonecrafter0: modified Java/native integration under AGPL-3.0-only.
- **In-Game Account Switcher**: portions of the Microsoft authentication flow under LGPL-3.0-or-later.
- **InvMove** by PieKing1215: portions of inventory movement and screen classification under LGPL-3.0.
- **ExploitPreventer** by Niklas S.: selected protection logic originating under the MIT License.
- **Inter** by Rasmus Andersson: bundled UI font under the SIL Open Font License 1.1.
- **Comfortaa** by Johan Aakerlund: bundled UI font under the SIL Open Font License 1.1.
- **Iosevka** by Renzhi Li / Belleve Invis: bundled monospace font under the SIL Open Font License 1.1.
- **Montserrat** by the Montserrat Project Authors: bundled UI font under the SIL Open Font License 1.1.
- **Onest** by the Onest Project Authors: bundled UI font under the SIL Open Font License 1.1.
- **ProFont**: bundled monospace font under the MIT License.
- **Lucide**: the bundled SVG interface icon set under the Lucide ISC license, including the Feather MIT notice where applicable.
- **Sodium / Iris**: implementation concepts explicitly credited in local source comments where applicable.

## Ownership summary

Most of Combatant's codebase is independently authored. The identified upstream lineage is concentrated in specific subsystems and files, not in the project as a whole. File-level headers are the authoritative indication for individual code files; this document supplies the broader context.
