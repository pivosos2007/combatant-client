/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.text;

/**
 * Tracks a text selection across one or more logical text rows/messages.
 * <p>
 * Context ids are expected to be stable within the current rendered frame
 * and ordered from top/oldest to bottom/newest. BetterChat uses the message
 * index inside the currently rendered message list as the context id.
 */
public final class TextSelection {
    private int anchorContextId = -1;
    private int anchor = -1;
    private int caretContextId = -1;
    private int caret = -1;

    public void update(int contextId, int charIndex) {
        if (anchorContextId < 0 || anchor < 0) {
            begin(contextId, charIndex);
            return;
        }
        updateCaret(contextId, charIndex);
    }

    public void begin(int contextId, int charIndex) {
        anchorContextId = contextId;
        anchor = Math.max(0, charIndex);
        caretContextId = contextId;
        caret = Math.max(0, charIndex);
    }

    /**
     * Legacy same-context update.
     */
    public void updateCaret(int charIndex) {
        if (anchorContextId < 0) return;
        caretContextId = anchorContextId;
        caret = Math.max(0, charIndex);
    }

    public void updateCaret(int contextId, int charIndex) {
        if (anchorContextId < 0) {
            begin(contextId, charIndex);
            return;
        }
        caretContextId = contextId;
        caret = Math.max(0, charIndex);
    }

    public boolean appliesToLine(int contextId) {
        if (!hasRange() || contextId < 0) return false;
        return contextId >= startContext() && contextId <= endContext();
    }

    public boolean appliesTo(int contextId) {
        return appliesToLine(contextId) && hasRange();
    }


    public boolean hasCaret() {
        return caretContextId >= 0 && caret >= 0;
    }

    public int caret() {
        return caret;
    }

    public int caretContext() {
        return caretContextId;
    }

    public int anchor() {
        return anchor;
    }

    public int anchorContext() {
        return anchorContextId;
    }

    public boolean hasRange() {
        if (anchorContextId < 0 || caretContextId < 0 || anchor < 0 || caret < 0) return false;
        return anchorContextId != caretContextId || anchor != caret;
    }

    public void clear() {
        anchorContextId = -1;
        anchor = -1;
        caretContextId = -1;
        caret = -1;
    }

    public int start() {
        if (!hasRange()) return -1;
        if (anchorContextId == caretContextId) return Math.min(anchor, caret);
        return isForward() ? anchor : caret;
    }

    public int end() {
        if (!hasRange()) return -1;
        if (anchorContextId == caretContextId) return Math.max(anchor, caret);
        return isForward() ? caret : anchor;
    }

    public int context() {
        return startContext();
    }

    public int startContext() {
        if (!hasRange()) return -1;
        return Math.min(anchorContextId, caretContextId);
    }

    public int endContext() {
        if (!hasRange()) return -1;
        return Math.max(anchorContextId, caretContextId);
    }

    public int startForLine(int contextId) {
        if (!appliesToLine(contextId)) return -1;
        if (anchorContextId == caretContextId) return Math.min(anchor, caret);
        return contextId == startContext() ? start() : Integer.MIN_VALUE;
    }

    public int endForLine(int contextId) {
        if (!appliesToLine(contextId)) return -1;
        if (anchorContextId == caretContextId) return Math.max(anchor, caret);
        return contextId == endContext() ? end() : Integer.MAX_VALUE;
    }

    private boolean isForward() {
        if (anchorContextId != caretContextId) return anchorContextId < caretContextId;
        return anchor <= caret;
    }
}
