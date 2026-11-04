# Adobe Spectrum 2 - reusable Combatant component package

This is an independent Combatant JS implementation using Adobe Spectrum 2 design
specifications, not React Spectrum/React Aria, nor a complete 1:1 port.

Sources:
- https://spectrum.adobe.com/getting-started/introduction
- https://spectrum.adobe.com/web/rsp/components/button
- https://spectrum.adobe.com/page/checkbox/
- https://spectrum.adobe.com/page/text-field/
- https://github.com/adobe/spectrum-design-data
- https://spectrum.adobe.com/foundations/typography/typography-system

The original dev previews: Buttons, Action Buttons, Checkbox,
Switch, Radio, Tabs, Text Field, Tags. They use Combatant's retained UI nodes,
input dispatcher and GPU renderer. Keyboard/IME for the editable field is routed
through SingleLineTextInput + CombatantTextInputBridge.

Typography: Adobe Clean Spectrum VF is restricted to Adobe software. We do not
redistribute it. Source Sans 3 is an official fallback in Spectrum's published
font stack; Source Sans 3 Regular and Semibold are already bundled in the main resources.
No font download or network operation is allowed during Gradle builds. This produces a deliberate, documented typography difference.

Colors: current primitives are sRGB 8-bit values (Spectrum design-data emits
rgb(...)/hex values). RGBA8 is sufficient for these examples; wide gamut/HDR
would require a separately color-managed renderer and a new framebuffer/packing
contract. Do not treat float intermediate shader arithmetic as proof of HDR
output or assume that Display-P3 is supported by an RGBA8 sRGB attachment.

Not yet 1:1: the exact S2 font, glyph/icon set, focus-visible keyboard semantics,
full variants and sizes, accessibility, advanced collections, long lists,
menu/popover/sheets and all Spectrum 2 motion details. These remain subject
to separate implementation and visual A/B validation.

## Production use

All component modules, Source Sans 3 Regular/Semibold, Spectrum SVGs and the color
wheel texture are included in ordinary builds. The component gallery Java host,
`gallery.js`, `overlays.js` and `modules/dev/component_gallery.js` remain dev-only.
The sections below record the development history and implementation limits.

From a script in `ui/modules/`, import individual modules or the full entry point:

```js
import { SpectrumButton, SpectrumCheckbox } from '../api/kits/adobe/spectrum2/components.js';

export function buildTemplate(ctx) {
  return SpectrumButton.render({
    key: 'save', label: 'Save', onClick: 'settings:save'
  });
}
```

Direct imports such as `../api/kits/adobe/spectrum2/button.js` load only the
required dependencies. Both forms use the existing Combatant UI script loader.
`SourceSans3` and `SourceSans3Semibold` are registered in the main font catalog.
Their bundled license is `THIRD_PARTY_LICENSES/SourceSans3-OFL-1.1.txt`.

Components produce retained UI nodes. The consuming host owns model state,
action handlers, text editing, pointer/keyboard behavior and overlay placement.
Pass explicit action IDs/prefixes through each component's props; legacy
`lab:adobe:*` defaults belong to the gallery and have no production handlers.
Reusable overlay components (`Menu`, `Dialog`, `StandardDialog`, `Popover`,
`ContextualHelp`, `Toast`, `Tooltip`, `Calendar`) are included; gallery-specific
routing and example data in `overlays.js` are not part of the production kit.

Validation: `node tools/dev/test_ui_spectrum2_production.mjs` checks every public
export, renders all 64 components with the production UI API in three states,
and checks shared-scope imports, dev gallery imports and bundled assets.

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
  despite declaring `AdobeSpectrumFonts`; the archive already contains the two dev Source Sans 3 faces.
  Gradle must not download or replace fonts. Check license provenance separately.

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

## Current audit (cajs baseline)

- The Pickers and Feedback pages were registered in JavaScript but absent from
  ClickGUI's Java action router. Registered both pages and strengthened tests.
- Radio was always blue, but Spectrum 2 defaults to a neutral radio group.
  The native implementation now exposes `emphasized` explicitly and includes
  the label in the interactive row. Spectrum RadioGroup selection is keyboard
  navigable via the existing Combatant input codes.
