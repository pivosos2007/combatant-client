/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.mainmenu;


import combatant.client.features.theme.Theme;
import combatant.client.util.screen.ClientScreen;
import combatant.client.util.session.MicrosoftSessionResult;
import combatant.client.util.session.microsoft.MicrosoftAuthService;
import combatant.client.util.session.microsoft.MicrosoftDeviceCode;
import combatant.client.util.text.ClipboardUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import combatant.client.features.account.AccountConfig;
import combatant.client.features.account.AccountEntry;
import combatant.client.features.account.SkinManager;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.clickgui.ClickGuiRenderer;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.clickgui.layout.screen.settings.render.LayoutRender2D;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.postprocess.MenuBackgroundRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.renderer.ui.draw.UiPaint;
import combatant.client.render.engine.renderer.ui.draw.UiPrimitive;
import combatant.client.render.engine.renderer.ui.draw.UiStroke;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.PlayerHeadRenderer;
import combatant.client.render.helpers.ScissorFunction;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

public final class CombatantAltManagerScreen extends Screen {
    private static final float SCALE = 2.12f;
    private static final float TEXT_SCALE = 1.30f;
    private static final float WINDOW_PAD = 7.0f * SCALE;
    private static final float WINDOW_CUT = 7.5f * SCALE;
    private static final float WINDOW_ROUNDING = 1.35f * SCALE;
    private static final float BUTTON_ELEVATION = 3.15f * SCALE;
    private static final DateTimeFormatter ACCOUNT_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private static final float LEFT_W = 100f * SCALE;
    private static final float LEFT_TOP_H = 137f * SCALE;
    private static final float LEFT_BOTTOM_H = 58f * SCALE;
    private static final float RIGHT_W = 300f * SCALE;
    private static final float RIGHT_H = (137f + 5f + 58f) * SCALE;
    private static final float GAP = 5f * SCALE;

    private static final float PANEL_R = 3.2f * SCALE;
    private static final int PIN_YELLOW = 0xFFFFC857;
    private static final int PIN_YELLOW_SOFT = 0xFFFFE39A;

    private static final float TITLE_FONT = 0.84f * TEXT_SCALE;
    private static final float LABEL_FONT = 0.58f * TEXT_SCALE;
    private static final float FIELD_FONT = 0.60f * TEXT_SCALE;
    private static final float BUTTON_FONT = 0.58f * TEXT_SCALE;
    private static final float ACTION_ICON_FONT = 1.02f * TEXT_SCALE;
    private static final float CARD_NAME_FONT = 0.72f * TEXT_SCALE;
    private static final float CARD_DATE_FONT = 0.61f * TEXT_SCALE;
    private static final float ACTIVE_NAME_FONT = 0.66f * TEXT_SCALE;
    private static final float ACTIVE_DATE_FONT = 0.50f * TEXT_SCALE;
    private static final float EMPTY_FONT = 0.56f * TEXT_SCALE;
    private static final float BADGE_FONT = 0.45f * TEXT_SCALE;
    private static final float OFFLINE_STATUS_FONT = 0.64f * TEXT_SCALE;
    private static final float PIN_ICON_FONT = 0.93f * TEXT_SCALE;
    private static final float DELETE_ICON_FONT = 1.05f * TEXT_SCALE;

    private static final float FIELD_H = 14f * SCALE;
    private static final float ADD_SIZE = 14f * SCALE;
    private static final float TOP_BUTTON_H = 16f * SCALE;
    private static final float NICK_LABEL_Y = 43f * SCALE;
    private static final float NICK_FIELD_Y = 53f * SCALE;
    private static final float PRIMARY_ACTION_Y = 76f * SCALE;
    private static final float CARD_H = 48f * SCALE;
    private static final float CARD_GAP = 5f * SCALE;
    private static final float CARD_BTN = 12f * SCALE;
    private static final float ACTIVE_HEAD = 24f * SCALE;
    private static final float CARD_HEAD = 19f * SCALE;

    private final Screen parent;
    private final AccountConfig accountConfig = AccountConfig.get();
    private final Map<String, Float> cardHoverAnims = new HashMap<>();
    private final Map<String, Float> cardActiveAnims = new HashMap<>();
    private final Map<String, Float> cardPressAnims = new HashMap<>();
    private final Map<String, Float> pinHoverAnims = new HashMap<>();
    private final Map<String, Float> pinPressAnims = new HashMap<>();
    private final Map<String, Float> deleteHoverAnims = new HashMap<>();
    private final Map<String, Float> deletePressAnims = new HashMap<>();
    private float fixedWidth;
    private float fixedHeight;
    private String nicknameText = "";
    private boolean nicknameFieldFocused;
    private float scrollOffset;
    private float targetScrollOffset;
    private float openAnim;
    private float fieldHoverAnim;
    private float fieldFocusAnim;
    private float addHoverAnim;
    private float addPressAnim;
    private boolean microsoftMode;
    private boolean microsoftAuthPending;
    private String microsoftStatus = "";
    private String microsoftUserCode = "";
    private String microsoftVerificationUrl = "";
    private float offlineModeHoverAnim;
    private float microsoftModeHoverAnim;
    private float offlineModePressAnim;
    private float microsoftModePressAnim;
    private float randomHoverAnim;
    private float randomPressAnim;
    private float openSiteHoverAnim;
    private float openSitePressAnim;
    private float clearHoverAnim;
    private float clearPressAnim;
    private TextRenderer titleRenderer;
    private TextRenderer bodyRenderer;
    private TextRenderer menuIconRenderer;
    private TextRenderer guiIconRenderer;
    private TextRenderer iconRenderer;

    public CombatantAltManagerScreen(Screen parent) {
        this(parent, false);
    }

    public CombatantAltManagerScreen(Screen parent, boolean microsoftMode) {
        super(Component.translatable("screen.combatant.alt_manager.title"));
        this.parent = parent;
        this.microsoftMode = microsoftMode;
    }

    private static float animate(float current, float target, float dt, float speed) {
        float next = AnimationUtility.approach(current, target, dt, speed);
        return AnimationUtility.snap(next, target, 0.001f);
    }

    private static void prune(Map<String, Float> map, Set<String> keys) {
        map.keySet().removeIf(key -> !keys.contains(key));
    }

    private static boolean inside(float mx, float my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }

    private static int withAlpha(int rgb, int alpha) {
        return (rgb & 0x00FFFFFF) | (Mth.clamp(alpha, 0, 255) << 24);
    }

    private static String tr(String key, Object... args) {
        return I18n.get(key, args);
    }

