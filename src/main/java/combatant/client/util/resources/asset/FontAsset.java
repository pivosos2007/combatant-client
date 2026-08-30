/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.resources.asset;

import combatant.client.events.UsedImplicitly;
import combatant.client.render.engine.text.FontInfo;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Metadata for one enum constant in a {@link FontCatalog}. */
@UsedImplicitly
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface FontAsset {
    String value();

    String family();

    FontInfo.Type type() default FontInfo.Type.Regular;

    boolean atlasOnly() default false;

    boolean primary() default false;

    boolean prewarm() default false;

    int order() default 0;
}
