/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.theme;

import net.minecraft.util.Util;
import combatant.client.render.engine.animation.AnimationUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

public enum Themes {
    ;

    public static final String THEME_CLASSIC = "classic";
    public static final String THEME_LEGACY = "legacy";
    public static final String THEME_AMBER = "amber";
    public static final String THEME_NOIR = "noir";
    public static final String THEME_PURPLE = "purple";
    public static final String THEME_BLUE = "blue";
    public static final String THEME_AZURE = "azure";
    public static final String THEME_NITRO = "nitro";
    public static final String THEME_SUNSET = "sunset";
    public static final String THEME_AURORA = "aurora";
    public static final String THEME_MINT = "mint";
    public static final String THEME_PEACH = "peach";
    public static final String THEME_LILAC = "lilac";
    public static final String THEME_SAND = "sand";
    public static final String THEME_DUSK = "dusk";
    public static final String THEME_PRISM = "prism";
    public static final String THEME_CINDER = "cinder";
    public static final String THEME_FOREST = "forest";
    public static final String THEME_SPEARMINT = "spearmint";
    public static final String THEME_JADE_GREEN = "jade_green";
    public static final String THEME_GREEN_SPIRIT = "green_spirit";
    public static final String THEME_ROSY_PINK = "rosy_pink";
    public static final String THEME_MAGENTA = "magenta";
    public static final String THEME_HOT_PINK = "hot_pink";
    public static final String THEME_LAVENDER = "lavender";
    public static final String THEME_AMETHYST = "amethyst";
    public static final String THEME_PURPLE_FIRE = "purple_fire";
    public static final String THEME_SUNSET_PINK = "sunset_pink";
    public static final String THEME_BLAZE_ORANGE = "blaze_orange";
    public static final String THEME_PINK_BLOOD = "pink_blood";
    public static final String THEME_PASTEL = "pastel";
    public static final String THEME_NEON_RED = "neon_red";
    public static final String THEME_RED_COFFEE = "red_coffee";
    public static final String THEME_DEEP_OCEAN = "deep_ocean";
    public static final String THEME_CHAMBRAY_BLUE = "chambray_blue";
    public static final String THEME_MINT_BLUE = "mint_blue";
    public static final String THEME_PACIFIC_BLUE = "pacific_blue";
    public static final String THEME_TROPICAL_ICE = "tropical_ice";
    private static final Theme CLASSIC = new Theme(
            0xF012191B, // window bg (cooler, unchanged)
            0xF0142226, // header darker
            0x60203030, // window stroke
            0x16202523, // surface for controls
            0x28353A3E, // surface hover
            0x5043A7C8, // card enabled overlay
            0x334046,   // card disabled
            0xFFFFFFFF, // text primary
            0x88C6D2CD, // text muted
            0xFF5CC8E7, // accent
            0x805CC8E7, // accent soft
            0x60FFFFFF  // stroke soft
    );
    private static final Theme LEGACY = new Theme(
            0xF0121314, // window bg (old fixed style)
            0xF0000205, // header
            0xE1121314, // stroke
            0xFF171819, // surface
            0xFF131315, // surface hover
            0x503F464D, // card enabled overlay
            0x33191C20, // card disabled
            0xFFF5F5FF, // text primary
            0x88A5A5A5, // text muted
            0xFF767C94, // accent
            0x80767C94, // accent soft
            0x60373746  // stroke soft
    );
    private static final Theme AMBER = new Theme(
            0xF019120D, // window bg (warm)
            0xF0201711, // header
            0x60C3652C, // stroke
            0xFF221711, // surface
            0xFF2B1E16, // hover
            0x50E08C3A, // card enabled overlay
            0x40302620, // card disabled
            0xFFFFF7EB, // text primary
            0x88E3C9A7, // text muted
            0xFFE69A43, // accent
            0x80E69A43, // accent soft
            0x60F5D7B0  // stroke soft
    );
    private static final Theme NOIR = new Theme(
            0xF00C0D10, // window bg (deep charcoal)
            0xF0121317, // header
            0x60393F4A, // stroke
            0xFF14161B, // surface
            0xFF1C1F26, // hover
            0x5039424F, // card enabled overlay
            0x40282D35, // card disabled
            0xFFF4F4F4, // text primary
            0x88C7CBD3, // text muted
            0xFFE16B78, // accent
            0x80E16B78, // accent soft
            0x605A6370  // stroke soft
    );
    private static final Theme PURPLE = new Theme(
            0xF0120E1A, // window bg (purple)
            0xF0181323, // header
            0x60433A5A, // stroke
            0xFF1C1627, // surface
            0xFF261D35, // hover
            0x505E4AA0, // card enabled overlay
            0x40312644, // card disabled
            0xFFF5F2FF, // text primary
            0x88CFC6E6, // text muted
            0xFF9B6BFF, // accent
            0x809B6BFF, // accent soft
            0x60705A9C  // stroke soft
    );
    private static final Theme BLUE = new Theme(
            0xF0080F1E, // window bg (deep blue)
            0xF00D1626, // header
            0x60405A82, // stroke
            0xFF101B2B, // surface
            0xFF162338, // hover
            0x50406BB3, // card enabled overlay
            0x4023324A, // card disabled
            0xFFF0F5FF, // text primary
            0x88B2C4E6, // text muted
            0xFF2E64CC, // accent
            0x803A6FD6, // accent soft
            0x60465F8E  // stroke soft
    );
    private static final Theme AZURE = new Theme(
            0xF00C1220, // window bg (blue)
            0xF0101A2A, // header
            0x60324B66, // stroke
            0xFF121C2C, // surface
            0xFF18253A, // hover
            0x503F6BAA, // card enabled overlay
            0x40283348, // card disabled
            0xFFF2F7FF, // text primary
            0x88BBD0EA, // text muted
            0xFF5DA7FF, // accent
            0x805DA7FF, // accent soft
            0x60507CA8  // stroke soft
    );
    private static final Theme NITRO = new Theme(
            0xFF121624, // window bg
            0xFF181E33, // header
            0x60465580, // stroke
            0xFF1A1F36, // surface
            0xFF232A45, // hover
            0x50A24CFF, // card enabled overlay
            0x40303A52, // card disabled
            0xFFF3F6FF, // text primary
            0x88C7D3F3, // text muted
            0xFF7A5CFF, // accent
            0x807A5CFF, // accent soft
            0x60829AC9  // stroke soft
    );
    private static final Theme SUNSET = new Theme(
            0xFF1A1116, // window bg
            0xFF211319, // header
            0x605C2B2B, // stroke
            0xFF24151C, // surface
            0xFF2E1A23, // hover
            0x50FF7A45, // card enabled overlay
            0x40261A1A, // card disabled
            0xFFFFF2E8, // text primary
            0x88E3B9A3, // text muted
            0xFFFF7A45, // accent
            0x80FF7A45, // accent soft
            0x60F5C1A6  // stroke soft
    );
    private static final Theme AURORA = new Theme(
            0xFF0D1A18, // window bg
            0xFF102320, // header
            0x602B5B57, // stroke
            0xFF132623, // surface
            0xFF1A312D, // hover
            0x5034D399, // card enabled overlay
            0x40304742, // card disabled
            0xFFF1FFF9, // text primary
            0x8897E3D0, // text muted
            0xFF2DD9C3, // accent
            0x802DD9C3, // accent soft
            0x6071B5A6  // stroke soft
    );
    private static final Theme MINT = new Theme(
            0xFF0E1917, // window bg
            0xFF112320, // header
            0x60255B53, // stroke
            0xFF132521, // surface
            0xFF1A2F2B, // hover
            0x5035CFA8, // card enabled overlay
            0x40304742, // card disabled
            0xFFF1FFF9, // text primary
            0x8897E3D0, // text muted
            0xFF43E0B2, // accent
            0x8043E0B2, // accent soft
            0x6071B5A6  // stroke soft
    );
    private static final Theme PEACH = new Theme(
            0xFF1A1210, // window bg
            0xFF221510, // header
            0x605C3A2A, // stroke
            0xFF241814, // surface
            0xFF2F211A, // hover
            0x50F2A269, // card enabled overlay
            0x40382A22, // card disabled
            0xFFFFF7F0, // text primary
            0x88E3C9A7, // text muted
            0xFFFFA46B, // accent
            0x80FFB680, // accent soft
            0x60F5D7B0  // stroke soft
    );
    private static final Theme LILAC = new Theme(
            0xFF14131E, // window bg
            0xFF1A1728, // header
            0x60433A5A, // stroke
            0xFF1B172A, // surface
            0xFF241D36, // hover
            0x506C5CBE, // card enabled overlay
            0x40312644, // card disabled
            0xFFF5F2FF, // text primary
            0x88CFC6E6, // text muted
            0xFFB79BFF, // accent
            0x80C8B0FF, // accent soft
            0x60705A9C  // stroke soft
    );
    private static final Theme SAND = new Theme(
            0xFF181510, // window bg
            0xFF201A13, // header
            0x6050432F, // stroke
            0xFF221B14, // surface
            0xFF2C231B, // hover
            0x50E8C98C, // card enabled overlay
            0x40322A21, // card disabled
            0xFFFFF4E8, // text primary
            0x88D8C4A2, // text muted
            0xFFE8C98C, // accent
            0x80F0D8A8, // accent soft
            0x607A6A54  // stroke soft
    );
    private static final Theme DUSK = new Theme(
            0xFF141018, // window bg
            0xFF1B1422, // header
            0x60503A4A, // stroke
            0xFF1D1626, // surface
            0xFF271B34, // hover
            0x50FF875A, // card enabled overlay
            0x4030263B, // card disabled
            0xFFF6F1FF, // text primary
            0x88D6C9E6, // text muted
            0xFFFF8A4C, // accent
            0x80FF9B5A, // accent soft
            0x606B5A7A  // stroke soft
    );
    private static final Theme PRISM = new Theme(
            0xFF0F1220, // window bg
            0xFF141A2B, // header
            0x60435173, // stroke
            0xFF151C2E, // surface
            0xFF1B243A, // hover
            0x504B7CFF, // card enabled overlay
            0x4031324A, // card disabled
            0xFFF3F6FF, // text primary
            0x88C2CDEB, // text muted
            0xFF5AC8FA, // accent
            0x8074B6FF, // accent soft
            0x606B7A9E  // stroke soft
    );
    private static final Theme CINDER = new Theme(
            0xFF120E0F, // window bg
            0xFF181012, // header
            0x604A2F32, // stroke
            0xFF1A1214, // surface
            0xFF23171A, // hover
            0x50D24A4A, // card enabled overlay
            0x40281A1C, // card disabled
            0xFFFFF3F3, // text primary
            0x88E3B9B9, // text muted
            0xFFDC4A4A, // accent
            0x80E06A6A, // accent soft
            0x606B4E52  // stroke soft
    );
    private static final Theme FOREST = new Theme(
            0xFF101611, // window bg
            0xFF141E15, // header
            0x6043573D, // stroke
            0xFF161F18, // surface
            0xFF1D2B20, // hover
            0x5071C36C, // card enabled overlay
            0x40263225, // card disabled
            0xFFF3FFF1, // text primary
            0x88C5E3C1, // text muted
            0xFF7DD36B, // accent
            0x807DD36B, // accent soft
            0x60607B58  // stroke soft
    );
    private static final ThemeEntry CLASSIC_ENTRY = new ThemeEntry(
            THEME_CLASSIC,
            "Classic",
            true,
            CLASSIC,
            flatGradient(CLASSIC.windowBg()),
            subtleHeaderGradient(CLASSIC.windowHeader()),
            flatGradient(CLASSIC.surface()),
            flatGradient(CLASSIC.cardEnabled()),
            flatGradient(CLASSIC.windowStroke())
    );
    private static ThemeEntry currentEntry = CLASSIC_ENTRY;
    private static ThemeEntry transitionFromEntry = CLASSIC_ENTRY;
    private static ThemeEntry animatedEntryCache = CLASSIC_ENTRY;
    private static GradientSpec transitionFromHudAccentGradient = resolveHudAccentGradient(CLASSIC_ENTRY, CLASSIC);
    private static GradientSpec transitionFromHudForegroundGradient = resolveHudForegroundGradient(CLASSIC_ENTRY, CLASSIC);
    private static GradientSpec transitionFromHudSelectionGradient = resolveHudSelectionGradient(CLASSIC_ENTRY, CLASSIC);
    private static GradientSpec transitionFromHudPanelGradient = resolveHudPanelGradient(CLASSIC_ENTRY, CLASSIC);
    private static GradientSpec transitionFromHudBackgroundGradient = resolveHudBackgroundGradient(CLASSIC_ENTRY, CLASSIC);
    private static GradientSpec transitionFromHudShadowGradient = resolveHudShadowGradient(CLASSIC_ENTRY, CLASSIC);
    private static final ThemeEntry LEGACY_ENTRY = new ThemeEntry(
            THEME_LEGACY,
            "Legacy",
            true,
            LEGACY,
            flatGradient(LEGACY.windowBg()),
            subtleHeaderGradient(LEGACY.windowHeader()),
            flatGradient(LEGACY.surface()),
            flatGradient(LEGACY.cardEnabled()),
            flatGradient(LEGACY.windowStroke())
    );

