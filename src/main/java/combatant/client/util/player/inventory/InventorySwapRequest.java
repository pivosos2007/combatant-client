/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.player.inventory;

import net.minecraft.world.item.ItemStack;

import java.util.Objects;
import java.util.function.Predicate;

/**
 * "Find an item, bring it to the server-held hand slot, run {@code action}". Defaults: whole inventory,
 * silent swap, restore afterwards.
 */
public final class InventorySwapRequest {
    private final Predicate<ItemStack> predicate;
    private final InventorySearchScope scope;
    private final InventorySwapVisibility visibility;
    private final boolean restore;
    private final InventoryActionKind actionKind;
    private final Runnable action;

    private InventorySwapRequest(Builder builder) {
        this.predicate = builder.predicate;
        this.scope = builder.scope;
        this.visibility = builder.visibility;
        this.restore = builder.restore;
        this.actionKind = builder.actionKind;
        this.action = builder.action;
    }

    public static Builder builder(Predicate<ItemStack> predicate, Runnable action) {
        return new Builder(predicate, action);
    }

    public Predicate<ItemStack> predicate() {
        return predicate;
    }

    public InventorySearchScope scope() {
        return scope;
    }

    public InventorySwapVisibility visibility() {
        return visibility;
    }

    public boolean restore() {
        return restore;
    }

    public InventoryActionKind actionKind() {
        return actionKind;
    }

    public Runnable action() {
        return action;
    }

    public static final class Builder {
        private final Predicate<ItemStack> predicate;
        private final Runnable action;
        private InventorySearchScope scope = InventorySearchScope.FULL;
        private InventorySwapVisibility visibility = InventorySwapVisibility.SILENT;
        private boolean restore = true;
        private InventoryActionKind actionKind = InventoryActionKind.GENERIC;

        private Builder(Predicate<ItemStack> predicate, Runnable action) {
            this.predicate = Objects.requireNonNull(predicate, "predicate");
            this.action = Objects.requireNonNull(action, "action");
        }

        public Builder scope(InventorySearchScope scope) {
            this.scope = scope != null ? scope : InventorySearchScope.FULL;
            return this;
        }

        public Builder visibility(InventorySwapVisibility visibility) {
            this.visibility = visibility != null ? visibility : InventorySwapVisibility.SILENT;
            return this;
        }

        public Builder restore(boolean restore) {
            this.restore = restore;
            return this;
        }

        public Builder actionKind(InventoryActionKind actionKind) {
            this.actionKind = actionKind != null ? actionKind : InventoryActionKind.GENERIC;
            return this;
        }

        public InventorySwapRequest build() {
            return new InventorySwapRequest(this);
        }
    }
}
