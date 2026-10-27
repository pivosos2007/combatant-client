/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.automation.Action;
import combatant.client.automation.ActionPriority;
import combatant.client.automation.ActionResult;
import combatant.client.automation.Actions;
import combatant.client.automation.AutomationRuntime;
import combatant.client.automation.AutomationTask;
import combatant.client.automation.FailureReason;
import combatant.client.automation.actions.BreakBlockAction;
import combatant.client.automation.actions.InteractBlockAction;
import combatant.client.automation.actions.InventoryAction;
import combatant.client.automation.actions.NavigateAction;
import combatant.client.automation.navigation.NavigationRequest;
import combatant.client.automation.placement.PlacementRotationMode;
import combatant.client.automation.placement.PlacementSlot;
import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.config.values.SetValue;
import combatant.client.config.values.StringValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.GameTickEvent;
import combatant.client.events.impl.PacketEvent;
import combatant.client.events.impl.WorldChangeEvent;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.Notifier;
import combatant.client.features.module.WorldPhase;
import combatant.client.features.relations.PlayerRelations;
import combatant.client.render.engine.renderer.Renderer3D;
import combatant.client.util.aiming.features.MovementCorrection;
import combatant.client.util.block.interaction.BlockInteractionPipeline;
import combatant.client.util.block.interaction.BlockInteractionPipeline.MiningPlan;
import combatant.client.util.block.interaction.BlockInteractionPipeline.RotationPlan;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Saves spawners from raiders. A stranger (not a friend, not whitelisted) entering the detection range, or a burst of
 * nearby block changes, starts a run: the module takes a silk-touch pickaxe, mines every spawner in range with the
 * shared {@link BreakBlockAction}, collects the drops, walks to an ender chest, opens it and shift-clicks the spawner
 * items inside. It then optionally disconnects and posts a Discord webhook.
 */
@ModuleInfo(
        id = "spawnerprotect",
        displayName = "SpawnerProtect",
        aliases = {"SpawnerSaver", "AutoSpawnerStash"},
        category = ModuleCategory.MISC,
        subcategory = ModuleSubcategory.UTILITY,
        description = "module.spawnerprotect.description")
public final class SpawnerProtect extends Module {

    private enum State {
        WAITING,
        MINING,
        COLLECTING,
        OPENING,
        DEPOSITING,
        FINISHED
    }

    private static final int IDLE_SCAN_INTERVAL = 100;
    private static final int PLAYER_SCAN_INTERVAL = 5;
    private static final int CHEST_SEARCH_RADIUS = 24;
    private static final int COLLECT_TIMEOUT_TICKS = 300;
    private static final int MAX_OPEN_ATTEMPTS = 4;
    private static final int MAX_STUCK_CLICKS = 4;
    private static final int PLAYER_SLOTS = 36;
    private static final double WHITELIST_NEAR_BLOCKS = 8.0;
    private static final long WEBHOOK_TIMEOUT_SECONDS = 10;

    private final Minecraft mc = Minecraft.getInstance();

    private final NumberValue<Double> detectRange = num("spawnerProtectDetectRange", "detect_range", 64.0D, 8.0D, 128.0D);
    private final BooleanValue detectBlockChanges = bool("spawnerProtectBlockChanges", "detect_block_changes", true);
    private final NumberValue<Integer> blockChangeThreshold = visibleWhen(
            num("spawnerProtectBlockThreshold", "block_change_threshold", 5, 1, 30), detectBlockChanges::get);
    private final NumberValue<Integer> blockChangeWindow = visibleWhen(
            num("spawnerProtectBlockWindow", "block_change_window", 60, 5, 600), detectBlockChanges::get);
    private final SetValue whitelist = textList(
            "spawnerProtectWhitelist", "whitelist", TextListSetting.PickerMode.TEXT);
    private final NumberValue<Double> spawnerRange = num("spawnerProtectSpawnerRange", "spawner_range", 24.0D, 4.0D, 48.0D);
    private final NumberValue<Double> breakRange = num("spawnerProtectBreakRange", "break_range", 4.5D, 1.0D, 6.0D);
    private final NumberValue<Integer> clickDelay = num("spawnerProtectClickDelay", "click_delay", 2, 1, 20);
    private final BooleanValue disconnect = bool("spawnerProtectDisconnect", "disconnect", true);
    private final StringValue webhookUrl = text("spawnerProtectWebhook", "webhook_url", "");
    private final BooleanValue render = bool("spawnerProtectRender", "render", true);
    private final RGBAColorValue spawnerColor = color("spawnerProtectSpawnerColor", "spawner_color", "#FFFF5A5A");
    private final RGBAColorValue chestColor = color("spawnerProtectChestColor", "chest_color", "#FFB05AFF");
    private final NumberValue<Float> lineWidth = num("spawnerProtectLineWidth", "line_width", 2.0F, 1.0F, 6.0F);

