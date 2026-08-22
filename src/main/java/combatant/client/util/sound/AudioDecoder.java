/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.sound;

import java.io.IOException;
import java.io.InputStream;

interface AudioDecoder {
    PcmAudioData decode(InputStream input) throws IOException;
}
