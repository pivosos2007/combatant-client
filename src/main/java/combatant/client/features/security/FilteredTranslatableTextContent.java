/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * Portions of this file are based on ExploitPreventer.
 * Original work copyright (c) 2025 Niklas S.
 *
 * Original portions remain under the MIT License.
 * Combatant modifications are licensed under GPLv3.
 * See THIRD_PARTY_NOTICES.md for details.
 */

/*
 * Portions of this file adapt protection logic from ExploitPreventer.
 * Original work copyright (c) 2025 Niklas S.
 * Licensed under MIT; see THIRD_PARTY_NOTICES.md.
 */
package combatant.client.features.security;

import net.minecraft.network.chat.contents.TranslatableContents;
import org.jspecify.annotations.Nullable;

public final class FilteredTranslatableTextContent extends TranslatableContents {

    @Nullable
    private final String resolved;

    public FilteredTranslatableTextContent(String key, @Nullable String fallback, Object[] args, @Nullable String resolved) {
        super(key, fallback, args);
        this.resolved = resolved;
    }

    @Nullable
    public String getResolved() {
        return resolved;
    }
}
