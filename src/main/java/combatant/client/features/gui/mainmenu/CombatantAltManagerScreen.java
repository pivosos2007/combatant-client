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
import combatant.client.config.MainConfig;
import combatant.client.features.account.AccountConfig;
import combatant.client.features.account.AccountEntry;
import combatant.client.features.account.SkinManager;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.theme.Themes;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.core.ViewportContext;
import combatant.client.render.engine.math.HudScale;
import combatant.client.render.engine.postprocess.MenuBackgroundRenderer;
import combatant.client.render.engine.renderer.Renderer2D;
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
    private static final float SCALE = 2.28f;
    private static final float TEXT_SCALE = 1.30f;
    private static final DateTimeFormatter ACCOUNT_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private static final float LEFT_W = 100f * SCALE;
    private static final float LEFT_TOP_H = 137f * SCALE;
    private static final float LEFT_BOTTOM_H = 58f * SCALE;
    private static final float RIGHT_W = 300f * SCALE;
    private static final float RIGHT_H = 165f * SCALE;
    private static final float GAP = 5f * SCALE;

    private static final float PANEL_R = 6f * SCALE;
    private static final float PANEL_SHADOW = 12f * SCALE;
    private static final float LEFT_CARD_R = 4f * SCALE;
    private static final float CARD_MARKER_W = 2.2f * SCALE;

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
    private static final float PIN_ICON_FONT = 0.93f * TEXT_SCALE;
    private static final float DELETE_ICON_FONT = 1.05f * TEXT_SCALE;

    private static final float FIELD_H = 14f * SCALE;
    private static final float ADD_SIZE = 14f * SCALE;
    private static final float TOP_BUTTON_H = 16f * SCALE;
    private static final float CARD_H = 40f * SCALE;
    private static final float CARD_GAP = 5f * SCALE;
    private static final float CARD_BTN = 12f * SCALE;
    private static final float ACTIVE_HEAD = 24f * SCALE;
    private static final float CARD_HEAD = 25f * SCALE;

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
        titleRenderer = Fonts.renderer("Inter", FontInfo.Type.Bold, TextRenderer.get());
        bodyRenderer = Fonts.renderer("InterMedium", FontInfo.Type.Regular, titleRenderer);
        menuIconRenderer = Fonts.renderer("MainMenuIcons", FontInfo.Type.Regular, TextRenderer.get());
        guiIconRenderer = Fonts.renderer("GuiIcons", FontInfo.Type.Regular, menuIconRenderer);
        iconRenderer = Fonts.renderer("RichIcons", FontInfo.Type.Regular, menuIconRenderer);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        updateUiMetrics();
        clampScroll();
        renderShaderBackground(context);

        float fx = toFixedX(mouseX);
        float fy = toFixedY(mouseY);
        updateAnimations(fx, fy);

        ViewportContext.beginUnscaledLogical(context);
        Renderer2D.COLOR.begin();
        try {
            drawDimmer();
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
        float open = AnimationUtility.easeOutCubic(openAnim);
        PanelColors c = colors(open);

        renderPanel(l.leftX, l.topY, LEFT_W, LEFT_TOP_H, c, open);
        renderPanel(l.leftX, l.bottomY, LEFT_W, LEFT_BOTTOM_H, c, open);
        renderPanel(l.rightX, l.topY, RIGHT_W, RIGHT_H, c, open);

        renderTopPanel(mouseX, mouseY, l.leftX, l.topY, c);
        renderActivePanel(context, l.leftX, l.bottomY, c);
        renderAccountsPanel(context, mouseX, mouseY, l.rightX, l.topY, c);
    }

    private void renderTopPanel(float mouseX, float mouseY, float x, float y, PanelColors c) {
        drawCentered(titleRenderer, tr("screen.combatant.alt_manager.account_panel"), x + LEFT_W * 0.5f - 8f * SCALE, y + 7f * SCALE, TITLE_FONT, c.title);
        renderModeToggle(mouseX, mouseY, x + 5f * SCALE, y + 27f * SCALE, LEFT_W - 10f * SCALE, 13f * SCALE, c);

        draw(bodyRenderer, microsoftMode ? tr("screen.combatant.alt_manager.microsoft_login") : tr("screen.combatant.alt_manager.nickname"), x + 5f * SCALE, y + 47f * SCALE, LABEL_FONT, c.label);

        float fieldX = x + 5f * SCALE;
        float fieldY = y + 57f * SCALE;
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
        float randomY = fieldY + FIELD_H + 5f * SCALE;
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
        drawCentered(titleRenderer, tr("screen.combatant.alt_manager.active_session"), x + LEFT_W * 0.5f - 15f * SCALE, y + 6f * SCALE, TITLE_FONT, c.title);

        float cardX = x + 5f * SCALE;
        float cardY = y + 24f * SCALE;
        float cardW = LEFT_W - 10f * SCALE;
        float cardH = 29f * SCALE;
        Renderer2D.COLOR.roundedRectCornersQuad(cardX, cardY, cardW, cardH, LEFT_CARD_R, LEFT_CARD_R, LEFT_CARD_R, LEFT_CARD_R, 1f,
                withAlpha(c.surface, 154), withAlpha(c.surfaceHover, 166), withAlpha(0xFF0D1118, 176), withAlpha(0xFF0D1118, 176));
        Renderer2D.COLOR.roundedRectStrokeCorners(cardX, cardY, cardW, cardH, LEFT_CARD_R, LEFT_CARD_R, LEFT_CARD_R, LEFT_CARD_R, 0.55f, HudRenderUtil.mixColor(c.stroke, c.strokeSoft, 0.34f));

        String activeName = accountConfig.getActiveAccountName();
        if (activeName == null || activeName.isBlank()) {
            drawCentered(bodyRenderer, tr("screen.combatant.alt_manager.no_account_selected"), cardX + cardW * 0.5f, cardY + 10.2f * SCALE, EMPTY_FONT, c.mutedLabel);
            return;
        }
        float faceX = cardX + 5f * SCALE;
        float faceY = cardY + 2.5f * SCALE;
        Identifier skin = accountConfig.getActiveAccountSkin();
        if (skin == null) skin = SkinManager.getSkin(activeName);

        PlayerHeadRenderer.drawRounded(context, faceX, faceY, ACTIVE_HEAD, 3f * SCALE, skin,
                new RenderColor(c.title), true, new RenderColor(c.stroke), 0.7f, false);

        float textX = faceX + ACTIVE_HEAD + 6f * SCALE;
        float textW = Math.max(1f, cardX + cardW - textX - 6f * SCALE);
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
        Themes.Theme theme = Theme.theme();
        int activeAccent = withAlpha(theme.accent(), Math.round(165f * activeAnim));

        int fillBase = HudRenderUtil.mixColor(c.surface, c.surfaceHover, 0.38f * hoverAnim + 0.10f * pressAnim);
        int fillTop = HudRenderUtil.mixColor(fillBase, c.surfaceHover, 0.03f * pressAnim);
        int fillBottom = HudRenderUtil.mixColor(fillBase, 0xFF05070A, 0.18f);
        int stroke = HudRenderUtil.mixColor(c.stroke, c.strokeSoft, 0.45f * hoverAnim);
        stroke = HudRenderUtil.mixColor(stroke, activeAccent, 0.38f * activeAnim + 0.10f * pressAnim);
        Renderer2D.COLOR.roundedRectSoftShadow(x, y, w, CARD_H, 4f * SCALE, 8f * SCALE, 0.018f + hoverAnim * 0.018f + activeAnim * 0.010f, c.shadow);
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, w, CARD_H, 4f * SCALE, 1f, fillTop, fillTop, fillBottom, fillBottom);
        renderActiveCardMarker(x, y, w, CARD_H, activeAnim, theme);
        Renderer2D.COLOR.roundedRectStrokeGradient(x, y, w, CARD_H, 4f * SCALE, 1f, 0.5f, HudRenderUtil.mixColor(stroke, 0xFFFFFFFF, 0.04f + hoverAnim * 0.03f), stroke, 90f);

        PlayerHeadRenderer.drawRounded(context, x + 7f * SCALE, y + 7f * SCALE, CARD_HEAD, 3f * SCALE, SkinManager.getSkin(entry.getName()),
                new RenderColor(c.title), true, null, 0f, false);

        draw(bodyRenderer, ellipsize(entry.getName(), CARD_NAME_FONT, w - CARD_HEAD - 45f * SCALE), x + 37f * SCALE, y + 9f * SCALE, CARD_NAME_FONT, c.title);
        draw(bodyRenderer, entry.getDate(), x + 37f * SCALE, y + 20f * SCALE, CARD_DATE_FONT, c.muted);
        renderAuthBadge(entry, x + 37f * SCALE, y + 30f * SCALE, c);

        float btnY = y + CARD_H - CARD_BTN - 5f * SCALE;
        float pinX = x + w - CARD_BTN * 2f - 8f * SCALE;
        float delX = x + w - CARD_BTN - 5f * SCALE;
        boolean pinHover = inList && inside(mouseX, mouseY, pinX, btnY, CARD_BTN, CARD_BTN);
        boolean delHover = inList && inside(mouseX, mouseY, delX, btnY, CARD_BTN, CARD_BTN);

        float pinHoverAnim = AnimationUtility.easeOutCubic(pinHoverAnims.getOrDefault(key, pinHover ? 1f : 0f));
        float pinPressAnim = AnimationUtility.easeOutBack(pinPressAnims.getOrDefault(key, 0f), 0.95f);
        int pinFill = entry.isPinned()
                ? HudRenderUtil.mixColor(withAlpha(c.accentSoft, 190), withAlpha(c.accent, 72), 0.18f + pinHoverAnim * 0.12f)
                : HudRenderUtil.mixColor(withAlpha(c.surface, 170), withAlpha(c.surfaceHover, 196), pinHoverAnim * 0.65f);
        int pinStroke = entry.isPinned()
                ? HudRenderUtil.mixColor(c.accent, 0xFFFFFFFF, pinHoverAnim * 0.12f)
                : HudRenderUtil.mixColor(c.stroke, c.strokeSoft, pinHoverAnim * 0.70f);
        Renderer2D.COLOR.roundedRectGradientQuad(pinX, btnY, CARD_BTN, CARD_BTN, 3f * SCALE, 1f, pinFill, pinFill, pinFill, pinFill);
        Renderer2D.COLOR.roundedRectStrokeGradient(pinX, btnY, CARD_BTN, CARD_BTN, 3f * SCALE, 1f, 0.5f, HudRenderUtil.mixColor(pinStroke, 0xFFFFFFFF, 0.04f), pinStroke, 90f);
        float pinContentLift = (pinHoverAnim * 0.35f + pinPressAnim * 0.6f) * SCALE;
        drawCentered(menuIconRenderer, "c", pinX + CARD_BTN * 0.5f, btnY + 1.5f * SCALE - pinContentLift, PIN_ICON_FONT, entry.isPinned() ? c.accent : withAlpha(0xC0C8D4, 255));

        float delHoverAnim = AnimationUtility.easeOutCubic(deleteHoverAnims.getOrDefault(key, delHover ? 1f : 0f));
        float delPressAnim = AnimationUtility.easeOutBack(deletePressAnims.getOrDefault(key, 0f), 0.95f);
        int delFillTop = HudRenderUtil.mixColor(withAlpha(c.surface, 150), withAlpha(0x5A2424, 210), 0.30f + delHoverAnim * 0.45f);
        int delFillBottom = HudRenderUtil.mixColor(withAlpha(c.surface, 140), withAlpha(0x2E1212, 220), 0.20f + delHoverAnim * 0.55f);
        int delStroke = HudRenderUtil.mixColor(c.stroke, withAlpha(0xA24E4E, 230), 0.25f + delHoverAnim * 0.65f);
        Renderer2D.COLOR.roundedRectGradientQuad(delX, btnY, CARD_BTN, CARD_BTN, 3f * SCALE, 1f, delFillTop, delFillTop, delFillBottom, delFillBottom);
        Renderer2D.COLOR.roundedRectStrokeGradient(delX, btnY, CARD_BTN, CARD_BTN, 3f * SCALE, 1f, 0.5f, HudRenderUtil.mixColor(delStroke, 0xFFFFFFFF, 0.04f), delStroke, 90f);
        float delContentLift = (delHoverAnim * 0.35f + delPressAnim * 0.6f) * SCALE;
        drawCentered(guiIconRenderer, "O", delX + CARD_BTN * 0.5f, btnY + 0.5f * SCALE - delContentLift, DELETE_ICON_FONT,
                HudRenderUtil.mixColor(withAlpha(0xC0C8D4, 255), withAlpha(0xFFB0B0, 255), 0.45f + delHoverAnim * 0.45f));
    }

    private void renderActiveCardMarker(float x, float y, float w, float h, float active, Themes.Theme theme) {
        float reveal = AnimationUtility.clamp01(active);
        if (reveal <= 0.001f) return;

        float markerW = CARD_MARKER_W * reveal;
        if (markerW <= 0.01f) return;

        int accent = withAlpha(theme.accent(), Math.round(255f * reveal));
        boolean clipped = ScissorFunction.pushRaw(x, y, markerW, h);
        if (!clipped) return;
        try {
            Renderer2D.COLOR.roundedRectCornersQuad(x, y, w, h, LEFT_CARD_R, LEFT_CARD_R, LEFT_CARD_R, LEFT_CARD_R, 1f, accent, accent, accent, accent);
        } finally {
            ScissorFunction.pop();
        }
    }

    private void renderAuthBadge(AccountEntry entry, float x, float y, PanelColors c) {
        boolean microsoft = entry != null && entry.isMicrosoft();
        String label = microsoft ? tr("screen.combatant.alt_manager.badge.microsoft") : tr("screen.combatant.alt_manager.badge.offline");
        float padX = 3.2f * SCALE;
        float badgeW = width(bodyRenderer, label, BADGE_FONT) + padX * 2f;
        float badgeH = 7f * SCALE;
        int fillTop = microsoft ? withAlpha(0x20394A, 172) : withAlpha(c.surface, 150);
        int fillBottom = microsoft ? withAlpha(0x132732, 184) : withAlpha(0x11151B, 160);
        int stroke = microsoft ? withAlpha(0x527A8A, 190) : c.stroke;
        int text = microsoft ? withAlpha(0xDDEEFF, 235) : c.muted;
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, badgeW, badgeH, 2.2f * SCALE, 1f, fillTop, fillTop, fillBottom, fillBottom);
        Renderer2D.COLOR.roundedRectStrokeGradient(x, y, badgeW, badgeH, 2.2f * SCALE, 1f, 0.45f, HudRenderUtil.mixColor(stroke, 0xFFFFFFFF, 0.05f), stroke, 90f);
        draw(bodyRenderer, label, x + padX, y + 1.9f * SCALE, BADGE_FONT, text);
    }

    private void renderModeToggle(float mouseX, float mouseY, float x, float y, float w, float h, PanelColors c) {
        float gap = 2f * SCALE;
        float itemW = (w - gap) * 0.5f;
        renderModeButton(x, y, itemW, h, c, tr("screen.combatant.alt_manager.mode.offline"), !microsoftMode,
                inside(mouseX, mouseY, x, y, itemW, h), offlineModeHoverAnim, offlineModePressAnim);
        renderModeButton(x + itemW + gap, y, itemW, h, c, tr("screen.combatant.alt_manager.mode.microsoft"), microsoftMode,
                inside(mouseX, mouseY, x + itemW + gap, y, itemW, h), microsoftModeHoverAnim, microsoftModePressAnim);
    }

    private void renderModeButton(float x, float y, float w, float h, PanelColors c, String label, boolean selected,
                                  boolean hovered, float hoverValue, float pressValue) {
        float hoverAnim = AnimationUtility.easeOutCubic(hoverValue);
        float pressAnim = AnimationUtility.easeOutCubic(pressValue);
        float activeAnim = selected ? 1f : 0f;
        int fillTop = HudRenderUtil.mixColor(withAlpha(c.surface, 170), withAlpha(c.surfaceHover, 210), hoverAnim * 0.42f + activeAnim * 0.38f + pressAnim * 0.10f);
        int fillBottom = HudRenderUtil.mixColor(withAlpha(c.surface, 158), withAlpha(0x1A2418, 210), activeAnim * 0.24f + hoverAnim * 0.16f);
        int stroke = HudRenderUtil.mixColor(c.stroke, selected ? c.accentSoft : c.strokeSoft, activeAnim * 0.70f + hoverAnim * 0.30f + pressAnim * 0.10f);
        int text = selected
                ? HudRenderUtil.mixColor(c.title, c.accent, 0.28f)
                : HudRenderUtil.mixColor(c.muted, c.title, hoverAnim * 0.35f);
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, w, h, 3f * SCALE, 1f, fillTop, fillTop, fillBottom, fillBottom);
        Renderer2D.COLOR.roundedRectStrokeGradient(x, y, w, h, 3f * SCALE, 1f, 0.5f, HudRenderUtil.mixColor(stroke, 0xFFFFFFFF, 0.04f), stroke, 90f);
        float lift = (hovered ? hoverAnim : 0f) * 0.35f * SCALE + pressAnim * 0.35f * SCALE;
        drawCentered(bodyRenderer, label, x + w * 0.5f, y + 4.1f * SCALE - lift, BUTTON_FONT, text);
    }

    private void renderMicrosoftField(float x, float y, float w, float h, PanelColors c) {
        float hoverAnim = AnimationUtility.easeOutCubic(fieldHoverAnim);
        int fillTop = HudRenderUtil.mixColor(withAlpha(c.surface, 188), withAlpha(c.surfaceHover, 208), hoverAnim * 0.45f);
        int fillBottom = HudRenderUtil.mixColor(withAlpha(c.surface, 176), withAlpha(0xFF0D1118, 196), 0.18f);
        int stroke = HudRenderUtil.mixColor(c.stroke, microsoftAuthPending ? c.accentSoft : c.strokeSoft, microsoftAuthPending ? 0.45f : hoverAnim * 0.60f);
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, w, h, 3f * SCALE, 1f, fillTop, fillTop, fillBottom, fillBottom);
        Renderer2D.COLOR.roundedRectStrokeGradient(x, y, w, h, 3f * SCALE, 1f, 0.5f, HudRenderUtil.mixColor(stroke, 0xFFFFFFFF, 0.04f), stroke, 90f);
        if (microsoftAuthPending) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 180.0);
            int glow = withAlpha(c.accentSoft, Math.round(22f + 32f * pulse));
            Renderer2D.COLOR.roundedRectGradientQuad(x + 1.2f * SCALE, y + h - 1.6f * SCALE, w - 2.4f * SCALE, 1.1f * SCALE, 0.6f * SCALE, 1f,
                    glow, withAlpha(c.accent, Math.round(20f + 24f * pulse)), withAlpha(c.accent, Math.round(20f + 24f * pulse)), glow);
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
        draw(bodyRenderer, ellipsize(display, FIELD_FONT, w - 8f * SCALE), x + 4f * SCALE, y + 4.3f * SCALE, FIELD_FONT, color);
    }

    private void renderField(float x, float y, float w, float h, PanelColors c, boolean hovered) {
        float hoverAnim = AnimationUtility.easeOutCubic(fieldHoverAnim);
        float focusAnim = AnimationUtility.easeOutCubic(fieldFocusAnim);
        int fillTop = HudRenderUtil.mixColor(withAlpha(c.surface, 188), withAlpha(c.surfaceHover, 208), hoverAnim * 0.45f + focusAnim * 0.16f);
        int fillBottom = HudRenderUtil.mixColor(withAlpha(c.surface, 176), withAlpha(0xFF0D1118, 196), 0.18f);
        int stroke = nicknameFieldFocused
                ? HudRenderUtil.mixColor(c.strokeSoft, c.accentSoft, 0.55f)
                : HudRenderUtil.mixColor(c.stroke, c.strokeSoft, hoverAnim * 0.72f);
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, w, h, 3f * SCALE, 1f, fillTop, fillTop, fillBottom, fillBottom);
        Renderer2D.COLOR.roundedRectStrokeGradient(x, y, w, h, 3f * SCALE, 1f, 0.5f, HudRenderUtil.mixColor(stroke, 0xFFFFFFFF, 0.04f), stroke, 90f);
        if (focusAnim > 0.01f) {
            int glowLeft = withAlpha(c.accentSoft, Math.round(48f * focusAnim));
            int glowRight = withAlpha(c.accent, Math.round(28f * focusAnim));
            Renderer2D.COLOR.roundedRectGradientQuad(x + 1.2f * SCALE, y + h - 1.6f * SCALE, w - 2.4f * SCALE, 1.1f * SCALE, 0.6f * SCALE, 1f,
                    glowLeft, glowRight, glowRight, glowLeft);
        }

        boolean caretOn = nicknameFieldFocused && AnimationUtility.blink(500L);
        String display = nicknameText.isEmpty() && !nicknameFieldFocused ? tr("screen.combatant.alt_manager.enter_nick") : nicknameText + (caretOn ? "|" : "");
        draw(bodyRenderer, display, x + 4f * SCALE, y + 4.3f * SCALE, FIELD_FONT, nicknameText.isEmpty() && !nicknameFieldFocused ? c.mutedLabel : c.title);
    }

    private void renderAddButton(float x, float y, PanelColors c) {
        float hoverAnim = AnimationUtility.easeOutCubic(addHoverAnim);
        float pressAnim = AnimationUtility.easeOutCubic(addPressAnim);
        int fillTop = HudRenderUtil.mixColor(withAlpha(c.surface, 172), withAlpha(c.surfaceHover, 202), hoverAnim * 0.72f);
        int fillBottom = HudRenderUtil.mixColor(withAlpha(c.surface, 162), withAlpha(0xFF0D1118, 186), 0.20f);
        int stroke = HudRenderUtil.mixColor(c.stroke, c.strokeSoft, hoverAnim * 0.65f + pressAnim * 0.20f);
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, ADD_SIZE, ADD_SIZE, 3f * SCALE, 1f, fillTop, fillTop, fillBottom, fillBottom);
        Renderer2D.COLOR.roundedRectStrokeGradient(x, y, ADD_SIZE, ADD_SIZE, 3f * SCALE, 1f, 0.5f, HudRenderUtil.mixColor(stroke, 0xFFFFFFFF, 0.04f), stroke, 90f);

        float cx = x + ADD_SIZE * 0.5f;
        float cy = y + ADD_SIZE * 0.5f;
        float plusLift = (hoverAnim * 0.18f + pressAnim * 0.32f) * SCALE;
        float plusStretch = 1f + hoverAnim * 0.04f + pressAnim * 0.08f;
        Renderer2D.COLOR.roundedRect(cx - 2.5f * SCALE * plusStretch, cy - 0.6f * SCALE - plusLift, 5f * SCALE * plusStretch, 1.2f * SCALE, 0.5f * SCALE, 1f, c.title);
        Renderer2D.COLOR.roundedRect(cx - 0.6f * SCALE, cy - 2.5f * SCALE * plusStretch - plusLift, 1.2f * SCALE, 5f * SCALE * plusStretch, 0.5f * SCALE, 1f, c.title);
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
        int fillTop;
        int fillBottom;
        int stroke;
        int text;
        if (destructive) {
            fillTop = HudRenderUtil.mixColor(withAlpha(0x1A1416, 170), withAlpha(0x4B2224, 212), 0.25f + hoverAnim * 0.45f + pressAnim * 0.12f);
            fillBottom = HudRenderUtil.mixColor(withAlpha(0x120E10, 165), withAlpha(0x261012, 220), 0.20f + hoverAnim * 0.40f + pressAnim * 0.10f);
            stroke = HudRenderUtil.mixColor(withAlpha(0x352A2A, 215), withAlpha(0x8C5054, 235), 0.22f + hoverAnim * 0.58f + pressAnim * 0.12f);
            text = HudRenderUtil.mixColor(withAlpha(0xD0A0A0, 255), withAlpha(0xFFB2B2, 255), 0.24f + hoverAnim * 0.56f + pressAnim * 0.08f);
        } else {
            fillTop = HudRenderUtil.mixColor(withAlpha(c.surface, 176), withAlpha(0x223746, 212), 0.20f + hoverAnim * 0.44f + pressAnim * 0.10f);
            fillBottom = HudRenderUtil.mixColor(withAlpha(c.surface, 166), withAlpha(0x13292F, 220), 0.16f + hoverAnim * 0.40f + pressAnim * 0.08f);
            stroke = HudRenderUtil.mixColor(c.stroke, withAlpha(0x466A76, 228), 0.18f + hoverAnim * 0.50f + pressAnim * 0.10f);
            text = HudRenderUtil.mixColor(withAlpha(0xD0D8E4, 255), withAlpha(0xEEF8FF, 255), 0.18f + hoverAnim * 0.38f + pressAnim * 0.08f);
        }

        Renderer2D.COLOR.roundedRectSoftShadow(x, y, w, h, 3f * SCALE, 7f * SCALE, 0.018f + hoverAnim * 0.015f, c.shadow);
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, w, h, 3f * SCALE, 1f, fillTop, fillTop, fillBottom, fillBottom);
        Renderer2D.COLOR.roundedRectStrokeGradient(x, y, w, h, 3f * SCALE, 1f, 0.5f, HudRenderUtil.mixColor(stroke, 0xFFFFFFFF, 0.04f), stroke, 90f);
        float contentLift = hoverAnim * 0.65f * SCALE + pressAnim * 0.55f * SCALE;
        draw(bodyRenderer, label, x + 6f * SCALE, y + 5f * SCALE - contentLift, BUTTON_FONT, text);
        if (svgName != null && !svgName.isBlank()) {
            float iconSize = 7.2f * SCALE;
            Renderer2D.COLOR.svg(svgName, x + w - 11.3f * SCALE, y + (h - iconSize) * 0.5f - contentLift, iconSize, iconSize, SvgRenderOptions.overrideColor(text));
        } else {
            draw(iconRenderer, glyph, x + (destructive ? 77f : 75f) * SCALE, y + (destructive ? 2.2f : 3.1f) * SCALE - contentLift, ACTION_ICON_FONT, text);
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
        float fieldY = l.topY + 57f * SCALE;
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
        float randomY = fieldY + FIELD_H + 5f * SCALE;
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

    private void renderPanel(float x, float y, float w, float h, PanelColors c, float open) {
        Renderer2D.COLOR.blurRect(x, y, w, h, PANEL_R, 15f, 1.0f, 0.30f, 0xFFFFFF);
        Renderer2D.COLOR.roundedRectSoftShadow(x, y, w, h, PANEL_R, PANEL_SHADOW, 0.022f + open * 0.012f, c.shadow);
        Renderer2D.COLOR.roundedRectGradientQuad(x, y, w, h, PANEL_R, 1f, c.bgTopLeft, c.bgTopRight, c.bgBottomRight, c.bgBottomLeft);
        Renderer2D.COLOR.roundedRectMaskedQuad(x, y, w, 22f * SCALE, x, y, w, h, PANEL_R, 1f,
                c.headerTopLeft, c.headerTopRight, c.headerBottomRight, c.headerBottomLeft);
        Renderer2D.COLOR.roundedRectStrokeGradient(x, y, w, h, PANEL_R, 1f, 1f, HudRenderUtil.mixColor(c.stroke, 0xFFFFFFFF, 0.04f), c.stroke, 90f);
    }

    private PanelColors colors(float alpha) {
        int bgAlpha = Math.round(alpha * 120f);
        int headerAlpha = Math.round(alpha * 150f);
        int strokeAlpha = Math.round(alpha * 100f);
        int shadowAlpha = Math.round(alpha * 80f);
        int titleAlpha = Math.round(alpha * 255f);
        int labelAlpha = Math.round(alpha * 155f);

        Themes.Theme theme = Theme.theme();
        int themeAccent = theme != null ? theme.accent() : 0xFFFFFFFF;
        int themeAccentSoft = theme != null ? theme.accentSoft() : themeAccent;

        int bgTopLeft = withAlpha(0x0D0F14, bgAlpha);
        int bgTopRight = withAlpha(0x101218, bgAlpha);
        int bgBottomLeft = withAlpha(0x08090C, bgAlpha);
        int bgBottomRight = withAlpha(0x0D0F14, bgAlpha);
        int headerTopLeft = withAlpha(0x14171F, headerAlpha);
        int headerTopRight = withAlpha(0x181B24, headerAlpha);
        int headerBottomLeft = withAlpha(0x10131A, headerAlpha);
        int headerBottomRight = withAlpha(0x14171F, headerAlpha);
        int stroke = withAlpha(0x252A36, strokeAlpha);
        int strokeSoft = withAlpha(0x3A4A5A, Math.round(alpha * 150f));
        int surface = withAlpha(0x1A1D24, Math.round(alpha * 160f));
        int surfaceHover = withAlpha(0x1A1F28, Math.round(alpha * 200f));
        int accent = withAlpha(themeAccent, titleAlpha);
        int accentSoft = withAlpha(themeAccentSoft, Math.round(alpha * 180f));
        return new PanelColors(
                bgTopLeft, bgTopRight, bgBottomRight, bgBottomLeft,
                headerTopLeft, headerTopRight, headerBottomRight, headerBottomLeft,
                stroke, strokeSoft,
                withAlpha(0x060810, shadowAlpha),
                withAlpha(0xFFFFFF, titleAlpha),
                withAlpha(0xFFFFFF, labelAlpha),
                withAlpha(0x808890, titleAlpha),
                withAlpha(0x606878, labelAlpha),
                surface, surfaceHover, accent, accentSoft
        );
    }

    private void renderShaderBackground(GuiGraphicsExtractor context) {
        Minecraft mc = this.minecraft;
        if (mc == null) return;
        MainConfig cfg = MainConfig.get();
        String mode = cfg != null ? cfg.getMenuBackgroundMode() : "off";
        if (mode == null || mode.equalsIgnoreCase("off")) {
            context.fill(0, 0, width, height, 0xFF000000);
            return;
        }
        MenuBackgroundRenderer.render(mc, mode.equalsIgnoreCase("aurora"));
    }

    private void drawDimmer() {
        Themes.Theme t = Theme.theme();
        int soft = withAlpha(HudRenderUtil.mixColor(t.windowBg(), 0xFF000000, 0.46f), 140);
        int deep = withAlpha(HudRenderUtil.mixColor(t.windowBg(), 0xFF000000, 0.68f), 180);
        Renderer2D.COLOR.quad(0, 0, fixedWidth, fixedHeight, soft, soft, deep, deep);
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
        float totalW = LEFT_W + GAP + RIGHT_W;
        float totalH = LEFT_TOP_H + GAP + LEFT_BOTTOM_H;
        return new Layout(
                fixedWidth * 0.5f - totalW * 0.5f,
                fixedHeight * 0.5f - totalH * 0.5f,
                fixedWidth * 0.5f - totalW * 0.5f + LEFT_W + GAP,
                fixedHeight * 0.5f - totalH * 0.5f + LEFT_TOP_H + GAP
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
        float fieldY = l.topY + 57f * SCALE;
        float fieldW = LEFT_W - 10f * SCALE - ADD_SIZE - 3f * SCALE;
        float addX = fieldX + fieldW + 3f * SCALE;
        float buttonX = l.leftX + 5f * SCALE;
        float buttonW = LEFT_W - 10f * SCALE;
        float randomY = fieldY + FIELD_H + 5f * SCALE;
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
