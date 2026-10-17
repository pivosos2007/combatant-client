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
import combatant.client.util.anticheat.AntiCheatPreset;
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
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.lang.ref.WeakReference;
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
    private static final int RECENT_PLACE_TICKS = 10;
    /** Plans for one cell that timed out while waiting for an exact click, before the cell is placed approximate. */
    private static final int EXACT_GIVE_UP_PLANS = 6;
    private static final int MAX_PLAN_ATTEMPTS = 12;
    private static final int MAX_CANDIDATES = 96;
    private static final int SHORTFALL_INTERVAL = 20;
    private static final int MISSING_NOTIFY_INTERVAL = 200;
    /** How long a block that only comes out facing wrong from here (piston, observer, stair, furnace) waits for the walker to find a better spot. */
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
        DONE,
        /** Nothing left the builder can do, but some cells are wrong or missing (listed under Problems). */
        DONE_INEXACT
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
    private final BooleanValue autoAdvanceLayer = bool("auto_advance_layer", true);
    private final BooleanValue strictLayers = bool("strict_layers", true);
    private final EnumValue<ApproximateMode> approximate = enumMode("approximate_states", ApproximateMode.PLACE);
    private final BooleanValue stopOnDamage = bool("stop_on_damage", false);
    private final NumberValue<Float> pauseHealth = num("pause_health", 6.0f, 0.0f, 20.0f);
    private final BooleanValue autoWalk = bool("auto_walk", true);
    private final NumberValue<Integer> stuckTimeout = visibleWhen(num("stuck_timeout", 100, 40, 400), autoWalk::get);
    private final BooleanValue autoJumpPillar = visibleWhen(bool("auto_jump_pillar", true), autoWalk::get);
    private final ItemIdSetValue helperBlocks =
            visibleWhen(itemList("helper_blocks", DEFAULT_HELPER_BLOCKS), () -> autoWalk.get() && autoJumpPillar.get());
    private final BooleanValue removeHelpers =
            visibleWhen(bool("remove_helpers", true), () -> autoWalk.get() && autoJumpPillar.get());
    private final BooleanValue donutSmpSafe = bool("donutsmp_safe", true);
    private final EnumValue<AntiCheatPreset> anticheat = enumMode("anticheat_mode", AntiCheatPreset.CUSTOM);

    private final Minecraft mc = Minecraft.getInstance();
    private final SchematicState state = SchematicState.INSTANCE;
    private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
    private final NumberValue<Float> effectiveSpeedMin = new NumberValue<>("effective_speed_min", 30.0f, 1.0f, 180.0f);
    private final NumberValue<Float> effectiveSpeedMax = new NumberValue<>("effective_speed_max", 60.0f, 1.0f, 180.0f);
    private final LinearAngleSmooth smoothing =
            new LinearAngleSmooth(effectiveSpeedMin, effectiveSpeedMax, effectiveSpeedMin, effectiveSpeedMax);
    private final Map<Long, Integer> retryAfter = new HashMap<>();
    private final Map<Long, Integer> exactGiveUps = new HashMap<>();
    private final int[] candidateCells = new int[MAX_CANDIDATES];
    private final double[] candidateDistance = new double[MAX_CANDIDATES];
    private final BuildMover mover = new BuildMover(mc, new MoverHost());
    private final BuildSafety safety = new BuildSafety();
    /** Facing-sensitive targets with only an approximate click from here: when that was first seen. */
    private final Map<Long, Integer> approximateSince = new HashMap<>();
    /** The look direction such a target needs; the walker only counts it from spots that give it. */
    private final Map<Long, Direction> lookWanted = new HashMap<>();
    /** Of those, the ones where only the yaw counts (repeaters, comparators, floor levers). */
    private final Set<Long> yawOnly = new HashSet<>();
    /** Cells whose 16-step rotation needs this exact yaw (signs, banners, heads standing on the floor). */
    private final Map<Long, Float> yawWanted = new HashMap<>();
    private final Map<Long, Integer> hazardDefers = new HashMap<>();
    private final Map<Long, Integer> adjustClicks = new HashMap<>();
    private final long[] gateScratch = new long[1];
    private int lastPlaceTick;
    private int adjusted;
    private int[] footprint;
    private boolean aimedThisTick;
    private int rotationWaitTicks;
    private int exactWaitTicks;


    private Pending pending;
    private int pendingAge;
    private int cooldown;
    private final RestockGate restockGate = new RestockGate();
    private int ticks;
    private int placed;
    private int approximatePlaced;
    private float lastHealth = -1.0f;
    // Weak: the module outlives every world, and a strong reference kept the old level alive after disconnect.
    private WeakReference<ClientLevel> lastLevel = new WeakReference<>(null);
    private Status status = Status.IDLE;
    private int lastShortfallTick = Integer.MIN_VALUE;
    private int lastMissingNotifyTick = Integer.MIN_VALUE;
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
        exactGiveUps.clear();
        lastHealth = -1.0f;
        lastLevel = new WeakReference<>(mc.level);
        completedLayerNotified = -1;
        lastShortfallTick = Integer.MIN_VALUE;
        mover.clearAll();
        footprint = null;
        rotationWaitTicks = 0;
        exactWaitTicks = 0;
        safety.reset();
        approximateSince.clear();
        lookWanted.clear();
        yawOnly.clear();
        yawWanted.clear();
        hazardDefers.clear();
        adjustClicks.clear();
        lastPlaceTick = ticks;
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
        double value = anticheat.get().limit((double) range.get(), AntiCheatPreset.GRIM_BLOCK_RANGE, AntiCheatPreset.VANILLA_BLOCK_RANGE);
        return donutRules() ? Math.min(value, DONUT_RANGE) : value;
    }

    private int nextDelay() {
        int value = delay.get();
        if (!donutRules()) return value;
        // A fixed cadence is the easiest pattern to spot; jitter keeps it at roughly a human pace.
        return Math.max(value, DONUT_MIN_DELAY) + ThreadLocalRandom.current().nextInt(DONUT_MAX_JITTER + 1);
    }

    /**
     * Several useItemOn packets in one tick, none matching the server-side rotation, is exactly what place-rate and
     * rotation checks flag; only a server declared to have no anticheat gets more than one click per tick.
     */
    private int effectiveBlocksPerTick() {
        if (donutRules() || anticheat.get() != AntiCheatPreset.VANILLA) return 1;
        return blocksPerTick.get();
    }

    private boolean effectiveRotate() {
        return rotate.get() || donutRules() || anticheat.get() == AntiCheatPreset.GRIM;
    }

    @Override
    public void onTick() {
        LocalPlayer player = mc.player;
        if (!isEnabled() || player == null || mc.level == null) return;
        ticks++;
        if (mc.level != lastLevel.get()) {
            reset();
            lastLevel = new WeakReference<>(mc.level);
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
        // A placed sign opens the server's text editor; closing it sends the (unchanged) text and the build goes on.
        if (ClientScreen.current(mc) instanceof AbstractSignEditScreen signEditor) signEditor.onClose();
        if (ClientScreen.current() != null || player.isUsingItem()
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
        Pending work = pending;
        if (work == null) return;
        // Placing against a lever, repeater, hopper or door goes out sneaking so it does not toggle or open;
        // clicking a block to set its delay or note must not, or vanilla places the held block instead.
        if (work.kind == Kind.PLACE && work.plan.sneak()) {
            event.setSneak(true);
        } else if (work.kind == Kind.ADJUST) {
            event.setSneak(false);
        }
    }

    private void pre() {
        LocalPlayer player = mc.player;
        aimedThisTick = false;
        if (!canWork(player)) {
            pending = null;
            restockGate.reset();
            mover.pause();
            RotationManager.INSTANCE.release(this);
            return;
        }
        restockGate.observe(movingOrSneaking(player));
        if (cooldown > 0) cooldown--;
        if (pending != null && (++pendingAge > PLAN_TIMEOUT_TICKS || !stillValid(player, pending))) {
            if (pendingAge > PLAN_TIMEOUT_TICKS && pending.kind != Kind.HELPER) {
                retryAfter.put(pending.plan.target().asLong(), ticks + RETRY_AFTER_TICKS);
                if (pending.plan.exact()) exactGiveUps.merge(pending.plan.target().asLong(), 1, Integer::sum);
            }
            pending = null;
        }
        // Planning during the cooldown lets the rotation travel to the next block while the delay runs out.
        // Mid pillar jump the support click owns the click and the rotation: a build block planned now would turn
        // the view away from the block under the feet and the jump would land without its support.
        if (pending == null && !(autoWalk.get() && mover.midPillarJump())) {
            pending = chooseAdjustment(player);
            if (pending == null) pending = choose(player);
            pendingAge = 0;
        }
        if (pending == null && autoWalk.get() && mover.midPillarJump()) status = Status.OUT_OF_REACH;
        if (pending == null && autoWalk.get()) {
            pending = move(player);
            pendingAge = 0;
        } else if (pending != null && autoWalk.get()) {
            mover.holdForClick(player, clickOnTheMove(pending));
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
        if (status == Status.DONE || status == Status.DONE_INEXACT) {
            if (removeHelpers.get() && mover.hasHelpers() && mover.tickClear(player, effectiveRange())) {
                status = Status.CLEARING;
            } else {
                mover.pause();
            }
            return null;
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

    /**
     * A placement that comes out the same wherever the click lands and whichever way the player looks (stone,
     * planks, wool) can go in while walking past. Anything with properties, a look-decided facing or a sneak
     * click waits for the player to stand.
     */
    private static boolean clickOnTheMove(Pending work) {
        return work.kind == Kind.PLACE && !work.plan.sneak() && work.expected.getProperties().isEmpty();
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
            // The click goes out ahead of this tick's flying packet, so Grim reads it against the rotation the
            // previous packet carried (RotationPlace "pre-flying") as well as the one this tick sends. One miss
            // there arms the check, after which every click that does not already face the block is denied and
            // the denials keep it armed. The last sent rotation has to look at the face too, whatever the block.
            facingRotation = server;
            if (!BuildPlanner.matches(BuildPlanner.trace(player, server, reach), plan)) {
                rotationWaitTicks++;
                return false;
            }
        } else {
            hit = new BlockHitResult(plan.hit(), plan.face(), plan.support(), false);
            facingRotation = null;
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
        boolean lookSensitive = BuildPlanner.lookDependent(work.expected);
        if (exact && lookSensitive && facingRotation != null) {
            exact = robustFacing(player, stack, plan, hit, facingRotation, work.expected);
        }
        // A plan that can give the exact state waits for it; clicking early puts a slab in the wrong half for good.
        // Only a cell whose exact click keeps failing (see exactGiveUps) is placed approximate. A sneaking click was
        // simulated by the planner standing up (a chest beside a hopper looks like a double chest there), while this
        // prediction ran crouched: that one is the truth, so no wait.
        if (!exact && plan.exact() && !plan.sneak()
                && exactGiveUps.getOrDefault(plan.target().asLong(), 0) < EXACT_GIVE_UP_PLANS) {
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
            player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).getAttackAnimation(), false);
            placed++;
            lastPlaceTick = ticks;
            if (!exact) {
                approximatePlaced++;
                state.flag(plan.target(), work.expected, SchematicAttention.Reason.APPROXIMATE);
            }
            approximateSince.remove(plan.target().asLong());
            lookWanted.remove(plan.target().asLong());
            yawOnly.remove(plan.target().asLong());
            yawWanted.remove(plan.target().asLong());
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

    /** A support block under the feet mid-jump: no exactness rules, but it is remembered for clean-up. */
    private boolean placeHelper(LocalPlayer player, Pending work, BlockHitResult hit) {
        BuildPlanner.Plan plan = work.plan;
        selectSlot(work.hotbarSlot);
        InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(hit.getLocation(), plan.face(), plan.support(), false));
        if (result != null && result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).getAttackAnimation(), false);
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
        BlockPos support = work.plan.support();
        // A cell holding part of its stack (one slab of a double one) is clicked on the block itself.
        boolean stacking = support.equals(work.plan.target()) && StateComparison.fewerPieces(current, work.expected);
        if (!current.canBeReplaced() && !stacking) return false;
        if (!stacking && mc.level.getBlockState(support).canBeReplaced()) return false;
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
        for (int i = 0; i < Math.min(count, MAX_PLAN_ATTEMPTS); i++) {
            int cell = candidateCells[i];
            BlockState expected = diff.expectedState(cell);
            Item item = SchematicMaterials.itemFor(expected);
            int hotbar = hotbarSlotFor(player, item);
            BlockPos target = diff.worldPos(cell, scratch).immutable();
            // Plan before restocking: swapping in the item for a cell that cannot be clicked from here makes the
            // next candidate swap its own item back over it, every tick, and nothing gets placed.
            ItemStack stack = hotbar >= 0 ? player.getInventory().getItem(hotbar) : new ItemStack(item);
            BuildPlanner.Plan plan = BuildPlanner.plan(player, mc.level, target, expected, stack, reach, aim);
            if (plan == null || !acceptPlan(player, stack, diff, plan, expected)) continue;
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
     * Whether a plan found for a candidate goes out now. An approximate block whose facing follows the look (a
     * piston, observer, stair, furnace) first gives the walker a while to find a spot with the right look; one that
     * no look fixes (a top slab or upside-down stair with only a floor to click) waits while a schematic neighbour
     * is still unbuilt, since that neighbour's side face gives the click for the right half; a cell that would fire
     * waits for the circuit. Both waits end after {@link #APPROXIMATE_PATIENCE_TICKS}.
     */
    private boolean acceptPlan(LocalPlayer player, ItemStack stack, SchematicDiff diff, BuildPlanner.Plan plan,
                               BlockState expected) {
        BlockPos target = plan.target();
        long key = target.asLong();
        if (!plan.exact()) {
            Direction look = RedstoneRules.requiredLook(expected);
            Direction yaw = RedstoneRules.requiredYaw(expected);
            Float rotation = null;
            if (look == null && yaw == null) {
                rotation = BuildPlanner.rotationFixing(player, mc.level, stack, plan, expected);
                if (rotation == null) yaw = BuildPlanner.lookFixing(player, mc.level, stack, plan, expected);
            }
            int since = approximateSince.computeIfAbsent(key, ignored -> ticks);
            if ((look != null || yaw != null || rotation != null) && autoWalk.get()
                    && ticks - since < APPROXIMATE_PATIENCE_TICKS) {
                if (rotation != null) {
                    yawWanted.put(key, rotation);
                } else {
                    lookWanted.put(key, look != null ? look : yaw);
                    if (look == null) yawOnly.add(key);
                }
                return false;
            }
            boolean fixable = look != null || yaw != null || rotation != null;
            if (!fixable && ticks - since < APPROXIMATE_PATIENCE_TICKS
                    && unbuiltNeighbour(diff, target, player)) {
                retryAfter.put(key, ticks + RECENT_PLACE_TICKS);
                return false;
            }
            // Out of patience: the walker stops looking for a better spot and the approximate click goes out.
            lookWanted.remove(key);
            yawOnly.remove(key);
            yawWanted.remove(key);
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
     * Whether a schematic cell beside {@code target} is going to be built soon enough to click its side face: it is
     * missing now, open under the current band, not derived, its material is usable and its chunk is loaded. Only
     * full blocks count: a neighbouring slab or stair gives no side face covering the upper half, and two such
     * cells would wait on each other. Only the sides count: the cell above belongs to a later layer.
     */
    private boolean unbuiltNeighbour(SchematicDiff diff, BlockPos target, LocalPlayer player) {
        SchematicPlacement placement = diff.placement();
        int openBand = safety.openBand(diff);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos neighbour = target.relative(direction);
            int x = placement.localX(neighbour.getX(), neighbour.getZ());
            int y = placement.localY(neighbour.getY());
            int z = placement.localZ(neighbour.getX(), neighbour.getZ());
            if (!diff.schematic().contains(x, y, z)) continue;
            int cell = diff.cellOf(diff.schematic().index(x, y, z));
            if (cell < 0 || !mc.level.hasChunkAt(neighbour)) continue;
            BlockState neighbourState = diff.expectedState(cell);
            if (neighbourState.isAir() || StateComparison.derived(neighbourState)
                    || !neighbourState.isCollisionShapeFullBlock(mc.level, neighbour)) continue;
            if (diff.status(cell) != SchematicDiff.MISSING || !safety.eligible(mc.level, diff, cell, openBand)) continue;
            Item item = SchematicMaterials.itemFor(neighbourState);
            if (item == Items.AIR || usableCount(player, item) == 0) continue;
            return true;
        }
        return false;
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
        InteractionResult result = mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit);
        if (result != null && result.consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND, player.getItemInHand(InteractionHand.MAIN_HAND).getAttackAnimation(), false);
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
        while (safety.layerSettled(mc.level, diff, layer, openBand)) {
            if (layer >= top) {
                // Power and cells hanging on higher layers were left behind on the way up; come back for them.
                if (BuildSafety.backlogOpen(diff, top + 1)) return top;
                // Wrong or missing cells left behind (approximate facings, states clicks cannot fix, cells nobody
                // could reach) are not a finished build: say so instead of reporting it exact.
                int inexact = inexactCells(diff);
                status = inexact > 0 ? Status.DONE_INEXACT : Status.DONE;
                if (completedLayerNotified != Integer.MAX_VALUE) {
                    completedLayerNotified = Integer.MAX_VALUE;
                    if (inexact > 0) {
                        Notifier.warning(I18n.get("module.schematicbuilder.notify.done_inexact", inexact));
                    } else {
                        Notifier.success(I18n.get("module.schematicbuilder.notify.done"));
                    }
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

    private static int inexactCells(SchematicDiff diff) {
        int count = 0;
        for (int y = 0, layers = diff.schematic().sizeY(); y < layers; y++) count += diff.wrong(y) + diff.missing(y);
        return count;
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
                if (usableCount(player, item) == 0) {
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

    /**
     * Items the builder can actually put in a hand: the hotbar, and the main inventory when restocking is on. The
     * offhand and a restock-off main inventory count for nothing here, however much the player carries.
     */
    private int usableCount(LocalPlayer player, Item item) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (int i = 0; i < (restock.get() ? Inventory.INVENTORY_SIZE : 9); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == item) total += stack.getCount();
        }
        return total;
    }

    private static boolean movingOrSneaking(LocalPlayer player) {
        if (player.isSprinting() || player.isShiftKeyDown()) return true;
        if (player.input == null || player.input.keyPresses == null) return false;
        net.minecraft.world.entity.player.Input keys = player.input.keyPresses;
        return keys.forward() || keys.backward() || keys.left() || keys.right() || keys.shift() || keys.sprint();
    }

    /**
     * Swaps a stack from the main inventory into the restock slot; placement resumes on a later tick. A container
     * click while moving is a Grim MultiActionsC flag, so the click waits (reporting success, so no other candidate
     * is tried and the walker is paused) until the player has stood still for a moment.
     */
    private boolean restockFromInventory(LocalPlayer player, Item item) {
        if (player.containerMenu != player.inventoryMenu) return false;
        if (!restockGate.ready()) return true;
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
            int missing = entry.getValue()[0] - usableCount(player, entry.getKey());
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
     * Missing cells of the working layers that the inventory has material for, as packed world positions; the
     * walker plans where to stand from these.
     */
    private int walkTargets(LocalPlayer player, long[] out) {
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
                if (status == SchematicDiff.CORRECT || status == SchematicDiff.WRONG) continue;
                if (y < layer && !BuildSafety.deferrable(diff, cell)) continue;
                if (diff.refresh(mc.level, cell) != SchematicDiff.MISSING) continue;
                if (!safety.eligible(mc.level, diff, cell, openBand)) continue;
                BlockState expected = diff.expectedState(cell);
                Item item = SchematicMaterials.itemFor(expected);
                if (item == Items.AIR || SchematicMaterials.itemsPerCell(expected) == 0) continue;
                if (usableCount(player, item) == 0) continue;
                diff.worldPos(cell, scratch);
                if (retryAfter.containsKey(scratch.asLong())) continue;
                out[count++] = WalkTerrain.pack(scratch.getX(), scratch.getY(), scratch.getZ());
            }
        }
        return count;
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

        // Items still needed per item, rebuilt once per diff revision: the pillar asks up to 18 times a tick.
        private final Map<Item, Integer> spareNeeded = new HashMap<>();
        private SchematicDiff spareDiff;
        private int spareRevision;

        @Override
        public boolean canSpare(Block block) {
            Item item = block.asItem();
            SchematicDiff diff = state.currentDiff();
            if (diff == null || mc.player == null) return true;
            if (diff != spareDiff || diff.revision() != spareRevision) {
                spareNeeded.clear();
                for (int cell = 0, count = diff.cellCount(); cell < count; cell++) {
                    if (diff.status(cell) == SchematicDiff.CORRECT) continue;
                    BlockState expected = diff.expectedState(cell);
                    spareNeeded.merge(SchematicMaterials.itemFor(expected), SchematicMaterials.itemsPerCell(expected), Integer::sum);
                }
                spareDiff = diff;
                spareRevision = diff.revision();
            }
            return SchematicMaterials.countInInventory(mc.player, item) > spareNeeded.getOrDefault(item, 0);
        }

        @Override
        public void releaseHotbar() {
            InventorySwap.INSTANCE.releaseHotbar(SchematicBuilder.this);
        }

        @Override
        public boolean lookAllows(long target, double eyeX, double eyeY, double eyeZ) {
            if (lookWanted.isEmpty() && yawWanted.isEmpty()) return true;
            long key = BlockPos.asLong(WalkTerrain.unpackX(target), WalkTerrain.unpackY(target), WalkTerrain.unpackZ(target));
            Float wantedYaw = yawWanted.get(key);
            if (wantedYaw != null) {
                return BuildPlanner.yawAllows(WalkTerrain.unpackX(target) + 0.5 - eyeX,
                        WalkTerrain.unpackZ(target) + 0.5 - eyeZ, wantedYaw);
            }
            Direction look = lookWanted.get(key);
            if (look == null) return true;
            double dx = WalkTerrain.unpackX(target) + 0.5 - eyeX;
            double dy = WalkTerrain.unpackY(target) + 0.5 - eyeY;
            double dz = WalkTerrain.unpackZ(target) + 0.5 - eyeZ;
            // Only the yaw decides a repeater: the height of the eyes does not matter.
            return dominantLook(dx, yawOnly.contains(key) ? 0.0 : dy, dz, look);
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
