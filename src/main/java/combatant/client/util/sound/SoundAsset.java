/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Metadata for one constant in a {@link SoundCatalog}. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SoundAsset {
    /** File name relative to {@link SoundCatalog#root()}, including .wav or .ogg. */
    String value();

    /** Optional logical id. The lower-case enum constant name is used by default. */
    String id() default "";

    float gain() default 1.0f;

    float pitch() default 1.0f;

    boolean looping() default false;

    boolean spatial() default false;

    float rolloff() default 1.0f;

    float referenceDistance() default 1.0f;

    float maxDistance() default 64.0f;
}
