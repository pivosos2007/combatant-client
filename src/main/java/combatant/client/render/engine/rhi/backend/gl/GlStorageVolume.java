/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */
package combatant.client.render.engine.rhi.backend.gl;

import com.mojang.renderpearl.backend.opengl.GlConst;
import combatant.client.render.engine.rhi.RhiStats;
import combatant.client.render.engine.rhi.shader.RhiFeatureSupport;
import combatant.client.render.engine.rhi.shader.RhiStorageVolume;
import combatant.client.render.engine.rhi.shader.RhiValidation;
import combatant.client.render.engine.rhi.shader.RhiVolumeCapabilities;
import combatant.client.render.engine.rhi.shader.RhiVolumeView;
import combatant.client.render.engine.rhi.shader.StorageVolumeDescriptor;
import combatant.client.render.engine.rhi.shader.VolumeViewDescriptor;
import combatant.client.util.logging.DebugLog;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL12C;
import org.lwjgl.opengl.GL42C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.ARBTextureView;

import java.util.HashMap;
import java.util.Map;

/** Native GL_TEXTURE_3D allocation owned by the active Mojang OpenGL context. */
final class GlStorageVolume implements RhiStorageVolume {
    private final StorageVolumeDescriptor descriptor;
    private final RhiStats stats;
    private final Map<ViewKey, Integer> sampledViewIds = new HashMap<>();
    final int id;
    private boolean closed;

    GlStorageVolume(StorageVolumeDescriptor descriptor, RhiStats stats) {
        this.descriptor = descriptor;
        this.stats = stats;
        RhiVolumeCapabilities capabilities = capabilities(descriptor);
        if (!capabilities.supportsDescriptor(descriptor)) {
            throw new UnsupportedOperationException(firstFailure(capabilities, descriptor));
        }

        int internalFormat = GlConst.toGlInternalId(descriptor.format());
        int previous = GL11C.glGetInteger(GL12C.GL_TEXTURE_BINDING_3D);
        int texture = GL11C.glGenTextures();
        try {
            GL11C.glBindTexture(GL12C.GL_TEXTURE_3D, texture);
            GL11C.glTexParameteri(GL12C.GL_TEXTURE_3D, GL12C.GL_TEXTURE_BASE_LEVEL, 0);
            GL11C.glTexParameteri(GL12C.GL_TEXTURE_3D, GL12C.GL_TEXTURE_MAX_LEVEL, descriptor.mipLevels() - 1);
            GL42C.glTexStorage3D(GL12C.GL_TEXTURE_3D, descriptor.mipLevels(), internalFormat,
                    descriptor.width(), descriptor.height(), descriptor.depth());
        } catch (RuntimeException | Error t) {
            GL11C.glDeleteTextures(texture);
            throw t;
        } finally {
            GL11C.glBindTexture(GL12C.GL_TEXTURE_3D, previous);
        }
        this.id = texture;
        stats.storageVolumeAllocated(descriptor.approximateByteSize());
        DebugLog.debug("[CombatantRHI/GL] allocated 3D volume '{}' {}x{}x{} mips={} format={} usage=0x{} approxBytes={}",
                descriptor.label(), descriptor.width(), descriptor.height(), descriptor.depth(), descriptor.mipLevels(),
                descriptor.format(), Integer.toHexString(descriptor.usage()), descriptor.approximateByteSize());
    }

