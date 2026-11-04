/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.iris.geometry;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.backend.opengl.GlBuffer;
import combatant.client.features.playermodel.PlayerModels;
import combatant.client.render.engine.core.CombatantRenderSystem;
import combatant.client.features.playermodel.PlayerModelRenderDiagnostics;
import combatant.client.render.engine.rhi.CombatantRhi;
import combatant.client.render.engine.rhi.RhiCapabilities;
import combatant.client.render.engine.rhi.RhiDrawCommand;
import combatant.client.render.engine.rhi.upload.PersistentGpuMesh;
import combatant.client.render.engine.rig.mesh.RigMeshData;
import combatant.client.render.engine.rig.mesh.RigVertex;
import combatant.client.render.engine.rig.shader.RigShaderLimits;
import combatant.client.render.iris.IrisSceneDepth;
import combatant.client.render.iris.IrisRuntime;
import combatant.client.render.engine.core.CombatantWorldMatrices;
import combatant.client.render.engine.RenderState;
import net.irisshaders.iris.vertices.ImmediateState;
import net.irisshaders.iris.vertices.IrisVertexFormats;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.IdentityHashMap;
import java.util.List;

/**
 * Iris-compatible indexed skinned geometry. Compute writes *canonical Iris ENTITY vertices*, not
 * Combatant's custom rig ABI, so Iris can select and run the real shaderpack entity program.
 * Only OpenGL 4.3+ compute is used here; unsupported hardware keeps the ordered vanilla collector.
 *
 * Per-mesh vertex/index buffers are persistent. Per-instance bone palette is updated before each
 * compute dispatch, and each instance is drawn immediately afterwards (no shared output-buffer
 * overwrite when two actors reference the same immutable model).
 */
public final class IrisPlayerRigGpu {
    private static final int SOURCE_FLOATS = 21;
    private static final int SOURCE_STRIDE = SOURCE_FLOATS * Float.BYTES;
    private static final int LOCAL_SIZE = 128;
    private static final IdentityHashMap<RigMeshData, Mesh> MESHES = new IdentityHashMap<>();
    private static CombatantRhi owner;
    private static int shader;
    private static int paletteBuffer;
    private static boolean disabled;
    private static int lightLocation = -1;
    private static int overlayLocation = -1;
    private static final ByteBuffer PALETTE_SCRATCH = ByteBuffer.allocateDirect(
            RigShaderLimits.MAX_BONES * 64).order(ByteOrder.nativeOrder());

    private IrisPlayerRigGpu() {}

    public static boolean preflight(PlayerModels.Ready asset) {
        if (disabled || !IrisRuntime.isShaderpackRendererActive() || IrisRuntime.isRenderingShadowPass()) return false;
        try {
            RenderSystem.assertOnRenderThread();
            CombatantRhi rhi = CombatantRenderSystem.rhi();
            // The active RHI caches its device capability snapshot. Avoid allocating and
            // probing a new capability object for every player submission each frame.
            RhiCapabilities capabilities = rhi.capabilities();
            if (!capabilities.computeShaders() || !capabilities.shaderStorageBuffers()
                    || capabilities.backendName().toLowerCase(java.util.Locale.ROOT).contains("vulkan")) return false;
            if (owner != rhi) { closeAll(); owner = rhi; }
            if (shader == 0) initialize();
            for (var surface : asset.surfaces()) {
                MESHES.computeIfAbsent(surface.mesh(), mesh -> upload(rhi, mesh));
            }
            return true;
        } catch (RuntimeException | LinkageError failure) {
            disable(failure);
            return false;
        }
    }

