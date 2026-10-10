/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.player;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import combatant.client.util.anticheat.AntiCheatPreset;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.RenderState;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.aiming.RotationManager;
import combatant.client.util.aiming.RotationTarget;
import combatant.client.util.aiming.data.Rotation;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.BlockOutlineRenderer;
import combatant.client.util.player.BreakSpeedUtil;
import combatant.client.util.player.inventory.InventorySwap;
import combatant.client.util.player.inventory.InventorySwapMode;

import java.util.List;

/**
 * Packet mining for anarchy servers: mines without holding the attack key, swaps to the best
 * tool only for the packet that finishes the block, and runs two tricks vanilla servers allow.
 *
 * <p>How the 26.2 server ({@code ServerPlayerGameMode.handleBlockBreakAction}) makes it work:</p>
 * <ul>
 *   <li>START remembers {@code destroyPos} and the tick it started. STOP only compares the
 *       position and computes {@code progress = speed(held item) * (now - start + 1)}; it never
 *       checks that a break is still in progress. So one START is enough, and the tool only has
 *       to be in hand when STOP arrives.</li>
 *   <li><b>Rebreak</b>: a successful STOP leaves {@code destroyPos} and the start tick alone. If
 *       the block is placed again, another bare STOP passes the progress check at once (the
 *       elapsed time keeps growing), so the new block breaks instantly until another START moves
 *       {@code destroyPos}.</li>
 *   <li><b>Double mine</b>: a STOP below 0.7 progress does not fail; it parks the block in the
 *       server's single "delayed destroy" slot, which the server keeps ticking on its own and
 *       breaks once {@code speed(held item) * elapsed >= 1}. The next START then mines a second
 *       block in parallel. The only client duty is holding the tool when the parked block is due.</li>
 *   <li><b>Efficiency lag</b>: the server reads Efficiency from the {@code MINING_EFFICIENCY}
 *       attribute, which it refreshes only on the player's own tick. A tool swapped in for the STOP
 *       packet alone brings its base speed but not its Efficiency, so when that bonus is needed the
 *       tool is leased a couple of ticks early and STOP waits until the server has applied it.
 *       Progress is still stateless, so the bonus counts for every elapsed tick, not just those.</li>
 * </ul>
 */
@ModuleInfo(
        id = "speedmine",
        displayName = "SpeedMine",
        aliases = {"PacketMine", "InstaMine"},
        category = ModuleCategory.PLAYER, subcategory = ModuleSubcategory.EXPLOIT,
        description = "module.speedmine.description")
public final class SpeedMine extends Module {

    // Below KillAura/AutoCrystal rotations so combat still wins the head when both run.
    private static final int ROTATION_PRIORITY = 20;
    // Vanilla STOP threshold; a smaller server-side progress parks the block instead of breaking it.
    private static final float SERVER_STOP_PROGRESS = 0.7f;
    // Round trip for a STOP -> block update; after this the server most likely disagreed.
    private static final int CONFIRM_TIMEOUT_TICKS = 12;
    private static final int MAX_RESTARTS = 3;
    // Vanilla waits this many ticks after finishing a block before the next one can start.
    private static final int BREAK_GAP_TICKS = 5;
    // A new server-held slot counts for Efficiency only after the server has ticked the player with it.
    private static final int ATTRIBUTE_SETTLE_TICKS = 2;
    // How long the tool stays leased while a parked block is due, and how often that is retried.
    private static final int DELAYED_HOLD_TICKS = ATTRIBUTE_SETTLE_TICKS + 5;
    private static final int MAX_DELAYED_HOLDS = 4;
    // Server reach check is eye to nearest block point, block reach plus this buffer.
    private static final double SERVER_REACH_BUFFER = 1.0;
    // Walking away mid-dig keeps the block until the server limit, not the (smaller) range setting.
    private static final double ABORT_RANGE_SLACK = 1.0;
    private static final float FACING_TOLERANCE_DEGREES = 20.0f;
    private static final int PRE_SWAP_LEASE_TICKS = ATTRIBUTE_SETTLE_TICKS + 3;

