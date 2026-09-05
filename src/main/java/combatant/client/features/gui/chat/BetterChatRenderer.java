/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.gui.chat;

import com.mojang.blaze3d.platform.InputConstants;
import combatant.client.mixins.accessors.SuggestionWindowAccessor;
import combatant.client.render.engine.text.VanillaTextRenderer;
import combatant.client.util.item.IllegalItemUtil;
import combatant.client.util.item.TopEnchantUtil;
import combatant.client.util.screen.ClientScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import combatant.client.features.command.CommandManager;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.gui.chat.ChatHoverUtil.HoverTip;
import combatant.client.features.gui.chat.rich.BetterChatMessage;
import combatant.client.features.gui.chat.rich.ItemNode;
import combatant.client.features.gui.chat.rich.TextNode;
import combatant.client.features.gui.hud.HudRenderUtil;
import combatant.client.features.gui.clickgui.layout.screen.settings.SettingsGuiPalette;
import combatant.client.features.gui.hud.draggable.DraggableHudElementRegistry;
import combatant.client.features.gui.hud.draggable.impl.BetterChat;
import combatant.client.features.gui.hud.nondraggable.impl.BetterTooltips;
import combatant.client.mixins.accessors.ChatScreenAccessor;
import combatant.client.mixins.accessors.TextFieldWidgetAccessor;
import combatant.client.render.engine.animation.AnimationUtility;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.renderer.Renderer2D;
import combatant.client.render.engine.svg.SvgRenderOptions;
import combatant.client.render.engine.text.FontInfo;
import combatant.client.render.engine.text.Fonts;
import combatant.client.render.engine.text.TextGlyphFallback;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.helpers.ClipFunction;
import combatant.client.render.helpers.ScissorFunction;
import combatant.client.render.helpers.SystemCursor;
import combatant.client.util.text.ChatNameUtil;
import combatant.client.util.text.ClipboardUtil;
import combatant.client.util.text.TextSelection;
import combatant.client.util.chat.ChatPasswordHeuristics;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.glfwGetKey;
import static combatant.client.features.theme.Theme.theme;

public enum BetterChatRenderer {
    ;
    private static final int MAX_MESSAGE_CHARS = 256; // vanilla chat input limit
    private static final float PADDING = 8f;
    private static final float LINE_SPACING = 3f;
    private static final float RADIUS = 24f;
    private static final float CLIP_AA_GUARD = 1.5f;
    private static final float MESSAGE_PAD_X = 8f;
    private static final float MESSAGE_PAD_Y = 5f;
    private static final float MESSAGE_GAP = 4f;
    private static final float MESSAGE_INPUT_GAP = 8f;
    private static final float MESSAGE_RADIUS = 9f;
    private static final float MESSAGE_TS_GAP = 8f;
    private static final float INPUT_RADIUS = 12f;
    private static final float SEARCH_ICON_WIDTH = 34f;
    private static final float SEARCH_ICON_HEIGHT = 22f;
    private static final float NEW_MESSAGE_REVEAL_SECONDS = 0.34f;
    private static final float NEW_MESSAGE_SLIDE_PX = 18f;
    private static final int MAX_HISTORY = 32000;
    private static final float CHAT_TOOLTIP_OFFSET_X = 6f;
    private static final float CHAT_TOOLTIP_OFFSET_Y = 12f;
    private static final RenderColor TMP_COLOR = new RenderColor(0xFFFFFFFF);
    private static final java.util.IdentityHashMap<ChatLine, CachedMessageLayout> MESSAGE_LAYOUT_CACHE = new java.util.IdentityHashMap<>();
    private static final java.util.IdentityHashMap<ChatLine, List<Segment>> SEGMENT_CACHE = new java.util.IdentityHashMap<>();
    private static final java.util.IdentityHashMap<CommandOutput.MessageLine, ChatLine> COMMAND_LINE_CACHE = new java.util.IdentityHashMap<>();
    private static final TextSelection selection = new TextSelection();
    private static final double DRAG_SELECT_THRESHOLD = 4.0; // px
    private static Renderer2D renderer;
    private static float lastRenderWidth = 0f;
    private static float lastRenderHeight = 0f;
    private static int scrollOffsetLines = 0;
    private static float smoothScrollOffsetLines = 0f;
    private static int maxScrollLines = 0;
    private static float lastFontSizeForUi = 16f;
    private static List<ChatLine> lastMessages = Collections.emptyList();
    private static boolean draggingScrollbar = false;
    private static float sbTrackX, sbTrackY, sbTrackW, sbTrackH;
    private static float sbThumbY, sbThumbH;
    private static float sbDragOffset = 0f;
    private static long lastScrollInteractionMs = 0L;
    private static boolean sbEnabled = false;
    private static int sbTotalLines = 0;
    private static int sbVisibleLines = 0;
    private static float inputBoxX = 0f, inputBoxY = 0f, inputBoxW = 0f, inputBoxH = 0f;
    private static boolean hasInputBox = false;
    // Suggest UI hitbox
    private static boolean suggestActive = false;
    private static float suggestX, suggestY, suggestW, suggestH, suggestItemH;
    private static float suggestRowsX, suggestRowsY, suggestRowsW, suggestRowsH;
    private static int suggestStart, suggestVisible, suggestTotal;
    private static net.minecraft.client.gui.components.CommandSuggestions.SuggestionsList suggestWindow;
    private static List<com.mojang.brigadier.suggestion.Suggestion> suggestEntries = Collections.emptyList();
    private static boolean lastHoverWasOutsideSuggest = true;
    private static boolean suggestHasScrollbar = false;
    private static boolean suggestDraggingScrollbar = false;
    private static float suggestTrackX, suggestTrackY, suggestTrackW, suggestTrackH, suggestThumbH;
    private static int suggestCurrentStart = -1;
    private static int suggestCurrentSelection = -1;
    private static double lastMouseFx = 0.0;
    private static double lastMouseFy = 0.0;
    private static boolean lastMouseValid = false;
    private static boolean tooltipCaptured = false;
    private static String lastSuggestInput = "";
    private static int lastSuggestCursor = -1;
    private static int lastSuggestSelStart = -1;
    private static int lastSuggestSelEnd = -1;
    private static CommandSuggestorBridge.SuggestionSnapshot lastSnapshot = null;
    private static long debugPerfWindowStartNs = 0L;
    private static long debugPerfAccumLayoutNs = 0L;
    private static long debugPerfAccumBuildNs = 0L;
    private static int debugPerfAccumFrames = 0;
    private static FrameLayout frame = FrameLayout.empty();
    private static ContextMenu contextMenu = ContextMenu.closed();
    private static boolean selecting = false;
    private static boolean searchHit = false;
    private static float searchRectX = 0f, searchRectY = 0f, searchRectW = 0f, searchRectH = 0f;
    private static boolean searchHotkeyDown = false;
    private static float chatHitX = 0f, chatHitY = 0f, chatHitW = 0f, chatHitH = 0f;
    private static String hoverEntityUuid = null;
    private static ChatScreen storedScreen;
    private static BetterChatStore previewStore;
    private static int lastSuggestionHash = 0;
    private static boolean leftPending = false;
    private static double leftDownFx = 0.0, leftDownFy = 0.0;
    private static int leftDownMsgIndex = -1;
    private static int leftDownCharIndex = -1;
    private static Style leftDownStyle = null;
    private static final List<PasswordMaskRect> passwordMaskRects = new ArrayList<>();
    private static boolean passwordReveal = false;
    private static boolean passwordMaskClickPending = false;
    private static float passwordRevealProgress = 0f;
    private static String passwordInputSnapshot = "";

    public static void markLayoutDirty() {
    }

    public static void renderEngine(Renderer2D rendererIn,
                                    TextRenderer fallback,
                                    GuiGraphicsExtractor ctx,
                                    int screenW,
                                    float baseX,
                                    float baseY) {
        SystemCursor.beginFrame(ctx);
        try {
            renderEngineInternal(rendererIn, fallback, ctx, screenW, baseX, baseY);
        } finally {
            SystemCursor.endFrame();
        }
    }

