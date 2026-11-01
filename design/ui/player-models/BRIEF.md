# PlayerModel selector: OpenPencil handoff brief

Refine the existing Combatant VisualPreview selector using Adobe Spectrum 2. Keep the supplied local kit.
Current implementation uses native foundation, tabs, button and action-button; card surfaces and motion
originate in its card component. Keep Combatant's theme colors and Onest typography.

Host: fullscreen Minecraft VisualPreview. Left panel is min(310px, 27% of virtual viewport width), minus 12px
outer gap; it starts at y=42 and ends 42px before viewport bottom. Right settings reserve min(430px, 38%).
The middle shows the player; keyboard/mouse hints must remain inside that middle region.
Reference viewports: 1280x720, 1920x1080; left panels 220/298px, heights 460/636/900px.

Required selector states: no addon installed, models list, texture variants for selected model,
selected item, hover/press, disabled variants tab, applied appearance and restore action, loading/failure.
Controls: title, Models/Textures tabs, selected name, scrollable icons with two-line names,
Apply/Restore primary action, Reload collection quiet action. Models can come from multiple namespaces.
Icons are at most 256x256. Include long Cyrillic names, missing optional icons and 44 appearances.
No filesystem paths, importer internals or developer diagnostics in the ordinary selection flow.

Quality settings stay in the existing right module panel: texture limit (512/1024/2048/original),
nearest/linear filter, mipmaps; preview pose, fitting transform and pivot remain there too.

Palette mappings: background/surface -> theme.windowBg; raised -> surface; field -> windowHeader;
border -> strokeSoft; text -> textPrimary; muted/disabled -> textMuted; accent -> accent;
selected -> accentSoft; onAccent -> black/white chosen by accent luminance.

Return editable design plus exported light/dark references at the viewports above, interaction states,
and spacing/type/color token mapping. JSX/CSS exports are references only; runtime remains Combatant Java/JS.
Do not introduce a web framework. Use the current ui.contract.json and ui.d.ts node/style capabilities.