    // custom = the settings below as set; grim = no rebreak, no double mine and vanilla's 5-tick gap between
    // finished blocks (GrimAC FastBreak flags chained breaks closer than that); vanilla = every packet trick on,
    // for servers with no anticheat.
    private final EnumValue<AntiCheatPreset> anticheat = enumMode("anticheat_mode", AntiCheatPreset.CUSTOM);
    private final NumberValue<Float> range = num("range", 5.0f, 3.0f, 6.0f);
    // Fraction of the predicted break time before STOP. 1.0 is exact; values closer to 0.7 finish
    // sooner on servers that trust the vanilla threshold, but lag makes early STOPs park the block.
    private final NumberValue<Float> breakAt = num("break_at", 1.0f, 0.7f, 1.0f);
    private final EnumValue<SwapMode> swapMode = enumMode("swap_mode", SwapMode.SILENT);
    private final BooleanValue rebreak = bool("rebreak", true);
    private final EnumValue<RebreakMode> rebreakMode =
            visibleWhen(enumMode("rebreak_mode", RebreakMode.INSTANT), rebreak::get);
    private final NumberValue<Integer> rebreakDelay =
            visibleWhen(num("rebreak_delay", 0, 0, 20), rebreak::get);
    private final BooleanValue doubleMine = bool("double_mine", true);
    private final BooleanValue rotate = bool("rotate", false);
    private final BooleanValue swing = bool("swing", true);
    private final BooleanValue render = bool("render", true);
    private final RGBAColorValue miningColor = visibleWhen(color("mining_color", "#50FF3C3C"), render::get);
    private final RGBAColorValue readyColor = visibleWhen(color("ready_color", "#503CFF5A"), render::get);
    private final RGBAColorValue rebreakColor = visibleWhen(color("rebreak_color", "#AA3CA0FF"), render::get);

    private final Minecraft mc = Minecraft.getInstance();

    // Block the server tracks as destroyPos; STOP/rebreak only work for this one.
    private MineTask primary;
    // Block parked in the server's delayed-destroy slot (double mine).
    private MineTask delayed;
    // Last primary that broke; stays valid until another START moves the server's destroyPos.
    private MineTask rebreakTask;
    private int rebreakSentTick = Integer.MIN_VALUE;
    private int ticks;
    private int lastStopTick = Integer.MIN_VALUE / 2;
    private ClientLevel lastLevel;
    // clear() restarts RotationManager's smooth return, so it must run once, not every tick.
    private boolean rotating;
    // Server-held hotbar slot as of this tick, the one before it, and when it last changed.
    private int serverSlot = -1;
    private int previousServerSlot = -1;
    private int serverSlotSinceTick;

    @Override
    public void onDisable() {
        if (primary != null && !primary.stopSent) sendAction(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, primary);
        releaseTool();
        RotationManager.INSTANCE.clear(this);
        rotating = false;
        reset();
    }

    private SwapMode swapNow() {
        return anticheat.get().pick(swapMode.get(), swapMode.get(), SwapMode.SILENT);
    }

    private boolean rebreakNow() {
        return anticheat.get().pick(rebreak.get(), false, true);
    }

    private RebreakMode rebreakModeNow() {
        return anticheat.get().pick(rebreakMode.get(), rebreakMode.get(), RebreakMode.INSTANT);
    }

    private int rebreakDelayNow() {
        return anticheat.get().pick(rebreakDelay.get(), rebreakDelay.get(), 0);
    }

    private boolean doubleMineNow() {
        return anticheat.get().pick(doubleMine.get(), false, true);
    }

    private float breakAtNow() {
        return anticheat.get().pick(breakAt.get(), breakAt.get(), 1.0f);
    }

