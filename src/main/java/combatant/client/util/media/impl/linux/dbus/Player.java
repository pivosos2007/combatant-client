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

package combatant.client.util.media.impl.linux.dbus;

import org.freedesktop.dbus.ObjectPath;
import org.freedesktop.dbus.TypeRef;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusProperty;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.Variant;

import java.util.Map;

/**
 * Modified Java port for Combatant by combatant.client.
 * Based on Redstonecrafter0/MediaPlayerInfo.
 */
@DBusInterfaceName("org.mpris.MediaPlayer2.Player")
@DBusProperty(name = "Metadata", type = Player.PropertyMetadataType.class, access = DBusProperty.Access.READ)
@DBusProperty(name = "PlaybackStatus", type = String.class, access = DBusProperty.Access.READ)
@DBusProperty(name = "LoopStatus", type = String.class, access = DBusProperty.Access.READ_WRITE)
@DBusProperty(name = "Volume", type = Double.class, access = DBusProperty.Access.READ_WRITE)
@DBusProperty(name = "Shuffle", type = Boolean.class, access = DBusProperty.Access.READ_WRITE)
@DBusProperty(name = "Position", type = Integer.class, access = DBusProperty.Access.READ)
@DBusProperty(name = "Rate", type = Double.class, access = DBusProperty.Access.READ_WRITE)
@DBusProperty(name = "MinimumRate", type = Double.class, access = DBusProperty.Access.READ_WRITE)
@DBusProperty(name = "MaximumRate", type = Double.class, access = DBusProperty.Access.READ_WRITE)
@DBusProperty(name = "CanControl", type = Boolean.class, access = DBusProperty.Access.READ)
@DBusProperty(name = "CanPlay", type = Boolean.class, access = DBusProperty.Access.READ)
@DBusProperty(name = "CanPause", type = Boolean.class, access = DBusProperty.Access.READ)
@DBusProperty(name = "CanSeek", type = Boolean.class, access = DBusProperty.Access.READ)
public interface Player extends DBusInterface {
    void Play();

    void Pause();

    void PlayPause();

    void Stop();

    void Next();

    void Previous();

    void Seek(long offset);

    void SetPosition(ObjectPath trackId, long position);

    interface PropertyMetadataType extends TypeRef<Map<String, Variant<?>>> {
    }
}