    private static void renderEngineInternal(Renderer2D rendererIn,
                                             TextRenderer fallback,
                                             GuiGraphicsExtractor ctx,
                                             int screenW,
                                             float baseX,
                                             float baseY) {
        renderer = rendererIn;
        BetterChat settings = BetterChat.get();
        boolean preview = DraggableHudElementRegistry.isForceVisible();
        if (settings == null || (!settings.isEnabled() && !preview)) {
            lastRenderWidth = 0f;
            lastRenderHeight = 0f;
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || (mc.player == null && !preview)) {
            lastRenderWidth = 0f;
            lastRenderHeight = 0f;
            return;
        }
        if (!preview && mc.isPaused()) {
            selection.clear();
            contextMenu = ContextMenu.closed();
            selecting = false;
            lastRenderWidth = 0f;
            lastRenderHeight = 0f;
            return;
        }

        BetterChatStore store = (preview && mc.player == null)
                ? buildPreviewStore()
                : BetterChatStoreManager.getActiveStore(mc);
        if (store == null) {
            if (preview) {
                store = buildPreviewStore();
            } else {
                lastRenderWidth = 0f;
                lastRenderHeight = 0f;
                return;
            }
        }

        boolean chatOpen = ClientScreen.current() instanceof ChatScreen;
        boolean activeChatSurface = chatOpen || preview;
        if (!chatOpen && store.size() == 0) {
            if (preview) {
                store = buildPreviewStore();
            } else {
                selection.clear();
                contextMenu = ContextMenu.closed();
                lastRenderWidth = 0f;
                lastRenderHeight = 0f;
                return;
            }
        }
        if (!chatOpen) {
            selection.clear();
            contextMenu = ContextMenu.closed();
            selecting = false;
            storedScreen = null;
            draggingScrollbar = false;
            sbEnabled = false;
            scrollOffsetLines = 0;
            BetterChatSearch.deactivate();
            resetSuggestionTracking();
            resetPasswordPrivacy();
        }
        if (chatOpen && ctx != null) {
            BetterTooltips.beginTooltipFrame();
            BetterTooltips.setDrawContext(ctx);
        }
        tooltipCaptured = false;

        int visibleLimit = Math.max(1, chatOpen ? settings.maxLines() : Math.min(settings.maxLines(), 12));
        List<CommandOutput.MessageLine> commandOutputMessages = settings.hideVanilla()
                ? CommandOutput.recentMessages()
                : List.of();
        int totalMessages = store.size() + commandOutputMessages.size();
        boolean autoFollow = scrollOffsetLines == 0;
        boolean tsEnabled = settings.timestampEnabled();
        boolean tsSeconds = settings.timestampSeconds();
        int tsColor = settings.timestampColor();
        boolean tsActiveOnly = settings.timestampToggles().get("active_only");
        boolean tsTipDate = settings.timestampToggles().get("hover_date");
        boolean tsTipUnix = settings.timestampToggles().get("hover_unix");
        boolean showTs = tsEnabled && (!tsActiveOnly || chatOpen);

        List<ChatLine> messages;
        if (BetterChatSearch.hasQuery() && BetterChatSearch.results() != null && chatOpen) {
            List<ChatLine> res = BetterChatSearch.results();
            totalMessages = res.size();
            int maxScrollSearch = Math.max(0, totalMessages - visibleLimit);
            scrollOffsetLines = Mth.clamp(scrollOffsetLines, 0, maxScrollSearch);
            int tailBudget = Math.min(totalMessages, Math.max(visibleLimit + scrollOffsetLines + 20, visibleLimit + 10));
            int endIdx = Math.min(totalMessages, Math.max(0, totalMessages - scrollOffsetLines));
            int startIdx = Math.max(0, endIdx - tailBudget);
            if (startIdx > endIdx) startIdx = endIdx;
            messages = res.subList(startIdx, endIdx);
        } else {
            int tailBudget = chatOpen
                    ? Math.max(visibleLimit + scrollOffsetLines + 80, visibleLimit)
                    : Math.max(visibleLimit + 8, visibleLimit);
            int needed = Math.min(MAX_HISTORY, Math.min(totalMessages, tailBudget));
            messages = mergeCommandOutput(store.tail(Math.min(store.size(), needed)), commandOutputMessages, needed);
        }
        lastMessages = messages;
        int[] messageGroups = BetterChatMessageGrouping.groupIds(messages);

        float fontSize = 16f * settings.fontScale();
        float width = computeWidth(screenW, settings.widthRatio());
        float tsWidth = 0f;
        if (showTs) {
            String sampleTs = tsSeconds ? "00:00:00" : "00:00";
            tsWidth = textWidth(getInterRegular(), sampleTs, Math.max(9f, fontSize * 0.72f)) + MESSAGE_TS_GAP;
        }
        float contentWidth = Math.max(32f, width - PADDING * 2f - MESSAGE_PAD_X * 2f - tsWidth);
        lastFontSizeForUi = fontSize;

        int lineTarget = visibleLimit + scrollOffsetLines + Math.max(10, visibleLimit / 2) + 2;
        long perfT0 = System.nanoTime();
        LayoutResult layoutResult = layoutLinesTailCached(messages, messageGroups, fontSize, contentWidth, lineTarget);
        long perfT1 = System.nanoTime();
        List<VisualLine> allLines = layoutResult.lines();
        float lineHeight = fontSize + LINE_SPACING;
        float boxHeight;
        List<VisualLine> visible;
        int localStart;
        hasInputBox = false; // reset per-frame before renderInput fills it
        float lineYOffset = 0f;
        int targetVisibleForBox = visibleLimit;
        if (allLines.isEmpty()) {
            maxScrollLines = 0;
            sbTotalLines = 0;
            sbVisibleLines = 0;
            boxHeight = 0f;
            visible = Collections.emptyList();
            smoothScrollOffsetLines = 0f;
        } else {
            int lineCount = allLines.size();
            int observedMessages = Math.max(1, layoutResult.messagesSeen());
            float avgLinesPerMsg = Math.max(1f, (float) lineCount / (float) observedMessages);
            int estimatedTotalLines = Math.max(lineCount, (int) Math.ceil(avgLinesPerMsg * totalMessages));
            maxScrollLines = Math.max(0, estimatedTotalLines - visibleLimit);
            scrollOffsetLines = Mth.clamp(scrollOffsetLines, 0, maxScrollLines);
            if (autoFollow) {
                smoothScrollOffsetLines = 0f;
            } else {
                smoothScrollOffsetLines = Mth.clamp(smoothScrollOffsetLines, 0f, maxScrollLines);
                smoothScrollOffsetLines = AnimationUtility.approach(
                        smoothScrollOffsetLines,
                        scrollOffsetLines,
                        draggingScrollbar ? 0.55f : 0.22f
                );
                smoothScrollOffsetLines = AnimationUtility.snap(
                        smoothScrollOffsetLines,
                        scrollOffsetLines,
                        draggingScrollbar ? 0.01f : 0.035f
                );
            }

            // The viewport is line-bounded even when one incoming message wraps heavily.
            // A large server table/message must not grow the whole BetterChat column and evict
            // unrelated history from the visible surface.
            int targetVisible = visibleLimit;
            targetVisibleForBox = Math.max(1, Math.min(targetVisible, lineCount));

            float globalStartFloat = autoFollow
                    ? Math.max(0f, lineCount - targetVisible)
                    : Math.max(0f, lineCount - visibleLimit - smoothScrollOffsetLines);
            int globalStart = Math.max(0, (int) Math.floor(globalStartFloat) - (autoFollow ? 0 : 1));
            int viewportStart = autoFollow
                    ? Math.max(0, lineCount - targetVisibleForBox)
                    : Mth.clamp((int) Math.floor(globalStartFloat), 0, Math.max(0, lineCount - 1));
            int viewportEnd = Math.min(lineCount, viewportStart + targetVisibleForBox);
            int groupsForBox = countMessageGroups(allLines.subList(viewportStart, viewportEnd), targetVisibleForBox);

            localStart = Math.max(0, Math.min(globalStart, Math.max(0, lineCount - 1)));
            int endLine = Math.min(lineCount, localStart + targetVisibleForBox + (autoFollow ? 0 : 3));
            // Do not expand to message-group boundaries here. A wrapped/box-drawing message may
            // be taller than the viewport; clipping it is preferable to creating a giant column.
            visible = allLines.subList(localStart, endLine);
            sbTotalLines = lineCount;
            sbVisibleLines = targetVisibleForBox;

            boxHeight = messageStackHeight(targetVisibleForBox, groupsForBox, lineHeight);
            if (autoFollow) {
                int actualGroups = countMessageGroups(visible, visible.size());
                float actualHeight = messageStackHeight(visible.size(), actualGroups, lineHeight);
                // Keep the newest visible lines pinned to the viewport bottom.
                lineYOffset = boxHeight - actualHeight;
            } else {
                lineYOffset = (localStart - globalStartFloat) * lineHeight;
            }
        }
        float stableBoxHeight = messageStackHeight(visibleLimit, visibleLimit, lineHeight);
        float fixedInputY = baseY + stableBoxHeight + MESSAGE_INPUT_GAP;
        float frameY = baseY + stableBoxHeight - boxHeight;
        float visualTop = Math.min(baseY, frameY);
        float visualBottom = chatOpen ? fixedInputY + Math.max(36f, fontSize + 16f) : frameY + boxHeight;
        float totalHeight = Math.max(0f, visualBottom - visualTop);
        lastRenderWidth = width;
        lastRenderHeight = totalHeight;

        long perfT2Start = System.nanoTime();
        frame = FrameLayout.build(visible, baseX, frameY, width, boxHeight, fontSize, lineHeight, lineYOffset, showTs ? tsWidth : 0f);
        long perfT2 = System.nanoTime();
        debugPerfAccumLayoutNs += (perfT1 - perfT0);
        debugPerfAccumBuildNs += (perfT2 - perfT2Start);
        debugPerfAccumFrames++;
        if (debugPerfWindowStartNs == 0L) debugPerfWindowStartNs = perfT2;
        long windowNs = perfT2 - debugPerfWindowStartNs;
        if (windowNs >= 1_000_000_000L && debugPerfAccumFrames > 0 && chatOpen) {
            double layoutMs = (debugPerfAccumLayoutNs / (double) debugPerfAccumFrames) / 1_000_000.0;
            double buildMs = (debugPerfAccumBuildNs / (double) debugPerfAccumFrames) / 1_000_000.0;
            /*DebugLog.info("[BetterChat][Perf] layout=%.2f ms build=%.2f ms lines=%d msgs=%d scroll=%d/%d vis=%d",
                    layoutMs, buildMs, allLines.size(), messages.size(), scrollOffsetLines, maxScrollLines, visibleLimit);*/
            debugPerfWindowStartNs = perfT2;
            debugPerfAccumLayoutNs = 0L;
            debugPerfAccumBuildNs = 0L;
            debugPerfAccumFrames = 0;
        }

        searchHit = false;
        if (!chatOpen) {
            BetterChatSearch.deactivate();
        }
        updateSearchHotkey(mc, chatOpen);

        MousePos mouse = resolveMouse(mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
        double mouseX = mouse.fx();
        double mouseY = mouse.fy();
        lastMouseFx = mouseX;
        lastMouseFy = mouseY;
        lastMouseValid = true;
        HoverTip hoverTip = null;
        hoverEntityUuid = null;

        TextRenderer tsRenderer = showTs ? getInterRegular() : null;
        float tsFontSize = Math.max(9f, fontSize * 0.72f);
        float tsTextHeight = 0f;
        if (showTs && tsRenderer != null) {
            float tsScale = scaleForSize(tsFontSize);
            tsRenderer.begin(tsScale, false, false);
            tsTextHeight = (float) tsRenderer.getHeight(false);
            tsRenderer.end();
        }

        boolean chatClip = false;
        MessageClipBounds clipBounds = frame.messageClipBounds();
        if (clipBounds != null) {
            if (!frame.bubbles().isEmpty()) {
                Renderer2D.requestLiquidGlassBlurBeforeNextShapeClip();
            }
            float clipRadius = Math.min(RADIUS, clipBounds.h() * 0.5f);
            chatClip = ClipFunction.pushRoundedRectAnalyticRequired(
                    clipBounds.x(), clipBounds.y(), clipBounds.w(), clipBounds.h(),
                    clipRadius, clipRadius, 0.0f, 0.0f
            );
        }
        drawMessageBubbles(frame.bubbles(), activeChatSurface);

        // Keep the cheap rectangular reject for embedded items, then let shape/text/MSDF pipelines
        // consume the same analytic rounded boundary. Clip selection is orthogonal to fast/warped text.
        boolean contentScissor = clipBounds != null && ScissorFunction.pushRaw(
                clipBounds.x() - CLIP_AA_GUARD,
                clipBounds.y() - CLIP_AA_GUARD,
                clipBounds.w() + CLIP_AA_GUARD * 2.0f,
                clipBounds.h() + CLIP_AA_GUARD * 2.0f
        );
        for (FrameLine line : frame.lines()) {
            float alpha = activeChatSurface ? 1f : fade(line.message().ageSeconds());
            if (alpha <= 0.01f) continue;
            drawSelection(line, alpha, lineHeight);
        }

        renderGlyphs(frame.lines(), fontSize, lineHeight, activeChatSurface);
        if (showTs && tsRenderer != null) {
            hoverTip = renderBubbleTimestamps(
                    tsRenderer,
                    frame.bubbles(),
                    tsFontSize,
                    tsTextHeight,
                    tsSeconds,
                    tsColor,
                    tsTipDate,
                    tsTipUnix,
                    activeChatSurface,
                    chatOpen,
                    hoverTip
            );
        }
        if (contentScissor) ScissorFunction.pop();
        if (chatClip) ClipFunction.pop();
        PickResult hover = frame.pick(mouseX, mouseY);
        boolean overSuggestWindow = isInsideSuggestWindow(mouse.fx(), mouse.fy(), mouse.rawX(), mouse.rawY());
        if (overSuggestWindow && lastHoverWasOutsideSuggest) {
            /*DebugLog.info("[BetterChat][Suggest] hover enter mx=%.1f my=%.1f raw=(%.1f,%.1f) box=(%.1f,%.1f,%.1f,%.1f)",
                    mouse.fx(), mouse.fy(), mouse.rawX(), mouse.rawY(), suggestX, suggestY, suggestW, suggestH)*/
            lastHoverWasOutsideSuggest = false;
        } else if (!overSuggestWindow && !lastHoverWasOutsideSuggest) {
            lastHoverWasOutsideSuggest = true;
        }

        if (hover != null && hover.glyph().item() != null && !hover.glyph().item().isEmpty() && !overSuggestWindow) {
            hoverTip = ChatHoverUtil.buildItemTip(hover.glyph().item(), mc, false);
        } else if (hover != null && hover.glyph().hover() != null && !overSuggestWindow) {
            hoverTip = ChatHoverUtil.fromHover(hover.glyph().hover(), mc);
            if (hoverTip != null) hoverEntityUuid = hoverTip.uuid();
        }

        // Fallback heuristic: if no hover info was provided (e.g., from persisted history), try to infer from cache or nickname.
        if (selecting) {
            hoverTip = null;
            hoverEntityUuid = null;
        }

        if (!selecting && !overSuggestWindow && hoverTip == null && hover != null) {
            String raw = hover.line().message().text().getString();
            String word = wordAt(raw, hover.glyph().charIndex());
            if (!word.isEmpty()) {
                boolean looksLikeItem = (word.startsWith("[") && word.contains("]"));
                HoverTip cached = ChatHoverUtil.inferFromDisplay(word, looksLikeItem, mc);
                if (cached != null) {
                    hoverTip = cached;
                    hoverEntityUuid = cached.uuid();
                }
            }
            if (hoverTip == null) {
                List<String> nickCandidates = ChatNameUtil.extractNicks(raw);
                String normalized = ChatNameUtil.normalizeNickCandidate(word);
                String targetNick = null;
                if (!normalized.isEmpty()
                        && ChatNameUtil.isNickLike(normalized)
                        && nickCandidates.stream().anyMatch(n -> n.equalsIgnoreCase(normalized))) {
                    targetNick = normalized;
                } else if (nickCandidates.size() == 1) {
                    targetNick = nickCandidates.getFirst();
                }
                if (targetNick != null) {
                    hoverTip = ChatHoverUtil.inferFromNick(targetNick, mc);
                    if (hoverTip != null) hoverEntityUuid = hoverTip.uuid();
                }
            }
        }

        if (overSuggestWindow || contextMenu.open) {
            hoverTip = null;
            hoverEntityUuid = null;
        }

        if (chatOpen) {
            renderInput(mc, fontSize, width, baseX, fixedInputY);
            float hitBottom = hasInputBox ? inputBoxY + inputBoxH : fixedInputY;
            chatHitX = baseX;
            chatHitY = frame.y();
            chatHitW = width;
            chatHitH = Math.max(boxHeight, hitBottom - chatHitY);
            renderSuggestions();
        } else {
            chatHitX = chatHitY = chatHitW = chatHitH = 0f;
            lastMouseValid = false;
        }

        if (chatOpen && maxScrollLines > 0) {
            int scrollbarStart = autoFollow
                    ? Math.max(0, sbTotalLines - visibleLimit)
                    : Math.max(0, sbTotalLines - visibleLimit - scrollOffsetLines);
            renderScrollbar(frame.x(), frame.y(), frame.w(), frame.h(), scrollbarStart, visibleLimit, sbTotalLines, mouseX, mouseY);
        }

        if (chatOpen && hoverTip != null) {
            captureTooltip(hoverTip, (float) mouseX + CHAT_TOOLTIP_OFFSET_X, (float) mouseY + CHAT_TOOLTIP_OFFSET_Y, ctx);
        }

        if (contextMenu.open) {
            contextMenu.render((float) mouseX, (float) mouseY, fontSize);
        }
    }

    private static int countMessageGroups(List<VisualLine> lines, int maxLines) {
        if (lines == null || lines.isEmpty() || maxLines <= 0) return 0;
        int groups = 0;
        int lastMessage = Integer.MIN_VALUE;
        int limit = Math.min(lines.size(), maxLines);
        for (int i = 0; i < limit; i++) {
            int messageGroup = lines.get(i).messageGroup();
            if (messageGroup != lastMessage) {
                groups++;
                lastMessage = messageGroup;
            }
        }
        return groups;
    }

    private static float messageStackHeight(int lineCount, int groupCount, float lineHeight) {
        if (lineCount <= 0) return 0f;
        return lineCount * lineHeight
                - LINE_SPACING
                + Math.max(0, groupCount - 1) * MESSAGE_GAP
                + MESSAGE_PAD_Y * 2f;
    }

    private static void drawMessageBubbles(List<MessageBubble> bubbles, boolean activeChatSurface) {
        if (bubbles == null || bubbles.isEmpty()) return;
        for (MessageBubble bubble : bubbles) {
            float alpha = activeChatSurface ? 1f : fade(bubble.message().ageSeconds());
            if (alpha <= 0.01f) continue;
            float reveal = newMessageReveal(bubble.message());
            float x = bubble.x() - (1f - reveal) * NEW_MESSAGE_SLIDE_PX;
            float y = bubble.y();
            float w = bubble.w();
            float h = bubble.h();
            drawMessageBubbleGlass(x, y, w, h, Math.min(MESSAGE_RADIUS, h * 0.5f), alpha);
        }
    }

    private static void drawMessageBubbleGlass(float x, float y, float w, float h, float radius, float alpha) {
        if (renderer == null || w <= 0f || h <= 0f) return;
        float drawAlpha = Mth.clamp(alpha, 0.0f, 1.0f);
        if (drawAlpha <= 0.001f) return;
        BetterChat settings = BetterChat.get();
        float configuredAlpha = settings != null ? settings.liquidGlassAlphaFactor() : (230f / 255f);
        float blurStrength = configuredAlpha * drawAlpha;
        float glassScale = MESSAGE_RADIUS <= 0.0f ? 1.0f : radius / MESSAGE_RADIUS;
        // Use the same theme tint family as the input field, but keep message material weaker.
        // The previous extra accent fill + bright stroke stacked on top of the glass shader and
        // made bubbles visibly more saturated than the input.
        HudRenderUtil.drawLiquidGlassCorners(
                x, y, w, h,
                radius, radius, radius, radius,
                glassScale, false,
                blurStrength, drawAlpha * 0.74f,
                theme().accent()
        );
        drawRoundedRect(x, y, w, h, radius,
                withAlpha(theme().windowBg(), Math.round(0x08 * drawAlpha)));
    }

    private static HoverTip renderBubbleTimestamps(TextRenderer tsRenderer,
                                                   List<MessageBubble> bubbles,
                                                   float tsFontSize,
                                                   float tsTextHeight,
                                                   boolean withSeconds,
                                                   int tsColor,
                                                   boolean tsTipDate,
                                                   boolean tsTipUnix,
                                                   boolean activeChatSurface,
                                                   boolean chatOpen,
                                                   HoverTip currentHover) {
        if (bubbles == null || bubbles.isEmpty()) return currentHover;
        HoverTip hover = currentHover;
        float tsScale = scaleForSize(tsFontSize);
        float shadowOffset = Math.max(0.75f, tsFontSize * 0.07f);
        float timestampShadowAlpha = 0.46f;
        tsRenderer.begin(tsScale, false, false);
        try {
            for (MessageBubble bubble : bubbles) {
                float alpha = activeChatSurface ? 1f : fade(bubble.message().ageSeconds());
                if (alpha <= 0.01f) continue;

                String ts = formatTimestamp(bubble.message().timestampMs(), withSeconds);
                float textW = (float) tsRenderer.getWidth(ts, false);
                float reveal = newMessageReveal(bubble.message());
                float tx = bubble.x() + bubble.w() - MESSAGE_PAD_X - textW - (1f - reveal) * NEW_MESSAGE_SLIDE_PX;
                float ty = bubble.lastLine().y0() + (bubble.lastLine().y1() - bubble.lastLine().y0() - tsTextHeight) * 0.5f;
                int shadowColor = mulAlpha(0xFF000000, alpha * timestampShadowAlpha);
                applyColor(shadowColor);
                tsRenderer.render(ts, tx + shadowOffset, ty, TMP_COLOR, false);
                tsRenderer.render(ts, tx, ty + shadowOffset, TMP_COLOR, false);
                applyColor(mulAlpha(tsColor, alpha * 0.82f));
                tsRenderer.render(ts, tx, ty, TMP_COLOR, false);

                if (hover == null && chatOpen && lastMouseValid) {
                    float hitPad = 2f;
                    if (lastMouseFx >= tx - hitPad && lastMouseFx <= tx + textW + hitPad
                            && lastMouseFy >= ty - hitPad && lastMouseFy <= ty + tsTextHeight + hitPad) {
                        hover = buildTimestampTooltip(bubble.message().timestampMs(), tsTipDate, tsTipUnix);
                    }
                }
            }
        } finally {
            tsRenderer.end();
        }
        return hover;
    }

    private static float fade(float ageSeconds) {
        BetterChat cfg = BetterChat.get();
        float life = cfg != null ? cfg.fadeSeconds() : 12f;
        if (life <= 0f) return 0f;

        // Держим сообщение полностью видимым, затем быстро гасим в конце.
        float fadeDur = Math.min(2.0f, Math.max(0.35f, life * 0.18f)); // быстрое затухание (0.35..2.0с)
        float holdDur = Math.max(0f, life - fadeDur);

        if (ageSeconds <= holdDur) return 1f;

        float t = (ageSeconds - holdDur) / fadeDur; // 0..1
        t = Mth.clamp(t, 0f, 1f);

        // Быстрое “падение” в конце (кубическая кривая)
        float inv = 1f - t;
        return inv * inv * inv;
    }

    private static void drawSelection(FrameLine line, float alpha, float lineHeight) {
        if (!selection.appliesToLine(line.messageIndex())) return;

        int selStart = selection.startForLine(line.messageIndex());
        int selEnd = selection.endForLine(line.messageIndex());
        if (selStart < 0 && selEnd < 0) return;

        float y0 = line.y0() - 1.5f;
        float h = lineHeight + 3f;
        float startX = Float.NaN;
        float endX = Float.NaN;
        for (GlyphBox g : line.glyphs()) {
            if (g.charEndExclusive() <= selStart || g.charIndex() > selEnd) continue;
            if (Float.isNaN(startX)) startX = g.x0();
            endX = g.x1();
        }
        if (Float.isNaN(startX) || Float.isNaN(endX)) return;
        float w = Math.max(1f, endX - startX);
        drawRoundedRect(startX, y0, w, h, 1.8f, mulAlpha(theme().accent(), alpha * 0.55f));
    }

    private static void renderGlyphs(List<FrameLine> lines, float fontSize, float lineHeight, boolean activeChatSurface) {
        if (lines == null || lines.isEmpty()) return;
        float scale = scaleForSize(fontSize);
        TextRenderer current = null;
        float currentHeight = 0f;
        float shadowOffset = Math.max(1f, fontSize * 0.08f);
        float shadowAlpha = 0.68f;
        for (FrameLine line : lines) {
            float alpha = activeChatSurface ? 1f : fade(line.message().ageSeconds());
            if (alpha <= 0.01f) continue;
            float baseY = line.y0();
            float reveal = newMessageReveal(line.message());
            float xOffset = -(1f - reveal) * NEW_MESSAGE_SLIDE_PX;
            boolean commandLine = CommandOutput.isCombatantMessage(line.message().text());
            // New-message animation is translation-only. Do not create a nested rectangular
            // clip here: every arrival used to push/pop GPU scissor state for ~340 ms, forcing
            // batch flushes and briefly invalidating lower HUD phases.
            List<GlyphBox> glyphs = line.glyphs();
            for (int gi = 0; gi < glyphs.size(); gi++) {
                GlyphBox g = glyphs.get(gi);
                if (g.item() != null && !g.item().isEmpty()) {
                    if (current != null) {
                        current.end();
                        current = null;
                    }
                    float iconSize = Math.max(1.0f, g.x1() - g.x0());
                    float gx = g.x0() + xOffset;
                    float itemY = baseY + (lineHeight - iconSize) * 0.5f;
                    double previousRendererAlpha = renderer.getAlpha();
                    try {
                        renderer.setAlpha(previousRendererAlpha * alpha);
                        renderer.item(
                                g.item(),
                                gx,
                                itemY,
                                iconSize / 16.0f,
                                31 * line.messageIndex() + gi,
                                Renderer2D.ITEM_OVERLAY_NONE,
                                null
                        );
                    } finally {
                        renderer.setAlpha(previousRendererAlpha);
                    }
                    continue;
                }
                if (commandLine && g.charIndex() < CommandOutput.PREFIX.length() && !TextGlyphFallback.isSvgFontKey(g.font())) {
                    if (current != null) {
                        current.end();
                        current = null;
                    }
                    int runEnd = gi + 1;
                    while (runEnd < glyphs.size()) {
                        GlyphBox next = glyphs.get(runEnd);
                        if (next.charIndex() >= CommandOutput.PREFIX.length()) break;
                        if (TextGlyphFallback.isSvgFontKey(next.font())) break;
                        if (!java.util.Objects.equals(next.font(), g.font())) break;
                        runEnd++;
                    }
                    renderCommandPrefixRun(glyphs, gi, runEnd, baseY, lineHeight, xOffset, scale, alpha, shadowOffset, shadowAlpha, line.message().text());
                    gi = runEnd - 1;
                    continue;
                }
                if (TextGlyphFallback.isSvgFontKey(g.font())) {
                    if (current != null) {
                        current.end();
                        current = null;
                    }
                    String svgName = TextGlyphFallback.svgNameFromFontKey(g.font());
                    float gx = g.x0() + xOffset;
                    float iconSize = Math.max(1.0f, g.x1() - g.x0());
                    float iconY = baseY + (lineHeight - iconSize) * 0.5f;
                    if (svgName != null) {
                        renderer.svg(svgName, gx, iconY, iconSize, iconSize, SvgRenderOptions.fromFile().withAlpha(alpha));
                    }
                    continue;
                }
                TextRenderer tr = fontRenderer(g.font());
                if (tr != current) {
                    if (current != null) {
                        current.end();
                    }
                    current = tr;
                    current.begin(scale, false, false);
                    currentHeight = (float) current.getHeight(false);
                }
                float textY = baseY + (lineHeight - currentHeight) * 0.5f;
                float gx = g.x0() + xOffset;
                int shadowColor = mulAlpha(0xFF000000, alpha * shadowAlpha);
                applyColor(shadowColor);
                current.render(g.text(), gx + shadowOffset, textY, TMP_COLOR, false);
                current.render(g.text(), gx - shadowOffset, textY, TMP_COLOR, false);
                current.render(g.text(), gx, textY + shadowOffset, TMP_COLOR, false);
                current.render(g.text(), gx, textY - shadowOffset, TMP_COLOR, false);
                applyColor(mulAlpha(g.color(), alpha));
                current.render(g.text(), gx, textY, TMP_COLOR, false);
            }
        }
        if (current != null) {
            current.end();
        }
    }

    private static void renderCommandPrefixRun(List<GlyphBox> glyphs,
                                               int from,
                                               int to,
                                               float baseY,
                                               float lineHeight,
                                               float xOffset,
                                               float scale,
                                               float alpha,
                                               float shadowOffset,
                                               float shadowAlpha,
                                               Component source) {
        if (from < 0 || to <= from || to > glyphs.size()) return;
        GlyphBox first = glyphs.get(from);
        GlyphBox last = glyphs.get(to - 1);
        TextRenderer tr = fontRenderer(first.font());
        if (tr == null) return;

        StringBuilder text = new StringBuilder();
        for (int i = from; i < to; i++) {
            text.append(glyphs.get(i).text());
        }
        if (text.isEmpty()) return;

        float runX = first.x0() + xOffset;
        float runW = Math.max(1.0f, last.x1() - first.x0());
        CommandOutput.Tone tone = CommandOutput.toneOf(source);
        int start = CommandOutput.prefixStartColor(tone);
        int end = CommandOutput.prefixEndColor(tone);
        int bottomMix = CommandOutput.mixRgb(end, 0xFFFFFFFF, 0.10f);

        tr.begin(scale, false, false);
        try {
            float textY = baseY + (lineHeight - (float) tr.getHeight(false)) * 0.5f;
            int shadowColor = mulAlpha(0xFF000000, alpha * shadowAlpha);
            applyColor(shadowColor);
            tr.render(text.toString(), runX + shadowOffset, textY, TMP_COLOR, false);
            tr.render(text.toString(), runX - shadowOffset, textY, TMP_COLOR, false);
            tr.render(text.toString(), runX, textY + shadowOffset, TMP_COLOR, false);
            tr.render(text.toString(), runX, textY - shadowOffset, TMP_COLOR, false);

            tr.renderQuadGradient(text.toString(), runX, textY, (idx, cp, x0, y0, x1, y1, out) -> {
                float t0 = (float) ((x0 - runX) / runW);
                float t1 = (float) ((x1 - runX) / runW);
                int leftTop = mulAlpha(CommandOutput.mixRgb(start, end, t0), alpha);
                int rightTop = mulAlpha(CommandOutput.mixRgb(start, end, t1), alpha);
                int leftBottom = mulAlpha(CommandOutput.mixRgb(start, bottomMix, t0), alpha);
                int rightBottom = mulAlpha(CommandOutput.mixRgb(start, bottomMix, t1), alpha);
                out[0] = leftTop;
                out[1] = leftBottom;
                out[2] = rightBottom;
                out[3] = rightTop;
            }, false);
        } finally {
            tr.end();
        }
    }

    private static List<ChatLine> mergeCommandOutput(List<ChatLine> storeLines,
                                                     List<CommandOutput.MessageLine> commandLines,
                                                     int needed) {
        if (commandLines == null || commandLines.isEmpty()) {
            return storeLines;
        }
        List<ChatLine> out = new ArrayList<>(storeLines.size() + commandLines.size());
        out.addAll(storeLines);
        for (CommandOutput.MessageLine line : commandLines) {
            if (line == null || line.text() == null) continue;
            out.add(COMMAND_LINE_CACHE.computeIfAbsent(line, key -> new ChatLine(key.text(), key.timestampMs())));
        }
        out.sort(java.util.Comparator.comparingLong(ChatLine::timestampMs));
        trimCommandLineCache(commandLines);
        if (needed > 0 && out.size() > needed) {
            return new ArrayList<>(out.subList(out.size() - needed, out.size()));
        }
        return out;
    }

    private static void trimCommandLineCache(List<CommandOutput.MessageLine> liveLines) {
        if (COMMAND_LINE_CACHE.size() <= 160) return;
        java.util.IdentityHashMap<CommandOutput.MessageLine, Boolean> live = new java.util.IdentityHashMap<>();
        for (CommandOutput.MessageLine line : liveLines) {
            live.put(line, Boolean.TRUE);
        }
        COMMAND_LINE_CACHE.keySet().removeIf(line -> !live.containsKey(line));
    }

    private static float newMessageReveal(ChatLine message) {
        if (message == null) return 1f;
        float age = message.ageSeconds();
        if (age <= 0f) return 0f;
        if (age >= NEW_MESSAGE_REVEAL_SECONDS) return 1f;
        float t = Mth.clamp(age / NEW_MESSAGE_REVEAL_SECONDS, 0f, 1f);
        float inv = 1f - t;
        return 1f - inv * inv * inv;
    }

    private static void captureTooltip(HoverTip tip, float x, float y, GuiGraphicsExtractor ctx) {
        if (tip == null || ctx == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.getWindow() == null) return;
        float scale = (float) mc.getWindow().getGuiScale();
        int mx = Math.round(x / scale);
        int my = Math.round(y / scale);
        List<FormattedCharSequence> lines = buildOrderedTooltipLines(tip);
        if (lines.isEmpty()) return;

        BetterTooltips.setTooltipScaleOverride(0.8f);
        if (tip.item() != null && !tip.item().isEmpty()) {
            BetterTooltips.captureItemTooltipOrdered(lines, DefaultTooltipPositioner.INSTANCE, mx, my, tip.item(), ctx);
        } else {
            BetterTooltips.captureTooltipOrdered(lines, DefaultTooltipPositioner.INSTANCE, mx, my);
        }
        tooltipCaptured = true;
    }

    public static void renderCapturedTooltip(GuiGraphicsExtractor ctx) {
        if (!tooltipCaptured) return;
        BetterTooltips.renderTooltipWithContext(ctx);
        BetterTooltips.resetTooltipScaleOverride();
        tooltipCaptured = false;
    }

    private static List<FormattedCharSequence> buildOrderedTooltipLines(HoverTip tip) {
        if (tip == null || tip.lines() == null || tip.lines().isEmpty()) return Collections.emptyList();
        List<FormattedCharSequence> out = new ArrayList<>(tip.lines().size());
        for (ChatHoverUtil.ColoredLine line : tip.lines()) {
            int argb = line.color() != 0 ? line.color() : theme().textPrimary();
            int rgb = argb & 0x00FFFFFF;
            Component text = Component.literal(line.text()).setStyle(Style.EMPTY.withColor(net.minecraft.network.chat.TextColor.fromRgb(rgb)));
            out.add(text.getVisualOrderText());
        }
        return out;
    }

    private static LayoutResult layoutLinesTailCached(List<ChatLine> messages, int[] messageGroups, float fontSize, float maxWidth, int maxLines) {
        if (messages.isEmpty()) return new LayoutResult(Collections.emptyList(), 0);
        List<VisualLine> lines = new ArrayList<>();
        int seenMessages = 0;
        for (int mi = messages.size() - 1; mi >= 0; mi--) {
            ChatLine msg = messages.get(mi);
            int messageGroup = messageGroups != null && mi >= 0 && mi < messageGroups.length ? messageGroups[mi] : mi;
            List<CachedLine> cached = cachedMessageLines(msg, fontSize, maxWidth);
            for (int j = cached.size() - 1; j >= 0; j--) {
                CachedLine cl = cached.get(j);
                lines.addFirst(new VisualLine(msg, mi, messageGroup, cl.startChar(), cl.endChar(), cl.glyphs()));
            }
            seenMessages++;
            if (lines.size() >= maxLines) break;
        }
        return new LayoutResult(lines, seenMessages);
    }

    private static List<CachedLine> cachedMessageLines(ChatLine msg, float fontSize, float maxWidth) {
        CachedMessageLayout cached = MESSAGE_LAYOUT_CACHE.get(msg);
        if (cached != null && Math.abs(cached.fontSize() - fontSize) < 0.01f && Math.abs(cached.maxWidth() - maxWidth) < 0.5f) {
            return cached.lines();
        }

        List<Segment> segments = SEGMENT_CACHE.computeIfAbsent(msg, m -> flatten(m.message()));
        List<CachedLine> rebuilt = new ArrayList<>();
        TextRenderer activeRenderer = null;
        String activeFont = null;
        float scale = scaleForSize(fontSize);

        int charIndex = 0;
        float lineX = 0f;
        List<Glyph> glyphs = new ArrayList<>();
        int lineStartIndex = 0;
        int breakGlyphIdx = -1;
        int breakCharIdx = -1;

        for (Segment seg : segments) {
            Style style = seg.style();
            String font = fontForStyle(style);
            int color = style.getColor() != null ? (0xFF << 24) | style.getColor().getValue() : theme().textPrimary();
            HoverEvent hover = style.getHoverEvent();

            if (seg.item() != null && !seg.item().isEmpty()) {
                if (activeRenderer != null) {
                    activeRenderer.end();
                    activeRenderer = null;
                }
                float itemSize = Math.max(12.0f, fontSize);
                if (lineX + itemSize > maxWidth && lineX > 0f) {
                    rebuilt.add(new CachedLine(lineStartIndex, Math.max(lineStartIndex, charIndex - 1), glyphs));
                    glyphs = new ArrayList<>();
                    lineX = 0f;
                    lineStartIndex = charIndex;
                    breakGlyphIdx = -1;
                    breakCharIdx = -1;
                }
                String accessible = seg.text();
                int logicalLength = Math.max(0, seg.logicalLength());
                glyphs.add(new Glyph(
                        lineX,
                        lineX + itemSize,
                        charIndex,
                        charIndex + logicalLength,
                        theme().textPrimary(),
                        hover,
                        "",
                        accessible,
                        style,
                        seg.item().copy()
                ));
                lineX += itemSize;
                charIndex += logicalLength;
                continue;
            }

            if (hover instanceof HoverEvent.ShowItem(net.minecraft.world.item.ItemStackTemplate itemTemplate)) {
                ItemStack stack = resolveCachedItem(itemTemplate.create());

                if (IllegalItemUtil.isIllegal(stack)) {
                    color = IllegalItemUtil.illegalColor();
                } else if (TopEnchantUtil.hasTopEnchant(stack)) {
                    color = TopEnchantUtil.topColor();
                }
            }

            if (color == theme().textPrimary()) {
                if (!(hover instanceof HoverEvent.ShowItem)) {
                    BetterChatHoverCache cache = BetterChatStoreManager.getActiveCache();
                    if (cache != null) {
                        String cleaned = seg.text().replaceAll("[\\[\\]<>‚]", "").trim();
                        if (!cleaned.isEmpty()) {
                            cache.findItemByDisplayNameWithMeta(cleaned);
                        }
                    }
                }
            }

            String txt = seg.text();

            for (int c = 0; c < txt.length(); ) {
                int cp = txt.codePointAt(c);
                int clusterEnd = nextTextClusterEnd(txt, c);
                int cpLen = clusterEnd - c;

                if (cp == '\n') {
                    rebuilt.add(new CachedLine(lineStartIndex, Math.max(lineStartIndex, charIndex - 1), glyphs));
                    glyphs = new ArrayList<>();
                    lineX = 0f;
                    lineStartIndex = charIndex;
                    breakGlyphIdx = -1;
                    breakCharIdx = -1;
                    charIndex += 1;
                    c += 1;
                    continue;
                }

                String glyphText = txt.substring(c, clusterEnd);

                String resolvedFont = fontForCluster(font, glyphText);
                boolean svgGlyph = TextGlyphFallback.isSvgFontKey(resolvedFont);
                TextRenderer tr = svgGlyph ? null : fontRenderer(resolvedFont);

                if (svgGlyph) {
                    if (activeRenderer != null) {
                        activeRenderer.end();
                        activeRenderer = null;
                    }
                    activeFont = resolvedFont;
                } else if (tr != activeRenderer) {
                    if (activeRenderer != null) {
                        activeRenderer.end();
                    }
                    activeRenderer = tr;
                    activeFont = resolvedFont;
                    if (activeRenderer != null) {
                        activeRenderer.begin(scale, true, false);
                    }
                }
                float cw = svgGlyph
                        ? svgGlyphSize(fontRenderer(font), fontSize, false)
                        : activeRenderer != null ? (float) activeRenderer.getWidth(glyphText, false) : 0f;
                int glyphCharIndex = charIndex;

                if (lineX + cw > maxWidth && lineX > 0f) {
                    if (breakGlyphIdx >= 0) {
                        List<Glyph> before = new ArrayList<>(glyphs.subList(0, breakGlyphIdx));
                        int endChar = Math.max(lineStartIndex, breakCharIdx - 1);
                        rebuilt.add(new CachedLine(lineStartIndex, endChar, before));

                        List<Glyph> remaining = glyphs.subList(breakGlyphIdx + 1, glyphs.size());
                        glyphs = new ArrayList<>(remaining.size());
                        float nx = 0f;
                        for (Glyph g : remaining) {
                            float gw = g.x1() - g.x0();
                            glyphs.add(new Glyph(
                                    nx,
                                    nx + gw,
                                    g.charIndex(),
                                    g.charEndExclusive(),
                                    g.color(),
                                    g.hover(),
                                    g.font(),
                                    g.text(),
                                    g.style(),
                                    g.item()
                            ));
                            nx += gw;
                        }
                        lineX = nx;
                        lineStartIndex = breakCharIdx + 1;
                        breakGlyphIdx = -1;
                        breakCharIdx = -1;
                    } else {
                        rebuilt.add(new CachedLine(lineStartIndex, Math.max(lineStartIndex, charIndex - 1), glyphs));
                        glyphs = new ArrayList<>();
                        lineX = 0f;
                        lineStartIndex = charIndex;
                    }
                }

                glyphs.add(new Glyph(
                        lineX,
                        lineX + cw,
                        glyphCharIndex,
                        glyphCharIndex + cpLen,
                        color,
                        hover,
                        activeFont != null ? activeFont : resolvedFont,
                        glyphText,
                        style,
                        null
                ));
                lineX += cw;

                charIndex += cpLen;

                if (cp == ' ') {
                    breakGlyphIdx = glyphs.size() - 1;
                    breakCharIdx = glyphCharIndex;
                }

                c += cpLen;
            }
        }

        if (!glyphs.isEmpty()) {
            rebuilt.add(new CachedLine(lineStartIndex, charIndex - 1, glyphs));
        } else if (charIndex == 0) {
            rebuilt.add(new CachedLine(0, 0, List.of(new Glyph(
                    0f,
                    2f,
                    0,
                    1,
                    theme().textPrimary(),
                    null,
                    "iosevka_medium",
                    " ",
                    Style.EMPTY,
                    null
            ))));
        }

        if (activeRenderer != null) {
            activeRenderer.end();
        }
        MESSAGE_LAYOUT_CACHE.put(msg, new CachedMessageLayout(fontSize, maxWidth, rebuilt));
        return rebuilt;
    }

    private static List<Segment> flatten(BetterChatMessage message) {
        List<Segment> segments = new ArrayList<>();
        BetterChatMessage safe = message == null ? BetterChatMessage.empty() : message;
        for (var node : safe.nodes()) {
            if (node instanceof TextNode text) {
                String[] previousItemKey = {null};
                text.component().visit((style, string) -> {
                    if (string != null && !string.isEmpty()) {
                        Style safeStyle = style == null ? Style.EMPTY : style;
                        ItemStack hoveredItem = resolveItemFromStyle(safeStyle);
                        if (!hoveredItem.isEmpty()) {
                            String itemKey = BetterChatStoreManager.hoverItemKey(hoveredItem);
                            if (!itemKey.equals(previousItemKey[0])) {
                                int iconOffset = showItemIconOffset(string);
                                if (iconOffset > 0) {
                                    segments.add(Segment.text(string.substring(0, iconOffset), safeStyle));
                                }
                                segments.add(Segment.decorativeItem(hoveredItem, safeStyle));
                                if (iconOffset < string.length()) {
                                    segments.add(Segment.text(string.substring(iconOffset), safeStyle));
                                }
                            } else {
                                segments.add(Segment.text(string, safeStyle));
                            }
                            previousItemKey[0] = itemKey;
                        } else {
                            previousItemKey[0] = null;
                            segments.add(Segment.text(string, safeStyle));
                        }
                    }
                    return Optional.empty();
                }, Style.EMPTY);
            } else if (node instanceof ItemNode item) {
                ItemStack stack = item.stack();
                if (!stack.isEmpty()) segments.add(Segment.richItem(item.plainText(), stack));
            }
        }
        return segments;
    }

    /** Keep the vanilla item brackets and place the real icon just inside the opening bracket. */
    private static int showItemIconOffset(String text) {
        if (text == null || text.isEmpty()) return 0;
        return text.codePointAt(0) == '[' ? Character.charCount(text.codePointAt(0)) : 0;
    }

    static ItemStack resolveItemFromStyle(Style style) {
        if (style == null) return ItemStack.EMPTY;
        HoverEvent hover = style.getHoverEvent();
        if (!(hover instanceof HoverEvent.ShowItem(net.minecraft.world.item.ItemStackTemplate template))) {
            return ItemStack.EMPTY;
        }
        return resolveCachedItem(template.create());
    }

    private static ItemStack resolveCachedItem(ItemStack source) {
        if (source == null || source.isEmpty()) return ItemStack.EMPTY;
        BetterChatHoverCache cache = BetterChatStoreManager.getActiveCache();
        if (cache == null) return source.copy();
        String key = BetterChatStoreManager.hoverItemKey(source);
        ItemStack cached = cache.getItem(key);
        return cached.isEmpty() ? source.copy() : cached;
    }

    private static ItemStack resolvePreviewItem(PickResult pick) {
        if (pick == null) return ItemStack.EMPTY;
        GlyphBox glyph = pick.glyph();
        if (glyph != null) {
            if (glyph.item() != null && !glyph.item().isEmpty()) return glyph.item().copy();
            ItemStack styled = resolveItemFromStyle(glyph.style());
            if (!styled.isEmpty()) return styled;
        }

        if (pick.line() != null && pick.line().message() != null) {
            String raw = pick.line().message().text().getString();
            HoverTip inferred = ChatHoverUtil.inferFromDisplay(
                    wordAt(raw, glyph == null ? 0 : glyph.charIndex()),
                    true,
                    Minecraft.getInstance()
            );
            if (inferred != null && inferred.item() != null && !inferred.item().isEmpty()) {
                return inferred.item().copy();
            }
        }

        BetterChatMessage message = pick.line() != null && pick.line().message() != null
                ? pick.line().message().message()
                : null;
        if (message == null) return ItemStack.EMPTY;

        ItemStack found = ItemStack.EMPTY;
        for (var node : message.nodes()) {
            ItemStack candidate = ItemStack.EMPTY;
            if (node instanceof ItemNode item) {
                candidate = resolveCachedItem(item.stack());
            } else if (node instanceof TextNode text) {
                final ItemStack[] fromText = {ItemStack.EMPTY};
                text.component().visit((style, value) -> {
                    if (fromText[0].isEmpty()) fromText[0] = resolveItemFromStyle(style);
                    return Optional.empty();
                }, Style.EMPTY);
                candidate = fromText[0];
            }
            if (candidate.isEmpty()) continue;
            if (!found.isEmpty() && !sameItemIdentity(found, candidate)) {
                return ItemStack.EMPTY;
            }
            found = candidate.copy();
        }
        return found;
    }

    private static boolean sameItemIdentity(ItemStack left, ItemStack right) {
        return left.getItem() == right.getItem()
                && left.getCount() == right.getCount()
                && java.util.Objects.equals(left.getComponents(), right.getComponents());
    }

    private static void updateSearchHotkey(Minecraft mc, boolean chatOpen) {
        if (!chatOpen || mc == null || mc.getWindow() == null) {
            searchHotkeyDown = false;
            return;
        }

        long handle = mc.getWindow().handle();

        boolean ctrl =
                glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW_PRESS ||
                        glfwGetKey(handle, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW_PRESS;

        boolean f =
                glfwGetKey(handle, GLFW.GLFW_KEY_F) == GLFW_PRESS;

        boolean down = ctrl && f;

        if (down && !searchHotkeyDown) {
            toggleSearchMode();
        }

        searchHotkeyDown = down;
    }

    private static void toggleSearchMode() {
        if (BetterChatSearch.isActive() || BetterChatSearch.hasQuery()) {
            BetterChatSearch.deactivate();
        } else {
            BetterChatSearch.setActive(true);
        }
    }

    private static void renderInput(Minecraft mc, float fontSize, float boxW, float x, float inputY) {
        if (!(ClientScreen.current() instanceof ChatScreen chatScreen)) return;
        EditBox field = ((ChatScreenAccessor) chatScreen).getChatField();
        if (field == null) return;

        String fieldText = field.getValue();
        boolean searchMode = BetterChatSearch.isActive();
        String text = searchMode ? BetterChatSearch.getText() : fieldText;
        int cursor = searchMode ? text.length() : field.getCursorPosition();
        int selStart = searchMode ? cursor : selectionStart(field, cursor);
        int selEnd = searchMode ? cursor : selectionEnd(field, cursor);

        BetterChat cfg = BetterChat.get();
        ChatPasswordHeuristics.SensitiveRange sensitiveRange = !searchMode && cfg != null && cfg.passwordPrivacy()
                ? ChatPasswordHeuristics.sensitiveRange(fieldText)
                : null;
        updatePasswordPrivacyState(fieldText, sensitiveRange);

        float padX = 12f;
        float padY = 7f;
        float lineHeight = fontSize + 2f;
        float iconGap = 8f;
        float searchBoxW = SEARCH_ICON_WIDTH;
        float searchBoxH = SEARCH_ICON_HEIGHT;
        float counterReserve = (!searchMode && text.length() >= MAX_MESSAGE_CHARS - 20) ? 34f : 0f;
        float modeReserve = (searchMode || BetterChatSearch.hasQuery()) ? 74f : 0f;
        float rightReserve = counterReserve + modeReserve;

        if (!searchMode && text.length() > MAX_MESSAGE_CHARS) {
            text = text.substring(0, MAX_MESSAGE_CHARS);
            field.setValue(text);
            setCursorSafe(field, text.length());
        }
        int remaining = searchMode ? Integer.MAX_VALUE : MAX_MESSAGE_CHARS - text.length();

        float maxLineWidth = Math.max(32f, boxW - padX * 2f - searchBoxW - iconGap - rightReserve);
        List<InputLine> lines = wrapInput(text, fontSize, maxLineWidth);
        if (lines.isEmpty()) {
            lines = List.of(new InputLine(0, 0, 0f));
        }
        float contentHeight = lines.size() * lineHeight;
        float h = Math.max(36f, contentHeight + padY * 2f);
        inputBoxX = x;
        inputBoxY = inputY;
        inputBoxW = boxW;
        inputBoxH = h;
        hasInputBox = true;

        drawGlassPill(x, inputY, boxW, h, Math.min(INPUT_RADIUS, h * 0.5f), 0.92f, false);

        float searchX = x + padX;
        float searchY = inputY + (h - searchBoxH) * 0.5f;
        renderSearchIcon(searchX, searchY, searchBoxW, searchBoxH, BetterChatSearch.isActive(), BetterChatSearch.hasQuery());
        searchRectX = searchX;
        searchRectY = searchY;
        searchRectW = searchBoxW;
        searchRectH = searchBoxH;
        searchHit = true;

        float textX = searchX + searchBoxW + iconGap;
        float textClipW = Math.max(20f, x + boxW - padX - textX - rightReserve);
        TextRenderer tr = getIosevkaRegular();
        float textScale = scaleForSize(fontSize);
        boolean clipped = ScissorFunction.pushRaw(textX, inputY, textClipW, h);
        if (tr != null) {
            tr.begin(textScale, false, false);
            float textH = (float) tr.getHeight(false);
            tr.end();
            float textYOffset = (lineHeight - textH) * 0.5f;
            float lineY = inputY + padY + textYOffset;
            if (text.isEmpty()) {
                drawText(tr, searchMode ? "Search chat..." : "Message...", textX, lineY, fontSize, theme().textMuted(), false);
            } else {
                for (InputLine line : lines) {
                    renderInputLine(tr, text, line, sensitiveRange, textX, lineY, fontSize, lineHeight);
                    lineY += lineHeight;
                }
            }
        }

        int min = Math.min(selStart, selEnd);
        int max = Math.max(selStart, selEnd);
        if (max > min) {
            for (int i = 0; i < lines.size(); i++) {
                InputLine line = lines.get(i);
                int lineStart = line.start();
                int lineEnd = line.end();
                int selA = Math.max(min, lineStart);
                int selB = Math.min(max, lineEnd);
                if (selB > selA) {
                    float selX = textX + textWidth(getIosevkaRegular(), safeSub(text, lineStart, selA), fontSize);
                    float selW = textWidth(getIosevkaRegular(), safeSub(text, selA, selB), fontSize);
                    float sy = inputY + padY + i * lineHeight;
                    drawRoundedRect(selX, sy, selW, lineHeight, 3.5f, withAlpha(theme().accent(), 0x4A));
                }
            }
        }

        int caretLine = 0;
        for (int i = 0; i < lines.size(); i++) {
            InputLine line = lines.get(i);
            if (cursor >= line.start() && cursor <= line.end()) {
                caretLine = i;
                break;
            }
        }

        float caretX = textX + widthTo(text, lines, cursor, fontSize);
        float caretY = inputY + padY + caretLine * lineHeight;
        boolean blink = (System.currentTimeMillis() / 500L) % 2 == 0;
        if (blink) {
            drawRoundedRect(caretX, caretY + 1f, 1.35f, Math.max(8f, lineHeight - 2f), 0.8f, withAlpha(theme().textPrimary(), 0xD8));
        }
        if (clipped) ScissorFunction.pop();

        if (BetterChatSearch.isActive() || BetterChatSearch.hasQuery()) {
            String q = BetterChatSearch.isActive() ? "Search" : BetterChatSearch.getText();
            float chipFont = Math.max(10f, fontSize * 0.72f);
            String chipText = fitText(getInterRegular(), q, chipFont, Math.max(36f, boxW * 0.28f));
            float chipW = textWidth(getInterRegular(), chipText, chipFont) + 14f;
            float chipH = Math.min(22f, h - 8f);
            float chipX = x + boxW - padX - chipW - counterReserve;
            float chipY = inputY + (h - chipH) * 0.5f;
            drawRoundedRect(chipX, chipY, chipW, chipH, chipH * 0.5f, withAlpha(theme().accentSoft(), BetterChatSearch.isActive() ? 0x66 : 0x40));
            drawText(getInterRegular(), chipText, chipX + 7f, chipY + (chipH - textHeight(getInterRegular(), chipFont)) * 0.5f, chipFont,
                    BetterChatSearch.hasQuery() ? theme().textPrimary() : theme().textMuted(), false);
        }

        if (!searchMode && remaining <= 20) {
            String remStr = String.valueOf(Math.max(0, remaining));
            float remSize = fontSize * 0.74f;
            int remColor = remaining <= 5 ? withAlpha(theme().accent(), 0xFF) : withAlpha(theme().textMuted(), 0xCC);
            drawText(getIosevkaRegular(), remStr, searchX + 2.0f,
                    inputY + h + 3.0f,
                    remSize, remColor, false);
        }
    }

    private static void updatePasswordPrivacyState(String fieldText, ChatPasswordHeuristics.SensitiveRange range) {
        passwordMaskRects.clear();
        if (range == null) {
            passwordReveal = false;
            passwordMaskClickPending = false;
            passwordRevealProgress = 0f;
            passwordInputSnapshot = "";
            return;
        }

        String current = fieldText == null ? "" : fieldText;
        if (!current.equals(passwordInputSnapshot)) {
            // Editing a credential re-masks it immediately. Do not animate from a previously
            // revealed state, otherwise the new character could flash on screen.
            passwordReveal = false;
            passwordMaskClickPending = false;
            passwordRevealProgress = 0f;
            passwordInputSnapshot = current;
        }
        passwordRevealProgress = AnimationUtility.approach(
                passwordRevealProgress,
                passwordReveal ? 1f : 0f,
                AnimationUtility.deltaTime(),
                12.0f
        );
        passwordRevealProgress = AnimationUtility.snap(passwordRevealProgress, passwordReveal ? 1f : 0f, 0.015f);
    }

    private static void resetPasswordPrivacy() {
        passwordMaskRects.clear();
        passwordReveal = false;
        passwordMaskClickPending = false;
        passwordRevealProgress = 0f;
        passwordInputSnapshot = "";
    }

    private static void renderInputLine(TextRenderer tr,
                                        String text,
                                        InputLine line,
                                        ChatPasswordHeuristics.SensitiveRange sensitiveRange,
                                        float textX,
                                        float lineY,
                                        float fontSize,
                                        float lineHeight) {
        if (sensitiveRange == null || !sensitiveRange.intersects(line.start(), line.end())) {
            drawText(tr, safeSub(text, line.start(), line.end()), textX, lineY, fontSize, theme().textPrimary(), true);
            return;
        }

        int secretStart = Math.max(line.start(), sensitiveRange.start());
        int secretEnd = Math.min(line.end(), sensitiveRange.end());
        String before = safeSub(text, line.start(), secretStart);
        String secret = safeSub(text, secretStart, secretEnd);
        String after = safeSub(text, secretEnd, line.end());

        float cursorX = textX;
        if (!before.isEmpty()) {
            drawText(tr, before, cursorX, lineY, fontSize, theme().textPrimary(), true);
            cursorX += textWidth(tr, before, fontSize);
        }

        float secretW = Math.max(1f, textWidth(tr, secret, fontSize));
        float easedReveal = AnimationUtility.smoothstep(passwordRevealProgress);
        if (easedReveal > 0.01f && !secret.isEmpty()) {
            int textAlpha = Math.round(255f * easedReveal);
            drawText(tr, secret, cursorX, lineY, fontSize, withAlpha(theme().textPrimary(), textAlpha), true);
        }

        float maskAlpha = 1f - easedReveal;
        if (maskAlpha > 0.01f) {
            float maskPadX = 3.5f;
            float maskH = Math.max(12f, fontSize * 0.88f);
            float maskY = lineY + (lineHeight - maskH) * 0.5f;
            float maskX = cursorX - maskPadX;
            float maskW = Math.max(22f, secretW + maskPadX * 2f);
            drawPasswordPrivacyMask(maskX, maskY, maskW, maskH, maskAlpha);
            if (!passwordReveal || passwordRevealProgress < 0.96f) {
                passwordMaskRects.add(new PasswordMaskRect(maskX, maskY, maskW, maskH));
            }
        }

        cursorX += secretW;
        if (!after.isEmpty()) {
            drawText(tr, after, cursorX, lineY, fontSize, theme().textPrimary(), true);
        }
    }

    private static void drawPasswordPrivacyMask(float x, float y, float w, float h, float alpha) {
        if (renderer == null || w <= 0f || h <= 0f) return;
        float a = Mth.clamp(alpha, 0f, 1f);
        int surfaceAlpha = Math.round(190f * a);
        int gradientAlpha = Math.round(178f * a);
        int strokeAlpha = Math.round(150f * a);
        float radius = Math.min(5.5f, h * 0.5f);

        renderer.roundedRect(x, y, w, h, radius, 1.0f, withAlpha(theme().windowBg(), surfaceAlpha));
        HudRenderUtil.ThemeGradient fill = HudRenderUtil.themeAccentGradient(gradientAlpha);
        renderer.roundedRectGradient(x, y, w, h, radius, 1.0f, fill.start(), fill.end(), fill.angleDeg());
        HudRenderUtil.ThemeGradient stroke = HudRenderUtil.themeAccentGradient(strokeAlpha);
        renderer.roundedRectStrokeGradient(x, y, w, h, radius, 1.0f, 0.7f, stroke.start(), stroke.end(), stroke.angleDeg());

        // A small moving highlight keeps the privacy surface readable as an intentional control
        // rather than a flat censor bar, while remaining fully theme-driven.
        float shimmer = (AnimationUtility.time(0.00022f) % 1f);
        float highlightW = Math.max(8f, w * 0.18f);
        float highlightX = x + (w + highlightW) * shimmer - highlightW;
        boolean clipped = ScissorFunction.pushRaw(x, y, w, h);
        renderer.roundedRectGradient(
                highlightX, y + 1f, highlightW, Math.max(1f, h - 2f),
                Math.max(1f, radius - 1f), 1.0f,
                withAlpha(0xFFFFFFFF, Math.round(30f * a)),
                withAlpha(0xFFFFFFFF, 0),
                0f
        );
        if (clipped) ScissorFunction.pop();
    }

    private static boolean isPasswordMaskHovered(double x, double y) {
        if (passwordReveal && passwordRevealProgress >= 0.96f) return false;
        for (PasswordMaskRect rect : passwordMaskRects) {
            if (x >= rect.x() && x <= rect.x() + rect.w() && y >= rect.y() && y <= rect.y() + rect.h()) {
                return true;
            }
        }
        return false;
    }

    private static List<InputLine> wrapInput(String text, float fontSize, float maxWidth) {
        List<InputLine> lines = new ArrayList<>();
        int start = 0;
        float width = 0f;

        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            int cpLen = Character.charCount(cp);

            String glyphText = new String(Character.toChars(cp));
            float cw = textWidth(getIosevkaRegular(), glyphText, fontSize);

            if (width + cw > maxWidth && width > 0f) {
                lines.add(new InputLine(start, i, width));
                start = i;
                width = 0f;
            }

            width += cw;
            i += cpLen;
        }

        lines.add(new InputLine(start, text.length(), width));
        return lines;
    }

    private static float widthTo(String text, List<InputLine> lines, int cursorIndex, float fontSize) {
        if (lines.isEmpty()) return 0f;
        for (InputLine line : lines) {
            if (cursorIndex >= line.start() && cursorIndex <= line.end()) {
                return textWidth(getIosevkaRegular(), safeSub(text, line.start(), cursorIndex), fontSize);
            }
        }
        InputLine last = lines.getLast();
        return textWidth(getIosevkaRegular(), safeSub(text, last.start(), last.end()), fontSize);
    }

    public static boolean onScroll(double delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!BetterChat.isActive()) return false;
        if (!(ClientScreen.current() instanceof ChatScreen)) return false;
        MousePos mouse = resolveMouse(mc.mouseHandler.xpos(), mc.mouseHandler.ypos());
        double mx = mouse.fx();
        double my = mouse.fy();
        double rawX = mouse.rawX();
        double rawY = mouse.rawY();
        boolean insideSuggest = isInsideSuggestWindow(mx, my, rawX, rawY);
        if (insideSuggest) {
            int step = delta > 0 ? -1 : 1;
            int scrollable = Math.max(0, suggestTotal - suggestVisible);
            suggestCurrentStart = Mth.clamp(suggestCurrentStart + step, 0, scrollable);
            applySuggestionWindowState(suggestCurrentStart, suggestCurrentSelection);
            //DebugLog.info("[BetterChat][Suggest] scroll handled start=%d total=%d visible=%d mx=%.1f my=%.1f box=(%.1f,%.1f,%.1f,%.1f)", suggestStart, suggestTotal, suggestVisible, mx, my, suggestX, suggestY, suggestW, suggestH);
            return true;
        }
        int step = delta > 0 ? 3 : -3;
        scrollOffsetLines = Mth.clamp(scrollOffsetLines + step, 0, maxScrollLines);
        lastScrollInteractionMs = System.currentTimeMillis();
        return false;
    }

