/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.movement;

import combatant.client.config.values.BooleanValue;
import combatant.client.config.values.EnumValue;
import combatant.client.config.values.NumberValue;
import combatant.client.events.EventHandler;
import combatant.client.events.impl.RotationUpdateEvent;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.features.module.ModuleSubcategory;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

@ModuleInfo(
        id = "clicktp",
        displayName = "ClickTp",
        aliases = {"ClickTeleport"},
        category = ModuleCategory.MOVEMENT,
        subcategory = ModuleSubcategory.RAGE,
        description = "module.clicktp.description")
public final class ClickTp extends Module {

    private static final double PACKET_STEP = 8.0D;
    private static final int MAX_PACKETS = 10;
    private static final double SURFACE_OFFSET = 0.01D;

    private final Minecraft mc = Minecraft.getInstance();

    private final EnumValue<Button> button = enumSetting("clickTpButton", "button", Button.PICK, Button.values());
    private final NumberValue<Double> range = num("clickTpRange", "range", 30.0D, 5.0D, 100.0D);
    private final BooleanValue requireSneak = bool("clickTpRequireSneak", "require_sneak", false);

    private boolean wasDown;

    @Override
    public void onEnable() {
        wasDown = false;
    }

    @EventHandler
    private void onRotationUpdate(RotationUpdateEvent event) {
        if (event.getType() != RotationUpdateEvent.Type.PRE) return;
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.getConnection() == null) return;

        boolean down = combatant.client.util.screen.ClientScreen.current() == null && button.get().key(mc).isDown();
        boolean clicked = down && !wasDown;
        wasDown = down;
        if (!clicked || player.isPassenger()) return;
        if (requireSneak.get() && !player.isShiftKeyDown()) return;

        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range.get()));
        BlockHitResult hit = mc.level.clip(new ClipContext(
                eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() != HitResult.Type.BLOCK) return;

        Vec3 target = destination(player, hit);
        if (target == null) return;
        teleport(player, target);
    }

    /** Free standing spot on the hit face, or null when the player's hitbox would not fit there. */
    private Vec3 destination(LocalPlayer player, BlockHitResult hit) {
        double nx = hit.getDirection().getStepX();
        double ny = hit.getDirection().getStepY();
        double nz = hit.getDirection().getStepZ();
        double halfWidth = player.getBbWidth() * 0.5D;
        Vec3 pos;
        if (hit.getDirection().getAxis() == net.minecraft.core.Direction.Axis.Y) {
            pos = hit.getLocation().add(0.0D, ny > 0 ? SURFACE_OFFSET : -player.getBbHeight(), 0.0D);
        } else {
            pos = hit.getLocation().add(nx * halfWidth, -SURFACE_OFFSET, nz * halfWidth);
        }

        Vec3 delta = pos.subtract(player.position());
        if (!mc.level.noCollision(player, player.getBoundingBox().move(delta))) {
            return null;
        }
        return pos;
    }

    private void teleport(LocalPlayer player, Vec3 target) {
        Vec3 start = player.position();
        if (!Double.isFinite(target.x) || !Double.isFinite(target.y) || !Double.isFinite(target.z)
                || !Double.isFinite(start.x) || !Double.isFinite(start.y) || !Double.isFinite(start.z)) {
            return;
        }
        double distance = start.distanceTo(target);
        // Total packets (intermediate + final) = ceil(distance / PACKET_STEP); refuse anything above the per-tick cap.
        int total = Math.max(1, (int) Math.ceil(distance / PACKET_STEP));
        if (total > MAX_PACKETS) return;
        for (int i = 1; i < total; i++) {
            Vec3 step = start.lerp(target, (double) i / total);
            mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                    step.x, step.y, step.z, false, player.horizontalCollision));
        }
        mc.getConnection().send(new ServerboundMovePlayerPacket.Pos(
                target.x, target.y, target.z, false, player.horizontalCollision));
        player.setPos(target.x, target.y, target.z);
        player.setDeltaMovement(Vec3.ZERO);
    }

    private enum Button {
        ATTACK,
        USE,
        PICK;

        KeyMapping key(Minecraft mc) {
            return switch (this) {
                case ATTACK -> mc.options.keyAttack;
                case USE -> mc.options.keyUse;
                case PICK -> mc.options.keyPickItem;
            };
        }
    }
}
