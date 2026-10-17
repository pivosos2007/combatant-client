/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module;

import combatant.client.events.UsedImplicitly;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares the text supplier for a module's optional ModuleList suffix.
 *
 * <p>Annotate a non-static, zero-argument method returning String. The compiler
 * validates the signature and indexes it; methods may be private, like event handlers.
 * The supplier is queried for visible enabled modules during HUD rendering, so it
 * must be inexpensive and return null/blank when no suffix should be shown.
 * Styling (including gradients) is owned solely by ModuleList.
 *
 * <pre>{@code
 * @ModuleListInfoProvider
 * private String moduleListDetail() {
 *     return mode.get().name();
 * }
 * }</pre>
 */
@UsedImplicitly
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ModuleListInfoProvider {
}