    private static final ThemeEntry AMBER_ENTRY = new ThemeEntry(
            THEME_AMBER,
            "Amber",
            true,
            AMBER,
            flatGradient(AMBER.windowBg()),
            subtleHeaderGradient(AMBER.windowHeader()),
            flatGradient(AMBER.surface()),
            flatGradient(AMBER.cardEnabled()),
            flatGradient(AMBER.windowStroke())
    );

    private static final ThemeEntry NOIR_ENTRY = new ThemeEntry(
            THEME_NOIR,
            "Noir",
            true,
            NOIR,
            flatGradient(NOIR.windowBg()),
            subtleHeaderGradient(NOIR.windowHeader()),
            flatGradient(NOIR.surface()),
            flatGradient(NOIR.cardEnabled()),
            flatGradient(NOIR.windowStroke())
    );

    private static final ThemeEntry PURPLE_ENTRY = new ThemeEntry(
            THEME_PURPLE,
            "Purple",
            true,
            PURPLE,
            flatGradient(PURPLE.windowBg()),
            subtleHeaderGradient(PURPLE.windowHeader()),
            flatGradient(PURPLE.surface()),
            flatGradient(PURPLE.cardEnabled()),
            flatGradient(PURPLE.windowStroke())
    );

    private static final ThemeEntry BLUE_ENTRY = new ThemeEntry(
            THEME_BLUE,
            "Blue",
            true,
            BLUE,
            flatGradient(BLUE.windowBg()),
            subtleHeaderGradient(BLUE.windowHeader()),
            flatGradient(BLUE.surface()),
            flatGradient(BLUE.cardEnabled()),
            flatGradient(BLUE.windowStroke())
    );

