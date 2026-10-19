/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.navigation;

import combatant.client.automation.compat.baritone.BaritoneNavigationProvider;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class BaritoneNavigationProviderTest {

    @Test
    void unavailableWithoutTheMod() {
        BaritoneNavigationProvider provider = new BaritoneNavigationProvider(() -> false);
        assertFalse(provider.isAvailable());
        NavigationHandle handle = provider.navigate(NavigationRequest.block(0, 0, 0));
        assertEquals(NavigationStatus.FAILED, handle.status());
        assertEquals(NavigationFailure.UNAVAILABLE, handle.result().failure());
    }

    @Test
    void modPresentButApiMissingIsUnavailableAndNotRetriedEveryCall() {
        AtomicInteger checks = new AtomicInteger();
        BaritoneNavigationProvider provider = new BaritoneNavigationProvider(() -> {
            checks.incrementAndGet();
            return true;
        });
        assertFalse(provider.isAvailable());
        assertFalse(provider.isAvailable());
        assertEquals(1, checks.get(), "a failed binding is remembered");
    }

    @Test
    void registryKeepsBuiltinWhenBaritoneIsSelectedButMissing() {
        NavigationProvider walk = new WalkNavigationProvider(() -> (x, y, z) -> (byte) 0, () -> true);
        NavigationProviders registry = new NavigationProviders(walk);
        registry.register(new BaritoneNavigationProvider(() -> false));
        assertSame(walk, registry.select(BaritoneNavigationProvider.ID));
    }
}
