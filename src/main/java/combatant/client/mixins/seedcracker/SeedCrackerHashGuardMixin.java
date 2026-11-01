package combatant.client.mixins.seedcracker;

import combatant.client.compat.seedcracker.SeedCrackerRuntime;
import kaptainwutax.seedcrackerX.cracker.HashedSeedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Resilient mode from the supplied fork: a client-visible hash is untrusted.
 * Zero is the upstream TimeMachine sentinel for "no usable hash"; unlike null,
 * it is safe for the upstream login/database path to inspect.
 */
@Pseudo
@Mixin(targets = "kaptainwutax.seedcrackerX.cracker.storage.DataStorage", remap = false)
public abstract class SeedCrackerHashGuardMixin {
    @ModifyVariable(method = "addHashedSeedData", at = @At("HEAD"), argsOnly = true,
            ordinal = 0, require = 0, remap = false)
    private HashedSeedData combatant$ignoreUntrustedHash(HashedSeedData original) {
        return SeedCrackerRuntime.ignoreServerHash() ? new HashedSeedData(0L) : original;
    }
}