    static RhiVolumeCapabilities capabilities(StorageVolumeDescriptor descriptor) {
        if (descriptor == null) return RhiVolumeCapabilities.unsupported("Volume descriptor is null");
        int max3d = GL11C.glGetInteger(GL12C.GL_MAX_3D_TEXTURE_SIZE);
        if (descriptor.width() > max3d || descriptor.height() > max3d || descriptor.depth() > max3d) {
            RhiFeatureSupport allocation = RhiFeatureSupport.unsupported(
                    "OpenGL 3D texture exceeds GL_MAX_3D_TEXTURE_SIZE=" + max3d + ": "
                            + descriptor.width() + "x" + descriptor.height() + "x" + descriptor.depth(),
                    RhiFeatureSupport.Fallback.REDUCE_RESOURCE);
            return new RhiVolumeCapabilities(allocation, allocation, allocation, allocation, allocation, allocation);
        }
        int internalFormat = GlConst.toGlInternalId(descriptor.format());
        if (internalFormat == 0) {
            RhiFeatureSupport format = RhiFeatureSupport.unsupported(
                    "OpenGL has no sized internal format for " + descriptor.format(),
                    RhiFeatureSupport.Fallback.ALTERNATE_FORMAT);
            return new RhiVolumeCapabilities(format, format, format, format, format, format);
        }
        int supported = GL43C.glGetInternalformati(GL12C.GL_TEXTURE_3D, internalFormat, GL43C.GL_INTERNALFORMAT_SUPPORTED);
        if (supported != GL11C.GL_TRUE) {
            RhiFeatureSupport format = RhiFeatureSupport.unsupported(
                    "OpenGL internal format is unsupported for GL_TEXTURE_3D: " + descriptor.format(),
                    RhiFeatureSupport.Fallback.ALTERNATE_FORMAT);
            return new RhiVolumeCapabilities(format, format, format, format, format, format);
        }

        RhiFeatureSupport allocation = RhiFeatureSupport.available();
        RhiFeatureSupport storage;
        if (!descriptor.storage()) {
            storage = RhiFeatureSupport.unsupported(
                    "3D volume descriptor lacks STORAGE_IMAGE usage",
                    RhiFeatureSupport.Fallback.RECREATE_WITH_REQUIRED_USAGE);
        } else if (descriptor.access().readable()
                && !supportsOperation(GL43C.glGetInternalformati(
                        GL12C.GL_TEXTURE_3D, internalFormat, GL43C.GL_SHADER_IMAGE_LOAD))) {
            storage = RhiFeatureSupport.unsupported(
                    "OpenGL format does not support image load for 3D storage: " + descriptor.format(),
                    RhiFeatureSupport.Fallback.ALTERNATE_FORMAT);
        } else if (descriptor.access().writable()
                && !supportsOperation(GL43C.glGetInternalformati(
                        GL12C.GL_TEXTURE_3D, internalFormat, GL43C.GL_SHADER_IMAGE_STORE))) {
            storage = RhiFeatureSupport.unsupported(
                    "OpenGL format does not support image store for 3D storage: " + descriptor.format(),
                    RhiFeatureSupport.Fallback.ALTERNATE_FORMAT);
        } else {
            storage = RhiFeatureSupport.available();
        }

        RhiFeatureSupport sampled = descriptor.sampled()
                ? RhiFeatureSupport.available()
                : RhiFeatureSupport.unsupported(
                "3D volume descriptor lacks TEXTURE_BINDING usage",
                RhiFeatureSupport.Fallback.RECREATE_WITH_REQUIRED_USAGE);
        boolean textureViews = GL.getCapabilities().OpenGL43 || GL.getCapabilities().GL_ARB_texture_view;
        RhiFeatureSupport mipViews = !descriptor.sampled()
                ? sampled
                : (textureViews
                ? RhiFeatureSupport.available()
                : RhiFeatureSupport.unsupported("OpenGL texture views are unavailable for sampled mip-range views",
                RhiFeatureSupport.Fallback.DROP_OPTIONAL_USAGE));
        RhiFeatureSupport copy;
        if (!descriptor.copySource() && !descriptor.copyDestination()) {
            copy = RhiFeatureSupport.unsupported(
                    "3D volume descriptor lacks COPY_SRC/COPY_DST usage",
                    RhiFeatureSupport.Fallback.RECREATE_WITH_REQUIRED_USAGE);
        } else if (GL.getCapabilities().OpenGL43 || GL.getCapabilities().GL_ARB_copy_image) {
            copy = RhiFeatureSupport.available();
        } else {
            copy = RhiFeatureSupport.unsupported("OpenGL copy-image support is unavailable",
                    RhiFeatureSupport.Fallback.DISABLE_CONSUMER);
        }
        RhiFeatureSupport clear;
        if (!descriptor.copyDestination()) {
            clear = RhiFeatureSupport.unsupported(
                    "3D volume descriptor lacks COPY_DST usage required by the RHI clear contract",
                    RhiFeatureSupport.Fallback.RECREATE_WITH_REQUIRED_USAGE);
        } else if (GL.getCapabilities().OpenGL44 || GL.getCapabilities().GL_ARB_clear_texture) {
            clear = RhiFeatureSupport.available();
        } else {
            clear = RhiFeatureSupport.unsupported("OpenGL clear-texture support is unavailable",
                    RhiFeatureSupport.Fallback.DISABLE_CONSUMER);
        }
        return new RhiVolumeCapabilities(allocation, storage, sampled, mipViews, copy, clear);
    }