    /** True while GRIM still owes vanilla's gap after the last finished block. */
    private boolean breakGapPending() {
        return anticheat.get() == AntiCheatPreset.GRIM && ticks - lastStopTick < BREAK_GAP_TICKS;
    }

    private float rangeNow() {
        return anticheat.get().pick(range.get(), range.get(), 6.0f);
    }

    /**
     * Called from the game-mode mixin for every vanilla left-click on a block.
     *
     * @return true when SpeedMine took the block and vanilla mining must be cancelled
     */
    public boolean handleAttack(BlockPos pos, Direction direction) {
        LocalPlayer player = mc.player;
        if (!isEnabled() || player == null || mc.level == null || player.getAbilities().instabuild) return false;
        if (isMining(pos)) return true;
        if (breakGapPending()) return true;

        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return false;
        if (bestProgressPerTick(pos, state) <= 0.0f) return false;
        // Only the held item decides whether vanilla insta-mines; a faster hotbar tool is ours to swap in.
        if (progressPerTick(pos, state, InventorySwap.INSTANCE.clientSelectedSlot()) >= 1.0f) {
            // Vanilla insta-mines it, but that START resets the server's start tick (not destroyPos),
            // so the rebreak timing has to restart from now.
            if (rebreakTask != null) rebreakTask.startTick = ticks;
            return false;
        }
        // A click is always taken: turning it down hands it to vanilla, whose START would cancel the
        // block being mined instead of letting it park for double mine. So only the server limit applies.
        return start(pos, direction, serverReach(player));
    }

    /** Starts packet-mining {@code pos} within the range setting. Used by AutoMine. */
    public boolean mine(BlockPos pos, Direction direction) {
        if (breakGapPending()) return false;
        return start(pos, direction, rangeNow());
    }

    private boolean start(BlockPos pos, Direction direction, double reach) {
        LocalPlayer player = mc.player;
        if (!isEnabled() || player == null || mc.level == null || mc.getConnection() == null) return false;
        if (isMining(pos)) return true;
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir() || !inRange(player, pos, reach)) return false;

        if (primary != null && !primary.stopSent) {
            if (canDoubleMine()) {
                parkPrimary();
            } else {
                sendAction(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, primary);
            }
        }