- No Gradle HTTP download tasks for Google/Adobe fonts remain. The supplied
  archive has the Source Sans 3 faces but not Roboto. Until licensed Roboto
  assets are integrated by the project owner, the Google dev kit may use
  Combatant's font fallback; do not claim 1:1 typography there.
- Pixel-perfect S2 verification still requires Minecraft GPU screenshots
  compared against official reference at matching device pixel ratio.

## Dev batch — metadata, identity, geometry, progress, toggles (cake baseline)

Added independent native implementations and catalog pages for:

- `Badge`: semantic and named color variants; S/M/L/XL, bold/subtle/outline;
  badge content is clipped to its assigned layout bounds.
- `Avatar` / `AvatarGroup`: initials (Latin names), guest fallback using the
  existing `user` SVG, configurable sizes, strokes, overlapping collection and
  a `+N` overflow marker. These samples do **not** load account photos or
  reproduce proprietary Adobe gradient assets.
- `Divider`: S/M/L horizontal and vertical flat renderer quads.
- `ProgressCircle`: S/M/L determinate native `arc` geometry, value range
  clamping, and animated indeterminate progress driven by the Java host's
  monotonic clock. This does not allocate an independent JS timer or GPU pass.
- `ToggleButton`: controlled selected state, quiet/emphasized/disabled variants,
  hover/press/focus-visible transitions and host-keyboard Enter/Space dispatch.

All samples are built with the existing `ui.*` inline authoring contract,
`SpectrumFoundation`, ClickGUI `UiRuntime` and `UiComponentLabSection` action
router. The Adobe navigation pages are registered with Java as well as JS;
Google Material 3 pages remain registered. The test harness now explicitly
uses matching kit/page combinations and covers these six additions at five
screen sizes along with host-controlled state and animation bounds.

References:
- https://spectrum.adobe.com/web/rsp/components/badge
- https://spectrum.adobe.com/web/rsp/components/avatar
- https://spectrum.adobe.com/web/rsp/components/avatar-group
- https://spectrum.adobe.com/web/rsp/components/progress-circle
- https://react-spectrum.adobe.com/Divider
- https://react-spectrum.adobe.com/ToggleButton

**Limitations:** These are dark-theme native previews, not pixel-verified Adobe
React Spectrum clones. The Avatar examples support initials/guest fallback only;
Badge and ToggleButton sizes/colors are approximations for the current sRGB
palette. Text overflow and full internationalized wrapping, React Aria
accessibility semantics, user images, and the full S2 motion tokens still need
native follow-up work. Live GPU parity is unverified until in-game captures.
The Source Sans 3 font faces are supplied in this archive, but the referenced
OFL license *text file* is absent: verify bundled font redistribution
provenance before publishing these resources.

## Dev batch — action grouping and persistent animation (cakr baseline)

- `ActionGroup`: controlled single/multiple selection, regular/compact density,
  quiet/emphasized styles, horizontal/vertical composition, optional disabled
  controls. Uses existing `SpectrumActionButton` hover/press state and the host's
  input/action router. The compact border geometry remains a native adaptation,
  not a pixel-verified implementation of Adobe's group-outline tokens.
- `ButtonGroup`: related actions, narrow-width row wrapping and vertical stacks,
  per-item variants and disabled dispatch guards.
- Java host keyboard support: Arrow keys change single action-group selection;
  Enter/Space act on focused enabled group members.
- Removed the frame-varying ProgressCircle phase from the **entire gallery's**
  structural signature. Java now patches `startAngle`/`endAngle` on three live arc
  nodes in the ProgressCircle page. The nodes' bounds, style, child hierarchy and
  event handlers are unchanged; no JS execution, reconcile or relayout is needed
  for the spinner frames alone.
- Empty bounds-patch maps no longer start profiler/patch passes, and unchanged
  bounds are skipped. Animation node searches are scoped to their actual pages;
  cursor text measurements occur only when the relevant editor is focused.

Static validation: `node tools/ui-tests/spectrum2-kit-smoke.cjs` verifies all 33
Spectrum pages at five viewport sizes, including key uniqueness and controlled
states; the Material 3 smoke test remains available. This does not replace an
actual Minecraft GPU frame capture, JS/Javet profiling or an in-game FPS test.

