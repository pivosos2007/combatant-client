/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.util.player.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Describes the slots exposed by a menu independently of its screen layout.
 * Player slot IDs are shared across every menu; other IDs are scoped to the
 * current menu because arbitrary containers have no universal semantic roles.
 */
public final class InventoryComponentMap {
    /** An on-screen slot may delegate its inventory operations to another slot. */
    public interface DelegatingSlot {
        Slot combatant$backingSlot();
    }

    public enum Kind {
        HOTBAR, MAIN, ARMOR, OFFHAND, SADDLE, CRAFT_INPUT, CRAFT_RESULT, CONTAINER, VIRTUAL
    }

    /** A stable semantic name, not a menu slot number or an inventory index. */
    public record SlotId(String value) {
        public static final SlotId OFFHAND = new SlotId("player.offhand");
        public static final SlotId ARMOR_HEAD = new SlotId("player.armor.head");
        public static final SlotId ARMOR_CHEST = new SlotId("player.armor.chest");
        public static final SlotId ARMOR_LEGS = new SlotId("player.armor.legs");
        public static final SlotId ARMOR_FEET = new SlotId("player.armor.feet");
        public static final SlotId ARMOR_BODY = new SlotId("player.armor.body");
        public static final SlotId SADDLE = new SlotId("player.saddle");

        public SlotId {
            Objects.requireNonNull(value, "value");
            if (value.isBlank()) throw new IllegalArgumentException("Empty slot ID");
        }

        public static SlotId hotbar(int index) {
            if (index < 0 || index >= 9) throw new IllegalArgumentException("Hotbar index: " + index);
            return new SlotId("player.hotbar." + index);
        }

        public static SlotId main(int index) {
            if (index < 0 || index >= 27) throw new IllegalArgumentException("Main inventory index: " + index);
            return new SlotId("player.main." + index);
        }

        public static SlotId player(int inventoryIndex) {
            return switch (inventoryIndex) {
                case 36 -> ARMOR_FEET;
                case 37 -> ARMOR_LEGS;
                case 38 -> ARMOR_CHEST;
                case 39 -> ARMOR_HEAD;
                case Inventory.SLOT_OFFHAND -> OFFHAND;
                case Inventory.SLOT_BODY_ARMOR -> ARMOR_BODY;
                case Inventory.SLOT_SADDLE -> SADDLE;
                default -> {
                    if (inventoryIndex >= 0 && inventoryIndex < 9) yield hotbar(inventoryIndex);
                    if (inventoryIndex >= 9 && inventoryIndex < 36) yield main(inventoryIndex - 9);
                    yield new SlotId("player.extra." + inventoryIndex);
                }
            };
        }
    }

    /** The actual backing storage. Identity of Container is significant. */
    public record Component(Container inventory, int index) {}

    /** One visible/menu-addressable slot, with both its ID and backing storage. */
    public record Entry(int menuSlot, SlotId id, Kind kind, Component component) {}

    /** Immutable view of every slot in the current menu, including virtual ones. */
    public static final class Layout {
        private final List<Entry> entries;
        private final Map<Integer, Entry> byMenuSlot;
        private final Map<SlotId, List<Entry>> byId;

        private Layout(List<Entry> entries) {
            this.entries = List.copyOf(entries);
            Map<Integer, Entry> menuSlots = new LinkedHashMap<>();
            Map<SlotId, List<Entry>> namedSlots = new LinkedHashMap<>();
            for (Entry entry : entries) {
                menuSlots.put(entry.menuSlot(), entry);
                namedSlots.computeIfAbsent(entry.id(), ignored -> new ArrayList<>()).add(entry);
            }
            this.byMenuSlot = Collections.unmodifiableMap(menuSlots);
            Map<SlotId, List<Entry>> immutable = new LinkedHashMap<>();
            namedSlots.forEach((id, matches) -> immutable.put(id, List.copyOf(matches)));
            this.byId = Collections.unmodifiableMap(immutable);
        }

        public List<Entry> entries() { return entries; }
        public Map<Integer, Entry> byMenuSlot() { return byMenuSlot; }
        public Map<SlotId, List<Entry>> byId() { return byId; }
        public Entry get(int menuSlot) { return byMenuSlot.get(menuSlot); }
        public List<Entry> get(SlotId id) { return byId.getOrDefault(id, List.of()); }

        /** The first matching screen slot, or -1 if that component is not shown. */
        public int menuSlot(SlotId id) {
            List<Entry> found = get(id);
            return found.isEmpty() ? -1 : found.get(0).menuSlot();
        }