    @Override
    protected void init() {
        titleRenderer = Fonts.renderer("OnestBold", FontInfo.Type.Regular, TextRenderer.get());
        bodyRenderer = Fonts.renderer("OnestMedium", FontInfo.Type.Regular, titleRenderer);
        menuIconRenderer = Fonts.renderer("MainMenuIcons", FontInfo.Type.Regular, TextRenderer.get());
        guiIconRenderer = Fonts.renderer("GuiIcons", FontInfo.Type.Regular, menuIconRenderer);
        iconRenderer = Fonts.renderer("RichIcons", FontInfo.Type.Regular, menuIconRenderer);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        updateUiMetrics();
        clampScroll();
        renderBackgroundTexture();

        float fx = toFixedX(mouseX);
        float fy = toFixedY(mouseY);
        updateAnimations(fx, fy);

        ViewportContext.beginUnscaledLogical(context);
        Renderer2D.COLOR.begin();
        try {
            Layout l = layout();
            WindowBounds window = windowBounds(l);
            MainMenuBackdrop.GridLayout backdropGrid = MainMenuBackdrop.layout(fixedWidth, fixedHeight);
            float cutoutPad = 1.15f * SCALE;
            MainMenuBackdrop.Cutout cutout = MainMenuBackdrop.Cutout.chamfered(
                    window.x - cutoutPad,
                    window.y - cutoutPad,
                    window.w + cutoutPad * 2f,
                    window.h + cutoutPad * 2f,
                    WINDOW_CUT + cutoutPad
            );
            MainMenuBackdrop.render(
                    fixedWidth, fixedHeight, 1.0f, fx, fy, backdropGrid, cutout
            );
            renderAltUi(context, fx, fy);
        } finally {
            Renderer2D.COLOR.render();
            ViewportContext.end(context);
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        if (click.button() != 0) return true;
        return handleClick(toFixedX((float) click.x()), toFixedY((float) click.y()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float fx = toFixedX((float) mouseX);
        float fy = toFixedY((float) mouseY);
        Layout layout = layout();
        if (!inside(fx, fy, layout.rightX, layout.topY, RIGHT_W, RIGHT_H)) return false;
        targetScrollOffset -= (float) verticalAmount * (25f * SCALE);
        clampScroll();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (nicknameFieldFocused) {
            int key = input.key();
            if (key == 259) {
                if (!nicknameText.isEmpty()) nicknameText = nicknameText.substring(0, nicknameText.length() - 1);
                return true;
            }
            if (key == 257 || key == 335) {
                commitNickname();
                nicknameFieldFocused = false;
                return true;
            }
            if (key == 256) {
                nicknameFieldFocused = false;
                return true;
            }
        }

        if (input.key() == 256) {
            closeToParent();
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        if (nicknameFieldFocused) {
            int cp = input.codepoint();
            if ((Character.isLetterOrDigit(cp) || cp == '_') && nicknameText.length() < 16) {
                nicknameText += Character.toString(cp);
                return true;
            }
        }
        return super.charTyped(input);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    private void renderAltUi(GuiGraphicsExtractor context, float mouseX, float mouseY) {
        Layout l = layout();
        WindowBounds window = windowBounds(l);
        float open = AnimationUtility.easeOutCubic(openAnim);
        PanelColors c = colors(open);

        renderWindow(window, c, open);
        renderPanel(l.leftX, l.topY, LEFT_W, LEFT_TOP_H, c, open);
        renderPanel(l.leftX, l.bottomY, LEFT_W, LEFT_BOTTOM_H, c, open);
        renderPanel(l.rightX, l.topY, RIGHT_W, RIGHT_H, c, open);

        renderTopPanel(mouseX, mouseY, l.leftX, l.topY, c);
        renderActivePanel(context, l.leftX, l.bottomY, c);
        renderAccountsPanel(context, mouseX, mouseY, l.rightX, l.topY, c);
    }

    private void renderTopPanel(float mouseX, float mouseY, float x, float y, PanelColors c) {
        draw(titleRenderer, tr("screen.combatant.alt_manager.account_panel"), x + 7f * SCALE, y + 7f * SCALE, TITLE_FONT, c.title);
        renderModeToggle(mouseX, mouseY, x + 5f * SCALE, y + 27f * SCALE, LEFT_W - 10f * SCALE, 13f * SCALE, c);

        draw(bodyRenderer, microsoftMode ? tr("screen.combatant.alt_manager.microsoft_login") : tr("screen.combatant.alt_manager.nickname"), x + 5f * SCALE, y + NICK_LABEL_Y, LABEL_FONT, c.label);

        float fieldX = x + 5f * SCALE;
        float fieldY = y + NICK_FIELD_Y;
        float fieldW = LEFT_W - 10f * SCALE - ADD_SIZE - 3f * SCALE;
        if (microsoftMode) {
            renderMicrosoftField(fieldX, fieldY, fieldW, FIELD_H, c);
        } else {
            renderField(fieldX, fieldY, fieldW, FIELD_H, c, inside(mouseX, mouseY, fieldX, fieldY, fieldW, FIELD_H));
        }

        float addX = fieldX + fieldW + 3f * SCALE;
        renderAddButton(addX, fieldY, c);

        float buttonX = x + 5f * SCALE;
        float buttonW = LEFT_W - 10f * SCALE;
        float randomY = y + PRIMARY_ACTION_Y;
        float openSiteY = randomY + TOP_BUTTON_H + 5f * SCALE;
        float clearY = (microsoftMode ? openSiteY : randomY) + TOP_BUTTON_H + 5f * SCALE;

        String authAction = microsoftMode
                ? (microsoftUserCode.isBlank()
                        ? (microsoftAuthPending ? tr("screen.combatant.alt_manager.waiting") : tr("screen.combatant.alt_manager.authorize"))
                        : tr("screen.combatant.alt_manager.copy_code"))
                : tr("screen.combatant.alt_manager.random");
        if (microsoftMode) {
            renderSvgAction(buttonX, randomY, buttonW, TOP_BUTTON_H, c, false, authAction, "user-check", randomHoverAnim, randomPressAnim);
        } else {
            renderAction(buttonX, randomY, buttonW, TOP_BUTTON_H, c, false, authAction, iconRenderer, "R", randomHoverAnim, randomPressAnim);
        }
        if (microsoftMode) {
            renderSvgAction(buttonX, openSiteY, buttonW, TOP_BUTTON_H, c, false, tr("screen.combatant.alt_manager.open_site"), "external-link", openSiteHoverAnim, openSitePressAnim);
        }
        renderAction(buttonX, clearY, buttonW, TOP_BUTTON_H, c, true, tr("screen.combatant.alt_manager.clear_all"), guiIconRenderer, "O", clearHoverAnim, clearPressAnim);
    }


    private void renderActivePanel(GuiGraphicsExtractor context, float x, float y, PanelColors c) {
        draw(titleRenderer, tr("screen.combatant.alt_manager.active_session"), x + 7f * SCALE, y + 6f * SCALE, TITLE_FONT, c.title);

        float cardX = x + 5f * SCALE;
        float cardY = y + 24f * SCALE;
        float cardW = LEFT_W - 10f * SCALE;
        float cardH = 29f * SCALE;
        renderAccountCardMaterial(cardX, cardY, cardW, cardH, c, 0f, 0.34f, 0f);

        String activeName = accountConfig.getActiveAccountName();
        if (activeName == null || activeName.isBlank()) {
            drawCentered(bodyRenderer, tr("screen.combatant.alt_manager.no_account_selected"), cardX + cardW * 0.5f, cardY + 10.2f * SCALE, EMPTY_FONT, c.mutedLabel);
            return;
        }
        float faceX = cardX + 6f * SCALE;
        float faceY = cardY + 2.5f * SCALE;
        Identifier skin = accountConfig.getActiveAccountSkin();
        if (skin == null) skin = SkinManager.getSkin(activeName);

        PlayerHeadRenderer.drawRounded(context, faceX, faceY, ACTIVE_HEAD, 2.4f * SCALE, skin,
                new RenderColor(c.title), true, new RenderColor(withAlpha(c.accentSoft, 142)), 0.70f, false);

        float textX = faceX + ACTIVE_HEAD + 6f * SCALE;
        float textW = Math.max(1f, cardX + cardW - textX - 10f * SCALE);
        draw(bodyRenderer, ellipsize(activeName, ACTIVE_NAME_FONT, textW), textX, faceY + 4f * SCALE, ACTIVE_NAME_FONT, c.title);
        draw(bodyRenderer, ellipsize(accountConfig.getActiveAccountDate(), ACTIVE_DATE_FONT, textW), textX, faceY + 14f * SCALE, ACTIVE_DATE_FONT, c.muted);
    }

    private void renderAccountsPanel(GuiGraphicsExtractor context, float mouseX, float mouseY, float x, float y, PanelColors c) {
        draw(titleRenderer, tr("screen.combatant.alt_manager.accounts_list"), x + 8f * SCALE, y + 7f * SCALE, TITLE_FONT, c.title);

        List<AccountEntry> accounts = accountConfig.getSortedAccounts();
        if (accounts.isEmpty()) {
            drawCentered(bodyRenderer, tr("screen.combatant.alt_manager.no_accounts_added"), x + RIGHT_W * 0.5f, y + RIGHT_H * 0.5f + 2f * SCALE, EMPTY_FONT, c.mutedLabel);
            return;
        }

        float listX = x + 5f * SCALE;
        float listY = y + 28f * SCALE;
        float listW = RIGHT_W - 10f * SCALE;
        float listH = RIGHT_H - 31f * SCALE;
        float cardW = (listW - CARD_GAP) * 0.5f;

        boolean clipped = ScissorFunction.pushRaw(listX, listY - 3f * SCALE, listW, listH + 6f * SCALE);
        try {
            for (int i = 0; i < accounts.size(); i++) {
                int col = i % 2;
                int row = i / 2;
                float cardX = listX + col * (cardW + CARD_GAP);
                float cardY = listY + row * (CARD_H + CARD_GAP) - scrollOffset;
                if (cardY + CARD_H < listY - 10f || cardY > listY + listH + 10f) continue;
                renderCard(context, accounts.get(i), mouseX, mouseY, cardX, cardY, cardW, listY, listH, c);
            }
        } finally {
            if (clipped) ScissorFunction.pop();
        }
    }

    private void renderCard(GuiGraphicsExtractor context, AccountEntry entry, float mouseX, float mouseY, float x, float y, float w, float listY, float listH, PanelColors c) {
        String key = entry.getName();
        boolean inList = mouseY >= listY && mouseY <= listY + listH;
        float hoverAnim = AnimationUtility.easeOutCubic(cardHoverAnims.getOrDefault(key, 0f));
        float activeAnim = AnimationUtility.easeOutCubic(cardActiveAnims.getOrDefault(key, 0f));
        float pressAnim = AnimationUtility.easeOutCubic(cardPressAnims.getOrDefault(key, 0f));

        // Match ClickGui Settings cards: rounded card, soft SDF shadow, backdrop blur,
        // four-corner palette gradient and only a weak hover/selected highlight.
        float topY = y - hoverAnim * 0.42f * SCALE + pressAnim * 0.16f * SCALE;
        renderAccountCardMaterial(x, topY, w, CARD_H, c, hoverAnim, activeAnim, pressAnim);

        SettingsGuiPalette palette = SettingsGuiPalette.current();
        float openAlpha = ((c.title >>> 24) & 0xFF) / 255f;
        LayoutRender2D.rectQuad(
                x, topY + 28f * SCALE, w, 0.5f * SCALE,
                LayoutRender2D.alpha(palette.moduleDividerStart(), openAlpha),
                LayoutRender2D.alpha(palette.moduleDividerEnd(), openAlpha),
                LayoutRender2D.alpha(palette.moduleDividerEnd(), openAlpha),
                LayoutRender2D.alpha(palette.moduleDividerStart(), openAlpha)
        );

        float faceX = x + 6f * SCALE;
        float faceY = topY + 5f * SCALE;
        int faceOutline = activeAnim > 0.01f
                ? withAlpha(HudRenderUtil.mixColor(c.accentSoft, c.accent, 0.38f), Math.round(112f + 88f * activeAnim))
                : withAlpha(c.strokeSoft, 96);
        PlayerHeadRenderer.drawRounded(context, faceX, faceY, CARD_HEAD, 3.4f * SCALE, SkinManager.getSkin(entry.getName()),
                new RenderColor(c.title), true, new RenderColor(faceOutline), 0.70f, false);

        float textX = x + 31f * SCALE;
        draw(bodyRenderer, ellipsize(entry.getName(), CARD_NAME_FONT, w - 42f * SCALE), textX, topY + 6f * SCALE, CARD_NAME_FONT, c.title);
        draw(bodyRenderer, ellipsize(entry.getDate(), CARD_DATE_FONT, w - 42f * SCALE), textX, topY + 17f * SCALE, CARD_DATE_FONT, c.muted);
        renderAuthBadge(entry, x + 7f * SCALE, topY + 34.5f * SCALE, c);

        float btnY = topY + CARD_H - CARD_BTN - 5f * SCALE;
        float pinX = x + w - CARD_BTN * 2f - 8f * SCALE;
        float delX = x + w - CARD_BTN - 5f * SCALE;
        boolean pinHover = inList && inside(mouseX, mouseY, pinX, btnY, CARD_BTN, CARD_BTN);
        boolean delHover = inList && inside(mouseX, mouseY, delX, btnY, CARD_BTN, CARD_BTN);

        float pinHoverAnim = AnimationUtility.easeOutCubic(pinHoverAnims.getOrDefault(key, pinHover ? 1f : 0f));
        float pinPressAnim = AnimationUtility.easeOutBack(pinPressAnims.getOrDefault(key, 0f), 0.95f);
        int pinAccent = entry.isPinned()
                ? HudRenderUtil.mixColor(PIN_YELLOW, PIN_YELLOW_SOFT, 0.28f)
                : HudRenderUtil.mixColor(c.strokeSoft, PIN_YELLOW, 0.16f + pinHoverAnim * 0.72f);
        int pinTint = HudRenderUtil.mixColor(c.surfaceHover, PIN_YELLOW,
                (entry.isPinned() ? 0.22f : 0.05f) + pinHoverAnim * 0.12f);
        float pinRadius = CARD_BTN * 0.5f;
        float pinCx = pinX + CARD_BTN * 0.5f;
        float pinCy = btnY + CARD_BTN * 0.5f;
        renderHexButtonSurface(pinCx, pinCy, pinRadius, pinAccent, pinTint, 0.96f, pinHoverAnim, pinPressAnim);
        float pinLift = BUTTON_ELEVATION * AnimationUtility.clamp01(1.0f - pinHoverAnim * 0.68f - pinPressAnim * 0.24f);
        int pinIcon = entry.isPinned()
                ? PIN_YELLOW_SOFT
                : HudRenderUtil.mixColor(c.muted, PIN_YELLOW_SOFT, 0.18f + pinHoverAnim * 0.76f);
        drawCentered(menuIconRenderer, "c", pinCx, pinCy - pinLift - 4.7f * SCALE, PIN_ICON_FONT, pinIcon);

        float delHoverAnim = AnimationUtility.easeOutCubic(deleteHoverAnims.getOrDefault(key, delHover ? 1f : 0f));
        float delPressAnim = AnimationUtility.easeOutBack(deletePressAnims.getOrDefault(key, 0f), 0.95f);
        int delAccent = HudRenderUtil.mixColor(withAlpha(0xFF9E4650, 255), withAlpha(0xFFFF7180, 255), delHoverAnim * 0.58f);
        int delTint = HudRenderUtil.mixColor(c.surfaceHover, withAlpha(0xFF542630, 255), 0.10f + delHoverAnim * 0.22f);
        float delCx = delX + CARD_BTN * 0.5f;
        float delCy = btnY + CARD_BTN * 0.5f;
        renderHexButtonSurface(delCx, delCy, pinRadius, delAccent, delTint, 0.96f, delHoverAnim, delPressAnim);
        float delLift = BUTTON_ELEVATION * AnimationUtility.clamp01(1.0f - delHoverAnim * 0.68f - delPressAnim * 0.24f);
        drawCentered(guiIconRenderer, "O", delCx, delCy - delLift - 5.6f * SCALE, DELETE_ICON_FONT,
                HudRenderUtil.mixColor(c.muted, withAlpha(0xFFFFE4E7, 255), 0.30f + delHoverAnim * 0.62f));
    }

    private void renderAccountCardMaterial(float x, float y, float w, float h, PanelColors c, float hover, float active, float press) {
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        float hoverAnim = AnimationUtility.clamp01(hover);
        float activeAnim = AnimationUtility.clamp01(active);
        float pressAnim = AnimationUtility.clamp01(press);
        float openAlpha = ((c.title >>> 24) & 0xFF) / 255f;
        float radius = 5f * SCALE;

        int top = SettingsGuiPalette.mix(
                palette.moduleCardTop(),
                palette.menuCategorySelectedLeft(),
                0.16f * activeAnim
        );
        int topStrong = SettingsGuiPalette.mix(
                palette.moduleCardTopStrong(),
                palette.menuCategorySelectedRight(),
                0.16f * activeAnim
        );
        int bottom = SettingsGuiPalette.mix(
                palette.moduleCardBottom(),
                palette.menuCategoryHoverLeft(),
                0.18f * hoverAnim
        );
        int bottomStrong = SettingsGuiPalette.mix(
                palette.moduleCardBottomStrong(),
                palette.menuCategoryHoverRight(),
                0.14f * hoverAnim
        );

        LayoutRender2D.roundedSoftShadow(
                x,
                y + 1.6f * SCALE,
                w,
                h,
                5.4f * SCALE,
                5.2f * SCALE,
                0.0f,
                LayoutRender2D.alpha(0xFF000000, openAlpha * (0.18f + 0.08f * hoverAnim - 0.025f * pressAnim))
        );
        ClickGuiRenderer.drawBlur(x, y, w, h, radius, 0xFF000000, (200f / 255f) * openAlpha);
        LayoutRender2D.roundedQuad(
                x, y, w, h, radius,
                LayoutRender2D.alpha(top, openAlpha),
                LayoutRender2D.alpha(topStrong, openAlpha),
                LayoutRender2D.alpha(bottom, openAlpha),
                LayoutRender2D.alpha(bottomStrong, openAlpha)
        );
        Renderer2D.COLOR.radialGlowMasked(
                x,
                y,
                w,
                h,
                radius,
                0f,
                48f * SCALE,
                x + w * 0.18f,
                y + h * 0.08f,
                LayoutRender2D.alpha(palette.moduleCardTopStrong(), openAlpha * (0.12f + 0.10f * hoverAnim))
        );
    }

    private void renderAuthBadge(AccountEntry entry, float x, float y, PanelColors c) {
        boolean microsoft = entry != null && entry.isMicrosoft();
        String label = microsoft ? tr("screen.combatant.alt_manager.badge.microsoft") : tr("screen.combatant.alt_manager.badge.offline");

        if (!microsoft) {
            draw(bodyRenderer, label, x, y + 0.7f * SCALE, OFFLINE_STATUS_FONT, c.muted);
            return;
        }

        float padX = 3.2f * SCALE;
        float badgeW = width(bodyRenderer, label, BADGE_FONT) + padX * 2f + 2f * SCALE;
        float badgeH = 7f * SCALE;
        int accent = HudRenderUtil.mixColor(c.strokeSoft, 0xFF72C7FF, 0.38f);
        UiPrimitive badge = parallelogram(x, y, badgeW, badgeH, 2.1f * SCALE, 0.55f * SCALE);
        Renderer2D.COLOR.primitive(badge, UiPaint.linear(
                withAlpha(HudRenderUtil.mixColor(c.surface, accent, 0.10f), 132),
                withAlpha(c.surface, 146), 90f, 0f));
        Renderer2D.COLOR.primitiveStroke(badge, UiPaint.solid(withAlpha(accent, 112)), UiStroke.of(0.28f * SCALE));
        draw(bodyRenderer, label, x + padX, y + 1.9f * SCALE, BADGE_FONT, c.label);
    }

    private void renderModeToggle(float mouseX, float mouseY, float x, float y, float w, float h, PanelColors c) {
        float gap = 2f * SCALE;
        float itemW = (w - gap) * 0.5f;
        renderModeButton(x, y, itemW, h, c, tr("screen.combatant.alt_manager.mode.offline"), !microsoftMode,
                inside(mouseX, mouseY, x, y, itemW, h), offlineModeHoverAnim, offlineModePressAnim);
        renderModeButton(x + itemW + gap, y, itemW, h, c, tr("screen.combatant.alt_manager.mode.microsoft"), microsoftMode,
                inside(mouseX, mouseY, x + itemW + gap, y, itemW, h), microsoftModeHoverAnim, microsoftModePressAnim);
    }

    private static UiPrimitive directional(float x, float y, float w, float h, float cut, float rounding) {
        return UiPrimitive.builder(x, y, w, h)
                .preset(UiPrimitive.Preset.DIRECTIONAL_RIGHT)
                .cut(cut)
                .rounding(rounding)
                .build();
    }

    private static UiPrimitive parallelogram(float x, float y, float w, float h, float cut, float rounding) {
        return UiPrimitive.builder(x, y, w, h)
                .preset(UiPrimitive.Preset.PARALLELOGRAM_RIGHT)
                .cut(cut)
                .rounding(rounding)
                .build();
    }

    private static UiPrimitive pointyHex(float centerX, float centerY, float radius, float rounding) {
        float width = MainMenuBackdrop.SQRT_3 * radius;
        return UiPrimitive.builder(centerX - width * 0.5f, centerY - radius, width, radius * 2f)
                .customConvex(
                        0.5, 0.0,
                        1.0, 0.25,
                        1.0, 0.75,
                        0.5, 1.0,
                        0.0, 0.75,
                        0.0, 0.25
                )
                .rounding(rounding)
                .build();
    }

    private static float elevatedTopY(float y, float hover, float press) {
        float settle = AnimationUtility.clamp01(1.0f - hover * 0.68f - press * 0.24f);
        return y - BUTTON_ELEVATION * settle;
    }

    private void renderElevatedButton(float x, float y, float w, float h,
                                      UiPrimitive.Preset preset, float cut, float rounding,
                                      int accent, int tint, float alpha, float hover, float press) {
        float topY = elevatedTopY(y, hover, press);
        float elevation = y - topY;
        UiPrimitive well = UiPrimitive.builder(x, y, w, h)
                .preset(preset)
                .cut(cut)
                .rounding(rounding)
                .build();

        // Match the main-menu hex depth hierarchy: dark socket, coloured sides, dense glass top.
        int wellTop = withAlpha(HudRenderUtil.mixColor(0xFF05080D, accent, 0.08f), Math.round(alpha * 154f));
        int wellBottom = withAlpha(HudRenderUtil.mixColor(0xFF020408, accent, 0.22f), Math.round(alpha * 112f));
        Renderer2D.COLOR.primitive(well, UiPaint.linear(wellTop, wellBottom, 90f, 0f));
        Renderer2D.COLOR.primitiveStroke(well,
                UiPaint.solid(withAlpha(accent, Math.round(alpha * (54f + hover * 34f)))),
                UiStroke.of(0.52f * SCALE));

        UiPrimitive top = UiPrimitive.builder(x, topY, w, h)
                .preset(preset)
                .cut(cut)
                .rounding(rounding)
                .build();
        if (elevation > 0.05f) renderPrimitiveExtrusion(top, elevation, accent, alpha);

        int denseTint = HudRenderUtil.mixColor(tint, 0xFFEAF5FF, 0.13f + hover * 0.05f);
        Renderer2D.COLOR.liquidGlassPrimitive(
                top,
                withAlpha(denseTint, Math.round(alpha * 238f)),
                alpha,
                1.0f,
                (9.6f + hover * 1.0f) * MainMenuBackdrop.MENU_SCALE,
                -9.0f,
                0.98f,
                0.84f,
                0.48f,
                0.036f * MainMenuBackdrop.MENU_SCALE,
                0.0f,
                0.0f,
                Renderer2D.BlurQuality.ULTRA,
                2.66f
        );
        Renderer2D.COLOR.primitiveStroke(top,
                UiPaint.solid(withAlpha(accent, Math.round(alpha * (150f + hover * 56f)))),
                UiStroke.of(1.34f * SCALE));
        Renderer2D.COLOR.primitiveStroke(top,
                UiPaint.solid(withAlpha(HudRenderUtil.mixColor(accent, 0xFFFFFFFF, 0.70f),
                        Math.round(alpha * (132f + hover * 38f)))),
                UiStroke.of(0.34f * SCALE));
    }

    private void renderHexButtonSurface(float centerX, float centerY, float radius, int accent, int tint,
                                        float alpha, float hover, float press) {
        float settle = AnimationUtility.clamp01(1.0f - hover * 0.68f - press * 0.24f);
        float elevation = BUTTON_ELEVATION * settle;
        UiPrimitive well = pointyHex(centerX, centerY, radius, 0.9f * MainMenuBackdrop.MENU_SCALE);
        Renderer2D.COLOR.primitive(well, UiPaint.linear(
                withAlpha(HudRenderUtil.mixColor(0xFF05080D, accent, 0.08f), Math.round(alpha * 158f)),
                withAlpha(HudRenderUtil.mixColor(0xFF020408, accent, 0.22f), Math.round(alpha * 114f)),
                90f, 0f));
        Renderer2D.COLOR.primitiveStroke(well,
                UiPaint.solid(withAlpha(accent, Math.round(alpha * (50f + hover * 34f)))),
                UiStroke.of(0.48f * SCALE));

        UiPrimitive top = pointyHex(centerX, centerY - elevation, radius, 0.9f * MainMenuBackdrop.MENU_SCALE);
        if (elevation > 0.05f) renderPrimitiveExtrusion(top, elevation, accent, alpha);
        int denseTint = HudRenderUtil.mixColor(tint, 0xFFEAF5FF, 0.13f + hover * 0.05f);
        Renderer2D.COLOR.liquidGlassPrimitive(
                top, withAlpha(denseTint, Math.round(alpha * 238f)), alpha, 1.0f,
                9.2f * MainMenuBackdrop.MENU_SCALE, -9.0f, 0.98f, 0.84f, 0.48f,
                0.036f * MainMenuBackdrop.MENU_SCALE, 0.0f, 0.0f,
                Renderer2D.BlurQuality.ULTRA, 2.62f
        );
        Renderer2D.COLOR.primitiveStroke(top,
                UiPaint.solid(withAlpha(accent, Math.round(alpha * (148f + hover * 58f)))),
                UiStroke.of(1.05f * SCALE));
        Renderer2D.COLOR.primitiveStroke(top,
                UiPaint.solid(withAlpha(HudRenderUtil.mixColor(accent, 0xFFFFFFFF, 0.70f),
                        Math.round(alpha * (126f + hover * 34f)))),
                UiStroke.of(0.32f * SCALE));
    }

    private void renderPrimitiveExtrusion(UiPrimitive top, float elevation, int accent, float alpha) {
        double[] points = top.points();
        int count = top.pointCount();
        if (points.length < 6 || count < 3) return;

        double centerX = top.bounds().x() + top.bounds().width() * 0.5;
        double centerY = top.bounds().y() + top.bounds().height() * 0.5;
        double halfW = Math.max(1.0, top.bounds().width() * 0.5);
        double halfH = Math.max(1.0, top.bounds().height() * 0.5);
        for (int i = 0; i < count; i++) {
            int j = (i + 1) % count;
            double x1 = points[i * 2];
            double y1 = points[i * 2 + 1];
            double x2 = points[j * 2];
            double y2 = points[j * 2 + 1];
            double midX = (x1 + x2) * 0.5;
            double midY = (y1 + y2) * 0.5;
            if (midY < centerY - top.bounds().height() * 0.04) continue;

            float nx = (float) ((midX - centerX) / halfW);
            float ny = AnimationUtility.clamp01((float) ((midY - centerY) / halfH));
            float blackMix;
            int faceAlpha;
            if (ny > 0.55f) {
                // Main-menu hexes deliberately keep the lower-right face brighter than lower-left.
                blackMix = nx >= 0f ? 0.59f : 0.78f;
                faceAlpha = Math.round(alpha * (nx >= 0f ? 224f : 218f));
            } else if (nx > 0.05f) {
                blackMix = 0.73f;
                faceAlpha = Math.round(alpha * 205f);
            } else {
                blackMix = 0.82f;
                faceAlpha = Math.round(alpha * 174f);
            }
            int side = withAlpha(HudRenderUtil.mixColor(accent, 0xFF020408, blackMix), faceAlpha);
            Renderer2D.COLOR.polygon(new double[]{
                    x1, y1,
                    x1, y1 + elevation,
                    x2, y2 + elevation,
                    x2, y2
            }, 4, side);
        }
    }

    private void renderModeButton(float x, float y, float w, float h, PanelColors c, String label, boolean selected,
                                  boolean hovered, float hoverValue, float pressValue) {
        float hoverAnim = AnimationUtility.easeOutCubic(hoverValue);
        float pressAnim = AnimationUtility.easeOutCubic(pressValue);
        int accent = selected
                ? HudRenderUtil.mixColor(c.accentSoft, c.accent, 0.48f)
                : HudRenderUtil.mixColor(c.strokeSoft, c.accentSoft, hoverAnim * 0.46f);
        int tint = selected
                ? HudRenderUtil.mixColor(c.surfaceHover, c.accentSoft, 0.34f)
                : HudRenderUtil.mixColor(c.surface, c.surfaceHover, hoverAnim * 0.54f);
        renderElevatedButton(
                x, y, w, h,
                UiPrimitive.Preset.PARALLELOGRAM_RIGHT, 4.2f * SCALE, 0.95f * SCALE,
                accent, tint, 0.97f, hoverAnim, pressAnim
        );
        float topY = elevatedTopY(y, hoverAnim, pressAnim);
        int text = selected
                ? HudRenderUtil.mixColor(c.title, c.accent, 0.30f)
                : HudRenderUtil.mixColor(c.muted, c.title, hoverAnim * 0.58f);
        drawCentered(bodyRenderer, label, x + w * 0.5f, topY + 4.1f * SCALE, BUTTON_FONT, text);
    }

    private void renderMicrosoftField(float x, float y, float w, float h, PanelColors c) {
        float hoverAnim = AnimationUtility.easeOutCubic(fieldHoverAnim);
        UiPrimitive field = directional(x, y, w, h, 4.2f * SCALE, 0.9f * SCALE);
        int fillTop = HudRenderUtil.mixColor(withAlpha(c.surface, 224), withAlpha(c.surfaceHover, 244), hoverAnim * 0.48f);
        int fillBottom = HudRenderUtil.mixColor(withAlpha(c.surface, 214), withAlpha(c.accentSoft, 118), microsoftAuthPending ? 0.22f : 0.08f);
        int stroke = HudRenderUtil.mixColor(c.stroke, microsoftAuthPending ? c.accentSoft : c.strokeSoft, microsoftAuthPending ? 0.58f : hoverAnim * 0.62f);
        Renderer2D.COLOR.primitive(field, UiPaint.linear(fillTop, fillBottom, 90f, 0f));
        Renderer2D.COLOR.primitiveStroke(field, UiPaint.solid(withAlpha(stroke, 202)), UiStroke.of(0.62f * MainMenuBackdrop.MENU_SCALE));
        if (microsoftAuthPending) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(AnimationUtility.time(0.006f, AnimationUtility.Mode.MILLIS));
            UiPrimitive pulseBar = parallelogram(x + 4f * SCALE, y + h - 1.6f * SCALE, w - 8f * SCALE, 1.0f * SCALE, 1.0f * SCALE, 0.35f * SCALE);
            Renderer2D.COLOR.primitive(pulseBar, UiPaint.linear(
                    withAlpha(c.accentSoft, Math.round(72f + 44f * pulse)),
                    withAlpha(c.accent, Math.round(48f + 36f * pulse)), 0f, 0f));
        }

        String display;
        int color;
        if (!microsoftUserCode.isBlank()) {
            display = tr("screen.combatant.alt_manager.code", microsoftUserCode);
            color = c.title;
        } else if (!microsoftStatus.isBlank()) {
            display = microsoftStatus;
            color = microsoftAuthPending ? c.title : c.muted;
        } else {
            display = tr("screen.combatant.alt_manager.ready_to_sign_in");
            color = c.mutedLabel;
        }
        draw(bodyRenderer, ellipsize(display, FIELD_FONT, w - 11f * SCALE), x + 5f * SCALE, y + 4.3f * SCALE, FIELD_FONT, color);
    }

    private void renderField(float x, float y, float w, float h, PanelColors c, boolean hovered) {
        float hoverAnim = AnimationUtility.easeOutCubic(fieldHoverAnim);
        float focusAnim = AnimationUtility.easeOutCubic(fieldFocusAnim);
        UiPrimitive field = directional(x, y, w, h, 4.2f * SCALE, 0.9f * SCALE);
        int fillTop = HudRenderUtil.mixColor(withAlpha(c.surface, 224), withAlpha(c.surfaceHover, 244), hoverAnim * 0.50f + focusAnim * 0.20f);
        int fillBottom = HudRenderUtil.mixColor(withAlpha(c.surface, 214), withAlpha(c.accentSoft, 124), focusAnim * 0.16f);
        int stroke = nicknameFieldFocused
                ? HudRenderUtil.mixColor(c.strokeSoft, c.accentSoft, 0.68f)
                : HudRenderUtil.mixColor(c.stroke, c.strokeSoft, hoverAnim * 0.72f);
        Renderer2D.COLOR.primitive(field, UiPaint.linear(fillTop, fillBottom, 90f, 0f));
        Renderer2D.COLOR.primitiveStroke(field, UiPaint.solid(withAlpha(stroke, 206)), UiStroke.of(0.62f * MainMenuBackdrop.MENU_SCALE));
        if (focusAnim > 0.01f) {
            UiPrimitive focusBar = parallelogram(x + 4f * SCALE, y + h - 1.6f * SCALE, w - 8f * SCALE, 1.0f * SCALE, 1.0f * SCALE, 0.35f * SCALE);
            Renderer2D.COLOR.primitive(focusBar, UiPaint.linear(
                    withAlpha(c.accentSoft, Math.round(104f * focusAnim)),
                    withAlpha(c.accent, Math.round(72f * focusAnim)), 0f, 0f));
        }

        boolean caretOn = nicknameFieldFocused && AnimationUtility.blink(500L, AnimationUtility.Mode.MILLIS);
        String display = nicknameText.isEmpty() && !nicknameFieldFocused ? tr("screen.combatant.alt_manager.enter_nick") : nicknameText + (caretOn ? "|" : "");
        draw(bodyRenderer, display, x + 5f * SCALE, y + 4.3f * SCALE, FIELD_FONT, nicknameText.isEmpty() && !nicknameFieldFocused ? c.mutedLabel : c.title);
    }

    private void renderAddButton(float x, float y, PanelColors c) {
        float hoverAnim = AnimationUtility.easeOutCubic(addHoverAnim);
        float pressAnim = AnimationUtility.easeOutCubic(addPressAnim);
        float radius = ADD_SIZE * 0.5f;
        float cx = x + ADD_SIZE * 0.5f;
        float cy = y + ADD_SIZE * 0.5f;
        int accent = HudRenderUtil.mixColor(c.accentSoft, c.accent, 0.30f + hoverAnim * 0.28f);
        int tint = HudRenderUtil.mixColor(c.surfaceHover, c.accentSoft, 0.20f + hoverAnim * 0.14f);
        renderHexButtonSurface(cx, cy, radius, accent, tint, 0.98f, hoverAnim, pressAnim);

        float settle = AnimationUtility.clamp01(1.0f - hoverAnim * 0.68f - pressAnim * 0.24f);
        float lift = BUTTON_ELEVATION * settle;
        float topCy = cy - lift;
        float plusStretch = 1f + hoverAnim * 0.05f + pressAnim * 0.06f;
        Renderer2D.COLOR.roundedRect(cx - 2.5f * SCALE * plusStretch, topCy - 0.55f * SCALE, 5f * SCALE * plusStretch, 1.1f * SCALE, 0.45f * SCALE, 1f, c.title);
        Renderer2D.COLOR.roundedRect(cx - 0.55f * SCALE, topCy - 2.5f * SCALE * plusStretch, 1.1f * SCALE, 5f * SCALE * plusStretch, 0.45f * SCALE, 1f, c.title);
    }

    private void renderAction(float x, float y, float w, float h, PanelColors c, boolean destructive, String label, TextRenderer iconRenderer, String glyph, float hoverValue, float pressValue) {
        renderAction(x, y, w, h, c, destructive, label, iconRenderer, glyph, null, hoverValue, pressValue);
    }

    private void renderSvgAction(float x, float y, float w, float h, PanelColors c, boolean destructive, String label, String svgName, float hoverValue, float pressValue) {
        renderAction(x, y, w, h, c, destructive, label, null, "", svgName, hoverValue, pressValue);
    }

    private void renderAction(float x, float y, float w, float h, PanelColors c, boolean destructive, String label, TextRenderer iconRenderer, String glyph, String svgName, float hoverValue, float pressValue) {
        float hoverAnim = AnimationUtility.easeOutCubic(hoverValue);
        float pressAnim = AnimationUtility.easeOutCubic(pressValue);
        int accent;
        int tint;
        int text;
        if (destructive) {
            accent = HudRenderUtil.mixColor(withAlpha(0xFFB64D59, 255), withAlpha(0xFFFF7080, 255), hoverAnim * 0.42f);
            tint = HudRenderUtil.mixColor(withAlpha(0xFF24151B, 255), withAlpha(0xFF51242E, 255), 0.34f + hoverAnim * 0.30f);
            text = HudRenderUtil.mixColor(withAlpha(0xFFF0D5D9, 255), withAlpha(0xFFFFFFFF, 255), hoverAnim * 0.54f);
        } else {
            accent = HudRenderUtil.mixColor(c.accentSoft, c.accent, 0.28f + hoverAnim * 0.30f);
            tint = HudRenderUtil.mixColor(c.surfaceHover, c.accentSoft, 0.16f + hoverAnim * 0.18f);
            text = HudRenderUtil.mixColor(c.label, c.title, 0.30f + hoverAnim * 0.56f);
        }

        renderElevatedButton(
                x, y, w, h,
                UiPrimitive.Preset.PARALLELOGRAM_RIGHT, 5.0f * SCALE, 0.95f * SCALE,
                accent, tint, 0.98f, hoverAnim, pressAnim
        );
        float topY = elevatedTopY(y, hoverAnim, pressAnim);
        draw(bodyRenderer, label, x + 7f * SCALE, topY + 5f * SCALE, BUTTON_FONT, text);
        if (svgName != null && !svgName.isBlank()) {
            float iconSize = 7.2f * SCALE;
            Renderer2D.COLOR.svg(svgName, x + w - 13.2f * SCALE, topY + (h - iconSize) * 0.5f, iconSize, iconSize, SvgRenderOptions.overrideColor(text));
        } else if (iconRenderer != null && glyph != null && !glyph.isBlank()) {
            float iconW = width(iconRenderer, glyph, ACTION_ICON_FONT);
            draw(iconRenderer, glyph, x + w - iconW - 8f * SCALE, topY + 3.0f * SCALE, ACTION_ICON_FONT, text);
        }
    }

    private boolean handleClick(float mouseX, float mouseY) {
        Layout l = layout();

        float modeY = l.topY + 27f * SCALE;
        float modeX = l.leftX + 5f * SCALE;
        float modeW = LEFT_W - 10f * SCALE;
        float modeGap = 2f * SCALE;
        float modeItemW = (modeW - modeGap) * 0.5f;
        if (inside(mouseX, mouseY, modeX, modeY, modeItemW, 13f * SCALE)) {
            offlineModePressAnim = 1f;
            microsoftMode = false;
            nicknameFieldFocused = false;
            return true;
        }
        if (inside(mouseX, mouseY, modeX + modeItemW + modeGap, modeY, modeItemW, 13f * SCALE)) {
            microsoftModePressAnim = 1f;
            microsoftMode = true;
            nicknameFieldFocused = false;
            return true;
        }

        float fieldX = l.leftX + 5f * SCALE;
        float fieldY = l.topY + NICK_FIELD_Y;
        float fieldW = LEFT_W - 10f * SCALE - ADD_SIZE - 3f * SCALE;
        if (!microsoftMode && inside(mouseX, mouseY, fieldX, fieldY, fieldW, FIELD_H)) {
            nicknameFieldFocused = true;
            return true;
        }
        nicknameFieldFocused = false;
        if (microsoftMode && inside(mouseX, mouseY, fieldX, fieldY, fieldW, FIELD_H)) {
            copyMicrosoftAuthData();
            return true;
        }

        float addX = fieldX + fieldW + 3f * SCALE;
        if (inside(mouseX, mouseY, addX, fieldY, ADD_SIZE, ADD_SIZE)) {
            addPressAnim = 1f;
            if (microsoftMode) startMicrosoftAuthorization();
            else commitNickname();
            return true;
        }

        float buttonX = l.leftX + 5f * SCALE;
        float buttonW = LEFT_W - 10f * SCALE;
        float randomY = l.topY + PRIMARY_ACTION_Y;
        float openSiteY = randomY + TOP_BUTTON_H + 5f * SCALE;
        float clearY = (microsoftMode ? openSiteY : randomY) + TOP_BUTTON_H + 5f * SCALE;
        if (inside(mouseX, mouseY, buttonX, randomY, buttonW, TOP_BUTTON_H)) {
            randomPressAnim = 1f;
            if (microsoftMode) {
                if (microsoftUserCode.isBlank()) startMicrosoftAuthorization();
                else copyMicrosoftAuthData();
            } else {
                addAccount(generateRandomNickname());
                nicknameText = "";
            }
            return true;
        }
        if (microsoftMode && inside(mouseX, mouseY, buttonX, openSiteY, buttonW, TOP_BUTTON_H)) {
            openSitePressAnim = 1f;
            openMicrosoftLoginSite();
            return true;
        }
        if (inside(mouseX, mouseY, buttonX, clearY, buttonW, TOP_BUTTON_H)) {
            clearPressAnim = 1f;
            accountConfig.clearAllAccounts();
            SkinManager.clearCache();
            scrollOffset = 0f;
            targetScrollOffset = 0f;
            return true;
        }

        float listX = l.rightX + 5f * SCALE;
        float listY = l.topY + 28f * SCALE;
        float listW = RIGHT_W - 10f * SCALE;
        float listH = RIGHT_H - 31f * SCALE;
        if (!inside(mouseX, mouseY, listX, listY, listW, listH)) return true;

        List<AccountEntry> accounts = accountConfig.getSortedAccounts();
        float cardW = (listW - CARD_GAP) * 0.5f;
        for (int i = 0; i < accounts.size(); i++) {
            int col = i % 2;
            int row = i / 2;
            float cardX = listX + col * (cardW + CARD_GAP);
            float cardY = listY + row * (CARD_H + CARD_GAP) - scrollOffset;
            if (cardY + CARD_H < listY || cardY > listY + listH) continue;

            float btnY = cardY + CARD_H - CARD_BTN - 5f * SCALE;
            float pinX = cardX + cardW - CARD_BTN * 2f - 8f * SCALE;
            float delX = cardX + cardW - CARD_BTN - 5f * SCALE;
            AccountEntry entry = accounts.get(i);

            if (inside(mouseX, mouseY, pinX, btnY, CARD_BTN, CARD_BTN)) {
                pinPressAnims.put(entry.getName(), 1f);
                entry.togglePinned();
                if (entry.isPinned()) accountConfig.setActiveAccount(entry);
                else accountConfig.save();
                return true;
            }
            if (inside(mouseX, mouseY, delX, btnY, CARD_BTN, CARD_BTN)) {
                deletePressAnims.put(entry.getName(), 1f);
                accountConfig.removeAccountBySortedIndex(i);
                clampScroll();
                return true;
            }
            if (inside(mouseX, mouseY, cardX, cardY, cardW, CARD_H)) {
                cardPressAnims.put(entry.getName(), 1f);
                accountConfig.setActiveAccount(entry);
                return true;
            }
        }

        return true;
    }

    private void renderWindow(WindowBounds bounds, PanelColors c, float open) {
        float bodyAlpha = 0.78f + open * 0.22f;
        SettingsGuiPalette palette = SettingsGuiPalette.current();

        UiPrimitive top = windowPrimitive(bounds.x, bounds.y, bounds.w, bounds.h);

        int glassTint = HudRenderUtil.mixColor(palette.workspaceGlassTint(), c.surfaceHover, 0.14f);
        Renderer2D.COLOR.liquidGlassPrimitive(
                top,
                withAlpha(glassTint, Math.round(238f * bodyAlpha)),
                bodyAlpha,
                1.0f,
                10.2f * MainMenuBackdrop.MENU_SCALE,
                -8.0f,
                0.98f,
                0.82f,
                0.44f,
                0.036f * MainMenuBackdrop.MENU_SCALE,
                0.0f,
                0.0f,
                Renderer2D.BlurQuality.ULTRA,
                2.62f
        );

        // Settings-like neutral wash. Accent belongs to controls/selection, not the whole frame.
        Renderer2D.COLOR.primitive(top, UiPaint.corners(
                withAlpha(palette.menuWindowBgLeft(), Math.round(72f * bodyAlpha)),
                withAlpha(palette.menuWindowBgRight(), Math.round(84f * bodyAlpha)),
                withAlpha(palette.menuWindowBgRight(), Math.round(96f * bodyAlpha)),
                withAlpha(palette.menuWindowBgLeft(), Math.round(78f * bodyAlpha))
        ));
        Renderer2D.COLOR.primitiveStroke(top,
                UiPaint.solid(withAlpha(c.strokeSoft, Math.round(112f * bodyAlpha))),
                UiStroke.of(0.72f * SCALE));
        Renderer2D.COLOR.primitiveStroke(top,
                UiPaint.solid(withAlpha(HudRenderUtil.mixColor(c.strokeSoft, 0xFFFFFFFF, 0.24f),
                        Math.round(66f * bodyAlpha))),
                UiStroke.of(0.26f * SCALE));
    }

    private void renderPanel(float x, float y, float w, float h, PanelColors c, float open) {
        SettingsGuiPalette palette = SettingsGuiPalette.current();
        UiPrimitive panel = UiPrimitive.builder(x, y, w, h)
                .preset(UiPrimitive.Preset.DIRECTIONAL_RIGHT)
                .cut(5.2f * SCALE)
                .rounding(PANEL_R)
                .build();
        Renderer2D.COLOR.primitive(panel, UiPaint.corners(
                withAlpha(palette.contentPlaneTop(), Math.round(open * 96f)),
                withAlpha(palette.navigationPlaneTop(), Math.round(open * 110f)),
                withAlpha(palette.navigationPlaneBottom(), Math.round(open * 122f)),
                withAlpha(palette.contentPlaneBottom(), Math.round(open * 104f))
        ));
        Renderer2D.COLOR.primitiveStroke(panel,
                UiPaint.solid(withAlpha(c.stroke, Math.round(open * 86f))),
                UiStroke.of(0.42f * SCALE));
    }

    private PanelColors colors(float alpha) {
        int titleAlpha = Math.round(alpha * 255f);
        int labelAlpha = Math.round(alpha * 212f);

        SettingsGuiPalette palette = SettingsGuiPalette.current();
        Themes.Theme theme = Theme.theme();
        int themeAccent = theme != null ? theme.accent() : 0xFF7A6BFF;
        int themeAccentSoft = theme != null ? theme.accentSoft() : 0xFF5D72C8;
        int windowBg = theme != null ? theme.windowBg() : 0xFF111823;
        int surfaceTheme = theme != null ? theme.surface() : 0xFF18202B;
        int surfaceHoverTheme = theme != null ? theme.surfaceHover() : 0xFF263345;

        int surface = withAlpha(HudRenderUtil.mixColor(windowBg, surfaceTheme, 0.42f), Math.round(alpha * 222f));
        int surfaceHover = withAlpha(HudRenderUtil.mixColor(surfaceTheme, surfaceHoverTheme, 0.46f), Math.round(alpha * 238f));
        int stroke = withAlpha(palette.glassEdgeSoft(), Math.round(alpha * 116f));
        int strokeSoft = withAlpha(palette.glassEdgeStrong(), Math.round(alpha * 172f));
        int accent = withAlpha(themeAccent, titleAlpha);
        int accentSoft = withAlpha(HudRenderUtil.mixColor(themeAccentSoft, themeAccent, 0.18f), Math.round(alpha * 232f));

        return new PanelColors(
                surface, surfaceHover, surface, surfaceHover,
                surfaceHover, surfaceHover, surface, surface,
                stroke, strokeSoft,
                withAlpha(0x02050A, Math.round(alpha * 150f)),
                withAlpha(0xFFFFFF, titleAlpha),
                withAlpha(0xE9F2FC, labelAlpha),
                withAlpha(0xB8C5D3, Math.round(alpha * 228f)),
                withAlpha(0x8797A9, Math.round(alpha * 194f)),
                surface, surfaceHover, accent, accentSoft
        );
    }

    private void renderBackgroundTexture() {
        Minecraft mc = this.minecraft;
        if (mc != null) MenuBackgroundRenderer.renderConfigured(mc);
    }

    private static UiPrimitive windowPrimitive(float x, float y, float w, float h) {
        return UiPrimitive.builder(x, y, w, h)
                .preset(UiPrimitive.Preset.CHAMFERED)
                .cut(WINDOW_CUT)
                .rounding(WINDOW_ROUNDING)
                .build();
    }

    private void updateUiMetrics() {
        Minecraft mc = minecraft;
        if (mc == null) {
            fixedWidth = width;
            fixedHeight = height;
            return;
        }
        int fbw = mc.getWindow().getWidth();
        int fbh = mc.getWindow().getHeight();
        fixedWidth = Math.max(1f, HudScale.virtualWidth(fbw, fbh));
        fixedHeight = Math.max(1f, HudScale.virtualHeight(fbw, fbh));
    }

    private float toFixedX(float screenX) {
        Minecraft mc = minecraft;
        if (mc == null) return screenX;
        float hudScale = HudScale.scale(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        if (hudScale <= 0f) return screenX;
        return screenX * mc.getWindow().getGuiScale() / hudScale;
    }

    private float toFixedY(float screenY) {
        Minecraft mc = minecraft;
        if (mc == null) return screenY;
        float hudScale = HudScale.scale(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        if (hudScale <= 0f) return screenY;
        return screenY * mc.getWindow().getGuiScale() / hudScale;
    }

    private Layout layout() {
        float contentW = LEFT_W + GAP + RIGHT_W;
        float contentH = LEFT_TOP_H + GAP + LEFT_BOTTOM_H;
        float windowW = contentW + WINDOW_PAD * 2f;
        float windowH = contentH + WINDOW_PAD * 2f;
        float windowX = fixedWidth * 0.5f - windowW * 0.5f;
        float windowY = fixedHeight * 0.5f - windowH * 0.5f;
        float leftX = windowX + WINDOW_PAD;
        float topY = windowY + WINDOW_PAD;
        return new Layout(
                leftX,
                topY,
                leftX + LEFT_W + GAP,
                topY + LEFT_TOP_H + GAP
        );
    }

    private WindowBounds windowBounds(Layout layout) {
        float contentW = LEFT_W + GAP + RIGHT_W;
        float contentH = LEFT_TOP_H + GAP + LEFT_BOTTOM_H;
        return new WindowBounds(
                layout.leftX - WINDOW_PAD,
                layout.topY - WINDOW_PAD,
                contentW + WINDOW_PAD * 2f,
                contentH + WINDOW_PAD * 2f
        );
    }

    private void commitNickname() {
        if (!nicknameText.isBlank()) {
            addAccount(nicknameText);
            nicknameText = "";
        }
    }

    private void addAccount(String nickname) {
        String clean = normalize(nickname);
        if (clean.isEmpty()) return;
        AccountEntry entry = accountConfig.addAccount(clean, LocalDateTime.now().format(ACCOUNT_DATE), null);
        if (entry != null) {
            accountConfig.setActiveAccount(entry);
            SkinManager.getSkin(clean);
        }
    }

    private void startMicrosoftAuthorization() {
        if (microsoftAuthPending) {
            copyMicrosoftAuthData();
            return;
        }

        microsoftAuthPending = true;
        microsoftUserCode = "";
        microsoftVerificationUrl = "";
        microsoftStatus = tr("screen.combatant.alt_manager.status.requesting_code");

        MicrosoftAuthService.requestDeviceCode()
                .thenCompose(code -> {
                    dispatchToClient(() -> showMicrosoftDeviceCode(code));
                    return MicrosoftAuthService.loginWithDeviceCode(code);
                })
                .whenComplete((session, throwable) -> dispatchToClient(() -> {
                    if (throwable != null) {
                        finishMicrosoftAuthorization(null, throwable);
                    } else {
                        finishMicrosoftAuthorization(session, null);
                    }
                }));
    }

    private void showMicrosoftDeviceCode(MicrosoftDeviceCode code) {
        if (code == null) {
            return;
        }
        microsoftUserCode = code.userCode();
        microsoftVerificationUrl = code.verificationUri().toString();
        microsoftStatus = tr("screen.combatant.alt_manager.status.use_link");
        copyMicrosoftAuthData();
    }

    private void finishMicrosoftAuthorization(MicrosoftSessionResult session, Throwable throwable) {
        microsoftAuthPending = false;
        if (throwable != null) {
            microsoftStatus = tr("screen.combatant.alt_manager.status.auth_failed");
            microsoftUserCode = "";
            microsoftVerificationUrl = "";
            Throwable root = unwrap(throwable);
            if (root != null && root.getMessage() != null && !root.getMessage().isBlank()) {
                microsoftStatus = ellipsizeStatus(root.getMessage());
            }
            return;
        }

        AccountEntry entry = accountConfig.addMicrosoftAccount(session);
        if (entry == null) {
            microsoftStatus = tr("screen.combatant.alt_manager.status.auth_failed");
            microsoftUserCode = "";
            microsoftVerificationUrl = "";
            return;
        }

        accountConfig.setActiveAccount(entry);
        SkinManager.getSkin(entry.getName());
        microsoftStatus = tr("screen.combatant.alt_manager.status.authorized", entry.getName());
        microsoftUserCode = "";
        microsoftVerificationUrl = "";
        clampScroll();
    }

    private void copyMicrosoftAuthData() {
        if (!microsoftUserCode.isBlank()) {
            ClipboardUtil.copy(microsoftUserCode);
            microsoftStatus = tr("screen.combatant.alt_manager.status.code_copied");
        } else if (!microsoftVerificationUrl.isBlank()) {
            ClipboardUtil.copy(microsoftVerificationUrl);
            microsoftStatus = tr("screen.combatant.alt_manager.status.link_copied");
        }
    }

    private void openMicrosoftLoginSite() {
        String target = microsoftVerificationUrl.isBlank() ? "https://www.microsoft.com/link" : microsoftVerificationUrl;
        Util.getPlatform().openUri(target);
    }

    private void dispatchToClient(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        Minecraft mc = minecraft != null ? minecraft : Minecraft.getInstance();
        if (mc == null) {
            runnable.run();
            return;
        }
        if (mc.isSameThread()) {
            runnable.run();
        } else {
            mc.execute(runnable);
        }
    }

    private static Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof CompletionException || current instanceof ExecutionException) && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static String ellipsizeStatus(String text) {
        String clean = normalize(text);
        return clean.length() <= 34 ? clean : clean.substring(0, 31) + "...";
    }

    private void clampScroll() {
        float max = Math.max(0f, (float) Math.ceil(accountConfig.getSortedAccounts().size() / 2.0) * (CARD_H + CARD_GAP) - (RIGHT_H - 31f * SCALE));
        if (scrollOffset > max) scrollOffset = max;
        if (targetScrollOffset > max) targetScrollOffset = max;
        if (scrollOffset < 0f) scrollOffset = 0f;
        if (targetScrollOffset < 0f) targetScrollOffset = 0f;
    }

    private void updateAnimations(float mouseX, float mouseY) {
        float dt = AnimationUtility.deltaTime();
        openAnim = animate(openAnim, 1f, dt, 7f);
        scrollOffset = animate(scrollOffset, targetScrollOffset, dt, 14f);
        scrollOffset = AnimationUtility.snap(scrollOffset, targetScrollOffset, 0.25f);

        Layout l = layout();
        float fieldX = l.leftX + 5f * SCALE;
        float fieldY = l.topY + NICK_FIELD_Y;
        float fieldW = LEFT_W - 10f * SCALE - ADD_SIZE - 3f * SCALE;
        float addX = fieldX + fieldW + 3f * SCALE;
        float buttonX = l.leftX + 5f * SCALE;
        float buttonW = LEFT_W - 10f * SCALE;
        float randomY = l.topY + PRIMARY_ACTION_Y;
        float openSiteY = randomY + TOP_BUTTON_H + 5f * SCALE;
        float clearY = (microsoftMode ? openSiteY : randomY) + TOP_BUTTON_H + 5f * SCALE;
        float modeX = l.leftX + 5f * SCALE;
        float modeY = l.topY + 27f * SCALE;
        float modeW = LEFT_W - 10f * SCALE;
        float modeGap = 2f * SCALE;
        float modeItemW = (modeW - modeGap) * 0.5f;

        fieldHoverAnim = animate(fieldHoverAnim, inside(mouseX, mouseY, fieldX, fieldY, fieldW, FIELD_H) ? 1f : 0f, dt, 11f);
        fieldFocusAnim = animate(fieldFocusAnim, !microsoftMode && nicknameFieldFocused ? 1f : 0f, dt, 10f);
        addHoverAnim = animate(addHoverAnim, inside(mouseX, mouseY, addX, fieldY, ADD_SIZE, ADD_SIZE) ? 1f : 0f, dt, 12f);
        offlineModeHoverAnim = animate(offlineModeHoverAnim, inside(mouseX, mouseY, modeX, modeY, modeItemW, 13f * SCALE) ? 1f : 0f, dt, 11f);
        microsoftModeHoverAnim = animate(microsoftModeHoverAnim, inside(mouseX, mouseY, modeX + modeItemW + modeGap, modeY, modeItemW, 13f * SCALE) ? 1f : 0f, dt, 11f);
        offlineModePressAnim = animate(offlineModePressAnim, 0f, dt, 8f);
        microsoftModePressAnim = animate(microsoftModePressAnim, 0f, dt, 8f);
        randomHoverAnim = animate(randomHoverAnim, inside(mouseX, mouseY, buttonX, randomY, buttonW, TOP_BUTTON_H) ? 1f : 0f, dt, 11f);
        openSiteHoverAnim = animate(openSiteHoverAnim, microsoftMode && inside(mouseX, mouseY, buttonX, openSiteY, buttonW, TOP_BUTTON_H) ? 1f : 0f, dt, 11f);
        clearHoverAnim = animate(clearHoverAnim, inside(mouseX, mouseY, buttonX, clearY, buttonW, TOP_BUTTON_H) ? 1f : 0f, dt, 11f);
        addPressAnim = animate(addPressAnim, 0f, dt, 8f);
        randomPressAnim = animate(randomPressAnim, 0f, dt, 7f);
        openSitePressAnim = animate(openSitePressAnim, 0f, dt, 7f);
        clearPressAnim = animate(clearPressAnim, 0f, dt, 7f);

        float listX = l.rightX + 5f * SCALE;
        float listY = l.topY + 28f * SCALE;
        float listW = RIGHT_W - 10f * SCALE;
        float listH = RIGHT_H - 31f * SCALE;
        float cardW = (listW - CARD_GAP) * 0.5f;
        String active = accountConfig.getActiveAccountName();
        List<AccountEntry> accounts = accountConfig.getSortedAccounts();
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < accounts.size(); i++) {
            AccountEntry entry = accounts.get(i);
            String key = entry.getName();
            keys.add(key);
            int col = i % 2;
            int row = i / 2;
            float cardX = listX + col * (cardW + CARD_GAP);
            float cardY = listY + row * (CARD_H + CARD_GAP) - scrollOffset;
            float btnY = cardY + CARD_H - CARD_BTN - 5f * SCALE;
            float pinX = cardX + cardW - CARD_BTN * 2f - 8f * SCALE;
            float delX = cardX + cardW - CARD_BTN - 5f * SCALE;
            boolean inList = mouseY >= listY && mouseY <= listY + listH;
            boolean cardHover = inList && inside(mouseX, mouseY, cardX, cardY, cardW, CARD_H);
            boolean pinHover = inList && inside(mouseX, mouseY, pinX, btnY, CARD_BTN, CARD_BTN);
            boolean delHover = inList && inside(mouseX, mouseY, delX, btnY, CARD_BTN, CARD_BTN);
            cardHoverAnims.put(key, animate(cardHoverAnims.getOrDefault(key, 0f), cardHover ? 1f : 0f, dt, 10f));
            cardActiveAnims.put(key, animate(cardActiveAnims.getOrDefault(key, 0f), key.equals(active) ? 1f : 0f, dt, 8f));
            cardPressAnims.put(key, animate(cardPressAnims.getOrDefault(key, 0f), 0f, dt, 7f));
            pinHoverAnims.put(key, animate(pinHoverAnims.getOrDefault(key, 0f), pinHover ? 1f : 0f, dt, 11f));
            pinPressAnims.put(key, animate(pinPressAnims.getOrDefault(key, 0f), 0f, dt, 8f));
            deleteHoverAnims.put(key, animate(deleteHoverAnims.getOrDefault(key, 0f), delHover ? 1f : 0f, dt, 11f));
            deletePressAnims.put(key, animate(deletePressAnims.getOrDefault(key, 0f), 0f, dt, 8f));
        }
        prune(cardHoverAnims, keys);
        prune(cardActiveAnims, keys);
        prune(cardPressAnims, keys);
        prune(pinHoverAnims, keys);
        prune(pinPressAnims, keys);
        prune(deleteHoverAnims, keys);
        prune(deletePressAnims, keys);
    }

    private String generateRandomNickname() {
        Random random = new Random();
        StringBuilder username = new StringBuilder();
        char[] vowels = {'a', 'e', 'i', 'o', 'u'};
        char[] consonants = {'b', 'c', 'd', 'f', 'g', 'h', 'j', 'k', 'l', 'm', 'n', 'p', 'r', 's', 't', 'v', 'w', 'x', 'y', 'z'};
        for (int attempts = 0; attempts < 10; attempts++) {
            username.setLength(0);
            int length = 6 + random.nextInt(5);
            boolean startVowel = random.nextBoolean();
            for (int i = 0; i < length; i++) {
                username.append((i & 1) == 0 ? (startVowel ? vowels[random.nextInt(vowels.length)] : consonants[random.nextInt(consonants.length)]) : (startVowel ? consonants[random.nextInt(consonants.length)] : vowels[random.nextInt(vowels.length)]));
            }
            if (random.nextInt(100) < 30) username.append(random.nextInt(100));
            String candidate = Character.toUpperCase(username.charAt(0)) + username.substring(1);
            if (accountConfig.findByName(candidate) == null) return candidate;
        }
        return "User" + (System.currentTimeMillis() % 1000L);
    }

    private void closeToParent() {
        if (minecraft != null) ClientScreen.show(minecraft, parent);
    }

    private void draw(TextRenderer renderer, String text, float x, float y, float size, int argb) {
        if (renderer == null || text == null || text.isEmpty()) return;
        renderer.setAlpha(1.0);
        renderer.begin(size, false, false);
        renderer.render(text, x, y, new RenderColor(argb), false);
        renderer.end();
    }

    private void drawCentered(TextRenderer renderer, String text, float centerX, float y, float size, int argb) {
        draw(renderer, text, centerX - width(renderer, text, size) * 0.5f, y, size, argb);
    }

    private float width(TextRenderer renderer, String text, float size) {
        if (renderer == null || text == null) return 0f;
        renderer.begin(size, true, false);
        float w = (float) renderer.getWidth(text, false);
        renderer.end();
        return w;
    }

    private String ellipsize(String text, float size, float maxWidth) {
        if (text == null || text.isEmpty()) return "";
        if (width(bodyRenderer, text, size) <= maxWidth) return text;
        String out = text;
        while (out.length() > 3 && width(bodyRenderer, out + "...", size) > maxWidth)
            out = out.substring(0, out.length() - 1);
        return out + "...";
    }

    private record Layout(float leftX, float topY, float rightX, float bottomY) {
    }

    private record WindowBounds(float x, float y, float w, float h) {
    }

    private record PanelColors(
            int bgTopLeft,
            int bgTopRight,
            int bgBottomRight,
            int bgBottomLeft,
            int headerTopLeft,
            int headerTopRight,
            int headerBottomRight,
            int headerBottomLeft,
            int stroke,
            int strokeSoft,
            int shadow,
            int title,
            int label,
            int muted,
            int mutedLabel,
            int surface,
            int surfaceHover,
            int accent,
            int accentSoft
    ) {
    }
}
