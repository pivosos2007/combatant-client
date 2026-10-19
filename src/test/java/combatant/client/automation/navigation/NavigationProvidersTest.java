/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.automation.navigation;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NavigationProvidersTest {

    private static final class Fake implements NavigationProvider {
        final String id;
        boolean available;

        Fake(String id, boolean available) {
            this.id = id;
            this.available = available;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public boolean isAvailable() {
            return available;
        }

        @Override
        public NavigationHandle navigate(NavigationRequest request) {
            return new FailedNavigationHandle(request, NavigationFailure.ERROR, id);
        }
    }

    private final Fake builtin = new Fake("walk", true);
    private final NavigationProviders registry = new NavigationProviders(builtin);

    @Test
    void preferredAvailableProviderWinsOverBuiltin() {
        Fake other = new Fake("other", true);
        registry.register(other);
        assertSame(other, registry.select("other"));
    }

    @Test
    void unavailableUnknownOrBlankPreferenceFallsBackToBuiltin() {
        Fake other = new Fake("other", false);
        registry.register(other);
        assertSame(builtin, registry.select("other"));
        assertSame(builtin, registry.select("missing"));
        assertSame(builtin, registry.select(null));
        assertSame(builtin, registry.select(" "));
        other.available = true;
        assertSame(other, registry.select("other"), "availability is re-checked on every selection");
    }

    @Test
    void builtinIdCannotBeReplacedOrUnregistered() {
        assertThrows(IllegalArgumentException.class, () -> registry.register(new Fake("walk", true)));
        registry.unregister("walk");
        assertSame(builtin, registry.get("walk"));
    }

    @Test
    void registrationListsAndUnregisters() {
        registry.register(new Fake("a", true));
        registry.register(new Fake("b", false));
        assertEquals(List.of("walk", "a", "b"), registry.ids());
        assertEquals(List.of("walk", "a"), registry.availableIds());
        registry.unregister("a");
        assertEquals(List.of("walk", "b"), registry.ids());
        assertSame(builtin, registry.select("a"));
    }
}
