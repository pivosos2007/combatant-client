/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 * ABP:R portions: Copyright (c) 2023 schwar, 2023 KadTheHunter, 2022 Flourick, 2019 Clayborn.
 * See THIRD_PARTY_LICENSES/AccurateBlockPlacement-Reborn-MIT.txt.
 */

package combatant.client.features.module.modules.player;

import combatant.client.events.impl.WorldChangeEvent;
import combatant.client.events.impl.BlockUseAttemptEvent;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.combat.AutoBow;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import combatant.client.config.common.CommonSettingSchemas;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.BlockPlacementInputEvent;
import combatant.client.events.impl.CrosshairTargetUpdateEvent;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.ItemFrameItem;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.ScaffoldingBlockItem;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.mixins.accessors.LivingEntityAccessor;
import combatant.client.mixins.accessors.MinecraftAccessor;
import combatant.client.mixins.accessors.MultiPlayerGameModeAccessor;
import combatant.client.util.block.interaction.BlockInteractionPipeline;

import java.util.LinkedHashMap;
import java.util.Map;

@ModuleInfo(
        id = "nodelay",
        displayName = "NoDelay",
        aliases = {"FastUse", "FastBreak", "FastPlace"},
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.EXPLOIT,
        description = "module.nodelay.description")
public final class NoDelay extends Module {

    private static final String SETTING_TYPES = "types";
    private static final String SETTING_USE_DELAY = "use_delay";
    private static final String SETTING_DELAY_MS = "delay_ms";
    private static final String SETTING_INTERVAL_MS = "interval_ms";
    private static final String SETTING_INTERACT_DELAY_MS = "interact_delay_ms";
    private static final String SETTING_INTERACT_INTERVAL_MS = "interact_interval_ms";
    private static final String SETTING_BLOCK_PLACE_COOLDOWN_TICKS = "block_place_cooldown_ticks";
    private static final String SETTING_FAST_BREAK_MODE = "fast_break_mode";
    private static final String SETTING_FAST_BREAK_PROGRESS = "fast_break_progress";
    private static final String SETTING_ACCURATE_PLACEMENT = "accurate_placement";
    private static final String SETTING_ACCURATE_ITEMS = "accurate_items";

    private static final String TYPE_JUMP = "Jump";
    private static final String TYPE_RIGHT_CLICK = "Right Click";
    private static final String TYPE_BLOCK_PLACE = "Block Place";
    private static final String TYPE_BREAK_COOLDOWN = "Break CoolDown";
    private static final String TYPE_FAST_BREAK = "Fast Break";
    private static final String TYPE_FAST_THROW = "Fast throw";
    private static final String TYPE_FAST_INTERACT = "Fast interact";
    private static final String TYPE_SHIELD_COOLDOWN = "Shield Cooldown";

    {
        addAction("toggle_accurate_placement", "NONE");
        addAction("toggle_fast_breaking", "NONE");
    }