        primary = new MineTask(pos.immutable(), direction != null ? direction : faceFor(player, pos), ticks);
        // The server's destroyPos moves with this START, so the old rebreak target is gone.
        rebreakTask = null;
        sendAction(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, primary);
        if (swing.get()) player.swing(InteractionHand.MAIN_HAND);
        return true;
    }

    public boolean isMining(BlockPos pos) {
        return (primary != null && primary.pos.equals(pos))
                || (delayed != null && delayed.pos.equals(pos))
                || (rebreakTask != null && rebreakTask.pos.equals(pos));
    }

    /**
     * True when no block is being dug on the primary slot. A primary whose STOP is already out counts
     * as idle: the server settles a STOP the moment it reads it, so a START right behind it cannot undo
     * the break, and waiting a round trip for the block update would only waste ping.
     */
    public boolean isIdle() {
        return primary == null || primary.stopSent;
    }

    /** True when a new {@link #mine} call would run in parallel instead of aborting the current block. */
    public boolean canDoubleMine() {
        return doubleMineNow() && delayed == null && primary != null && !primary.stopSent;
    }

    public BlockPos rebreakPos() {
        return rebreakTask != null ? rebreakTask.pos : null;
    }

    /**
     * Runs at the start of the client tick, before the player's movement packet. {@code onTick} runs after it,
     * which put every START/STOP behind that tick's flying packet and tripped GrimAC's Post check.
     */
    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.getConnection() == null) {
            reset();
            return;
        }
        if (lastLevel != null && mc.level != lastLevel) {
            releaseTool();
            reset();
        }
        lastLevel = mc.level;
        ticks++;
        trackServerSlot();

        tickDelayed(player);
        tickPrimary(player);
        tickRebreak(player);
        updateRotation(player);
    }

    private void tickPrimary(LocalPlayer player) {
        MineTask task = primary;
        if (task == null) return;
        BlockState state = mc.level.getBlockState(task.pos);

        if (task.stopSent) {
            if (state.isAir()) {
                onPrimaryBroken(task);
            } else if (ticks - task.stopTick > CONFIRM_TIMEOUT_TICKS) {
                restart(task);
            }
            return;
        }
        if (state.isAir()) {
            // Broken by someone else or by a lagging STOP; the server still points at this pos.
            onPrimaryBroken(task);
            return;
        }
        if (!inRange(player, task.pos, rangeNow() + ABORT_RANGE_SLACK)) {
            sendAction(ServerboundPlayerActionPacket.Action.ABORT_DESTROY_BLOCK, task);
            primary = null;
            return;
        }

        int toolSlot = toolSlot(task.pos, state);
        int elapsed = ticks - task.startTick + 1;
        // Server math is stateless: speed of whatever is held at STOP times elapsed ticks.
        float full = progressPerTick(task.pos, state, toolSlot);
        task.progress = elapsed * full;
        if (!readyToStop(task.pos, state, toolSlot, elapsed, full, breakAtNow()) || !isFacing(player, task)) return;

        sendWithTool(toolSlot, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, task);
        task.stopSent = true;
        task.stopTick = ticks;
    }

    private void tickDelayed(LocalPlayer player) {
        MineTask task = delayed;
        if (task == null) return;
        BlockState state = mc.level.getBlockState(task.pos);
        if (state.isAir()) {
            delayed = null;
            releaseDelayedTool();
            return;
        }

        int toolSlot = toolSlot(task.pos, state);
        int elapsed = ticks - task.startTick + 1;
        float full = progressPerTick(task.pos, state, toolSlot);
        task.progress = elapsed * full;
        if (swapNow() == SwapMode.OFF || toolSlot < 0) return;
        // The server finishes a parked block at a full 1.0, never at the 0.7 STOP threshold, and it
        // re-checks every tick with whatever is held then. Hold the tool early enough that its
        // Efficiency is already applied on the tick the block comes due.
        if ((elapsed + ATTRIBUTE_SETTLE_TICKS + 1) * full < 1.0f) return;

        if (task.holdUntilTick > ticks) return;
        if (task.holds >= MAX_DELAYED_HOLDS) {
            // Server never broke it (anti-cheat, or it lost the slot); stop pinning the hotbar.
            delayed = null;
            releaseDelayedTool();
            return;
        }
        task.holds++;
        task.holdUntilTick = ticks + DELAYED_HOLD_TICKS;
        holdTool(toolSlot, DELAYED_HOLD_TICKS);
    }

    private void tickRebreak(LocalPlayer player) {
        MineTask task = rebreakTask;
        if (task == null || primary != null) return;
        if (!inRange(player, task.pos, rangeNow() + ABORT_RANGE_SLACK)) return;

        BlockState state = mc.level.getBlockState(task.pos);
        if (state.isAir()) {
            rebreakSentTick = Integer.MIN_VALUE;
            return;
        }
        int toolSlot = toolSlot(task.pos, state);
        float full = progressPerTick(task.pos, state, toolSlot);
        if (full <= 0.0f) return;

        if (rebreakModeNow() == RebreakMode.NORMAL) {
            if (ticks - task.stopTick >= rebreakDelayNow()) restartRebreak(task);
            return;
        }

        if (rebreakSentTick != Integer.MIN_VALUE) {
            // A STOP went out and the block is still there: this server does not allow the trick.
            if (ticks - rebreakSentTick > CONFIRM_TIMEOUT_TICKS) restartRebreak(task);
            return;
        }
        if (ticks - task.stopTick < rebreakDelayNow()) return;
        if (!readyToStop(task.pos, state, toolSlot, ticks - task.startTick + 1, full, SERVER_STOP_PROGRESS)
                || !isFacing(player, task)) return;

        sendWithTool(toolSlot, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, task);
        rebreakSentTick = ticks;
        task.stopTick = ticks;
    }

    /**
     * True when a STOP sent now would reach {@code threshold} on the server. Otherwise, a few ticks
     * before the finish, starts holding the tool: the server needs that for Efficiency, and
     * anticheats that model break speed from the item held while digging then see the tool
     * for the end of the dig instead of a sword that turns into a pickaxe for one packet. The STOP
     * itself never waits for this; it goes out the tick the math says it lands.
     */
    private boolean readyToStop(BlockPos pos, BlockState state, int toolSlot, int elapsed, float full, float threshold) {
        float now = serverProgressPerTick(pos, state, toolSlot);
        if (elapsed * now >= threshold) return true;
        if (toolSlot >= 0 && toolSlot != serverSlot
                && (elapsed + ATTRIBUTE_SETTLE_TICKS + 1) * full >= threshold) {
            holdTool(toolSlot, PRE_SWAP_LEASE_TICKS);
        }
        return false;
    }

    private void holdTool(int toolSlot, int leaseTicks) {
        if (swapNow() == SwapMode.OFF) return;
        if (swapNow() == SwapMode.NORMAL) {
            InventorySwap.INSTANCE.selectHotbar(toolSlot);
        } else {
            InventorySwap.INSTANCE.leaseHotbar(this, toolSlot, leaseTicks);
        }
    }

    private void trackServerSlot() {
        int slot = InventorySwap.INSTANCE.serverSelectedSlot();
        if (slot == serverSlot) return;
        previousServerSlot = serverSlot;
        serverSlot = slot;
        serverSlotSinceTick = ticks;
    }

    private void onPrimaryBroken(MineTask task) {
        primary = null;
        if (!rebreakNow()) return;
        task.stopTick = ticks;
        rebreakTask = task;
        rebreakSentTick = Integer.MIN_VALUE;
    }

    private void restart(MineTask task) {
        if (++task.restarts > MAX_RESTARTS) {
            primary = null;
            return;
        }
        task.startTick = ticks;
        task.progress = 0.0f;
        task.stopSent = false;
        sendAction(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, task);
    }

    private void restartRebreak(MineTask task) {
        rebreakTask = null;
        rebreakSentTick = Integer.MIN_VALUE;
        mine(task.pos, task.direction);
    }

    /** Moves the running block into the server's delayed-destroy slot so a second one can start. */
    private void parkPrimary() {
        MineTask task = primary;
        BlockState state = mc.level.getBlockState(task.pos);
        // The STOP may already be past 0.7, in which case it breaks right away with the tool.
        sendWithTool(toolSlot(task.pos, state), ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, task);
        task.stopSent = true;
        task.stopTick = ticks;
        delayed = task;
        primary = null;
    }

    private void sendWithTool(int toolSlot, ServerboundPlayerActionPacket.Action action, MineTask task) {
        if (action == ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK) lastStopTick = ticks;
        InventorySwap swap = InventorySwap.INSTANCE;
        if (toolSlot < 0 || swapNow() == SwapMode.OFF || swap.serverSelectedSlot() == toolSlot) {
            sendAction(action, task);
            return;
        }
        if (swapNow() == SwapMode.NORMAL) {
            swap.selectHotbar(toolSlot);
            sendAction(action, task);
            return;
        }
        swap.withSwap(toolSlot, InventorySwapMode.SILENT, () -> sendAction(action, task));
    }

    private void sendAction(ServerboundPlayerActionPacket.Action action, MineTask task) {
        if (mc.getConnection() == null) return;
        mc.getConnection().send(new ServerboundPlayerActionPacket(action, task.pos, task.direction));
    }

    /** The primary may be holding the same lease for its own STOP; leave it to expire in that case. */
    private void releaseDelayedTool() {
        if (primary == null) releaseTool();
    }

    private void releaseTool() {
        InventorySwap.INSTANCE.releaseHotbar(this);
    }

    private void updateRotation(LocalPlayer player) {
        MineTask task = rotationTask();
        if (!rotate.get() || task == null) {
            if (rotating) RotationManager.INSTANCE.clear(this);
            rotating = false;
            return;
        }
        rotating = true;
        RotationTarget target = new RotationTarget(
                wantedRotation(player, task),
                null,
                List.of(),
                2,
                FACING_TOLERANCE_DEGREES,
                true,
                MovementCorrection.SILENT,
                null
        );
        RotationManager.INSTANCE.setRotationTarget(target, ROTATION_PRIORITY, this);
    }

    private MineTask rotationTask() {
        if (primary != null) return primary;
        if (rebreakTask != null && mc.level != null && !mc.level.getBlockState(rebreakTask.pos).isAir()) return rebreakTask;
        return null;
    }

    private boolean isFacing(LocalPlayer player, MineTask task) {
        if (!rotate.get()) return true;
        Rotation server = RotationManager.INSTANCE.getServerRotation();
        return server == null || server.angleTo(wantedRotation(player, task)) <= FACING_TOLERANCE_DEGREES;
    }

    private static Rotation wantedRotation(LocalPlayer player, MineTask task) {
        Vec3 face = Vec3.atCenterOf(task.pos).relative(task.direction, 0.5);
        return Rotation.lookingAt(face, player.getEyePosition()).normalize();
    }

    private int toolSlot(BlockPos pos, BlockState state) {
        if (swapNow() == SwapMode.OFF) return InventorySwap.INSTANCE.clientSelectedSlot();
        int best = BreakSpeedUtil.bestHotbarSlot(mc.player, mc.level, pos, state);
        return best >= 0 ? best : InventorySwap.INSTANCE.clientSelectedSlot();
    }

    private float progressPerTick(BlockPos pos, BlockState state, int slot) {
        if (slot < 0) return 0.0f;
        return BreakSpeedUtil.progressPerTick(mc.player, mc.level, pos, state, mc.player.getInventory().getItem(slot));
    }

    /**
     * Progress per tick the server computes for a STOP sent now with {@code slot} swapped in: the
     * tool's own speed, but Efficiency from the stack the server has applied attributes for. Right
     * after a slot change that is either of the last two server slots, so take the weaker one.
     */
    private float serverProgressPerTick(BlockPos pos, BlockState state, int slot) {
        if (slot < 0 || serverSlot < 0) return progressPerTick(pos, state, slot);
        LocalPlayer player = mc.player;
        ItemStack tool = player.getInventory().getItem(slot);
        float progress = BreakSpeedUtil.progressPerTick(player, mc.level, pos, state, tool,
                player.getInventory().getItem(serverSlot));
        if (ticks - serverSlotSinceTick < ATTRIBUTE_SETTLE_TICKS && previousServerSlot >= 0) {
            progress = Math.min(progress, BreakSpeedUtil.progressPerTick(player, mc.level, pos, state, tool,
                    player.getInventory().getItem(previousServerSlot)));
        }
        return progress;
    }

    private float bestProgressPerTick(BlockPos pos, BlockState state) {
        return progressPerTick(pos, state, toolSlot(pos, state));
    }

    public float mineRange() {
        return rangeNow();
    }

    /**
     * Same measure as the server's {@code isWithinBlockInteractionRange(pos, 1.0)}, which gates both
     * START and STOP: eye to the nearest point of the block, capped at block reach + 1.0. Measuring
     * to the block center instead rejected blocks a vanilla click can reach, and vanilla then sent its
     * own START, moving the server's destroyPos off the block being double-mined.
     */
    static boolean inRange(LocalPlayer player, BlockPos pos, double range) {
        double limit = Math.min(range, serverReach(player));
        return new AABB(pos).distanceToSqr(player.getEyePosition()) <= limit * limit;
    }

    private static double serverReach(LocalPlayer player) {
        return player.blockInteractionRange() + SERVER_REACH_BUFFER;
    }

    /** Exposed face closest to the eye; falls back to the nearest face when the block is buried. */
    public static Direction faceFor(LocalPlayer player, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        Direction bestOpen = null;
        double bestOpenDist = Double.MAX_VALUE;
        Direction bestAny = Direction.UP;
        double bestAnyDist = Double.MAX_VALUE;
        for (Direction direction : Direction.values()) {
            double dist = eye.distanceToSqr(center.relative(direction, 0.5));
            if (dist < bestAnyDist) {
                bestAnyDist = dist;
                bestAny = direction;
            }
            if (dist < bestOpenDist && player.level().getBlockState(pos.relative(direction)).canBeReplaced()) {
                bestOpenDist = dist;
                bestOpen = direction;
            }
        }
        return bestOpen != null ? bestOpen : bestAny;
    }

    @Override
    public WorldPhase getWorldPhase() {
        // NONE (the default) leaves the module out of every world render pass.
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || mc.level == null) return;
        float prevWidth = RenderState.lineWidth;
        RenderState.lineWidth = 1.5f;
        try {
            renderTask(renderer, primary, breakAtNow());
            renderTask(renderer, delayed, 1.0f);
            MineTask rebreakTarget = rebreakTask;
            if (rebreakTarget != null && primary == null) {
                BlockOutlineRenderer.renderBox(renderer, new AABB(rebreakTarget.pos), rebreakColor.getArgb());
            }
        } finally {
            RenderState.lineWidth = prevWidth;
        }
    }

    private void renderTask(Renderer3D renderer, MineTask task, float finishAt) {
        if (task == null) return;
        float done = Math.min(1.0f, task.progress / finishAt);
        // A parked block has its STOP out long before it is due, so only progress counts for it.
        boolean ready = done >= 1.0f || (task == primary && task.stopSent);
        int argb = ready ? readyColor.getArgb() : miningColor.getArgb();
        double inset = 0.5 * (1.0 - done);
        AABB box = new AABB(task.pos).deflate(inset);
        fillBox(renderer, box, argb);
        BlockOutlineRenderer.renderBox(renderer, new AABB(task.pos), argb | 0xFF000000);
    }

    private static void fillBox(Renderer3D renderer, AABB box, int argb) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        double x0 = box.minX, y0 = box.minY, z0 = box.minZ, x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
        renderer.quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, b, a);
        renderer.quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, b, a);
        renderer.quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, b, a);
        renderer.quad(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, b, a);
        renderer.quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, b, a);
        renderer.quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, b, a);
    }

    private void reset() {
        primary = null;
        delayed = null;
        rebreakTask = null;
        rebreakSentTick = Integer.MIN_VALUE;
        lastLevel = null;
        serverSlot = -1;
        previousServerSlot = -1;
    }

    private static final class MineTask {
        final BlockPos pos;
        final Direction direction;
        int startTick;
        int stopTick;
        boolean stopSent;
        int restarts;
        int holds;
        int holdUntilTick;
        float progress;

        MineTask(BlockPos pos, Direction direction, int startTick) {
            this.pos = pos;
            this.direction = direction;
            this.startTick = startTick;
        }
    }

    private enum SwapMode implements EnumValue.IdProvider {
        OFF("off"),
        NORMAL("normal"),
        SILENT("silent");

        private final String id;

        SwapMode(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }
    }

    private enum RebreakMode implements EnumValue.IdProvider {
        INSTANT("instant"),
        NORMAL("normal");

        private final String id;

        RebreakMode(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }
    }
}