References:
- https://spectrum.adobe.com/web/rsp/components/action-group
- https://spectrum.adobe.com/web/rsp/components/button-group

## Dev batch — retained Accordion animation, Action Bar, Illustrated Message (caku baseline)

- Accordion panels now remain mounted even when closed. Its disclosure clock
  no longer enters the structural JS model. Java patches the clipped panel bounds,
  header positions, following gallery controls and chevron/content opacity in the
  existing `UiRuntime` tree; the four node groups are located once per page
  activation and reused during frame updates. The gallery reserves the maximum
  disclosure scroll height to avoid changing its scroll extent every frame.
  Single/multiple expansion policy and keyboard activation remain host-controlled.
- `ActionBar`: selected count, clear selection, available and disabled actions,
  an emphasized variant, compact layout below 425px. The preview demonstrates
  the component in normal gallery flow; page-independent sticky overlay
  positioning is NOT implemented by this composition.
- `IllustratedMessage`: vertical and responsive horizontal layouts with three
  sizes and optional button-group actions. Uses original geometric native vector
  shapes, not Adobe's proprietary illustration pack or gradient assets.
- Full gallery smoke: `node tools/ui-tests/spectrum2-kit-smoke.cjs`
  or `node tools/dev/test_ui_adobe_spectrum2.cjs`.
- The actual Minecraft Java 25 build, visual pixel matching, accessibility
  API parity and in-game FPS profiling remain unverified.

References:
- https://spectrum.adobe.com/web/rsp/components/accordion
- https://spectrum.adobe.com/web/rsp/components/action-bar
- https://spectrum.adobe.com/web/rsp/components/illustrated-message

## Dev batch — scroll-safe retained Accordion, kit-scoped search, and new primitives (cako baseline)

- **Accordion scrolling fix:** `UiRuntime.render(context, afterScrollLayout)` invokes a
  host-owned paint/bounds patch callback after its smooth-scroll tick and layout,
  before input hover and draw. Previously the scroll tick recomputed the
  unanimated JS-template geometry *after* Java patched Accordion, leaving rows
  misplaced until scrolling stopped. The original `render(context)` API remains
  unchanged for all other callers. Disclosure animation still does not re-run JS.
- **Component catalog search:** one search field in the neutral catalog sidebar,
  scoped to the selected UI kit; filters both category headers and page entries.
  Google and Adobe have separately retained queries. Text editing, caret hit
  testing, clipboard, IME and focus use `SingleLineTextInput` and
  `CombatantTextInputBridge`. An empty result is rendered explicitly, and the
  currently opened preview stays displayed when navigation is filtered.
- **InlineAlert:** semantic informative/positive/notice/negative presentations,
  responsive heights and a native action button (Retry example).
- **Skeleton:** text, rectangle and circular loading placeholders. They remain
  static rather than forcing JS/timer/layout updates every frame.
- **LabeledValue:** vertical/horizontal read-only property/value pairs.
- **ColorSwatch:** semantic color samples with S2-inspired size and square/circle
  variants. It is non-interactive; no ColorSwatchPicker behavior is implied.

Validation: `node tools/dev/test_ui_adobe_spectrum2.cjs` exercises all Spectrum
pages with the actual inline-import contract at five viewport sizes, verifies
catalog filtering for both kits, and checks the runtime scroll/layout/patch
ordering. `test_ui_component_gallery.cjs` and `test_ui_material3_overlays.cjs`
provide additional Google Material 3 regression coverage. None of the above is
an in-game GPU/FPS or pixel-perfect visual test.

Component design references:
- https://react-spectrum.adobe.com/InlineAlert
- https://react-spectrum.adobe.com/Skeleton
- https://react-spectrum.adobe.com/LabeledValue
- https://react-spectrum.adobe.com/ColorSwatch

## Correctness pass — ActionButtonGroup, InlineAlert, ColorSwatch (cakp)

This section **supersedes** the prior provisional ActionGroup, InlineAlert and
ColorSwatch descriptions above. These are independent Combatant adaptations of
Spectrum 2's options; they are not Adobe's React components.