    private final BooleanMapValue types = group("noDelayTypes", SETTING_TYPES, defaultTypes());
    private final BooleanValue useDelay =
            visibleWhen(bool("noDelayUseDelay", SETTING_USE_DELAY, true), () -> types.get(TYPE_FAST_THROW));
    private final NumberValue<Integer> delayMs =
            visibleWhen(num("noDelayDelayMs", SETTING_DELAY_MS, 250, 10, 250),
                    () -> types.get(TYPE_FAST_THROW) && useDelay.get());
    private final NumberValue<Integer> intervalMs =
            visibleWhen(num("noDelayIntervalMs", SETTING_INTERVAL_MS, 35, 10, 100),
                    () -> types.get(TYPE_FAST_THROW));
    private final NumberValue<Integer> interactDelayMs =
            visibleWhen(num("noDelayInteractDelayMs", SETTING_INTERACT_DELAY_MS, 150, 10, 250),
                    () -> types.get(TYPE_FAST_INTERACT));
    private final NumberValue<Integer> interactIntervalMs =
            visibleWhen(num("noDelayInteractIntervalMs", SETTING_INTERACT_INTERVAL_MS, 45, 10, 150),
                    () -> types.get(TYPE_FAST_INTERACT));
    private final NumberValue<Integer> blockPlaceCooldownTicks =
            visibleWhen(num("noDelayBlockPlaceCooldownTicks", SETTING_BLOCK_PLACE_COOLDOWN_TICKS, 0, 0, 6),
                    () -> types.get(TYPE_BLOCK_PLACE));
    private final EnumValue<FastBreakMode> fastBreakMode =
            visibleWhen(enumCommon(
                            "noDelayFastBreakMode",
                            SETTING_FAST_BREAK_MODE,
                            CommonSettingSchemas.MODE,
                            FastBreakMode.NORMAL,
                            FastBreakMode.values()
                    ),
                    () -> types.get(TYPE_FAST_BREAK));
    private final NumberValue<Float> fastBreakProgress =
            visibleWhen(num("noDelayFastBreakProgress", SETTING_FAST_BREAK_PROGRESS, 0.5f, 0.1f, 1.0f),
                    () -> types.get(TYPE_FAST_BREAK));

    private final BooleanValue accuratePlacement =
            bool("noDelayAccuratePlacement", SETTING_ACCURATE_PLACEMENT, true);
    private final BooleanMapValue accurateItems = visibleWhen(
            group("noDelayAccurateItems", SETTING_ACCURATE_ITEMS, defaultAccurateItems()),
            accuratePlacement::get);
    private final Minecraft mc = Minecraft.getInstance();

    private long pressStartThrow = 0L;
    private long lastThrow = 0L;
    private long pressStartInteract = 0L;
    private long lastInteract = 0L;
    private BlockPos lastBoostedBreakPos;
    private final Vulcan297FastBreakTiming vulcanBreakTiming = new Vulcan297FastBreakTiming();
    private final AccuratePlacementController<PlacementTarget> accurateController = new AccuratePlacementController<>();
    private final AccuratePlacementPressLedger accuratePresses = new AccuratePlacementPressLedger();
    private final java.util.ArrayDeque<PlacementTarget> pendingBackfill = new java.util.ArrayDeque<>();
    private long lastAccurateUseTick = Long.MIN_VALUE;
    private long lastBackfillTick = Long.MIN_VALUE;
    private ClientLevel accurateLevel;
    private PlacementTarget executingAccuratePlacement;
    private boolean executingAccurateAccepted;
    private boolean observedAccurate = true;

    private static Map<String, Boolean> defaultTypes() {
        Map<String, Boolean> defaults = new LinkedHashMap<>();
        defaults.put(TYPE_JUMP, true);
        defaults.put(TYPE_RIGHT_CLICK, true);
        defaults.put(TYPE_BLOCK_PLACE, false);
        defaults.put(TYPE_BREAK_COOLDOWN, true);
        defaults.put(TYPE_FAST_BREAK, true);
        defaults.put(TYPE_FAST_THROW, true);
        defaults.put(TYPE_FAST_INTERACT, true);
        defaults.put(TYPE_SHIELD_COOLDOWN, true);
        return defaults;
    }

    private static Map<String, Boolean> defaultAccurateItems() {
        Map<String, Boolean> enabled = new LinkedHashMap<>();
        enabled.put("Tools", true);
        enabled.put("Buckets", true);
        enabled.put("Armor Stands", true);
        enabled.put("Item Frames", true);
        enabled.put("Spawn Eggs", true);
        return enabled;
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc == null || mc.player == null) {
            resetBreakBoost();
            clearAccuratePlacement();
            return;
        }

        while (isActionPressedOnce("toggle_accurate_placement")) {
            accuratePlacement.set(!accuratePlacement.get());
            syncAccurateState();
            saveConfig();
        }
        while (isActionPressedOnce("toggle_fast_breaking")) {
            types.set(TYPE_BREAK_COOLDOWN, !types.get(TYPE_BREAK_COOLDOWN));
            saveConfig();
        }
        syncAccurateState();

