/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.clickgui.picker;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.block.Block;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.util.item.EnchantMeta;
import combatant.client.util.item.EnchantRegistry;
import combatant.client.util.screen.ScreenCatalog;
import combatant.client.util.particle.ParticleClassCatalog;
import combatant.client.util.logging.DebugLog;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public enum PickerCatalogFactory {
    ;
    private static final Comparator<PickerEntryData> ENTRY_ORDER = Comparator
            .comparing(PickerEntryData::label, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(PickerEntryData::id, String.CASE_INSENSITIVE_ORDER);
    private static final EnumSet<TextListSetting.PickerMode> ASYNC_MODES = EnumSet.of(
            TextListSetting.PickerMode.BLOCKS,
            TextListSetting.PickerMode.ITEMS,
            TextListSetting.PickerMode.EQUIPPABLE_ARMOR,
            TextListSetting.PickerMode.ENCHANTMENTS,
            TextListSetting.PickerMode.ALL,
            TextListSetting.PickerMode.SOUNDS,
            TextListSetting.PickerMode.LIVING_ENTITIES,
            TextListSetting.PickerMode.ENTITIES,
            TextListSetting.PickerMode.PARTICLES
    );
    private static final Map<TextListSetting.PickerMode, CompletableFuture<List<PickerEntryData>>> ASYNC_CACHE =
            new ConcurrentHashMap<>();
    private static final ExecutorService CATALOG_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "Combatant-PickerCatalog");
        thread.setDaemon(true);
        return thread;
    });
    /**
     * Completed only after 26.2 has bound item component holders. Futures requested earlier stay
     * pending instead of executing an unsafe registry walk and permanently caching a failure.
     */
    private static final CompletableFuture<Void> RUNTIME_READY = new CompletableFuture<>();


    public static boolean supportsAsync(TextListSetting.PickerMode mode) {
        return mode != null && ASYNC_MODES.contains(mode);
    }

    /**
     * Starts registry-backed catalog construction while normal client initialization continues.
     * Returns false while 26.2's item component holders are not bound yet; callers should retry
     * from a later client tick instead of poisoning the cache with an early startup failure.
     */
    public static boolean prewarmAsync() {
        if (!markRuntimeReadyIfPossible()) return false;

        // Highest first-open value first; executor ordering intentionally prioritizes item-backed pickers.
        requestEntriesAsync(TextListSetting.PickerMode.ITEMS);
        requestEntriesAsync(TextListSetting.PickerMode.BLOCKS);
        requestEntriesAsync(TextListSetting.PickerMode.ALL);
        requestEntriesAsync(TextListSetting.PickerMode.EQUIPPABLE_ARMOR);
        requestEntriesAsync(TextListSetting.PickerMode.ENTITIES);
        requestEntriesAsync(TextListSetting.PickerMode.LIVING_ENTITIES);
        requestEntriesAsync(TextListSetting.PickerMode.SOUNDS);
        requestEntriesAsync(TextListSetting.PickerMode.ENCHANTMENTS);
        requestEntriesAsync(TextListSetting.PickerMode.PARTICLES);
        return true;
    }

    /** Cheap readiness probe for Holder.Reference component binding used by ItemStack construction. */
    public static boolean runtimeReadyForCatalogBuild() {
        if (RUNTIME_READY.isDone()) return true;
        try {
            ItemStack probe = Items.STONE.getDefaultInstance();
            return probe != null && !probe.isEmpty();
        } catch (RuntimeException notBoundYet) {
            return false;
        }
    }

    /**
     * Marks the catalog worker barrier ready only after a successful ItemStack construction probe.
     * Safe to call repeatedly from the render thread while startup/reload is still settling.
     */
    public static boolean markRuntimeReadyIfPossible() {
        if (RUNTIME_READY.isDone()) return true;
        if (!runtimeReadyForCatalogBuild()) return false;
        RUNTIME_READY.complete(null);
        return true;
    }

    /**
     * Returns one shared immutable catalog future. No registry traversal is performed by the UI
     * thread when the result is still being built.
     */
    public static CompletableFuture<List<PickerEntryData>> requestEntriesAsync(TextListSetting.PickerMode mode) {
        if (!supportsAsync(mode)) {
            return CompletableFuture.completedFuture(List.of());
        }

        // A picker can technically be constructed before the startup prewarm pump reaches its
        // resource barrier. Probe here too, but never execute the worker until the barrier opens.
        markRuntimeReadyIfPossible();

        CompletableFuture<List<PickerEntryData>> raw = ASYNC_CACHE.get(mode);
        if (raw == null) {
            CompletableFuture<List<PickerEntryData>> created = RUNTIME_READY
                    .thenApplyAsync(ignored -> List.copyOf(buildAsyncEntries(mode)), CATALOG_EXECUTOR);
            CompletableFuture<List<PickerEntryData>> raced = ASYNC_CACHE.putIfAbsent(mode, created);
            raw = raced != null ? raced : created;

            if (raced == null) {
                CompletableFuture<List<PickerEntryData>> tracked = created;
                created.whenComplete((entries, error) -> {
                    if (error == null) return;
                    // A transient startup/reload failure must not become a permanent empty cache.
                    ASYNC_CACHE.remove(mode, tracked);
                    DebugLog.warnOnce(
                            "picker-catalog-async:" + mode.name().toLowerCase(Locale.ROOT),
                            "Failed to build async picker catalog: %s",
                            mode,
                            error
                    );
                });
            }
        }

        // Picker UI remains non-blocking even if a transient worker failure occurs. The raw failed
        // future is evicted above so a subsequent picker/prewarm request can retry normally.
        return raw.exceptionally(error -> List.of());
    }

    /** Owner-aware async view for catalogs that preserve configured values missing from discovery. */
    public static CompletableFuture<List<PickerEntryData>> requestEntriesAsync(
            TextListSetting.PickerMode mode, TextListSetting owner) {
        CompletableFuture<List<PickerEntryData>> base = requestEntriesAsync(mode);
        if (mode != TextListSetting.PickerMode.PARTICLES || owner == null) return base;
        Set<String> selected = Set.copyOf(owner.getValueSet());
        if (selected.isEmpty()) return base;
        return base.thenApply(entries -> mergeParticleSelections(entries, selected));
    }

    /** Returns the completed shared result without blocking, or {@code null} while it is pending. */
    public static List<PickerEntryData> completedEntries(TextListSetting.PickerMode mode) {
        CompletableFuture<List<PickerEntryData>> future = ASYNC_CACHE.get(mode);
        if (future == null || !future.isDone() || future.isCompletedExceptionally()) return null;
        return future.getNow(List.of());
    }

    /** Language/resource reload changes labels, so future picker instances need fresh snapshots. */
    public static void invalidateAsyncCaches() {
        ASYNC_CACHE.clear();
    }

    private static List<PickerEntryData> buildAsyncEntries(TextListSetting.PickerMode mode) {
        return switch (mode) {
            case BLOCKS -> blockEntries();
            case ITEMS -> itemEntries();
            case EQUIPPABLE_ARMOR -> equippableArmorEntries();
            case ENCHANTMENTS -> enchantmentEntries();
            case ALL -> allEntries();
            case SOUNDS -> soundEntries();
            case LIVING_ENTITIES -> livingEntityEntries();
            case ENTITIES -> entityEntries();
            case PARTICLES -> particleEntries(null);
            default -> List.of();
        };
    }

    public static PickerCatalog forMode(TextListSetting.PickerMode mode) {
        if (mode == null) return owner -> List.of();
        return switch (mode) {
            case SCREENS -> PickerCatalogFactory::screenEntries;
            case BLOCKS -> owner -> blockEntries();
            case ITEMS -> owner -> itemEntries();
            case EQUIPPABLE_ARMOR -> owner -> equippableArmorEntries();
            case ENCHANTMENTS -> owner -> enchantmentEntries();
            case ALL -> owner -> allEntries();
            case SOUNDS -> owner -> soundEntries();
            case LIVING_ENTITIES -> owner -> livingEntityEntries();
            case ENTITIES -> owner -> entityEntries();
            case PARTICLES -> PickerCatalogFactory::particleEntries;
            case TEXT -> owner -> List.of();
        };
    }

    private static List<PickerEntryData> screenEntries(TextListSetting owner) {
        Set<String> selected = owner == null ? Set.of() : owner.getValueSet();
        List<PickerEntryData> out = new ArrayList<>();
        for (ScreenCatalog.Entry entry : ScreenCatalog.entries(selected)) {
            out.add(new PickerEntryData(entry.getId(), entry.label(), ItemStack.EMPTY));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<PickerEntryData> blockEntries() {
        List<PickerEntryData> out = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            String label = I18n.get(block.getDescriptionId());
            ItemStack stack = block.asItem().getDefaultInstance();
            out.add(new PickerEntryData(id, label, stack));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<PickerEntryData> itemEntries() {
        List<PickerEntryData> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            ItemStack stack = item.getDefaultInstance();
            String label = stack.getHoverName().getString();
            out.add(new PickerEntryData(id, label, stack));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<PickerEntryData> equippableArmorEntries() {
        List<PickerEntryData> out = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = item.getDefaultInstance();
            if (!isArmorEquippable(stack)) continue;
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            String label = stack.getHoverName().getString();
            out.add(new PickerEntryData(id, label, stack));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<PickerEntryData> enchantmentEntries() {
        List<PickerEntryData> out = new ArrayList<>();
        for (EnchantMeta meta : EnchantRegistry.REGISTRY.values()) {
            String id = "minecraft:" + meta.key();
            out.add(new PickerEntryData(id, meta.localizedName(), ItemStack.EMPTY));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<PickerEntryData> allEntries() {
        Set<String> seen = new LinkedHashSet<>();
        List<PickerEntryData> out = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            String id = BuiltInRegistries.BLOCK.getKey(block).toString();
            if (!seen.add(id)) continue;
            String label = I18n.get(block.getDescriptionId());
            ItemStack stack = block.asItem().getDefaultInstance();
            out.add(new PickerEntryData(id, label, stack));
        }
        for (Item item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).toString();
            if (!seen.add(id)) continue;
            ItemStack stack = item.getDefaultInstance();
            String label = stack.getHoverName().getString();
            out.add(new PickerEntryData(id, label, stack));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<PickerEntryData> soundEntries() {
        List<PickerEntryData> out = new ArrayList<>();
        for (SoundEvent sound : BuiltInRegistries.SOUND_EVENT) {
            Identifier id = BuiltInRegistries.SOUND_EVENT.getKey(sound);
            if (id == null) continue;
            out.add(new PickerEntryData(id.toString(), soundLabel(id), ItemStack.EMPTY));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<PickerEntryData> livingEntityEntries() {
        List<PickerEntryData> out = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Class<?> baseClass = type.getBaseClass();
            if (baseClass == null || !net.minecraft.world.entity.LivingEntity.class.isAssignableFrom(baseClass))
                continue;
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id == null) continue;
            String label = I18n.get(type.getDescriptionId());
            out.add(new PickerEntryData(id.toString(), label, ItemStack.EMPTY));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<PickerEntryData> mergeParticleSelections(
            List<PickerEntryData> entries, Set<String> selectedIds) {
        LinkedHashMap<String, PickerEntryData> merged = new LinkedHashMap<>();
        if (entries != null) {
            for (PickerEntryData entry : entries) {
                if (entry != null) merged.putIfAbsent(entry.id(), entry);
            }
        }
        if (selectedIds != null) {
            for (String id : selectedIds) {
                if (id == null || id.isBlank()) continue;
                merged.putIfAbsent(id, new PickerEntryData(
                        id, ParticleClassCatalog.labelForClassName(id), ItemStack.EMPTY));
            }
        }
        ArrayList<PickerEntryData> out = new ArrayList<>(merged.values());
        out.sort(ENTRY_ORDER);
        return List.copyOf(out);
    }

    private static List<PickerEntryData> particleEntries(TextListSetting owner) {
        Set<String> selected = owner == null ? Set.of() : owner.getValueSet();
        List<PickerEntryData> out = new ArrayList<>();
        for (ParticleClassCatalog.Entry entry : ParticleClassCatalog.entries(selected)) {
            out.add(new PickerEntryData(entry.id(), entry.label(), ItemStack.EMPTY));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static List<PickerEntryData> entityEntries() {
        List<PickerEntryData> out = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (id == null) continue;
            String label = I18n.get(type.getDescriptionId());
            out.add(new PickerEntryData(id.toString(), label, ItemStack.EMPTY));
        }
        out.sort(ENTRY_ORDER);
        return out;
    }

    private static String humanizeSnakeCase(String id) {
        if (id == null || id.isBlank()) return "";
        String[] parts = id.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder(id.length() + 4);
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) out.append(part, 1, part.length());
        }
        return out.toString();
    }

    private static String soundLabel(Identifier id) {
        if (id == null) return "";
        String label = humanizeSnakeCase(id.getPath().replace('.', '_'));
        if ("minecraft".equals(id.getNamespace())) return label;
        return id.getNamespace() + ": " + label;
    }

    private static boolean isArmorEquippable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Equippable eq = stack.get(DataComponents.EQUIPPABLE);
        if (eq == null) return false;
        EquipmentSlot slot = eq.slot();
        return slot == EquipmentSlot.HEAD
                || slot == EquipmentSlot.CHEST
                || slot == EquipmentSlot.LEGS
                || slot == EquipmentSlot.FEET;
    }
}
