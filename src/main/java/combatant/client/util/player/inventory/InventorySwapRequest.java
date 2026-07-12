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

public final class InventorySwapRequest {
    private final Predicate<ItemStack> predicate;
    private final InventorySearchScope scope;
    private final InventorySwapVisibility visibility;
    private final Boolean restore;
    private final InventorySwapPolicy policyOverride;
    private final InventoryActionKind actionKind;
    private final Runnable action;

    private InventorySwapRequest(Builder builder) {
        this.predicate = builder.predicate;
        this.scope = builder.scope;
        this.visibility = builder.visibility;
        this.restore = builder.restore;
        this.policyOverride = builder.policyOverride;
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

    public boolean restore(boolean defaultRestore) {
        return restore != null ? restore : defaultRestore;
    }

    public InventorySwapPolicy policy(InventorySwapPolicy defaultPolicy) {
        return policyOverride != null ? policyOverride : defaultPolicy;
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
        private InventorySearchScope scope;
        private InventorySwapVisibility visibility;
        private Boolean restore;
        private InventorySwapPolicy policyOverride;
        private InventoryActionKind actionKind = InventoryActionKind.GENERIC;

        private Builder(Predicate<ItemStack> predicate, Runnable action) {
            this.predicate = Objects.requireNonNull(predicate, "predicate");
            this.action = Objects.requireNonNull(action, "action");
        }

        public Builder scope(InventorySearchScope scope) {
            this.scope = scope;
            return this;
        }

        public Builder visibility(InventorySwapVisibility visibility) {
            this.visibility = visibility;
            return this;
        }

        public Builder restore(boolean restore) {
            this.restore = restore;
            return this;
        }

        public Builder policy(InventorySwapPolicy policy) {
            this.policyOverride = policy;
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