        if (effType(TYPE_RIGHT_CLICK)) {
            ((MinecraftAccessor) mc).combatant$setItemUseCooldown(0);
        } else if (effType(TYPE_BLOCK_PLACE)) {
            applyBlockPlaceCooldown();
        }

        if (effType(TYPE_BREAK_COOLDOWN) && mc.gameMode instanceof MultiPlayerGameModeAccessor accessor) {
            accessor.combatant$setBlockBreakingCooldown(0);
        }

        if (effType(TYPE_FAST_BREAK)) {
            applyFastBreakBoost();
        } else {
            resetBreakBoost();
        }

        if (effType(TYPE_JUMP)) {
            ((LivingEntityAccessor) mc.player).combatant$setJumpingCooldown(0);
        }
        if (!accurateAvailable()) clearAccuratePlacement();
    }


    public boolean shouldDisableShieldCooldown() {
        return isEnabled() && effType(TYPE_SHIELD_COOLDOWN);
    }

    @Override
    public void onDisable() {
        pressStartThrow = 0L;
        lastThrow = 0L;
        pressStartInteract = 0L;
        lastInteract = 0L;
        resetBreakBoost();
        clearAccuratePlacement();
    }

    public boolean handleFastUse() {
        if (!isEnabled()) return false;
        if (mc == null || mc.player == null || mc.gameMode == null) return false;
        if (!effType(TYPE_FAST_THROW) && !effType(TYPE_FAST_INTERACT)) return false;

        LocalPlayer player = mc.player;

        if (!mc.options.keyUse.isDown()) {
            pressStartThrow = 0L;
            pressStartInteract = 0L;
            return false;
        }

        Item main = player.getMainHandItem().getItem();
        Item off = player.getOffhandItem().getItem();
        HitResult hit = mc.hitResult;
        UseContext ctx = resolveFastUseContext(main, off, hit);
        if (ctx == null) return false;

        if (ctx.target == UseTarget.THROWABLE && !effType(TYPE_FAST_THROW)) return false;
        if ((ctx.target == UseTarget.BONE_MEAL_BLOCK || ctx.target == UseTarget.ITEM_FRAME) && !effType(TYPE_FAST_INTERACT)) {
            return false;
        }

        long now = System.currentTimeMillis();
        if (ctx.target == UseTarget.THROWABLE) {
            if (pressStartThrow == 0L) {
                pressStartThrow = now;
                return false;
            }
            if (useDelay.get() && (now - pressStartThrow) < delayMs.get()) return true;
            if ((now - lastThrow) < intervalMs.get()) return true;
            lastThrow = now;

            InteractionResult result = performFastUse(player, ctx);
            if (result != null && result.consumesAction()) player.swing(ctx.hand, player.getItemInHand(ctx.hand).getInteractAnimation(), false);
            return true;
        }

        if (pressStartInteract == 0L) {
            pressStartInteract = now;
            return false;
        }
        if ((now - pressStartInteract) < interactDelayMs.get()) return true;
        if ((now - lastInteract) < interactIntervalMs.get()) return true;
        lastInteract = now;

        InteractionResult result = performFastUse(player, ctx);
        if (result != null && result.consumesAction()) player.swing(ctx.hand, player.getItemInHand(ctx.hand).getInteractAnimation(), false);
        return true;
    }

    private void applyBlockPlaceCooldown() {
        if (mc.player == null || !mc.options.keyUse.isDown()) return;
        if (!isBlockPlaceCandidate(mc.player, mc.hitResult)) return;

        MinecraftAccessor accessor = (MinecraftAccessor) mc;
        int current = accessor.combatant$getItemUseCooldown();
        int maxCooldown = blockPlaceCooldownTicks.get();
        if (current > maxCooldown) {
            accessor.combatant$setItemUseCooldown(maxCooldown);
        }
    }

    private boolean isBlockPlaceCandidate(LocalPlayer player, HitResult hit) {
        if (!(hit instanceof BlockHitResult)) return false;
        Item main = player.getMainHandItem().getItem();
        Item off = player.getOffhandItem().getItem();
        return main instanceof BlockItem || off instanceof BlockItem;
    }

    @EventHandler
    private void onAccurateInput(BlockPlacementInputEvent event) {
        if (event.isCancelled() || event.getReplayOwner() != null) return;
        processAccurateTarget(event.getTarget(), accuratePresses.observe(event.getClickCount()), event.isHeld(), event);
    }

    @EventHandler(priority = -1000)
    private void onFastUseInput(BlockPlacementInputEvent event) {
        if (!event.isCancelled() && event.getReplayOwner() == null && handleFastUse()) event.cancel();
    }

    @EventHandler(priority = -10000)
    private void onAccuratePick(CrosshairTargetUpdateEvent event) {
        processAccurateTarget(event.getHitResult(), accuratePresses.observe(event.getClickCount()), event.isHeld(), null);
        if (event.isHeld()) replayAccurateBackfill();
    }

    @EventHandler
    private void onAccurateWorldChange(WorldChangeEvent event) {
        clearAccuratePlacement();
    }

    private boolean accurateAvailable() {
        return isEnabled() && accuratePlacement.get() && mc.player != null && mc.level != null
                && mc.gameMode != null && ClientScreen.current() == null;
    }

    private InteractionHand processAccurateTarget(HitResult target, int clicks, boolean held, BlockPlacementInputEvent input) {
        if (BlockInteractionPipeline.INSTANCE.inputOwner() != null) return null;
        syncAccurateState();
        if (!accurateAvailable()) {
            clearAccuratePlacement();
            return null;
        }
        if (accurateLevel != mc.level) {
            clearAccuratePlacement();
            accurateLevel = mc.level;
        }
        InteractionHand hand = placementHand();
        if (hand == null) {
            accurateController.reset();
            pendingBackfill.clear();
            return null;
        }
        Vec3 mouse = mouseRatio();
        if (clicks > 0) {
            accurateController.freshPress(mouse);
            pendingBackfill.clear();
        }
        if (!held && clicks == 0) {
            accurateController.reset();
            pendingBackfill.clear();
            return null;
        }
        if (isAccurateExcluded(target, hand, clicks > 0, held)) return null;
        BlockHitResult hit = (BlockHitResult) target;
        if (input != null) input.claim(hand);
        PlacementTarget plan = snapshot(hit, hand);
        int cooldown = ((MinecraftAccessor) mc).combatant$getItemUseCooldown();
        AccuratePlacementController.Decision<PlacementTarget> decision = accurateController.select(
                plan, hit.getBlockPos(), hit.getDirection(), plan.destination(),
                clicks, held, cooldown, mc.player.position(), mouse);
        // Input was observed above, never drained: the vanilla keybinding owns its clicks.
        if (decision.uses() > 0) {
            BlockState oldState = mc.level.getBlockState(plan.destination());
            for (PlacementTarget previous : decision.backfill()) {
                if (isTargetValid(previous, true)) pendingBackfill.addLast(previous);
            }
            for (int i = 0; i < decision.uses(); i++) {
                if (!isTargetValid(plan, false)) break;
                boolean accepted = executeAccurate(plan);
                recordAccuratePlacement(plan, oldState, accepted);
            }
        }
        return hand;
    }

    private Vec3 mouseRatio() {
        int width = mc.getWindow().getScreenWidth(), height = mc.getWindow().getScreenHeight();
        return width > 0 && height > 0
                ? new Vec3(mc.mouseHandler.xpos() / width, mc.mouseHandler.ypos() / height, 0) : null;
    }

    private PlacementTarget snapshot(BlockHitResult hit, InteractionHand hand) {
        BlockPlaceContext context = new BlockPlaceContext(new UseOnContext(mc.player, hand, hit));
        return new PlacementTarget(hit, hand, context.getClickedPos().immutable(),
                mc.player.getMainHandItem().copy(), mc.player.getOffhandItem().copy(),
                mc.player.getInventory().getSelectedSlot(), mc.level,
                mc.level.getBlockState(hit.getBlockPos()), mc.level.getBlockState(context.getClickedPos()));
    }

    private boolean isAccurateExcluded(HitResult target, InteractionHand hand, boolean fresh, boolean held) {
        if (mc.player.isUsingItem()) return true;
        ItemStack stack = mc.player.getItemInHand(hand);
        if (!isAccurateItemAllowed(stack)) return true;
        if (doesItemUse(stack)) return true;
        if (!(target instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return true;
        BlockState state = mc.level.getBlockState(hit.getBlockPos());
        Block block = state.getBlock();
        if (state.isAir()) return true;
        if (ACTIVATABLE_BLOCK.get(block.getClass()) && !(block instanceof StairBlock)
                && !mc.player.isShiftKeyDown()) return true;
        if ((block instanceof BaseEntityBlock || block instanceof BedBlock) && !fresh && held) return true;
        if (stack.getItem() instanceof ScaffoldingBlockItem && block instanceof ScaffoldingBlock) return true;
        if (block instanceof ComposterBlock && stack.has(DataComponents.COMPOSTABLE)) return true;
        if (mc.player.getMainHandItem().getItem() instanceof BoneMealItem && block instanceof BonemealableBlock) return true;
        NoInteract noInteract = Modules.get(NoInteract.class);
        if (noInteract != null && noInteract.shouldBlockBlockInteraction()) return true;
        AutoBow autoBow = Modules.get(AutoBow.class);
        if (autoBow != null && autoBow.shouldCancelVanillaDoItemUse()) return true;
        return false;
    }

    private boolean doesItemUse(ItemStack stack) {
        return hasSpecialItemUse(stack, isAccurateItemAllowed(stack));
    }

    static boolean hasSpecialItemUse(ItemStack stack, boolean allowedCategory) {
        if (stack.isEmpty()) return false;
        var equippable = stack.get(DataComponents.EQUIPPABLE);
        if (stack.has(DataComponents.CONSUMABLE) || stack.has(DataComponents.BLOCKS_ATTACKS)
                || stack.has(DataComponents.KINETIC_WEAPON) || (equippable != null && equippable.swappable())) return true;
        return !allowedCategory && ITEM_USES.get(stack.getItem().getClass());
    }

    static boolean overrides(Class<?> type, Class<?> base, String name, Class<?>... arguments) {
        try {
            base.getDeclaredMethod(name, arguments);
            for (Class<?> current = type; current != null && current != base; current = current.getSuperclass()) {
                try {
                    current.getDeclaredMethod(name, arguments);
                    return true;
                } catch (NoSuchMethodException ignored) { }
            }
            return !base.isAssignableFrom(type);
        } catch (ReflectiveOperationException | SecurityException error) {
            return true;
        }
    }

    private static final ClassValue<Boolean> ACTIVATABLE_BLOCK = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            return overrides(type, BlockBehaviour.class, "useWithoutItem", BlockState.class, Level.class,
                    BlockPos.class, Player.class, BlockHitResult.class)
                    || overrides(type, BlockBehaviour.class, "useItemOn", ItemStack.class, BlockState.class,
                    Level.class, BlockPos.class, Player.class, InteractionHand.class, BlockHitResult.class);
        }
    };

    private static final ClassValue<Boolean> ITEM_USES = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            return overrides(type, Item.class, "use", Level.class, Player.class, InteractionHand.class);
        }
    };

    private boolean isAccurateItemAllowed(ItemStack stack) {
        if (stack.isEmpty()) return false;
        Item item = stack.getItem();
        return item instanceof BlockItem
                || (accurateItems.get("Tools") && (stack.has(DataComponents.BLOCK_TRANSFORMER) || item instanceof FlintAndSteelItem))
                || (accurateItems.get("Buckets") && item instanceof BucketItem)
                || (accurateItems.get("Armor Stands") && item instanceof ArmorStandItem)
                || (accurateItems.get("Item Frames") && item instanceof ItemFrameItem)
                || (accurateItems.get("Spawn Eggs") && item instanceof SpawnEggItem);
    }

    private InteractionHand placementHand() {
        for (InteractionHand hand : InteractionHand.values()) {
            if (isAccurateItemAllowed(mc.player.getItemInHand(hand))) return hand;
        }
        return null;
    }

    private boolean sameHeldItems(PlacementTarget plan) {
        return mc.level == plan.level() && mc.player != null
                && mc.player.getInventory().getSelectedSlot() == plan.slot()
                && ItemStack.isSameItemSameComponents(mc.player.getMainHandItem(), plan.mainStack())
                && ItemStack.isSameItemSameComponents(mc.player.getOffhandItem(), plan.offStack());
    }

    private boolean isTargetValid(PlacementTarget plan, boolean replay) {
        if (replay && !accurateController.canAutoPlace(
                plan.hit().getBlockPos(), plan.destination())) return false;
        if (!accurateAvailable() || !sameHeldItems(plan) || mc.player.isUsingItem()) return false;
        if (isAccurateExcluded(plan.hit(), plan.hand(), !replay, replay)) return false;
        if (!mc.level.getBlockState(plan.hit().getBlockPos()).equals(plan.supportState())) return false;
        if (!mc.level.getBlockState(plan.destination()).equals(plan.destinationState())) return false;
        double range = mc.player.blockInteractionRange();
        if (mc.player.getEyePosition().distanceToSqr(plan.hit().getLocation()) > range * range) return false;
        BlockPlaceContext context = new BlockPlaceContext(new UseOnContext(mc.player, plan.hand(), plan.hit()));
        return context.getClickedPos().equals(plan.destination());
    }

    private boolean executeAccurate(PlacementTarget plan) {
        lastAccurateUseTick = mc.level.getGameTime();
        executingAccuratePlacement = plan;
        executingAccurateAccepted = false;
        try {
            BlockInteractionPipeline.INSTANCE.useBlockInput(this, plan.hit());
            return executingAccurateAccepted;
        } finally {
            executingAccuratePlacement = null;
            executingAccurateAccepted = false;
        }
    }

    private void replayAccurateBackfill() {
        if (BlockInteractionPipeline.INSTANCE.inputOwner() != null
                || !accurateAvailable() || pendingBackfill.isEmpty()
                || mc.player.isUsingItem()) return;
        long tick = mc.level.getGameTime();
        if (tick == lastBackfillTick || tick == lastAccurateUseTick
                || ((MinecraftAccessor) mc).combatant$getItemUseCooldown() > 0) return;
        lastBackfillTick = tick;
        while (!pendingBackfill.isEmpty()) {
            PlacementTarget previous = pendingBackfill.removeFirst();
            if (!isTargetValid(previous, true)) continue;
            BlockState before = mc.level.getBlockState(previous.destination());
            boolean accepted = executeAccurate(previous);
            recordAccuratePlacement(previous, before, accepted);
            break;
        }
    }

    private void recordAccuratePlacement(PlacementTarget plan, BlockState before, boolean acceptedBlockPlacement) {
        if (mc.level != plan.level() || mc.player == null) return;
        if (acceptedBlockPlacement || (before != null
                && !before.equals(mc.level.getBlockState(plan.destination())))) {
            accurateController.placed(plan.destination(), plan.hit().getDirection(), mc.player.position());
            pendingBackfill.removeIf(previous -> !accurateController.canAutoPlace(
                    previous.hit().getBlockPos(), previous.destination()));
        }
    }

    private void clearAccuratePlacement() {
        executingAccuratePlacement = null;
        executingAccurateAccepted = false;
        accurateController.reset();
        accuratePresses.reset();
        pendingBackfill.clear();
        lastAccurateUseTick = Long.MIN_VALUE;
        lastBackfillTick = Long.MIN_VALUE;
        accurateLevel = null;
    }

    @EventHandler
    private void onAccurateUseResult(BlockUseAttemptEvent event) {
        PlacementTarget active = executingAccuratePlacement;
        if (active != null && BlockInteractionPipeline.INSTANCE.inputOwner() == this
                && event.getHit() != null && event.getHand() == active.hand()
                && event.isBlockItemAtStart() && event.getResult() != null
                && event.getResult().consumesAction()
                && event.getHit().getBlockPos().equals(active.hit().getBlockPos())
                && event.getHit().getDirection() == active.hit().getDirection()) {
            executingAccurateAccepted = true;
        }
    }

    private void syncAccurateState() {
        if (observedAccurate != accuratePlacement.get()) {
            observedAccurate = accuratePlacement.get();
            clearAccuratePlacement();
        }
    }

    @Override
    public void onEnable() {
        observedAccurate = accuratePlacement.get();
        clearAccuratePlacement();
    }

    private record PlacementTarget(BlockHitResult hit, InteractionHand hand, BlockPos destination,
                                   ItemStack mainStack, ItemStack offStack, int slot, ClientLevel level,
                                   BlockState supportState, BlockState destinationState) { }

    @EventHandler
    private void onDigPacket(PacketEvent.SendPost event) {
        if (!isEnabled() || !types.get(TYPE_FAST_BREAK)
                || fastBreakMode.get() != FastBreakMode.VULCAN_297) return;
        if (!(event.getPacket() instanceof ServerboundPlayerActionPacket packet)) return;
        switch (packet.getAction()) {
            case START_DESTROY_BLOCK -> {
                long now = System.nanoTime();
                BlockPos target = packet.getPos();
                vulcanBreakTiming.start(target, now);
                // Capture the initial tool/block speed too: Vulcan computes its
                // deadline at the initial click, not at the later STOP packet.
                if (mc.level != null && mc.player != null) {
                    BlockState state = mc.level.getBlockState(target);
                    if (!state.isAir()) {
                        vulcanBreakTiming.ready(target,
                                state.getDestroyProgress(mc.player, mc.level, target),
                                0.0f, 1.0f, now);
                    }
                }
                lastBoostedBreakPos = null;
            }
            case STOP_DESTROY_BLOCK, ABORT_DESTROY_BLOCK -> vulcanBreakTiming.stop(packet.getPos());
            default -> { }
        }
    }

    private void applyFastBreakBoost() {
        if (mc.gameMode == null || mc.getConnection() == null) {
            resetBreakBoost();
            return;
        }
        if (!mc.gameMode.isDestroying()) {
            resetBreakBoost();
            return;
        }
        if (!(mc.gameMode instanceof MultiPlayerGameModeAccessor accessor)) {
            return;
        }

        BlockPos pos = accessor.combatant$getCurrentBreakingPos();
        if (pos == null) return;

        float progress = accessor.combatant$getCurrentBreakingProgress();
        if (pos.equals(lastBoostedBreakPos)) return;

        if (fastBreakMode.get() == FastBreakMode.VULCAN_297) {
            if (mc.level == null || mc.player == null || mc.player.getAbilities().instabuild) return;
            BlockState state = mc.level.getBlockState(pos);
            if (state.isAir()) return;

            float damagePerTick = state.getDestroyProgress(mc.player, mc.level, pos);
            // FastBreak A bypasses its duration check for a genuine Haste effect.
            // This is a check-specific exemption, not a reason to forge dig packets.
            boolean exemptFromDuration = mc.player.hasEffect(MobEffects.HASTE);
            if (!exemptFromDuration && !vulcanBreakTiming.ready(pos, damagePerTick, progress,
                    effFastBreakProgress(), System.nanoTime())) return;
            if (progress < effFastBreakProgress()) return;

            // Never send a premature STOP/ABORT pair. Vanilla sends the final STOP
            // (and performs its own block prediction) on the next breaking update.
            accessor.combatant$setCurrentBreakingProgress(1.0f);
            lastBoostedBreakPos = pos.immutable();
            return;
        }

        if (progress < effFastBreakProgress()) return;
        vulcanBreakTiming.reset();
        Direction direction = resolveBreakingDirection(pos);
        mc.getConnection().send(
                new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos, direction)
        );
        mc.getConnection().send(
                new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, pos, direction)
        );

        lastBoostedBreakPos = pos.immutable();
    }

    private void resetBreakBoost() {
        lastBoostedBreakPos = null;
        vulcanBreakTiming.reset();
    }

    private boolean effType(String type) {
        return types.get(type);
    }

    private float effFastBreakProgress() {
        return fastBreakProgress.get();
    }

    private Direction resolveBreakingDirection(BlockPos breakingPos) {
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult blockHit && breakingPos.equals(blockHit.getBlockPos())) {
            return blockHit.getDirection();
        }
        return Direction.DOWN;
    }

    private boolean isThrowable(Item item) {
        return item == Items.EXPERIENCE_BOTTLE || item == Items.SNOWBALL || item == Items.EGG;
    }

    private boolean isBoneMeal(Item item) {
        return item == Items.BONE_MEAL;
    }

    private boolean isItemFrameTargeted(HitResult hit) {
        return hit instanceof EntityHitResult ehr && ehr.getEntity() instanceof ItemFrame;
    }

    private UseContext resolveFastUseContext(Item main, Item off, HitResult hit) {
        if (isThrowable(main)) return new UseContext(InteractionHand.MAIN_HAND, UseTarget.THROWABLE, null, null);
        if (isThrowable(off)) return new UseContext(InteractionHand.OFF_HAND, UseTarget.THROWABLE, null, null);

        if (isBoneMeal(main) && hit instanceof BlockHitResult bhr) {
            return new UseContext(InteractionHand.MAIN_HAND, UseTarget.BONE_MEAL_BLOCK, bhr, null);
        }
        if (isBoneMeal(off) && hit instanceof BlockHitResult bhr) {
            return new UseContext(InteractionHand.OFF_HAND, UseTarget.BONE_MEAL_BLOCK, bhr, null);
        }

        if (isItemFrameTargeted(hit)) {
            return new UseContext(InteractionHand.MAIN_HAND, UseTarget.ITEM_FRAME, null, (EntityHitResult) hit);
        }
        return null;
    }

    private InteractionResult performFastUse(LocalPlayer player, UseContext ctx) {
        if (ctx.target == UseTarget.THROWABLE) {
            return mc.gameMode.useItem(player, ctx.hand);
        }
        if (ctx.target == UseTarget.BONE_MEAL_BLOCK) {
            return BlockInteractionPipeline.INSTANCE.useOnBlock(
                    this, player, ctx.hand, -1, 0, ctx.blockHit, false
            );
        }
        if (ctx.target == UseTarget.ITEM_FRAME) {
            return mc.gameMode.interact(player, ctx.entityHit.getEntity(), ctx.entityHit, ctx.hand);
        }
        return InteractionResult.PASS;
    }

    private enum UseTarget {
        THROWABLE,
        BONE_MEAL_BLOCK,
        ITEM_FRAME
    }

    private enum FastBreakMode implements EnumValue.IdProvider {
        NORMAL("normal"),
        VULCAN_297("vulcan_297");

        private final String id;

        FastBreakMode(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }
    }

    private record UseContext(InteractionHand hand, UseTarget target, BlockHitResult blockHit,
                              EntityHitResult entityHit) {
    }
}
