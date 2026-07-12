/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.relations;

import net.minecraft.world.entity.player.Player;
import combatant.client.features.module.Modules;
import combatant.client.features.module.modules.misc.DefineTarget;
import combatant.client.util.block.bed.BedwarsTeamColorUtil;

public enum CategoryService {
    ;

    public static CategoryType get(Player player) {
        if (player == null) return CategoryType.DEFAULT;
        return CategoryRules.determine(player.getGameProfile().name());
    }

    public static CategoryType get(String name) {
        return CategoryRules.determine(name);
    }

    public static int getColor(Player player) {
        return getBedwarsAwareColor(player, get(player));
    }

    public static int getColor(String name) {
        return getBedwarsAwareColor(name, get(name));
    }

    public static boolean isFriend(Player p) {
        CategoryType type = get(p);
        return type == CategoryType.FRIEND || type == CategoryType.BEDWARS_SELF;
    }

    public static boolean isEnemy(Player p) {
        CategoryType type = get(p);
        return type == CategoryType.ENEMY || type == CategoryType.BEDWARS_ENEMY;
    }

    public static boolean isStaff(Player p) {
        return get(p) == CategoryType.STAFF;
    }

    private static int toColor(CategoryType type) {
        PlayerRelations rel = PlayerRelations.get();
        return switch (type) {
            case STAFF -> rel.colorStaff();
            case BEDWARS_SELF -> rel.colorBedwarsSelf();
            case FRIEND -> rel.colorFriend();
            case BEDWARS_ENEMY -> rel.colorBedwarsEnemy();
            case ENEMY -> rel.colorEnemy();
            default -> rel.colorDefault();
        };
    }

    public static int getBedwarsAwareColor(Player player, CategoryType type) {
        if (type == null) {
            return PlayerRelations.get().colorDefault();
        }

        DefineTarget defineTarget = Modules.get(DefineTarget.class);
        if (defineTarget != null && defineTarget.useBedwarsTeamColors()) {
            if (type == CategoryType.BEDWARS_SELF || type == CategoryType.BEDWARS_ENEMY) {
                Integer rgb = BedwarsTeamColorUtil.getPlayerTeamRgb(player);
                if (rgb != null) {
                    return 0xFF000000 | rgb;
                }
            }
        }

        return toColor(type);
    }

    public static int getBedwarsAwareColor(String name, CategoryType type) {
        if (type == null) {
            return PlayerRelations.get().colorDefault();
        }

        DefineTarget defineTarget = Modules.get(DefineTarget.class);
        if (defineTarget != null && defineTarget.useBedwarsTeamColors()) {
            if (type == CategoryType.BEDWARS_SELF || type == CategoryType.BEDWARS_ENEMY) {
                Integer rgb = BedwarsTeamColorUtil.getPlayerTeamRgb(name);
                if (rgb != null) {
                    return 0xFF000000 | rgb;
                }
            }
        }

        return toColor(type);
    }
}
