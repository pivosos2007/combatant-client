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

@UsedImplicitly
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ModuleInfo {
    String id();

    String displayName();

    String[] aliases() default {};

    ModuleCategory category();

    /**
     * Optional second-level grouping for module browsers. Browsers fall back to the default
     * subcategory of {@link #category()} when this value is omitted or incompatible.
     */
    ModuleSubcategory subcategory() default ModuleSubcategory.UNSPECIFIED;

    /**
     * Optional addon-owned subcategory id. Addons register the id through
     * {@code CombatantAddonContext.registerModuleSubcategory(...)}. When present on an addon
     * module it takes precedence over {@link #subcategory()} for ClickGUI grouping.
     */
    String subcategoryId() default "";

    /**
     * i18n key for the module description.
     */
    String description() default "";

    boolean enabledByDefault() default false;

    /**
     * Whether the ClickGUI Modules tab lists this module (category lists and search). A module with
     * {@code listed = false} is still a regular module (keybinds, config, ModuleList HUD) and is controlled
     * from a dedicated tab instead.
     */
    boolean listed() default true;
}