    /** Invoked from Iris' actual G-buffer / forward-translucent boundaries, never post-composite. */
    public static void render(List<combatant.client.features.playermodel.PlayerModelGpuRenderer.IrisDraw> draws, boolean translucent) {
        if (disabled || draws.isEmpty() || owner == null || !IrisRuntime.isShaderpackRendererActive()) return;
        var mc = Minecraft.getInstance();
        if (mc == null || mc.gameRenderer == null || mc.gameRenderer.mainRenderTarget() == null) return;
        var target = mc.gameRenderer.mainRenderTarget();
        var lightmap = mc.gameRenderer.levelLightmap();
        var overlayTexture = mc.gameRenderer.overlayTexture().getTextureView();
        if (lightmap == null || overlayTexture == null) return;
        var lightSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var color = target.getColorTextureView();
        var depth = IrisSceneDepth.importedGeometryDepthAttachment();
        if (color == null || depth == null) return;

        var mv = RenderSystem.getModelViewStack();
        boolean oldMultiply = ImmediateState.safeToMultiply;
        boolean old3d = RenderState.rendering3D;
        mv.pushMatrix();
        try {
            Matrix4f view = CombatantWorldMatrices.positionMatrix();
            if (view != null) { mv.identity(); mv.mul(view); }
            ImmediateState.safeToMultiply = true;
            RenderState.rendering3D = true;
            for (var draw : draws) {
                var texture = mc.getTextureManager().getTexture(draw.snapshot().asset().texture());
                if (texture == null || texture.getTextureView() == null || texture.getSampler() == null) continue;
                for (var surface : draw.snapshot().asset().surfaces()) {
                    if (surface.translucent() != translucent) continue;
                    Mesh mesh = MESHES.get(surface.mesh());
                    if (mesh == null) continue;
                    // Flush each dispatch before its draw; a second actor may share this mesh.
                    long started = PlayerModelRenderDiagnostics.enabled() ? System.nanoTime() : 0L;
                    skin(mesh, draw.snapshot(), draw.light(), draw.overlay());
                    RhiDrawCommand command = RhiDrawCommand.builder("Iris indexed skinned player rig")
                            .pipeline(translucent
                                    ? IrisImportedGeometryPipelines.TRANSLUCENT_BLEND_DOUBLE_SIDED
                                    : IrisImportedGeometryPipelines.GBUFFER_DOUBLE_SIDED)
                            .colorAttachment(color).depthAttachment(depth)
                            .mesh(mesh.allocation.handle()).transform(draw.model())
                            .sampler("Sampler0", texture.getTextureView(), texture.getSampler())
                            .sampler("Sampler1", overlayTexture, lightSampler)
                            .sampler("Sampler2", lightmap, lightSampler)
                            .build();
                    CombatantRenderSystem.rhi().drawMeshes(List.of(command));
                    if (PlayerModelRenderDiagnostics.enabled())
                        PlayerModelRenderDiagnostics.irisIssued(1, mesh.vertices, System.nanoTime() - started);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            disable(failure);
        } finally {
            ImmediateState.safeToMultiply = oldMultiply;
            RenderState.rendering3D = old3d;
            mv.popMatrix();
        }
    }

    private static Mesh upload(CombatantRhi rhi, RigMeshData source) {
        int stride = IrisVertexFormats.ENTITY.getVertexSize();
        if (stride != 56) throw new IllegalStateException("Unexpected Iris ENTITY stride: " + stride);
        int count = source.vertexCount();
        ByteBuffer vertices = ByteBuffer.allocateDirect(Math.multiplyExact(count, stride)).order(ByteOrder.nativeOrder());
        ByteBuffer indices = ByteBuffer.allocateDirect(Math.multiplyExact(source.indexCount(), 4)).order(ByteOrder.nativeOrder());
        ByteBuffer inputs = ByteBuffer.allocateDirect(Math.multiplyExact(count, SOURCE_STRIDE)).order(ByteOrder.nativeOrder());
        int offset = 0;
        for (var part : source.parts()) {
            float[] tangents = tangents(part);
            for (int i = 0; i < part.vertexCount(); i++) {
                RigVertex v = part.vertex(i);
                if (v.deformId() >= 0) throw new IllegalArgumentException("Procedurally deformed rig needs CPU collector");
                inputs.putFloat(v.x()).putFloat(v.y()).putFloat(v.z())
                        .putFloat(v.u()).putFloat(v.v())
                        .putFloat(v.normalX()).putFloat(v.normalY()).putFloat(v.normalZ());
                int argb = v.colorArgb();
                inputs.putInt(((argb >>> 16) & 255) | (argb & 0x0000ff00)
                        | ((argb & 255) << 16) | (argb & 0xff000000));
                inputs.putFloat(v.bone0()).putFloat(v.bone1()).putFloat(v.bone2()).putFloat(v.bone3());
                inputs.putFloat(v.weight0()).putFloat(v.weight1()).putFloat(v.weight2()).putFloat(v.weight3());
                inputs.putFloat(tangents[4 * i]).putFloat(tangents[4 * i + 1])
                        .putFloat(tangents[4 * i + 2]).putFloat(tangents[4 * i + 3]);
            }
            for (int i = 0; i < part.indexCount(); i++) indices.putInt(offset + part.index(i));
            offset += part.vertexCount();
        }
        vertices.clear(); // Vertex data is populated only by compute, once per instance.
        indices.flip();
        inputs.flip();
        PersistentGpuMesh allocation = rhi.persistentMeshes().upload(
                "Iris dynamic skinned rig", vertices, stride, indices, source.indexCount(), IndexType.INT);
        int input = 0;
        try {
            if (!(allocation.handle().vertexBuffer() instanceof GlBuffer))
                throw new IllegalStateException("Iris rig vertex storage is not OpenGL-backed");
            input = GL15C.glGenBuffers();
            int oldGeneric = GL11C.glGetInteger(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING);
            try {
                GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, input);
                GL15C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, inputs, GL15C.GL_STATIC_DRAW);
            } finally {
                GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, oldGeneric);
            }
            return new Mesh(allocation, input, count);
        } catch (RuntimeException | Error error) {
            if (input != 0) GL15C.glDeleteBuffers(input);
            allocation.close();
            throw error;
        }
    }

    private static void skin(Mesh mesh, PlayerModels.Snapshot instance, int light, int overlay) {
        instance.rig().solve();
        int bones = instance.rig().pose().boneCount();
        if (bones <= 0 || bones > RigShaderLimits.MAX_BONES) throw new IllegalArgumentException("Invalid GPU rig bone count");
        ByteBuffer palette = PALETTE_SCRATCH;
        palette.clear();
        palette.limit(bones * 16 * Float.BYTES);
        java.nio.FloatBuffer floats = palette.asFloatBuffer();
        for (int i = 0; i < bones; i++) {
            Matrix4fc matrix = instance.rig().rig().skinMatrixRef(i);
            matrix.get(i * 16, floats);
        }
        int oldGeneric = GL11C.glGetInteger(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING);
        try {
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, paletteBuffer);
            GL15C.glBufferSubData(GL43C.GL_SHADER_STORAGE_BUFFER, 0, palette);
        } finally {
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, oldGeneric);
        }
        int oldProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int[] oldBuffers = new int[3];
        // Restore shader storage bindings so Iris shaderpack SSBOs are never displaced.
        for (int i = 0; i < 3; i++) oldBuffers[i] = GL30C.glGetIntegeri(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING, i);
        try {
            GL20C.glUseProgram(shader);
            GL30C.glUniform1ui(lightLocation, light);
            GL30C.glUniform1ui(overlayLocation, overlay);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 0, mesh.input);
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 1,
                    ((GlBuffer) mesh.allocation.handle().vertexBuffer()).handle());
            GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, 2, paletteBuffer);
            GL43C.glDispatchCompute((mesh.vertices + LOCAL_SIZE - 1) / LOCAL_SIZE, 1, 1);
            GL43C.glMemoryBarrier(GL43C.GL_VERTEX_ATTRIB_ARRAY_BARRIER_BIT | GL43C.GL_SHADER_STORAGE_BARRIER_BIT);
        } finally {
            for (int i = 0; i < 3; i++) GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, i, oldBuffers[i]);
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, oldGeneric);
            GL20C.glUseProgram(oldProgram);
        }
    }

    private static void initialize() {
        int compute = GL20C.glCreateShader(GL43C.GL_COMPUTE_SHADER);
        try {
            GL20C.glShaderSource(compute, SOURCE);
            GL20C.glCompileShader(compute);
            if (GL20C.glGetShaderi(compute, GL20C.GL_COMPILE_STATUS) == GL11C.GL_FALSE)
                throw new IllegalStateException("Iris rig compute shader: " + GL20C.glGetShaderInfoLog(compute));
            shader = GL20C.glCreateProgram();
            GL20C.glAttachShader(shader, compute);
            GL20C.glLinkProgram(shader);
            if (GL20C.glGetProgrami(shader, GL20C.GL_LINK_STATUS) == GL11C.GL_FALSE)
                throw new IllegalStateException("Iris rig compute link: " + GL20C.glGetProgramInfoLog(shader));
            lightLocation = GL20C.glGetUniformLocation(shader, "packedLight");
            overlayLocation = GL20C.glGetUniformLocation(shader, "packedOverlay");
            paletteBuffer = GL15C.glGenBuffers();
            int previous = GL11C.glGetInteger(GL43C.GL_SHADER_STORAGE_BUFFER_BINDING);
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, paletteBuffer);
            GL15C.glBufferData(GL43C.GL_SHADER_STORAGE_BUFFER, RigShaderLimits.MAX_BONES * 64L, GL15C.GL_STREAM_DRAW);
            GL15C.glBindBuffer(GL43C.GL_SHADER_STORAGE_BUFFER, previous);
        } finally {
            GL20C.glDeleteShader(compute);
        }
    }

    /** Invoked during asset reload and RHI shutdown; no GL resource may outlive its backend. */
    public static void invalidate() {
        if (!RenderSystem.isOnRenderThread()) {
            Minecraft.getInstance().execute(IrisPlayerRigGpu::invalidate);
            return;
        }
        closeAll();
        disabled = false;
    }

    public static void releaseBackend(CombatantRhi rhi) { if (owner == rhi) closeAll(); }

    private static void closeAll() {
        for (Mesh mesh : MESHES.values()) {
            if (mesh.input != 0) GL15C.glDeleteBuffers(mesh.input);
            if (owner != null) owner.resources().retire(mesh.allocation);
            else mesh.allocation.close();
        }
        MESHES.clear();
        if (paletteBuffer != 0) { GL15C.glDeleteBuffers(paletteBuffer); paletteBuffer = 0; }
        if (shader != 0) { GL20C.glDeleteProgram(shader); shader = 0; }
        lightLocation = overlayLocation = -1;
        owner = null;
    }

    private static void disable(Throwable failure) {
        disabled = true;
        combatant.client.util.logging.DebugLog.warnOnce("playermodel.iris.gpu.fallback",
                "[IrisRig] indexed compute unavailable, ordered collector fallback next frame: %s: %s",
                failure.getClass().getSimpleName(), failure.getMessage());
    }

    private record Mesh(PersistentGpuMesh allocation, int input, int vertices) {}

    /** Derive a stable UV tangent basis once per immutable mesh, not every frame. */
    private static float[] tangents(combatant.client.render.engine.rig.mesh.RigMeshPart part) {
        int count = part.vertexCount();
        float[] axes = new float[count * 6];
        float[] result = new float[count * 4];
        for (int i = 0; i < part.indexCount(); i += 3) {
            int ia = part.index(i), ib = part.index(i + 1), ic = part.index(i + 2);
            RigVertex a = part.vertex(ia), b = part.vertex(ib), c = part.vertex(ic);
            float x1 = b.x() - a.x(), y1 = b.y() - a.y(), z1 = b.z() - a.z();
            float x2 = c.x() - a.x(), y2 = c.y() - a.y(), z2 = c.z() - a.z();
            float u1 = b.u() - a.u(), v1 = b.v() - a.v();
            float u2 = c.u() - a.u(), v2 = c.v() - a.v();
            float det = u1 * v2 - u2 * v1;
            if (Math.abs(det) < 1e-12f) continue;
            float inv = 1f / det;
            float tx = (v2 * x1 - v1 * x2) * inv;
            float ty = (v2 * y1 - v1 * y2) * inv;
            float tz = (v2 * z1 - v1 * z2) * inv;
            float bx = (u1 * x2 - u2 * x1) * inv;
            float by = (u1 * y2 - u2 * y1) * inv;
            float bz = (u1 * z2 - u2 * z1) * inv;
            for (int vertex : new int[]{ia, ib, ic}) {
                int o = vertex * 6;
                axes[o] += tx; axes[o + 1] += ty; axes[o + 2] += tz;
                axes[o + 3] += bx; axes[o + 4] += by; axes[o + 5] += bz;
            }
        }
        for (int i = 0; i < count; i++) {
            RigVertex v = part.vertex(i);
            int o = i * 6, t = i * 4;
            float nx = v.normalX(), ny = v.normalY(), nz = v.normalZ();
            float tx = axes[o], ty = axes[o + 1], tz = axes[o + 2];
            float dot = nx * tx + ny * ty + nz * tz;
            tx -= nx * dot; ty -= ny * dot; tz -= nz * dot;
            float size = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (size < 1e-8f || !Float.isFinite(size)) {
                // Pick an axis perpendicular to the normal if a face has degenerate UVs.
                tx = Math.abs(nx) < .9f ? 0f : -nz;
                ty = Math.abs(nx) < .9f ? -nz : 0f;
                tz = Math.abs(nx) < .9f ? ny : nx;
                size = (float) Math.sqrt(tx * tx + ty * ty + tz * tz);
            }
            tx /= size; ty /= size; tz /= size;
            float handed = (ny * tz - nz * ty) * axes[o + 3]
                    + (nz * tx - nx * tz) * axes[o + 4]
                    + (nx * ty - ny * tx) * axes[o + 5];
            result[t] = tx; result[t + 1] = ty; result[t + 2] = tz;
            result[t + 3] = handed < 0f ? -1f : 1f;
        }
        return result;
    }

    // ENTITY = pos.xyz/color/uv0/overlay/light/normal/IDs/uv1/tangent, 56 bytes exactly.
    // Colors and packed normals are emitted as little-endian u32 for Iris' normalized attributes.
    private static final String SOURCE = """
            #version 430 core
            layout(local_size_x=128) in;
            layout(std430,binding=0) readonly buffer Source { uint v[]; };
            float a(uint i) { return uintBitsToFloat(v[i]); }
            layout(std430,binding=1) writeonly buffer Target { uint outputWords[]; };
            layout(std430,binding=2) readonly buffer Palette { mat4 joints[]; };
            uniform uint packedLight;
            uniform uint packedOverlay;
            uint sn(float x) { return uint(int(round(clamp(x,-1.0,1.0)*127.0))) & 255u; }
            uint n4(vec3 n) { return sn(n.x) | (sn(n.y)<<8u) | (sn(n.z)<<16u); }
            void main() {
                uint id=gl_GlobalInvocationID.x;
                uint b=id*21u;
                if(b>=uint(v.length())) return;
                vec3 p=vec3(a(b),a(b+1u),a(b+2u));
                vec3 n=vec3(a(b+5u),a(b+6u),a(b+7u));
                vec3 t=vec3(a(b+17u),a(b+18u),a(b+19u));
                // PlayerModelCompiler emits at most two influences, with normalized weights.
                // Do not fetch/branch over four matrix slots for every vertex.
                mat4 skin=joints[int(a(b+9u))];
                float w1=a(b+14u);
                if(w1>0.0) {
                    skin=skin*a(b+13u)+joints[int(a(b+10u))]*w1;
                }
                vec3 skp=(skin*vec4(p,1.0)).xyz;
                vec3 skn=(skin*vec4(n,0.0)).xyz;
                vec3 skt=(skin*vec4(t,0.0)).xyz;
                skn=length(skn)>1e-8 ? normalize(skn) : normalize(n);
                skt-=skn*dot(skn,skt);
                skt=length(skt)>1e-8 ? normalize(skt) : vec3(1.0,0.0,0.0);
                uint o=id*14u;
                outputWords[o]=floatBitsToUint(skp.x);
                outputWords[o+1u]=floatBitsToUint(skp.y);
                outputWords[o+2u]=floatBitsToUint(skp.z);
                outputWords[o+3u]=v[b+8u]; // RGBA8 (uint avoids NaN canonicalization)
                outputWords[o+4u]=v[b+3u];
                outputWords[o+5u]=v[b+4u];
                outputWords[o+6u]=packedOverlay;
                outputWords[o+7u]=packedLight;
                outputWords[o+8u]=n4(skn);
                outputWords[o+9u]=0u;
                outputWords[o+10u]=0u;
                outputWords[o+11u]=0u;
                outputWords[o+12u]=0u;
                outputWords[o+13u]=n4(skt) | (sn(-a(b+20u))<<24u);
            }
            """;
}
