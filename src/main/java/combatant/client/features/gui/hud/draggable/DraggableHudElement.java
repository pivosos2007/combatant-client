/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.hud.draggable;

import combatant.client.config.values.ConfigValue;
import combatant.client.features.gui.hud.BaseHudElement;
import combatant.client.features.gui.hud.HudAnchorX;
import combatant.client.features.gui.hud.HudAnchorY;
import combatant.client.features.gui.hud.WidgetAnchorSide;
import lombok.Getter;

import java.util.Map;

/**
 * Base draggable HUD element with self-owned config and layout.
 */
public abstract class DraggableHudElement extends BaseHudElement {

    private final DraggableLayoutValue layoutValue = new DraggableLayoutValue("layout");
    @Getter
    private boolean positionInitialized = false;
    @Getter
    private HudAnchorX anchorX = HudAnchorX.FREE;
    @Getter
    private HudAnchorY anchorY = HudAnchorY.FREE;
    @Getter
    private float anchorOffsetX;
    @Getter
    private float anchorOffsetY;
    @Getter
    private String parentId;
    @Getter
    private WidgetAnchorSide parentSide = WidgetAnchorSide.NONE;
    @Getter
    private float parentOffsetX;
    @Getter
    private float parentOffsetY;
    private long renderOrder;

    protected DraggableHudElement() {
        super();
    }

    protected DraggableHudElement(String id, String title, boolean defaultEnabled) {
        super(id, title, defaultEnabled);
    }

    public DraggableLayoutValue layoutValue() {
        return layoutValue;
    }

    protected final void defaultLayout(float x, float y) {
        defaultLayout(x, y, "FREE", "FREE");
    }

    protected final void defaultLayout(float x, float y, String anchorX, String anchorY) {
        layoutValue.set(new DraggableLayoutValue.State(
                x,
                y,
                parseDefaultAnchorX(anchorX),
                parseDefaultAnchorY(anchorY),
                null,
                WidgetAnchorSide.NONE,
                0f,
                0f,
                true
        ));
    }

    protected final void defaultLinkedLayout(float x,
                                             float y,
                                             String parentId,
                                             String parentSide,
                                             float parentOffsetX,
                                             float parentOffsetY) {
        layoutValue.set(new DraggableLayoutValue.State(
                x,
                y,
                HudAnchorX.FREE,
                HudAnchorY.FREE,
                parentId,
                parseDefaultParentSide(parentSide),
                parentOffsetX,
                parentOffsetY,
                true
        ));
    }

    private static HudAnchorX parseDefaultAnchorX(String value) {
        if (value == null || value.isBlank()) return HudAnchorX.FREE;
        try {
            return HudAnchorX.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return HudAnchorX.FREE;
        }
    }

    private static HudAnchorY parseDefaultAnchorY(String value) {
        if (value == null || value.isBlank()) return HudAnchorY.FREE;
        try {
            return HudAnchorY.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return HudAnchorY.FREE;
        }
    }

    private static WidgetAnchorSide parseDefaultParentSide(String value) {
        if (value == null || value.isBlank()) return WidgetAnchorSide.NONE;
        try {
            return WidgetAnchorSide.valueOf(value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return WidgetAnchorSide.NONE;
        }
    }

    public void markPositionInitialized() {
        positionInitialized = true;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public long getRuntimeRenderOrder() {
        return renderOrder;
    }

    public void moveTo(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void setAnchors(HudAnchorX anchorX, HudAnchorY anchorY, float offsetX, float offsetY) {
        this.anchorX = anchorX != null ? anchorX : HudAnchorX.FREE;
        this.anchorY = anchorY != null ? anchorY : HudAnchorY.FREE;
        this.anchorOffsetX = offsetX;
        this.anchorOffsetY = offsetY;
    }

    public void setParentAnchor(String parentId,
                                WidgetAnchorSide side,
                                float offsetX,
                                float offsetY) {
        this.parentId = parentId;
        this.parentSide = side != null ? side : WidgetAnchorSide.NONE;
        this.parentOffsetX = offsetX;
        this.parentOffsetY = offsetY;
    }

    public void clearParentAnchor() {
        this.parentId = null;
        this.parentSide = WidgetAnchorSide.NONE;
        this.parentOffsetX = 0f;
        this.parentOffsetY = 0f;
    }

    public void applyLayoutState(DraggableLayoutValue.State state) {
        if (state == null) return;
        setAnchors(state.anchorX(), state.anchorY(), state.x(), state.y());
        if (state.parentId() != null && state.parentSide() != WidgetAnchorSide.NONE) {
            setParentAnchor(state.parentId(), state.parentSide(), state.parentOffsetX(), state.parentOffsetY());
        } else {
            clearParentAnchor();
        }
    }

    public DraggableLayoutValue.State snapshotLayout() {
        return new DraggableLayoutValue.State(
                anchorOffsetX,
                anchorOffsetY,
                anchorX,
                anchorY,
                parentId,
                parentSide,
                parentOffsetX,
                parentOffsetY,
                true
        );
    }

    void markRendered(long order) {
        this.renderOrder = order;
    }

    /**
     * Apply default position if no saved layout.
     */
    public abstract void applyDefaultPosition(int screenW, int screenH);

    public boolean isDraggable() {
        return true;
    }

    public boolean supportsWidgetAnchoring() {
        return true;
    }

    @Override
    public String getTranslationKeyPrefix() {
        return "setting.draggable." + getId();
    }

    @Override
    public String getConfigName() {
        return getId();
    }

    @Override
    protected void collectExtraConfigValues(Map<String, ConfigValue<?>> map) {
        map.put(layoutValue.getName(), layoutValue);
    }
}
