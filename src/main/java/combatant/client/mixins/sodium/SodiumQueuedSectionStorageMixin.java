/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.mixins.sodium;

import combatant.client.render.sodium.SodiumSectionStorageView;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.storage.QueuedSectionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Sodium 0.9.2+mc26.2 adapter. Keep private storage details out of deferred renderer code. */
@Pseudo
@Mixin(value = QueuedSectionStorage.class, remap = false)
public abstract class SodiumQueuedSectionStorageMixin implements SodiumSectionStorageView {
    @Shadow @Final
    private Long2ReferenceMap<RenderSection> sections;

    @Shadow @Final
    private Long2ReferenceMap<RenderSection> queuedPuts;

    @Shadow @Final
    private LongSet queuedRemovals;

    @Shadow
    private boolean isQueueing;

    @Unique
    private long combatant$structuralRevision;

    @Unique
    private boolean combatant$applyChangesOnSafeReadEnd;

    @Override
    public Iterable<RenderSection> combatant$sections() {
        return sections.values();
    }

    @Override
    public long combatant$structuralRevision() {
        return combatant$structuralRevision;
    }

    @Inject(method = "queuePut", at = @At("RETURN"), remap = false)
    private void combatant$revisionAfterDirectPut(long key, RenderSection section, CallbackInfo ci) {
        // When Sodium is in its safe-read phase this write is only queued; revision is advanced
        // after endSafeReadPhase actually applies the topology mutation.
        if (!isQueueing) combatant$structuralRevision++;
    }

    @Inject(method = "queueRemove", at = @At("RETURN"), remap = false)
    private void combatant$revisionAfterDirectRemove(long key, CallbackInfoReturnable<RenderSection> cir) {
        if (!isQueueing && cir.getReturnValue() != null) combatant$structuralRevision++;
    }

    @Inject(method = "endSafeReadPhase", at = @At("HEAD"), remap = false)
    private void combatant$captureQueuedTopologyChange(CallbackInfo ci) {
        combatant$applyChangesOnSafeReadEnd = isQueueing
                && (!queuedPuts.isEmpty() || !queuedRemovals.isEmpty());
    }

    @Inject(method = "endSafeReadPhase", at = @At("RETURN"), remap = false)
    private void combatant$revisionAfterQueuedChanges(CallbackInfo ci) {
        if (combatant$applyChangesOnSafeReadEnd) {
            combatant$structuralRevision++;
            combatant$applyChangesOnSafeReadEnd = false;
        }
    }

    @Inject(method = "deleteAll", at = @At("RETURN"), remap = false)
    private void combatant$revisionAfterDeleteAll(CallbackInfo ci) {
        combatant$structuralRevision++;
    }
}
