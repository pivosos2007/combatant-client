/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.features.gui.schematic;

import combatant.client.render.engine.text.BuiltinFontCatalog;

import combatant.client.render.engine.renderer.ui.draw.LayoutRender2D;

import combatant.client.automation.compat.litematica.LitematicaCompat;
import combatant.client.automation.compat.litematica.LitematicaWorkspace;
import combatant.client.features.gui.clickgui.settings.ColorPresetRepository;
import combatant.client.util.filedialog.NativeFileDialogService;
import net.minecraft.client.Minecraft;
import java.nio.file.Path;
import combatant.client.input.CombatantInput;
import combatant.client.input.InputDevice;
import combatant.client.input.InputKeys;
import combatant.client.input.InputModifierMask;
import combatant.client.input.minecraft.CombatantTextInputBridge;
import combatant.client.render.engine.renderer.ui.runtime.core.UiRuntime;
import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;
import combatant.client.render.engine.renderer.ui.runtime.core.UiBounds;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.util.text.SingleLineTextInput;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class LitematicaSettingsPanel {
    private static final String[] GROUPS = {"generic", "visuals", "overlays", "colors", "hotkeys"};
    private static final String[] LABELS = {"General", "Visuals", "Info overlays", "Colors", "Hotkeys"};
    private static final long REFRESH_NANOS = 1_000_000_000L;
    private static final int ROW_HEIGHT = 78;
    private static final int COLOR_EXTRA_STACKED = 456;
    private static final int COLOR_EXTRA_WIDE = 274;
    private static final int CHOICE_EXTRA = 178;
    private final SingleLineTextInput filter = new SingleLineTextInput(120);
    private final SingleLineTextInput edit = new SingleLineTextInput(192);
    private String group = GROUPS[0], editing = "", editingSection = "", expanded = "", error = "";
    private String captureId = "", errorOption = "";
    private final List<Integer> capturedKeys = new ArrayList<>();
    private boolean pendingPointerCaret;
    private boolean editFocused;
    // Keep JS snapshots stable during color dragging; paint the live value onto retained nodes.
    private String dragStartColorValue = "";
    private float lastPressedX;
    private boolean filtering;
    private TextRenderer textRenderer;
    private long textRendererGeneration = -1L;
    private List<LitematicaWorkspace.Option> cached = List.of();
    private List<LitematicaWorkspace.Option> shown = List.of();
    private long cachedAt;
    private String lastQuery = null;
    private float scrollY;
    private float viewportHeight = 600f;
    private float viewportWidth = 700f;
    private int rowHeight() {return viewportWidth < 440f ? 112 : ROW_HEIGHT;}
    private UiBounds editBounds;
    private UiBounds editingRowBounds;
    private UiBounds expandedBounds;
    private UiBounds colorAreaBounds, colorHueBounds, colorAlphaBounds;
    private UiBounds draggingColorBounds;
    private String draggingColorChannel = "";
    private List<LitematicaWorkspace.Option> visible = List.of();
    private List<Integer> presetArgb = List.of();
    private List<String> presetHex = List.of();
    private long presetsCheckedAt;

    private void refreshPresets(boolean force) {
        long now = System.nanoTime();
        if (!force && now - presetsCheckedAt < REFRESH_NANOS) return;
        presetsCheckedAt = now;
        List<Integer> next = ColorPresetRepository.load();
        if (next.equals(presetArgb)) return;
        presetArgb = next;
        presetHex = next.stream()
                .map(color -> String.format(Locale.ROOT, "#%08X", color)).toList();
    }

    private static Map<String,Object> props(Object... pairs) {
        Map<String,Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) out.put((String)pairs[i], pairs[i+1]);
        return out;
    }
    public String selectedGroup() { return group; }
    public String selectedGroupLabel() {
        for (int i=0; i<GROUPS.length; i++) if (group.equals(GROUPS[i])) return LABELS[i];
        return LABELS[0];
    }
    public void selectGroup(String target) {
        for (String value : GROUPS) if (value.equals(target)) {
            if (!value.equals(group)) {
                cancelEdit(); captureId = ""; capturedKeys.clear(); filter.clear(); filter.unfocus(); filtering = false;
                CombatantTextInputBridge.blur(filter);
                group = value; expanded = ""; scrollY = 0f;
                lastQuery = null; error = ""; invalidate();
            }
            return;
        }
    }
    private void invalidate() { cachedAt = 0L; }
    private int expandedIndex() {
        for (int i = 0; i < shown.size(); i++) if (shown.get(i).id().equals(expanded)) return i;
        return -1;
    }
    private int expandedHeight() {
        int i = expandedIndex();
        if (i < 0) return 0;
        if (shown.get(i).type().equals("choice")) return CHOICE_EXTRA;
        float contentWidth = viewportWidth - 13f;
        return contentWidth >= 720f ? COLOR_EXTRA_WIDE : COLOR_EXTRA_STACKED;
    }
    private static UiNode find(UiNode node, String key) {
        if (node == null) return null;
        if (key.equals(node.key())) return node;
        for (UiNode child : node.children()) {
            UiNode match = find(child, key);
            if (match != null) return match;
        }
        return null;
    }
    public void syncViewport(UiRuntime runtime) {
        if (runtime == null) return;
        UiNode scrolling = find(runtime.root(), "lite:scroll:" + group);
        if (scrolling != null) {
            scrollY = Math.max(0f, scrolling.state().scrollY());
            viewportHeight = Math.max(80f, scrolling.bounds().height());
            viewportWidth = Math.max(1f, scrolling.bounds().width());
        } else scrollY = 0f;
    }
    public Map<String,Object> snapshot() {
        LitematicaWorkspace bridge = LitematicaCompat.workspace();
        long now = System.nanoTime();
        if (bridge != null && (cachedAt == 0L || now - cachedAt >= REFRESH_NANOS)) {
            cached = bridge.options(group);
            cachedAt = now;
            lastQuery = null;
        }
        String query = filter.text().strip().toLowerCase(Locale.ROOT);
        if (!query.equals(lastQuery)) {
            shown = query.isEmpty() ? cached : cached.stream().filter(o ->
                    o.name().toLowerCase(Locale.ROOT).contains(query) ||
                    o.id().toLowerCase(Locale.ROOT).contains(query) ||
                    o.description().toLowerCase(Locale.ROOT).contains(query)).toList();
            lastQuery = query;
        }
        int ei = expandedIndex(), extra = expandedHeight(), rowHeight = rowHeight();
        if (ei >= 0 && shown.get(ei).type().equals("color")) refreshPresets(false);
        String displayValue = draggingColorChannel.isEmpty() ? edit.text() : dragStartColorValue;
        float adjusted = scrollY - (ei >= 0 && scrollY > ei * rowHeight + rowHeight ? extra : 0);
        int start = Math.max(0, (int)(Math.max(0, adjusted) / rowHeight) - 2);
        int end = Math.min(shown.size(), start + (int)Math.ceil(viewportHeight / rowHeight) + 9);
        visible = shown.subList(Math.min(start, shown.size()), Math.max(Math.min(start, shown.size()), end));
        List<Map<String,Object>> options = new ArrayList<>(visible.size());
        for (int i = 0; i < visible.size(); i++) {
            LitematicaWorkspace.Option option = visible.get(i);
            options.add(props("id", option.id(), "label", option.name(), "type", option.type(),
                    "value", option.id().equals(editing) ? displayValue : option.value(),
                    "description", option.description(), "editing", option.id().equals(editing),
                    "expanded", option.id().equals(expanded), "index", start + i,
                    "choices", option.choices(), "min", option.min(), "max", option.max(),
                    "capturing", option.id().equals(captureId),
                    "invalid", option.id().equals(errorOption)));
        }
        return props("category", group, "categoryLabel", selectedGroupLabel(), "options", options,
                "count", shown.size(), "total", shown.size(), "rowHeight", rowHeight,
                "expandedIndex", ei,
                "expandedHeight", extra, "search", filter.text(), "searchFocused", filtering,
                "searchScroll", filter.viewportOffset(),
                "searchCaret", measure(filter.text(), filter.cursor()),
                "searchSelectionStart", measure(filter.text(), filter.selectionStart()),
                "searchSelectionEnd", measure(filter.text(), filter.selectionEnd()),
                "editing", editing, "editValue", displayValue, "editFocused", editFocused,
                "editScroll", edit.viewportOffset(), "editCaret", measure(edit.text(), edit.cursor()),
                "editSelectionStart", measure(edit.text(), edit.selectionStart()),
                "editSelectionEnd", measure(edit.text(), edit.selectionEnd()), "error", error,
                "colorPresets", ei >= 0 && shown.get(ei).type().equals("color")
                        ? presetHex : List.of(),
                "captureId", captureId, "capturePreview", captureLabel(),
                "errorPulse", errorOption.isEmpty() ? 0f :
                        .45f + .55f * (float)Math.sin(now / 220_000_000d));
    }
    private float measure(String value, int cursor) {
        if (value == null || value.isEmpty() || cursor <= 0) return 0f;
        long generation = Fonts.generation();
        if (textRenderer == null || textRendererGeneration != generation) {
            textRenderer = Fonts.renderer("SourceSans3", FontInfo.Type.Regular,
                    BuiltinFontCatalog.ONEST_MEDIUM.renderer());
            textRendererGeneration = generation;
        }
        return LayoutRender2D.textWidth(textRenderer,
                value.substring(0, Math.min(cursor, value.length())), 17f);
    }
    public void registerActions(UiRuntime runtime) {
        if (runtime == null) return;
        register(runtime, "search", () -> { filtering = true; cancelEdit(); });
        register(runtime, "search-clear", () -> { filter.clear(); filtering = true; });
        register(runtime, "edit-focus", () -> { if (!editing.isEmpty()) editFocused = true; });
        register(runtime, "edit-apply", this::applyEdit);
        register(runtime, "edit-cancel", this::cancelEdit);
        for (LitematicaWorkspace.Option option : visible) {
            register(runtime, "option:" + option.id(), () -> {
                LitematicaWorkspace bridge = LitematicaCompat.workspace();
                if (bridge == null) return;
                if (option.type().equals("boolean")) {
                    if (!bridge.toggleOption(group, option.id())) error = "Litematica rejected the change";
                    else {error = ""; invalidate();}
                } else if (option.type().equals("choice")) {
                    cancelEdit();
                    if (option.choices().isEmpty()) {
                        if (!bridge.toggleOption(group, option.id())) error = "Litematica rejected the change";
                        else {error = ""; invalidate();}
                    } else expanded = expanded.equals(option.id()) ? "" : option.id();
                } else if (option.type().equals("color")) {
                    if (expanded.equals(option.id())) { cancelEdit(); expanded = ""; }
                    else { expanded = option.id(); beginEdit(option); }
                } else if (option.type().equals("hotkey")) {
                    cancelEdit(); expanded = "";
                    captureId = option.id(); capturedKeys.clear(); error = ""; errorOption = "";
                } else if (option.type().equals("directory")) {
                    chooseDirectory(option);
                } else if (!option.id().equals(editing)) beginEdit(option);
            });
            if (option.type().equals("number")) for (int direction : new int[]{-1,1}) {
                register(runtime, "number:"+option.id()+":"+(direction>0?"up":"down"), () -> {
                    LitematicaWorkspace bridge = LitematicaCompat.workspace();
                    if (bridge == null) return;
                    try {
                        String value = editing.equals(option.id()) ? edit.text() : option.value();
                        java.math.BigDecimal current = new java.math.BigDecimal(value.strip());
                        java.math.BigDecimal step = current.scale() > 0 ?
                                java.math.BigDecimal.ONE.movePointLeft(Math.min(3,current.scale())) :
                                java.math.BigDecimal.ONE;
                        String next = current.add(step.multiply(java.math.BigDecimal.valueOf(direction)))
                                .stripTrailingZeros().toPlainString();
                        if (bridge.setOption(group,option.id(),next)) {
                            if (editing.equals(option.id())) edit.setText(next);
                            error = ""; invalidate();
                        } else invalid(option.id(), "Value outside permitted range" );
                    } catch (NumberFormatException ex) {invalid(option.id(), "Invalid numeric value");}
                });
            }
            if (option.type().equals("choice")) for (int i=0; i<option.choices().size(); i++) {
                String choice = option.choices().get(i);
                register(runtime, "choice:"+option.id()+":"+i, () -> {
                    LitematicaWorkspace bridge = LitematicaCompat.workspace();
                    if (bridge != null && bridge.setOption(group,option.id(),choice)) {
                        expanded = ""; error = ""; invalidate();
                    } else invalid(option.id(), "Could not apply selection");
                });
            }
            if (option.type().equals("color")) {
                for (int i = 0; i < presetArgb.size(); i++) {
                    int preset = presetArgb.get(i);
                    register(runtime, "color:"+option.id()+":"+i, () -> {
                        if (editing.equals(option.id())) {
                            edit.setText(String.format(Locale.ROOT, "#%08X", preset));
                            edit.moveToEnd(); error = ""; errorOption = "";
                        }
                    });
                }
                for (String channel : List.of("area", "hue", "alpha"))
                    register(runtime, "color-"+channel+":"+option.id(), () -> { /* Handled by mouse drag capture */ });
                register(runtime, "color-save:"+option.id(), () -> {
                    try {
                        ColorPresetRepository.save(parseColor(edit.text()));
                        refreshPresets(true);
                        error = ""; errorOption = "";
                    } catch (IllegalArgumentException ex) { invalid(option.id(), "Invalid HEX color"); }
                });
                register(runtime, "color-remove:"+option.id(), () -> {
                    try {
                        ColorPresetRepository.delete(parseColor(edit.text()));
                        refreshPresets(true);
                    }
                    catch (IllegalArgumentException ex) { invalid(option.id(), "Invalid HEX color"); }
                });
            }
        }
    }
    private void beginEdit(LitematicaWorkspace.Option option) {
        cancelEdit(); editing = option.id(); editingSection = group;
        edit.setText(option.value()); edit.moveToEnd();
        editFocused = !option.type().equals("color");
        pendingPointerCaret = editFocused;
        error = ""; errorOption = ""; filtering = false;
        filter.unfocus(); CombatantTextInputBridge.blur(filter);
    }
    private static void register(UiRuntime runtime, String name, Runnable runnable) {
        runtime.actions().register("schematic:lite-"+name, ctx -> {runnable.run();return true;});
    }
    public void updateInput(float x, float y, float w, float h, TextRenderer font, UiRuntime runtime) {
        if (font == null || w <= 0 || h <= 0) return;
        textRenderer = font;
        textRendererGeneration = Fonts.generation();
        float searchW = Math.min(300f, Math.max(220f, w - 48f));
        float searchX = w < 720f ? x + 24f : x + w - searchW - 24f;
        float searchY = y + (w < 720f ? 67f : 17f);
        filter.layout(searchX + 43f, searchW - 88f, font, 17f);
        if (filtering) CombatantTextInputBridge.focus(filter,
                Math.round(searchX), Math.round(searchY),
                Math.round(searchX + searchW), Math.round(searchY + 44f));
        else CombatantTextInputBridge.blur(filter);
        UiNode node = editing.isEmpty() || runtime == null ? null : find(runtime.root(), "lite:inline:"+editing);
        editBounds = node == null ? null : node.bounds();
        UiNode editingRow = editing.isEmpty() || runtime == null ? null :
                find(runtime.root(), "lite:row:"+editing+":surface");
        editingRowBounds = editingRow == null ? null : editingRow.bounds();
        UiNode openRow = expanded.isEmpty() || runtime == null ? null :
                find(runtime.root(), "lite:row:"+expanded+":surface");
        expandedBounds = openRow == null ? null : openRow.bounds();
        UiNode area = expanded.isEmpty() || runtime == null ? null : find(runtime.root(),"lite:color:area:"+expanded);
        UiNode hue = expanded.isEmpty() || runtime == null ? null : find(runtime.root(),"lite:color:hue:"+expanded);
        UiNode alpha = expanded.isEmpty() || runtime == null ? null : find(runtime.root(),"lite:color:alpha:"+expanded);
        colorAreaBounds = area == null ? null : area.bounds();
        colorHueBounds = hue == null ? null : hue.bounds();
        colorAlphaBounds = alpha == null ? null : alpha.bounds();
        if (editBounds != null && editBounds.width() > 0f) {
            edit.layout(editBounds.x()+13f, Math.max(1f,editBounds.width()-26f),font,17f);
            if (editFocused && pendingPointerCaret) {
                edit.beginDrag(lastPressedX, false);
                SingleLineTextInput.mouseReleased();
                pendingPointerCaret = false;
            }
            if (editFocused) CombatantTextInputBridge.focus(edit,
                    Math.round(editBounds.x()), Math.round(editBounds.y()),
                    Math.round(editBounds.right()), Math.round(editBounds.bottom()));
            else CombatantTextInputBridge.blur(edit);
        } else CombatantTextInputBridge.blur(edit);
    }
    public boolean mousePressed(float mx, float my, int button, float x, float y, float w, float h) {
        lastPressedX = mx;
        if (!captureId.isEmpty() && button != 0) {
            int sdlButton = button == 1 ? 3 : button == 2 ? 2 : button + 1;
            addCapture(sdlButton - 100);
            return true;
        }
        if (button != 0) return false;
        float searchW = Math.min(300f, Math.max(220f, w - 48f));
        float sx = w < 720f ? x + 24f : x + w - searchW - 24f;
        float sy = y + (w < 720f ? 67f : 17f);
        if (inside(mx,my,sx,sy,searchW,44f)) {
            filtering = true; cancelEdit();
            if (mx >= sx + searchW - 41f && !filter.text().isEmpty()) {
                filter.clear();
            } else filter.beginDrag(mx,shift());
            return true;
        }
        // Clicking anywhere else leaves search mode immediately.
        if (filtering) {
            filtering = false; filter.unfocus(); CombatantTextInputBridge.blur(filter);
        }
        if (!editing.isEmpty()) {
            if (expanded.equals(editing)) {
                if (beginColorDrag(mx,my,"area",colorAreaBounds)) return true;
                if (beginColorDrag(mx,my,"hue",colorHueBounds)) return true;
                if (beginColorDrag(mx,my,"alpha",colorAlphaBounds)) return true;
            }
            if (editBounds != null && inside(mx,my,editBounds.x(),editBounds.y(),editBounds.width(),editBounds.height())) {
                editFocused = true;
                edit.beginDrag(mx,shift());
                return true;
            }
            if (editFocused) {
                editFocused = false;
                edit.unfocus();
                CombatantTextInputBridge.blur(edit);
            }
            if (editingRowBounds == null || !inside(mx,my,editingRowBounds.x(),editingRowBounds.y(),
                    editingRowBounds.width(),editingRowBounds.height())) {
                if (expanded.equals(editing)) {
                    cancelEdit();
                    expanded = "";
                } else applyEdit();
            }
        }
        if (!expanded.isEmpty() && expandedBounds != null &&
                !inside(mx,my,expandedBounds.x(),expandedBounds.y(),expandedBounds.width(),expandedBounds.height()))
            expanded = "";
        return false;
    }
    private static float clamp(float value,float min,float max) {
        return Math.max(min,Math.min(max,value));
    }
    private boolean beginColorDrag(float mx,float my,String channel,UiBounds bounds) {
        if (bounds == null || !inside(mx,my,bounds.x(),bounds.y(),bounds.width(),bounds.height())) return false;
        draggingColorChannel = channel;
        draggingColorBounds = bounds;
        dragStartColorValue = edit.text();
        editFocused = false;
        edit.unfocus();
        CombatantTextInputBridge.blur(edit);
        updateColor(mx,my);
        return true;
    }
    private void updateColor(float mx,float my) {
        if (draggingColorBounds == null || draggingColorChannel.isEmpty() || editing.isEmpty()) return;
        String value=edit.text().strip().replaceFirst("(?i)^(#|0x)","");
        if (!value.matches("(?i)[0-9a-f]{6}([0-9a-f]{2})?")) value="FFFFFFFF";
        if (value.length()==6) value="FF"+value;
        long packed=Long.parseLong(value,16);
        int a=(int)(packed>>>24)&0xFF;
        float r=((packed>>>16)&0xFF)/255f,g=((packed>>>8)&0xFF)/255f,b=(packed&0xFF)/255f;
        float max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b)),d=max-min;
        float h=0f;
        if (d>0f) {
            if (max==r) h=((g-b)/d)%6f;
            else if (max==g) h=(b-r)/d+2f;
            else h=(r-g)/d+4f;
            h=(h*60f+360f)%360f;
        }
        float saturation=max==0f?0f:d/max,brightness=max;
        UiBounds area=draggingColorBounds;
        switch (draggingColorChannel) {
            case "area" -> {
                saturation=clamp((mx-area.x())/Math.max(1f,area.width()),0f,1f);
                brightness=1f-clamp((my-area.y())/Math.max(1f,area.height()),0f,1f);
            }
            case "hue" -> h=(1f-clamp((my-area.y())/Math.max(1f,area.height()),0f,1f))*360f;
            case "alpha" -> a=Math.round((1f-clamp((my-area.y())/Math.max(1f,area.height()),0f,1f))*255f);
            default -> {return;}
        }
        float chroma=brightness*saturation,x=chroma*(1f-Math.abs((h/60f)%2f-1f)),m=brightness-chroma;
        float rr,gg,bb;
        if (h<60f){rr=chroma;gg=x;bb=0f;}
        else if(h<120f){rr=x;gg=chroma;bb=0f;}
        else if(h<180f){rr=0f;gg=chroma;bb=x;}
        else if(h<240f){rr=0f;gg=x;bb=chroma;}
        else if(h<300f){rr=x;gg=0f;bb=chroma;}
        else {rr=chroma;gg=0f;bb=x;}
        int rgb=(Math.round((rr+m)*255f)<<16)|(Math.round((gg+m)*255f)<<8)|Math.round((bb+m)*255f);
        edit.setText(String.format(Locale.ROOT,"#%08X",((long)a<<24)|(rgb&0xFFFFFFL)));
    }
    public void paintLiveColor(UiRuntime runtime) {
        if (runtime == null || editing.isEmpty() || !expanded.equals(editing)) return;
        UiNode root = runtime.root();
        UiNode area = find(root, "lite:color:area:" + editing);
        if (area == null) return;
        int argb;
        try { argb = parseColor(edit.text()); }
        catch (IllegalArgumentException ex) { return; }
        int alpha = (argb >>> 24) & 255;
        int red = (argb >>> 16) & 255, green = (argb >>> 8) & 255, blue = argb & 255;
        float r=red/255f, g=green/255f, b=blue/255f;
        float max=Math.max(r,Math.max(g,b)), min=Math.min(r,Math.min(g,b)), chroma=max-min;
        float hue=0f;
        if (chroma>0f) {
            if (max==r) hue=((g-b)/chroma)%6f;
            else if(max==g) hue=(b-r)/chroma+2f;
            else hue=(r-g)/chroma+4f;
            hue=(hue*60f+360f)%360f;
        }
        float saturation=max==0f?0f:chroma/max;
        String current=String.format(Locale.ROOT,"#%08X",argb);
        String rgb=String.format(Locale.ROOT,"#FF%06X",argb&0xFFFFFF);
        String hueRgb=String.format(Locale.ROOT,"#FF%06X",java.awt.Color.HSBtoRGB(hue/360f,1f,1f)&0xFFFFFF);
        UiNode gradient=find(root,"lite:color:area:"+editing+":gradient");
        if(gradient!=null) gradient.patchProp("topRightColor",hueRgb);
        paintHandle(root,"lite:color:area:"+editing,
                area.bounds().x()+area.bounds().width()*saturation,
                area.bounds().y()+area.bounds().height()*(1f-max),rgb);
        UiNode hueSlider=find(root,"lite:color:hue:"+editing);
        if(hueSlider!=null) paintHandle(root,"lite:color:hue:"+editing,
                hueSlider.bounds().x()+25f,
                hueSlider.bounds().y()+hueSlider.bounds().height()*(1f-hue/360f),hueRgb);
        UiNode alphaSlider=find(root,"lite:color:alpha:"+editing);
        if(alphaSlider!=null) {
            paintHandle(root,"lite:color:alpha:"+editing,
                    alphaSlider.bounds().x()+25f,
                    alphaSlider.bounds().y()+alphaSlider.bounds().height()*(1f-alpha/255f),current);
            UiNode alphaGradient=find(root,"lite:color:alpha:"+editing+":gradient:0");
            if(alphaGradient!=null){
                alphaGradient.patchProp("startColor",rgb);
                alphaGradient.patchProp("endColor","#00"+rgb.substring(3));
            }
        }
        UiNode inlineText=find(root,"lite:inline:"+editing+":value");
        if(inlineText!=null) inlineText.patchProp("text",edit.text());
        UiNode preview=find(root,"lite:color:preview:"+editing);
        if(preview!=null) preview.patchProp("fill",current);
    }
    private static void paintHandle(UiNode root,String prefix,float x,float y,String color){
        moveHandlePart(root,prefix+":shadow",x,y,24f,null);
        moveHandlePart(root,prefix+":outline",x,y,20f,color);
        moveHandlePart(root,prefix+":inner",x,y,14f,null);
    }
    private static void moveHandlePart(UiNode root,String key,float cx,float cy,float size,String fill){
        UiNode part=find(root,key);
        if(part==null)return;
        UiBounds old=part.bounds();
        part.setBounds(new UiBounds(cx-size/2f,cy-size/2f,old.width(),old.height()));
        if(fill!=null)part.patchProp("fill",fill);
    }
    /** Native drag coordinates are sampled by SchematicPanel during each mouse move. */
    public boolean mouseDragged(float mx,float my) {
        if (draggingColorChannel.isEmpty()) return false;
        updateColor(mx,my);
        return true;
    }
    public void mouseReleased() {
        if (draggingColorChannel.isEmpty()) return;
        draggingColorChannel=""; draggingColorBounds=null; dragStartColorValue="";
    }
    private static boolean inside(float mx,float my,float x,float y,float w,float h) {
        return mx>=x && my>=y && mx<x+w && my<y+h;
    }
    private void applyEdit() {
        if (editing.isEmpty()) return;
        LitematicaWorkspace bridge = LitematicaCompat.workspace();
        if (bridge == null || !bridge.setOption(editingSection, editing, edit.text())) {
            invalid(editing, "Could not apply value for " + editing);
            return;
        }
        error = ""; errorOption = ""; cancelEdit(); expanded = ""; invalidate();
    }
    private void cancelEdit() {
        draggingColorChannel=""; draggingColorBounds=null; dragStartColorValue="";
        editFocused=false; pendingPointerCaret=false;
        editing = ""; editingSection = "";
        edit.unfocus(); CombatantTextInputBridge.blur(edit);
    }
    private void invalid(String id, String detail) { errorOption = id; error = detail; }
    private static int parseColor(String input) {
        String raw = input.strip().replaceFirst("(?i)^(#|0x)", "");
        if (!raw.matches("(?i)[a-f0-9]{6}([a-f0-9]{2})?")) throw new IllegalArgumentException("HEX");
        long parsed = Long.parseLong(raw, 16);
        return (int)(raw.length()==6 ? parsed | 0xFF000000L : parsed);
    }
    private static String keyName(int key) {
        try {
            for (java.lang.reflect.Field field : InputKeys.class.getFields()) {
                if (field.getType() == int.class && field.getInt(null) == key)
                    return field.getName().replace('_', ' ');
            }
        } catch (IllegalAccessException ignored) { }
        return Integer.toString(key);
    }
    private String captureLabel() {
        return capturedKeys.isEmpty() ? "Press keys · Enter to save · Backspace to clear" :
                capturedKeys.stream().map(LitematicaSettingsPanel::keyName).collect(java.util.stream.Collectors.joining(" + "));
    }
    private void addCapture(int code) {
        if (code != 0 && !capturedKeys.contains(code) && capturedKeys.size() < 8) capturedKeys.add(code);
    }
    private void commitHotkey() {
        if (captureId.isEmpty()) return;
        LitematicaWorkspace bridge = LitematicaCompat.workspace();
        if (bridge == null || !bridge.setHotkeyKeys(captureId, List.copyOf(capturedKeys))) {
            invalid(captureId, "Could not assign this hotkey"); return;
        }
        captureId = ""; capturedKeys.clear(); error = ""; errorOption = ""; invalidate();
    }
    private void chooseDirectory(LitematicaWorkspace.Option option) {
        Path previous;
        try { previous = Path.of(option.value()); }
        catch (RuntimeException ex) { previous = null; }
        Path start = previous != null && java.nio.file.Files.isDirectory(previous) ? previous : null;
        String section = group;
        NativeFileDialogService.pickFolder(start, Minecraft.getInstance()::schedule)
                .whenComplete((result, failure) -> Minecraft.getInstance().schedule(() -> {
                    if (failure != null) { invalid(option.id(), failure.getMessage()); return; }
                    if (result.status() == NativeFileDialogService.Status.CANCELLED) return;
                    if (result.status() != NativeFileDialogService.Status.SELECTED) {
                        invalid(option.id(), result.detail()); return;
                    }
                    LitematicaWorkspace bridge = LitematicaCompat.workspace();
                    if (bridge == null || !bridge.setOption(section, option.id(), result.path().toString()))
                        invalid(option.id(), "Litematica rejected the directory");
                    else { error = ""; errorOption = ""; invalidate(); }
                }));
    }
    private boolean ctrl() {
        return CombatantInput.state().isPressed(InputDevice.KEYBOARD,InputKeys.LEFT_CONTROL)
                ||CombatantInput.state().isPressed(InputDevice.KEYBOARD,InputKeys.RIGHT_CONTROL);
    }
    private boolean shift() {
        return CombatantInput.state().isPressed(InputDevice.KEYBOARD,InputKeys.LEFT_SHIFT)
                ||CombatantInput.state().isPressed(InputDevice.KEYBOARD,InputKeys.RIGHT_SHIFT);
    }
    public boolean keyPressed(int key) {
        if (!captureId.isEmpty()) {
            if (key == InputKeys.ESCAPE) { captureId=""; capturedKeys.clear(); return true; }
            if (key == InputKeys.ENTER || key == InputKeys.KP_ENTER) { commitHotkey(); return true; }
            if (key == InputKeys.BACKSPACE || key == InputKeys.DELETE) { capturedKeys.clear(); return true; }
            addCapture(key);
            return true;
        }
        if (key == InputKeys.ESCAPE && cancel()) return true;
        SingleLineTextInput active = editFocused && !editing.isEmpty() ? edit : filtering ? filter : null;
        if (active == null) {
            if (!editing.isEmpty() && (key == InputKeys.ENTER || key == InputKeys.KP_ENTER)) {
                applyEdit(); return true;
            }
            if (ctrl() && key == InputKeys.F) {filtering = true;filter.selectAll();return true;}
            return false;
        }
        if (key == InputKeys.ENTER || key == InputKeys.KP_ENTER) {
            if (!editing.isEmpty()) applyEdit(); else {filtering = false; filter.unfocus();}
            return true;
        }
        int shortcut = key >= InputKeys.A && key <= InputKeys.Z ? 'a'+key-InputKeys.A : key;
        int mask = (ctrl() ? InputModifierMask.SHORTCUT : 0)|(shift() ? InputModifierMask.SHIFT : 0);
        active.keyPressed(key,shortcut,mask);
        return true;
    }
    public boolean charTyped(char value) {
        SingleLineTextInput active = editFocused && !editing.isEmpty() ? edit : filtering ? filter : null;
        if (active == null) return false;
        if (!CombatantTextInputBridge.owns(active) && value >= 32 && !Character.isISOControl(value))
            active.insert(String.valueOf(value));
        return true;
    }
    public boolean cancel() {
        if (!captureId.isEmpty()) { captureId=""; capturedKeys.clear(); return true; }
        if (!editing.isEmpty()) {cancelEdit();expanded="";return true;}
        if (filtering) {filtering = false;filter.unfocus();CombatantTextInputBridge.blur(filter);return true;}
        if (!expanded.isEmpty()) {expanded = ""; return true;}
        return false;
    }
    public boolean blocksMovement() {return filtering || !editing.isEmpty() || !captureId.isEmpty();}
    public void removed() {
        cancelEdit(); expanded = ""; captureId=""; capturedKeys.clear(); filtering = false;filter.unfocus();
        CombatantTextInputBridge.blur(filter);invalidate();
    }
}