    private static final ThemeEntry AZURE_ENTRY = new ThemeEntry(
            THEME_AZURE,
            "Azure",
            true,
            AZURE,
            flatGradient(AZURE.windowBg()),
            subtleHeaderGradient(AZURE.windowHeader()),
            flatGradient(AZURE.surface()),
            flatGradient(AZURE.cardEnabled()),
            flatGradient(AZURE.windowStroke())
    );

    private static final ThemeEntry NITRO_ENTRY = new ThemeEntry(
            THEME_NITRO,
            "Nitro",
            true,
            NITRO,
            new GradientSpec(true, 0xFF36205F, 0xFF0B7DFF, 45f),
            new GradientSpec(true, 0xFF4B2FAF, 0xFF24C1FF, 45f),
            new GradientSpec(true, 0xFF1A1F36, 0xFF121728, 90f),
            new GradientSpec(true, 0xFF5A37D5, 0xFF2AD1FF, 45f),
            new GradientSpec(true, 0xFF6C4BFF, 0xFF4BD0FF, 45f)
    );

    private static final ThemeEntry SUNSET_ENTRY = new ThemeEntry(
            THEME_SUNSET,
            "Sunset",
            true,
            SUNSET,
            new GradientSpec(true, 0xFF2A1020, 0xFFB2431F, 45f),
            new GradientSpec(true, 0xFF3B1626, 0xFFC25B2A, 45f),
            new GradientSpec(true, 0xFF24151C, 0xFF1E1116, 90f),
            new GradientSpec(true, 0xFFFF8A4C, 0xFFFF4B7A, 45f),
            new GradientSpec(true, 0xFFFFB27A, 0xFFFF6A3A, 45f)
    );

    private static final ThemeEntry AURORA_ENTRY = new ThemeEntry(
            THEME_AURORA,
            "Aurora",
            true,
            AURORA,
            new GradientSpec(true, 0xFF0B2A3C, 0xFF2A8E6B, 120f),
            new GradientSpec(true, 0xFF0E3B46, 0xFF1FB88B, 120f),
            new GradientSpec(true, 0xFF132623, 0xFF10201D, 90f),
            new GradientSpec(true, 0xFF2DD9C3, 0xFF5A7CFF, 135f),
            new GradientSpec(true, 0xFF78FFE5, 0xFF6FB1FF, 120f)
    );

    private static final ThemeEntry MINT_ENTRY = new ThemeEntry(
            THEME_MINT,
            "Mint",
            true,
            MINT,
            new GradientSpec(true, 0xFF0B2A24, 0xFF1B3E36, 120f),
            new GradientSpec(true, 0xFF0F3A32, 0xFF1A6E59, 120f),
            new GradientSpec(true, 0xFF132521, 0xFF0F1E1B, 90f),
            new GradientSpec(true, 0xFF3DE3B4, 0xFFB7F2D9, 45f),
            new GradientSpec(true, 0xFF48E6B9, 0xFF7EF0D6, 120f)
    );

    private static final ThemeEntry PEACH_ENTRY = new ThemeEntry(
            THEME_PEACH,
            "Peach",
            true,
            PEACH,
            new GradientSpec(true, 0xFF2A1512, 0xFF4B241A, 45f),
            new GradientSpec(true, 0xFF3B1B14, 0xFF6A301F, 45f),
            new GradientSpec(true, 0xFF241814, 0xFF1C1310, 90f),
            new GradientSpec(true, 0xFFFFB47A, 0xFFFF7FA8, 45f),
            new GradientSpec(true, 0xFFFFC28E, 0xFFFF8A5A, 45f)
    );

    private static final ThemeEntry LILAC_ENTRY = new ThemeEntry(
            THEME_LILAC,
            "Lilac",
            true,
            LILAC,
            new GradientSpec(true, 0xFF241A3B, 0xFF1A3A55, 120f),
            new GradientSpec(true, 0xFF2F214A, 0xFF3D2F6A, 120f),
            new GradientSpec(true, 0xFF1B172A, 0xFF15121F, 90f),
            new GradientSpec(true, 0xFFB79BFF, 0xFF7AD9FF, 45f),
            new GradientSpec(true, 0xFFC5B0FF, 0xFF8BB2FF, 120f)
    );

    private static final ThemeEntry SAND_ENTRY = new ThemeEntry(
            THEME_SAND,
            "Sand",
            true,
            SAND,
            new GradientSpec(true, 0xFF2A2016, 0xFF3A2F22, 45f),
            new GradientSpec(true, 0xFF32261B, 0xFF4A3A2B, 45f),
            new GradientSpec(true, 0xFF221B14, 0xFF1A1510, 90f),
            new GradientSpec(true, 0xFFF2D29B, 0xFFE8B67A, 45f),
            new GradientSpec(true, 0xFFF5E0B2, 0xFFD1A36C, 45f)
    );

    private static final ThemeEntry DUSK_ENTRY = new ThemeEntry(
            THEME_DUSK,
            "Dusk",
            true,
            DUSK,
            new GradientSpec(true, 0xFF2B1630, 0xFF6B2A1F, 45f),
            new GradientSpec(true, 0xFF3B1B3A, 0xFF8A3B24, 45f),
            new GradientSpec(true, 0xFF1D1626, 0xFF141018, 90f),
            new GradientSpec(true, 0xFF7C4BFF, 0xFFFF9B3D, 45f),
            new GradientSpec(true, 0xFFA775FF, 0xFFFF7A3A, 45f)
    );

    private static final ThemeEntry PRISM_ENTRY = new ThemeEntry(
            THEME_PRISM,
            "Prism",
            true,
            PRISM,
            new GradientSpec(true, 0xFF1A243A, 0xFF2A1E4A, 135f),
            new GradientSpec(true, 0xFF223050, 0xFF352060, 135f),
            new GradientSpec(true, 0xFF151C2E, 0xFF101522, 90f),
            new GradientSpec(true, 0xFF59C4FF, 0xFFB35CFF, 45f),
            new GradientSpec(true, 0xFF7AD4FF, 0xFFE36BFF, 45f)
    );

    private static final ThemeEntry CINDER_ENTRY = new ThemeEntry(
            THEME_CINDER,
            "Cinder",
            true,
            CINDER,
            new GradientSpec(true, 0xFF1A0C0C, 0xFF2A1416, 45f),
            new GradientSpec(true, 0xFF241012, 0xFF3A191C, 45f),
            new GradientSpec(true, 0xFF1A1214, 0xFF120E0F, 90f),
            new GradientSpec(true, 0xFFDC4A4A, 0xFF401010, 45f),
            new GradientSpec(true, 0xFFE26A6A, 0xFF7A2A2A, 45f)
    );

    private static final ThemeEntry FOREST_ENTRY = new ThemeEntry(
            THEME_FOREST,
            "Forest",
            true,
            FOREST,
            new GradientSpec(true, 0xFF1A241C, 0xFF2B3A24, 120f),
            new GradientSpec(true, 0xFF1F2B21, 0xFF2F4B2E, 120f),
            new GradientSpec(true, 0xFF161F18, 0xFF101611, 90f),
            new GradientSpec(true, 0xFF7DD36B, 0xFFC6E886, 45f),
            new GradientSpec(true, 0xFF9BE07C, 0xFF6BC26A, 45f)
    );

