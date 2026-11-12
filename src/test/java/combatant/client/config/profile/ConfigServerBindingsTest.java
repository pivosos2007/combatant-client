/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.config.profile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigServerBindingsTest {
    @TempDir Path directory;

    @Test
    void replacesAssignmentForTheSameTargetAndPersistsExactlyOneEntry() throws Exception {
        var file = directory.resolve("server-bindings.properties");
        var bindings = new ConfigServerBindings(file);
        bindings.set(ConfigProfileType.MODULES, "first", "Example.org:25565");
        bindings.set(ConfigProfileType.MODULES, "second", "example.org.");
        bindings.set(ConfigProfileType.MODULES, "local-first", "local:example.org");
        bindings.set(ConfigProfileType.MODULES, "local-second", "local:example.org");
        bindings.reload();
        assertEquals(Map.of("example.org", "second", "local:example.org", "local-second"),
                bindings.bindingsFor(ConfigProfileType.MODULES));
        bindings.unbind(ConfigProfileType.MODULES, "example.org");
        bindings.reload();
        assertEquals(Map.of("local:example.org", "local-second"), bindings.bindingsFor(ConfigProfileType.MODULES));
    }

    @Test
    void reloadNormalizesAliasesWithoutDuplicatingTheCanonicalBinding() throws Exception {
        var file = directory.resolve("server-bindings.properties");
        Files.writeString(file, "modules|Example.org\\:25565=old\nmodules|example.org=current\n");
        var bindings = new ConfigServerBindings(file);
        assertEquals(Map.of("example.org", "current"), bindings.bindingsFor(ConfigProfileType.MODULES));
        bindings.set(ConfigProfileType.MODULES, "replacement", "EXAMPLE.ORG");
        bindings.reload();
        assertEquals(Map.of("example.org", "replacement"), bindings.bindingsFor(ConfigProfileType.MODULES));
    }
}
