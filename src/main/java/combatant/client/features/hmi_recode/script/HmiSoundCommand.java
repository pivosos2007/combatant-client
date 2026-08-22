/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

/* HoldMyItems Recode; original project by sapling, CC0-1.0. */
package combatant.client.features.hmi_recode.script;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Sound requests emitted by HMI resource scripts. */
public record HmiSoundCommand(String id, float volume) {
    public static List<HmiSoundCommand> decode(Object raw) {
        if (!(raw instanceof Iterable<?> iterable)) return List.of();
        List<HmiSoundCommand> out = new ArrayList<>();
        for (Object entry : iterable) {
            if (entry instanceof List<?> packed && !packed.isEmpty() && packed.get(0) instanceof String name) {
                if (name.isBlank()) continue;
                float volume = packed.size() > 1 && packed.get(1) instanceof Number number
                        ? number.floatValue() : 1.0f;
                out.add(new HmiSoundCommand(name, volume));
                continue;
            }
            if (!(entry instanceof Map<?, ?> map)) continue;
            Object id = map.get("id");
            if (!(id instanceof String name) || name.isBlank()) continue;
            float volume = map.get("volume") instanceof Number number ? number.floatValue() : 1.0f;
            out.add(new HmiSoundCommand(name, volume));
        }
        return out;
    }

    public void play(LocalPlayer player) {
        if (player == null) return;
        Identifier soundId = Identifier.tryParse(id);
        if (soundId == null) return;
        player.playSound(SoundEvent.createVariableRangeEvent(soundId), volume, 1.0f);
    }
}
