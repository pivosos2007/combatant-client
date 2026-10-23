/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player.schematic;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.ItemIdSetValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.MovementInputEvent;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.aiming.features.processors.anglesmooth.impl.LinearAngleSmooth;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.schematic.RedstoneRules;
import combatant.client.util.schematic.Schematic;
import combatant.client.util.schematic.SchematicAttention;
import combatant.client.util.schematic.SchematicDiff;
import combatant.client.util.schematic.SchematicMaterials;
import combatant.client.util.schematic.SchematicPlacement;
import combatant.client.util.schematic.SchematicState;
import combatant.client.util.schematic.StateAdjustments;
import combatant.client.util.schematic.StateComparison;
import combatant.client.util.schematic.walk.WalkTerrain;
import combatant.client.util.screen.ClientScreen;
import combatant.client.util.server.DonutSmp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Places the missing blocks of the selected schematic layer from where the player stands, bottom-up.
 *
 * <p>With rotations on, a click only goes out once both the rotation the server already has and the one this
 * tick's movement packet will carry look at the planned face: vanilla resolves facing from the former and
 * Grim checks the click against the latter.</p>
 */
@ModuleInfo(
        id = "schematicbuilder",
        displayName = "SchematicBuilder",
        aliases = {"Printer", "LitematicaPrinter", "AutoBuild"},
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.AUTOMATION,
        description = "module.schematicbuilder.description")
public final class SchematicBuilder extends Module {

    private static final int ROTATION_PRIORITY = 10;
    private static final int PLAN_TIMEOUT_TICKS = 20;
    private static final int RETRY_AFTER_TICKS = 40;
    private static final int TERRAFORM_TIMEOUT_TICKS = 400;
    private static final int RECENT_PLACE_TICKS = 10;
    /** A stair or slab clicked before the rotation settled lands in the wrong half; this is how long to wait for it. */
    private static final int EXACT_WAIT_TICKS = 4;
    private static final int MAX_PLAN_ATTEMPTS = 12;
    private static final int MAX_CANDIDATES = 96;
    private static final int SHORTFALL_INTERVAL = 20;
    private static final int MISSING_NOTIFY_INTERVAL = 200;
    /** How long a piston or observer that only comes out facing wrong from here waits for the walker to find a better spot. */
    private static final int APPROXIMATE_PATIENCE_TICKS = 400;
    /** Times a cell that would fire on placement is put back before it goes in anyway (with a warning). */
    private static final int HAZARD_DEFER_LIMIT = 3;
    /** Nothing placed for this long and nothing left to walk to: the next band opens early. */
    private static final int GATE_STALL_TICKS = 300;
    private static final int GATE_CHECK_INTERVAL = 20;
    /** At most one use per tick reaches the server; a gap lets the client see the new delay or note first. */
    private static final int ADJUST_CLICK_DELAY = 2;
    private static final int ADJUST_SLACK_CLICKS = 4;
    private static final int ATTENTION_PRUNE_INTERVAL = 20;
    private static final double DONUT_RANGE = 4.0;
    private static final int DONUT_MIN_DELAY = 3;
    private static final int DONUT_MAX_JITTER = 2;
    private static final float DONUT_MAX_ROTATION_SPEED = 45.0f;

    public enum ApproximateMode {
        PLACE,
        SKIP
    }

    public enum Status {
        IDLE,
        NO_SCHEMATIC,
        WORKING,
        PAUSED,
        MISSING_MATERIALS,
        OUT_OF_REACH,
        WALKING,
        PILLARING,
        CLEARING,
        TERRAFORMING,
        RESTOCKING,
        DONE
    }

    private static final Set<String> DEFAULT_HELPER_BLOCKS =
            new LinkedHashSet<>(List.of("minecraft:cobblestone", "minecraft:dirt"));

    private final NumberValue<Float> range = num("range", 4.0f, 2.0f, 4.5f);
    private final NumberValue<Integer> delay = num("delay", 1, 0, 20);
    private final NumberValue<Integer> blocksPerTick = num("blocks_per_tick", 1, 1, 4);
    private final BooleanValue rotate = bool("rotate", true);
    private final NumberValue<Float> rotationSpeedMin =
            visibleWhen(num("rotation_speed_min", 60.0f, 5.0f, 180.0f), rotate::get);
    private final NumberValue<Float> rotationSpeedMax =
            visibleWhen(num("rotation_speed_max", 120.0f, 5.0f, 180.0f), rotate::get);
    private final BooleanValue silentSwap = bool("silent_swap", true);
    private final BooleanValue restock = bool("restock", true);
    private final NumberValue<Integer> restockSlot = visibleWhen(num("restock_slot", 9, 1, 9), restock::get);
    private final BooleanValue pauseWhenMissing = bool("pause_when_missing", true);
    private final BooleanValue indexNearbyChests = visibleWhen(bool("index_nearby_chests", true), restock::get);
    private final NumberValue<Integer> chestScanRange = visibleWhen(
            num("chest_scan_range", 16, 4, 32), () -> restock.get() && indexNearbyChests.get());
    private final BooleanValue autoAdvanceLayer = bool("auto_advance_layer", true);
    private final BooleanValue strictLayers = bool("strict_layers", true);
    private final EnumValue<ApproximateMode> approximate = enumMode("approximate_states", ApproximateMode.PLACE);
    private final BooleanValue stopOnDamage = bool("stop_on_damage", false);
    private final NumberValue<Float> pauseHealth = num("pause_health", 6.0f, 0.0f, 20.0f);
    private final BooleanValue autoWalk = bool("auto_walk", true);
    private final BooleanValue terraform = bool("terraform", true);
    private final BooleanValue terraformAir = visibleWhen(bool("terraform_air", false), terraform::get);
    private final NumberValue<Integer> stuckTimeout = visibleWhen(num("stuck_timeout", 100, 40, 400), autoWalk::get);
    private final BooleanValue autoJumpPillar = visibleWhen(bool("auto_jump_pillar", true), autoWalk::get);
    private final ItemIdSetValue helperBlocks =
            visibleWhen(itemList("helper_blocks", DEFAULT_HELPER_BLOCKS), () -> autoWalk.get() && autoJumpPillar.get());
    private final BooleanValue removeHelpers =
            visibleWhen(bool("remove_helpers", true), () -> autoWalk.get() && autoJumpPillar.get());
    private final BooleanValue donutSmpSafe = bool("donutsmp_safe", true);

    private final Minecraft mc = Minecraft.getInstance();
    private final SchematicState state = SchematicState.INSTANCE;
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
    private final NumberValue<Float> effectiveSpeedMin = new NumberValue<>("effective_speed_min", 30.0f, 1.0f, 180.0f);
    private final NumberValue<Float> effectiveSpeedMax = new NumberValue<>("effective_speed_max", 60.0f, 1.0f, 180.0f);
    private final LinearAngleSmooth smoothing =
            new LinearAngleSmooth(effectiveSpeedMin, effectiveSpeedMax, effectiveSpeedMin, effectiveSpeedMax);
    private final Map<Long, Integer> retryAfter = new HashMap<>();
    private BlockPos terraformTarget;
    private int terraformTicks;
    private final int[] candidateCells = new int[MAX_CANDIDATES];
    private final double[] candidateDistance = new double[MAX_CANDIDATES];
    private final BuildMover mover = new BuildMover(mc, new MoverHost());
    private final BuildSafety safety = new BuildSafety();
    private final ChestIndex chestIndex = new ChestIndex();

    /** The builder's container index, read by the in-world supply highlight. */
    public ChestIndex chestIndex() {
        return chestIndex;
    }

    /** Facing-sensitive targets with only an approximate click from here: when that was first seen. */
    private final Map<Long, Integer> approximateSince = new HashMap<>();
    /** The look direction such a target needs; the walker only counts it from spots that give it. */
    private final Map<Long, Direction> lookWanted = new HashMap<>();
    /** Of those, the ones where only the yaw counts (repeaters, comparators, floor levers). */
    private final Set<Long> yawOnly = new HashSet<>();
    private final Map<Long, Integer> hazardDefers = new HashMap<>();
    private final Map<Long, Integer> adjustClicks = new HashMap<>();
    private final long[] gateScratch = new long[1];
    private int lastPlaceTick;
    private int adjusted;
    private int[] footprint;
    private boolean aimedThisTick;
    private int rotationWaitTicks;
    private int exactWaitTicks;
    private BlockPos restockChest;
    private int restockChestTicks;
    private boolean openChestPending;
    private BuildPlanner.Plan restockPlan;
    private int containerOpenTicks;
    private int containerClickCooldown;
    private final SneakPacing sneakPacing = new SneakPacing();


