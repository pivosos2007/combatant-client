/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.features.playeranimator.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import combatant.client.features.playeranimator.PlayerRigInstance;
import combatant.client.render.ViewObstructionFadeContext;
import combatant.client.render.engine.msaa.MsaaWorldTarget;
import combatant.client.render.engine.rig.deform.RigDeformDefinition;
import combatant.client.render.engine.rig.deform.RigDeformFlags;
import combatant.client.render.engine.rig.deform.RigDeformState;
import combatant.client.render.engine.rig.mesh.RigMeshData;
import combatant.client.render.engine.rig.mesh.RigMeshPart;
import combatant.client.render.engine.rig.mesh.RigVertex;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.OrderedSubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.UvMapping;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.ARGB;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Predicate;

/**
 * Ordered-submit compatible rig renderer. It CPU-skins the foundation mesh into vanilla's custom
 * geometry node, so skin, armor layers, trims, glint, outlines and renderer ordering remain intact.
 */
public final class PlayerRigCpuRenderer {
    private static final Map<HumanoidModel<?>, RigMeshData> PLAYER_MESHES = new WeakHashMap<>();
    private static final Map<HumanoidModel<?>, RigMeshData> ARMOR_MESHES = new WeakHashMap<>();

    private PlayerRigCpuRenderer() {
    }

    public static boolean submitPlayer(SubmitNodeCollector collector, HumanoidModel<?> model,
                                       AvatarRenderState state, PlayerRigInstance rig, PoseStack matrices,
                                       RenderType renderType, int light, int overlay, int tint,
                                       UvMapping sprite, int outlineColor,
                                       ModelFeatureRenderer.CrumblingOverlay crumbling) {
        if (collector == null || model == null || state == null || rig == null || renderType == null) return false;
        if (crumbling != null) return false;
        RigMeshData mesh;
        synchronized (PLAYER_MESHES) {
            mesh = PLAYER_MESHES.computeIfAbsent(model, key -> PlayerRigModelCompiler.compile(key, true));
        }
        Predicate<String> visible = name -> playerPartVisible(state, name);
        int effectiveTint = ViewObstructionFadeContext.applyToArgb(tint, MsaaWorldTarget.isActive());
        submitWithOutline(collector, mesh, rig, matrices, renderType, light, overlay, effectiveTint,
                sprite, outlineColor, visible);
        return true;
    }

    public static boolean submitArmor(OrderedSubmitNodeCollector collector, HumanoidModel<?> model,
                                      PlayerRigInstance rig, PoseStack matrices, RenderType renderType,
                                      int light, int overlay, int tint, UvMapping sprite,
                                      int outlineColor,
                                      ModelFeatureRenderer.CrumblingOverlay crumbling) {
        if (collector == null || model == null || rig == null || renderType == null) return false;
        if (crumbling != null) return false;
        RigMeshData mesh;
        synchronized (ARMOR_MESHES) {
            // Armor models are reused for helmet/chest/legs/boots. Compile all cubes once and
            // snapshot the per-submit visibility before vanilla mutates the model for the next slot.
            mesh = ARMOR_MESHES.computeIfAbsent(model, key -> PlayerRigModelCompiler.compile(key, true));
        }
        ArmorVisibility visibility = new ArmorVisibility(
                drawable(model.head), drawable(model.hat), drawable(model.body),
                drawable(model.leftArm), drawable(model.rightArm),
                drawable(model.leftLeg), drawable(model.rightLeg)
        );
        int effectiveTint = ViewObstructionFadeContext.applyToArgb(tint, MsaaWorldTarget.isActive());
        submitWithOutline(collector, mesh, rig, matrices, renderType, light, overlay, effectiveTint,
                sprite, outlineColor, visibility::test);
        return true;
    }

    public static void clearCaches() {
        synchronized (PLAYER_MESHES) { PLAYER_MESHES.clear(); }
        synchronized (ARMOR_MESHES) { ARMOR_MESHES.clear(); }
    }

    /** Scratch per deferred submission, shared between solid and outline passes. */
    public static final class ReplacementSkinCache {
        private final java.util.IdentityHashMap<RigMeshPart, float[]> vertices = new java.util.IdentityHashMap<>();
        private Matrix4fc[] skinMatrices;

