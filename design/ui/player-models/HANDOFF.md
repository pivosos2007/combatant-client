# Implementation and evidence

Source components: src/dev/resources/assets/combatant/ui/api/kits/adobe/spectrum2/{foundation,tabs,button,action-button,card}.js.
Production subset: src/main/resources/assets/combatant/ui/api/components/player-model-spectrum/.
Host: PlayerModelSelector.java; module: ui/modules/preview/player_models.js.
Theme mapping is recorded in BRIEF.md and supplied by the Java host every frame.

Implemented: actual local Spectrum component ports, scrollable image rows, bounded label height,
empty/disabled states, model and variant actions; central scene bounds for camera hints.
Programmatic UI contract/layout checks cover three widths/heights, two palettes and an empty collection.

Pending design evidence: OpenPencil exports have not been supplied; no visual parity claim.
UI_DESIGN_PIPELINE.md and design/ui/{BRIEF,HANDOFF}.template.md were absent when inspected,
so these documents are self-contained. No in-game capture or test will be performed, per user instruction.
