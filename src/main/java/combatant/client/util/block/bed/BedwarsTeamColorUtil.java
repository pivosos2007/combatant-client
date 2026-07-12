/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.util.block.bed;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.scores.PlayerTeam;

public enum BedwarsTeamColorUtil {
    ;

    public static Integer getPlayerTeamRgb(Player player) {
        if (player == null) {
            return null;
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!slot.isArmor()) {
                continue;
            }

            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) {
                continue;
            }

            int color = DyedItemColor.getOrDefault(stack, -1);
            if (color != -1) {
                return color & 0x00FFFFFF;
            }
        }

        return null;
    }

    public static Integer getPlayerTeamRgb(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return null;
        }

        for (Player player : mc.level.players()) {
            String playerName = player.getGameProfile().name();
            if (playerName != null && playerName.equalsIgnoreCase(name)) {
                return getPlayerTeamRgb(player);
            }
        }

        return null;
    }

    public static Integer getPlayerTabRgb(Player player) {
        if (player == null) {
            return null;
        }

        Integer scoreboardRgb = getScoreboardTeamRgb(player.getTeam());
        if (scoreboardRgb != null) {
            return scoreboardRgb;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getConnection() == null) {
            return null;
        }

        PlayerInfo entry = mc.getConnection().getPlayerInfo(player.getUUID());
        return entry == null ? null : extractTextRgb(entry.getTabListDisplayName());
    }

    public static Integer getPlayerTabRgb(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return null;
        }

        if (mc.level != null) {
            for (Player player : mc.level.players()) {
                String playerName = player.getGameProfile().name();
                if (playerName != null && playerName.equalsIgnoreCase(name)) {
                    Integer scoreboardRgb = getScoreboardTeamRgb(player.getTeam());
                    if (scoreboardRgb != null) {
                        return scoreboardRgb;
                    }
                    break;
                }
            }
        }

        if (mc == null || mc.getConnection() == null) {
            return null;
        }

        for (PlayerInfo entry : mc.getConnection().getOnlinePlayers()) {
            if (entry == null || entry.getProfile() == null) {
                continue;
            }

            String entryName = entry.getProfile().name();
            if (entryName != null && entryName.equalsIgnoreCase(name)) {
                return extractTextRgb(entry.getTabListDisplayName());
            }
        }

        return null;
    }

    public static TeamRelation determineRelation(String name) {
        if (name == null || name.isBlank()) {
            return TeamRelation.NONE;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return TeamRelation.NONE;
        }

        String selfName = mc.player.getGameProfile().name();
        if (selfName != null && selfName.equalsIgnoreCase(name)) {
            return TeamRelation.SELF;
        }

        Integer selfTab = getPlayerTabRgb(mc.player);
        Integer otherTab = getPlayerTabRgb(name);
        if (selfTab != null && otherTab != null) {
            return selfTab.equals(otherTab) ? TeamRelation.SELF : TeamRelation.ENEMY;
        }

        Integer selfArmor = getPlayerTeamRgb(mc.player);
        Integer otherArmor = getPlayerTeamRgb(name);
        if (selfArmor != null && otherArmor != null) {
            return selfArmor.equals(otherArmor) ? TeamRelation.SELF : TeamRelation.ENEMY;
        }

        return TeamRelation.NONE;
    }

    public static Integer getBedTeamRgb(BlockState state) {
        if (state == null || !(state.getBlock() instanceof BedBlock bedBlock)) {
            return null;
        }

        return bedBlock.getColor().getTextureDiffuseColor() & 0x00FFFFFF;
    }

    public static int replaceRgb(int argb, int rgb) {
        return (argb & 0xFF000000) | (rgb & 0x00FFFFFF);
    }

    private static Integer extractTextRgb(Component text) {
        if (text == null) {
            return null;
        }

        if (text.getStyle() != null && text.getStyle().getColor() != null) {
            return text.getStyle().getColor().getValue() & 0x00FFFFFF;
        }

        for (Component sibling : text.getSiblings()) {
            Integer siblingRgb = extractTextRgb(sibling);
            if (siblingRgb != null) {
                return siblingRgb;
            }
        }

        return null;
    }

    private static Integer getScoreboardTeamRgb(PlayerTeam team) {
        if (team == null || team.getColor().isEmpty()) {
            return null;
        }

        return team.getColor().map(net.minecraft.world.scores.TeamColor::rgb).orElse(null);
    }

    public enum TeamRelation {
        SELF,
        ENEMY,
        NONE
    }
}