        private float[] vertices(RigMeshPart part, PlayerRigInstance rig, RigDeformState deform) {
            if (skinMatrices == null) {
                // Snapshots are immutable after submission. Resolve the rig once, rather than
                // repeating the dirty-flag check for every influence of every vertex.
                rig.solve();
                skinMatrices = new Matrix4fc[rig.pose().boneCount()];
                for (int bone = 0; bone < skinMatrices.length; bone++) {
                    skinMatrices[bone] = rig.rig().skinMatrixRef(bone);
                }
            }
            return vertices.computeIfAbsent(part, key -> {
                // Six floats per distinct source vertex. Skinning is independent of the
                // consumer's light, tint and pose transform, which can vary between passes.
                float[] out = new float[Math.multiplyExact(key.vertexCount(), 6)];
                SkinScratch scratch = new SkinScratch();
                for (int i = 0; i < key.vertexCount(); i++) {
                    RigVertex vertex = key.vertex(i);
                    if (vertex.deformId() < 0) {
                        // Imported replacement meshes contain normalized static normals and
                        // no per-vertex procedural deformation channel.
                        scratch.localPosition.set(vertex.x(), vertex.y(), vertex.z());
                        scratch.localNormal.set(vertex.normalX(), vertex.normalY(), vertex.normalZ());
                    } else {
                        deform(vertex, deform, scratch);
                    }
                    skinReplacement(vertex, skinMatrices, scratch);
                    int base = i * 6;
                    out[base] = scratch.position.x;
                    out[base + 1] = scratch.position.y;
                    out[base + 2] = scratch.position.z;
                    out[base + 3] = scratch.normal.x;
                    out[base + 4] = scratch.normal.y;
                    out[base + 5] = scratch.normal.z;
                }
                return out;
            });
        }
    }

    public static void submitReplacement(OrderedSubmitNodeCollector collector, RigMeshData mesh,
                                         PlayerRigInstance rig, PoseStack matrices, RenderType renderType,
                                         int light, int overlay, int tint, int outlineColor,
                                         Object resourceOwner, ReplacementSkinCache skinCache,
                                         Predicate<String> visiblePart) {
        int effectiveTint = ViewObstructionFadeContext.applyToArgb(tint, MsaaWorldTarget.isActive());
        // Retain resource ownership throughout the deferred collector lifetime.
        Predicate<String> residentParts = name -> {
            java.lang.ref.Reference.reachabilityFence(resourceOwner);
            return visiblePart.test(name);
        };
        if (renderType.isOutline()) {
            submitCached(collector, mesh, rig, matrices, renderType, 15728880,
                    OverlayTexture.NO_OVERLAY, outlineColor != 0 ? outlineColor : tint,
                    residentParts, skinCache);
            return;
        }
        submitCached(collector, mesh, rig, matrices, renderType, light, overlay,
                effectiveTint, residentParts, skinCache);
        if (outlineColor != 0) renderType.outline().ifPresent(outline ->
                submitCached(collector, mesh, rig, matrices, outline, 15728880,
                        OverlayTexture.NO_OVERLAY, outlineColor, residentParts, skinCache));
    }

    private static void submitCached(OrderedSubmitNodeCollector collector, RigMeshData mesh,
                                     PlayerRigInstance rig, PoseStack matrices, RenderType type,
                                     int light, int overlay, int tint,
                                     Predicate<String> parts, ReplacementSkinCache skinCache) {
        collector.submitCustomGeometry(matrices, type,
                (pose, consumer) -> renderCached(mesh, rig, pose, consumer, light, overlay,
                        tint, parts, type.primitiveTopology(), skinCache));
    }

    private static void renderCached(RigMeshData mesh, PlayerRigInstance rig, PoseStack.Pose pose,
                                     VertexConsumer consumer, int light, int overlay, int tint,
                                     Predicate<String> visiblePart, PrimitiveTopology topology,
                                     ReplacementSkinCache cache) {
        if (topology != PrimitiveTopology.QUADS && topology != PrimitiveTopology.TRIANGLES)
            throw new IllegalStateException("Unsupported replacement topology: " + topology);
        RigDeformState deform = rig.rig().deform();
        boolean diagnostic = combatant.client.features.playermodel.PlayerModelRenderDiagnostics.enabled();
        long started = diagnostic ? System.nanoTime() : 0L;
        int emitted = 0;
        // Two tightly grouped geometry submissions, no matrix skinning in the triangle loop.
        // Preserve original triangle winding / per-vertex attributes / outline tint.
        Vector3f position = new Vector3f();
        Vector3f normal = new Vector3f();
        for (RigMeshPart part : mesh.parts()) {
            if (!visiblePart.test(part.name())) continue;
            float[] skinned = cache.vertices(part, rig, deform);
            if (diagnostic) emitted += part.indexCount() / 3 * (topology == PrimitiveTopology.QUADS ? 4 : 3);
            for (int i = 0; i < part.indexCount(); i += 3) {
                for (int corner = 0; corner < (topology == PrimitiveTopology.QUADS ? 4 : 3); corner++) {
                    int offset = Math.min(corner, 2);
                    int index = part.index(i + offset);
                    RigVertex vertex = part.vertex(index);
                    int base = index * 6;
                    position.set(skinned[base], skinned[base + 1], skinned[base + 2]);
                    normal.set(skinned[base + 3], skinned[base + 4], skinned[base + 5]);
                    consumer.addVertex(pose, position);
                    consumer.setColor(ARGB.multiply(tint, vertex.colorArgb()));
                    consumer.setUv(vertex.u(), vertex.v());
                    consumer.setOverlay(overlay);
                    consumer.setLight(light);
                    consumer.setNormal(pose, normal);
                }
            }
        }
        if (diagnostic) combatant.client.features.playermodel.PlayerModelRenderDiagnostics
                .cpuGeometry(System.nanoTime() - started, emitted);
    }

