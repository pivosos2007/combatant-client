/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.module.modules.misc;

import combatant.client.config.DisableSettingI18n;
import combatant.client.config.values.BooleanMapValue;
import combatant.client.config.values.NumberValue;
import combatant.client.features.gui.clickgui.ClickGuiEditorScreen;
import combatant.client.features.gui.clickgui.ClickGuiPickerScreen;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.ClickGuiScreen;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleCategory;
import combatant.client.features.module.ModuleInfo;
import combatant.client.util.screen.ClientScreen;
import combatant.client.features.gui.clickgui.sound.GuiSound;
import net.minecraft.client.Minecraft;

import java.util.LinkedHashMap;
import java.util.Map;

@ModuleInfo(id = "clickgui", displayName = "ClickGUI", aliases = {"menu", "settings"}, category = ModuleCategory.MISC)
public class ClickGui extends Module {
    private static boolean suppressScreenClose = false;

    @DisableSettingI18n(name = false, options = true)
    private final BooleanMapValue guiSoundToggles = group("gui_sounds", defaultGuiSounds());
    private final NumberValue<Double> guiSoundVolume = num("gui_sound_volume", 0.75, 0.0, 2.0);

    {
        setDefaultBind("RIGHT_SHIFT");
    }

    public static boolean isSuppressScreenClose() {
        return suppressScreenClose;
    }

    public static void setSuppressScreenClose(boolean suppress) {
        suppressScreenClose = suppress;
    }

    private static Map<String, Boolean> defaultGuiSounds() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        out.put("guibinding", true);
        out.put("guibindingnull", true);
        out.put("guibindingstart", true);
        out.put("guibindreset", true);
        out.put("guichangemode", true);
        out.put("guiyes", true);
        out.put("guino", true);
        out.put("moduleopen", true);
        out.put("moduleclose", true);
        out.put("moduleonopen", true);
        out.put("moduleonclose", true);
        out.put("guiscroll", true);
        out.put("guislidermove", true);
        return out;
    }

    @Override
    public void onDisable() {
        Minecraft mc = Minecraft.getInstance();
        boolean clickGuiScreen = mc != null && (ClientScreen.current() instanceof ClickGuiScreen
                || ClientScreen.current() instanceof ClickGuiPickerScreen
                || ClientScreen.current() instanceof ClickGuiEditorScreen);

        if (!suppressScreenClose && clickGuiScreen) {
            GuiSound.CLOSE.feedback(0.25);
            ClickGuiRenderer.beginCloseAnimation();
            return;
        }

        ClickGuiRenderer.finishCloseImmediately();
        if (mc != null && clickGuiScreen && !suppressScreenClose) {
            ClientScreen.show(mc, null);
        }
    }

    @Override
    public void onEnable() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || (ClientScreen.current() != null && !ClickGuiRenderer.isClosingForExit())) {
            setEnabled(false);
            return;
        }
        ClickGuiRenderer.beginOpenAnimation();
        ClickGuiRenderer.openClickGuiScreen();
    }

    public boolean isGuiSoundEnabled(String key) {
        return guiSoundToggles.get(key);
    }

    public float getGuiSoundVolume() {
        return guiSoundVolume.get().floatValue();
    }
}