        public int menuSlot(Container backing, int index) {
            if (backing == null) return -1;
            for (Entry entry : entries) {
                Component component = entry.component();
                if (component != null && component.inventory() == backing && component.index() == index)
                    return entry.menuSlot();
            }
            return -1;
        }
    }

    private InventoryComponentMap() {}

    public static Slot unwrap(Slot slot) {
        // Use identity detection: a future UI may introduce more than one wrapper layer.
        IdentityHashMap<Slot, Boolean> visited = new IdentityHashMap<>();
        while (slot instanceof DelegatingSlot delegate && visited.put(slot, Boolean.TRUE) == null) {
            Slot next = delegate.combatant$backingSlot();
            if (next == null || next == slot) break;
            slot = next;
        }
        return slot;
    }

    public static Component resolve(Slot slot) {
        Slot backing = unwrap(slot);
        return backing == null ? null : new Component(backing.container, backing.getContainerSlot());
    }

    public static Component resolve(AbstractContainerMenu menu, int menuSlotId) {
        if (menu == null || menuSlotId < 0 || menuSlotId >= menu.slots.size()) return null;
        return resolve(menu.slots.get(menuSlotId));
    }

    /**
     * No caching: creative tabs and modded menus may replace their slot lists.
     * Every backing container is assigned its own group in this menu snapshot.
     */
    public static Layout layout(AbstractContainerMenu menu) {
        if (menu == null) return new Layout(List.of());
        List<Entry> entries = new ArrayList<>(menu.slots.size());
        IdentityHashMap<Container, Integer> containers = new IdentityHashMap<>();
        int nextContainer = 0;
        for (int menuId = 0; menuId < menu.slots.size(); menuId++) {
            Component component = resolve(menu, menuId);
            if (component == null || component.inventory() == null) {
                entries.add(new Entry(menuId, new SlotId("menu.virtual." + menuId), Kind.VIRTUAL, component));
                continue;
            }

            Container backing = component.inventory();
            int index = component.index();
            SlotId id;
            Kind kind;
            if (backing instanceof Inventory) {
                id = SlotId.player(index);
                kind = index >= 0 && index < 9 ? Kind.HOTBAR
                        : index >= 9 && index < 36 ? Kind.MAIN
                        : index >= 36 && index < 40 || index == Inventory.SLOT_BODY_ARMOR ? Kind.ARMOR
                        : index == Inventory.SLOT_OFFHAND ? Kind.OFFHAND
                        : index == Inventory.SLOT_SADDLE ? Kind.SADDLE : Kind.CONTAINER;
            } else if (backing instanceof CraftingContainer) {
                id = new SlotId("craft.input." + index);
                kind = Kind.CRAFT_INPUT;
            } else if (backing instanceof ResultContainer) {
                id = new SlotId("craft.result." + index);
                kind = Kind.CRAFT_RESULT;
            } else {
                Integer group = containers.get(backing);
                if (group == null) {
                    group = nextContainer++;
                    containers.put(backing, group);
                }
                id = new SlotId("menu.container." + group + "." + index);
                kind = Kind.CONTAINER;
            }
            entries.add(new Entry(menuId, id, kind, component));
        }
        return new Layout(entries);
    }

    /** Returns the menu's backing storage components indexed by menu slot. */
    public static Map<Integer, Component> snapshot(AbstractContainerMenu menu) {
        Map<Integer, Component> result = new LinkedHashMap<>();
        for (Entry entry : layout(menu).entries()) {
            if (entry.component() != null) result.put(entry.menuSlot(), entry.component());
        }
        return Collections.unmodifiableMap(result);
    }

    public static int playerInventoryIndex(Component component, Container playerInventory) {
        return component != null && playerInventory != null && component.inventory() == playerInventory
                && component.index() >= 0 && component.index() < playerInventory.getContainerSize()
                ? component.index() : -1;
    }

    public static int playerInventoryIndex(Slot slot, Player player) {
        return player == null ? -1 : playerInventoryIndex(resolve(slot), player.getInventory());
    }

    public static int playerInventoryIndex(AbstractContainerMenu menu, int menuSlotId, Player player) {
        return player == null ? -1 : playerInventoryIndex(resolve(menu, menuSlotId), player.getInventory());
    }

    /** Resolves the real menu slot, returning -1 rather than assuming a GUI layout. */
    public static int menuSlotFor(AbstractContainerMenu menu, Container backing, int index) {
        if (menu == null || backing == null) return -1;
        // Fast path for transactions: do not allocate an entire layout per click.
        for (int id = 0; id < menu.slots.size(); id++) {
            Component component = resolve(menu, id);
            if (component != null && component.inventory() == backing && component.index() == index)
                return id;
        }
        return -1;
    }
}