    private Pending pending;
    private int pendingAge;
    private int cooldown;
    private int ticks;
    private int placed;
    private int approximatePlaced;
    private float lastHealth = -1.0f;
    private ClientLevel lastLevel;
    private Status status = Status.IDLE;
    /** Far enough in the past that {@code ticks - NEVER} neither overflows nor lands under an interval. */
    private static final int NEVER = Integer.MIN_VALUE / 2;
    private int lastShortfallTick = NEVER;
    private int lastMissingNotifyTick = NEVER;
    private int completedLayerNotified = -1;
    private boolean missingThisTick;

    @Override
    public void onEnable() {
        reset();
        // Config load can enable the module before any world exists; only warn a player who can act on it.
        if (mc.player != null && !state.isPlaced()) {
            Notifier.warning(I18n.get("module.schematicbuilder.notify.no_placement"));
        }
    }

    @Override
    public void onDisable() {
        reset();
        state.setMissingMaterials(List.of());
        status = Status.IDLE;
    }

    private void reset() {
        RotationManager.INSTANCE.release(this);
        InventorySwap.INSTANCE.releaseHotbar(this);
        pending = null;
        pendingAge = 0;
        cooldown = 0;
        retryAfter.clear();
        terraformTarget = null;
        lastHealth = -1.0f;
        lastLevel = mc.level;
        completedLayerNotified = -1;
        lastShortfallTick = NEVER;
        mover.clearAll();
        footprint = null;
        rotationWaitTicks = 0;
        exactWaitTicks = 0;
        safety.reset();
        approximateSince.clear();
        lookWanted.clear();
        yawOnly.clear();
        hazardDefers.clear();
        adjustClicks.clear();
        lastPlaceTick = ticks;
        restockChest = null;
        restockChestTicks = 0;
        openChestPending = false;
        restockPlan = null;
        containerOpenTicks = 0;
        containerClickCooldown = 0;
        sneakPacing.reset();
    }

    public Status status() {
        return status;
    }

    public int placedCount() {
        return placed;
    }

    public int approximateCount() {
        return approximatePlaced;
    }

    /** Clicks spent on placed blocks to set a delay, mode, note or lever. */
    public int adjustedCount() {
        return adjusted;
    }

    /** Walk statistics for diagnostics: routes planned, route cells, blocks walked, pillar blocks, helpers broken, aborts. */
    public String moverSummary() {
        return String.format(java.util.Locale.ROOT,
                "routes=%d routeCells=%d walked=%.1f pillar=%d pillarJumps=%d helpersRemoved=%d aborts=%d rotWait=%d exactWait=%d "
                        + "adjusted=%d forcedBand=%d %s",
                mover.routes(), mover.routeCells(), mover.walked(), mover.pillarBlocks(), mover.pillarJumps(),
                mover.helpersRemoved(), mover.aborts(), rotationWaitTicks, exactWaitTicks, adjusted,
                safety.forcedBand(), mover);
    }

    private boolean donutRules() {
        return donutSmpSafe.get() && DonutSmp.isConnected();
    }

    private double effectiveRange() {
        double value = range.get();
        return donutRules() ? Math.min(value, DONUT_RANGE) : value;
    }

    private int nextDelay() {
        int value = delay.get();
        if (!donutRules()) return value;
        // A fixed cadence is the easiest pattern to spot; jitter keeps it at roughly a human pace.
        return Math.max(value, DONUT_MIN_DELAY) + ThreadLocalRandom.current().nextInt(DONUT_MAX_JITTER + 1);
    }

    private int effectiveBlocksPerTick() {
        return donutRules() ? 1 : blocksPerTick.get();
    }

    private boolean effectiveRotate() {
        return rotate.get() || donutRules();
    }

    @Override
    public void onTick() {
        LocalPlayer player = mc.player;
        if (!isEnabled() || player == null || mc.level == null) return;
        ticks++;
        if (mc.level != lastLevel) {
            reset();
            lastLevel = mc.level;
        }
        if (stopOnDamage.get() && lastHealth >= 0.0f && player.getHealth() < lastHealth - 0.01f) {
            Notifier.warning(I18n.get("module.schematicbuilder.notify.damaged"));
            setEnabled(false);
            return;
        }
        lastHealth = player.getHealth();
        mover.trackDistance(player);
        state.tickDiff(mc, 256, 256);
        retryAfter.values().removeIf(until -> until <= ticks);
        if (ticks - lastShortfallTick >= SHORTFALL_INTERVAL) {
            lastShortfallTick = ticks;
            updateShortfall(player);
        }
        if (ticks % GATE_CHECK_INTERVAL == 0) checkGateStall(player);
        if (ticks % ATTENTION_PRUNE_INTERVAL == 0) pruneAttention();
        if (player.containerMenu != player.inventoryMenu && mc.hitResult instanceof BlockHitResult bhr
                && SupplyPoint.isContainer(mc.level, bhr.getBlockPos())) {
            chestIndex.recordOpenContainer(mc.level, player, bhr.getBlockPos());
        }
    }

    /**
     * Power waits until every lower cell is in. When some never come (no material, nowhere to stand) and nothing
     * was placed for a while with nothing left to walk to, the next band opens anyway and the cells that held it
     * shut are listed in the menu.
     */
    private void checkGateStall(LocalPlayer player) {
        SchematicDiff diff = state.currentDiff();
        if (diff == null || !safety.gated(diff) || ticks - lastPlaceTick < GATE_STALL_TICKS) return;
        // Walking or climbing towards the missing cells is progress; standing with nothing to do is not. Cells
        // still worth walking to hold the gate four times as long (a pillar can take a few tries).
        if (pending != null || autoWalk.get() && mover.mode() != BuildMover.Mode.IDLE) return;
        if (ticks - lastPlaceTick < GATE_STALL_TICKS * 4 && walkTargets(player, gateScratch) > 0) return;
        int band = safety.openBand(diff);
        flagOpenCells(diff, band);
        safety.force(band + 1);
        lastPlaceTick = ticks;
        Notifier.warning(I18n.get("module.schematicbuilder.notify.power_early"));
    }

    private void flagOpenCells(SchematicDiff diff, int band) {
        int flagged = 0;
        for (int cell = 0, count = diff.cellCount(); cell < count && flagged < 32; cell++) {
            if (!BuildSafety.open(diff.status(cell)) || diff.order().cellBand(cell) != band) continue;
            state.flag(diff.worldPos(cell, scratch), diff.expectedState(cell), SchematicAttention.Reason.BEFORE_POWER);
            flagged++;
        }
    }

    /** Entries whose cell has since come out right are dropped. */
    private void pruneAttention() {
        if (state.attentionCount() == 0 || mc.level == null) return;
        for (SchematicAttention.Entry entry : state.attention()) {
            if (entry.pos() == null || !mc.level.hasChunkAt(entry.pos())) continue;
            if (StateComparison.matches(mc.level.getBlockState(entry.pos()), entry.state())) state.unflag(entry.pos());
        }
    }