    private static void submit(OrderedSubmitNodeCollector collector, RigMeshData mesh, PlayerRigInstance rig,
                               PoseStack matrices, RenderType renderType, int light, int overlay, int tint,
                               UvMapping sprite, Predicate<String> visiblePart) {
        collector.submitCustomGeometry(matrices, renderType,
                (pose, consumer) -> render(mesh, rig, pose, consumer, light, overlay, tint, sprite,
                        visiblePart, renderType.primitiveTopology()));
    }

    private static void submitWithOutline(OrderedSubmitNodeCollector collector, RigMeshData mesh,
                                          PlayerRigInstance rig, PoseStack matrices, RenderType renderType,
                                          int light, int overlay, int tint, UvMapping sprite,
                                          int outlineColor, Predicate<String> visiblePart) {
        if (renderType.isOutline()) {
            submit(collector, mesh, rig, matrices, renderType, 15728880, OverlayTexture.NO_OVERLAY,
                    outlineColor != 0 ? outlineColor : tint, sprite, visiblePart);
            return;
        }

        submit(collector, mesh, rig, matrices, renderType, light, overlay, tint, sprite, visiblePart);
        if (outlineColor == 0) return;
        renderType.outline().ifPresent(outline -> submit(
                collector, mesh, rig, matrices, outline, 15728880, OverlayTexture.NO_OVERLAY,
                outlineColor, sprite, visiblePart
        ));
    }

    private static void render(RigMeshData mesh, PlayerRigInstance rig, PoseStack.Pose pose,
                               VertexConsumer rawConsumer, int light, int overlay, int tint,
                               UvMapping sprite, Predicate<String> visiblePart,
                               PrimitiveTopology topology) {
        VertexConsumer consumer = sprite != null ? sprite.wrap(rawConsumer) : rawConsumer;
        SkinScratch scratch = new SkinScratch();
        RigDeformState deform = rig.rig().deform();
        for (RigMeshPart part : mesh.parts()) {
            if (!visiblePart.test(baseName(part.name()))) continue;
            if (part.indexCount() % 3 != 0) {
                throw new IllegalStateException("Rig mesh part must contain triangle indices: " + part.name());
            }
            for (int i = 0; i < part.indexCount(); i += 3) {
                RigVertex a = part.vertex(part.index(i));
                RigVertex b = part.vertex(part.index(i + 1));
                RigVertex c = part.vertex(part.index(i + 2));
                emit(a, deform, rig, scratch, pose, consumer, light, overlay, tint);
                emit(b, deform, rig, scratch, pose, consumer, light, overlay, tint);
                emit(c, deform, rig, scratch, pose, consumer, light, overlay, tint);
                if (topology == PrimitiveTopology.QUADS) {
                    // A triangle represented as (a,b,c,c) makes the quad's second generated
                    // triangle degenerate instead of joining unrelated mesh faces.
                    emit(c, deform, rig, scratch, pose, consumer, light, overlay, tint);
                } else if (topology != PrimitiveTopology.TRIANGLES) {
                    throw new IllegalStateException("Unsupported rig render topology: " + topology);
                }
            }
        }
    }