    public static boolean onMouseButton(double mx, double my, int button, boolean pressed) {
        Minecraft mc = Minecraft.getInstance();
        if (!BetterChat.isActive()) return false;
        if (!(ClientScreen.current() instanceof ChatScreen)) return false;

        MousePos mouse = resolveMouse(mx, my);
        double fx = mouse.fx();
        double fy = mouse.fy();
        boolean leftUp = button == GLFW.GLFW_MOUSE_BUTTON_LEFT && !pressed;

        boolean overSuggestWindow = isInsideSuggestWindow(fx, fy, mouse.rawX(), mouse.rawY());

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && sbEnabled && isScrollbarHovered(fx, fy)) {
            if (pressed) {
                draggingScrollbar = true;
                sbDragOffset = (fy >= sbThumbY && fy <= sbThumbY + sbThumbH)
                        ? (float) fy - sbThumbY
                        : sbThumbH * 0.5f;
                scrollToPosition(fy);
            } else {
                draggingScrollbar = false;
            }
            return true;
        }
        if (draggingScrollbar) {
            if (leftUp) draggingScrollbar = false;
            return true;
        }

        float sTop = suggestRowsY;
        boolean overSuggestRows = isInsideSuggestRows(fx, fy, mouse.rawX(), mouse.rawY());

