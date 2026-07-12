/*
 * This file is part of the Combatant Client distribution.
 * Combatant modifications copyright (c) 2026 pivosos2007.
 *
 * This file belongs to Combatant's MediaPlayerInfo integration, based on
 * https://github.com/Redstonecrafter0/MediaPlayerInfo.
 * Copyright (c) Redstonecrafter0 and contributors.
 *
 * Licensed under the GNU Affero General Public License v3.0.
 * See THIRD_PARTY_NOTICES.md for details.
 */

package combatant.client.util.media.impl;

import combatant.client.util.media.IMediaSession;
import combatant.client.util.media.MediaPlayerInfo;

import java.util.List;

public final class DummyMediaPlayerInfo implements MediaPlayerInfo {
    public static final DummyMediaPlayerInfo INSTANCE = new DummyMediaPlayerInfo();

    private DummyMediaPlayerInfo() {
    }

    @Override
    public List<IMediaSession> getMediaSessions() {
        return List.of();
    }
}
