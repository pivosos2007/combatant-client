/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat.rich;

import net.minecraft.network.chat.Component;
import combatant.client.features.gui.chat.actions.ChatMessageActions;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;

/** Immutable ordered rich-message node list. */
public final class BetterChatMessage {
    private static final BetterChatMessage EMPTY = new BetterChatMessage(List.of(new TextNode(Component.empty())));

    private final List<BetterChatNode> nodes;
    private final Component accessibleComponent;
    private final String plainText;
    private final String actionToken;

    public BetterChatMessage(List<? extends BetterChatNode> nodes) {
        this(nodes, null);
    }

    private BetterChatMessage(List<? extends BetterChatNode> nodes, String actionToken) {
        List<BetterChatNode> copy = new ArrayList<>();
        if (nodes != null) {
            for (BetterChatNode node : nodes) {
                if (node != null) copy.add(node);
            }
        }
        if (copy.isEmpty()) copy.add(new TextNode(Component.empty()));
        this.nodes = List.copyOf(copy);
        this.actionToken = actionToken;

        MutableComponent accessible = Component.empty();
        StringBuilder plain = new StringBuilder();
        for (BetterChatNode node : this.nodes) {
            if (node instanceof TextNode text) {
                accessible.append(text.component());
            } else {
                accessible.append(Component.literal(node.plainText()));
            }
            plain.append(node.plainText());
        }
        this.accessibleComponent = accessible;
        ChatMessageActions.bind(accessible, actionToken);
        this.plainText = plain.toString();
    }

    public static BetterChatMessage empty() {
        return EMPTY;
    }

    public static BetterChatMessage text(Component component) {
        return new BetterChatMessage(List.of(new TextNode(component)), ChatMessageActions.tokenOf(component));
    }

    public String actionToken() { return actionToken; }

    public List<BetterChatNode> nodes() {
        return nodes;
    }

    public Component accessibleComponent() {
        return accessibleComponent;
    }

    public String plainText() {
        return plainText;
    }

    public boolean isTextOnly() {
        return nodes.stream().allMatch(TextNode.class::isInstance);
    }

    public BetterChatMessage append(BetterChatNode node) {
        if (node == null) return this;
        List<BetterChatNode> appended = new ArrayList<>(nodes.size() + 1);
        appended.addAll(nodes);
        appended.add(node);
        return new BetterChatMessage(appended, actionToken);
    }

    public boolean semanticallyEquals(BetterChatMessage other) {
        if (other == null || nodes.size() != other.nodes.size()) return false;
        if (!java.util.Objects.equals(actionToken, other.actionToken)) return false;
        for (int i = 0; i < nodes.size(); i++) {
            BetterChatNode left = nodes.get(i);
            BetterChatNode right = other.nodes.get(i);
            if (left instanceof TextNode lt && right instanceof TextNode rt) {
                if (!lt.semanticallyEquals(rt)) return false;
            } else if (left instanceof ItemNode li && right instanceof ItemNode ri) {
                if (!li.semanticallyEquals(ri)) return false;
            } else {
                return false;
            }
        }
        return true;
    }
}