        if (suggestDraggingScrollbar) {
            if (leftUp) suggestDraggingScrollbar = false;
            return true;
        }

        if (suggestHasScrollbar && isInsideSuggestScrollbar(fx, fy)) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (pressed) {
                    suggestDraggingScrollbar = true;
                    scrollSuggestToPosition(fy);
                }
            }
            return true;
        }

        if (overSuggestRows) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && pressed) {
                int row = (int) ((fy - sTop) / suggestItemH);
                row = Mth.clamp(row, 0, Math.max(0, suggestVisible - 1));
                int idx = Mth.clamp(suggestStart + row, 0, Math.max(0, suggestTotal - 1));
                applySuggestion(idx);
                //DebugLog.info("[BetterChat][Suggest] click row=%d idx=%d mx=%.1f my=%.1f raw=(%.1f,%.1f) box=(%.1f,%.1f,%.1f,%.1f)", row, idx, fx, fy, mouse.rawX(), mouse.rawY(), suggestX, suggestY, suggestW, suggestH);
                return true;
            }
            // block other clicks while over suggest box
            return true;
        }

        if (overSuggestWindow) {
            return true;
        }

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (pressed && isPasswordMaskHovered(fx, fy)) {
                passwordReveal = true;
                passwordMaskClickPending = true;
                return true;
            }
            if (!pressed && passwordMaskClickPending) {
                passwordMaskClickPending = false;
                return true;
            }
        }

        if (searchHit) {
            boolean inside = fx >= searchRectX && fx <= searchRectX + searchRectW && fy >= searchRectY && fy <= searchRectY + searchRectH;
            if (inside && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (pressed) {
                    toggleSearchMode();
                }
                return true;
            }
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && pressed) {
                BetterChatSearch.setActive(false);
            }
        }

        if (contextMenu.open) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                if (pressed && contextMenu.contains(fx, fy)) {
                    ContextMenu.MenuEntry entry = contextMenu.pick(fy);
                    if (entry != null) entry.action().run();
                }
                contextMenu = ContextMenu.closed();
                return true;
            }
        }

        PickResult pick = frame.pick(fx, fy);
        if (pick == null) {
            if (pressed && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                selection.clear();
                selecting = false;
                leftPending = false;
            }
            if (!pressed && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                selecting = false;
            }
            contextMenu = ContextMenu.closed();
            return false;
        }

        boolean ctrlDown = InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_LEFT_CONTROL)
                || InputConstants.isKeyDown(mc.getWindow(), GLFW.GLFW_KEY_RIGHT_CONTROL);

        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (pressed) {
                // Кандидат на клик/выделение. Выделение начнём только при drag.
                leftPending = true;
                leftDownFx = fx;
                leftDownFy = fy;
                leftDownMsgIndex = pick.line().messageIndex();
                leftDownCharIndex = pick.glyph().charIndex();
                leftDownStyle = pick.glyph().style();
                selecting = false;              // не выделяем сразу
                contextMenu = ContextMenu.closed();
                return true;                    // важно: блокируем ванильный suggest по клику
            } else {
                // отпускание ЛКМ
                if (leftPending) {
                    leftPending = false;

                    if (selecting) {
                        // закончили drag-выделение
                        selecting = false;
                        return true;
                    }

                    // одиночный клик (без drag)
                    boolean handled = handleClickEvent(leftDownStyle, ctrlDown);
                    if (!handled && ctrlDown) {
                        handled = tryPrefillTellFromClick(leftDownMsgIndex, leftDownCharIndex);
                    }
                    if (!handled) selection.clear();
                    return true; // блокируем ванильную обработку кликов по чату
                }
            }
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (pressed) {
                openContextMenu(pick, mx, my, resolvePreviewItem(pick));
                return true;
            }
        }
        return false;
    }

    public static void onMouseMove(double mx, double my) {
        if (!BetterChat.isActive()) return;
        MousePos mouse = resolveMouse(mx, my);
        double fx = mouse.fx();
        double fy = mouse.fy();
        if (draggingScrollbar) {
            scrollToPosition(fy);
            return;
        }
        if (suggestDraggingScrollbar) {
            scrollSuggestToPosition(fy);
            return;
        }
        if (isInsideSuggestWindow(fx, fy, mouse.rawX(), mouse.rawY())) return;

        if (leftPending && !selecting) {
            double dx = fx - leftDownFx;
            double dy = fy - leftDownFy;
            if (dx * dx + dy * dy >= DRAG_SELECT_THRESHOLD * DRAG_SELECT_THRESHOLD) {
                selection.update(leftDownMsgIndex, leftDownCharIndex);
                selecting = true;
            } else {
                // пока не потащили — не начинаем выделение
                return;
            }
        }

        if (!selecting) return;
        PickResult pick = frame.pick(mouse.fx(), mouse.fy());
        if (pick == null) {
            pick = nearestFramePick(mouse.fx(), mouse.fy());
        }
        if (pick == null) return;
        selection.updateCaret(pick.line().messageIndex(), selectionCaretIndex(pick.glyph()));
    }

    private static int selectionCaretIndex(GlyphBox glyph) {
        if (glyph == null) return 0;
        return Math.max(glyph.charIndex(), glyph.charEndExclusive() - 1);
    }

    private static PickResult nearestFramePick(double mx, double my) {
        if (frame == null || frame.lines() == null || frame.lines().isEmpty()) return null;

        FrameLine bestLine = null;
        float bestDist = Float.MAX_VALUE;
        for (FrameLine line : frame.lines()) {
            float dy;
            if (my < line.y0()) {
                dy = (float) (line.y0() - my);
            } else if (my > line.y1() + LINE_SPACING) {
                dy = (float) (my - (line.y1() + LINE_SPACING));
            } else {
                dy = 0.0f;
            }

            if (dy < bestDist) {
                bestDist = dy;
                bestLine = line;
            }
        }

        if (bestLine == null || bestLine.glyphs().isEmpty()) return null;
        GlyphBox first = bestLine.glyphs().getFirst();
        GlyphBox last = bestLine.glyphs().getLast();
        GlyphBox glyph = mx <= first.x0() ? first : last;
        return new PickResult(bestLine, glyph);
    }

    private static void openContextMenu(PickResult pick, double mx, double my, ItemStack previewItem) {
        List<ContextMenu.MenuEntry> entries = new ArrayList<>();
        ChatLine msg = pick.line().message();
        String full = msg.text().getString();
        int safeLen = full.length();
        BetterChat settings = BetterChat.get();
        long ts = msg.timestampMs();
        boolean tsEnabled = settings != null && settings.timestampEnabled();
        boolean tsUnix = settings != null && settings.timestampToggles().get("hover_unix");

        if (selection.appliesTo(pick.line().messageIndex()) && selection.hasRange()) {
            String selected = selectedText();
            entries.add(new ContextMenu.MenuEntry(I18n.get("better_chat.context.copy_selection"), () -> ClipboardUtil.copy(selected)));
        } else {
            entries.add(new ContextMenu.MenuEntry(I18n.get("better_chat.context.copy_message"), () -> ClipboardUtil.copy(full)));
        }

        if (previewItem != null && !previewItem.isEmpty()) {
            ItemStack stack = previewItem.copy();
            entries.add(new ContextMenu.MenuEntry(
                    I18n.get("better_chat.context.preview_item"),
                    () -> BetterChatItemInteraction.tryOpenPreview(stack)
            ));
        }

        List<String> nickCandidates = ChatNameUtil.extractNicks(full);
        String hovered = ChatNameUtil.normalizeNickCandidate(wordAt(full, pick.glyph().charIndex()));
        String targetNick = null;
        if (!hovered.isEmpty()
                && ChatNameUtil.isNickLike(hovered)
                && nickCandidates.stream().anyMatch(n -> n.equalsIgnoreCase(hovered))) {
            targetNick = hovered;
        } else if (nickCandidates.size() == 1) {
            targetNick = nickCandidates.getFirst();
        }
        if (isOnlinePlayer(targetNick)) {
            String nickToTell = targetNick;
            entries.add(new ContextMenu.MenuEntry(I18n.get("better_chat.context.reply"), () -> prefillTell(nickToTell)));
        }

        if (hoverEntityUuid != null && !hoverEntityUuid.isEmpty()) {
            String uuidToCopy = hoverEntityUuid;
            entries.add(new ContextMenu.MenuEntry(I18n.get("better_chat.context.copy_uuid"), () -> ClipboardUtil.copy(uuidToCopy)));
        }

        if (tsEnabled) {
            entries.add(new ContextMenu.MenuEntry(I18n.get("better_chat.context.copy_time"), () -> ClipboardUtil.copy(formatTimestamp(ts, settings.timestampSeconds()))));
            entries.add(new ContextMenu.MenuEntry(I18n.get("better_chat.context.copy_date"), () -> ClipboardUtil.copy(formatDate(ts))));
            if (tsUnix) {
                entries.add(new ContextMenu.MenuEntry(I18n.get("better_chat.context.copy_unix"), () -> ClipboardUtil.copy(String.valueOf(ts / 1000L))));
            }
        }

        contextMenu = ContextMenu.open((float) mx, (float) my, entries);
    }

    public static void copySelectionToClipboard() {
        String selected = selectedText();
        if (selected.isEmpty()) return;
        ClipboardUtil.copy(selected);
    }

    private static String selectedText() {
        if (!selection.hasRange()) return "";
        if (lastMessages == null || lastMessages.isEmpty()) return "";

        int startMsg = Mth.clamp(selection.startContext(), 0, lastMessages.size() - 1);
        int endMsg = Mth.clamp(selection.endContext(), 0, lastMessages.size() - 1);
        if (endMsg < startMsg) return "";

        StringBuilder out = new StringBuilder();
        for (int msgIdx = startMsg; msgIdx <= endMsg; msgIdx++) {
            ChatLine line = lastMessages.get(msgIdx);
            if (line == null || line.text() == null) continue;

            String full = line.text().getString();
            int len = full.length();
            int start = selection.startForLine(msgIdx);
            int end = selection.endForLine(msgIdx);

            int from = start == Integer.MIN_VALUE ? 0 : Mth.clamp(start, 0, len);
            int toInclusive = end == Integer.MAX_VALUE ? Math.max(0, len - 1) : Mth.clamp(end, 0, Math.max(0, len - 1));
            int to = Mth.clamp(toInclusive + 1, 0, len);

            if (to > from) {
                if (!out.isEmpty()) out.append(' ');
                out.append(safeSub(full, from, to));
            }
        }
        return out.toString();
    }

    private static boolean handleClickEvent(Style style, boolean ctrlDown) {
        if (style == null) return false;
        ClickEvent evt = style.getClickEvent();
        if (evt == null) return false;
        String val = switch (evt) {
            case ClickEvent.OpenUrl openUrl -> openUrl.uri().toString();
            case ClickEvent.OpenFile openFile -> openFile.path();
            case ClickEvent.RunCommand run -> run.command();
            case ClickEvent.SuggestCommand sug -> sug.command();
            case ClickEvent.CopyToClipboard copy -> copy.value();
            case ClickEvent.ShowDialog showDialog -> showDialog.dialog().value().toString();
            case ClickEvent.ChangePage changePage -> String.valueOf(changePage.page());
            case ClickEvent.Custom custom -> custom.id().toString();
            default -> null;
        };
        if (val == null || val.isEmpty()) return false;
        try {
            if (evt instanceof ClickEvent.OpenUrl || evt instanceof ClickEvent.OpenFile) {
                Minecraft mc = Minecraft.getInstance();
                if (ClientScreen.current() instanceof ChatScreen chat) {
                    ClientScreen.show(mc, new ConfirmLinkScreen(confirm -> {
                        if (confirm) Util.getPlatform().openUri(val);
                        ClientScreen.show(mc, chat);
                    }, val, false));
                } else {
                    Util.getPlatform().openUri(val);
                }
                return true;
            } else if (evt instanceof ClickEvent.CopyToClipboard) {
                ClipboardUtil.copy(val);
                return true;
            } else if (evt instanceof ClickEvent.RunCommand && val.startsWith("@")) {
                return CommandManager.handle(val);
            } else if (evt instanceof ClickEvent.SuggestCommand || evt instanceof ClickEvent.RunCommand) {
                // Убираем нежелательный автокомплит: без Ctrl не подставляем команды в поле ввода.
                if (!ctrlDown) return false;
                prefillChat(val);
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            //DebugLog.error("[BetterChat] click action failed", e);
            return false;
        }
    }

    private static boolean tryPrefillTellFromClick(int msgIndex, int charIndex) {
        if (msgIndex < 0 || msgIndex >= lastMessages.size()) return false;
        String full = lastMessages.get(msgIndex).text().getString();
        List<String> nickCandidates = ChatNameUtil.extractNicks(full);
        if (nickCandidates.isEmpty()) return false;

        String hovered = ChatNameUtil.normalizeNickCandidate(wordAt(full, charIndex));
        String targetNick = null;
        if (!hovered.isEmpty()
                && ChatNameUtil.isNickLike(hovered)
                && nickCandidates.stream().anyMatch(n -> n.equalsIgnoreCase(hovered))) {
            targetNick = hovered;
        } else if (nickCandidates.size() == 1) {
            targetNick = nickCandidates.getFirst();
        }
        if (!isOnlinePlayer(targetNick)) return false;
        prefillTell(targetNick);
        return true;
    }

    private static void prefillChat(String value) {
        if (value == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(ClientScreen.current() instanceof ChatScreen chatScreen)) return;
        EditBox field = ((ChatScreenAccessor) chatScreen).getChatField();
        if (field == null) return;
        field.setValue(value);
        setCursorSafe(field, value.length());
    }

    private static void prefillTell(String nick) {
        Minecraft mc = Minecraft.getInstance();
        if (!(ClientScreen.current() instanceof ChatScreen chatScreen)) return;
        if (!isOnlinePlayer(nick)) return;
        EditBox field = ((ChatScreenAccessor) chatScreen).getChatField();
        if (field == null) return;
        String value = "/tell " + nick + " ";
        field.setValue(value);
    }

    public static void applyAutoPrefixIfNeeded(EditBox field) {
        // Compact BetterChat no longer owns chat-prefix chips; hook kept for compatibility.
    }

    private static boolean isOnlinePlayer(String nick) {
        if (nick == null || nick.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null || mc.player.connection == null) return false;
        return mc.player.connection.getOnlinePlayers().stream()
                .anyMatch(e -> e.getProfile() != null && nick.equalsIgnoreCase(e.getProfile().name()));
    }

    private static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    private static int mulAlpha(int argb, float a) {
        int alpha = (int) (((argb >>> 24) & 0xFF) * a);
        return (argb & 0x00FFFFFF) | (Mth.clamp(alpha, 0, 255) << 24);
    }

    private static void renderSearchIcon(float x, float y, float w, float h, boolean active, boolean hasQuery) {
        boolean hover = lastMouseValid && lastMouseFx >= x && lastMouseFx <= x + w && lastMouseFy >= y && lastMouseFy <= y + h;
        float radius = h * 0.5f;
        int bg = active
                ? withAlpha(theme().accent(), 0x8E)
                : (hasQuery ? withAlpha(theme().accentSoft(), 0x8A) : withAlpha(theme().surface(), hover ? 0xC0 : 0xA2));
        int tint = withAlpha(theme().accent(), active ? 0x30 : (hasQuery ? 0x24 : 0x18));
        int strokeA = active ? withAlpha(theme().accent(), 0xC8) : withAlpha(theme().textPrimary(), hover ? 0x38 : 0x24);
        int strokeB = withAlpha(theme().accentSoft(), active ? 0x86 : (hasQuery ? 0x66 : 0x40));

        drawRoundedRect(x, y, w, h, radius, bg);
        drawRoundedRect(x + 1f, y + 1f, w - 2f, h - 2f, Math.max(0f, radius - 1f), tint);
        drawRoundedRectStrokeGradient(x, y, w, h, radius, 0.7f, strokeA, strokeB);

        TextRenderer icons = Fonts.renderer("Icons", FontInfo.Type.Regular, getInterRegular());
        String icon = "s";
        float iconSize = h * 0.50f;
        float iconW = textWidth(icons, icon, iconSize);
        float iconH = textHeight(icons, iconSize);
        int iconColor = active || hasQuery ? theme().textPrimary() : withAlpha(theme().textPrimary(), hover ? 0xD8 : 0xB8);
        drawText(icons, icon, x + (w - iconW) * 0.5f, y + (h - iconH) * 0.5f - h * 0.015f, iconSize, iconColor, false);
    }

    private static void drawLiquidHoverPill(float x, float y, float w, float h, float radius, float alpha) {
        if (renderer == null || w <= 0f || h <= 0f || alpha <= 0.001f) return;
        flushRenderer();
        renderer.liquidGlassRect(
                x, y, w, h,
                Math.max(1.0f, radius),
                10.5f,
                theme().accentSoft(),
                Mth.clamp(alpha, 0.0f, 1.0f),
                0.08f,
                -18.0f,
                0.82f,
                0.72f,
                0.34f,
                0.095f,
                1.2f
        );
        drawRoundedRect(x, y, w, h, radius, withAlpha(theme().surfaceHover(), 0x32));
        drawRoundedRectStrokeGradient(x, y, w, h, radius, 0.55f,
                withAlpha(theme().accentSoft(), 0x50),
                withAlpha(theme().accent(), 0x34));
    }

    private static void drawScrollbarGlass(float trackX,
                                           float trackY,
                                           float trackW,
                                           float trackH,
                                           float thumbY,
                                           float thumbH,
                                           boolean hot,
                                           int trackAlpha,
                                           int thumbAlpha) {
        if (renderer == null || trackW <= 0f || trackH <= 0f || thumbH <= 0f) return;

        SettingsGuiPalette palette = SettingsGuiPalette.current();
        int trackA = withAlpha(palette.moduleScrollTrackA(), trackAlpha);
        int trackB = withAlpha(palette.moduleScrollTrackB(), Math.min(255, trackAlpha + 0x18));
        int handleA = withAlpha(palette.moduleScrollHandleA(), thumbAlpha);
        int handleB = withAlpha(palette.moduleScrollHandleB(), Math.min(255, thumbAlpha + 0x0C));
        float radius = trackW * 0.5f;

        // Identical structure to ClickGui Modules/Settings: one vertical gradient for the track
        // and one for the handle. No rail, highlight, stroke or hover-width decoration.
        renderer.roundedRectGradientQuad(
                trackX, trackY, trackW, trackH, radius, 1.0f,
                trackA, trackB, trackB, trackA
        );
        renderer.roundedRectGradientQuad(
                trackX, thumbY, trackW, thumbH, radius, 1.0f,
                handleA, handleB, handleB, handleA
        );
    }

    private static void drawGlassPill(float x, float y, float w, float h, float radius, float alpha, boolean softPanel) {
        if (renderer == null || w <= 0f || h <= 0f) return;

        // BetterChat uses liquid glass as a background material only. Text is rendered later
        // and is not touched here. Blur strength is separate from the glass veil opacity.
        BetterChat settings = BetterChat.get();
        float drawAlpha = Mth.clamp(alpha, 0.0f, 1.0f);
        if (drawAlpha <= 0.001f) return;
        float configuredAlpha = settings != null ? settings.liquidGlassAlphaFactor() : (230f / 255f);
        float blurStrength = configuredAlpha * drawAlpha;
        float materialAlpha = Mth.clamp(0.72f + configuredAlpha * 0.28f, 0.0f, 1.0f) * drawAlpha;
        float glassRadius = Math.max(2f, radius);
        float thickness = softPanel ? 15.5f : 12.5f;
        float fresnelPower = softPanel ? -26.0f : -20.0f;
        float fresnelAlpha = Mth.clamp(0.72f + configuredAlpha * 0.28f, 0.0f, 1.0f) * drawAlpha;
        float baseAlpha = Mth.clamp((softPanel ? 0.66f : 0.68f) + configuredAlpha * (softPanel ? 0.34f : 0.32f), 0.0f, 1.0f);
        float fresnelMix = softPanel ? 0.46f : 0.44f;
        float distortPx = (softPanel ? 0.172f : 0.145f) * drawAlpha;

        flushRenderer();
        renderer.liquidGlassRect(
                x, y, w, h,
                glassRadius,
                thickness,
                theme().accent(),
                materialAlpha,
                blurStrength,
                fresnelPower,
                fresnelAlpha,
                baseAlpha,
                fresnelMix,
                distortPx,
                softPanel ? 2.65f : 2.25f
        );

        int veilAlpha = Math.round((softPanel ? 0x16 : 0x0C) * drawAlpha);
        if (veilAlpha > 0) {
            drawRoundedRect(x, y, w, h, glassRadius, withAlpha(theme().windowBg(), veilAlpha));
        }
    }

    private static String fitText(TextRenderer font, String text, float size, float maxWidth) {
        if (text == null) return "";
        if (textWidth(font, text, size) <= maxWidth) return text;
        String suffix = "...";
        float suffixW = textWidth(font, suffix, size);
        int end = text.length();
        while (end > 0 && textWidth(font, text.substring(0, end), size) + suffixW > maxWidth) {
            end--;
        }
        return end <= 0 ? suffix : text.substring(0, end) + suffix;
    }

    private static TextRenderer getInterRegular() {
        return Fonts.renderer("Inter", FontInfo.Type.Regular, TextRenderer.get());
    }

    private static TextRenderer getIosevkaRegular() {
        return Fonts.renderer("Iosevka", FontInfo.Type.Regular, TextRenderer.get());
    }

    private static TextRenderer getIosevkaItalic() {
        return Fonts.renderer("Iosevka", FontInfo.Type.Italic, getIosevkaRegular());
    }

    private static TextRenderer getIosevkaBold() {
        return Fonts.renderer("Iosevka", FontInfo.Type.Bold, getIosevkaRegular());
    }

    private static TextRenderer getIosevkaBoldItalic() {
        return Fonts.renderer("Iosevka", FontInfo.Type.BoldItalic, getIosevkaBold());
    }

    private static TextRenderer fontRenderer(String key) {
        if (key == null) return getIosevkaRegular();
        return switch (key) {
            case "iosevka_bold" -> getIosevkaBold();
            case "iosevka_bold_italic" -> getIosevkaBoldItalic();
            case "iosevka_medium_italic" -> getIosevkaItalic();
            case "vanilla_symbols" -> TextGlyphFallback.vanillaSymbols(getIosevkaRegular());
            case "vanilla" -> VanillaTextRenderer.INSTANCE;
            default -> getIosevkaRegular();
        };
    }

    private static String fontForGlyph(String baseFont, int codePoint) {
        TextRenderer preferred = fontRenderer(baseFont);
        return TextGlyphFallback.fontKeyForGlyph(baseFont, preferred, codePoint, "iosevka_medium");
    }

    private static String fontForCluster(String baseFont, String cluster) {
        if (cluster == null || cluster.isEmpty()) return baseFont;
        int first = cluster.codePointAt(0);
        if (Character.charCount(first) == cluster.length()) {
            return fontForGlyph(baseFont, first);
        }

        TextRenderer preferred = fontRenderer(baseFont);
        if (preferred != null) {
            boolean complete = true;
            for (int offset = 0; offset < cluster.length(); ) {
                int codePoint = cluster.codePointAt(offset);
                if (!preferred.hasGlyph(codePoint)) {
                    complete = false;
                    break;
                }
                offset += Character.charCount(codePoint);
            }
            if (complete) return baseFont != null ? baseFont : "iosevka_medium";
        }
        return TextGlyphFallback.VANILLA_KEY;
    }

    private static int nextTextClusterEnd(String text, int start) {
        int offset = start + Character.charCount(text.codePointAt(start));
        boolean afterJoiner = false;
        while (offset < text.length()) {
            int codePoint = text.codePointAt(offset);
            if (afterJoiner) {
                offset += Character.charCount(codePoint);
                afterJoiner = false;
                continue;
            }
            if (codePoint == 0x200D) {
                offset += Character.charCount(codePoint);
                afterJoiner = true;
                continue;
            }
            int type = Character.getType(codePoint);
            boolean combining = type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK;
            boolean variationSelector = codePoint >= 0xFE00 && codePoint <= 0xFE0F
                    || codePoint >= 0xE0100 && codePoint <= 0xE01EF;
            boolean emojiModifier = codePoint >= 0x1F3FB && codePoint <= 0x1F3FF;
            if (!combining && !variationSelector && !emojiModifier) break;
            offset += Character.charCount(codePoint);
        }
        return offset;
    }

    private static TextRenderer rendererForGlyph(TextRenderer preferred, int codePoint) {
        return TextGlyphFallback.rendererForGlyph(preferred, codePoint);
    }

    private static float scaleForSize(float size) {
        return size / 18.0f;
    }

    private static float textWidth(TextRenderer tr, String text, float size) {
        if (tr == null || text == null || text.isEmpty()) return 0f;
        float scale = scaleForSize(size);
        float svgAdvance = svgGlyphSize(tr, size, false);
        TextRenderer current = null;
        float width = 0f;
        try {
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                if (TextGlyphFallback.shouldUseVanillaSvg(tr, cp)) {
                    if (current != null) {
                        current.end();
                        current = null;
                    }
                    width += svgAdvance;
                    i += Character.charCount(cp);
                    continue;
                }
                String glyph = new String(Character.toChars(cp));
                TextRenderer next = rendererForGlyph(tr, cp);
                if (next != current) {
                    if (current != null) current.end();
                    current = next;
                    current.begin(scale, true, false);
                }
                width += (float) current.getWidth(glyph, false);
                i += Character.charCount(cp);
            }
            return width;
        } finally {
            if (current != null) current.end();
        }
    }

    private static float textHeight(TextRenderer tr, float size) {
        if (tr == null) return 0f;
        float scale = scaleForSize(size);
        tr.begin(scale, true, false);
        try {
            return (float) tr.getHeight(false);
        } finally {
            tr.end();
        }
    }

    private static void drawText(TextRenderer tr, String text, float x, float y, float size, int argb, boolean shadow) {
        if (tr == null || text == null || text.isEmpty()) return;
        if (((argb >>> 24) & 0xFF) <= 0) return;
        float scale = scaleForSize(size);
        float svgSize = svgGlyphSize(tr, size, shadow);
        float svgY = y + (textHeight(tr, size) - svgSize) * 0.5f;
        applyColor(argb);
        TextRenderer current = null;
        float cursorX = x;
        try {
            for (int i = 0; i < text.length(); ) {
                int cp = text.codePointAt(i);
                if (TextGlyphFallback.shouldUseVanillaSvg(tr, cp)) {
                    if (current != null) {
                        current.end();
                        current = null;
                    }
                    String svgName = TextGlyphFallback.vanillaSvgName(cp);
                    if (renderer != null && svgName != null) {
                        renderer.svg(svgName, cursorX, svgY, svgSize, svgSize,
                                SvgRenderOptions.fromFile().withAlpha(((argb >>> 24) & 0xFF) / 255.0f));
                    }
                    cursorX += svgSize;
                    i += Character.charCount(cp);
                    continue;
                }
                String glyph = new String(Character.toChars(cp));
                TextRenderer next = rendererForGlyph(tr, cp);
                if (next != current) {
                    if (current != null) current.end();
                    current = next;
                    current.begin(scale, false, false);
                }
                cursorX = (float) current.render(glyph, cursorX, y, TMP_COLOR, shadow);
                i += Character.charCount(cp);
            }
        } finally {
            if (current != null) current.end();
        }
    }

    private static float svgGlyphSize(TextRenderer tr, float size, boolean shadow) {
        return Math.max(1.0f, textHeight(tr, size)) * 0.92f;
    }

    private static void drawRoundedRect(float x, float y, float w, float h, float radius, int argb) {
        if (renderer == null) return;
        if (((argb >>> 24) & 0xFF) <= 0) return;
        BetterChat cfg = BetterChat.get();
        boolean useGradient = cfg != null && cfg.gradientEnabled();
        HudRenderUtil.drawHudBackground(renderer, x, y, w, h, radius, 1.1f, argb, useGradient);
    }

    private static void drawRoundedRectStrokeGradient(float x, float y, float w, float h, float radius, float thickness,
                                                      int startArgb, int endArgb) {
        if (renderer == null) return;
        if (((startArgb >>> 24) & 0xFF) <= 0 && ((endArgb >>> 24) & 0xFF) <= 0) return;
        renderer.roundedRectStrokeGradient(x, y, w, h, radius, 1.1f, thickness, startArgb, endArgb, (float) 0.0);
    }

    private static void drawRect(float x, float y, float w, float h, int argb) {
        if (renderer == null) return;
        if (((argb >>> 24) & 0xFF) <= 0) return;
        renderer.quad(x, y, w, h, argb);
    }

    private static void flushRenderer() {
        if (renderer == null) return;
        if (Renderer2D.isBatching()) {
            Renderer2D.flushBatch();
        } else {
            renderer.begin();
        }
    }

    private static void applyColor(int argb) {
        BetterChatRenderer.TMP_COLOR.a = (argb >>> 24) & 0xFF;
        BetterChatRenderer.TMP_COLOR.r = (argb >>> 16) & 0xFF;
        BetterChatRenderer.TMP_COLOR.g = (argb >>> 8) & 0xFF;
        BetterChatRenderer.TMP_COLOR.b = argb & 0xFF;
    }

    private static String fontForStyle(Style style) {
        boolean bold = style.isBold();
        boolean italic = style.isItalic();
        if (bold && italic) return "iosevka_bold_italic";
        if (bold) return "iosevka_bold";
        if (italic) return "iosevka_medium_italic";
        return "iosevka_medium";
    }

    private static String safeSub(String s, int start, int end) {
        if (s == null) return "";
        int len = s.length();
        start = Math.max(0, Math.min(len, start));
        end = Math.max(0, Math.min(len, end));
        if (end < start) {
            int t = start;
            start = end;
            end = t;
        }
        return s.substring(start, end);
    }

    private static int selectionStart(EditBox field, int fallback) {
        if (field == null) return fallback;
        return Mth.clamp(field.getCursorPosition(), 0, field.getValue().length());
    }

    private static int selectionEnd(EditBox field, int fallback) {
        if (field == null) return fallback;
        try {
            return Mth.clamp(((TextFieldWidgetAccessor) field).combatant$getSelectionEnd(), 0, field.getValue().length());
        } catch (Throwable ignored) {
            String selected = field.getHighlighted();
            int cursor = selectionStart(field, fallback);
            if (selected == null || selected.isEmpty()) return cursor;
            return Mth.clamp(cursor + selected.length(), 0, field.getValue().length());
        }
    }

    private static void setCursorSafe(EditBox field, int pos) {
        if (field == null) return;
        field.moveCursorTo(Mth.clamp(pos, 0, field.getValue().length()), false);
    }

    private static String wordAt(String text, int charIndex) {
        if (text == null || text.isEmpty()) return "";
        int idx = Mth.clamp(charIndex, 0, Math.max(0, text.length() - 1));
        int start = idx;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) start--;
        int end = idx;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) end++;
        if (end <= start) return "";
        return text.substring(start, end).replaceAll("[<>:\\[\\]]", "");
    }

    private static MousePos resolveMouse(double rawX, double rawY) {
        // No scaling; keep raw and framebuffer identical.
        return new MousePos(rawX, rawY, rawX, rawY);
    }

    public static void resetScroll() {
        scrollOffsetLines = 0;
        smoothScrollOffsetLines = 0f;
        draggingScrollbar = false;
        sbEnabled = false;
        sbDragOffset = 0f;
    }

    public static float getLastWidth() {
        return lastRenderWidth;
    }

    public static float getLastHeight() {
        return lastRenderHeight;
    }

    public static boolean contains(float mx, float my) {
        return isInteractive(mx, my);
    }

    public static boolean isInteractive(float mx, float my) {
        boolean inChat = chatHitW > 0f && mx >= chatHitX && mx <= chatHitX + chatHitW && my >= chatHitY && my <= chatHitY + chatHitH;
        boolean inSearch = searchHit && mx >= searchRectX && mx <= searchRectX + searchRectW && my >= searchRectY && my <= searchRectY + searchRectH;
        boolean inInput = hasInputBox && mx >= inputBoxX && mx <= inputBoxX + inputBoxW && my >= inputBoxY && my <= inputBoxY + inputBoxH;
        boolean inSuggest = isInsideSuggestWindow(mx, my);
        boolean inContext = contextMenu.open && contextMenu.contains(mx, my);
        boolean inScrollbar = sbEnabled && isScrollbarHovered(mx, my);
        return inChat || inSearch || inInput || inSuggest || inContext || inScrollbar;
    }

    public static void onNewMessage() {
        if (scrollOffsetLines > 0 && scrollOffsetLines <= 2) {
            scrollOffsetLines = 0;
            smoothScrollOffsetLines = 0f;
        }
    }

    private static boolean isInsideSuggestRows(double mx, double my) {
        if (!suggestActive) return false;
        return mx >= suggestRowsX && mx <= suggestRowsX + suggestRowsW
                && my >= suggestRowsY && my <= suggestRowsY + suggestRowsH;
    }

    private static boolean isInsideSuggestRows(double mx, double my, double rawX, double rawY) {
        return isInsideSuggestRows(mx, my) || isInsideSuggestRows(rawX, rawY);
    }

    private static boolean isInsideSuggestWindow(double mx, double my) {
        if (!suggestActive) return false;
        return mx >= suggestX && mx <= suggestX + suggestW && my >= suggestY && my <= suggestY + suggestH;
    }

    private static boolean isInsideSuggestWindow(double mx, double my, double rawX, double rawY) {
        return isInsideSuggestWindow(mx, my) || isInsideSuggestWindow(rawX, rawY);
    }

    private static boolean isInsideSuggestScrollbar(double mx, double my) {
        if (!suggestActive || !suggestHasScrollbar) return false;
        float hitPad = 4f;
        return mx >= suggestTrackX - hitPad &&
                mx <= suggestTrackX + suggestTrackW + hitPad &&
                my >= suggestTrackY - hitPad &&
                my <= suggestTrackY + suggestTrackH + hitPad;
    }

    private static float computeWidth(int screenW, float ratio) {
        float clamped = Mth.clamp(ratio, 0.2f, 0.8f);
        float w = screenW * clamped;
        return Mth.clamp(w, 320f, 560f);
    }

    private static BetterChatStore buildPreviewStore() {
        if (previewStore == null) {
            BetterChatStore store = new BetterChatStore();
            store.add(Component.literal("Better Chat"));
            store.add(Component.literal("Drag to position"));
            store.add(Component.literal("Messages appear here"));
            previewStore = store;
        }
        return previewStore;
    }

    private static void renderScrollbar(
            float boxX,
            float boxY,
            float boxW,
            float boxH,
            int startLine,
            int visibleLines,
            int totalLines,
            double mouseX,
            double mouseY) {
        if (visibleLines <= 0 || totalLines <= visibleLines) {
            sbEnabled = false;
            draggingScrollbar = false;
            return;
        }

        int scrollable = Math.max(1, totalLines - visibleLines);
        boolean hot = draggingScrollbar || isScrollbarHovered(mouseX, mouseY);
        boolean recently = System.currentTimeMillis() - lastScrollInteractionMs < 850L;
        float trackW = 5.0f;
        float trackX = boxX - trackW - 7f;
        float trackY = boxY + 10f;
        float trackH = Math.max(16f, boxH - 20f);
        float thumbH = Math.max(18f, trackH * ((float) visibleLines / (float) totalLines));
        float ratio = (float) startLine / (float) scrollable;
        float thumbY = trackY + (trackH - thumbH) * Mth.clamp(ratio, 0f, 1f);

        sbTrackX = trackX;
        sbTrackY = trackY;
        sbTrackW = trackW;
        sbTrackH = trackH;
        sbThumbY = thumbY;
        sbThumbH = thumbH;
        sbEnabled = true;

        if (hot || draggingScrollbar) {
            SystemCursor.set(SystemCursor.CursorType.SCROLL);
        }

        int trackAlpha = hot ? 0xB8 : (recently ? 0x9C : 0x84);
        int thumbAlpha = hot ? 0xFF : (recently ? 0xEC : 0xD4);
        drawScrollbarGlass(trackX, trackY, trackW, trackH, thumbY, thumbH, hot, trackAlpha, thumbAlpha);
    }

    private static boolean isScrollbarHovered(double mx, double my) {
        float hitPad = 5f;
        return sbEnabled &&
                mx >= sbTrackX - hitPad &&
                mx <= sbTrackX + sbTrackW + hitPad &&
                my >= sbTrackY - hitPad &&
                my <= sbTrackY + sbTrackH + hitPad;
    }

    private static void scrollToPosition(double my) {
        if (!sbEnabled || sbVisibleLines <= 0 || sbTotalLines <= sbVisibleLines) return;
        float trackSpan = sbTrackH - sbThumbH;
        if (trackSpan <= 0f) return;
        float rel = (float) (my - sbTrackY - sbDragOffset);
        float t = Mth.clamp(rel / trackSpan, 0f, 1f);
        int scrollable = Math.max(0, sbTotalLines - sbVisibleLines);
        int targetStart = Math.round(t * scrollable);
        scrollOffsetLines = Mth.clamp(sbTotalLines - sbVisibleLines - targetStart, 0, scrollable);
        lastScrollInteractionMs = System.currentTimeMillis();
    }

    private static void scrollSuggestToPosition(double my) {
        if (!suggestHasScrollbar || suggestVisible <= 0 || suggestTotal <= suggestVisible) return;
        float trackSpan = suggestTrackH - suggestThumbH;
        if (trackSpan <= 0f) return;
        float rel = (float) (my - suggestTrackY - suggestThumbH * 0.5f);
        float t = Mth.clamp(rel / trackSpan, 0f, 1f);
        int scrollable = Math.max(0, suggestTotal - suggestVisible);
        int targetStart = Math.round(t * scrollable);
        suggestCurrentStart = Mth.clamp(targetStart, 0, scrollable);
        applySuggestionWindowState(suggestCurrentStart, suggestCurrentSelection);
    }

    private static void applySuggestionWindowState(int start, int selection) {
        if (suggestWindow == null) return;
        SuggestionWindowAccessor acc = (SuggestionWindowAccessor) suggestWindow;
        acc.setCombatant$inWindowIndex(Math.max(0, start));
        if (selection >= 0) {
            acc.setCombatant$selection(selection);
        }
    }

    private static void applySuggestion(int idx) {
        Minecraft mc = Minecraft.getInstance();
        if (!(ClientScreen.current() instanceof ChatScreen chatScreen)) return;
        EditBox field = ((ChatScreenAccessor) chatScreen).getChatField();
        if (field == null) return;

        // Update vanilla selection state for completeness
        if (suggestWindow != null) {
            SuggestionWindowAccessor acc = (SuggestionWindowAccessor) suggestWindow;
            acc.setCombatant$selection(idx);
        }

        if (idx < 0 || idx >= suggestEntries.size()) return;
        com.mojang.brigadier.suggestion.Suggestion suggestion = suggestEntries.get(idx);
        String current = field.getValue();
        try {
            String applied = suggestion.apply(current);
            field.setValue(applied);
            setCursorSafe(field, applied.length());
            suggestCurrentSelection = idx;
            applySuggestionWindowState(suggestCurrentStart, suggestCurrentSelection);
        } catch (Exception e) {
            //DebugLog.error("[BetterChat][Suggest] apply failed", e);
        }
    }

    private static CommandSuggestorBridge.SuggestionSnapshot buildCustomSnapshot(String text, int cursor) {
        List<com.mojang.brigadier.suggestion.Suggestion> suggestions = CommandManager.suggest(text, cursor);
        if (suggestions == null || suggestions.isEmpty()) return null;
        List<String> texts = new ArrayList<>(suggestions.size());
        for (com.mojang.brigadier.suggestion.Suggestion s : suggestions) {
            texts.add(s.getText());
        }
        int selection = 0;
        int start = 0;
        int visible = Math.min(5, Math.max(1, suggestions.size()));
        return new CommandSuggestorBridge.SuggestionSnapshot(0, 0, 0, 0, texts, suggestions, selection, start, visible);
    }

    @SuppressWarnings("unused")
    public static void setSuggestionContext(ChatScreen screen, GuiGraphicsExtractor context, int mouseX, int mouseY) {
        storedScreen = screen;
    }

    private static char commandPrefix(String text) {
        if (text == null) return 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) continue;
            if (c == '/' || c == '@') return c;
            return 0;
        }
        return 0;
    }

    private static boolean refreshSuggestionsIfNeeded(String text, int cursor, int selStart, int selEnd) {
        if (text.equals(lastSuggestInput)
                && cursor == lastSuggestCursor
                && selStart == lastSuggestSelStart
                && selEnd == lastSuggestSelEnd) {
            return false;
        }
        lastSuggestInput = text;
        lastSuggestCursor = cursor;
        lastSuggestSelStart = selStart;
        lastSuggestSelEnd = selEnd;
        return true;
    }

    private static void resetSuggestionTracking() {
        lastSuggestInput = "";
        lastSuggestCursor = -1;
        lastSuggestSelStart = -1;
        lastSuggestSelEnd = -1;
        lastSnapshot = null;
        lastSuggestionHash = 0;
        suggestWindow = null;
        suggestEntries = Collections.emptyList();
        suggestRowsX = suggestRowsY = suggestRowsW = suggestRowsH = 0f;
        suggestCurrentStart = -1;
        suggestCurrentSelection = -1;
    }

    private static void renderSuggestions() {
        suggestActive = false;
        suggestHasScrollbar = false;
        if (storedScreen == null) return;
        EditBox field = ((ChatScreenAccessor) storedScreen).getChatField();
        String text = field != null ? field.getValue() : null;
        if (field == null || text == null || text.isEmpty()) {
            storedScreen = null;
            resetSuggestionTracking();
            return;
        }

        int cursor = field.getCursorPosition();
        int selStart = selectionStart(field, cursor);
        int selEnd = selectionEnd(field, cursor);

        char prefix = commandPrefix(text);
        if (prefix == 0) {
            storedScreen = null;
            resetSuggestionTracking();
            return;
        }

        boolean inputChanged = refreshSuggestionsIfNeeded(text, cursor, selStart, selEnd);
        boolean windowChanged = false;
        if (prefix == '/') {
            windowChanged = CommandSuggestorBridge.peekWindow(storedScreen) != CommandSuggestorBridge.lastWindow();
        }
        if (inputChanged || windowChanged || lastSnapshot == null) {
            if (prefix == '@') {
                lastSnapshot = buildCustomSnapshot(text, cursor);
            } else {
                lastSnapshot = CommandSuggestorBridge.snapshot(storedScreen);
            }
        }
        CommandSuggestorBridge.SuggestionSnapshot snap = lastSnapshot;
        if (snap == null) {
            storedScreen = null;
            resetSuggestionTracking();
            return;
        }
        if (prefix == '@') {
            suggestWindow = null;
        }

        // Debug logging once per snapshot content to confirm suggestions arrive
        int hash = snap.texts().hashCode() ^ snap.selection() ^ snap.startIndex();
        boolean snapshotChanged = hash != lastSuggestionHash;
        if (snapshotChanged) {
            lastSuggestionHash = hash;
            /*DebugLog.info(
                    "[BetterChat][Suggest] snapshot texts=%d sel=%d start=%d vis=%d",
                    snap.texts().size(),
                    snap.selection(),
                    snap.startIndex(),
                    snap.visibleCount()
            );*/
        }
        suggestEntries = snap.suggestions();

        int total = snap.texts().size();
        int visible = Math.min(5, Math.max(1, total - snap.startIndex()));
        int scrollable = Math.max(0, total - visible);

        if (snapshotChanged) {
            suggestCurrentStart = Mth.clamp(snap.startIndex(), 0, scrollable);
            suggestCurrentSelection = Mth.clamp(snap.selection(), 0, Math.max(0, total - 1));
        }
        if (suggestCurrentStart < 0) {
            suggestCurrentStart = Mth.clamp(snap.startIndex(), 0, scrollable);
        } else {
            suggestCurrentStart = Mth.clamp(suggestCurrentStart, 0, scrollable);
        }
        if (suggestCurrentSelection < 0) {
            suggestCurrentSelection = Mth.clamp(snap.selection(), 0, Math.max(0, total - 1));
        } else {
            suggestCurrentSelection = Mth.clamp(suggestCurrentSelection, 0, Math.max(0, total - 1));
        }
        applySuggestionWindowState(suggestCurrentStart, suggestCurrentSelection);

        float baseFont = Math.max(10f, lastFontSizeForUi * 0.95f);
        float itemH = baseFont + 6f;

        float screenW = Minecraft.getInstance().getWindow().getWidth();
        float h = itemH * visible + 6f;
        float x;
        float w;
        float y;

        if (hasInputBox && inputBoxW > 0f) {
            x = inputBoxX;
            float needed = textWidthRange(snap.texts(), snap.startIndex(), visible, baseFont) + 18f;
            w = Math.max(140f, Math.min(inputBoxW, Math.max(needed, 0f)));
            y = inputBoxY - h - 4f;
        } else {
            double scale = Minecraft.getInstance().getWindow().getGuiScale();
            x = Math.max(4f, (float) (snap.x() * scale));
            float snapW = snap.w() > 0 ? (float) (snap.w() * scale) : 200f;
            float needed = textWidthRange(snap.texts(), snap.startIndex(), visible, baseFont) + 18f;
            w = Math.max(140f, Math.max(needed, snapW));
            y = (float) (snap.y() * scale) - h - 2f;
        }
        if (y < 4f) y = 4f;
        if (x + w > screenW - 4f) x = screenW - w - 4f;

        // avoid zero-size windows
        if (w <= 2f || h <= 2f || snap.texts().isEmpty()) {
            storedScreen = null;
            return;
        }

        boolean hasScrollbar = total > visible;
        float scrollbarReserve = hasScrollbar ? 13f : 0f;
        float maxTextWidth = textWidthRange(snap.texts(), snap.startIndex(), visible, baseFont);
        w = Math.max(w, maxTextWidth + 18f + scrollbarReserve);

        drawGlassPill(x, y, w, h, 7f, 0.82f, true);

        float trackW = hasScrollbar ? 4.8f : 0f;
        float trackX = x + w - trackW - 5f;
        float trackY = y + 3.5f;
        float trackH = h - 7f;
        float thumbH = hasScrollbar ? Math.max(12f, trackH * ((float) visible / (float) total)) : 0f;
        float thumbY = hasScrollbar ? trackY + (trackH - thumbH) * ((float) suggestCurrentStart / (float) (Math.max(1, total - visible))) : trackY;
        float rowX = x + 3f;
        float rowY = y + 3f;
        float rowW = Math.max(0f, (hasScrollbar ? trackX - 3f : x + w - 3f) - rowX);
        float rowH = visible * itemH;

        for (int i = 0; i < visible; i++) {
            int idx = suggestCurrentStart + i;
            if (idx < 0 || idx >= snap.texts().size()) break;
            float top = rowY + i * itemH;
            boolean hoverRow = lastMouseValid &&
                    lastMouseFx >= rowX && lastMouseFx <= rowX + rowW &&
                    lastMouseFy >= top && lastMouseFy <= top + itemH;
            if (hoverRow) {
                drawLiquidHoverPill(rowX, top + 0.6f, rowW, itemH - 1.2f, 4.5f, 0.46f);
            }
            float textH = textHeight(getIosevkaRegular(), baseFont);
            float textY = top + (itemH - textH) * 0.5f;
            drawText(getIosevkaRegular(), snap.texts().get(idx), rowX + 4f, textY, baseFont, theme().textPrimary(), true);
        }

        if (hasScrollbar) {
            boolean hotScroll = suggestDraggingScrollbar || (lastMouseValid
                    && lastMouseFx >= trackX - 4f && lastMouseFx <= trackX + trackW + 4f
                    && lastMouseFy >= trackY - 4f && lastMouseFy <= trackY + trackH + 4f);
            if (hotScroll) {
                SystemCursor.set(SystemCursor.CursorType.SCROLL);
            }
            drawScrollbarGlass(trackX, trackY, trackW, trackH, thumbY, thumbH, hotScroll, hotScroll ? 0xB0 : 0x88, hotScroll ? 0xFF : 0xD8);
        }

        suggestActive = true;
        suggestX = x;
        suggestY = y;
        suggestW = w;
        suggestH = h;
        suggestItemH = itemH;
        suggestRowsX = rowX;
        suggestRowsY = rowY;
        suggestRowsW = rowW;
        suggestRowsH = rowH;
        suggestStart = suggestCurrentStart;
        suggestVisible = visible;
        suggestTotal = total;
        suggestWindow = CommandSuggestorBridge.lastWindow();
        suggestHasScrollbar = hasScrollbar;
        suggestTrackX = trackX;
        suggestTrackY = trackY;
        suggestTrackW = trackW;
        suggestTrackH = trackH;
        suggestThumbH = thumbH;
        if (!hasScrollbar) suggestDraggingScrollbar = false;
        //DebugLog.info("[BetterChat][Suggest] box x=%.1f y=%.1f w=%.1f h=%.1f start=%d vis=%d total=%d", x, y, w, h, suggestStart, suggestVisible, suggestTotal);
        storedScreen = null;
    }

    private static float textWidthRange(List<String> list, int start, int count, float fontSize) {
        float max = 0f;
        for (int i = 0; i < count; i++) {
            int idx = start + i;
            if (idx < 0 || idx >= list.size()) break;
            max = Math.max(max, textWidth(getIosevkaRegular(), list.get(idx), fontSize));
        }
        return max;
    }

    private static String formatTimestamp(long ms, boolean withSeconds) {
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern(withSeconds ? "HH:mm:ss" : "HH:mm");
        return dt.format(fmt);
    }

    private static String formatDate(long ms) {
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
        return dt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"));
    }

    private static HoverTip buildTimestampTooltip(long ms, boolean showDate, boolean showUnix) {
        List<ChatHoverUtil.ColoredLine> lines = new ArrayList<>();
        LocalDateTime dt = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault());
        if (showDate) {
            lines.add(new ChatHoverUtil.ColoredLine(I18n.get(
                    "better_chat.hover.date",
                    dt.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
            ), 0));
        }
        if (showUnix) {
            lines.add(new ChatHoverUtil.ColoredLine("Unix: " + (ms / 1000L), 0xFF888888));
        }
        if (lines.isEmpty()) return null;
        return new HoverTip(lines, null, false, net.minecraft.world.item.ItemStack.EMPTY);
    }

    private record MousePos(double rawX, double rawY, double fx, double fy) {
    }

    private record InputLine(int start, int end, float width) {
    }

    private record PasswordMaskRect(float x, float y, float w, float h) {
    }

    private record Segment(String text, Style style, ItemStack item, int logicalLength) {
        static Segment text(String text, Style style) {
            String safe = text == null ? "" : text;
            return new Segment(safe, style == null ? Style.EMPTY : style, null, safe.length());
        }

        static Segment richItem(String accessibleText, ItemStack item) {
            String safe = accessibleText == null ? "" : accessibleText;
            return new Segment(safe, Style.EMPTY, item == null ? ItemStack.EMPTY : item.copy(), safe.length());
        }

        static Segment decorativeItem(ItemStack item, Style style) {
            return new Segment("", style == null ? Style.EMPTY : style,
                    item == null ? ItemStack.EMPTY : item.copy(), 0);
        }
    }

    private record VisualLine(ChatLine message, int messageIndex, int messageGroup, int startChar, int endChar, List<Glyph> glyphs) {
    }

    private record Glyph(
            float x0,
            float x1,
            int charIndex,
            int charEndExclusive,
            int color,
            HoverEvent hover,
            String font,
            String text,
            Style style,
            ItemStack item
    ) {
    }

    private record GlyphBox(
            float x0,
            float y0,
            float x1,
            float y1,
            int charIndex,
            int charEndExclusive,
            int color,
            String font,
            HoverEvent hover,
            String text,
            Style style,
            ItemStack item
    ) {
    }

    private record CachedLine(int startChar, int endChar, List<Glyph> glyphs) {
    }

    private record CachedMessageLayout(float fontSize, float maxWidth, List<CachedLine> lines) {
    }

    private record FrameLine(ChatLine message, int messageIndex, int messageGroup, float y0, float y1, List<GlyphBox> glyphs) {
    }

    private record MessageBubble(ChatLine message, int messageGroup, float x, float y, float w, float h, FrameLine firstLine, FrameLine lastLine) {
    }

    private record MessageClipBounds(float x, float y, float w, float h) {
    }

    private record FrameLayout(float x, float y, float w, float h, List<FrameLine> lines, List<MessageBubble> bubbles) {
        static FrameLayout empty() {
            return new FrameLayout(0, 0, 0, 0, Collections.emptyList(), Collections.emptyList());
        }

        static FrameLayout build(List<VisualLine> lines, float x, float y, float w, float h, float fontSize, float lineHeight, float yOffset, float timestampReserve) {
            List<FrameLine> list = new ArrayList<>(lines.size());
            float cursorY = y + BetterChatRenderer.MESSAGE_PAD_Y + yOffset;
            int lastMessageGroup = Integer.MIN_VALUE;
            for (int i = 0; i < lines.size(); i++) {
                VisualLine vl = lines.get(i);
                if (i > 0 && vl.messageGroup() != lastMessageGroup) {
                    cursorY += BetterChatRenderer.MESSAGE_GAP;
                }
                lastMessageGroup = vl.messageGroup();
                float y0 = cursorY;
                float y1 = y0 + fontSize;
                List<GlyphBox> boxes = new ArrayList<>(vl.glyphs().size());
                for (Glyph g : vl.glyphs()) {
                    boxes.add(new GlyphBox(
                            x + BetterChatRenderer.PADDING + BetterChatRenderer.MESSAGE_PAD_X + g.x0(),
                            y0,
                            x + BetterChatRenderer.PADDING + BetterChatRenderer.MESSAGE_PAD_X + g.x1(),
                            y1,
                            g.charIndex(),
                            g.charEndExclusive(),
                            g.color(),
                            g.font(),
                            g.hover(),
                            g.text(),
                            g.style(),
                            g.item()
                    ));
                }
                list.add(new FrameLine(vl.message(), vl.messageIndex(), vl.messageGroup(), y0, y1, boxes));
                cursorY += lineHeight;
            }
            return new FrameLayout(x, y, w, h, list, buildBubbles(list, x, w, timestampReserve));
        }

        private static List<MessageBubble> buildBubbles(List<FrameLine> lines, float x, float w, float timestampReserve) {
            if (lines.isEmpty()) return Collections.emptyList();
            List<MessageBubble> bubbles = new ArrayList<>();
            int start = 0;
            while (start < lines.size()) {
                FrameLine first = lines.get(start);
                int end = start;
                float maxRight = x + BetterChatRenderer.PADDING + BetterChatRenderer.MESSAGE_PAD_X;
                while (end + 1 < lines.size() && lines.get(end + 1).messageGroup() == first.messageGroup()) {
                    end++;
                }
                for (int i = start; i <= end; i++) {
                    FrameLine line = lines.get(i);
                    if (!line.glyphs().isEmpty()) {
                        maxRight = Math.max(maxRight, line.glyphs().getLast().x1());
                    }
                }
                FrameLine last = lines.get(end);
                float bubbleX = x + BetterChatRenderer.PADDING;
                float bubbleY = first.y0() - BetterChatRenderer.MESSAGE_PAD_Y;
                float maxBubbleW = Math.max(32f, w - BetterChatRenderer.PADDING * 2f);
                float desiredW = maxRight - bubbleX + BetterChatRenderer.MESSAGE_PAD_X + Math.max(0f, timestampReserve);
                float bubbleW = Mth.clamp(desiredW, 76f, maxBubbleW);
                float bubbleH = last.y1() - first.y0() + BetterChatRenderer.MESSAGE_PAD_Y * 2f;
                bubbles.add(new MessageBubble(last.message(), first.messageGroup(), bubbleX, bubbleY, bubbleW, bubbleH, first, last));
                start = end + 1;
            }
            return bubbles;
        }

        MessageClipBounds messageClipBounds() {
            if (lines.isEmpty() || h <= 0f) return null;
            /*
             * Match the mask horizontally to the message bubbles, not to the outer draggable HUD
             * bounds. Bubbles begin at x + PADDING; using the outer x left almost the whole top-left
             * radius in empty padding, so the remaining visible cut looked rectangular even though
             * the stencil polygon itself was rounded.
             *
             * The Y/H stay viewport-fixed: overscan/group expansion may move bubbles through this
             * shape, but must never expand the clipping shape with them.
             */
            float clipX = x + BetterChatRenderer.PADDING;
            float clipW = Math.max(1f, w - BetterChatRenderer.PADDING * 2f);
            return new MessageClipBounds(clipX, y, clipW, h);
        }

        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }

        PickResult pick(double mx, double my) {
            if (!contains(mx, my)) return null;
            for (FrameLine line : lines) {
                if (my < line.y0() || my > line.y1() + LINE_SPACING) continue;
                if (line.glyphs().isEmpty()) continue;
                float minX = line.glyphs().getFirst().x0();
                float maxX = line.glyphs().getLast().x1();
                float margin = 2f;
                if (mx < minX - margin || mx > maxX + margin) continue;
                GlyphBox closest = null;
                for (GlyphBox g : line.glyphs()) {
                    if (mx >= g.x0() && mx <= g.x1()) {
                        return new PickResult(line, g);
                    }
                    if (closest == null || Math.abs(mx - g.x1()) < Math.abs(mx - closest.x1())) {
                        closest = g;
                    }
                }
                if (closest != null) {
                    return new PickResult(line, closest);
                }
            }
            return null;
        }
    }

    private record PickResult(FrameLine line, GlyphBox glyph) {
    }

    private record ContextMenu(boolean open, float x, float y, List<MenuEntry> entries) {
        private static final float MENU_PAD_X = 8f;
        private static final float MENU_PAD_Y = 7f;
        private static final float MENU_RADIUS = 9f;

        static ContextMenu closed() {
            return new ContextMenu(false, 0, 0, Collections.emptyList());
        }

        static ContextMenu open(float x, float y, List<MenuEntry> entries) {
            return new ContextMenu(true, x, y, entries);
        }

        private static float itemHeight(float fontSize) {
            return Math.max(20f, fontSize + 7f);
        }

        boolean contains(double mx, double my) {
            if (!open) return false;
            float fontSize = lastFontSizeForUi * 0.92f;
            return mx >= x && mx <= x + width(fontSize) && my >= y && my <= y + height(fontSize);
        }

        MenuEntry pick(double my) {
            if (!open) return null;
            float fontSize = lastFontSizeForUi * 0.92f;
            float itemH = itemHeight(fontSize);
            float cy = y + MENU_PAD_Y;
            for (int i = 0; i < entries.size(); i++) {
                float top = cy + i * itemH;
                float bottom = top + itemH;
                if (my >= top && my <= bottom) {
                    return entries.get(i);
                }
            }
            return null;
        }

        void render(float mouseX, float mouseY, float fontSize) {
            if (!open || entries.isEmpty()) return;
            float fs = Math.max(12f, fontSize * 0.92f);
            float itemH = itemHeight(fs);
            float w = width(fs);
            float h = height(fs);

            drawGlassPill(x, y, w, h, MENU_RADIUS, 0.94f, true);

            float accentX = x + 5f;
            drawRoundedRect(accentX, y + MENU_PAD_Y + 2f, 2f, h - MENU_PAD_Y * 2f - 4f, 1f, withAlpha(theme().accent(), 0x72));

            float cy = y + MENU_PAD_Y;
            for (MenuEntry entry : entries) {
                boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= cy && mouseY <= cy + itemH;
                if (hover) {
                    drawRoundedRect(x + 8f, cy + 1f, w - 16f, itemH - 2f, itemH * 0.5f, withAlpha(theme().surfaceHover(), 0xAA));
                    drawRoundedRectStrokeGradient(
                            x + 8f, cy + 1f, w - 16f, itemH - 2f, itemH * 0.5f,
                            0.55f,
                            withAlpha(theme().accent(), 0x66),
                            withAlpha(theme().accentSoft(), 0x38)
                    );
                }

                float dot = 3.2f;
                int dotColor = hover ? withAlpha(theme().accent(), 0xE0) : withAlpha(theme().textMuted(), 0x70);
                drawRoundedRect(x + 14f, cy + (itemH - dot) * 0.5f, dot, dot, dot * 0.5f, dotColor);

                float textY = cy + (itemH - textHeight(getIosevkaRegular(), fs)) * 0.5f;
                int textColor = hover ? theme().textPrimary() : withAlpha(theme().textPrimary(), 0xE4);
                drawText(getIosevkaRegular(), entry.label(), x + 24f, textY, fs, textColor, true);
                cy += itemH;
            }
        }

        private float width(float fontSize) {
            float maxText = 0f;
            for (MenuEntry e : entries) {
                maxText = Math.max(maxText, textWidth(getIosevkaRegular(), e.label(), fontSize));
            }
            return Math.max(168f, maxText + MENU_PAD_X * 2f + 34f);
        }

        private float height(float fontSize) {
            return MENU_PAD_Y * 2f + entries.size() * itemHeight(fontSize);
        }

        record MenuEntry(String label, Runnable action) {
        }
    }

    public record Layout(float x, float y) {
    }

    private record LayoutResult(List<VisualLine> lines, int messagesSeen) {
    }
}