- **ActionButtonGroup:** `density=compact` + non-quiet creates one connected
  outlined group with real exterior corner geometry and interior dividers (no
  overlapping individual outlines). `density=regular` keeps separated buttons.
  Controlled `selectionMode`, `enableSelection`, `isEmphasized`, `isJustified`,
  `isQuiet`, `isDisabled`, vertical layout, wrapping and individually disabled
  members are supported; the JS renderer retains no selection state between calls.
  Overflow *collapse-to-menu* is not implemented; wrapping is supported.
- **InlineAlert:** semantic `neutral`, `informative`, `positive`, `notice`,
  `negative` variants and `fillStyle=border|subtleFill|boldFill`. Heading and
  content are paired with an optional semantic icon on the trailing side.
  Semantic variants use the same *leading clipped card accent* as Map Browser's
  Triangulation mode cards: a full-size rounded surface painted through a 4px
  retained-tree `clip` container. Its visible left edge inherits the card's
  exact radius, rather than resembling an independent rounded strip. The clip
  is owned by `UiRenderer` / `UiRuntimeScissorStack` (not a component-side
  `ScissorFunction` call). There is no squircle or compound SDF involved.
  The optional host action is retained for compatibility.
- **ColorSwatch / ColorSwatchGroup:** `size=XS|S|M|L`; sample `shape=square` or
  `rectangle` (2:1); independent `rounding=none|default|full`; controlled
  selected and disabled states. `density=compact|regular|spacious` belongs to
  the group, changing spacing, not color dimensions or corner rounding.
  Gallery options and sample selection are controlled by Java action router and
  do not introduce a per-frame JS clock. This is a native **swatch sample
  group**, not a complete accessible ColorSwatchPicker / ColorPicker.

Run `node tools/dev/test_ui_adobe_spectrum2.cjs` to validate the actual inline
loader contract across Spectrum pages and the new option combinations. Verify
GPU silhouette/color and focus-visible behavior in-game with Java 25 before
claiming visual or accessibility parity.

References:
- https://spectrum.adobe.com/web/rsp/components/action-group
- https://spectrum.adobe.com/web/rsp/components/in-line-alert
- https://react-spectrum.adobe.com/InlineAlert
- https://spectrum.adobe.com/web/rsp/components/swatch
- https://spectrum.adobe.com/web/rsp/components/swatch-group

## Checkbox Group and Segmented Control (caks baseline)

New retained components are registered in the Spectrum 2 selection catalog:

- `checkbox-group.js`: form-field legend, required/optional indicators, multiple
  selection, responsive horizontal/vertical arrangements, item/group disabled
  states, descriptions and validation errors. It composes the existing native
  `SpectrumCheckbox` rather than duplicating checkbox drawing or input handlers.
- `segmented-control.js`: view switching with one selected item, independent
  track and trackless layouts, horizontal/vertical orientation and disabled
  items. It is not an ActionGroup: activating a segment changes the current view.

The Java host owns selection state and registers every action through the existing
`UiRuntime` action router. Keyboard activation and arrow navigation run through
`UiComponentLabSection`'s current input dispatcher, with no JS timers, polling,
secondary input system or per-frame model fields. Both components are demo
implementations; icon-only variants, full RTL support and Adobe's upstream
accessibility/runtime semantics are not claimed.

References:
- https://spectrum.adobe.com/web/rsp/components/checkbox-group
- https://spectrum.adobe.com/web/rsp/components/segmented-control

## Extended data and date controls (cakd baseline)

Added five independent retained components, surfaced in the Adobe gallery and
registered in its Java action router:

- `Table` (`table.js`): selectable rows (single or multiple), checkbox and
  highlight selection, header sorting, compact/regular/spacious row density,
  quiet appearance, row-level disabled state, right-aligned numeric column.
  Data ordering and selected keys are controlled by Java. The example uses a
  small fixed collection; large virtualized collections and drag reordering
  are outside this batch.
- `NumberField` (`number-field.js`): text editing through Combatant's
  `SingleLineTextInput`/IME/clipboard pipeline, native stepper actions,
  0–100 clamping in the Java host, size variants, read-only/disabled and
  validation displays. Locale-specific formatting, arbitrary arithmetic
  expressions and unit conversion are **not yet implemented**.