    // Tenacity 7 palette ports. The original two-color palette is preserved in
    // accent/accentSoft; its gradient flag controls whether the semantic UI
    // surfaces also receive a two-tone gradient treatment.
    private static final ThemeEntry SPEARMINT_ENTRY = pairedPaletteEntry(
            THEME_SPEARMINT, "Spearmint", 0xFF61C2A2, 0xFF41826C, false
    );
    private static final ThemeEntry JADE_GREEN_ENTRY = pairedPaletteEntry(
            THEME_JADE_GREEN, "Jade Green", 0xFF00A86B, 0xFF006942, false
    );
    private static final ThemeEntry GREEN_SPIRIT_ENTRY = pairedPaletteEntry(
            THEME_GREEN_SPIRIT, "Green Spirit", 0xFF00873E, 0xFF9FE2BF, true
    );
    private static final ThemeEntry ROSY_PINK_ENTRY = pairedPaletteEntry(
            THEME_ROSY_PINK, "Rosy Pink", 0xFFFF66CC, 0xFFBF4D99, false
    );
    private static final ThemeEntry MAGENTA_ENTRY = pairedPaletteEntry(
            THEME_MAGENTA, "Magenta", 0xFFD53F77, 0xFF9D446E, false
    );
    private static final ThemeEntry HOT_PINK_ENTRY = pairedPaletteEntry(
            THEME_HOT_PINK, "Hot Pink", 0xFFE75480, 0xFFAC4FC6, true
    );
    private static final ThemeEntry LAVENDER_ENTRY = pairedPaletteEntry(
            THEME_LAVENDER, "Lavender", 0xFFDBA6F7, 0xFF9873AC, false
    );
    private static final ThemeEntry AMETHYST_ENTRY = pairedPaletteEntry(
            THEME_AMETHYST, "Amethyst", 0xFF9063CD, 0xFF62438C, false
    );
    private static final ThemeEntry PURPLE_FIRE_ENTRY = pairedPaletteEntry(
            THEME_PURPLE_FIRE, "Purple Fire", 0xFF68478D, 0xFFB1A2CA, true
    );
    private static final ThemeEntry SUNSET_PINK_ENTRY = pairedPaletteEntry(
            THEME_SUNSET_PINK, "Sunset Pink", 0xFFFF9114, 0xFFF569E7, true
    );
    private static final ThemeEntry BLAZE_ORANGE_ENTRY = pairedPaletteEntry(
            THEME_BLAZE_ORANGE, "Blaze Orange", 0xFFFFA94D, 0xFFFF8200, false
    );
    private static final ThemeEntry PINK_BLOOD_ENTRY = pairedPaletteEntry(
            THEME_PINK_BLOOD, "Pink Blood", 0xFFE40046, 0xFFFFA6C9, true
    );
    private static final ThemeEntry PASTEL_ENTRY = pairedPaletteEntry(
            THEME_PASTEL, "Pastel", 0xFFFF6D6A, 0xFFBF5250, false
    );
    private static final ThemeEntry NEON_RED_ENTRY = pairedPaletteEntry(
            THEME_NEON_RED, "Neon Red", 0xFFD22730, 0xFFB8192A, false
    );
    private static final ThemeEntry RED_COFFEE_ENTRY = pairedPaletteEntry(
            // Black is the identity/background half of this palette. Using it as
            // the semantic accent would make selected states disappear on the
            // dark UI, so the red half is intentionally promoted to accent.
            THEME_RED_COFFEE, "Red Coffee", 0xFFE1223B, 0xFF000000, false
    );
    private static final ThemeEntry DEEP_OCEAN_ENTRY = pairedPaletteEntry(
            THEME_DEEP_OCEAN, "Deep Ocean", 0xFF3C5291, 0xFF001440, true
    );
    private static final ThemeEntry CHAMBRAY_BLUE_ENTRY = pairedPaletteEntry(
            THEME_CHAMBRAY_BLUE, "Chambray Blue", 0xFF212EB6, 0xFF3C5291, false
    );
    private static final ThemeEntry MINT_BLUE_ENTRY = pairedPaletteEntry(
            THEME_MINT_BLUE, "Mint Blue", 0xFF429E9D, 0xFF285E5D, false
    );
    private static final ThemeEntry PACIFIC_BLUE_ENTRY = pairedPaletteEntry(
            THEME_PACIFIC_BLUE, "Pacific Blue", 0xFF05A9C7, 0xFF047387, false
    );
    private static final ThemeEntry TROPICAL_ICE_ENTRY = pairedPaletteEntry(
            THEME_TROPICAL_ICE, "Tropical Ice", 0xFF66FFD1, 0xFF0695FF, true
    );

    private static final Map<String, ThemeEntry> PRESET_ENTRIES = Map.ofEntries(
            Map.entry(THEME_CLASSIC, CLASSIC_ENTRY),
            Map.entry(THEME_LEGACY, LEGACY_ENTRY),
            Map.entry(THEME_AMBER, AMBER_ENTRY),
            Map.entry(THEME_NOIR, NOIR_ENTRY),
            Map.entry(THEME_PURPLE, PURPLE_ENTRY),
            Map.entry(THEME_BLUE, BLUE_ENTRY),
            Map.entry(THEME_AZURE, AZURE_ENTRY),
            Map.entry(THEME_NITRO, NITRO_ENTRY),
            Map.entry(THEME_SUNSET, SUNSET_ENTRY),
            Map.entry(THEME_AURORA, AURORA_ENTRY),
            Map.entry(THEME_MINT, MINT_ENTRY),
            Map.entry(THEME_PEACH, PEACH_ENTRY),
            Map.entry(THEME_LILAC, LILAC_ENTRY),
            Map.entry(THEME_SAND, SAND_ENTRY),
            Map.entry(THEME_DUSK, DUSK_ENTRY),
            Map.entry(THEME_PRISM, PRISM_ENTRY),
            Map.entry(THEME_CINDER, CINDER_ENTRY),
            Map.entry(THEME_FOREST, FOREST_ENTRY),
            Map.entry(THEME_SPEARMINT, SPEARMINT_ENTRY),
            Map.entry(THEME_JADE_GREEN, JADE_GREEN_ENTRY),
            Map.entry(THEME_GREEN_SPIRIT, GREEN_SPIRIT_ENTRY),
            Map.entry(THEME_ROSY_PINK, ROSY_PINK_ENTRY),
            Map.entry(THEME_MAGENTA, MAGENTA_ENTRY),
            Map.entry(THEME_HOT_PINK, HOT_PINK_ENTRY),
            Map.entry(THEME_LAVENDER, LAVENDER_ENTRY),
            Map.entry(THEME_AMETHYST, AMETHYST_ENTRY),
            Map.entry(THEME_PURPLE_FIRE, PURPLE_FIRE_ENTRY),
            Map.entry(THEME_SUNSET_PINK, SUNSET_PINK_ENTRY),
            Map.entry(THEME_BLAZE_ORANGE, BLAZE_ORANGE_ENTRY),
            Map.entry(THEME_PINK_BLOOD, PINK_BLOOD_ENTRY),
            Map.entry(THEME_PASTEL, PASTEL_ENTRY),
            Map.entry(THEME_NEON_RED, NEON_RED_ENTRY),
            Map.entry(THEME_RED_COFFEE, RED_COFFEE_ENTRY),
            Map.entry(THEME_DEEP_OCEAN, DEEP_OCEAN_ENTRY),
            Map.entry(THEME_CHAMBRAY_BLUE, CHAMBRAY_BLUE_ENTRY),
            Map.entry(THEME_MINT_BLUE, MINT_BLUE_ENTRY),
            Map.entry(THEME_PACIFIC_BLUE, PACIFIC_BLUE_ENTRY),
            Map.entry(THEME_TROPICAL_ICE, TROPICAL_ICE_ENTRY)
    );

