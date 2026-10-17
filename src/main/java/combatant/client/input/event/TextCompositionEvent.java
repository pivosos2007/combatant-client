/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.input.event;

import combatant.client.events.Event;
import combatant.client.input.InputFocus;

import java.util.List;

public final class TextCompositionEvent extends Event {
    private final String text;
    private final int caretPosition;
    private final List<String> blocks;
    private final int focusedBlock;
    private final InputFocus.Snapshot focus;

    public TextCompositionEvent(String text, int caretPosition, List<String> blocks,
                                int focusedBlock, InputFocus.Snapshot focus) {
        this.text = text == null ? "" : text;
        this.caretPosition = caretPosition;
        this.blocks = blocks == null ? List.of() : List.copyOf(blocks);
        this.focusedBlock = focusedBlock;
        this.focus = focus;
    }

    public String text() { return text; }
    public int caretPosition() { return caretPosition; }
    public List<String> blocks() { return blocks; }
    public int focusedBlock() { return focusedBlock; }
    public InputFocus.Snapshot focus() { return focus; }
}