    private static void emit(RigVertex vertex, RigDeformState deform, PlayerRigInstance rig,
                             SkinScratch scratch, PoseStack.Pose pose, VertexConsumer consumer,
                             int light, int overlay, int tint) {
        deform(vertex, deform, scratch);
        skin(vertex, rig, scratch);
        int color = ARGB.multiply(tint, vertex.colorArgb());
        // Do not use a fluent chain here. UvMapping.wrap(...) may return a
        // SpriteCoordinateExpander, whose addVertex(...) forwards to and returns the delegate.
        // Chaining setUv(...) from that return value therefore bypasses sprite UV expansion and
        // makes trim passes sample the whole armor-trims atlas. Keep every attribute write on the
        // wrapper itself so atlas-backed armor trims receive the same UV remap as vanilla models.
        consumer.addVertex(pose, scratch.position);
        consumer.setColor(color);
        consumer.setUv(vertex.u(), vertex.v());
        consumer.setOverlay(overlay);
        consumer.setLight(light);
        consumer.setNormal(pose, scratch.normal);
    }

    private static void skin(RigVertex vertex, PlayerRigInstance rig, SkinScratch out) {
        out.position.zero();
        out.normal.zero();
        applySkin(rig, vertex.bone0(), vertex.weight0(), out.localPosition, out.localNormal, out);
        applySkin(rig, vertex.bone1(), vertex.weight1(), out.localPosition, out.localNormal, out);
        applySkin(rig, vertex.bone2(), vertex.weight2(), out.localPosition, out.localNormal, out);
        applySkin(rig, vertex.bone3(), vertex.weight3(), out.localPosition, out.localNormal, out);
        if (out.normal.lengthSquared() > 1.0e-8f) out.normal.normalize();
        else out.normal.set(out.localNormal);
    }

    private static void skinReplacement(RigVertex vertex, Matrix4fc[] matrices, SkinScratch out) {
        out.position.zero();
        out.normal.zero();
        applyReplacementSkin(matrices, vertex.bone0(), vertex.weight0(), out);
        applyReplacementSkin(matrices, vertex.bone1(), vertex.weight1(), out);
        applyReplacementSkin(matrices, vertex.bone2(), vertex.weight2(), out);
        applyReplacementSkin(matrices, vertex.bone3(), vertex.weight3(), out);
        if (out.normal.lengthSquared() > 1.0e-8f) out.normal.normalize();
        else out.normal.set(out.localNormal);
    }

    private static void applyReplacementSkin(Matrix4fc[] matrices, int bone, float weight,
                                             SkinScratch out) {
        if (bone < 0 || weight <= 0f) return;
        if (bone >= matrices.length) throw new IllegalArgumentException("Invalid replacement bone: " + bone);
        Matrix4fc matrix = matrices[bone];
        matrix.transformPosition(out.localPosition, out.transformedPosition);
        matrix.transformDirection(out.localNormal, out.transformedNormal);
        out.position.fma(weight, out.transformedPosition);
        out.normal.fma(weight, out.transformedNormal);
    }

    private static void applySkin(PlayerRigInstance rig, int bone, float weight,
                                  Vector3f position, Vector3f normal, SkinScratch out) {
        if (bone < 0 || weight <= 0f) return;
        Matrix4fc matrix = rig.rig().skinMatrixRef(bone);
        matrix.transformPosition(position, out.transformedPosition);
        matrix.transformDirection(normal, out.transformedNormal);
        out.position.fma(weight, out.transformedPosition);
        out.normal.fma(weight, out.transformedNormal);
    }