- `ComboBox` (`combo-box.js`): actual host-edited filter text, an anchored
  and viewport-flipping results popover, disabled state, zero-results state,
  arrow navigation, Enter to choose, Escape to dismiss, and outside-click.
  The current example is a finite in-memory collection, not a remote query.
- `Calendar` (`calendar.js`): deterministic Gregorian grid with leap years,
  configurable first weekday, date bounds, controlled selection and previous/
  next navigation. The JS calendar uses no Date allocations or timers.
- `DatePicker` (`date-picker.js`): single-date and range popovers, chronological
  range normalization, day navigation with arrows and month paging with
  PageUp/PageDown, bounds, and read-only/disabled field variants. The current
  adaptation uses one month and calendar-based selection; segment-by-segment
  date typing, time fields, multiple visible months, and timezone support
  remain unimplemented.

Runtime: the Java host owns the state and reuses the existing input dispatcher,
keyboard constants, and text-input bridge. New JS components do not install
animation loops, timers or a parallel diff/layout loop. Their model properties
change only in response to editing or user actions; reading the same model
must not cause per-frame JS rebuilds. The host only constructs the ComboBox
suggestion list while the relevant overlay is open.

Source specifications:
- https://spectrum.adobe.com/web/rsp/components/table
- https://spectrum.adobe.com/web/rsp/components/number-field
- https://spectrum.adobe.com/web/rsp/components/combo-box
- https://spectrum.adobe.com/web/rsp/components/date-picker

Validation: `node tools/dev/test_ui_adobe_spectrum2.cjs` (current catalog,
all kit pages, 5 viewport sizes, dynamic overlays and date boundaries), plus
`node tools/ui-tests/m3-kit-smoke.cjs`. JS tree validation and Java source
parsing do not replace a Java 25 build or Minecraft GPU A/B screenshots.

## Large component batch — range, collections and disclosure (cakf baseline)

Independent Combatant retained-UI implementations and catalog pages:

- `RangeSlider`: bounded lower/upper values, native Java pointer dragging of the
  nearest thumb and keyboard adjustments through `InputKeys`. Shift+arrows edit
  the upper thumb; Home/End move the active thumb to the constraint. Dragging
  changes state only on pointer movement, not during idle frames.
- `ToggleButtonGroup`: single and multiple controlled selection, compact joined
  borders, regular separated controls, justified sizing and disabled entries.
- `SelectBoxGroup`: distinct card layout with selected state, single/multiple
  selection, disabled options and adaptive columns.
- `TagGroup`: controlled multi-selection, independent remove controls, wrapping,
  `maxRows` overflow and a reset action in the gallery.
- `Disclosure`: individually controlled headers with quiet/disabled variants,
  flat separators, clipped panels and no JS timer.

The five modules use the existing `SpectrumFoundation` primitives, Java action
router, retained `UiNode` tree and shared component search. No new renderer,
scissor call, JavaScript timer or per-frame signature input was introduced.

Coverage: `node tools/dev/test_ui_adobe_spectrum2.cjs` walks every registered
Spectrum page (51 pages, five viewport sizes) and additional state variations.
The Material 3 regression tests are unchanged.

Reference APIs:
- https://react-spectrum.adobe.com/RangeSlider
- https://react-spectrum.adobe.com/ToggleButtonGroup
- https://react-spectrum.adobe.com/SelectBoxGroup
- https://react-spectrum.adobe.com/TagGroup
- https://react-spectrum.adobe.com/Disclosure

Remaining gaps: these are native previews, not React Spectrum widgets; per-thumb
accessibility/ARIA and exact Adobe motion are not implemented. Disclosure
currently expands immediately on activation to avoid the previous accordion's
full-tree per-frame reconcile issue. Full in-game visual, FPS and Java 25 build
verification are still outstanding.

## Color controls (cakj(3) baseline)

Five independent native components share one Java-host-owned HSV + alpha state:

- `ColorArea`: saturation/brightness selection using one four-corner native GPU
  gradient quad, a native handle, and pointer / arrow-key input.
- `ColorSlider`: hue (six native linear gradient strips), opacity (transparent
  checkerboard), horizontal or vertical hue track, and keyboard input.
