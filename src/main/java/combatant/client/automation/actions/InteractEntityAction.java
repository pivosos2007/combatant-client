/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.actions;

import combatant.client.automation.AbstractAction;
import combatant.client.automation.ActionContext;
import combatant.client.automation.ActionLease;
import combatant.client.automation.ActionResource;
import combatant.client.automation.ActionResult;
import combatant.client.automation.ExecutionPhase;
import combatant.client.automation.FailureReason;
import combatant.client.util.combat.AttackUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * One attack or interaction with an entity, in the {@link ExecutionPhase#AFTER_ROTATION} phase. Attacks go through
 * {@link AttackUtil} (living targets, so criticals/attribute swap/RubberHand rules apply) or the game mode for other
 * entities; interactions go through {@code MultiPlayerGameMode.interact}. Holds INTERACTION, plus ROTATION when the
 * entity has to be aimed at first.
 *
 * <p>Fails {@link FailureReason#INVALID_TARGET} when the entity is gone or dead and
 * {@link FailureReason#UNREACHABLE} when its hitbox is farther than {@code reach} from the eyes. Succeeds right after
 * the action was performed.
 */
public final class InteractEntityAction extends AbstractAction {

    public enum Kind {
        ATTACK,
        INTERACT
    }

    /** Share of the way from the nearest hitbox point toward the centre that is aimed at. */
    private static final double CENTER_BIAS = 0.5;

    private final Supplier<? extends Entity> target;
    private final Kind kind;
    private final InteractionHand hand;
    private final double reach;
    private final AimRequirement aim;
    private final float minAttackStrength;
    private final boolean keepSprint;
    private boolean performed;
    private boolean failed;

    private InteractEntityAction(Supplier<? extends Entity> target, Kind kind, InteractionHand hand, double reach,
                                 RotateAction.Settings rotation, float minAttackStrength, boolean keepSprint) {
        super(kind == Kind.ATTACK ? "AttackEntity" : "InteractEntity");
        this.target = Objects.requireNonNull(target, "target");
        this.kind = kind;
        this.hand = Objects.requireNonNull(hand, "hand");
        this.reach = Math.max(0.0, reach);
        this.minAttackStrength = Math.max(0.0f, Math.min(1.0f, minAttackStrength));
        this.keepSprint = keepSprint;
        this.aim = rotation != null ? AimRequirement.atPoint(this::aimPoint, rotation) : null;
    }

    /**
     * @param rotation          rotation requirement (null: no rotation, attack from the current server rotation)
     * @param minAttackStrength attack cooldown fraction (0..1) to wait for before swinging
     */
    public static InteractEntityAction attack(Supplier<? extends Entity> target, double reach,
                                              RotateAction.Settings rotation, float minAttackStrength,
                                              boolean keepSprint) {
        return new InteractEntityAction(target, Kind.ATTACK, InteractionHand.MAIN_HAND, reach, rotation,
                minAttackStrength, keepSprint);
    }

    public static InteractEntityAction interact(Supplier<? extends Entity> target, InteractionHand hand,
                                                double reach, RotateAction.Settings rotation) {
        return new InteractEntityAction(target, Kind.INTERACT, hand, reach, rotation, 0.0f, false);
    }

    private Vec3 aimPoint() {
        Entity entity = target.get();
        LocalPlayer player = Minecraft.getInstance().player;
        if (entity == null || player == null) return null;
        return aimPoint(player.getEyePosition(), entity.getBoundingBox());
    }

    /** Hitbox point nearest to the eyes, pulled toward the centre so the ray lands inside the box, not on its edge. */
    static Vec3 aimPoint(Vec3 eyes, AABB box) {
        Vec3 nearest = new Vec3(clamp(eyes.x, box.minX, box.maxX), clamp(eyes.y, box.minY, box.maxY),
                clamp(eyes.z, box.minZ, box.maxZ));
        return nearest.lerp(box.getCenter(), CENTER_BIAS);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** True when the hitbox is within {@code reach} of the eyes. */
    static boolean inReach(Vec3 eyes, AABB box, double reach) {
        return box.distanceToSqr(eyes) <= reach * reach;
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return ActionResult.failure(FailureReason.PRECONDITION, "no player");
        if (failed) return ActionResult.failure(FailureReason.REJECTED, kind + " not accepted");
        if (performed) return ActionResult.SUCCESS;

        Entity entity = target.get();
        if (entity == null || !entity.isAlive() || entity.level() != player.level()) {
            return ActionResult.failure(FailureReason.INVALID_TARGET, "entity gone");
        }
        if (!inReach(player.getEyePosition(), entity.getBoundingBox(), reach)) {
            return ActionResult.failure(FailureReason.UNREACHABLE, "out of reach");
        }
        if (!acquire(ctx, ActionResource.INTERACTION)) return ActionResult.RUNNING;
        if (aim != null) {
            if (!acquire(ctx, ActionResource.INTERACTION, ActionResource.ROTATION)) return ActionResult.RUNNING;
            if (!aim.request(player, ctx.priority(), this)) {
                return ActionResult.failure(FailureReason.INVALID_TARGET, "no aim point");
            }
        }
        ctx.defer(ExecutionPhase.AFTER_ROTATION, this, this::perform);
        return ActionResult.RUNNING;
    }

    private void perform() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Entity entity = target.get();
        if (player == null || mc.gameMode == null || entity == null || performed) return;
        if (aim != null && !aim.aligned(this)) return;
        // Re-validate: the entity and the player moved between the tick and this phase.
        if (!entity.isAlive() || !inReach(player.getEyePosition(), entity.getBoundingBox(), reach)) return;

        if (kind == Kind.ATTACK) {
            if (minAttackStrength > 0.0f && player.getAttackStrengthScale(0.0f) < minAttackStrength) return;
            boolean ok;
            if (entity instanceof LivingEntity living) {
                ok = AttackUtil.attack(mc, living, null, null, false, true, keepSprint);
            } else {
                mc.gameMode.attack(player, entity);
                AttackUtil.swingMainHand(player);
                ok = true;
            }
            // A refused attack (using an item, attribute-swap suppression) is retried on the next tick.
            performed = ok;
        } else {
            EntityHitResult hit = new EntityHitResult(entity, aimPoint(player.getEyePosition(), entity.getBoundingBox()));
            InteractionResult result = mc.gameMode.interact(player, entity, hit, hand);
            if (result != null && result.consumesAction()) {
                player.swing(hand, player.getItemInHand(hand).getInteractAnimation(), false);
                performed = true;
            } else {
                failed = true;
            }
        }
    }

    @Override
    protected void onRevoked(ActionLease lease) {
        if (aim != null) aim.release(this);
    }

    @Override
    protected void onEnd(ActionContext ctx, ActionResult result) {
        if (aim != null) aim.release(this);
    }
}