    @EventHandler(priority = -15)
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (!isEnabled()) return;
        if (event.getType() == RotationUpdateEvent.Type.PRE) {
            pre();
        } else {
            post();
        }
    }

    private boolean canWork(LocalPlayer player) {
        if (player == null || mc.level == null || mc.gameMode == null) return false;
        if (!state.isPlaced()) {
            status = Status.NO_SCHEMATIC;
            return false;
        }
        if (ClientScreen.current() != null || player.isUsingItem() || player.containerMenu != player.inventoryMenu
                || pauseHealth.get() > 0.0f && player.getHealth() <= pauseHealth.get()) {
            status = Status.PAUSED;
            return false;
        }
        return true;
    }

    @EventHandler
    private void onMovementInput(MovementInputEvent event) {
        LocalPlayer player = mc.player;
        if (!isEnabled() || player == null) return;
        if (autoWalk.get()) mover.applyInput(event, player);
        // Grim MultiActions / Vulcan BadPackets I: no sprint while a container is open or being opened.
        if (player.containerMenu != player.inventoryMenu || openChestPending || restockChest != null) {
            event.setSprint(false);
            if (player.isSprinting()) player.setSprinting(false);
        }
        Pending work = pending;
        if (openChestPending || restockChest != null) {
            event.setSneak(sneakPacing.apply(false, ticks));
            return;
        }
        // Placing against a lever, repeater, hopper or door goes out sneaking so it does not toggle or open;
        // clicking a block to set its delay or note must not, or vanilla places the held block instead.
        // The sneak is held and spaced by SneakPacing so START_SNEAKING packets never come closer than 2 ticks.
        if (work != null && work.kind == Kind.PLACE && work.plan.sneak()) {
            event.setSneak(sneakPacing.apply(true, ticks));
        } else if (work != null && work.kind == Kind.ADJUST || sneakPacing.holding()) {
            event.setSneak(sneakPacing.apply(false, ticks));
        }
    }

    private void pre() {
        LocalPlayer player = mc.player;
        aimedThisTick = false;
        openChestPending = false;
        if (player.containerMenu == player.inventoryMenu) {
            containerOpenTicks = 0;
            containerClickCooldown = 0;
        }
        if (restockChest != null && player.containerMenu != player.inventoryMenu) {
            tickOpenRestockChest(player);
            return;
        }
        if (!canWork(player)) {
            pending = null;
            mover.pause();
            RotationManager.INSTANCE.release(this);
            return;
        }
        if (cooldown > 0) cooldown--;
        if (pending != null && (++pendingAge > PLAN_TIMEOUT_TICKS || !stillValid(player, pending))) {
            if (pendingAge > PLAN_TIMEOUT_TICKS && pending.kind != Kind.HELPER) {
                retryAfter.put(pending.plan.target().asLong(), ticks + RETRY_AFTER_TICKS);
            }
            pending = null;
        }
        // Planning during the cooldown lets the rotation travel to the next block while the delay runs out.
        // Mid pillar jump the support click owns the click and the rotation: a build block planned now would turn
        // the view away from the block under the feet and the jump would land without its support.
        if (pending == null && !(autoWalk.get() && mover.midPillarJump())) {
            pending = chooseAdjustment(player);
            if (pending == null && terraform.get() && terraform(player)) {
                status = Status.TERRAFORMING;
                return;
            }
            if (pending == null) {
                mover.stopMiningCell();
                terraformTarget = null;
                pending = choose(player);
                if (pending == null && status == Status.DONE && terraform.get() && terraformRemaining()) {
                    status = Status.OUT_OF_REACH;
                }
            }
            pendingAge = 0;
        }
        if (pending == null && autoWalk.get() && mover.midPillarJump()) status = Status.OUT_OF_REACH;
        // Nothing in reach and nothing to walk to because the stock for the remaining cells is gone: that is a
        // shortfall too, or the walker idles and the chest restock below never starts.
        if (pending == null && status == Status.OUT_OF_REACH && restock.get() && !state.missingMaterials().isEmpty()
                && walkTargets(player, gateScratch) == 0) {
            status = Status.MISSING_MATERIALS;
        }
        if (pending == null && status == Status.MISSING_MATERIALS && restock.get()
                && (indexNearbyChests.get() || SupplyPoint.INSTANCE.pos() != null)) {
            if (planChestRestock(player)) return;
        } else {
            restockChest = null;
            restockChestTicks = 0;
        }
        if (pending == null && autoWalk.get()) {
            pending = move(player);
            pendingAge = 0;
        } else if (pending != null && autoWalk.get()) {
            mover.holdForClick();
        }
        if (pending == null) {
            if (!aimedThisTick) RotationManager.INSTANCE.release(this);
            return;
        }
        status = pending.kind == Kind.HELPER ? Status.PILLARING : Status.WORKING;
        if (effectiveRotate() || pending.kind == Kind.HELPER) requestRotation(pending.plan.rotation());
    }

    /** Nothing to click from here: let the mover walk, climb or clear up, and report what it is doing. */
    private Pending move(LocalPlayer player) {
        if (status == Status.DONE) {
            if (removeHelpers.get() && mover.hasHelpers() && mover.tickClear(player, effectiveRange())) {
                status = Status.CLEARING;
            } else {
                mover.pause();
            }
            return null;
        }
        if (status == Status.RESTOCKING && restockChest != null) {
            BuildMover.HelperClick click = mover.tickBuild(player);
            if (click == null) return null;
            ItemStack stack = player.getInventory().getItem(click.hotbarSlot());
            return new Pending(click.plan(), click.expected(), stack.getItem(), click.hotbarSlot(), Kind.HELPER);
        }
        if (status != Status.OUT_OF_REACH) {
            mover.pause();
            return null;
        }
        BuildMover.HelperClick click = mover.tickBuild(player);
        status = switch (mover.mode()) {
            case WALKING -> Status.WALKING;
            case PILLARING -> Status.PILLARING;
            default -> Status.OUT_OF_REACH;
        };
        if (click == null) return null;
        ItemStack stack = player.getInventory().getItem(click.hotbarSlot());
        return new Pending(click.plan(), click.expected(), stack.getItem(), click.hotbarSlot(), Kind.HELPER);
    }

    private void requestRotation(Rotation rotation) {
        float min = rotationSpeedMin.get();
        float max = Math.max(min, rotationSpeedMax.get());
        if (donutRules()) {
            min = Math.min(min, DONUT_MAX_ROTATION_SPEED);
            max = Math.min(max, DONUT_MAX_ROTATION_SPEED);
        }
        effectiveSpeedMin.set(min);
        effectiveSpeedMax.set(max);
        RotationTarget target = new RotationTarget(rotation, null, List.of(smoothing), 2, 1.0f, true,
                MovementCorrection.SILENT, null);
        RotationManager.INSTANCE.setRotationTarget(target, ROTATION_PRIORITY, this);
    }

    private void post() {
        LocalPlayer player = mc.player;
        if (openChestPending && restockChest != null && restockPlan != null && canWork(player)) {
            openChestPending = false;
            BuildPlanner.Plan click = restockPlan;
            if (!player.isShiftKeyDown() && click.target().equals(restockChest)) {
                Rotation current = effectiveRotate() ? RotationManager.INSTANCE.getCurrentRotation() : null;
                BlockHitResult hit = effectiveRotate()
                        ? BuildPlanner.trace(player, current, player.blockInteractionRange() + 0.5)
                        : new BlockHitResult(click.hit(), click.face(), click.support(), false);
                if (BuildPlanner.matches(hit, click)
                        && BuildPlanner.legalClick(player, current, click.support(), click.face(), hit.getLocation())) {
                    InteractionResult res = useWithRotation(player, new BlockHitResult(hit.getLocation(), click.face(),
                            click.support(), false), RotationManager.INSTANCE.getServerRotation());
                    if (res != null && res.consumesAction()) {
                        player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).getInteractAnimation(), false);
                    }
                }
            }
            cooldown = Math.max(cooldown, 4);
            return;
        }
        if (pending == null || cooldown > 0 || !canWork(player)) return;
        int budget = effectiveRotate() ? 1 : effectiveBlocksPerTick();
        for (int i = 0; i < budget && pending != null && cooldown <= 0; i++) {
            if (pending.kind == Kind.ADJUST) {
                tryAdjust(player, pending);
                return;
            }
            if (!tryPlace(player, pending)) return;
            pending = effectiveRotate() || i + 1 >= budget ? null : choose(player);
        }
    }

    private boolean tryPlace(LocalPlayer player, Pending work) {
        BuildPlanner.Plan plan = work.plan;
        // The sneak goes out with this tick's input packet; the crouched eyes are what the plan aimed from.
        if (player.isUsingItem() || player.containerMenu != player.inventoryMenu) return false;
        if (plan.sneak() && !(player.isShiftKeyDown() && player.isCrouching())) {
            rotationWaitTicks++;
            return false;
        }
        BlockHitResult hit;
        Rotation facingRotation;
        if (effectiveRotate()) {
            Rotation current = RotationManager.INSTANCE.getCurrentRotation();
            Rotation server = RotationManager.INSTANCE.getServerRotation();
            double reach = effectiveRange() + 0.5;
            hit = BuildPlanner.trace(player, current, reach);
            if (!BuildPlanner.matches(hit, plan)) {
                rotationWaitTicks++;
                return false;
            }
            facingRotation = server;
            // The server picks the block's facing from the rotation it already has; that only has to look at
            // the face too when the facing matters. A stone block comes out the same whatever the old rotation.
            if (!BuildPlanner.matches(BuildPlanner.trace(player, server, reach), plan)
                    && !facingIndependent(player, work, plan, hit, server, current)) {
                rotationWaitTicks++;
                return false;
            }
        } else {
            hit = new BlockHitResult(plan.hit(), plan.face(), plan.support(), false);
            facingRotation = null;
        }
        if (!BuildPlanner.legalClick(player, effectiveRotate() ? RotationManager.INSTANCE.getCurrentRotation() : null,
                plan.support(), plan.face(), hit.getLocation())) {
            rotationWaitTicks++;
            return false;
        }

        ItemStack stack = player.getInventory().getItem(work.hotbarSlot);
        if (stack.isEmpty() || stack.getItem() != work.item) {
            pending = null;
            return false;
        }
        BlockState predicted = BuildPlanner.simulate(player, mc.level, stack, plan.target(), plan.support(),
                plan.face(), hit.getLocation(), facingRotation);
        if (predicted == null || predicted.getBlock() != work.expected.getBlock()) return false;
        if (work.kind == Kind.HELPER) return placeHelper(player, work, hit);
        boolean exact = BuildPlanner.exact(predicted, work.expected);
        // The ray reaches the planned face before the rotation settles on the planned point; clicking then puts
        // a stair or slab in the wrong half. Wait for the exact click, but not past half the plan timeout. A
        // piston or observer facing the wrong way is worse than a late one: it waits for the plan timeout.
        boolean lookSensitive = RedstoneRules.lookDecides(work.expected);
        if (exact && lookSensitive && facingRotation != null) {
            exact = robustFacing(player, stack, plan, hit, facingRotation, work.expected);
        }
        if (!exact && plan.exact() && (pendingAge < EXACT_WAIT_TICKS || lookSensitive)) {
            exactWaitTicks++;
            return false;
        }
        if (!exact && approximate.get() == ApproximateMode.SKIP) {
            retryAfter.put(plan.target().asLong(), ticks + RETRY_AFTER_TICKS);
            pending = null;
            return false;
        }

        selectSlot(work.hotbarSlot);
        InteractionResult result = useWithRotation(player, new BlockHitResult(hit.getLocation(), plan.face(),
                plan.support(), false), facingRotation);
        if (result != null && result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).getInteractAnimation(), false);
            placed++;
            lastPlaceTick = ticks;
            if (!exact) {
                approximatePlaced++;
                state.flag(plan.target(), work.expected, SchematicAttention.Reason.APPROXIMATE);
            }
            approximateSince.remove(plan.target().asLong());
            lookWanted.remove(plan.target().asLong());
            yawOnly.remove(plan.target().asLong());
            mover.onPlaced();
        }
        retryAfter.put(plan.target().asLong(), ticks + RECENT_PLACE_TICKS);
        cooldown = nextDelay();
        return true;
    }

    /**
     * The click with the client's own prediction made under the rotation the server will use. Predicting under
     * the camera's look put a piston or observer down facing the camera way until the server's correction came
     * back, and a correction lost to the prediction's block-change sequence left it that way for good.
     */
    private InteractionResult useWithRotation(LocalPlayer player, BlockHitResult hit, Rotation rotation) {
        if (rotation == null) return mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        try {
            player.setYRot(rotation.yaw());
            player.setXRot(rotation.pitch());
            return mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        } finally {
            player.setYRot(yaw);
            player.setXRot(pitch);
        }
    }

    /**
     * Which of the two rotations the server reads the facing from depends on packet order, and a look close to
     * the diagonal flips axis on the smallest rounding: a piston or observer only goes out once both rotations,
     * and both nudged a couple of degrees, give the wanted facing.
     */
    private boolean robustFacing(LocalPlayer player, ItemStack stack, BuildPlanner.Plan plan, BlockHitResult hit,
                                 Rotation server, BlockState expected) {
        Rotation current = RotationManager.INSTANCE.getCurrentRotation();
        Rotation[] checks = {
                current,
                new Rotation(server.yaw() + BuildPlanner.LOOK_MARGIN, server.pitch()),
                new Rotation(server.yaw() - BuildPlanner.LOOK_MARGIN, server.pitch()),
                new Rotation(server.yaw(), Math.min(90.0f, server.pitch() + BuildPlanner.LOOK_MARGIN)),
                new Rotation(server.yaw(), Math.max(-90.0f, server.pitch() - BuildPlanner.LOOK_MARGIN))
        };
        for (Rotation rotation : checks) {
            if (rotation == null) return false;
            BlockState result = BuildPlanner.simulate(player, mc.level, stack, plan.target(), plan.support(),
                    plan.face(), hit.getLocation(), rotation);
            if (result == null || !BuildPlanner.exact(result, expected)) return false;
        }
        return true;
    }

    private boolean facingIndependent(LocalPlayer player, Pending work, BuildPlanner.Plan plan, BlockHitResult hit,
                                      Rotation server, Rotation current) {
        ItemStack stack = player.getInventory().getItem(work.hotbarSlot);
        BlockState withServer = BuildPlanner.simulate(player, mc.level, stack, plan.target(), plan.support(),
                plan.face(), hit.getLocation(), server);
        BlockState withCurrent = BuildPlanner.simulate(player, mc.level, stack, plan.target(), plan.support(),
                plan.face(), hit.getLocation(), current);
        return withServer != null && withServer == withCurrent;
    }

    /** A support block under the feet mid-jump: no exactness rules, but it is remembered for clean-up. */
    private boolean placeHelper(LocalPlayer player, Pending work, BlockHitResult hit) {
        BuildPlanner.Plan plan = work.plan;
        selectSlot(work.hotbarSlot);
        InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit.getLocation(), plan.face(), plan.support(), false));
        if (result != null && result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).getInteractAnimation(), false);
            mover.onHelperPlaced(plan.target(), work.expected.getBlock());
        }
        cooldown = nextDelay();
        return true;
    }

    private void selectSlot(int hotbarSlot) {
        if (silentSwap.get()) {
            InventorySwap.INSTANCE.leaseHotbar(this, hotbarSlot, 2);
        } else {
            InventorySwap.INSTANCE.selectHotbar(hotbarSlot);
        }
    }

    private boolean stillValid(LocalPlayer player, Pending work) {
        if (work.kind == Kind.ADJUST) {
            BlockState current = mc.level.getBlockState(work.plan.target());
            double reach = effectiveRange();
            return StateAdjustments.clicksNeeded(current, work.expected) > 0
                    && player.getEyePosition().distanceToSqr(work.plan.hit()) <= reach * reach;
        }
        // A support click only makes sense while the feet are above the cell it fills.
        if (work.kind == Kind.HELPER && player.getY() < work.plan.target().getY() + 1.0) return false;
        BlockState current = mc.level.getBlockState(work.plan.target());
        if (!current.canBeReplaced()) return false;
        if (mc.level.getBlockState(work.plan.support()).canBeReplaced()) return false;
        ItemStack stack = player.getInventory().getItem(work.hotbarSlot);
        if (stack.isEmpty() || stack.getItem() != work.item) return false;
        double reach = effectiveRange();
        return player.getEyePosition().distanceToSqr(work.plan.hit()) <= reach * reach;
    }

    private Pending choose(LocalPlayer player) {
        SchematicDiff diff = state.diff(mc.level);
        if (diff == null) {
            status = Status.IDLE;
            return null;
        }
        int openBand = safety.openBand(diff);
        int layer = workingLayer(diff, openBand);
        if (layer < 0) return null;
        int high = strictLayers.get() ? layer : Math.max(layer, state.visibleHigh());

        int count = collectCandidates(player, diff, layer, high, openBand);
        if (count == 0) {
            status = missingThisTick ? Status.MISSING_MATERIALS : Status.OUT_OF_REACH;
            return null;
        }
        if (missingThisTick && pauseWhenMissing.get()) {
            status = Status.MISSING_MATERIALS;
            notifyMissing();
            return null;
        }
        sortCandidates(count);
        double reach = effectiveRange();
        boolean aim = effectiveRotate();
        int attempts = 0;
        for (int i = 0; i < count && attempts < MAX_PLAN_ATTEMPTS; i++) {
            int cell = candidateCells[i];
            BlockState expected = diff.expectedState(cell);
            Item item = SchematicMaterials.itemFor(expected);
            int hotbar = hotbarSlotFor(player, item);
            BlockPos target = diff.worldPos(cell, scratch).immutable();
            // A cell with nothing to click against anywhere around it is not a plan attempt: it waits for a
            // neighbour, and must not crowd the cells that can be placed out of the attempt budget.
            if (!BuildPlanner.hasSupport(mc.level, target)) continue;
            attempts++;
            // Plan before restocking: swapping in the item for a cell that cannot be clicked from here makes the
            // next candidate swap its own item back over it, every tick, and nothing gets placed.
            ItemStack stack = hotbar >= 0 ? player.getInventory().getItem(hotbar) : new ItemStack(item);
            BuildPlanner.Plan plan = BuildPlanner.plan(player, mc.level, target, expected, stack, reach, aim);
            if (plan == null || !acceptPlan(diff, plan, expected)) continue;
            if (hotbar < 0) {
                if (restock.get() && restockFromInventory(player, item)) {
                    status = Status.WORKING;
                    return null;
                }
                continue;
            }
            return new Pending(plan, expected, item, hotbar, Kind.PLACE);
        }
        status = Status.OUT_OF_REACH;
        return null;
    }

    /**
     * Whether a plan found for a candidate goes out now. An approximate piston or observer first gives the walker
     * a while to find a spot with the right look; a cell that would fire waits a few times for the circuit.
     */
    private boolean acceptPlan(SchematicDiff diff, BuildPlanner.Plan plan, BlockState expected) {
        BlockPos target = plan.target();
        long key = target.asLong();
        if (!plan.exact()) {
            Direction look = RedstoneRules.requiredLook(expected);
            Direction yaw = RedstoneRules.requiredYaw(expected);
            int since = approximateSince.computeIfAbsent(key, ignored -> ticks);
            if ((look != null || yaw != null) && autoWalk.get() && ticks - since < APPROXIMATE_PATIENCE_TICKS) {
                lookWanted.put(key, look != null ? look : yaw);
                if (look == null) yawOnly.add(key);
                return false;
            }
            // Out of patience: the walker stops looking for a better spot and the approximate click goes out.
            lookWanted.remove(key);
            yawOnly.remove(key);
            if (approximate.get() == ApproximateMode.SKIP) {
                state.flag(target, expected, SchematicAttention.Reason.APPROXIMATE);
                retryAfter.put(key, ticks + RETRY_AFTER_TICKS);
                return false;
            }
        }
        BuildSafety.Hazard hazard = safety.hazard(mc.level, diff, target, expected);
        if (hazard == BuildSafety.Hazard.NONE) return true;
        int defers = hazardDefers.merge(key, 1, Integer::sum);
        if (hazard == BuildSafety.Hazard.NEVER || defers <= HAZARD_DEFER_LIMIT) {
            if (hazard == BuildSafety.Hazard.NEVER) state.flag(target, expected, SchematicAttention.Reason.TNT_POWERED);
            retryAfter.put(key, ticks + RETRY_AFTER_TICKS);
            return false;
        }
        state.flag(target, expected, SchematicAttention.Reason.WOULD_TRIGGER);
        return true;
    }

    /**
     * Clicks on placed blocks that need a delay, mode, note, lever position or door set. Comes before new
     * placements so the click lands while the player still stands next to the block; a lever is only switched on
     * once nothing else is open, since that powers the build.
     */
    private Pending chooseAdjustment(LocalPlayer player) {
        SchematicDiff diff = state.diff(mc.level);
        if (diff == null) return null;
        int high = strictLayers.get() ? state.layer() : Math.max(state.layer(), state.visibleHigh());
        double reach = effectiveRange();
        Vec3 eyes = player.getEyePosition();
        double prefilter = (reach + 0.87) * (reach + 0.87);
        boolean allPlaced = diff.openTotal() == 0 || ticks % GATE_CHECK_INTERVAL == 0 && nothingElseToPlace(diff);
        for (int y = 0; y <= high && y < state.layerCount(); y++) {
            if (diff.wrong(y) == 0) continue;
            for (int cell = diff.layerStart(y), end = diff.layerEnd(y); cell < end; cell++) {
                if (diff.status(cell) != SchematicDiff.WRONG) continue;
                diff.worldPos(cell, scratch);
                if (Vec3.atCenterOf(scratch).distanceToSqr(eyes) > prefilter || retryAfter.containsKey(scratch.asLong())) continue;
                Pending work = adjustment(player, diff, cell, allPlaced, reach);
                if (work != null) return work;
            }
        }
        return null;
    }

    private Pending adjustment(LocalPlayer player, SchematicDiff diff, int cell, boolean allPlaced, double reach) {
        BlockState expected = diff.expectedState(cell);
        if (StateAdjustments.clickedElsewhere(expected)) return null;
        BlockState actual = mc.level.getBlockState(scratch);
        int clicks = StateAdjustments.clicksNeeded(actual, expected);
        if (clicks == StateAdjustments.IMPOSSIBLE) {
            if (actual.getBlock() == expected.getBlock() && !state.flagged(scratch)) {
                state.flag(scratch, expected, SchematicAttention.Reason.WRONG_STATE);
            }
            return null;
        }
        if (clicks == 0 || StateAdjustments.activates(expected) && !allPlaced) return null;
        long key = scratch.asLong();
        int spent = adjustClicks.getOrDefault(key, 0);
        if (spent >= StateAdjustments.MAX_CLICKS + ADJUST_SLACK_CLICKS) {
            state.flag(scratch, expected, SchematicAttention.Reason.ADJUST_FAILED);
            retryAfter.put(key, ticks + RETRY_AFTER_TICKS * 10);
            return null;
        }
        BuildPlanner.Plan plan = BuildPlanner.planClick(player, mc.level, scratch.immutable(), reach);
        if (plan == null) return null;
        return new Pending(plan, expected, null, -1, Kind.ADJUST);
    }

    /** One use on the placed block; the world is read again before the next one. */
    private void tryAdjust(LocalPlayer player, Pending work) {
        BuildPlanner.Plan plan = work.plan;
        if (player.isUsingItem() || player.containerMenu != player.inventoryMenu) return;
        if (player.isShiftKeyDown()) {
            rotationWaitTicks++;
            return;
        }
        BlockHitResult hit;
        if (effectiveRotate()) {
            hit = BuildPlanner.trace(player, RotationManager.INSTANCE.getCurrentRotation(), effectiveRange() + 0.5);
            if (hit == null || !hit.getBlockPos().equals(plan.target())) {
                rotationWaitTicks++;
                return;
            }
        } else {
            hit = new BlockHitResult(plan.hit(), plan.face(), plan.target(), false);
        }
        if (!BuildPlanner.legalClick(player, effectiveRotate() ? RotationManager.INSTANCE.getCurrentRotation() : null,
                plan.target(), hit.getDirection(), hit.getLocation())) {
            rotationWaitTicks++;
            return;
        }
        InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        if (result != null && result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).getInteractAnimation(), false);
            adjusted++;
            lastPlaceTick = ticks;
        }
        adjustClicks.merge(plan.target().asLong(), 1, Integer::sum);
        cooldown = Math.max(nextDelay(), ADJUST_CLICK_DELAY);
        pending = null;
    }

    /**
     * The selected layer, advanced past layers whose open cells all wait (for power to open or for a block above)
     * when enabled. -1 once the whole build is done.
     */
    private int workingLayer(SchematicDiff diff, int openBand) {
        int layer = state.layer();
        int top = state.layerCount() - 1;
        // A wrong block of the layer counts as done for the diff, but once terraforming mines it the cell is open
        // again: moving up first would strand it below the working layer.
        while (safety.layerSettled(mc.level, diff, layer, openBand) && !(terraform.get() && terraformRemaining())) {
            if (layer >= top) {
                // Power and cells hanging on higher layers were left behind on the way up; come back for them.
                if (BuildSafety.backlogOpen(diff, top + 1)) return top;
                status = Status.DONE;
                if (completedLayerNotified != Integer.MAX_VALUE) {
                    completedLayerNotified = Integer.MAX_VALUE;
                    Notifier.success(I18n.get("module.schematicbuilder.notify.done"));
                    flagUnadjusted(diff);
                }
                return -1;
            }
            if (!autoAdvanceLayer.get()) {
                status = Status.DONE;
                if (completedLayerNotified != layer) {
                    completedLayerNotified = layer;
                    Notifier.info(I18n.get("module.schematicbuilder.notify.layer_done", layer + 1));
                }
                return -1;
            }
            layer++;
            state.setLayer(layer);
        }
        return layer;
    }

    /**
     * Climbing before a layer protects the step onto it from being walled off by the layer's own blocks; a layer
     * left with only torches, dust or levers to place walls nothing off and is built from where the player is.
     */
    private static boolean hasOpenSupport(SchematicDiff diff, int y) {
        for (int cell = diff.layerStart(y), end = diff.layerEnd(y); cell < end; cell++) {
            if (BuildSafety.open(diff.status(cell)) && diff.order().order(cell) < RedstoneRules.ATTACHED) return true;
        }
        return false;
    }

    /** Whether every open cell is one the builder already gave up on (listed in the menu). */
    private boolean nothingElseToPlace(SchematicDiff diff) {
        if (diff.openTotal() == 0) return true;
        for (int cell = 0, count = diff.cellCount(); cell < count; cell++) {
            if (BuildSafety.open(diff.status(cell)) && !state.flagged(diff.worldPos(cell, scratch))) return false;
        }
        return true;
    }

    /** Blocks still waiting for a delay, mode, note or lever click when the build is done were out of reach. */
    private void flagUnadjusted(SchematicDiff diff) {
        for (int cell = 0, count = diff.cellCount(); cell < count; cell++) {
            if (diff.status(cell) != SchematicDiff.WRONG) continue;
            BlockState expected = diff.expectedState(cell);
            if (StateAdjustments.clickedElsewhere(expected)) continue;
            diff.worldPos(cell, scratch);
            if (StateAdjustments.clicksNeeded(mc.level.getBlockState(scratch), expected) > 0) {
                state.flag(scratch, expected, SchematicAttention.Reason.ADJUST_FAILED);
            }
        }
    }

    private int collectCandidates(LocalPlayer player, SchematicDiff diff, int low, int high, int openBand) {
        missingThisTick = false;
        if (autoWalk.get() && player.onGround() && hasOpenSupport(diff, low)
                && mover.climbBeforePlacing(player, state.placement().worldY(low))) return 0;
        Vec3 eyes = player.getEyePosition();
        Rotation facing = RotationManager.INSTANCE.getCurrentRotation();
        if (facing == null) facing = new Rotation(player.getYRot(), player.getXRot());
        // Cell centres up to half a block diagonal beyond reach can still expose a reachable face.
        double reach = effectiveRange() + 0.87;
        double reachSq = reach * reach;
        int count = 0;
        // Layers below the working one only hold cells left behind on purpose: power and blocks hanging on a
        // higher layer.
        for (int y = 0; y <= high; y++) {
            if (y < low && BuildSafety.openCount(diff, y) == 0) continue;
            for (int cell = diff.layerStart(y), end = diff.layerEnd(y); cell < end; cell++) {
                byte status = diff.status(cell);
                if (status == SchematicDiff.CORRECT || status == SchematicDiff.WRONG) continue;
                if (y < low && !BuildSafety.deferrable(diff, cell)) continue;
                diff.worldPos(cell, scratch);
                double dx = scratch.getX() + 0.5 - eyes.x, dy = scratch.getY() + 0.5 - eyes.y, dz = scratch.getZ() + 0.5 - eyes.z;
                double distSq = dx * dx + dy * dy + dz * dz;
                if (distSq > reachSq || retryAfter.containsKey(scratch.asLong())) continue;
                if (diff.refresh(mc.level, cell) != SchematicDiff.MISSING) continue;
                if (!safety.eligible(mc.level, diff, cell, openBand)) continue;
                diff.worldPos(cell, scratch);
                BlockState expected = diff.expectedState(cell);
                Item item = SchematicMaterials.itemFor(expected);
                if (item == Items.AIR || SchematicMaterials.itemsPerCell(expected) == 0) continue;
                if (SchematicMaterials.countInInventory(player, item) == 0) {
                    missingThisTick = true;
                    continue;
                }
                if (count < MAX_CANDIDATES) {
                    candidateCells[count] = cell;
                    // Lower layers first so blocks can lean on what is below them, then farthest first: a block
                    // can only hide the faces of cells behind it, so placing near cells first walls off the far
                    // ones (the corners of a ring around the player become unclickable). Within one block of
                    // distance the cell closest to the current aim goes first, so the view sweeps instead of
                    // jumping back and forth.
                    double distance = Math.sqrt(distSq);
                    double turn = facing.angleTo(Rotation.lookingAt(Vec3.atCenterOf(scratch), eyes));
                    candidateDistance[count] = BuildSafety.major(diff.order().order(cell), y) * 1000.0
                            + Math.floor(reach - distance) * 10.0 + Math.min(turn, 180.0) / 18.01;
                    count++;
                }
            }
        }
        return count;
    }

    private void sortCandidates(int count) {
        for (int i = 1; i < count; i++) {
            int cell = candidateCells[i];
            double distance = candidateDistance[i];
            int j = i - 1;
            while (j >= 0 && candidateDistance[j] > distance) {
                candidateCells[j + 1] = candidateCells[j];
                candidateDistance[j + 1] = candidateDistance[j];
                j--;
            }
            candidateCells[j + 1] = cell;
            candidateDistance[j + 1] = distance;
        }
    }

    private static int hotbarSlotFor(LocalPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        int selected = inventory.getSelectedSlot();
        if (inventory.getItem(selected).getItem() == item) return selected;
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i).getItem() == item) return i;
        }
        return -1;
    }

    /** Swaps a stack from the main inventory into the restock slot; placement resumes on a later tick. */
    private boolean restockFromInventory(LocalPlayer player, Item item) {
        if (player.containerMenu != player.inventoryMenu) return false;
        Inventory inventory = player.getInventory();
        for (int i = 9; i < Inventory.INVENTORY_SIZE; i++) {
            if (inventory.getItem(i).getItem() != item) continue;
            int hotbar = restockSlot.get() - 1;
            if (InventorySwap.INSTANCE.clickSwap(InventorySwap.mapInventoryToScreenSlot(i), hotbar)) {
                cooldown = Math.max(cooldown, 2);
                return true;
            }
            return false;
        }
        return false;
    }

    private void updateShortfall(LocalPlayer player) {
        SchematicDiff diff = state.currentDiff();
        Schematic schematic = state.schematic();
        if (diff == null || schematic == null) {
            state.setMissingMaterials(List.of());
            return;
        }
        int layer = state.layer();
        int high = strictLayers.get() ? layer : Math.max(layer, state.visibleHigh());
        Map<Item, int[]> needed = new IdentityHashMap<>();
        for (int y = layer; y <= high; y++) {
            for (int cell = diff.layerStart(y), end = diff.layerEnd(y); cell < end; cell++) {
                byte status = diff.status(cell);
                if (status == SchematicDiff.CORRECT || status == SchematicDiff.WRONG) continue;
                BlockState expected = diff.expectedState(cell);
                Item item = SchematicMaterials.itemFor(expected);
                int cost = SchematicMaterials.itemsPerCell(expected);
                if (item == Items.AIR || cost == 0) continue;
                needed.computeIfAbsent(item, ignored -> new int[1])[0] += cost;
            }
        }
        List<SchematicMaterials.Entry> shortfall = new ArrayList<>();
        for (Map.Entry<Item, int[]> entry : needed.entrySet()) {
            int missing = entry.getValue()[0] - SchematicMaterials.countInInventory(player, entry.getKey());
            if (missing > 0) shortfall.add(new SchematicMaterials.Entry(entry.getKey(), missing));
        }
        shortfall.sort((a, b) -> Integer.compare(b.count(), a.count()));
        state.setMissingMaterials(shortfall);
    }

    private void notifyMissing() {
        if (ticks - lastMissingNotifyTick < MISSING_NOTIFY_INTERVAL) return;
        lastMissingNotifyTick = ticks;
        List<SchematicMaterials.Entry> missing = state.missingMaterials();
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < Math.min(3, missing.size()); i++) {
            if (i > 0) list.append(", ");
            SchematicMaterials.Entry entry = missing.get(i);
            list.append(entry.count()).append("x ").append(new ItemStack(entry.item()).getHoverName().getString());
        }
        if (missing.size() > 3) list.append(", ...");
        Notifier.warning(I18n.get("module.schematicbuilder.notify.missing", list.toString()));
    }

    /**
     * Whether the world block at {@code pos} is in the way of the schematic block {@code expected} (null for an air
     * cell) and terraforming may mine it: a different block, never a fluid, bedrock or the support under the feet.
     */
    private boolean minable(BlockPos pos, BlockState expected) {
        if (mc.level == null || mc.player == null || !mc.level.hasChunkAt(pos)) return false;
        BlockState actual = mc.level.getBlockState(pos);
        if (actual.isAir() || actual.getBlock() instanceof LiquidBlock) return false;
        if (expected != null && actual.getBlock() == expected.getBlock()) return false;
        if (actual.getDestroySpeed(mc.level, pos) < 0.0f) return false;
        if (pos.equals(mc.player.blockPosition().below()) || mover.isHelper(pos)) return false;
        return true;
    }

    /** Whether any wrong block of the working layers is left for terraforming to mine. */
    private boolean terraformRemaining() {
        SchematicDiff diff = state.currentDiff();
        if (diff == null || mc.level == null) return false;
        int high = strictLayers.get() ? state.layer() : Math.max(state.layer(), state.visibleHigh());
        for (int y = 0; y <= high && y < state.layerCount(); y++) {
            if (diff.wrong(y) == 0) continue;
            for (int cell = diff.layerStart(y), end = diff.layerEnd(y); cell < end; cell++) {
                if (diff.status(cell) != SchematicDiff.WRONG) continue;
                diff.worldPos(cell, scratch);
                if (!retryAfter.containsKey(scratch.asLong()) && minable(scratch, diff.expectedState(cell))) return true;
            }
        }
        return false;
    }

    /**
     * Mines a reachable block that stands where the schematic wants something else (and, with {@code terraform_air},
     * anything in a schematic air cell). Returns whether a block is being mined this tick.
     */
    private boolean terraform(LocalPlayer player) {
        SchematicDiff diff = state.diff(mc.level);
        Schematic schematic = state.schematic();
        if (diff == null || schematic == null) return false;
        double reach = effectiveRange();
        if (terraformTarget != null) {
            if (++terraformTicks <= TERRAFORM_TIMEOUT_TICKS && stillTerraformable(diff, schematic, terraformTarget)
                    && mover.mineCell(player, terraformTarget, reach)) {
                return true;
            }
            if (terraformTicks > TERRAFORM_TIMEOUT_TICKS) {
                retryAfter.put(terraformTarget.asLong(), ticks + RETRY_AFTER_TICKS);
                mover.stopMiningCell();
            }
            terraformTarget = null;
        }
        Vec3 eyes = player.getEyePosition();
        double limit = (reach + 0.87) * (reach + 0.87);
        int high = strictLayers.get() ? state.layer() : Math.max(state.layer(), state.visibleHigh());
        List<BlockPos> found = new ArrayList<>();
        for (int y = 0; y <= high && y < state.layerCount(); y++) {
            if (diff.wrong(y) == 0) continue;
            for (int cell = diff.layerStart(y), end = diff.layerEnd(y); cell < end; cell++) {
                if (diff.status(cell) != SchematicDiff.WRONG) continue;
                diff.worldPos(cell, scratch);
                if (Vec3.atCenterOf(scratch).distanceToSqr(eyes) > limit || retryAfter.containsKey(scratch.asLong())) continue;
                if (minable(scratch, diff.expectedState(cell))) found.add(scratch.immutable());
            }
        }
        if (terraformAir.get()) {
            int r = (int) Math.ceil(reach + 0.87);
            int ex = Mth.floor(eyes.x), ey = Mth.floor(eyes.y), ez = Mth.floor(eyes.z);
            for (int x = ex - r; x <= ex + r; x++) {
                for (int z = ez - r; z <= ez + r; z++) {
                    int lx = state.placement().localX(x, z), lz = state.placement().localZ(x, z);
                    for (int y = ey - r; y <= ey + r; y++) {
                        int ly = state.placement().localY(y);
                        if (ly > high || !schematic.contains(lx, ly, lz) || schematic.paletteIndex(lx, ly, lz) != 0) continue;
                        scratch.set(x, y, z);
                        if (Vec3.atCenterOf(scratch).distanceToSqr(eyes) > limit || retryAfter.containsKey(scratch.asLong())) continue;
                        if (minable(scratch, null)) found.add(scratch.immutable());
                    }
                }
            }
        }
        // Top first so gravity blocks and the ground under later targets stay put; nearest first within a level.
        found.sort((a, b) -> a.getY() != b.getY() ? Integer.compare(b.getY(), a.getY())
                : Double.compare(Vec3.atCenterOf(a).distanceToSqr(eyes), Vec3.atCenterOf(b).distanceToSqr(eyes)));
        for (BlockPos pos : found) {
            if (mover.mineCell(player, pos, reach)) {
                terraformTarget = pos;
                terraformTicks = 0;
                return true;
            }
        }
        return false;
    }

    private boolean stillTerraformable(SchematicDiff diff, Schematic schematic, BlockPos pos) {
        SchematicPlacement placement = state.placement();
        int lx = placement.localX(pos.getX(), pos.getZ()), ly = placement.localY(pos.getY()), lz = placement.localZ(pos.getX(), pos.getZ());
        if (!schematic.contains(lx, ly, lz)) return false;
        int index = schematic.paletteIndex(lx, ly, lz);
        if (index == 0) return terraformAir.get() && minable(pos, null);
        int cell = diff.cellOf(schematic.index(lx, ly, lz));
        return cell >= 0 && minable(pos, diff.expectedState(cell));
    }

    /**
     * Missing cells of the working layers that the inventory has material for, as packed world positions; the
     * walker plans where to stand from these.
     */
    private int walkTargets(LocalPlayer player, long[] out) {
        if (status == Status.RESTOCKING && restockChest != null && out.length > 0) {
            out[0] = WalkTerrain.pack(restockChest.getX(), restockChest.getY(), restockChest.getZ());
            return 1;
        }
        SchematicDiff diff = state.currentDiff();
        Schematic schematic = state.schematic();
        if (diff == null || schematic == null || mc.level == null) return 0;
        footprint = state.placement().worldBounds(schematic.sizeX(), schematic.sizeY(), schematic.sizeZ());
        int layer = state.layer();
        int high = strictLayers.get() ? layer : Math.max(layer, state.visibleHigh());
        int openBand = safety.openBand(diff);
        int count = 0;
        for (int y = 0; y <= high && count < out.length; y++) {
            if (y < layer && BuildSafety.openCount(diff, y) == 0) continue;
            for (int cell = diff.layerStart(y), end = diff.layerEnd(y); cell < end && count < out.length; cell++) {
                byte status = diff.status(cell);
                if (status == SchematicDiff.CORRECT) continue;
                if (y < layer && !BuildSafety.deferrable(diff, cell) && status != SchematicDiff.WRONG) continue;
                if (status == SchematicDiff.WRONG || diff.refresh(mc.level, cell) == SchematicDiff.WRONG) {
                    if (!terraform.get() || retryAfter.containsKey(diff.worldPos(cell, scratch).asLong())) continue;
                    if (!minable(scratch, diff.expectedState(cell))) continue;
                    out[count++] = WalkTerrain.pack(scratch.getX(), scratch.getY(), scratch.getZ());
                    continue;
                }
                if (diff.refresh(mc.level, cell) != SchematicDiff.MISSING) continue;
                if (!safety.eligible(mc.level, diff, cell, openBand)) continue;
                BlockState expected = diff.expectedState(cell);
                Item item = SchematicMaterials.itemFor(expected);
                if (item == Items.AIR || SchematicMaterials.itemsPerCell(expected) == 0) continue;
                if (SchematicMaterials.countInInventory(player, item) == 0) continue;
                diff.worldPos(cell, scratch);
                if (retryAfter.containsKey(scratch.asLong())) continue;
                out[count++] = WalkTerrain.pack(scratch.getX(), scratch.getY(), scratch.getZ());
            }
        }
        return count;
    }

    private boolean chestRayOnFace(LocalPlayer player, BuildPlanner.Plan click) {
        Rotation current = RotationManager.INSTANCE.getCurrentRotation();
        BlockHitResult hit = BuildPlanner.trace(player, current, player.blockInteractionRange() + 0.5);
        return BuildPlanner.matches(hit, click)
                && BuildPlanner.legalClick(player, current, click.support(), click.face(), hit.getLocation());
    }

    private boolean planChestRestock(LocalPlayer player) {
        if (state.missingMaterials().isEmpty()) updateShortfall(player);
        Set<Item> needed = new HashSet<>();
        for (SchematicMaterials.Entry entry : state.missingMaterials()) {
            needed.add(entry.item());
        }
        if (needed.isEmpty()) return false;
        BlockPos target = chestIndex.findTarget(mc.level, player, needed, indexNearbyChests.get(),
                chestScanRange.get(), ticks);
        if (target == null) {
            restockChest = null;
            return false;
        }
        if (!target.equals(restockChest)) {
            restockChest = target;
            restockChestTicks = 0;
        }
        status = Status.RESTOCKING;
        // A face of the chest the eyes really see, aimed at a point the ray lands on: never a fixed top face.
        BuildPlanner.Plan click = BuildPlanner.planClick(player, mc.level, target,
                Math.min(effectiveRange(), player.blockInteractionRange()));
        restockPlan = click;
        if (click != null) {
            mover.holdForClick();
            if (effectiveRotate()) {
                aimedThisTick = true;
                requestRotation(click.rotation());
            }
            restockChestTicks++;
            // Only once the camera's own ray lands on the planned face of the chest.
            if (!effectiveRotate() || chestRayOnFace(player, click)) openChestPending = true;
            if (restockChestTicks > 25) {
                chestIndex.markCooldown(mc.level, target, ticks + ChestIndex.retryCooldownTicks());
                restockChest = null;
                restockPlan = null;
                openChestPending = false;
            }
            return true;
        }
        if (!autoWalk.get()) return false;
        pending = move(player);
        pendingAge = 0;
        if (pending != null) {
            status = Status.PILLARING;
            requestRotation(pending.plan.rotation());
        } else if (!aimedThisTick) {
            RotationManager.INSTANCE.release(this);
        }
        return true;
    }

    private void tickOpenRestockChest(LocalPlayer player) {
        status = Status.RESTOCKING;
        mover.pause();
        RotationManager.INSTANCE.release(this);
        containerOpenTicks++;
        if (containerClickCooldown > 0) containerClickCooldown--;
        // Grim/Vulcan inventory checks: the first click waits out the open, later clicks keep the delay.
        if (!ContainerPacing.ready(containerOpenTicks, containerClickCooldown)) return;
        AbstractContainerMenu menu = player.containerMenu;
        int containerSlots = menu.slots.size() - 36;
        if (containerSlots <= 0) {
            player.closeContainer();
            restockChest = null;
            return;
        }
        chestIndex.recordOpenContainer(mc.level, player, restockChest);
        updateShortfall(player);
        Set<Item> needed = new HashSet<>();
        for (SchematicMaterials.Entry entry : state.missingMaterials()) {
            needed.add(entry.item());
        }
        for (int i = 0; i < containerSlots; i++) {
            Slot slot = menu.slots.get(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty() || !needed.contains(stack.getItem())) continue;
            if (!hasRoomFor(player.getInventory(), stack)) break;
            mc.gameMode.handleContainerInput(menu.containerId, i, 0, ContainerInput.QUICK_MOVE, player);
            containerClickCooldown = ContainerPacing.spacing(delay.get(), donutRules());
            return;
        }
        chestIndex.recordOpenContainer(mc.level, player, restockChest);
        chestIndex.markCooldown(mc.level, restockChest, ticks + ChestIndex.retryCooldownTicks());
        player.closeContainer();
        restockChest = null;
        restockChestTicks = 0;
        updateShortfall(player);
    }

    /**
     * Vulcan BadPackets K: consecutive {@code START_SNEAKING} packets closer than ~90 ms flag. A sneak is held
     * for at least {@link #MIN_HOLD_TICKS} ticks and a new one starts at least {@link #MIN_START_GAP_TICKS}
     * ticks after the previous start.
     */
    static final class SneakPacing {
        static final int MIN_HOLD_TICKS = 2;
        static final int MIN_START_GAP_TICKS = 3;
        private int startTick = Integer.MIN_VALUE / 2;
        private boolean sneaking;

        /** The sneak state to send for {@code tick} when the builder {@code wants} to sneak. */
        boolean apply(boolean wants, int tick) {
            if (sneaking) {
                if (!wants && tick - startTick >= MIN_HOLD_TICKS) sneaking = false;
            } else if (wants && tick - startTick >= MIN_START_GAP_TICKS) {
                sneaking = true;
                startTick = tick;
            }
            return sneaking;
        }

        boolean holding() {
            return sneaking;
        }

        void reset() {
            sneaking = false;
            startTick = Integer.MIN_VALUE / 2;
        }
    }

    /** Container click cadence: the first click waits out the open, later clicks keep the configured delay. */
    static final class ContainerPacing {
        static final int OPEN_WAIT_TICKS = 2;
        static final int DONUT_MIN_SPACING = 2;

        private ContainerPacing() {
        }

        /** {@code openTicks}: ticks since the menu was seen (1 on the first); {@code cooldown}: ticks left to wait. */
        static boolean ready(int openTicks, int cooldown) {
            return openTicks > OPEN_WAIT_TICKS && cooldown <= 0;
        }

        static int spacing(int delay, boolean donutRules) {
            return donutRules ? Math.max(DONUT_MIN_SPACING, delay) : Math.max(1, delay);
        }
    }

    private static boolean hasRoomFor(Inventory inv, ItemStack incoming) {
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack cur = inv.getItem(i);
            if (cur.isEmpty()) return true;
            if (ItemStack.isSameItemSameComponents(cur, incoming) && cur.getCount() < cur.getMaxStackSize()) return true;
        }
        return false;
    }

    private final class MoverHost implements BuildMover.Host {
        @Override
        public int walkTargets(LocalPlayer player, long[] out) {
            return SchematicBuilder.this.walkTargets(player, out);
        }

        @Override
        public double reach() {
            return effectiveRange();
        }

        @Override
        public boolean inFootprint(long cell) {
            int[] b = footprint;
            if (b == null) return false;
            int x = WalkTerrain.unpackX(cell), y = WalkTerrain.unpackY(cell), z = WalkTerrain.unpackZ(cell);
            return x >= b[0] && y >= b[1] && z >= b[2] && x <= b[3] && y <= b[4] && z <= b[5];
        }

        @Override
        public void aim(Rotation rotation) {
            aimedThisTick = true;
            requestRotation(rotation);
        }

        @Override
        public int stuckTimeout() {
            return stuckTimeout.get();
        }

        @Override
        public boolean pillarEnabled() {
            return autoJumpPillar.get();
        }

        @Override
        public Set<String> helperIds() {
            return helperBlocks.get();
        }

        @Override
        public boolean canSpare(Block block) {
            Item item = block.asItem();
            SchematicDiff diff = state.currentDiff();
            if (diff == null || mc.player == null) return true;
            int needed = 0;
            for (int cell = 0, count = diff.cellCount(); cell < count; cell++) {
                if (diff.status(cell) == SchematicDiff.CORRECT) continue;
                BlockState expected = diff.expectedState(cell);
                if (SchematicMaterials.itemFor(expected) == item) needed += SchematicMaterials.itemsPerCell(expected);
            }
            return SchematicMaterials.countInInventory(mc.player, item) > needed;
        }

        @Override
        public void releaseHotbar() {
            InventorySwap.INSTANCE.releaseHotbar(SchematicBuilder.this);
        }

        @Override
        public boolean lookAllows(long target, double eyeX, double eyeY, double eyeZ) {
            if (lookWanted.isEmpty()) return true;
            long key = BlockPos.asLong(WalkTerrain.unpackX(target), WalkTerrain.unpackY(target), WalkTerrain.unpackZ(target));
            Direction look = lookWanted.get(key);
            if (look == null) return true;
            double dx = WalkTerrain.unpackX(target) + 0.5 - eyeX;
            double dy = WalkTerrain.unpackY(target) + 0.5 - eyeY;
            double dz = WalkTerrain.unpackZ(target) + 0.5 - eyeZ;
            // Only the yaw decides a repeater: the height of the eyes does not matter.
            return dominantLook(dx, yawOnly.contains(key) ? 0.0 : dy, dz, look);
        }

        @Override
        public boolean bridgeEnabled() {
            return autoJumpPillar.get();
        }

        @Override
        public int bridgeLimit() {
            return 8;
        }
    }

    /**
     * Whether the look from the eyes to a cell runs along {@code look} clearly enough that clicks across the
     * cell's faces (up to half a block off its centre) keep that nearest axis.
     */
    static boolean dominantLook(double dx, double dy, double dz, Direction look) {
        double along = dx * look.getStepX() + dy * look.getStepY() + dz * look.getStepZ();
        if (along <= 0.0) return false;
        double ax = Math.abs(dx), ay = Math.abs(dy), az = Math.abs(dz);
        double others = switch (look.getAxis()) {
            case X -> Math.max(ay, az);
            case Y -> Math.max(ax, az);
            case Z -> Math.max(ax, ay);
        };
        return along > others + 0.5;
    }

    private enum Kind {
        /** Places a schematic block. */
        PLACE,
        /** Places a support block under the feet mid pillar jump. */
        HELPER,
        /** Uses a placed block to step its delay, mode, note or position. */
        ADJUST
    }

    private record Pending(BuildPlanner.Plan plan, BlockState expected, Item item, int hotbarSlot, Kind kind) {
    }
}