    private static void deform(RigVertex vertex, RigDeformState state, SkinScratch scratch) {
        Vector3f position = scratch.localPosition;
        Vector3f normal = scratch.localNormal;
        position.set(vertex.x(), vertex.y(), vertex.z());
        normal.set(vertex.normalX(), vertex.normalY(), vertex.normalZ()).normalize();
        int id = vertex.deformId();
        if (id < 0 || !state.defined(id)) return;
        int flags = vertex.deformFlags() & state.flags(id);
        if ((flags & (RigDeformFlags.BEND | RigDeformFlags.TWIST)) == 0) return;

        RigDeformDefinition definition = state.definition(id);
        Vector3f axis = scratch.axis.set(definition.axis());
        Vector3f bendAxis = scratch.bendAxis.set(definition.bendAxis());
        Vector3f origin = scratch.origin.set(definition.origin());
        float u = Math.max(0f, Math.min(1f, vertex.deformU()));
        float longitudinal = u * definition.length();
        Vector3f restCenter = scratch.restCenter.set(axis).mul(longitudinal).add(origin);
        Vector3f crossSection = scratch.crossSection.set(position).sub(restCenter);

        if ((flags & RigDeformFlags.TWIST) != 0 && Math.abs(state.twistAngle(id)) > 1.0e-7f) {
            float angle = state.twistAngle(id) * profile(u, definition.twistStart(), definition.twistEnd(), state.twistFalloff(id));
            crossSection.rotateAxis(angle, axis.x, axis.y, axis.z);
            normal.rotateAxis(angle, axis.x, axis.y, axis.z);
        }

        Vector3f center = restCenter;
        float localAngle = 0f;
        float fullAngle = state.bendAngle(id);
        if ((flags & RigDeformFlags.BEND) != 0 && Math.abs(fullAngle) > 1.0e-7f) {
            float start = definition.bendStart();
            float end = definition.bendEnd();
            float startDistance = start * definition.length();
            float endDistance = end * definition.length();
            float activeLength = Math.max(endDistance - startDistance, 1.0e-6f);
            Vector3f direction = scratch.direction.set(bendAxis).cross(axis).normalize();
            Vector3f startCenter = scratch.startCenter.set(axis).mul(startDistance).add(origin);
            if (u > start) {
                localAngle = fullAngle * profile(u, start, end, state.bendFalloff(id));
                float radius = activeLength / fullAngle;
                if (u < end) {
                    center = startCenter
                            .fma((float) Math.sin(localAngle) * radius, axis)
                            .fma((1f - (float) Math.cos(localAngle)) * radius, direction);
                } else {
                    Vector3f endCenter = scratch.endCenter.set(startCenter)
                            .fma((float) Math.sin(fullAngle) * radius, axis)
                            .fma((1f - (float) Math.cos(fullAngle)) * radius, direction);
                    Vector3f tangent = scratch.tangent.set(axis)
                            .rotateAxis(fullAngle, bendAxis.x, bendAxis.y, bendAxis.z);
                    center = endCenter.fma(longitudinal - endDistance, tangent);
                    localAngle = fullAngle;
                }
            }
            crossSection.rotateAxis(localAngle, bendAxis.x, bendAxis.y, bendAxis.z);
            normal.rotateAxis(localAngle, bendAxis.x, bendAxis.y, bendAxis.z);
        }
        position.set(center).add(crossSection);
        normal.normalize();
    }

    private static float profile(float u, float start, float end, float falloff) {
        float t = Math.max(0f, Math.min(1f, (u - start) / Math.max(end - start, 1.0e-6f)));
        float smooth = t * t * (3f - 2f * t);
        return t + (smooth - t) * Math.max(0f, Math.min(1f, falloff));
    }

    private static boolean playerPartVisible(AvatarRenderState state, String part) {
        if (state.isSpectator) return "head".equals(part) || "hat".equals(part) && state.showHat;
        return switch (part) {
            case "hat" -> state.showHat;
            case "jacket" -> state.showJacket;
            case "left_sleeve" -> state.showLeftSleeve;
            case "right_sleeve" -> state.showRightSleeve;
            case "left_pants" -> state.showLeftPants;
            case "right_pants" -> state.showRightPants;
            default -> true;
        };
    }

    private static String baseName(String name) {
        int split = name.lastIndexOf('_');
        if (split > 0 && split + 1 < name.length() && Character.isDigit(name.charAt(split + 1))) {
            return name.substring(0, split);
        }
        return name;
    }

    private static boolean drawable(net.minecraft.client.model.geom.ModelPart part) {
        return part != null && part.visible && !part.skipDraw;
    }

    private record ArmorVisibility(boolean head, boolean hat, boolean body,
                                   boolean leftArm, boolean rightArm,
                                   boolean leftLeg, boolean rightLeg) {
        boolean test(String part) {
            return switch (part) {
                case "head" -> head;
                case "hat" -> hat;
                case "body", "jacket" -> body;
                case "left_arm", "left_sleeve" -> leftArm;
                case "right_arm", "right_sleeve" -> rightArm;
                case "left_leg", "left_pants" -> leftLeg;
                case "right_leg", "right_pants" -> rightLeg;
                default -> true;
            };
        }
    }

    private static final class SkinScratch {
        final Vector3f localPosition = new Vector3f();
        final Vector3f localNormal = new Vector3f();
        final Vector3f position = new Vector3f();
        final Vector3f normal = new Vector3f();
        final Vector3f transformedPosition = new Vector3f();
        final Vector3f transformedNormal = new Vector3f();
        final Vector3f axis = new Vector3f();
        final Vector3f bendAxis = new Vector3f();
        final Vector3f origin = new Vector3f();
        final Vector3f restCenter = new Vector3f();
        final Vector3f crossSection = new Vector3f();
        final Vector3f direction = new Vector3f();
        final Vector3f startCenter = new Vector3f();
        final Vector3f endCenter = new Vector3f();
        final Vector3f tangent = new Vector3f();
    }
}
