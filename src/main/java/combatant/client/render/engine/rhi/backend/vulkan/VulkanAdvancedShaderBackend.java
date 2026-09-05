/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.render.engine.rhi.backend.vulkan;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.vulkan.Destroyable;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuBuffer;
import com.mojang.blaze3d.vulkan.VulkanGpuSampler;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import combatant.client.mixininterface.IMsaaTexture;
import combatant.client.mixininterface.IVulkanBackendInfo;
import combatant.client.mixininterface.IVulkanCommandEncoderAccess;
import combatant.client.render.engine.rhi.GpuMeshHandle;
import combatant.client.render.engine.rhi.RhiCapabilities;
import combatant.client.render.engine.rhi.RhiStats;
import combatant.client.render.engine.rhi.shader.*;
import combatant.client.render.engine.shader.CombatantShaderSources;
import net.minecraft.client.Minecraft;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.lwjgl.util.shaderc.Shaderc.*;
import static org.lwjgl.vulkan.KHRDynamicRendering.vkCmdBeginRenderingKHR;
import static org.lwjgl.vulkan.KHRDynamicRendering.vkCmdEndRenderingKHR;
import static org.lwjgl.vulkan.KHRSynchronization2.*;
import static org.lwjgl.vulkan.VK12.*;

/**
 * Vulkan-native advanced shader backend sharing Mojang's VkDevice, VMA allocator and command
 * submission. Native commands are appended to Mojang's current graphics submission; Combatant does
 * not create a second device, allocator, queue owner or hidden submit chain.
 */
final class VulkanAdvancedShaderBackend implements AdvancedShaderBackend {
    private static final int DESCRIPTOR_SETS_PER_POOL = 64;

    private final RhiStats stats;
    private final VulkanNativeShaderCompiler compiler = new VulkanNativeShaderCompiler();
    private final Map<DescriptorPoolKey, DescriptorPoolBatch> descriptorPools = new HashMap<>();
    private long descriptorSubmitIndex = Long.MIN_VALUE;
    private volatile boolean closed;

    VulkanAdvancedShaderBackend(RhiStats stats) {
        this.stats = stats;
    }

    @Override
    public EnumSet<RhiShaderStage> stages() {
        EnumSet<RhiShaderStage> stages = EnumSet.of(RhiShaderStage.VERTEX, RhiShaderStage.FRAGMENT);
        RhiCapabilities capabilities = RhiCapabilities.current();
        if (capabilities.nativeComputeSubmission()) stages.add(RhiShaderStage.COMPUTE);
        if (capabilities.nativeTessellationSubmission()) {
            stages.add(RhiShaderStage.TESS_CONTROL);
            stages.add(RhiShaderStage.TESS_EVALUATION);
        }
        if (capabilities.nativeGeometrySubmission()) stages.add(RhiShaderStage.GEOMETRY);
        return stages;
    }

    @Override
    public RhiStorageBuffer createStorageBuffer(StorageBufferDescriptor descriptor) {
        requireOpen();
        if (descriptor == null) throw new IllegalArgumentException("descriptor");
        IVulkanBackendInfo backend = requireBackend();
        if (backend.combatant$vma() == 0L) {
            throw new UnsupportedOperationException("Native Vulkan storage allocator is unavailable");
        }
        long maxRange = Integer.toUnsignedLong(
                backend.combatant$physicalDevice().vkPhysicalDeviceProperties().limits().maxStorageBufferRange());
        if (descriptor.byteSize() > maxRange) {
            throw new IllegalArgumentException("Storage buffer exceeds Vulkan maxStorageBufferRange: size="
                    + descriptor.byteSize() + " max=" + maxRange + " label=" + descriptor.label());
        }
        return new VulkanStorageBuffer(backend, descriptor, stats);
    }

    @Override
    public RhiStorageImage createStorageImage(StorageImageDescriptor descriptor) {
        requireOpen();
        if (descriptor == null) throw new IllegalArgumentException("descriptor");
        validateStorageImageFormat(requireBackend(), descriptor);
        return RhiStorageImages.create(descriptor);
    }

