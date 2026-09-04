/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.renderer.ui;

import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.ScissorState;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.CommonColors;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import combatant.client.render.engine.RenderState;
import combatant.client.mixins.accessors.GameRendererAccessor;
import combatant.client.render.engine.color.RenderColor;
import combatant.client.render.engine.pipeline.CombatantRenderPipelines;
import combatant.client.render.engine.profiler.RenderCostProfiler;
import combatant.client.render.engine.renderer.MeshRenderer;
import combatant.client.render.engine.text.TextRenderer;
import combatant.client.render.engine.uniform.MeshBuilder;
import combatant.client.render.engine.uniform.impl.UIBatchUniforms;
import combatant.client.render.engine.uniform.impl.UiClipUniforms;
import combatant.client.render.engine.renderer.ui.clip.UiClipSnapshot;

import java.util.ArrayList;
import java.util.List;

import static combatant.client.render.engine.renderer.Renderer2D.ITEM_OVERLAY_COOLDOWN;
import static combatant.client.render.engine.renderer.Renderer2D.ITEM_OVERLAY_COUNT;
import static combatant.client.render.engine.renderer.Renderer2D.ITEM_OVERLAY_DURABILITY;
import static combatant.client.render.engine.renderer.Renderer2D.ITEM_OVERLAY_DURABILITY_TEXT;

public final class ItemBatchRenderer {
    private static final int ITEM_DURABILITY_TRACK_COLOR = 0x72000000;
    private static final int ITEM_DURABILITY_TRACK_HIGHLIGHT = 0x26FFFFFF;
    private static final int ITEM_OVERLAY_PREALLOCATED_ITEMS = Math.max(32,
            Integer.getInteger("combatant.render.itemOverlay.preallocatedItems", 256));

    // GuiItemAtlas feature rendering must not share GameRenderer's staged vertex buffer.
    // During world/Iris rendering that buffer can already be uploaded/in-use; if a second
    // FeatureRenderDispatcher begins on it, prepareFrame() may fail after PreparedFrame.begin(),
    // leaving that dispatcher permanently poisoned ("PreparedFrame already in use").
    //
    // Keep world-billboard atlas work and deferred HUD atlas work isolated from both the main
    // renderer and from each other.
    private static RenderBuffers uiItemRenderBuffers;
    private static FeatureRenderDispatcher uiItemFeatureDispatcher;
    private static RenderBuffers worldItemRenderBuffers;
    private static FeatureRenderDispatcher worldItemFeatureDispatcher;

    // UI and world billboards must never share the same mutable GuiItemAtlas.
    // World render commands are deferred until Renderer3D flushes them; repacking
    // the shared atlas before that point invalidates already-recorded UVs.
    private static GuiItemAtlas itemAtlas;
    private static int itemAtlasSlotTextureSize;
    private static int itemAtlasTextureSize;
    private static boolean uiItemFrameOpen;
    private static final Object2ObjectOpenHashMap<ItemResolveKey, TrackingItemStackRenderState> uiResolvedStates =
            new Object2ObjectOpenHashMap<>(128);
    private static final ObjectArrayList<ItemDrawCommand> uiPreparedCommands = new ObjectArrayList<>(256);
    private static final ObjectArrayList<ItemDrawCommand> uiNewPreparedCommands = new ObjectArrayList<>(64);
    private static final ObjectOpenHashSet<Object> uiModelIdentities = new ObjectOpenHashSet<>();
    private static final List<WorldItemAtlasPage> worldItemAtlases = new ArrayList<>();
    private static int worldItemAtlasCursor;
    private static boolean worldItemFrameOpen;
    private static MeshBuilder itemBlitMesh;
    private static MeshBuilder itemDurabilityGlowMesh;
    private static MeshBuilder itemDurabilityRoundedMesh;
    private static MeshBuilder itemCooldownMesh;

    /**
     * Resolve one billboard submission against a dedicated atlas page.
     *
     * <p>Each caller in the current world phase gets its own persistent page. This is
     * intentionally isolated from the normal UI item atlas: NameTags/DropESP can submit
     * deferred world meshes without a later module or CustomHotbar repacking their slots
     * before the GPU draw executes.</p>
     */
    public static List<WorldItemSprite[]> resolveWorldItemSprites(List<WorldItemRow> rows) {
        if (rows == null || rows.isEmpty()) return List.of();

        Minecraft mc = Minecraft.getInstance();
        List<WorldItemSprite[]> sprites = new ArrayList<>(rows.size());
        for (WorldItemRow row : rows) {
            sprites.add(new WorldItemSprite[row == null || row.stacks() == null ? 0 : row.stacks().length]);
        }
        if (mc == null || mc.gameRenderer == null || mc.level == null) return sprites;

        List<TrackingItemStackRenderState[]> states = new ArrayList<>(rows.size());
        ObjectOpenHashSet<Object> identities = new ObjectOpenHashSet<>();
        for (WorldItemRow row : rows) {
            ItemStack[] stacks = row == null || row.stacks() == null ? new ItemStack[0] : row.stacks();
            TrackingItemStackRenderState[] rowStates = new TrackingItemStackRenderState[stacks.length];
            states.add(rowStates);

            for (int i = 0; i < stacks.length; i++) {
                ItemStack stack = stacks[i];
                if (stack == null || stack.isEmpty()) continue;

                TrackingItemStackRenderState state = new TrackingItemStackRenderState();
                Player player = row != null ? row.player() : null;
                int seedBase = row != null ? row.seedBase() : 0;
                mc.getItemModelResolver().updateForTopItem(
                        state,
                        stack,
                        ItemDisplayContext.GUI,
                        player != null ? player.level() : mc.level,
                        player,
                        seedBase + i
                );

                if (!state.isEmpty()) {
                    rowStates[i] = state;
                    identities.add(state.getModelIdentity());
                }
            }
        }
        if (identities.isEmpty()) return sprites;

        if (!worldItemFrameOpen) {
            worldItemAtlasCursor = 0;
            worldItemFrameOpen = true;
        }

        WorldItemAtlasPage page = ensureWorldItemAtlas(
                mc,
                getWorldItemFeatureDispatcher(mc),
                identities,
                worldItemAtlasCursor++
        );
        GuiItemAtlas atlas = page.atlas;
        GpuSampler sampler = RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST);