    /**
     * ARB_internalformat_query2 operation queries are tri-state support levels, not GL booleans.
     * FULL_SUPPORT and CAVEAT_SUPPORT both mean the operation is legal; only GL_NONE is unsupported.
     */
    private static boolean supportsOperation(int supportLevel) {
        return supportLevel != GL11C.GL_NONE;
    }

    private static String firstFailure(RhiVolumeCapabilities capabilities, StorageVolumeDescriptor descriptor) {
        if (!capabilities.allocation().supported()) return capabilities.allocation().reason();
        if (descriptor.storage() && !capabilities.storageViews().supported()) return capabilities.storageViews().reason();
        if (descriptor.sampled() && !capabilities.sampledViews().supported()) return capabilities.sampledViews().reason();
        if ((descriptor.copySource() || descriptor.copyDestination()) && !capabilities.copy().supported()) return capabilities.copy().reason();
        return "OpenGL 3D volume descriptor is unsupported";
    }

    @Override
    public StorageVolumeDescriptor descriptor() {
        return descriptor;
    }

    @Override
    public String backendName() {
        return "OpenGL";
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    int sampledTextureId(RhiVolumeView view) {
        ensureView(view, true, false);
        VolumeViewDescriptor range = view.descriptor();
        if (range.baseMipLevel() == 0 && range.mipLevels() == descriptor.mipLevels()) return id;
        ViewKey key = new ViewKey(range.baseMipLevel(), range.mipLevels());
        Integer existing = sampledViewIds.get(key);
        if (existing != null) return existing;
        if (!(GL.getCapabilities().OpenGL43 || GL.getCapabilities().GL_ARB_texture_view)) {
            throw new UnsupportedOperationException("OpenGL sampled mip-range volume views require texture-view support");
        }
        int textureView = GL11C.glGenTextures();
        try {
            if (GL.getCapabilities().OpenGL43) {
                GL43C.glTextureView(textureView, GL12C.GL_TEXTURE_3D, id,
                        GlConst.toGlInternalId(descriptor.format()),
                        range.baseMipLevel(), range.mipLevels(), 0, 1);
            } else {
                ARBTextureView.glTextureView(textureView, GL12C.GL_TEXTURE_3D, id,
                        GlConst.toGlInternalId(descriptor.format()),
                        range.baseMipLevel(), range.mipLevels(), 0, 1);
            }
        } catch (RuntimeException | Error t) {
            GL11C.glDeleteTextures(textureView);
            throw t;
        }
        sampledViewIds.put(key, textureView);
        stats.storageVolumeViewCreated();
        DebugLog.debug("[CombatantRHI/GL] created sampled 3D view '{}' baseMip={} mips={}",
                descriptor.label(), range.baseMipLevel(), range.mipLevels());
        return textureView;
    }

    int storageMip(RhiVolumeView view) {
        ensureView(view, false, true);
        return view.descriptor().baseMipLevel();
    }

    private void ensureView(RhiVolumeView view, boolean sampled, boolean storage) {
        if (closed) throw new IllegalStateException("Storage volume is closed: " + descriptor.label());
        if (view == null || view.volume() != this) {
            throw new IllegalArgumentException("Volume view does not belong to this OpenGL resource");
        }
        view.requireValid();
        if (sampled && !view.descriptor().sampled()) throw new IllegalArgumentException("Expected sampled volume view");
        if (storage && !view.descriptor().storage()) throw new IllegalArgumentException("Expected storage volume view");
    }

    @Override
    public void close() {
        if (closed) {
            RhiValidation.doubleDestroy("OpenGL storage volume", descriptor.label());
            return;
        }
        closed = true;
        for (int view : sampledViewIds.values()) GL11C.glDeleteTextures(view);
        sampledViewIds.clear();
        GL11C.glDeleteTextures(id);
        stats.storageVolumeDestroyed(descriptor.approximateByteSize());
        DebugLog.debug("[CombatantRHI/GL] destroyed 3D volume '{}'", descriptor.label());
    }

    private record ViewKey(int baseMipLevel, int mipLevels) {}
}
