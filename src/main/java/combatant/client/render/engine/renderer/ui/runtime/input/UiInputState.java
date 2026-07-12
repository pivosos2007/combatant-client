/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui.runtime.input;

import combatant.client.render.engine.renderer.ui.runtime.core.UiNode;

public final class UiInputState {
    private UiNode hoveredNode;
    private UiNode pressedNode;
    private UiNode focusedNode;
    private UiNode scrollOwner;

    public UiNode hoveredNode() {
        return hoveredNode;
    }

    public UiNode pressedNode() {
        return pressedNode;
    }

    public UiNode focusedNode() {
        return focusedNode;
    }

    public UiNode scrollOwner() {
        return scrollOwner;
    }

    public void setHoveredNode(UiNode hoveredNode) {
        this.hoveredNode = hoveredNode;
    }

    public void setPressedNode(UiNode pressedNode) {
        this.pressedNode = pressedNode;
    }

    public void setFocusedNode(UiNode focusedNode) {
        this.focusedNode = focusedNode;
    }

    public void setScrollOwner(UiNode scrollOwner) {
        this.scrollOwner = scrollOwner;
    }
}
