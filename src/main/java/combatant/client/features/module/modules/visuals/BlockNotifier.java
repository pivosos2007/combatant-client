/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.visuals;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.ColorListValue;
import combatant.client.config.values.NumberValue;
import combatant.client.config.values.RGBAColorValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.PacketEvent;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.gui.clickgui.settings.TextListSetting;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import combatant.client.features.module.WorldPhase;
import combatant.client.render.engine.renderer.Renderer3D;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

@ModuleInfo(
        id = "blocknotifier",
        displayName = "BlockNotifier",
        aliases = {"blockalert"},
        category = ModuleCategory.VISUALS, subcategory = ModuleSubcategory.ESP,
        description = "module.blocknotifier.description")
public final class BlockNotifier extends Module {
    private static final int MAX_MARKS = 64;

    private final Minecraft mc = Minecraft.getInstance();
    private final RGBAColorValue color = color("blockNotifierColor", "color", "#80FF9500");
    private final ColorListValue blocks = colorList("blockNotifierBlocks", "blocks", TextListSetting.PickerMode.BLOCKS,
            Set.of("minecraft:tnt", "minecraft:respawn_anchor"), color);
    private final NumberValue<Integer> range = num("blockNotifierRange", "range", 32, 4, 128);
    private final BooleanValue chat = bool("blockNotifierChat", "chat", true);
    private final BooleanValue highlight = bool("blockNotifierHighlight", "highlight", true);
    private final NumberValue<Integer> durationSeconds = num("blockNotifierDuration", "duration_seconds", 10, 1, 60);
    private final BooleanValue throughWalls = bool("blockNotifierThroughWalls", "through_walls", true);

    private record Update(BlockPos pos, BlockState state) {
    }

    private record Mark(BlockPos pos, String blockId, long expiresMs) {
    }

    private final ConcurrentLinkedQueue<Update> pending = new ConcurrentLinkedQueue<>();
    private final List<Mark> marks = new ArrayList<>();

    @Override
    public void onDisable() {
        pending.clear();
        marks.clear();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!isEnabled()) return;
        if (event.getPacket() instanceof ClientboundBlockUpdatePacket p) {
            pending.add(new Update(p.getPos().immutable(), p.getBlockState()));
        } else if (event.getPacket() instanceof ClientboundSectionBlocksUpdatePacket p) {
            p.runUpdates((pos, state) -> pending.add(new Update(pos.immutable(), state)));
        }
    }

    @Override
    public void onTick() {
        if (!isEnabled() || mc.level == null || mc.player == null) {
            pending.clear();
            return;
        }
        long now = Util.getMillis();
        marks.removeIf(m -> m.expiresMs() <= now);

        Set<String> targets = blocks.get();
        double maxSq = (double) range.get() * range.get();
        Update update;
        while ((update = pending.poll()) != null) {
            String id = BuiltInRegistries.BLOCK.getKey(update.state().getBlock()).toString();
            if (targets == null || !targets.contains(id)) continue;
            if (update.pos().distToCenterSqr(mc.player.position()) > maxSq) continue;
            boolean known = false;
            for (Mark m : marks) {
                if (m.pos().equals(update.pos())) {
                    known = true;
                    break;
                }
            }
            if (known) continue;
            if (marks.size() >= MAX_MARKS) marks.remove(0);
            marks.add(new Mark(update.pos(), id, now + durationSeconds.get() * 1000L));
            if (chat.get()) {
                BlockPos p = update.pos();
                CommandOutput.warning(I18n.get("notification.blocknotifier.found",
                        update.state().getBlock().getName().getString(), p.getX(), p.getY(), p.getZ(),
                        (int) Math.sqrt(p.distToCenterSqr(mc.player.position()))));
            }
        }
    }

    @Override
    public WorldPhase getWorldPhase() {
        return WorldPhase.END_MAIN;
    }

    @Override
    public void onRenderWorldEngine(Renderer3D renderer, Renderer3D depthRenderer, float tickDelta) {
        if (!isEnabled() || !highlight.get() || renderer == null || marks.isEmpty()) return;
        Renderer3D.DepthMode depth = throughWalls.get() ? Renderer3D.DepthMode.NONE : Renderer3D.DepthMode.MAIN;
        for (Mark m : marks) {
            BlockPos p = m.pos();
            AABB box = new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1, p.getZ() + 1);
            if (!Renderer3D.Culling.isInFrustum(box)) continue;
            int argb = blocks.argbFor(m.blockId());
            renderer.filledBox(box, argb, depth);
            renderer.outlineBox(box, argb | 0xFF000000, 2.0f, depth);
        }
    }
}