    private static final ThemeStore STORE = ThemeStore.get();

    private static final long THEME_TRANSITION_MS = 340L;

    private static Theme current = CLASSIC;
    private static String currentId = THEME_CLASSIC;
    private static Theme transitionFromTheme = CLASSIC;
    private static long transitionStartMs = 0L;
    private static boolean transitionActive = false;
    private static Theme animatedThemeCache = CLASSIC;
    private static int animatedCacheKey = -1;

    static {
        applyThemeFromStore();
    }

    static Theme theme() {
        return animatedTheme();
    }

    static ThemeEntry currentEntry() {
        return animatedEntry();
    }

    /**
     * HUD accent/stroke gradient with transition-safe semantics.
     * <p>
     * Raw theme gradients cannot be blended directly when one theme has the gradient
     * disabled: the disabled slot contains its semantic surface/stroke color, while HUD
     * code falls back to accent colors. Switching the slot to enabled at transition start
     * therefore creates a dark flash. These accessors blend the colors that are actually
     * rendered by HUD consumers instead.
     */
    public static GradientSpec hudAccentGradient() {
        return animatedHudGradient(
                transitionFromHudAccentGradient,
                resolveHudAccentGradient(currentEntry, current)
        );
    }

    /** Theme foreground/card accent gradient used by HUD icons and selectors. */
    public static GradientSpec hudForegroundGradient() {
        return animatedHudGradient(
                transitionFromHudForegroundGradient,
                resolveHudForegroundGradient(currentEntry, current)
        );
    }

    /** Theme card/selection gradient with the legacy HUD selector fallback preserved. */
    public static GradientSpec hudSelectionGradient() {
        return animatedHudGradient(
                transitionFromHudSelectionGradient,
                resolveHudSelectionGradient(currentEntry, current)
        );
    }

    /** Theme panel gradient used by HUD surfaces. */
    public static GradientSpec hudPanelGradient() {
        return animatedHudGradient(
                transitionFromHudPanelGradient,
                resolveHudPanelGradient(currentEntry, current)
        );
    }

    /** Subtle window background gradient used by generic HUD panel fills. */
    public static GradientSpec hudBackgroundGradient() {
        return animatedHudGradient(
                transitionFromHudBackgroundGradient,
                resolveHudBackgroundGradient(currentEntry, current)
        );
    }

    /** Theme shadow gradient; a non-gradient theme resolves to a solid accent pair. */
    public static GradientSpec hudShadowGradient() {
        return animatedHudGradient(
                transitionFromHudShadowGradient,
                resolveHudShadowGradient(currentEntry, current)
        );
    }

    static String currentId() {
        return currentId;
    }

    static List<ThemeEntry> themes() {
        return STORE.getThemes();
    }

    public static List<ThemeEntry> presetEntries() {
        List<ThemeEntry> out = new ArrayList<>();
        out.add(CLASSIC_ENTRY);
        out.add(LEGACY_ENTRY);
        out.add(AMBER_ENTRY);
        out.add(NOIR_ENTRY);
        out.add(PURPLE_ENTRY);
        out.add(BLUE_ENTRY);
        out.add(AZURE_ENTRY);
        out.add(NITRO_ENTRY);
        out.add(SUNSET_ENTRY);
        out.add(AURORA_ENTRY);
        out.add(MINT_ENTRY);
        out.add(PEACH_ENTRY);
        out.add(LILAC_ENTRY);
        out.add(SAND_ENTRY);
        out.add(DUSK_ENTRY);
        out.add(PRISM_ENTRY);
        out.add(CINDER_ENTRY);
        out.add(FOREST_ENTRY);
        out.add(SPEARMINT_ENTRY);
        out.add(JADE_GREEN_ENTRY);
        out.add(GREEN_SPIRIT_ENTRY);
        out.add(ROSY_PINK_ENTRY);
        out.add(MAGENTA_ENTRY);
        out.add(HOT_PINK_ENTRY);
        out.add(LAVENDER_ENTRY);
        out.add(AMETHYST_ENTRY);
        out.add(PURPLE_FIRE_ENTRY);
        out.add(SUNSET_PINK_ENTRY);
        out.add(BLAZE_ORANGE_ENTRY);
        out.add(PINK_BLOOD_ENTRY);
        out.add(PASTEL_ENTRY);
        out.add(NEON_RED_ENTRY);
        out.add(RED_COFFEE_ENTRY);
        out.add(DEEP_OCEAN_ENTRY);
        out.add(CHAMBRAY_BLUE_ENTRY);
        out.add(MINT_BLUE_ENTRY);
        out.add(PACIFIC_BLUE_ENTRY);
        out.add(TROPICAL_ICE_ENTRY);
        return Collections.unmodifiableList(out);
    }

    public static ThemeEntry presetEntry(String id) {
        if (id == null) return null;
        return PRESET_ENTRIES.get(id.toLowerCase());
    }

    static List<ThemeEntry> customThemes() {
        return STORE.getCustomThemes();
    }

    static boolean isCustomTheme(String id) {
        return STORE.isCustomId(id);
    }

    static String uniqueCustomId(String name) {
        return STORE.uniqueCustomId(name);
    }

    static ThemeEntry saveCustomTheme(ThemeEntry entry, boolean select) {
        ThemeEntry saved = STORE.saveCustom(entry);
        if (saved != null && select) {
            setTheme(saved.getId());
        }
        return saved;
    }

    static ThemeEntry saveCustomTheme(ThemeEntry entry) {
        return saveCustomTheme(entry, currentId != null && entry != null && currentId.equals(entry.getId()));
    }

    static boolean deleteCustomTheme(String id) {
        boolean removed = STORE.deleteCustom(id);
        if (removed && (id == null || id.equals(currentId))) {
            setTheme(THEME_CLASSIC);
        }
        return removed;
    }

    static ThemeEntry createCustomTheme(String name, ThemeEntry base, boolean select) {
        ThemeEntry src = base != null ? base : currentEntry();
        if (src == null) src = CLASSIC_ENTRY;
        String displayName = name == null || name.isBlank() ? "Custom Theme" : name.trim();
        String id = STORE.uniqueCustomId(displayName);
        ThemeEntry entry = new ThemeEntry(
                id,
                displayName,
                false,
                src.theme(),
                src.windowGradient(),
                src.headerGradient(),
                src.surfaceGradient(),
                src.cardGradient(),
                src.strokeGradient()
        );
        return saveCustomTheme(entry, select);
    }