- `ColorWheel`: static 512x512 texture (`textures/ui/spectrum2/hue_wheel.png`)
  created for this implementation, and a single native moveable handle. No
  per-frame JS tessellation or angle-specific cached textures.
- `ColorField`: hex RGB editor with native `SingleLineTextInput` / IME bridge,
  error state and read-only / disabled variants.
- `ColorSwatchPicker`: selectable preset palette built on the existing
  `SpectrumColorSwatchGroup`, with host-owned selected ID.

These previews share RGB/HSV math and use the existing mouse and keyboard
routing. Dragging submits changes only when the integer hue/S/V/alpha changes;
no color control schedules its own timer. Dragging **does** still force the
normal gallery JS reconcile when the model changes. Achieving sub-tree-only
reactive updates and full keyboard accessibility metadata requires more work.

Design references:
- https://spectrum.adobe.com/web/rsp/components/color-area
- https://spectrum.adobe.com/web/rsp/components/color-slider
- https://spectrum.adobe.com/web/rsp/components/color-wheel
- https://spectrum.adobe.com/web/rsp/components/color-field
- https://spectrum.adobe.com/web/rsp/components/swatch-group

Rendering and platform differences: values are 8-bit sRGB; no wide-gamut
color management, segment-based NumberField color channels, screen color
sampling, alpha checker patterns in every swatch, React Aria semantics or
pixel-matched Adobe theme measurements. Verify the texture in game because
this environment cannot execute Minecraft's rendering pipeline.

## Collection and contextual controls (cakk baseline)

- `ListView`: stable-key items, compact/regular/spacious row density, single and
  multiple host-controlled selection, highlight/checkbox styles, quiet surface,
  disabled rows, descriptive secondary text and native keyboard focus.
- `CardView`: adaptive grid with stable item IDs, native preview color blocks,
  selected/disabled cards, multiple and single selection and compact sizing.
  This demo does **not** implement drag-and-drop, masonry/waterfall layout,
  virtualization or image loading.
- `RadioGroup`: host-controlled field, required/optional label, horizontal or
  vertical layout, emphasis, validation, disabled members and arrow navigation.
- `ContextualHelp`: small info/help trigger with a host-anchored, viewport-flipping
  popover; Escape/outside click dismiss it. Full React Aria focus containment and
  screen-reader announcements are not reproduced by the native runtime.
- `LinkButton`: navigational visual affordance with primary/secondary/quiet and
  disabled states; in this dev preview actions report to the gallery rather
  than opening an external URL.

The gallery's Java host owns collection state, action dispatch and keyboard
navigation. No new animation clock, JS timer, per-frame value or render hook
was introduced. The real inline-loader test enumerates current Adobe navigation
pages instead of obsolete `selection/inputs/navigation` IDs and also tests
anchor overlays at multiple viewport sizes.

References:
- https://react-spectrum.adobe.com/ListView
- https://react-spectrum.adobe.com/CardView
- https://react-spectrum.adobe.com/RadioGroup
- https://react-spectrum.adobe.com/ContextualHelp
- https://react-spectrum.adobe.com/LinkButton

## Catalog search / Popover / Standard Dialog (cakl baseline)

- Component catalog `Ctrl/Cmd+F` uses the normalized Combatant shortcut mask and
  focuses the existing per-kit `SingleLineTextInput`, selecting the prior query.
- Search's editable text, selection (native editor + `TextSelection` paint snapshot),
  caret and IME preedit are drawn inside the retained `clip overflow-hidden`
  text viewport. Horizontal offsets use the editor's own measured layout.
  The clear button remains outside the clipped region.
- Added native `SpectrumPopover` with anchored top/bottom/left/right positioning,
  viewport-aware side flipping, outside-click and Escape dismissal.
- Added native `SpectrumStandardDialog` with S/M/L widths, content, actions,
  keyboard focus traversal, and modal pointer blocking. These are separate from
  the existing alert-dialog and contextual-help components.
- No new per-frame JavaScript clocks or polling. Full Minecraft visual and
  keyboard/IME integration checks remain necessary to certify parity.

References:
- https://spectrum.adobe.com/web/rsp/components/popover
- https://spectrum.adobe.com/web/rsp/components/standard-dialog