        // GuiItemAtlas mutates global RenderSystem state while actually drawing slots. Keep
        // the guard around getOrUpdate only; model resolution and atlas allocation do not need
        // to touch the world render state.
        GuiAtlasRenderState atlasState = GuiAtlasRenderState.capture();
        RuntimeException atlasFailure = null;
        try {
            atlasState.enterGuiPass();
            for (int rowIndex = 0; rowIndex < states.size(); rowIndex++) {
                TrackingItemStackRenderState[] rowStates = states.get(rowIndex);
                WorldItemSprite[] rowSprites = sprites.get(rowIndex);

                for (int i = 0; i < rowStates.length; i++) {
                    TrackingItemStackRenderState state = rowStates[i];
                    if (state == null) continue;

                    GuiItemAtlas.SlotView slot = atlas.getOrUpdate(state);
                    if (slot != null && slot.textureView() != null) {
                        rowSprites[i] = new WorldItemSprite(
                                slot.textureView(),
                                sampler,
                                slot.u0(),
                                slot.v0(),
                                slot.u1(),
                                slot.v1()
                        );
                    }
                }
            }
        } catch (RuntimeException failure) {
            // FeatureRenderDispatcher.prepareFrameWithContext() calls PreparedFrame.begin()
            // before feature preparation and does not roll it back if preparation itself throws.
            // Do not keep a possibly-poisoned dispatcher alive after the intentionally-soft
            // world-atlas failure path.
            atlasFailure = failure;
        } finally {
            atlasState.restore();
        }
        if (atlasFailure != null) {
            for (WorldItemSprite[] row : sprites) {
                if (row == null) continue;
                for (int i = 0; i < row.length; i++) {
                    row[i] = null;
                }
            }
            resetWorldItemRenderer();
        }
        return sprites;
    }

    public static void finishWorldItemFrame() {
        if (!worldItemFrameOpen) return;

        int usedPages = Math.min(worldItemAtlasCursor, worldItemAtlases.size());
        for (int i = 0; i < usedPages; i++) {
            GuiItemAtlas atlas = worldItemAtlases.get(i).atlas;
            if (atlas != null) {
                atlas.endFrame();
            }
        }

        worldItemAtlasCursor = 0;
        worldItemFrameOpen = false;
        if (worldItemRenderBuffers != null) {
            worldItemRenderBuffers.endFrame();
        }
    }

    /**
     * Exact guard for GuiItemAtlas, which mutates global RenderSystem state by design.
     * Vanilla restores some of that state at a higher GuiRenderer level; Combatant calls the
     * atlas directly, so every touched global has to be restored here.
     */
    private static final class GuiAtlasRenderState {
        private final GpuTextureView outputColor;
        private final GpuTextureView outputDepth;
        private final GpuBufferSlice projection;
        private final ProjectionType projectionType;
        private final Matrix4f modelView;
        private final GpuBufferSlice shaderLights;
        private final GpuBufferSlice shaderFog;
        private final ScissorState scissor;
        private final boolean rendering3D;

        private GuiAtlasRenderState(GpuTextureView outputColor,
                                    GpuTextureView outputDepth,
                                    GpuBufferSlice projection,
                                    ProjectionType projectionType,
                                    Matrix4f modelView,
                                    GpuBufferSlice shaderLights,
                                    GpuBufferSlice shaderFog,
                                    ScissorState scissor,
                                    boolean rendering3D) {
            this.outputColor = outputColor;
            this.outputDepth = outputDepth;
            this.projection = projection;
            this.projectionType = projectionType;
            this.modelView = modelView;
            this.shaderLights = shaderLights;
            this.shaderFog = shaderFog;
            this.scissor = scissor;
            this.rendering3D = rendering3D;
        }

        static GuiAtlasRenderState capture() {
            return new GuiAtlasRenderState(
                    RenderSystem.outputColorTextureOverride,
                    RenderSystem.outputDepthTextureOverride,
                    RenderSystem.getProjectionMatrixBuffer(),
                    RenderSystem.getProjectionType(),
                    RenderSystem.getModelViewMatrixCopy(),
                    RenderSystem.getShaderLights(),
                    RenderSystem.getShaderFog(),
                    new ScissorState(RenderSystem.getScissorStateForRenderTypeDraws()),
                    RenderState.rendering3D
            );
        }

        void enterGuiPass() {
            RenderState.rendering3D = false;
            RenderSystem.getModelViewStack().identity();

            // GuiItemAtlas is normally prepared by vanilla after GameRenderer switches the
            // global fog UBO to FogMode.NONE. Calling the atlas from Combatant's world phase
            // without doing the same makes item/feature pipelines sample WORLD fog while
            // drawing into the off-screen atlas. That produces the intermittent grey/
            // "fogged" item sprites (especially on feature/glint paths).
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameRenderer instanceof GameRendererAccessor accessor) {
                FogRenderer fogRenderer = accessor.combatant$getFogRenderer();
                if (fogRenderer != null) {
                    RenderSystem.setShaderFog(fogRenderer.getBuffer(FogRenderer.FogMode.NONE));
                }
            }
        }

        void restore() {
            // Restore the actual matrix value, not just stack depth. A nested feature pass is
            // allowed to touch the shared stack while GuiItemAtlas renders.
            RenderSystem.getModelViewStack().set(modelView);
            if (projection != null && projectionType != null) {
                RenderSystem.setProjectionMatrix(projection, projectionType);
            }
            RenderSystem.outputColorTextureOverride = outputColor;
            RenderSystem.outputDepthTextureOverride = outputDepth;
            RenderSystem.setShaderLights(shaderLights);
            RenderSystem.setShaderFog(shaderFog);
            RenderSystem.getScissorStateForRenderTypeDraws().setFrom(scissor);
            RenderState.rendering3D = rendering3D;
        }
    }

    private static final class WorldItemAtlasPage {
        GuiItemAtlas atlas;
        int slotTextureSize;
        int textureSize;
    }

    public record WorldItemRow(@Nullable Player player, ItemStack[] stacks, int seedBase) {
    }

    public record WorldItemSprite(GpuTextureView textureView,
                                  GpuSampler sampler,
                                  float u0,
                                  float v0,
                                  float u1,
                                  float v1) {
    }

    static float clampRoundedRadius(float radius, double w, double h) {
        return (float) Mth.clamp(radius, 0.0f, (float) Math.min(w, h) * 0.5f);
    }

    public static void init() {
        itemBlitMesh = createItemOverlayMesh(CombatantRenderPipelines.UI_TEXTURED_PREMULTIPLIED_ALPHA, 1);
        itemDurabilityGlowMesh = createItemOverlayMesh(CombatantRenderPipelines.UI_ROUNDED_GLOW_BATCH, 1);
        itemDurabilityRoundedMesh = createItemOverlayMesh(CombatantRenderPipelines.UI_ROUNDED_BATCH, 3);
        itemCooldownMesh = createItemOverlayMesh(CombatantRenderPipelines.UI_COLORED, 1);
    }

    private static MeshBuilder createItemOverlayMesh(RenderPipeline pipeline, int quadsPerItem) {
        int quads = Math.max(1, ITEM_OVERLAY_PREALLOCATED_ITEMS * Math.max(1, quadsPerItem));
        return new MeshBuilder(pipeline.getVertexFormatBinding(0), pipeline.getPrimitiveTopology(), quads * 4, quads * 6);
    }

    private static MeshBuilder beginItemOverlayMesh(MeshBuilder mesh, int commandCount, int quadsPerCommand) {
        if (mesh.isBuilding()) {
            mesh.end();
        }

        int quads = Math.max(1, Math.max(ITEM_OVERLAY_PREALLOCATED_ITEMS, commandCount) * Math.max(1, quadsPerCommand));
        mesh.reserve(quads * 4, quads * 6);
        mesh.begin();
        return mesh;
    }

    private static MeshBuilder beginItemBlitMesh(int commandCount) {
        if (itemBlitMesh == null) {
            itemBlitMesh = createItemOverlayMesh(CombatantRenderPipelines.UI_TEXTURED_PREMULTIPLIED_ALPHA, 1);
        }
        return beginItemOverlayMesh(itemBlitMesh, commandCount, 1);
    }

    private static MeshBuilder beginItemDurabilityGlowMesh(int commandCount) {
        if (itemDurabilityGlowMesh == null) {
            itemDurabilityGlowMesh = createItemOverlayMesh(CombatantRenderPipelines.UI_ROUNDED_GLOW_BATCH, 1);
        }
        return beginItemOverlayMesh(itemDurabilityGlowMesh, commandCount, 1);
    }

    private static MeshBuilder beginItemDurabilityRoundedMesh(int commandCount) {
        if (itemDurabilityRoundedMesh == null) {
            itemDurabilityRoundedMesh = createItemOverlayMesh(CombatantRenderPipelines.UI_ROUNDED_BATCH, 3);
        }
        return beginItemOverlayMesh(itemDurabilityRoundedMesh, commandCount, 3);
    }

    private static MeshBuilder beginItemCooldownMesh(int commandCount) {
        if (itemCooldownMesh == null) {
            itemCooldownMesh = createItemOverlayMesh(CombatantRenderPipelines.UI_COLORED, 1);
        }
        return beginItemOverlayMesh(itemCooldownMesh, commandCount, 1);
    }

    /**
     * Prepare all item models and GuiItemAtlas slots before ordered UI replay.
     *
     * <p>This intentionally mirrors vanilla GuiRenderer.prepareItemElements(): the atlas is an
     * offscreen preparation pass, not an ordered HUD draw. Keeping getOrUpdate() here prevents
     * its projection/lighting/render-target transitions from occurring between liquid-glass,
     * stencil/clip and ordinary UI draws. The path is backend-agnostic; no GL/Vulkan state
     * probing or backend bypass is involved.</p>
     */
    static void prepareUiItems(List<ItemBatch> batches) {
        if (batches == null || batches.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null) return;

        boolean foundNewCommand = false;
        uiNewPreparedCommands.clear();
        for (ItemBatch batch : batches) {
            if (batch == null || batch.isEmpty()) continue;

            for (ItemDrawCommand command : batch.commands) {
                if (command == null || command.stack.isEmpty() || !command.drawItem || command.alpha <= 0.001f) {
                    continue;
                }
                if (command.atlasPreparationRegistered) {
                    continue;
                }

                command.preparedSlot = null;
                ItemResolveKey key = new ItemResolveKey(command.player, command.stack, command.seed);
                TrackingItemStackRenderState renderState = uiResolvedStates.get(key);
                if (renderState == null) {
                    try (RenderCostProfiler.Scope ignoredResolve = RenderCostProfiler.itemRender("resolve_model")) {
                        renderState = new TrackingItemStackRenderState();
                        mc.getItemModelResolver().updateForTopItem(
                                renderState,
                                command.stack,
                                ItemDisplayContext.GUI,
                                command.player != null ? command.player.level() : mc.level,
                                command.player,
                                command.seed
                        );
                    }
                    uiResolvedStates.put(key, renderState);
                }

                command.resolvedState = renderState;
                if (renderState.isEmpty()) continue;

                uiModelIdentities.add(renderState.getModelIdentity());
                command.atlasPreparationRegistered = true;
                uiPreparedCommands.add(command);
                uiNewPreparedCommands.add(command);
                foundNewCommand = true;
            }
        }

        if (!foundNewCommand || uiModelIdentities.isEmpty()) {
            uiNewPreparedCommands.clear();
            return;
        }

        try {
            GuiItemAtlas previousAtlas = itemAtlas;
            GuiItemAtlas atlas = ensureItemAtlas(mc, getItemFeatureDispatcher(mc), uiModelIdentities);
            uiItemFrameOpen = true;

            // Existing slots stay stable while DynamicAtlasAllocator reclaims entries because the
            // frame-wide identity set retains every active model. Only an actual atlas recreation
            // invalidates prior SlotViews; in that rare case refresh them all in this safe prepass.
            ObjectArrayList<ItemDrawCommand> commandsToUpdate = atlas != previousAtlas
                    ? uiPreparedCommands
                    : uiNewPreparedCommands;
            for (int i = 0, size = commandsToUpdate.size(); i < size; i++) {
                ItemDrawCommand command = commandsToUpdate.get(i);
                TrackingItemStackRenderState renderState = command.resolvedState;
                if (renderState == null || renderState.isEmpty()) {
                    command.preparedSlot = null;
                    continue;
                }

                try (RenderCostProfiler.Scope ignoredReplay = RenderCostProfiler.itemRender("atlas_model")) {
                    GuiItemAtlas.SlotView slot = atlas.getOrUpdate(renderState);
                    command.preparedSlot = slot != null && slot.textureView() != null ? slot : null;
                }
            }
        } catch (RuntimeException failure) {
            resetUiItemRenderer();
            throw failure;
        } finally {
            uiNewPreparedCommands.clear();
        }
    }

    static int flush(ItemBatch batch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || batch.isEmpty()) {
            return 0;
        }

        try (RenderCostProfiler.Scope ignoredItems = RenderCostProfiler.itemRender("item_batch")) {
            int drawCalls = 0;
            MeshBuilder itemMesh = null;
            GpuTextureView itemAtlasTextureView = null;
            for (ItemDrawCommand command : batch.commands) {
                GuiItemAtlas.SlotView slot = command.preparedSlot;
                if (slot == null || slot.textureView() == null || command.alpha <= 0.001f) continue;

                if (itemMesh == null) {
                    itemMesh = beginItemBlitMesh(batch.commands.size());
                }
                itemAtlasTextureView = slot.textureView();
                appendItemAtlasBlit(itemMesh, command, slot);
            }

            GpuSampler itemSampler = itemMesh != null
                    ? RenderSystem.getSamplerCache().getRepeat(FilterMode.NEAREST)
                    : null;
            if (submitItemBlitMesh(mc, itemMesh, itemAtlasTextureView, itemSampler, batch.clipSnapshot)) {
                drawCalls++;
            }

            MeshBuilder durabilityGlowMesh = null;
            MeshBuilder durabilityRoundedMesh = null;
            MeshBuilder cooldownMesh = null;
            for (ItemDrawCommand command : batch.commands) {
                if (hasItemDurabilityBar(command)) {
                    if (durabilityGlowMesh == null) {
                        durabilityGlowMesh = beginItemDurabilityGlowMesh(batch.commands.size());
                    }
                    if (durabilityRoundedMesh == null) {
                        durabilityRoundedMesh = beginItemDurabilityRoundedMesh(batch.commands.size());
                    }
                    appendItemDurabilityBar(command, durabilityGlowMesh, durabilityRoundedMesh);
                }

                if (hasItemCooldownOverlay(mc, command)) {
                    if (cooldownMesh == null) {
                        cooldownMesh = beginItemCooldownMesh(batch.commands.size());
                    }
                    appendItemCooldownOverlayQuads(mc, command, cooldownMesh);
                }
            }

            if (submitItemOverlayMesh(mc, CombatantRenderPipelines.UI_ROUNDED_GLOW_BATCH, durabilityGlowMesh, true)) {
                drawCalls++;
            }
            if (submitItemOverlayMesh(mc, CombatantRenderPipelines.UI_ROUNDED_BATCH, durabilityRoundedMesh, true)) {
                drawCalls++;
            }
            if (submitItemOverlayMesh(mc, CombatantRenderPipelines.UI_COLORED, cooldownMesh, false)) {
                drawCalls++;
            }

            TextRenderer overlayTextRenderer = null;
            float overlayTextScale = Float.NaN;
            try {
                for (ItemDrawCommand command : batch.commands) {
                    if (hasItemDurabilityText(command)) {
                        String durabilityText = getItemDurabilityText(command);
                        if (durabilityText != null && !durabilityText.isEmpty()) {
                            float textScale = Math.max(0.0001f, command.scaleX * 0.44f);
                            if (overlayTextRenderer == null) {
                                overlayTextRenderer = TextRenderer.get();
                            }
                            if (!overlayTextRenderer.isBuilding() || Float.compare(overlayTextScale, textScale) != 0) {
                                if (overlayTextRenderer.isBuilding()) {
                                    overlayTextRenderer.end();
                                }
                                overlayTextRenderer.begin(textScale, false, false);
                                overlayTextScale = textScale;
                            }

                            float textW = (float) overlayTextRenderer.getWidth(durabilityText, false);
                            float textX = command.x + (16.0f * command.scaleX - textW) * 0.5f;
                            float textY = command.y + 11.0f * command.scaleX;
                            overlayTextRenderer.render(durabilityText, textX, textY,
                                    new RenderColor(multiplyAlpha(getItemDurabilityTextColor(command), command.alpha)), true);
                        }
                    }

                    String overlayText = getItemOverlayText(command);
                    if (overlayText == null || overlayText.isEmpty()) {
                        continue;
                    }

                    float textScale = Math.max(0.0001f, command.scaleX * 0.5f);
                    if (overlayTextRenderer == null) {
                        overlayTextRenderer = TextRenderer.get();
                    }
                    if (!overlayTextRenderer.isBuilding() || Float.compare(overlayTextScale, textScale) != 0) {
                        if (overlayTextRenderer.isBuilding()) {
                            overlayTextRenderer.end();
                        }
                        overlayTextRenderer.begin(textScale, false, false);
                        overlayTextScale = textScale;
                    }

                    float textX = command.x + 19.0f * command.scaleX - 2.0f * command.scaleX
                            - (float) overlayTextRenderer.getWidth(overlayText, false);
                    float textY = command.y + 9.0f * command.scaleX;
                    overlayTextRenderer.render(overlayText, textX, textY,
                            new RenderColor(multiplyAlpha(CommonColors.WHITE, command.alpha)), true);
                }
            } finally {
                if (overlayTextRenderer != null && overlayTextRenderer.isBuilding()) {
                    overlayTextRenderer.end();
                }
            }

            return drawCalls;
        }
    }

    private static WorldItemAtlasPage ensureWorldItemAtlas(Minecraft mc,
                                                           FeatureRenderDispatcher dispatcher,
                                                           ObjectOpenHashSet<Object> modelIdentities,
                                                           int pageIndex) {
        int guiScale = Math.max(1, (int) Math.round(mc.getWindow().getGuiScale()));
        int slotTextureSize = Math.max(16, 16 * guiScale);
        int requiredTextureSize = GuiItemAtlas.computeTextureSizeFor(
                slotTextureSize,
                Math.max(ITEM_OVERLAY_PREALLOCATED_ITEMS, modelIdentities.size())
        );

        while (worldItemAtlases.size() <= pageIndex) {
            worldItemAtlases.add(new WorldItemAtlasPage());
        }

        WorldItemAtlasPage page = worldItemAtlases.get(pageIndex);
        boolean needsRecreate = page.atlas == null
                || page.slotTextureSize != slotTextureSize
                || page.textureSize < requiredTextureSize;

        if (!needsRecreate && !page.atlas.tryPrepareFor(modelIdentities)) {
            needsRecreate = true;
        }

        if (needsRecreate) {
            if (page.atlas != null) {
                page.atlas.close();
            }
            page.atlas = new GuiItemAtlas(dispatcher, requiredTextureSize, slotTextureSize);
            page.slotTextureSize = slotTextureSize;
            page.textureSize = requiredTextureSize;
        }

        return page;
    }

    private static GuiItemAtlas ensureItemAtlas(Minecraft mc,
                                               FeatureRenderDispatcher dispatcher,
                                               ObjectOpenHashSet<Object> modelIdentities) {
        int guiScale = Math.max(1, (int) Math.round(mc.getWindow().getGuiScale()));
        int slotTextureSize = Math.max(16, 16 * guiScale);
        int requiredTextureSize = GuiItemAtlas.computeTextureSizeFor(
                slotTextureSize, Math.max(ITEM_OVERLAY_PREALLOCATED_ITEMS, modelIdentities.size()));

        if (itemAtlas != null
                && itemAtlasSlotTextureSize == slotTextureSize
                && itemAtlasTextureSize >= requiredTextureSize
                && !itemAtlas.tryPrepareFor(modelIdentities)) {
            closeItemAtlas();
        }

        if (itemAtlas == null || itemAtlasSlotTextureSize != slotTextureSize || itemAtlasTextureSize < requiredTextureSize) {
            closeItemAtlas();
            itemAtlas = new GuiItemAtlas(dispatcher, requiredTextureSize, slotTextureSize);
            itemAtlasSlotTextureSize = slotTextureSize;
            itemAtlasTextureSize = requiredTextureSize;
        }

        return itemAtlas;
    }

    private static void closeItemAtlas() {
        if (itemAtlas != null) {
            itemAtlas.close();
            itemAtlas = null;
        }
        itemAtlasSlotTextureSize = 0;
        itemAtlasTextureSize = 0;
    }

    private static FeatureRenderDispatcher getItemFeatureDispatcher(Minecraft mc) {
        if (uiItemFeatureDispatcher == null) {
            uiItemRenderBuffers = new RenderBuffers(1);
            uiItemFeatureDispatcher = new FeatureRenderDispatcher(
                    uiItemRenderBuffers,
                    mc.getModelManager(),
                    mc.getAtlasManager(),
                    mc.font,
                    mc.gameRenderer.gameRenderState()
            );
        }
        return uiItemFeatureDispatcher;
    }

    private static FeatureRenderDispatcher getWorldItemFeatureDispatcher(Minecraft mc) {
        if (worldItemFeatureDispatcher == null) {
            worldItemRenderBuffers = new RenderBuffers(1);
            worldItemFeatureDispatcher = new FeatureRenderDispatcher(
                    worldItemRenderBuffers,
                    mc.getModelManager(),
                    mc.getAtlasManager(),
                    mc.font,
                    mc.gameRenderer.gameRenderState()
            );
        }
        return worldItemFeatureDispatcher;
    }

    private static void resetWorldItemRenderer() {
        for (WorldItemAtlasPage page : worldItemAtlases) {
            if (page.atlas != null) {
                page.atlas.close();
                page.atlas = null;
            }
            page.slotTextureSize = 0;
            page.textureSize = 0;
        }
        worldItemAtlasCursor = 0;
        worldItemFrameOpen = false;

        if (worldItemFeatureDispatcher != null) {
            worldItemFeatureDispatcher.close();
            worldItemFeatureDispatcher = null;
        }
        if (worldItemRenderBuffers != null) {
            worldItemRenderBuffers.close();
            worldItemRenderBuffers = null;
        }
    }

    /** Finish vanilla's item-atlas resources at the real frame boundary, not per ordered batch. */
    public static void finishUiItemFrame() {
        for (int i = 0, size = uiPreparedCommands.size(); i < size; i++) {
            ItemDrawCommand command = uiPreparedCommands.get(i);
            if (command != null) {
                command.preparedSlot = null;
                command.atlasPreparationRegistered = false;
            }
        }
        uiResolvedStates.clear();
        uiPreparedCommands.clear();
        uiNewPreparedCommands.clear();
        uiModelIdentities.clear();
        if (uiItemFrameOpen) {
            if (itemAtlas != null) itemAtlas.endFrame();
            if (uiItemRenderBuffers != null) uiItemRenderBuffers.endFrame();
        }
        uiItemFrameOpen = false;
    }

    private static void resetUiItemRenderer() {
        uiItemFrameOpen = false;
        for (int i = 0, size = uiPreparedCommands.size(); i < size; i++) {
            ItemDrawCommand command = uiPreparedCommands.get(i);
            if (command != null) {
                command.preparedSlot = null;
                command.atlasPreparationRegistered = false;
            }
        }
        uiResolvedStates.clear();
        uiPreparedCommands.clear();
        uiNewPreparedCommands.clear();
        uiModelIdentities.clear();
        closeItemAtlas();
        if (uiItemFeatureDispatcher != null) {
            uiItemFeatureDispatcher.close();
            uiItemFeatureDispatcher = null;
        }
        if (uiItemRenderBuffers != null) {
            uiItemRenderBuffers.close();
            uiItemRenderBuffers = null;
        }
    }

    private static boolean hasItemDurabilityBar(ItemDrawCommand command) {
        int overlayFlags = command.overlayFlags;
        return (overlayFlags & ITEM_OVERLAY_DURABILITY) != 0
                && (overlayFlags & ITEM_OVERLAY_DURABILITY_TEXT) == 0
                && isItemDurabilityOverlayVisible(command);
    }

    private static boolean hasItemDurabilityText(ItemDrawCommand command) {
        return (command.overlayFlags & ITEM_OVERLAY_DURABILITY_TEXT) != 0
                && isItemDurabilityOverlayVisible(command);
    }

    private static boolean isItemDurabilityOverlayVisible(ItemDrawCommand command) {
        ItemStack stack = command.stack;
        if (stack == null || stack.isEmpty() || !stack.isDamageableItem() || !stack.isBarVisible()) {
            return false;
        }
        int threshold = Mth.clamp(command.durabilityThresholdPercent, 0, 100);
        return durabilityPercentInt(stack) <= threshold;
    }

    private static boolean hasItemCooldownOverlay(Minecraft mc, ItemDrawCommand command) {
        if ((command.overlayFlags & ITEM_OVERLAY_COOLDOWN) == 0) {
            return false;
        }
        LocalPlayer player = mc.player;
        float progress = player == null
                ? 0.0f
                : player.getCooldowns().getCooldownPercent(command.stack, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        return progress > 0.0f;
    }

    private static void appendItemDurabilityBar(ItemDrawCommand command, MeshBuilder glowMesh, MeshBuilder roundedMesh) {
        ItemStack stack = command.stack;
        float x = command.x;
        float y = command.y;
        float scale = command.scaleX;
        float barX = x + 2.0f * scale;
        float barY = y + 13.15f * scale;
        float barW = 13.0f * scale;
        float barH = Math.max(1.35f * scale, 1.0f);
        float radius = Math.max(0.85f * scale, barH * 0.5f);
        float pct = durabilityPercent(stack);
        float fillW = Mth.clamp(barW * pct, 0.0f, barW);
        if (fillW <= 0.01f) {
            return;
        }

        int base = ARGB.opaque(stack.getBarColor());
        int bright = adjustRgb(base, 1.18f);
        int deep = adjustRgb(base, 0.74f);
        int glow = multiplyAlpha(withAlpha(base, Mth.clamp(96 + Math.round((1.0f - pct) * 48.0f), 96, 144)), command.alpha);
        int innerGlow = multiplyAlpha(withAlpha(adjustRgb(base, 1.35f), 118), command.alpha);
        int trackTop = multiplyAlpha(ITEM_DURABILITY_TRACK_HIGHLIGHT, command.alpha);
        int trackBottom = multiplyAlpha(ITEM_DURABILITY_TRACK_COLOR, command.alpha);
        int brightAlpha = multiplyAlpha(bright, command.alpha);
        int deepAlpha = multiplyAlpha(deep, command.alpha);

        appendRoundedGlowQuad(glowMesh, barX, barY, fillW, barH, radius, Math.max(3.0f * scale, 2.0f), glow);
        appendRoundedRectQuad(roundedMesh, barX, barY, barW, barH,
                barX, barY, barW, barH,
                radius, trackTop, trackTop, trackBottom, trackBottom);
        appendRoundedRectQuad(roundedMesh, barX, barY, fillW, barH,
                barX, barY, barW, barH,
                radius, brightAlpha, brightAlpha, deepAlpha, deepAlpha);
        appendRoundedRectQuad(roundedMesh, barX, barY, Math.min(fillW, Math.max(1.2f * scale, fillW * 0.28f)), barH,
                barX, barY, barW, barH,
                radius, innerGlow, multiplyAlpha(withAlpha(innerGlow, 70), command.alpha), multiplyAlpha(withAlpha(innerGlow, 34), command.alpha), multiplyAlpha(withAlpha(innerGlow, 58), command.alpha));
    }

    private static void appendItemCooldownOverlayQuads(Minecraft mc, ItemDrawCommand command, MeshBuilder mesh) {
        if ((command.overlayFlags & ITEM_OVERLAY_COOLDOWN) == 0) {
            return;
        }
        LocalPlayer player = mc.player;
        float progress = player == null
                ? 0.0f
                : player.getCooldowns().getCooldownPercent(command.stack, mc.getDeltaTracker().getGameTimeDeltaPartialTick(true));
        if (progress > 0.0f) {
            float scale = command.scaleX;
            float top = command.y + Mth.floor(16.0f * (1.0f - progress)) * scale;
            float height = Mth.ceil(16.0f * progress) * scale;
            appendQuad(mesh, command.x, top, 16.0f * scale, height, multiplyAlpha(Integer.MAX_VALUE, command.alpha));
        }
    }

    private static @Nullable String getItemOverlayText(ItemStack stack, int overlayFlags, @Nullable String stackCountText) {
        if (stack == null || stack.isEmpty()) return null;
        if ((overlayFlags & ITEM_OVERLAY_COUNT) != 0 && (stack.getCount() != 1 || stackCountText != null)) {
            return stackCountText == null ? String.valueOf(stack.getCount()) : stackCountText;
        }
        return null;
    }

    private static @Nullable String getItemOverlayText(ItemDrawCommand command) {
        return getItemOverlayText(command.stack, command.overlayFlags, command.stackCountText);
    }

    private static @Nullable String getItemDurabilityText(ItemDrawCommand command) {
        if (!hasItemDurabilityText(command)) {
            return null;
        }
        return durabilityPercentInt(command.stack) + "%";
    }

    private static int getItemDurabilityTextColor(ItemDrawCommand command) {
        int pct = durabilityPercentInt(command.stack);
        int threshold = Mth.clamp(command.durabilityTextColorThresholdPercent, 0, 100);
        if (pct <= threshold) {
            return ARGB.opaque(command.stack.getBarColor());
        }
        return CommonColors.WHITE;
    }

    private static boolean submitItemBlitMesh(Minecraft mc,
                                              @Nullable MeshBuilder mesh,
                                              @Nullable GpuTextureView atlasTextureView,
                                              @Nullable GpuSampler sampler,
                                              UiClipSnapshot clipSnapshot) {
        if (mesh == null || sampler == null || atlasTextureView == null) {
            return false;
        }
        if (mesh.isBuilding()) {
            mesh.end();
        }
        if (mesh.getIndicesCount() <= 0) {
            return false;
        }

        UiClipSnapshot clip = clipSnapshot != null ? clipSnapshot : UiClipSnapshot.NONE;
        RenderPipeline pipeline = CombatantRenderPipelines.UI_TEXTURED_PREMULTIPLIED_ALPHA;
        if (clip.usesAnalyticPipeline()) {
            pipeline = CombatantRenderPipelines.analyticClipTexturedPipeline(pipeline);
        }
        MeshRenderer renderer = MeshRenderer.begin()
                .attachments(mc.gameRenderer.mainRenderTarget().getColorTextureView(), null)
                .pipeline(pipeline)
                .mesh(mesh)
                .uniform("UIBatch", itemBlitUiBatch(mc))
                .sampler("u_Texture", atlasTextureView, sampler);
        if (clip.usesAnalyticPipeline()) {
            renderer.uniform("UIClip", UiClipUniforms.write(clip));
        }
        renderer.end();
        return true;
    }

    /**
     * Item-atlas blits are pure screen-space UI. Bind their only transform input explicitly so
     * the draw cannot observe the projection/model-view state temporarily used by GuiItemAtlas.
     */
    private static GpuBufferSlice itemBlitUiBatch(Minecraft mc) {
        UIBatchUniforms.update(mc.getWindow().getWidth(), mc.getWindow().getHeight());
        return UIBatchUniforms.get();
    }

    private static void appendItemAtlasBlit(MeshBuilder mesh, ItemDrawCommand command, GuiItemAtlas.SlotView slot) {
        mesh.ensureQuadCapacity();

        double x0;
        double y0;
        double x1;
        double y1;
        double x2;
        double y2;
        double x3;
        double y3;

        if (command.pivoted) {
            x0 = transformItemX(command, 0.0f);
            y0 = transformItemY(command, 0.0f);
            x1 = transformItemX(command, 0.0f);
            y1 = transformItemY(command, 16.0f);
            x2 = transformItemX(command, 16.0f);
            y2 = transformItemY(command, 16.0f);
            x3 = transformItemX(command, 16.0f);
            y3 = transformItemY(command, 0.0f);
        } else {
            x0 = command.x;
            y0 = command.y;
            x1 = command.x;
            y1 = command.y + 16.0f * command.scaleY;
            x2 = command.x + 16.0f * command.scaleX;
            y2 = command.y + 16.0f * command.scaleY;
            x3 = command.x + 16.0f * command.scaleX;
            y3 = command.y;
        }

        int alpha = Mth.clamp(Math.round(command.alpha * 255.0f), 0, 255);
        int i1 = mesh.vec2(x0, y0).raw2(slot.u0(), slot.v0()).color(alpha, alpha, alpha, alpha).next();
        int i2 = mesh.vec2(x1, y1).raw2(slot.u0(), slot.v1()).color(alpha, alpha, alpha, alpha).next();
        int i3 = mesh.vec2(x2, y2).raw2(slot.u1(), slot.v1()).color(alpha, alpha, alpha, alpha).next();
        int i4 = mesh.vec2(x3, y3).raw2(slot.u1(), slot.v0()).color(alpha, alpha, alpha, alpha).next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static double transformItemX(ItemDrawCommand command, float localX) {
        return command.x + command.pivotX + (localX - command.pivotX) * command.scaleX;
    }

    private static double transformItemY(ItemDrawCommand command, float localY) {
        return command.y + command.pivotY + (localY - command.pivotY) * command.scaleY;
    }

    private static boolean submitItemOverlayMesh(Minecraft mc,
                                                 RenderPipeline pipeline,
                                                 @Nullable MeshBuilder mesh,
                                                 boolean uiBatchUniform) {
        if (mesh == null) {
            return false;
        }
        if (mesh.isBuilding()) {
            mesh.end();
        }
        if (mesh.getIndicesCount() <= 0) {
            return false;
        }

        MeshRenderer renderer = MeshRenderer.begin()
                .attachments(mc.gameRenderer.mainRenderTarget().getColorTextureView(), null)
                .pipeline(pipeline)
                .mesh(mesh);
        if (uiBatchUniform) {
            UIBatchUniforms.update(mc.getWindow().getWidth(), mc.getWindow().getHeight());
            renderer.uniform("UIBatch", UIBatchUniforms.get());
        }
        renderer.end();
        return true;
    }

    private static void appendRoundedRectQuad(MeshBuilder mesh,
                                              double x,
                                              double y,
                                              double w,
                                              double h,
                                              double maskX,
                                              double maskY,
                                              double maskW,
                                              double maskH,
                                              float radius,
                                              int cTopLeft,
                                              int cTopRight,
                                              int cBottomRight,
                                              int cBottomLeft) {
        if (w <= 0.0 || h <= 0.0 || maskW <= 0.0 || maskH <= 0.0) {
            return;
        }
        mesh.ensureQuadCapacity();

        int tlA = (cTopLeft >>> 24) & 0xFF;
        int tlR = (cTopLeft >>> 16) & 0xFF;
        int tlG = (cTopLeft >>> 8) & 0xFF;
        int tlB = cTopLeft & 0xFF;
        int trA = (cTopRight >>> 24) & 0xFF;
        int trR = (cTopRight >>> 16) & 0xFF;
        int trG = (cTopRight >>> 8) & 0xFF;
        int trB = cTopRight & 0xFF;
        int brA = (cBottomRight >>> 24) & 0xFF;
        int brR = (cBottomRight >>> 16) & 0xFF;
        int brG = (cBottomRight >>> 8) & 0xFF;
        int brB = cBottomRight & 0xFF;
        int blA = (cBottomLeft >>> 24) & 0xFF;
        int blR = (cBottomLeft >>> 16) & 0xFF;
        int blG = (cBottomLeft >>> 8) & 0xFF;
        int blB = cBottomLeft & 0xFF;
        float clampedRadius = clampRoundedRadius(radius, maskW, maskH);

        int i1 = mesh.vec2(x, y).local2(x, y).color(tlR, tlG, tlB, tlA).vec4(maskX, maskY, maskW, maskH).vec4(clampedRadius, 0.0f, 0.0f, 0.0f).next();
        int i2 = mesh.vec2(x, y + h).local2(x, y + h).color(blR, blG, blB, blA).vec4(maskX, maskY, maskW, maskH).vec4(clampedRadius, 0.0f, 0.0f, 0.0f).next();
        int i3 = mesh.vec2(x + w, y + h).local2(x + w, y + h).color(brR, brG, brB, brA).vec4(maskX, maskY, maskW, maskH).vec4(clampedRadius, 0.0f, 0.0f, 0.0f).next();
        int i4 = mesh.vec2(x + w, y).local2(x + w, y).color(trR, trG, trB, trA).vec4(maskX, maskY, maskW, maskH).vec4(clampedRadius, 0.0f, 0.0f, 0.0f).next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static void appendRoundedGlowQuad(MeshBuilder mesh,
                                              double x,
                                              double y,
                                              double w,
                                              double h,
                                              float radius,
                                              float glow,
                                              int argb) {
        if (w <= 0.0 || h <= 0.0 || glow <= 0.0f) {
            return;
        }
        mesh.ensureQuadCapacity();

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        float clampedRadius = clampRoundedRadius(radius, w, h);

        double gx = x - glow;
        double gy = y - glow;
        double gw = w + glow * 2.0;
        double gh = h + glow * 2.0;

        int i1 = mesh.vec2(gx, gy).local2(gx, gy).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, glow, 0f).next();
        int i2 = mesh.vec2(gx, gy + gh).local2(gx, gy + gh).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, glow, 0f).next();
        int i3 = mesh.vec2(gx + gw, gy + gh).local2(gx + gw, gy + gh).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, glow, 0f).next();
        int i4 = mesh.vec2(gx + gw, gy).local2(gx + gw, gy).color(r, g, b, a).vec4(x, y, w, h).vec4(clampedRadius, 0.0f, glow, 0f).next();
        mesh.quad(i1, i2, i3, i4);
    }

    private static float durabilityPercent(ItemStack stack) {
        int max = stack.getMaxDamage();
        if (max <= 0) {
            return 1.0f;
        }
        return Mth.clamp((max - stack.getDamageValue()) / (float) max, 0.0f, 1.0f);
    }

    private static int durabilityPercentInt(ItemStack stack) {
        return Math.round(durabilityPercent(stack) * 100.0f);
    }

    private static int withAlpha(int argb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (argb & 0x00FFFFFF);
    }

    private static int multiplyAlpha(int argb, float alphaFactor) {
        int alpha = (argb >>> 24) & 0xFF;
        return withAlpha(argb, Math.round(alpha * Mth.clamp(alphaFactor, 0.0f, 1.0f)));
    }

    private static int adjustRgb(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = Mth.clamp(Math.round(((argb >>> 16) & 0xFF) * factor), 0, 255);
        int g = Mth.clamp(Math.round(((argb >>> 8) & 0xFF) * factor), 0, 255);
        int b = Mth.clamp(Math.round((argb & 0xFF) * factor), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static void appendQuad(MeshBuilder mesh, double x, double y, double width, double height, int argb) {
        mesh.ensureQuadCapacity();

        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        int i1 = mesh.vec2(x, y).color(r, g, b, a).next();
        int i2 = mesh.vec2(x, y + height).color(r, g, b, a).next();
        int i3 = mesh.vec2(x + width, y + height).color(r, g, b, a).next();
        int i4 = mesh.vec2(x + width, y).color(r, g, b, a).next();
        mesh.quad(i1, i2, i3, i4);
    }

}
