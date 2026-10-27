# Adobe Spectrum 2 — development-only Combatant component package

This is an independent Combatant JS implementation using Adobe Spectrum 2 design
specifications, not React Spectrum/React Aria, nor a complete 1:1 port.

Sources:
- https://spectrum.adobe.com/getting-started/introduction
- https://spectrum.adobe.com/web/rsp/components/button
- https://spectrum.adobe.com/page/checkbox/
- https://spectrum.adobe.com/page/text-field/
- https://github.com/adobe/spectrum-design-data
- https://spectrum.adobe.com/foundations/typography/typography-system

The currently implemented dev previews: Buttons, Action Buttons, Checkbox,
Switch, Radio, Tabs, Text Field, Tags. They use Combatant's retained UI nodes,
input dispatcher and GPU renderer. Keyboard/IME for the editable field is routed
through SingleLineTextInput + CombatantTextInputBridge.

Typography: Adobe Clean Spectrum VF is restricted to Adobe software. We do not
redistribute it. Source Sans 3 is an official fallback in Spectrum's published
font stack; the build fetches Source Sans 3 Regular and Semibold (SIL OFL 1.1,
revision `87b37a2`) for dev only. The license is included with the generated
resources. This produces a deliberate, documented typography difference.

Colors: current primitives are sRGB 8-bit values (Spectrum design-data emits
rgb(...)/hex values). RGBA8 is sufficient for these examples; wide gamut/HDR
would require a separately color-managed renderer and a new framebuffer/packing
contract. Do not treat float intermediate shader arithmetic as proof of HDR
output or assume that Display-P3 is supported by an RGBA8 sRGB attachment.

Not yet 1:1: the exact S2 font, glyph/icon set, focus-visible keyboard semantics,
full variants and sizes, accessibility, advanced collections, long lists,
menu/popover/sheets and all Spectrum 2 motion details. These remain subject
to separate implementation and visual A/B validation.

## Second dev batch (caja baseline)

- Picker: labeled selection field, viewport-flipping popover, disabled entry,
  mouse/outside-click, native `InputKeys` keyboard navigation, typeahead,
  focus recovery. Current demo implements **single selection** only.
- Slider: controlled 0–100 value, click/drag via Combatant pointer events,
  Home/End, arrow keys, Shift increment; no GLFW listener.
- ProgressBar/Meter: determinate values; meter supports informative/positive/
  notice/negative semantic color variants, driven by the gallery slider.
- SearchField: native `SingleLineTextInput` and `CombatantTextInputBridge`,
  clear action and submit on Enter (no separate keyboard stack).
- The original `build.gradle` in caja omitted actual Source Sans 3 file provisioning
  despite declaring `AdobeSpectrumFonts`; a **dev-only** Gradle task now adds the
  open regular/semibold TTF files and SIL OFL text from Adobe's tagged `3.052R`
  release. No production resource/source set is altered.

Reference APIs:
- https://react-spectrum.adobe.com/Picker
- https://react-spectrum.adobe.com/Slider
- https://react-spectrum.adobe.com/SearchField
- https://react-spectrum.adobe.com/ProgressBar
- https://react-spectrum.adobe.com/Meter

This remains a native **partial adaptation** rather than a pixel-verified 1:1
copy: Adobe Clean Spectrum VF is not bundled; React Aria accessibility semantics,
full Spectrum 2 variants, long async collections, indeterminate animation and
remaining S2 components are not claimed to be complete. Color values in this
batch are sRGB RGBA8; it does not require Display-P3 or HDR framebuffers.