    private final ArrayDeque<Integer> blockChangeTicks = new ArrayDeque<>();
    private final Set<Long> failedSpawners = new HashSet<>();
    private final List<BlockPos> knownSpawners = new ArrayList<>();

    private State state = State.WAITING;
    private AutomationTask task;
    private BlockPos currentSpawner;
    private BlockPos chest;
    private String reason = "";
    private boolean blockTrigger;
    private int tickCounter;
    private int scanTicker;
    private int stateTicks;
    private int openAttempts;
    private int clickTicker;
    private int stuckClicks;
    private int lastStuckSlot = -1;
    private int lastStuckCount;
    private int minedCount;

    @Override
    public void onEnable() {
        reset();
    }

    @Override
    public void onDisable() {
        AutomationRuntime.INSTANCE.cancelOwner(this, "spawnerprotect disabled");
        closeOurContainer();
        reset();
    }

    @EventHandler
    private void onWorldChange(WorldChangeEvent event) {
        AutomationRuntime.INSTANCE.cancelOwner(this, "world changed");
        reset();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!isEnabled() || state != State.WAITING || !detectBlockChanges.get()) return;
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null) return;
        if (event.getPacket() instanceof ClientboundBlockUpdatePacket update) {
            noteBlockChange(level, player, update.getPos(), update.getBlockState());
        } else if (event.getPacket() instanceof ClientboundSectionBlocksUpdatePacket section) {
            section.runUpdates((pos, newState) -> noteBlockChange(level, player, pos, newState));
        }
    }

    private void noteBlockChange(ClientLevel level, LocalPlayer player, BlockPos pos, BlockState newState) {
        BlockState oldState = level.getBlockState(pos);
        if (oldState.isAir() || oldState.getBlock() == newState.getBlock()) return;
        double range = detectRange.get();
        if (player.position().distanceToSqr(Vec3.atCenterOf(pos)) > range * range) return;
        Vec3 center = Vec3.atCenterOf(pos);
        for (AbstractClientPlayer other : level.players()) {
            if (other == player) continue;
            if (other.getEyePosition().distanceToSqr(center) <= WHITELIST_NEAR_BLOCKS * WHITELIST_NEAR_BLOCKS
                    && isTrusted(other.getGameProfile().name())) {
                return;
            }
        }
        blockChangeTicks.addLast(tickCounter);
        while (!blockChangeTicks.isEmpty() && tickCounter - blockChangeTicks.peekFirst() > blockChangeWindow.get()) {
            blockChangeTicks.pollFirst();
        }
        if (blockChangeTicks.size() >= blockChangeThreshold.get()) blockTrigger = true;
    }

    /**
     * Ender-chest shift-clicks run at the start of the client tick, ahead of that tick's movement packet, so the
     * click never trails the flying packet (Grim PostOrder). One click per tick.
     */
    @EventHandler
    private void onGameTick(GameTickEvent event) {
        if (!isEnabled() || state != State.DEPOSITING) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null || player.isDeadOrDying()) return;
        tickDepositing(player);
    }

    @Override
    public void onTick() {
        if (!isEnabled()) return;
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null || player.isDeadOrDying()) return;
        tickCounter++;
        stateTicks++;

        switch (state) {
            case WAITING -> tickWaiting(player, level);
            case MINING -> tickMining(player, level);
            case COLLECTING -> tickCollecting(player, level);
            case OPENING -> tickOpening(player, level);
            case DEPOSITING, FINISHED -> {
            }
        }
    }

    // --- WAITING -----------------------------------------------------------------------------------------------

    private void tickWaiting(LocalPlayer player, ClientLevel level) {
        String found = null;
        if (blockTrigger) {
            found = I18n.get("notification.spawnerprotect.reason_blocks");
        } else if (++scanTicker >= PLAYER_SCAN_INTERVAL) {
            scanTicker = 0;
            AbstractClientPlayer stranger = findStranger(player, level);
            if (stranger != null) {
                found = I18n.get("notification.spawnerprotect.reason_player", stranger.getGameProfile().name());
            }
        }
        if (found == null) return;

        blockTrigger = false;
        blockChangeTicks.clear();
        scanTicker = -IDLE_SCAN_INTERVAL;
        scanSpawners(player, level);
        if (knownSpawners.isEmpty() && countSpawnerItems(player) == 0) return;

        reason = found;
        failedSpawners.clear();
        minedCount = 0;
        Notifier.warning(I18n.get("notification.spawnerprotect.triggered", found));
        if (!knownSpawners.isEmpty() && findSilkPickaxe(player, level) < 0) {
            Notifier.warning(I18n.get("notification.spawnerprotect.no_pickaxe"));
            knownSpawners.clear();
        }
        enter(knownSpawners.isEmpty() ? State.COLLECTING : State.MINING);
    }

    private AbstractClientPlayer findStranger(LocalPlayer self, ClientLevel level) {
        double range = detectRange.get();
        AbstractClientPlayer best = null;
        double bestDist = Double.MAX_VALUE;
        for (AbstractClientPlayer other : level.players()) {
            if (other == self || other.isRemoved() || other.isSpectator()) continue;
            if (isTrusted(other.getGameProfile().name())) continue;
            double dist = self.distanceTo(other);
            if (dist > range || dist >= bestDist) continue;
            best = other;
            bestDist = dist;
        }
        return best;
    }

    private boolean isTrusted(String name) {
        if (PlayerRelations.get().isFriend(name)) return true;
        String lower = name.toLowerCase(Locale.ROOT);
        for (String entry : whitelist.get()) {
            if (entry != null && entry.trim().toLowerCase(Locale.ROOT).equals(lower)) return true;
        }
        return false;
    }

    // --- MINING ------------------------------------------------------------------------------------------------

    private void tickMining(LocalPlayer player, ClientLevel level) {
        if (task != null) {
            if (!task.isDone()) return;
            if (currentSpawner != null) {
                if (task.result().isFailure()) failedSpawners.add(currentSpawner.asLong());
                else minedCount++;
            }
            task = null;
            currentSpawner = null;
        }
        scanSpawners(player, level);
        knownSpawners.removeIf(pos -> failedSpawners.contains(pos.asLong()));
        if (knownSpawners.isEmpty() || findSilkPickaxe(player, level) < 0) {
            enter(State.COLLECTING);
            return;
        }
        Vec3 eye = player.getEyePosition();
        knownSpawners.sort(Comparator.comparingDouble(pos -> eye.distanceToSqr(Vec3.atCenterOf(pos))));
        currentSpawner = knownSpawners.get(0);
        BlockPos target = currentSpawner;
        task = submit("SpawnerProtectMine", Actions.sequence("SpawnerProtectMine",
                Actions.defer("SpawnerProtectTool", () -> toolStep(player, level)),
                Actions.defer("SpawnerProtectWalk", () -> walkStep(target, breakRange.get())),
                new BreakBlockAction(target, null, miningPlan(), 2, 400, 60)));
    }

    private void scanSpawners(LocalPlayer player, ClientLevel level) {
        knownSpawners.clear();
        int radius = (int) Math.ceil(spawnerRange.get());
        BlockPos center = player.blockPosition();
        double rangeSq = spawnerRange.get() * spawnerRange.get();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius),
                center.offset(radius, radius, radius))) {
            if (!level.getBlockState(pos).is(Blocks.SPAWNER)) continue;
            if (pos.distToCenterSqr(player.position()) > rangeSq) continue;
            knownSpawners.add(pos.immutable());
        }
    }

    /** Puts the silk-touch pickaxe into the selected hotbar slot, or null when it is already there. */
    private Action toolStep(LocalPlayer player, ClientLevel level) {
        int slot = findSilkPickaxe(player, level);
        int selected = player.getInventory().getSelectedSlot();
        if (slot < 0) {
            return Actions.step("SpawnerProtectNoPickaxe",
                    () -> ActionResult.failure(FailureReason.MISSING_ITEM, "no silk touch pickaxe"));
        }
        if (slot == selected) return null;
        if (slot < 9) return InventoryAction.select(slot);
        return InventoryAction.moveToHotbar(slot, selected, 40);
    }

    private Action walkStep(BlockPos target, double reach) {
        LocalPlayer player = mc.player;
        if (player == null) return null;
        if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(target)) <= (reach - 0.5) * (reach - 0.5)) {
            return null;
        }
        NavigationRequest request = NavigationRequest.reach(target.getX(), target.getY(), target.getZ(),
                Math.max(1.5, reach - 1.25))
                .withOptions(NavigationRequest.Options.DEFAULT.withTimeout(400));
        return new NavigateAction(request);
    }

    private MiningPlan miningPlan() {
        RotationPlan rotation = new RotationPlan(true, List.of(), 2, 4.0f, false, MovementCorrection.SILENT,
                ActionPriority.SAFETY);
        return new MiningPlan(breakRange.get(), 0.0D, true, false, 5, rotation, false, false, false);
    }

    private int findSilkPickaxe(LocalPlayer player, ClientLevel level) {
        var registry = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Enchantment silk = registry.getValue(Enchantments.SILK_TOUCH);
        if (silk == null) return -1;
        Holder<Enchantment> holder = registry.wrapAsHolder(silk);
        for (int slot = 0; slot < PLAYER_SLOTS; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.isEmpty() || !stack.is(ItemTags.PICKAXES)) continue;
            if (EnchantmentHelper.getItemEnchantmentLevel(holder, stack) > 0) return slot;
        }
        return -1;
    }

    // --- COLLECTING --------------------------------------------------------------------------------------------

    private void tickCollecting(LocalPlayer player, ClientLevel level) {
        if (task != null) {
            if (!task.isDone()) return;
            task = null;
        }
        ItemEntity drop = nearestSpawnerDrop(player, level);
        if (drop == null) {
            // Give freshly dropped items a moment to appear before concluding nothing is left.
            if (stateTicks < 10) return;
            enter(countSpawnerItems(player) > 0 ? State.OPENING : State.FINISHED);
            return;
        }
        if (stateTicks > COLLECT_TIMEOUT_TICKS) {
            enter(countSpawnerItems(player) > 0 ? State.OPENING : State.FINISHED);
            return;
        }
        if (player.distanceTo(drop) <= 1.0F) return;
        NavigationRequest request = NavigationRequest.near(drop.getBlockX(), drop.getBlockY(), drop.getBlockZ(), 0.5)
                .withOptions(NavigationRequest.Options.DEFAULT.withTimeout(100));
        task = submit("SpawnerProtectCollect", new NavigateAction(request));
    }

    private ItemEntity nearestSpawnerDrop(LocalPlayer player, ClientLevel level) {
        double range = spawnerRange.get() + 8.0D;
        AABB box = player.getBoundingBox().inflate(range);
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity entity : level.getEntitiesOfClass(ItemEntity.class, box)) {
            if (!entity.getItem().is(Items.SPAWNER)) continue;
            double dist = player.distanceToSqr(entity);
            if (dist < bestDist) {
                best = entity;
                bestDist = dist;
            }
        }
        return best;
    }

    // --- OPENING -----------------------------------------------------------------------------------------------

    private void tickOpening(LocalPlayer player, ClientLevel level) {
        if (task != null) {
            if (!task.isDone()) return;
            boolean ok = task.result().isSuccess() && isChestMenu(player);
            task = null;
            if (ok) {
                enter(State.DEPOSITING);
                return;
            }
            if (++openAttempts >= MAX_OPEN_ATTEMPTS) {
                Notifier.warning(I18n.get("notification.spawnerprotect.chest_failed"));
                enter(State.FINISHED);
                return;
            }
        }
        if (countSpawnerItems(player) == 0) {
            enter(State.FINISHED);
            return;
        }
        if (chest == null || !level.getBlockState(chest).is(Blocks.ENDER_CHEST)) {
            chest = findEnderChest(player, level);
            if (chest == null) {
                Notifier.warning(I18n.get("notification.spawnerprotect.no_chest"));
                enter(State.FINISHED);
                return;
            }
        }
        BlockPos target = chest;
        double reach = breakRange.get();
        InteractBlockAction.Settings settings = new InteractBlockAction.Settings(
                reach, reach, PlacementRotationMode.NORMAL, MovementCorrection.SILENT, 2, true, 20);
        task = submit("SpawnerProtectOpen", Actions.sequence("SpawnerProtectOpen",
                Actions.defer("SpawnerProtectChestWalk", () -> walkStep(target, reach)),
                new InteractBlockAction(this, "SpawnerProtectChest", target,
                        p -> PlacementSlot.find(p, stack -> !stack.isEmpty()), settings,
                        () -> mc.player != null && isChestMenu(mc.player))));
    }

    private BlockPos findEnderChest(LocalPlayer player, ClientLevel level) {
        BlockPos center = player.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        int r = CHEST_SEARCH_RADIUS;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (!level.getBlockState(pos).is(Blocks.ENDER_CHEST)) continue;
            double dist = pos.distToCenterSqr(player.position());
            if (dist < bestDist) {
                best = pos.immutable();
                bestDist = dist;
            }
        }
        return best;
    }

    private boolean isChestMenu(LocalPlayer player) {
        return player.containerMenu instanceof ChestMenu && player.containerMenu != player.inventoryMenu;
    }

    // --- DEPOSITING --------------------------------------------------------------------------------------------

    private void tickDepositing(LocalPlayer player) {
        if (!isChestMenu(player)) {
            // The screen was closed under us; try again from the start.
            enter(State.OPENING);
            return;
        }
        if (++clickTicker < clickDelay.get()) return;
        clickTicker = 0;

        AbstractContainerMenu menu = player.containerMenu;
        int first = Math.max(0, menu.slots.size() - PLAYER_SLOTS);
        int slot = -1;
        for (int i = first; i < menu.slots.size(); i++) {
            if (menu.getSlot(i).getItem().is(Items.SPAWNER)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            player.closeContainer();
            enter(State.FINISHED);
            return;
        }

        int count = menu.getSlot(slot).getItem().getCount();
        if (slot == lastStuckSlot && count == lastStuckCount) {
            if (++stuckClicks >= MAX_STUCK_CLICKS) {
                Notifier.warning(I18n.get("notification.spawnerprotect.chest_full"));
                player.closeContainer();
                enter(State.FINISHED);
                return;
            }
        } else {
            stuckClicks = 0;
        }
        lastStuckSlot = slot;
        lastStuckCount = count;
        mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, player);
    }

    // --- FINISH ------------------------------------------------------------------------------------------------

    private void finish(LocalPlayer player) {
        int left = countSpawnerItems(player);
        Notifier.success(I18n.get("notification.spawnerprotect.done", minedCount, left));
        sendWebhook(player, left);
        if (disconnect.get()) {
            player.connection.getConnection().disconnect(
                    Component.literal(I18n.get("notification.spawnerprotect.disconnect", reason)));
        }
        setEnabled(false);
    }

    private void sendWebhook(LocalPlayer player, int spawnersLeftInInventory) {
        String url = webhookUrl.get() == null ? "" : webhookUrl.get().trim();
        if (url.isEmpty()) return;
        ServerData server = mc.getCurrentServer();
        String text = "**SpawnerProtect** | " + player.getGameProfile().name()
                + (server != null ? " @ " + server.ip : "")
                + "\nReason: " + reason
                + "\nSpawners mined: " + minedCount
                + ", still in inventory: " + spawnersLeftInInventory
                + "\nPosition: " + player.getBlockX() + " " + player.getBlockY() + " " + player.getBlockZ();
        String body = "{\"content\":\"" + escapeJson(text) + "\"}";
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(WEBHOOK_TIMEOUT_SECONDS))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .exceptionally(error -> null);
        } catch (IllegalArgumentException invalidUrl) {
            Notifier.warning(I18n.get("notification.spawnerprotect.bad_webhook"));
        }
    }

    private static String escapeJson(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.toString();
    }

    // --- shared ------------------------------------------------------------------------------------------------

    private void enter(State next) {
        state = next;
        stateTicks = 0;
        clickTicker = 0;
        stuckClicks = 0;
        lastStuckSlot = -1;
        if (next == State.OPENING) openAttempts = 0;
        if (next == State.FINISHED && mc.player != null) finish(mc.player);
    }

    private AutomationTask submit(String name, Action root) {
        return AutomationRuntime.INSTANCE.submit(AutomationTask.builder(this, name, root)
                .priority(ActionPriority.SAFETY)
                .build());
    }

    private int countSpawnerItems(LocalPlayer player) {
        int total = 0;
        for (int slot = 0; slot < PLAYER_SLOTS; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.SPAWNER)) total += stack.getCount();
        }
        return total;
    }

    private void closeOurContainer() {
        LocalPlayer player = mc.player;
        if (player != null && state == State.DEPOSITING && isChestMenu(player)) player.closeContainer();
    }

    private void reset() {
        state = State.WAITING;
        task = null;
        currentSpawner = null;
        chest = null;
        reason = "";
        blockTrigger = false;
        tickCounter = 0;
        scanTicker = 0;
        stateTicks = 0;
        openAttempts = 0;
        clickTicker = 0;
        stuckClicks = 0;
        lastStuckSlot = -1;
        minedCount = 0;
        blockChangeTicks.clear();
        failedSpawners.clear();
        knownSpawners.clear();
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !render.get() || state == State.WAITING) return;
        for (BlockPos pos : knownSpawners) {
            drawBox(renderer, pos, spawnerColor.getArgb());
        }
        if (chest != null) drawBox(renderer, chest, chestColor.getArgb());
    }

    private void drawBox(Renderer3D renderer, BlockPos pos, int argb) {
        AABB box = new AABB(pos);
        if (!Renderer3D.Culling.isInFrustum(box)) return;
        renderer.filledBox(box, (argb & 0x00FFFFFF) | 0x30000000);
        renderer.outlineBox(box, argb, lineWidth.get());
    }
}