    @Override
    public RhiComputePipeline createComputePipeline(ComputePipelineDescriptor descriptor) {
        requireOpen();
        if (descriptor == null) throw new IllegalArgumentException("descriptor");
        if (!RhiCapabilities.current().nativeComputeSubmission()) {
            throw new UnsupportedOperationException("Native Vulkan compute submission is unavailable");
        }
        validateResourceLayout(descriptor.resources(), "Vulkan compute");

        IVulkanBackendInfo backend = requireBackend();
        VulkanDevice ownerDevice = requireMojangDevice(backend);
        VkDevice device = backend.combatant$vkDevice();
        String source = CombatantShaderSources.loadNativeStage(
                Minecraft.getInstance().getResourceManager(), descriptor.shader(), CombatantShaderSources.COMPUTE_EXTENSION);
        ByteBuffer spirv = compiler.compile(descriptor.shader().toString(), source, shaderc_compute_shader);
        long shaderModule = 0L;
        long descriptorSetLayout = 0L;
        long pipelineLayout = 0L;
        long pipeline = 0L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            shaderModule = createShaderModule(device, spirv, stack, descriptor.label());
            descriptorSetLayout = createDescriptorSetLayout(device, descriptor.resources(), VK_SHADER_STAGE_COMPUTE_BIT, stack, descriptor.label());
            pipelineLayout = createPipelineLayout(device, descriptorSetLayout, stack, descriptor.label());

            VkPipelineShaderStageCreateInfo stage = VkPipelineShaderStageCreateInfo.calloc(stack)
                    .sType$Default()
                    .stage(VK_SHADER_STAGE_COMPUTE_BIT)
                    .module(shaderModule)
                    .pName(stack.UTF8("main"));
            VkComputePipelineCreateInfo.Buffer pipelineInfo = VkComputePipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0).sType$Default().stage(stage).layout(pipelineLayout);
            LongBuffer pPipeline = stack.mallocLong(1);
            check(vkCreateComputePipelines(device, 0L, pipelineInfo, null, pPipeline),
                    "vkCreateComputePipelines", descriptor.label());
            pipeline = pPipeline.get(0);
            return new VulkanComputePipeline(ownerDevice, device, descriptor,
                    descriptorSetLayout, pipelineLayout, pipeline);
        } catch (RuntimeException | Error t) {
            if (pipeline != 0L) vkDestroyPipeline(device, pipeline, null);
            if (pipelineLayout != 0L) vkDestroyPipelineLayout(device, pipelineLayout, null);
            if (descriptorSetLayout != 0L) vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
            throw t;
        } finally {
            if (shaderModule != 0L) vkDestroyShaderModule(device, shaderModule, null);
            MemoryUtil.memFree(spirv);
        }
    }

    @Override
    public RhiPatchPipeline createPatchPipeline(PatchPipelineDescriptor descriptor) {
        requireOpen();
        if (descriptor == null) throw new IllegalArgumentException("descriptor");
        RhiCapabilities capabilities = RhiCapabilities.current();
        if (!capabilities.nativeTessellationSubmission()) {
            throw new UnsupportedOperationException("Native Vulkan tessellation submission is unavailable");
        }
        if (descriptor.geometryShader() != null && !capabilities.nativeGeometrySubmission()) {
            throw new UnsupportedOperationException("Patch pipeline requests geometry stage but Vulkan geometry shaders are unavailable");
        }
        validateResourceLayout(descriptor.resources(), "Vulkan patch");

        IVulkanBackendInfo backend = requireBackend();
        int maxPatchSize = backend.combatant$physicalDevice()
                .vkPhysicalDeviceProperties().limits().maxTessellationPatchSize();
        if (descriptor.controlPoints() > maxPatchSize) {
            throw new IllegalArgumentException("Patch control-point count exceeds Vulkan device limit: requested="
                    + descriptor.controlPoints() + " max=" + maxPatchSize);
        }
        VulkanDevice ownerDevice = requireMojangDevice(backend);
        VkDevice device = backend.combatant$vkDevice();
        var resourceManager = Minecraft.getInstance().getResourceManager();

        List<StageModule> modules = new ArrayList<>(5);
        long descriptorSetLayout = 0L;
        long pipelineLayout = 0L;
        long pipeline = 0L;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            modules.add(compileStage(device, descriptor.vertexShader(), CombatantShaderSources.VERT_EXTENSION,
                    shaderc_vertex_shader, VK_SHADER_STAGE_VERTEX_BIT, stack));
            modules.add(compileStage(device, descriptor.tessControlShader(), CombatantShaderSources.TESS_CONTROL_EXTENSION,
                    shaderc_tess_control_shader, VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT, stack));
            modules.add(compileStage(device, descriptor.tessEvaluationShader(), CombatantShaderSources.TESS_EVALUATION_EXTENSION,
                    shaderc_tess_evaluation_shader, VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT, stack));
            if (descriptor.geometryShader() != null) {
                modules.add(compileStage(device, descriptor.geometryShader(), CombatantShaderSources.GEOMETRY_EXTENSION,
                        shaderc_geometry_shader, VK_SHADER_STAGE_GEOMETRY_BIT, stack));
            }
            modules.add(compileStage(device, descriptor.fragmentShader(), CombatantShaderSources.FRAG_EXTENSION,
                    shaderc_fragment_shader, VK_SHADER_STAGE_FRAGMENT_BIT, stack));

            int resourceStages = VK_SHADER_STAGE_VERTEX_BIT
                    | VK_SHADER_STAGE_TESSELLATION_CONTROL_BIT
                    | VK_SHADER_STAGE_TESSELLATION_EVALUATION_BIT
                    | VK_SHADER_STAGE_FRAGMENT_BIT;
            if (descriptor.geometryShader() != null) resourceStages |= VK_SHADER_STAGE_GEOMETRY_BIT;
            descriptorSetLayout = createDescriptorSetLayout(device, descriptor.resources(), resourceStages, stack, descriptor.label());
            pipelineLayout = createPipelineLayout(device, descriptorSetLayout, stack, descriptor.label());

            VkPipelineShaderStageCreateInfo.Buffer stageInfos = VkPipelineShaderStageCreateInfo.calloc(modules.size(), stack);
            for (int i = 0; i < modules.size(); i++) {
                StageModule module = modules.get(i);
                stageInfos.get(i).sType$Default()
                        .stage(module.stageFlags)
                        .module(module.module)
                        .pName(stack.UTF8("main"));
            }

            var format = descriptor.vertexLayout().nativeFormat();
            var elements = format.getElements();
            VkVertexInputAttributeDescription.Buffer attributes = VkVertexInputAttributeDescription.calloc(elements.size(), stack);
            for (int i = 0; i < elements.size(); i++) {
                var element = elements.get(i);
                attributes.get(i)
                        .location(i)
                        .binding(0)
                        .format(VulkanConst.toVk(element.format()))
                        .offset(element.offset());
            }
            VkVertexInputBindingDescription.Buffer bindings = VkVertexInputBindingDescription.calloc(1, stack);
            bindings.get(0)
                    .binding(0)
                    .stride(format.getVertexSize())
                    .inputRate(format.getStepRate() == 0 ? VK_VERTEX_INPUT_RATE_VERTEX : VK_VERTEX_INPUT_RATE_INSTANCE);
            VkPipelineVertexInputStateCreateInfo vertexInput = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pVertexAttributeDescriptions(attributes)
                    .pVertexBindingDescriptions(bindings);
            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .topology(VK_PRIMITIVE_TOPOLOGY_PATCH_LIST);
            VkPipelineTessellationStateCreateInfo tessellation = VkPipelineTessellationStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .patchControlPoints(descriptor.controlPoints());
            VkPipelineRasterizationStateCreateInfo raster = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .polygonMode(VK_POLYGON_MODE_FILL)
                    .cullMode(vkCullMode(descriptor.cullMode()))
                    .frontFace(VK_FRONT_FACE_CLOCKWISE)
                    .lineWidth(1.0f);
            VkPipelineMultisampleStateCreateInfo multisample = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .rasterizationSamples(vkSamples(descriptor.samples()));
            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType$Default();
            if (descriptor.depthMode() != AdvancedDepthMode.DISABLED) {
                depthStencil.depthTestEnable(true)
                        .depthWriteEnable(descriptor.depthMode() == AdvancedDepthMode.READ_WRITE_LEQUAL)
                        .depthCompareOp(VK_COMPARE_OP_LESS_OR_EQUAL);
            }

            VkPipelineColorBlendAttachmentState.Buffer colorAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack);
            configureBlend(colorAttachment.get(0), descriptor.blendMode());
            VkPipelineColorBlendStateCreateInfo blend = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pAttachments(colorAttachment);
            VkPipelineViewportStateCreateInfo viewport = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .viewportCount(1)
                    .scissorCount(1);
            VkPipelineDynamicStateCreateInfo dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType$Default()
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR));

            int colorVkFormat = VulkanConst.toVk(descriptor.colorFormat());
            VkPipelineRenderingCreateInfoKHR rendering = VkPipelineRenderingCreateInfoKHR.calloc(stack)
                    .sType$Default()
                    .colorAttachmentCount(1)
                    .pColorAttachmentFormats(stack.ints(colorVkFormat));
            if (descriptor.depthFormat() != null) rendering.depthAttachmentFormat(VulkanConst.toVk(descriptor.depthFormat()));

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack);
            pipelineInfo.get(0).sType$Default()
                    .pStages(stageInfos)
                    .pVertexInputState(vertexInput)
                    .pInputAssemblyState(inputAssembly)
                    .pTessellationState(tessellation)
                    .pRasterizationState(raster)
                    .pMultisampleState(multisample)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(blend)
                    .pViewportState(viewport)
                    .pDynamicState(dynamic)
                    .layout(pipelineLayout)
                    .pNext(rendering);
            LongBuffer pPipeline = stack.mallocLong(1);
            check(vkCreateGraphicsPipelines(device, 0L, pipelineInfo, null, pPipeline),
                    "vkCreateGraphicsPipelines(patch)", descriptor.label());
            pipeline = pPipeline.get(0);
            return new VulkanPatchPipeline(ownerDevice, device, descriptor,
                    descriptorSetLayout, pipelineLayout, pipeline);
        } catch (RuntimeException | Error t) {
            if (pipeline != 0L) vkDestroyPipeline(device, pipeline, null);
            if (pipelineLayout != 0L) vkDestroyPipelineLayout(device, pipelineLayout, null);
            if (descriptorSetLayout != 0L) vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
            throw t;
        } finally {
            for (StageModule module : modules) {
                if (module.module != 0L) vkDestroyShaderModule(device, module.module, null);
                MemoryUtil.memFree(module.spirv);
            }
        }
    }

    @Override
    public void dispatch(ComputeDispatchCommand command) {
        requireOpen();
        if (command == null) throw new IllegalArgumentException("command");
        if (!(command.pipeline() instanceof VulkanComputePipeline pipeline) || pipeline.closed) {
            throw new IllegalArgumentException("Compute pipeline does not belong to the active Vulkan backend");
        }
        validateBindings(pipeline.resources(), command.storageBindings(), command.sampledTextures(), command.storageImages());

        VulkanCommandEncoder encoder = currentEncoder();
        VkCommandBuffer commandBuffer = commandBuffer(encoder);
        vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, pipeline.pipeline);
        bindResourceDescriptors(encoder, commandBuffer, pipeline.device,
                pipeline.descriptorSetLayout, pipeline.pipelineLayout, VK_PIPELINE_BIND_POINT_COMPUTE,
                pipeline.resources(), command.storageBindings(), command.sampledTextures(), command.storageImages(), pipeline.label());
        vkCmdDispatch(commandBuffer, command.groupsX(), command.groupsY(), command.groupsZ());
        stats.computeDispatch(command.groupsX(), command.groupsY(), command.groupsZ(), command.storageBindings().size());
    }

    @Override
    public void barrier(RhiResourceBarrier barrier) {
        requireOpen();
        if (barrier == null) throw new IllegalArgumentException("barrier");
        VkCommandBuffer commandBuffer = commandBuffer(currentEncoder());

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkDependencyInfo dependency = VkDependencyInfo.calloc(stack).sType$Default();

            if (!barrier.buffers().isEmpty()) {
                VkBufferMemoryBarrier2.Buffer buffers = VkBufferMemoryBarrier2.calloc(barrier.buffers().size(), stack);
                for (int i = 0; i < barrier.buffers().size(); i++) {
                    RhiStorageBuffer rhiBuffer = barrier.buffers().get(i);
                    if (!(rhiBuffer instanceof VulkanStorageBuffer buffer)) {
                        throw new IllegalArgumentException("Barrier buffer does not belong to the active Vulkan backend");
                    }
                    buffers.get(i).sType$Default()
                            .srcStageMask(stageMask(barrier.sourceStage()))
                            .srcAccessMask(accessMask(barrier.sourceStage(), barrier.sourceAccess()))
                            .dstStageMask(stageMask(barrier.destinationStage()))
                            .dstAccessMask(accessMask(barrier.destinationStage(), barrier.destinationAccess()))
                            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .buffer(buffer.vkBuffer())
                            .offset(0L)
                            .size(VK_WHOLE_SIZE);
                }
                dependency.pBufferMemoryBarriers(buffers);
            }

            if (!barrier.images().isEmpty()) {
                VkImageMemoryBarrier2.Buffer images = VkImageMemoryBarrier2.calloc(barrier.images().size(), stack);
                for (int i = 0; i < barrier.images().size(); i++) {
                    RhiStorageImage rhiImage = barrier.images().get(i);
                    if (!(rhiImage.view() instanceof VulkanGpuTextureView view) || view.isClosed()) {
                        throw new IllegalArgumentException("Barrier image does not belong to the active Vulkan backend");
                    }
                    VulkanGpuTexture texture = view.texture();
                    if (texture == null || texture.isClosed()) {
                        throw new IllegalArgumentException("Barrier image texture is closed");
                    }
                    VkImageSubresourceRange range = images.get(i).subresourceRange();
                    range.aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                            .baseMipLevel(0)
                            .levelCount(1)
                            .baseArrayLayer(0)
                            .layerCount(1);

                    images.get(i).sType$Default()
                            .srcStageMask(stageMask(barrier.sourceStage()))
                            .srcAccessMask(accessMask(barrier.sourceStage(), barrier.sourceAccess()))
                            .dstStageMask(stageMask(barrier.destinationStage()))
                            .dstAccessMask(accessMask(barrier.destinationStage(), barrier.destinationAccess()))
                            .oldLayout(VK_IMAGE_LAYOUT_GENERAL)
                            .newLayout(VK_IMAGE_LAYOUT_GENERAL)
                            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                            .image(texture.vkImage());
                }
                dependency.pImageMemoryBarriers(images);
            }

            if (barrier.buffers().isEmpty() && barrier.images().isEmpty()) {
                VkMemoryBarrier2.Buffer memory = VkMemoryBarrier2.calloc(1, stack);
                memory.get(0).sType$Default()
                        .srcStageMask(stageMask(barrier.sourceStage()))
                        .srcAccessMask(accessMask(barrier.sourceStage(), barrier.sourceAccess()))
                        .dstStageMask(stageMask(barrier.destinationStage()))
                        .dstAccessMask(accessMask(barrier.destinationStage(), barrier.destinationAccess()));
                dependency.pMemoryBarriers(memory);
            }

            vkCmdPipelineBarrier2KHR(commandBuffer, dependency);
            stats.advancedShaderBarrier();
        }
    }

    @Override
    public void drawPatches(PatchDrawCommand command) {
        requireOpen();
        if (command == null) throw new IllegalArgumentException("command");
        if (!(command.pipeline() instanceof VulkanPatchPipeline pipeline) || pipeline.closed) {
            throw new IllegalArgumentException("Patch pipeline does not belong to the active Vulkan backend");
        }
        if (!(command.colorAttachment() instanceof VulkanGpuTextureView color) || color.isClosed()) {
            throw new IllegalArgumentException("Vulkan patch color target must be a live VulkanGpuTextureView");
        }
        VulkanGpuTextureView depth = null;
        if (command.depthAttachment() != null) {
            if (!(command.depthAttachment() instanceof VulkanGpuTextureView vkDepth) || vkDepth.isClosed()) {
                throw new IllegalArgumentException("Vulkan patch depth target must be a live VulkanGpuTextureView");
            }
            depth = vkDepth;
        }
        validatePatchTarget(pipeline.descriptor, color, depth);
        validateBindings(pipeline.resources(), command.storageBindings(), command.sampledTextures(), command.storageImages());

        GpuMeshHandle mesh = command.mesh();
        mesh.validateForDraw(command.label());
        if (!(mesh.vertexBuffer() instanceof VulkanGpuBuffer vertex)
                || !(mesh.indexBuffer() instanceof VulkanGpuBuffer index)) {
            throw new IllegalArgumentException("Patch mesh is not backed by Mojang VulkanGpuBuffer objects");
        }
        if (mesh.vertexStride() > 0
                && mesh.vertexStride() != pipeline.descriptor.vertexLayout().nativeFormat().getVertexSize()) {
            throw new IllegalArgumentException("Patch mesh stride does not match pipeline vertex layout");
        }

        VulkanCommandEncoder encoder = currentEncoder();
        VkCommandBuffer commandBuffer = commandBuffer(encoder);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkRenderingAttachmentInfo.Buffer colorAttachment = VkRenderingAttachmentInfo.calloc(1, stack);
            colorAttachment.get(0).sType$Default()
                    .imageView(color.vkImageView())
                    .imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                    .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                    .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
            VkRenderingInfo rendering = VkRenderingInfo.calloc(stack).sType$Default();
            rendering.renderArea().offset().set(0, 0);
            rendering.renderArea().extent().set(color.getWidth(0), color.getHeight(0));
            rendering.layerCount(1).pColorAttachments(colorAttachment);

            VkRenderingAttachmentInfo depthAttachment = null;
            if (pipeline.descriptor.depthMode() != AdvancedDepthMode.DISABLED) {
                if (depth == null) throw new IllegalArgumentException("Patch pipeline requires a depth attachment");
                depthAttachment = VkRenderingAttachmentInfo.calloc(stack).sType$Default()
                        .imageView(depth.vkImageView())
                        .imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                        .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
                        .storeOp(VK_ATTACHMENT_STORE_OP_STORE);
                rendering.pDepthAttachment(depthAttachment);
            }

            vkCmdBeginRenderingKHR(commandBuffer, rendering);
            try {
                VkViewport.Buffer viewport = VkViewport.calloc(1, stack);
                viewport.get(0).x(0.0f).y(0.0f)
                        .width(color.getWidth(0)).height(color.getHeight(0))
                        .minDepth(0.0f).maxDepth(1.0f);
                vkCmdSetViewport(commandBuffer, 0, viewport);
                VkRect2D.Buffer scissor = VkRect2D.calloc(1, stack);
                scissor.get(0).offset().set(0, 0);
                scissor.get(0).extent().set(color.getWidth(0), color.getHeight(0));
                vkCmdSetScissor(commandBuffer, 0, scissor);

                vkCmdBindPipeline(commandBuffer, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline.pipeline);
                bindResourceDescriptors(encoder, commandBuffer, pipeline.device,
                        pipeline.descriptorSetLayout, pipeline.pipelineLayout, VK_PIPELINE_BIND_POINT_GRAPHICS,
                        pipeline.resources(), command.storageBindings(), command.sampledTextures(), command.storageImages(), pipeline.label());

                vkCmdBindVertexBuffers(commandBuffer, 0,
                        stack.longs(vertex.vkBuffer()), stack.longs(0L));
                vkCmdBindIndexBuffer(commandBuffer, index.vkBuffer(), 0L,
                        mesh.indexType() == IndexType.SHORT ? VK_INDEX_TYPE_UINT16 : VK_INDEX_TYPE_UINT32);
                vkCmdDrawIndexed(commandBuffer, mesh.indexCount(), 1, mesh.firstIndex(), mesh.baseVertex(), 0);
                stats.drawCall();
            } finally {
                vkCmdEndRenderingKHR(commandBuffer);
            }
        }
    }

    @Override
    public void close() {
        if (closed) return;
        closed = true;
        compiler.close();
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Vulkan advanced shader backend is closed");
    }

    private static IVulkanBackendInfo requireBackend() {
        IVulkanBackendInfo backend = VulkanBackendAccess.current();
        if (backend == null || backend.combatant$vkDevice() == null) {
            throw new UnsupportedOperationException("Mojang Vulkan device bridge is unavailable");
        }
        return backend;
    }

    private static VulkanDevice requireMojangDevice(IVulkanBackendInfo backend) {
        if (!(backend instanceof VulkanDevice device)) {
            throw new IllegalStateException("Vulkan backend bridge is not the active Mojang VulkanDevice");
        }
        return device;
    }

    private static VulkanCommandEncoder currentEncoder() {
        return ((VulkanDevice) requireBackend()).createCommandEncoder();
    }

    private static VkCommandBuffer commandBuffer(VulkanCommandEncoder encoder) {
        if (!(encoder instanceof IVulkanCommandEncoderAccess access)) {
            throw new IllegalStateException("Vulkan command encoder bridge is unavailable");
        }
        if (access.combatant$renderPassActive()) {
            throw new IllegalStateException("Native advanced Vulkan work cannot be recorded inside an active Mojang render pass");
        }
        return access.combatant$commandBuffer();
    }

    private StageModule compileStage(VkDevice device,
                                     net.minecraft.resources.Identifier shader,
                                     String extension,
                                     int shadercKind,
                                     int vkStage,
                                     MemoryStack stack) {
        String source;
        if (CombatantShaderSources.VERT_EXTENSION.equals(extension)) {
            source = CombatantShaderSources.load(Minecraft.getInstance().getResourceManager(), shader,
                    com.mojang.blaze3d.shaders.ShaderType.VERTEX);
        } else if (CombatantShaderSources.FRAG_EXTENSION.equals(extension)) {
            source = CombatantShaderSources.load(Minecraft.getInstance().getResourceManager(), shader,
                    com.mojang.blaze3d.shaders.ShaderType.FRAGMENT);
        } else {
            source = CombatantShaderSources.loadNativeStage(Minecraft.getInstance().getResourceManager(), shader, extension);
        }
        ByteBuffer spirv = compiler.compile(shader.toString(), source, shadercKind);
        long module = createShaderModule(device, spirv, stack, shader.toString());
        return new StageModule(vkStage, module, spirv);
    }

    private static long createShaderModule(VkDevice device, ByteBuffer spirv, MemoryStack stack, String label) {
        VkShaderModuleCreateInfo moduleInfo = VkShaderModuleCreateInfo.calloc(stack)
                .sType$Default()
                .pCode(spirv);
        LongBuffer pModule = stack.mallocLong(1);
        check(vkCreateShaderModule(device, moduleInfo, null, pModule), "vkCreateShaderModule", label);
        return pModule.get(0);
    }

    private static long createDescriptorSetLayout(VkDevice device,
                                                  ShaderResourceLayout resources,
                                                  int stageFlags,
                                                  MemoryStack stack,
                                                  String label) {
        if (resources.slots().isEmpty()) return 0L;
        VkDescriptorSetLayoutBinding.Buffer bindings = VkDescriptorSetLayoutBinding.calloc(resources.slots().size(), stack);
        for (int i = 0; i < resources.slots().size(); i++) {
            ShaderResourceSlot slot = resources.slots().get(i);
            bindings.get(i)
                    .binding(slot.binding())
                    .descriptorType(descriptorType(slot.kind()))
                    .descriptorCount(1)
                    .stageFlags(stageFlags);
        }
        VkDescriptorSetLayoutCreateInfo layoutInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack)
                .sType$Default()
                .pBindings(bindings);
        LongBuffer pLayout = stack.mallocLong(1);
        check(vkCreateDescriptorSetLayout(device, layoutInfo, null, pLayout),
                "vkCreateDescriptorSetLayout", label);
        return pLayout.get(0);
    }

    private static long createPipelineLayout(VkDevice device,
                                             long descriptorSetLayout,
                                             MemoryStack stack,
                                             String label) {
        VkPipelineLayoutCreateInfo info = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default();
        if (descriptorSetLayout != 0L) info.pSetLayouts(stack.longs(descriptorSetLayout));
        LongBuffer pLayout = stack.mallocLong(1);
        check(vkCreatePipelineLayout(device, info, null, pLayout), "vkCreatePipelineLayout", label);
        return pLayout.get(0);
    }

    private void bindResourceDescriptors(VulkanCommandEncoder encoder,
                                         VkCommandBuffer commandBuffer,
                                         VkDevice device,
                                         long descriptorSetLayout,
                                         long pipelineLayout,
                                         int bindPoint,
                                         ShaderResourceLayout layout,
                                         List<StorageBinding> buffers,
                                         List<SampledTextureBinding> sampled,
                                         List<StorageImageBinding> images,
                                         String label) {
        if (descriptorSetLayout == 0L) return;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            long descriptorSet = allocateDescriptorSet(
                    encoder, device, descriptorSetLayout, layout, stack, label);

            int total = buffers.size() + sampled.size() + images.size();
            VkWriteDescriptorSet.Buffer writes = VkWriteDescriptorSet.calloc(total, stack);
            List<VkDescriptorBufferInfo.Buffer> bufferInfos = new ArrayList<>(buffers.size());
            List<VkDescriptorImageInfo.Buffer> imageInfos = new ArrayList<>(sampled.size() + images.size());
            int write = 0;
            long storageAlignment = storageBufferOffsetAlignment();
            long maxStorageRange = maxStorageBufferRange();
            for (StorageBinding binding : buffers) {
                if (!(binding.buffer() instanceof VulkanStorageBuffer buffer)) {
                    throw new IllegalArgumentException("Storage binding does not belong to the active Vulkan backend");
                }
                if (storageAlignment > 1L && (binding.offset() % storageAlignment) != 0L) {
                    throw new IllegalArgumentException("Storage binding offset is not aligned: binding="
                            + binding.binding() + " offset=" + binding.offset() + " alignment=" + storageAlignment);
                }
                if (binding.size() <= 0L || binding.size() > maxStorageRange) {
                    throw new IllegalArgumentException("Storage binding range is invalid for Vulkan: binding="
                            + binding.binding() + " size=" + binding.size() + " max=" + maxStorageRange);
                }
                VkDescriptorBufferInfo.Buffer info = VkDescriptorBufferInfo.calloc(1, stack);
                info.get(0).buffer(buffer.vkBuffer()).offset(binding.offset()).range(binding.size());
                bufferInfos.add(info);
                writes.get(write++).sType$Default()
                        .dstSet(descriptorSet)
                        .dstBinding(binding.binding())
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                        .descriptorCount(1)
                        .pBufferInfo(info);
            }
            for (SampledTextureBinding binding : sampled) {
                if (!(binding.texture() instanceof VulkanGpuTextureView texture) || texture.isClosed()) {
                    throw new IllegalArgumentException("Sampled texture does not belong to the active Vulkan backend");
                }
                if (!(binding.sampler() instanceof VulkanGpuSampler sampler)) {
                    throw new IllegalArgumentException("Sampler does not belong to the active Vulkan backend");
                }
                VkDescriptorImageInfo.Buffer info = VkDescriptorImageInfo.calloc(1, stack);
                info.get(0).sampler(sampler.vkSampler())
                        .imageView(texture.vkImageView())
                        .imageLayout(VK_IMAGE_LAYOUT_GENERAL);
                imageInfos.add(info);
                writes.get(write++).sType$Default()
                        .dstSet(descriptorSet)
                        .dstBinding(binding.binding())
                        .descriptorType(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                        .descriptorCount(1)
                        .pImageInfo(info);
            }
            for (StorageImageBinding binding : images) {
                if (!(binding.image().view() instanceof VulkanGpuTextureView texture) || texture.isClosed()) {
                    throw new IllegalArgumentException("Storage image does not belong to the active Vulkan backend");
                }
                VkDescriptorImageInfo.Buffer info = VkDescriptorImageInfo.calloc(1, stack);
                info.get(0).sampler(0L)
                        .imageView(texture.vkImageView())
                        .imageLayout(VK_IMAGE_LAYOUT_GENERAL);
                imageInfos.add(info);
                writes.get(write++).sType$Default()
                        .dstSet(descriptorSet)
                        .dstBinding(binding.binding())
                        .descriptorType(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                        .descriptorCount(1)
                        .pImageInfo(info);
            }
            vkUpdateDescriptorSets(device, writes, null);
            vkCmdBindDescriptorSets(commandBuffer, bindPoint, pipelineLayout, 0,
                    stack.longs(descriptorSet), null);
        }
    }

    private long allocateDescriptorSet(VulkanCommandEncoder encoder,
                                       VkDevice device,
                                       long descriptorSetLayout,
                                       ShaderResourceLayout layout,
                                       MemoryStack stack,
                                       String label) {
        if (!(encoder instanceof IVulkanCommandEncoderAccess access)) {
            throw new IllegalStateException("Vulkan command encoder bridge is unavailable");
        }

        long submitIndex = access.combatant$currentSubmitIndex();
        if (descriptorSubmitIndex != submitIndex) {
            descriptorSubmitIndex = submitIndex;
            descriptorPools.clear();
        }

        DescriptorPoolKey key = new DescriptorPoolKey(
                layout.count(ShaderResourceKind.STORAGE_BUFFER),
                layout.count(ShaderResourceKind.SAMPLED_TEXTURE),
                layout.count(ShaderResourceKind.STORAGE_IMAGE)
        );
        DescriptorPoolBatch batch = descriptorPools.get(key);
        if (batch == null || batch.allocatedSets >= batch.maxSets) {
            batch = createDescriptorPoolBatch(encoder, device, key, stack, label);
            descriptorPools.put(key, batch);
        }

        VkDescriptorSetAllocateInfo allocInfo = VkDescriptorSetAllocateInfo.calloc(stack)
                .sType$Default()
                .descriptorPool(batch.pool)
                .pSetLayouts(stack.longs(descriptorSetLayout));
        LongBuffer pSet = stack.mallocLong(1);
        check(vkAllocateDescriptorSets(device, allocInfo, pSet), "vkAllocateDescriptorSets", label);
        batch.allocatedSets++;
        return pSet.get(0);
    }

    private static DescriptorPoolBatch createDescriptorPoolBatch(VulkanCommandEncoder encoder,
                                                                 VkDevice device,
                                                                 DescriptorPoolKey key,
                                                                 MemoryStack stack,
                                                                 String label) {
        int typeCount = (key.storageBuffers > 0 ? 1 : 0)
                + (key.sampledTextures > 0 ? 1 : 0)
                + (key.storageImages > 0 ? 1 : 0);
        VkDescriptorPoolSize.Buffer sizes = VkDescriptorPoolSize.calloc(typeCount, stack);
        int index = 0;
        if (key.storageBuffers > 0) {
            sizes.get(index++).type(VK_DESCRIPTOR_TYPE_STORAGE_BUFFER)
                    .descriptorCount(key.storageBuffers * DESCRIPTOR_SETS_PER_POOL);
        }
        if (key.sampledTextures > 0) {
            sizes.get(index++).type(VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER)
                    .descriptorCount(key.sampledTextures * DESCRIPTOR_SETS_PER_POOL);
        }
        if (key.storageImages > 0) {
            sizes.get(index).type(VK_DESCRIPTOR_TYPE_STORAGE_IMAGE)
                    .descriptorCount(key.storageImages * DESCRIPTOR_SETS_PER_POOL);
        }

        VkDescriptorPoolCreateInfo poolInfo = VkDescriptorPoolCreateInfo.calloc(stack)
                .sType$Default()
                .maxSets(DESCRIPTOR_SETS_PER_POOL)
                .pPoolSizes(sizes);
        LongBuffer pPool = stack.mallocLong(1);
        check(vkCreateDescriptorPool(device, poolInfo, null, pPool), "vkCreateDescriptorPool", label);
        long pool = pPool.get(0);
        encoder.queueForDestroy((Destroyable) () -> vkDestroyDescriptorPool(device, pool, null));
        return new DescriptorPoolBatch(pool, DESCRIPTOR_SETS_PER_POOL);
    }

    private static int descriptorType(ShaderResourceKind kind) {
        return switch (kind) {
            case STORAGE_BUFFER -> VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
            case SAMPLED_TEXTURE -> VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            case STORAGE_IMAGE -> VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
        };
    }

    private static void validateResourceLayout(ShaderResourceLayout layout, String owner) {
        RhiCapabilities caps = RhiCapabilities.current();
        for (ShaderResourceSlot slot : layout.slots()) {
            if (slot.kind() == ShaderResourceKind.STORAGE_IMAGE && !caps.imageLoadStore()) {
                throw new UnsupportedOperationException(owner + " requires Vulkan image load/store support");
            }
        }
    }

    private static void validateBindings(ShaderResourceLayout layout,
                                         List<StorageBinding> buffers,
                                         List<SampledTextureBinding> sampled,
                                         List<StorageImageBinding> images) {
        for (StorageBinding binding : buffers) {
            ShaderResourceSlot slot = layout.slot(binding.binding());
            if (slot == null || slot.kind() != ShaderResourceKind.STORAGE_BUFFER) {
                throw new IllegalArgumentException("No STORAGE_BUFFER slot declared at binding " + binding.binding());
            }
            validateAccess(slot, binding.access(), binding.binding());
        }
        for (SampledTextureBinding binding : sampled) {
            ShaderResourceSlot slot = layout.slot(binding.binding());
            if (slot == null || slot.kind() != ShaderResourceKind.SAMPLED_TEXTURE) {
                throw new IllegalArgumentException("No SAMPLED_TEXTURE slot declared at binding " + binding.binding());
            }
        }
        for (StorageImageBinding binding : images) {
            ShaderResourceSlot slot = layout.slot(binding.binding());
            if (slot == null || slot.kind() != ShaderResourceKind.STORAGE_IMAGE) {
                throw new IllegalArgumentException("No STORAGE_IMAGE slot declared at binding " + binding.binding());
            }
            validateAccess(slot, binding.access(), binding.binding());
        }
        for (ShaderResourceSlot slot : layout.slots()) {
            boolean found = switch (slot.kind()) {
                case STORAGE_BUFFER -> buffers.stream().anyMatch(b -> b.binding() == slot.binding());
                case SAMPLED_TEXTURE -> sampled.stream().anyMatch(b -> b.binding() == slot.binding());
                case STORAGE_IMAGE -> images.stream().anyMatch(b -> b.binding() == slot.binding());
            };
            if (!found) throw new IllegalArgumentException("Missing " + slot.kind() + " binding " + slot.binding());
        }
    }

    private static void validateAccess(ShaderResourceSlot slot, StorageAccess actual, int binding) {
        if (!slot.access().allows(actual)) {
            throw new IllegalArgumentException("Binding " + binding + " access " + actual
                    + " exceeds declared access " + slot.access());
        }
    }

    private static long storageBufferOffsetAlignment() {
        IVulkanBackendInfo backend = requireBackend();
        return Math.max(1L, backend.combatant$physicalDevice()
                .vkPhysicalDeviceProperties().limits().minStorageBufferOffsetAlignment());
    }

    private static long maxStorageBufferRange() {
        IVulkanBackendInfo backend = requireBackend();
        return Integer.toUnsignedLong(backend.combatant$physicalDevice()
                .vkPhysicalDeviceProperties().limits().maxStorageBufferRange());
    }

    private static void validateStorageImageFormat(IVulkanBackendInfo backend, StorageImageDescriptor descriptor) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkFormatProperties props = VkFormatProperties.calloc(stack);
            vkGetPhysicalDeviceFormatProperties(backend.combatant$physicalDevice().vkPhysicalDevice(),
                    VulkanConst.toVk(descriptor.format()), props);
            int features = props.optimalTilingFeatures();
            if ((features & VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT) == 0) {
                throw new UnsupportedOperationException("Vulkan format is not storage-image capable: " + descriptor.format());
            }
            if (descriptor.sampled() && (features & VK_FORMAT_FEATURE_SAMPLED_IMAGE_BIT) == 0) {
                throw new UnsupportedOperationException("Vulkan format is not sampleable: " + descriptor.format());
            }
        }
    }

    private static void validatePatchTarget(PatchPipelineDescriptor descriptor,
                                            VulkanGpuTextureView color,
                                            VulkanGpuTextureView depth) {
        if (color.texture().getFormat() != descriptor.colorFormat()) {
            throw new IllegalArgumentException("Patch color format mismatch: expected=" + descriptor.colorFormat()
                    + " actual=" + color.texture().getFormat());
        }
        int colorSamples = color.texture() instanceof IMsaaTexture msaa ? msaa.combatant$getSamples() : 1;
        if (colorSamples != descriptor.samples()) {
            throw new IllegalArgumentException("Patch color sample mismatch: expected=" + descriptor.samples()
                    + " actual=" + colorSamples);
        }
        if (descriptor.depthMode() != AdvancedDepthMode.DISABLED) {
            if (depth == null) throw new IllegalArgumentException("Patch pipeline requires depth");
            if (depth.texture().getFormat() != descriptor.depthFormat()) {
                throw new IllegalArgumentException("Patch depth format mismatch: expected=" + descriptor.depthFormat()
                        + " actual=" + depth.texture().getFormat());
            }
            int depthSamples = depth.texture() instanceof IMsaaTexture msaa ? msaa.combatant$getSamples() : 1;
            if (depthSamples != descriptor.samples()) {
                throw new IllegalArgumentException("Patch depth sample mismatch: expected=" + descriptor.samples()
                        + " actual=" + depthSamples);
            }
        }
    }

    private static int vkSamples(int samples) {
        return switch (samples) {
            case 1 -> VK_SAMPLE_COUNT_1_BIT;
            case 2 -> VK_SAMPLE_COUNT_2_BIT;
            case 4 -> VK_SAMPLE_COUNT_4_BIT;
            case 8 -> VK_SAMPLE_COUNT_8_BIT;
            case 16 -> VK_SAMPLE_COUNT_16_BIT;
            default -> throw new IllegalArgumentException("Unsupported sample count: " + samples);
        };
    }

    private static int vkCullMode(AdvancedCullMode mode) {
        return switch (mode) {
            case NONE -> VK_CULL_MODE_NONE;
            case BACK -> VK_CULL_MODE_BACK_BIT;
            case FRONT -> VK_CULL_MODE_FRONT_BIT;
        };
    }

    private static void configureBlend(VkPipelineColorBlendAttachmentState state, AdvancedBlendMode mode) {
        state.colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT
                | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
        switch (mode) {
            case OPAQUE -> state.blendEnable(false);
            case ALPHA -> state.blendEnable(true)
                    .srcColorBlendFactor(VK_BLEND_FACTOR_SRC_ALPHA)
                    .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .alphaBlendOp(VK_BLEND_OP_ADD);
            case PREMULTIPLIED_ALPHA -> state.blendEnable(true)
                    .srcColorBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstColorBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE_MINUS_SRC_ALPHA)
                    .alphaBlendOp(VK_BLEND_OP_ADD);
            case ADDITIVE -> state.blendEnable(true)
                    .srcColorBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstColorBlendFactor(VK_BLEND_FACTOR_ONE)
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                    .dstAlphaBlendFactor(VK_BLEND_FACTOR_ONE)
                    .alphaBlendOp(VK_BLEND_OP_ADD);
        }
    }

    private static long stageMask(RhiResourceBarrier.Stage stage) {
        return switch (stage) {
            case COMPUTE -> VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR;
            case GRAPHICS -> VK_PIPELINE_STAGE_2_ALL_GRAPHICS_BIT_KHR;
            case INDIRECT -> VK_PIPELINE_STAGE_2_DRAW_INDIRECT_BIT_KHR;
            case TRANSFER -> VK_PIPELINE_STAGE_2_TRANSFER_BIT_KHR;
        };
    }

    private static long accessMask(RhiResourceBarrier.Stage stage, RhiResourceBarrier.Access access) {
        if (stage == RhiResourceBarrier.Stage.INDIRECT) return VK_ACCESS_2_INDIRECT_COMMAND_READ_BIT_KHR;
        if (stage == RhiResourceBarrier.Stage.TRANSFER) {
            return switch (access) {
                case READ -> VK_ACCESS_2_TRANSFER_READ_BIT_KHR;
                case WRITE -> VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR;
                case READ_WRITE -> VK_ACCESS_2_TRANSFER_READ_BIT_KHR | VK_ACCESS_2_TRANSFER_WRITE_BIT_KHR;
            };
        }
        return switch (access) {
            case READ -> VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR | VK_ACCESS_2_SHADER_SAMPLED_READ_BIT_KHR;
            case WRITE -> VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR;
            case READ_WRITE -> VK_ACCESS_2_SHADER_STORAGE_READ_BIT_KHR | VK_ACCESS_2_SHADER_SAMPLED_READ_BIT_KHR
                    | VK_ACCESS_2_SHADER_STORAGE_WRITE_BIT_KHR;
        };
    }

    private static void check(int result, String operation, String label) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(operation + " failed: VkResult=" + result + " label=" + label);
        }
    }

    private record DescriptorPoolKey(int storageBuffers, int sampledTextures, int storageImages) {}

    private static final class DescriptorPoolBatch {
        private final long pool;
        private final int maxSets;
        private int allocatedSets;

        private DescriptorPoolBatch(long pool, int maxSets) {
            this.pool = pool;
            this.maxSets = maxSets;
        }
    }

    private record StageModule(int stageFlags, long module, ByteBuffer spirv) {}

    private static final class VulkanComputePipeline implements RhiComputePipeline {
        private final VulkanDevice ownerDevice;
        private final VkDevice device;
        private final ComputePipelineDescriptor descriptor;
        private final long descriptorSetLayout;
        private final long pipelineLayout;
        private final long pipeline;
        private boolean closed;

        private VulkanComputePipeline(VulkanDevice ownerDevice,
                                      VkDevice device,
                                      ComputePipelineDescriptor descriptor,
                                      long descriptorSetLayout,
                                      long pipelineLayout,
                                      long pipeline) {
            this.ownerDevice = ownerDevice;
            this.device = device;
            this.descriptor = descriptor;
            this.descriptorSetLayout = descriptorSetLayout;
            this.pipelineLayout = pipelineLayout;
            this.pipeline = pipeline;
        }

        @Override public String label() { return descriptor.label(); }
        @Override public ShaderResourceLayout resources() { return descriptor.resources(); }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            ownerDevice.createCommandEncoder().queueForDestroy((Destroyable) () -> {
                vkDestroyPipeline(device, pipeline, null);
                vkDestroyPipelineLayout(device, pipelineLayout, null);
                if (descriptorSetLayout != 0L) vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
            });
        }
    }

    private static final class VulkanPatchPipeline implements RhiPatchPipeline {
        private final VulkanDevice ownerDevice;
        private final VkDevice device;
        private final PatchPipelineDescriptor descriptor;
        private final long descriptorSetLayout;
        private final long pipelineLayout;
        private final long pipeline;
        private boolean closed;

        private VulkanPatchPipeline(VulkanDevice ownerDevice,
                                    VkDevice device,
                                    PatchPipelineDescriptor descriptor,
                                    long descriptorSetLayout,
                                    long pipelineLayout,
                                    long pipeline) {
            this.ownerDevice = ownerDevice;
            this.device = device;
            this.descriptor = descriptor;
            this.descriptorSetLayout = descriptorSetLayout;
            this.pipelineLayout = pipelineLayout;
            this.pipeline = pipeline;
        }

        @Override public String label() { return descriptor.label(); }
        @Override public int controlPoints() { return descriptor.controlPoints(); }
        @Override public ShaderResourceLayout resources() { return descriptor.resources(); }

        @Override
        public void close() {
            if (closed) return;
            closed = true;
            ownerDevice.createCommandEncoder().queueForDestroy((Destroyable) () -> {
                vkDestroyPipeline(device, pipeline, null);
                vkDestroyPipelineLayout(device, pipelineLayout, null);
                if (descriptorSetLayout != 0L) vkDestroyDescriptorSetLayout(device, descriptorSetLayout, null);
            });
        }
    }

}
