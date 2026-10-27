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
import combatant.client.automation.FailureReason;
import combatant.client.automation.navigation.NavigationHandle;
import combatant.client.automation.navigation.NavigationIntent;
import combatant.client.automation.navigation.NavigationProvider;
import combatant.client.automation.navigation.NavigationProviders;
import combatant.client.automation.navigation.NavigationRequest;
import combatant.client.automation.navigation.NavigationResult;
import combatant.client.automation.navigation.PlayerSample;
import combatant.client.util.aiming.RotationManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Walks the player to a goal through a {@link NavigationProvider}. Holds MOVEMENT, JUMP and SPRINT for the whole
 * navigation, waiting while another owner has them; whoever yields to manual input ends the action with
 * {@code MANUAL_OVERRIDE} when the player touches the keys.
 *
 * <p>For handles that steer ({@code !drivesPlayer()}) each tick's {@link NavigationIntent} becomes keys relative
 * to the movement rotation through {@link MovementKeys}. For handles that drive the player themselves (Baritone)
 * the leases are still held so no other runtime action fights the provider, no keys are written, and the handle
 * is cancelled, which stops the provider, when the action ends or a lease is revoked.</p>
 */
public final class NavigateAction extends AbstractAction {

    /** The player and rotation, behind a seam so the action runs headless. */
    public interface Environment {
        /** Fills {@code out} with the player's state; false when there is no player. */
        boolean sample(PlayerSample out);

        /** Yaw the movement keys are relative to. */
        float movementYaw();

        /** Vanilla's sprint preconditions (hunger, water, item use, sneaking). */
        boolean canSprint();
    }

    private static final Environment CLIENT = new Environment() {
        @Override
        public boolean sample(PlayerSample out) {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null) return false;
            Vec3 motion = player.getDeltaMovement();
            out.set(player.getX(), player.getY(), player.getZ(), motion.x, motion.z, player.onGround(),
                    player.horizontalCollision);
            out.eyeHeight = player.getEyeHeight();
            return true;
        }

        @Override
        public float movementYaw() {
            return RotationManager.INSTANCE.getMovementRotation().yaw();
        }

        @Override
        public boolean canSprint() {
            LocalPlayer player = Minecraft.getInstance().player;
            return player != null
                    && (player.getFoodData().getFoodLevel() > 6 || player.getAbilities().mayfly)
                    && !player.isInWater() && !player.isUsingItem() && !player.isShiftKeyDown();
        }
    };

    private final NavigationRequest request;
    private final NavigationProvider provider;
    private final String preferredProviderId;
    private final Environment environment;
    private final PlayerSample sample = new PlayerSample();
    private NavigationHandle handle;

    /** Uses the built-in walker. */
    public NavigateAction(NavigationRequest request) {
        this(request, (String) null);
    }

    /** Uses the registered provider with that id when it is available, else the built-in walker. */
    public NavigateAction(NavigationRequest request, String preferredProviderId) {
        super("Navigate");
        this.request = request;
        this.provider = null;
        this.preferredProviderId = preferredProviderId;
        this.environment = CLIENT;
    }

    public NavigateAction(NavigationRequest request, NavigationProvider provider) {
        this(request, provider, CLIENT);
    }

    public NavigateAction(NavigationRequest request, NavigationProvider provider, Environment environment) {
        super("Navigate");
        this.request = request;
        this.provider = provider;
        this.preferredProviderId = null;
        this.environment = environment;
    }

    /** The live handle once the action has acquired its resources; null before. */
    public NavigationHandle handle() {
        return handle;
    }

    @Override
    protected ActionResult onTick(ActionContext ctx) {
        if (!acquire(ctx, ActionResource.MOVEMENT, ActionResource.JUMP, ActionResource.SPRINT)) {
            return ActionResult.RUNNING;
        }
        if (!environment.sample(sample)) {
            return ActionResult.failure(FailureReason.PRECONDITION, "no player");
        }
        if (handle == null) {
            NavigationProvider chosen = provider != null ? provider : NavigationProviders.INSTANCE.select(preferredProviderId);
            handle = chosen.navigate(request);
        }
        handle.tick(sample);
        if (handle.isDone()) return translate(handle.result());
        if (!handle.drivesPlayer()) applyIntent(ctx, handle.intent());
        return ActionResult.RUNNING;
    }

    private void applyIntent(ActionContext ctx, NavigationIntent intent) {
        int keys = intent.steer
                ? MovementKeys.keysToward(environment.movementYaw(), intent.aimX - sample.x, intent.aimZ - sample.z)
                : 0;
        MovementKeys.apply(ctx, this, keys);
        ctx.setJump(this, intent.jump);
        ctx.setSprint(this, intent.sprint && keys == MovementKeys.FORWARD && !intent.jump && environment.canSprint());
    }

    private static ActionResult translate(NavigationResult result) {
        return switch (result.status()) {
            case SUCCEEDED -> ActionResult.SUCCESS;
            case CANCELLED -> ActionResult.cancelled(result.detail());
            case FAILED, RUNNING -> ActionResult.failure(switch (result.failure()) {
                case NO_PATH, STUCK -> FailureReason.UNREACHABLE;
                case TIMEOUT -> FailureReason.TIMEOUT;
                case UNAVAILABLE -> FailureReason.PRECONDITION;
                case CANCELLED -> FailureReason.CANCELLED;
                case ERROR, NONE -> FailureReason.ERROR;
            }, result.detail());
        };
    }

    @Override
    protected void onRevoked(ActionLease lease) {
        if (handle != null) handle.cancel();
    }

    @Override
    protected void onEnd(ActionContext ctx, ActionResult result) {
        if (handle != null) handle.cancel();
    }
}