    public static Map<String, Object> toProfileValues(ThemeEntry entry) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (entry == null) return out;
        Theme t = entry.theme();
        out.put("name", entry.name());
        out.put("windowBg", colorString(t.windowBg()));
        out.put("windowHeader", colorString(t.windowHeader()));
        out.put("windowStroke", colorString(t.windowStroke()));
        out.put("surface", colorString(t.surface()));
        out.put("surfaceHover", colorString(t.surfaceHover()));
        out.put("cardEnabled", colorString(t.cardEnabled()));
        out.put("cardDisabled", colorString(t.cardDisabled()));
        out.put("textPrimary", colorString(t.textPrimary()));
        out.put("textMuted", colorString(t.textMuted()));
        out.put("accent", colorString(t.accent()));
        out.put("accentSoft", colorString(t.accentSoft()));
        out.put("strokeSoft", colorString(t.strokeSoft()));
        writeGradient(out, "window", entry.windowGradient());
        writeGradient(out, "header", entry.headerGradient());
        writeGradient(out, "surface", entry.surfaceGradient());
        writeGradient(out, "card", entry.cardGradient());
        writeGradient(out, "stroke", entry.strokeGradient());
        return out;
    }

    public static ThemeEntry fromProfileValues(String id, String fallbackName, Map<String, Object> values) {
        String key = id == null || id.isBlank() ? uniqueCustomId(fallbackName) : id.toLowerCase();
        ThemeEntry existing = STORE.getById(key);
        ThemeEntry baseEntry = existing != null ? existing : CLASSIC_ENTRY;
        Theme base = baseEntry.theme();
        Map<String, Object> map = values == null ? Map.of() : values;
        String name = stringValue(map.get("name"), fallbackName == null || fallbackName.isBlank() ? key : fallbackName);
        Theme theme = new Theme(
                colorValue(map.get("windowBg"), base.windowBg()),
                colorValue(map.get("windowHeader"), base.windowHeader()),
                colorValue(map.get("windowStroke"), base.windowStroke()),
                colorValue(map.get("surface"), base.surface()),
                colorValue(map.get("surfaceHover"), base.surfaceHover()),
                colorValue(map.get("cardEnabled"), base.cardEnabled()),
                colorValue(map.get("cardDisabled"), base.cardDisabled()),
                colorValue(map.get("textPrimary"), base.textPrimary()),
                colorValue(map.get("textMuted"), base.textMuted()),
                colorValue(map.get("accent"), base.accent()),
                colorValue(map.get("accentSoft"), base.accentSoft()),
                colorValue(map.get("strokeSoft"), base.strokeSoft())
        );
        return new ThemeEntry(
                key,
                name,
                false,
                theme,
                readGradient(map, "window", baseEntry.windowGradient(), theme.windowBg()),
                readGradient(map, "header", baseEntry.headerGradient(), theme.windowHeader()),
                readGradient(map, "surface", baseEntry.surfaceGradient(), theme.surface()),
                readGradient(map, "card", baseEntry.cardGradient(), theme.cardEnabled()),
                readGradient(map, "stroke", baseEntry.strokeGradient(), theme.windowStroke())
        );
    }

    public static ThemeEntry importProfileValues(String id, String fallbackName, Map<String, Object> values) {
        ThemeEntry entry = fromProfileValues(id, fallbackName, values);
        return saveCustomTheme(entry, false);
    }

    private static void writeGradient(Map<String, Object> out, String prefix, GradientSpec spec) {
        GradientSpec g = spec != null ? spec : flatGradient(CLASSIC.accent());
        out.put(prefix + "GradientEnabled", g.enabled());
        out.put(prefix + "GradientStart", colorString(g.start()));
        out.put(prefix + "GradientEnd", colorString(g.end()));
        out.put(prefix + "GradientAngle", g.angleDeg());
    }

    private static GradientSpec readGradient(Map<String, Object> map, String prefix, GradientSpec fallback, int baseColor) {
        GradientSpec fb = fallback != null ? fallback : flatGradient(baseColor);
        boolean enabled = booleanValue(map.get(prefix + "GradientEnabled"), fb.enabled());
        int start = colorValue(map.get(prefix + "GradientStart"), fb.start());
        int end = colorValue(map.get(prefix + "GradientEnd"), fb.end());
        float angle = floatValue(map.get(prefix + "GradientAngle"), fb.angleDeg());
        return new GradientSpec(enabled, start, end, angle);
    }

    public static String colorString(int argb) {
        return String.format("#%08X", argb);
    }

    public static int colorValue(Object raw, int fallback) {
        if (raw instanceof Number n) return n.intValue();
        if (raw instanceof CharSequence s) return ThemeStore.parseColorString(s.toString(), fallback);
        return fallback;
    }

    private static String stringValue(Object raw, String fallback) {
        if (raw instanceof CharSequence s && !s.toString().isBlank()) return s.toString();
        return fallback;
    }

    private static boolean booleanValue(Object raw, boolean fallback) {
        if (raw instanceof Boolean b) return b;
        if (raw instanceof CharSequence s) return Boolean.parseBoolean(s.toString());
        return fallback;
    }

    private static float floatValue(Object raw, float fallback) {
        if (raw instanceof Number n) return n.floatValue();
        if (raw instanceof CharSequence s) {
            try {
                return Float.parseFloat(s.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    static void setTheme(String id) {
        if (id == null || id.isBlank()) {
            boolean animate = !THEME_CLASSIC.equals(currentId) || transitionActive;
            applyThemeFromEntry(CLASSIC_ENTRY, animate);
            STORE.setCurrentId(THEME_CLASSIC);
            return;
        }
        String key = id.toLowerCase();
        ThemeEntry entry = STORE.getById(key);
        if (entry == null) entry = PRESET_ENTRIES.get(key);
        if (entry == null) entry = CLASSIC_ENTRY;
        boolean animate = !entry.getId().equals(currentId) || transitionActive;
        applyThemeFromEntry(entry, animate);
        STORE.setCurrentId(entry.getId());
    }

    static void setTheme(Theme t) {
        if (t == null) {
            setTheme(THEME_CLASSIC);
            return;
        }
        for (ThemeEntry entry : PRESET_ENTRIES.values()) {
            if (entry.theme() == t) {
                setTheme(entry.getId());
                return;
            }
        }
        Theme from = displayedThemeRaw();
        ThemeEntry fromEntry = displayedEntryRaw();
        ThemeEntry targetEntry = new ThemeEntry(
                THEME_CLASSIC,
                "Custom",
                false,
                t,
                flatGradient(t.windowBg()),
                subtleHeaderGradient(t.windowHeader()),
                flatGradient(t.surface()),
                flatGradient(t.cardEnabled()),
                flatGradient(t.windowStroke())
        );
        beginThemeTransition(from, fromEntry);
        current = t;
        currentId = THEME_CLASSIC;
        currentEntry = targetEntry;
    }

    static Theme classic() {
        return CLASSIC;
    }

    private static void applyThemeFromStore() {
        String id = STORE.getCurrentId();
        ThemeEntry entry = STORE.getById(id);
        if (entry == null) entry = PRESET_ENTRIES.get(THEME_CLASSIC);
        if (entry == null) entry = CLASSIC_ENTRY;
        applyThemeFromEntry(entry, false);
    }

    private static void applyThemeFromEntry(ThemeEntry entry, boolean animate) {
        if (entry == null) entry = CLASSIC_ENTRY;
        if (animate) {
            beginThemeTransition(displayedThemeRaw(), displayedEntryRaw());
        } else {
            transitionActive = false;
            animatedCacheKey = -1;
        }
        current = entry.theme();
        currentId = entry.getId();
        currentEntry = entry;
        if (!animate) {
            animatedThemeCache = current;
            animatedEntryCache = currentEntry;
            syncHudTransitionSources();
        }
    }

    private static void beginThemeTransition(Theme fromTheme, ThemeEntry fromEntry) {
        // Snapshot the currently rendered HUD gradients before replacing transition state.
        // This also keeps rapid/re-entrant theme changes continuous instead of restarting
        // from a raw disabled gradient slot.
        GradientSpec displayedHudAccent = hudAccentGradient();
        GradientSpec displayedHudForeground = hudForegroundGradient();
        GradientSpec displayedHudSelection = hudSelectionGradient();
        GradientSpec displayedHudPanel = hudPanelGradient();
        GradientSpec displayedHudBackground = hudBackgroundGradient();
        GradientSpec displayedHudShadow = hudShadowGradient();

        transitionFromTheme = fromTheme != null ? fromTheme : current;
        transitionFromEntry = fromEntry != null ? fromEntry : currentEntry;
        transitionFromHudAccentGradient = displayedHudAccent;
        transitionFromHudForegroundGradient = displayedHudForeground;
        transitionFromHudSelectionGradient = displayedHudSelection;
        transitionFromHudPanelGradient = displayedHudPanel;
        transitionFromHudBackgroundGradient = displayedHudBackground;
        transitionFromHudShadowGradient = displayedHudShadow;
        transitionStartMs = Util.getMillis();
        transitionActive = true;
        animatedCacheKey = -1;
    }

    private static Theme displayedThemeRaw() {
        if (!transitionActive) return current;
        return animatedTheme();
    }

    private static ThemeEntry displayedEntryRaw() {
        if (!transitionActive) return currentEntry;
        return animatedEntry();
    }

    private static Theme animatedTheme() {
        if (!transitionActive) return current;
        float progress = transitionProgress();
        if (!transitionActive) return current;
        int key = Math.round(progress * 1024.0f);
        if (key == animatedCacheKey && animatedThemeCache != null) return animatedThemeCache;
        animatedThemeCache = blendTheme(transitionFromTheme, current, progress);
        animatedEntryCache = null;
        animatedCacheKey = key;
        return animatedThemeCache;
    }

    private static ThemeEntry animatedEntry() {
        if (!transitionActive) return currentEntry;
        float progress = transitionProgress();
        if (!transitionActive) return currentEntry;
        int key = Math.round(progress * 1024.0f);
        if (key != animatedCacheKey || animatedThemeCache == null) {
            animatedThemeCache = blendTheme(transitionFromTheme, current, progress);
            animatedEntryCache = null;
            animatedCacheKey = key;
        }
        if (animatedEntryCache != null) return animatedEntryCache;
        animatedEntryCache = new ThemeEntry(
                currentEntry.getId(),
                currentEntry.name(),
                currentEntry.builtin(),
                animatedThemeCache,
                blendGradient(transitionFromEntry.windowGradient(), currentEntry.windowGradient(), progress),
                blendGradient(transitionFromEntry.headerGradient(), currentEntry.headerGradient(), progress),
                blendGradient(transitionFromEntry.surfaceGradient(), currentEntry.surfaceGradient(), progress),
                blendGradient(transitionFromEntry.cardGradient(), currentEntry.cardGradient(), progress),
                blendGradient(transitionFromEntry.strokeGradient(), currentEntry.strokeGradient(), progress)
        );
        return animatedEntryCache;
    }

    private static float transitionProgress() {
        long elapsed = Math.max(0L, Util.getMillis() - transitionStartMs);
        if (elapsed >= THEME_TRANSITION_MS) {
            transitionActive = false;
            animatedThemeCache = current;
            animatedEntryCache = currentEntry;
            animatedCacheKey = -1;
            return 1.0f;
        }
        float linear = THEME_TRANSITION_MS <= 0L ? 1.0f : (float) elapsed / (float) THEME_TRANSITION_MS;
        return AnimationUtility.easeOutCubic(AnimationUtility.clamp01(linear));
    }

    private static Theme blendTheme(Theme from, Theme to, float t) {
        if (from == null) from = CLASSIC;
        if (to == null) to = CLASSIC;
        return new Theme(
                mixArgb(from.windowBg(), to.windowBg(), t),
                mixArgb(from.windowHeader(), to.windowHeader(), t),
                mixArgb(from.windowStroke(), to.windowStroke(), t),
                mixArgb(from.surface(), to.surface(), t),
                mixArgb(from.surfaceHover(), to.surfaceHover(), t),
                mixArgb(from.cardEnabled(), to.cardEnabled(), t),
                mixArgb(from.cardDisabled(), to.cardDisabled(), t),
                mixArgb(from.textPrimary(), to.textPrimary(), t),
                mixArgb(from.textMuted(), to.textMuted(), t),
                mixArgb(from.accent(), to.accent(), t),
                mixArgb(from.accentSoft(), to.accentSoft(), t),
                mixArgb(from.strokeSoft(), to.strokeSoft(), t)
        );
    }

    private static GradientSpec blendGradient(GradientSpec from, GradientSpec to, float t) {
        if (from == null) from = flatGradient(CLASSIC.accent());
        if (to == null) to = flatGradient(CLASSIC.accent());
        boolean enabled = from.enabled() || to.enabled();
        int start = mixArgb(from.start(), to.start(), t);
        int end = mixArgb(from.end(), to.end(), t);
        float angle = mixAngle(from.angleDeg(), to.angleDeg(), t);
        return new GradientSpec(enabled, start, end, angle);
    }

    private static GradientSpec animatedHudGradient(GradientSpec from, GradientSpec to) {
        if (!transitionActive) return to;
        float progress = transitionProgress();
        if (!transitionActive) return to;
        return blendVisibleGradient(from, to, progress);
    }

    private static GradientSpec blendVisibleGradient(GradientSpec from, GradientSpec to, float t) {
        if (from == null) from = new GradientSpec(true, CLASSIC.accent(), CLASSIC.accentSoft(), 90.0f);
        if (to == null) to = from;
        return new GradientSpec(
                true,
                mixArgb(from.start(), to.start(), t),
                mixArgb(from.end(), to.end(), t),
                mixAngle(from.angleDeg(), to.angleDeg(), t)
        );
    }

    private static GradientSpec resolveHudAccentGradient(ThemeEntry entry, Theme theme) {
        Theme resolvedTheme = theme != null ? theme : CLASSIC;
        GradientSpec gradient = entry != null ? entry.strokeGradient() : null;
        if (gradient != null && gradient.enabled()) {
            // HUD strokes are intentionally normalized top -> bottom.
            return new GradientSpec(true, gradient.start(), gradient.end(), 90.0f);
        }
        return new GradientSpec(true, resolvedTheme.accent(), resolvedTheme.accentSoft(), 90.0f);
    }

    private static GradientSpec resolveHudForegroundGradient(ThemeEntry entry, Theme theme) {
        Theme resolvedTheme = theme != null ? theme : CLASSIC;
        GradientSpec gradient = entry != null ? entry.cardGradient() : null;
        if (gradient != null && gradient.enabled()) {
            return new GradientSpec(true, gradient.start(), gradient.end(), gradient.angleDeg());
        }
        return new GradientSpec(true, resolvedTheme.accent(), resolvedTheme.accentSoft(), 45.0f);
    }

    private static GradientSpec resolveHudSelectionGradient(ThemeEntry entry, Theme theme) {
        Theme resolvedTheme = theme != null ? theme : CLASSIC;
        GradientSpec gradient = entry != null ? entry.cardGradient() : null;
        if (gradient != null && gradient.enabled()) {
            return new GradientSpec(true, gradient.start(), gradient.end(), gradient.angleDeg());
        }
        return new GradientSpec(
                true,
                resolvedTheme.accent(),
                mixArgb(resolvedTheme.accentSoft(), resolvedTheme.textPrimary(), 0.18f),
                45.0f
        );
    }

    private static GradientSpec resolveHudPanelGradient(ThemeEntry entry, Theme theme) {
        Theme resolvedTheme = theme != null ? theme : CLASSIC;
        GradientSpec gradient = entry != null ? entry.windowGradient() : null;
        if (gradient != null && gradient.enabled()) {
            return new GradientSpec(true, gradient.start(), gradient.end(), gradient.angleDeg());
        }
        return resolveHudAccentGradient(entry, resolvedTheme);
    }

    private static GradientSpec resolveHudBackgroundGradient(ThemeEntry entry, Theme theme) {
        Theme resolvedTheme = theme != null ? theme : CLASSIC;
        GradientSpec gradient = entry != null ? entry.windowGradient() : null;
        if (gradient != null && gradient.enabled()) {
            return new GradientSpec(true, gradient.start(), gradient.end(), gradient.angleDeg());
        }

        int base = resolvedTheme.windowBg();
        int alpha = (base >>> 24) & 0xFF;
        int white = (alpha << 24) | 0x00FFFFFF;
        int black = alpha << 24;
        return new GradientSpec(
                true,
                mixArgb(base, white, 0.06f),
                mixArgb(base, black, 0.08f),
                90.0f
        );
    }

    private static GradientSpec resolveHudShadowGradient(ThemeEntry entry, Theme theme) {
        Theme resolvedTheme = theme != null ? theme : CLASSIC;
        GradientSpec gradient = entry != null ? entry.strokeGradient() : null;
        if (gradient != null && gradient.enabled()) {
            return new GradientSpec(true, gradient.start(), gradient.end(), gradient.angleDeg());
        }
        int accent = resolvedTheme.accent();
        return new GradientSpec(true, accent, accent, 45.0f);
    }

    private static void syncHudTransitionSources() {
        transitionFromHudAccentGradient = resolveHudAccentGradient(currentEntry, current);
        transitionFromHudForegroundGradient = resolveHudForegroundGradient(currentEntry, current);
        transitionFromHudSelectionGradient = resolveHudSelectionGradient(currentEntry, current);
        transitionFromHudPanelGradient = resolveHudPanelGradient(currentEntry, current);
        transitionFromHudBackgroundGradient = resolveHudBackgroundGradient(currentEntry, current);
        transitionFromHudShadowGradient = resolveHudShadowGradient(currentEntry, current);
    }

    private static int mixArgb(int from, int to, float t) {
        t = AnimationUtility.clamp01(t);
        int fa = (from >>> 24) & 0xFF;
        int fr = (from >>> 16) & 0xFF;
        int fg = (from >>> 8) & 0xFF;
        int fb = from & 0xFF;
        int ta = (to >>> 24) & 0xFF;
        int tr = (to >>> 16) & 0xFF;
        int tg = (to >>> 8) & 0xFF;
        int tb = to & 0xFF;
        int a = Math.round(fa + (ta - fa) * t);
        int r = Math.round(fr + (tr - fr) * t);
        int g = Math.round(fg + (tg - fg) * t);
        int b = Math.round(fb + (tb - fb) * t);
        return (clampChannel(a) << 24) | (clampChannel(r) << 16) | (clampChannel(g) << 8) | clampChannel(b);
    }

    private static float mixAngle(float from, float to, float t) {
        float delta = ((to - from + 540.0f) % 360.0f) - 180.0f;
        float out = from + delta * AnimationUtility.clamp01(t);
        out %= 360.0f;
        return out < 0.0f ? out + 360.0f : out;
    }

    private static ThemeEntry pairedPaletteEntry(String id, String name, int color1, int color2, boolean gradient) {
        Theme theme = pairedPaletteTheme(color1, color2);
        if (!gradient) {
            return new ThemeEntry(
                    id,
                    name,
                    true,
                    theme,
                    flatGradient(theme.windowBg()),
                    subtleHeaderGradient(theme.windowHeader()),
                    flatGradient(theme.surface()),
                    flatGradient(theme.cardEnabled()),
                    flatGradient(theme.windowStroke())
            );
        }

        int primary = forceOpaque(color1);
        int secondary = forceOpaque(color2);
        return new ThemeEntry(
                id,
                name,
                true,
                theme,
                new GradientSpec(true,
                        withAlpha(mixArgb(0xFF0B0E12, primary, 0.14f), 0xF4),
                        withAlpha(mixArgb(0xFF0B0E12, secondary, 0.14f), 0xF4),
                        120f),
                new GradientSpec(true,
                        withAlpha(mixArgb(0xFF10141A, primary, 0.19f), 0xF4),
                        withAlpha(mixArgb(0xFF10141A, secondary, 0.19f), 0xF4),
                        120f),
                new GradientSpec(true,
                        mixArgb(0xFF171B21, primary, 0.08f),
                        mixArgb(0xFF171B21, secondary, 0.08f),
                        90f),
                new GradientSpec(true, primary, secondary, 45f),
                new GradientSpec(true, primary, secondary, 45f)
        );
    }

    /**
     * Converts a classic two-color client palette into the semantic colors used by
     * the current ClickGUI/HUD. Backgrounds stay deliberately dark and only pick
     * up a small amount of hue, while the supplied colors remain exact for the
     * accent pair.
     */
    private static Theme pairedPaletteTheme(int color1, int color2) {
        int primary = forceOpaque(color1);
        int secondary = forceOpaque(color2);
        int midpoint = mixArgb(primary, secondary, 0.5f);

        return new Theme(
                withAlpha(mixArgb(0xFF0C0F13, primary, 0.055f), 0xF4),
                withAlpha(mixArgb(0xFF10141A, secondary, 0.075f), 0xF4),
                withAlpha(midpoint, 0x60),
                mixArgb(0xFF171B21, primary, 0.055f),
                mixArgb(0xFF20252D, secondary, 0.085f),
                withAlpha(mixArgb(primary, secondary, 0.28f), 0x50),
                withAlpha(mixArgb(0xFF2A3038, secondary, 0.07f), 0x40),
                mixArgb(0xFFF6F8FB, primary, 0.025f),
                withAlpha(mixArgb(0xFFBBC4D0, secondary, 0.10f), 0x88),
                primary,
                withAlpha(secondary, 0x80),
                withAlpha(midpoint, 0x60)
        );
    }

    private static int forceOpaque(int argb) {
        return 0xFF000000 | (argb & 0x00FFFFFF);
    }

    private static int withAlpha(int argb, int alpha) {
        return (clampChannel(alpha) << 24) | (argb & 0x00FFFFFF);
    }

    private static GradientSpec flatGradient(int color) {
        return new GradientSpec(false, color, color, 90f);
    }

    private static GradientSpec subtleHeaderGradient(int color) {
        int start = adjustBrightness(color, 0.08f);
        int end = adjustBrightness(color, -0.06f);
        return new GradientSpec(true, start, end, 90f);
    }

    private static int adjustBrightness(int argb, float delta) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        r = clampChannel(r + Math.round((delta >= 0 ? (255 - r) : r) * delta));
        g = clampChannel(g + Math.round((delta >= 0 ? (255 - g) : g) * delta));
        b = clampChannel(b + Math.round((delta >= 0 ? (255 - b) : b) * delta));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int clampChannel(int v) {
        if (v < 0) return 0;
        if (v > 255) return 255;
        return v;
    }

    public record Theme(
            int windowBg,
            int windowHeader,
            int windowStroke,
            int surface,
            int surfaceHover,
            int cardEnabled,
            int cardDisabled,
            int textPrimary,
            int textMuted,
            int accent,
            int accentSoft,
            int strokeSoft
    ) {
    }

    public record GradientSpec(boolean enabled, int start, int end, float angleDeg) {
    }

    public record ThemeEntry(
            String id,
            String name,
            boolean builtin,
            Theme theme,
            GradientSpec windowGradient,
            GradientSpec headerGradient,
            GradientSpec surfaceGradient,
            GradientSpec cardGradient,
            GradientSpec strokeGradient
    ) {
        public String getId() {
            return id;
        }
    }
}
