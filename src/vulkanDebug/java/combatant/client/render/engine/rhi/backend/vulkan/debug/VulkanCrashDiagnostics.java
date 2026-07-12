package combatant.client.render.engine.rhi.backend.vulkan.debug;

import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.vulkan.VulkanInstance;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import net.minecraft.client.Minecraft;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkDebugUtilsMessengerCallbackDataEXT;
import org.lwjgl.vulkan.VkPhysicalDeviceLimits;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import combatant.client.render.engine.rhi.backend.vulkan.util.VulkanRuntimeGuards;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Opt-in Vulkan diagnostics. Compiled only when Gradle property combatantVulkanDebug=true is used.
 */
public enum VulkanCrashDiagnostics {
    ;

    public static final boolean FORCE_DRIVER_DEBUG = bool("combatant.vulkan.debug.forceDriverDebug", true);
    public static final boolean VERBOSE_DRAW_EVENTS = bool("combatant.vulkan.debug.verboseDraw", false);
    public static final long MAX_FILE_BYTES = longProp("combatant.vulkan.debug.maxBytes", 8L * 1024L * 1024L);
    public static final int MAX_EVENT_LINES = intProp("combatant.vulkan.debug.maxEventLines", 256);

    private static final Logger LOGGER = LoggerFactory.getLogger("Combatant/VulkanDiag");
    private static final String DIAGNOSTIC_FILE_NAME = "combatant-vulkan-diagnostics.log";
    private static final Object FILE_LOCK = new Object();
    private static final ConcurrentHashMap<String, AtomicInteger> EVENT_COUNTS = new ConcurrentHashMap<>();
    private static final AtomicLong WRITTEN_BYTES = new AtomicLong();
    private static volatile Path resolvedDiagnosticLog;
    private static volatile boolean headerWritten;
    private static volatile boolean truncated;

    public static GpuDebugOptions forceDebugOptions(GpuDebugOptions options) {
        if (!FORCE_DRIVER_DEBUG) return options;
        int logLevel = options == null ? 4 : Math.max(options.logLevel(), 4);
        boolean labelsOriginally = options != null && options.useLabels();
        boolean validationOriginally = options != null && options.useValidationLayers();
        boolean labels = labelsOriginally && !VulkanRuntimeGuards.FORCE_DISABLE_VULKAN_DEBUG_LABELS;
        boolean validation = true;
        GpuDebugOptions forced = new GpuDebugOptions(logLevel, true, labels, validation);
        breadcrumb("debug-options.forced",
                "original", options,
                "forced", forced,
                "labelsOriginally", labelsOriginally,
                "labelsForcedOff", VulkanRuntimeGuards.FORCE_DISABLE_VULKAN_DEBUG_LABELS,
                "validationOriginally", validationOriginally,
                "validationEnabled", validation);
        LOGGER.warn("[Combatant/VulkanDiag] forcing Vulkan debug utils + validation layers: {}", forced);
        return forced;
    }

    public static void dumpDevice(VulkanInstance instance,
                                  VulkanPhysicalDevice physicalDevice,
                                  Set<String> requestedDeviceExtensions) {
        if (physicalDevice == null) {
            breadcrumb("device.dump.skipped", "reason", "physicalDevice=null");
            return;
        }
        try {
            VkPhysicalDeviceProperties props = physicalDevice.vkPhysicalDeviceProperties();
            VkPhysicalDeviceLimits limits = props == null ? null : props.limits();
            String apiVersion = props == null ? "unknown" : version(props.apiVersion());
            String driverVersion = props == null ? "unknown" : version(props.driverVersion());
            String instanceExtensions = instance == null ? "<no-instance>" : join(instance.getEnabledExtensions());
            String deviceExtensions = join(requestedDeviceExtensions);
            String sampleCounts = limits == null ? "unknown" : String.format(
                    "color=0x%X depth=0x%X stencil=0x%X noAttachments=0x%X",
                    limits.framebufferColorSampleCounts(),
                    limits.framebufferDepthSampleCounts(),
                    limits.framebufferStencilSampleCounts(),
                    limits.framebufferNoAttachmentsSampleCounts()
            );
            String queues = String.format("graphics=%s compute=%s transfer=%s",
                    physicalDevice.graphicsQueueFamilyAndIndex(),
                    physicalDevice.computeQueueFamilyAndIndex(),
                    physicalDevice.transferQueueFamilyAndIndex());
            breadcrumb("device.dump",
                    "device", physicalDevice.deviceName(),
                    "vendor", physicalDevice.vendorName(),
                    "type", physicalDevice.deviceType(),
                    "driverInfo", physicalDevice.driverInfo(),
                    "apiVersion", apiVersion,
                    "driverVersionRaw", driverVersion,
                    "vkVendorId", props == null ? "unknown" : props.vendorID(),
                    "vkDeviceId", props == null ? "unknown" : props.deviceID(),
                    "debugUtilsEnabled", instance != null && instance.debug() != null && instance.debug().enabled(),
                    "instanceExtensions", instanceExtensions,
                    "deviceExtensions", deviceExtensions,
                    "sampleCounts", sampleCounts,
                    "queues", queues,
                    "stencil", "runtime-failsafe-only",
                    "msaa", "config-and-capability-controlled",
                    "hardDisableLabels", VulkanRuntimeGuards.FORCE_DISABLE_VULKAN_DEBUG_LABELS,
                    "hardDisableCheckpoints", VulkanRuntimeGuards.FORCE_DISABLE_VULKAN_CHECKPOINTS,
                    "maxBytes", MAX_FILE_BYTES,
                    "verboseDraw", VERBOSE_DRAW_EVENTS,
                    "maxEventLines", MAX_EVENT_LINES);
        } catch (Throwable t) {
            breadcrumb("device.dump.failed", "error", throwable(t));
        }
    }

    public static void driverDebugMessage(int severity, int type, long callbackData) {
        if (callbackData == 0L) {
            breadcrumbQuiet("driver.debug.message", "severity", severityName(severity), "type", typeName(type), "message", "<null-callback-data>");
            return;
        }
        try {
            VkDebugUtilsMessengerCallbackDataEXT data = VkDebugUtilsMessengerCallbackDataEXT.create(callbackData);
            breadcrumbQuiet("driver.debug.message",
                    "severity", severityName(severity),
                    "type", typeName(type),
                    "id", data.pMessageIdNameString(),
                    "number", data.messageIdNumber(),
                    "message", sanitize(data.pMessageString()));
        } catch (Throwable t) {
            breadcrumbQuiet("driver.debug.message.failed",
                    "severity", severity,
                    "type", type,
                    "error", throwable(t));
        }
    }

    public static void breadcrumb(String event, Object... keyValues) {
        String line = formatLine(event, keyValues);
        LOGGER.warn(line);
        writeLine(event, line);
    }

    public static void breadcrumbQuiet(String event, Object... keyValues) {
        if (isNoisyRenderEvent(event) && !VERBOSE_DRAW_EVENTS) return;
        writeLine(event, formatLine(event, keyValues));
    }

    public static void breadcrumbDraw(String event, Object... keyValues) {
        if (!VERBOSE_DRAW_EVENTS) return;
        writeLine(event, formatLine(event, keyValues));
    }

    public static String throwable(Throwable t) {
        if (t == null) return "null";
        return t.getClass().getName() + ": " + String.valueOf(t.getMessage());
    }

    private static boolean isNoisyRenderEvent(String event) {
        if (event == null) return false;
        return event.startsWith("renderpass.draw")
                || event.startsWith("renderpass.multidraw")
                || event.startsWith("renderpass.bindtexture")
                || event.startsWith("renderpass.uniform")
                || event.startsWith("renderpass.vertexbuffer")
                || event.startsWith("renderpass.indexbuffer")
                || event.startsWith("renderpass.scissor")
                || event.startsWith("renderpass.pushdescriptors");
    }

    private static boolean allowEvent(String event) {
        if (event == null || MAX_EVENT_LINES <= 0) return true;
        AtomicInteger counter = EVENT_COUNTS.computeIfAbsent(event, ignored -> new AtomicInteger());
        int count = counter.incrementAndGet();
        if (count <= MAX_EVENT_LINES) return true;
        if (count == MAX_EVENT_LINES + 1) {
            writeLineDirect(formatLine("event.suppressed", "event", event, "limit", MAX_EVENT_LINES));
        }
        return false;
    }

    private static String formatLine(String event, Object... keyValues) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('[').append(Instant.now()).append("] ").append(event == null ? "<null-event>" : event);
        if (keyValues != null) {
            for (int i = 0; i < keyValues.length; i += 2) {
                Object key = keyValues[i];
                Object value = i + 1 < keyValues.length ? keyValues[i + 1] : "<missing>";
                sb.append(' ').append(String.valueOf(key)).append('=').append(sanitize(String.valueOf(value)));
            }
        }
        return sb.toString();
    }

    private static String sanitize(String value) {
        if (value == null) return "null";
        return value.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ');
    }

    private static String join(Collection<String> values) {
        if (values == null || values.isEmpty()) return "<empty>";
        StringJoiner joiner = new StringJoiner(",");
        for (String value : values) joiner.add(value);
        return joiner.toString();
    }

    private static String version(int version) {
        return VK12.VK_VERSION_MAJOR(version) + "." + VK12.VK_VERSION_MINOR(version) + "." + VK12.VK_VERSION_PATCH(version)
                + " (0x" + Integer.toHexString(version) + ")";
    }

    private static String severityName(int severity) {
        if ((severity & 0x1000) != 0) return "ERROR(0x" + Integer.toHexString(severity) + ")";
        if ((severity & 0x0100) != 0) return "WARNING(0x" + Integer.toHexString(severity) + ")";
        if ((severity & 0x0010) != 0) return "INFO(0x" + Integer.toHexString(severity) + ")";
        if ((severity & 0x0001) != 0) return "VERBOSE(0x" + Integer.toHexString(severity) + ")";
        return "UNKNOWN(0x" + Integer.toHexString(severity) + ")";
    }

    private static String typeName(int type) {
        StringJoiner joiner = new StringJoiner("|");
        if ((type & 0x1) != 0) joiner.add("GENERAL");
        if ((type & 0x2) != 0) joiner.add("VALIDATION");
        if ((type & 0x4) != 0) joiner.add("PERFORMANCE");
        String joined = joiner.toString();
        return joined.isEmpty() ? "UNKNOWN(0x" + Integer.toHexString(type) + ")" : joined + "(0x" + Integer.toHexString(type) + ")";
    }

    private static Path diagnosticLogPath() {
        Path cached = resolvedDiagnosticLog;
        if (cached != null) return cached;
        Path resolved = resolveMinecraftLogsPath();
        resolvedDiagnosticLog = resolved;
        return resolved;
    }

    private static Path resolveMinecraftLogsPath() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft != null && minecraft.gameDirectory != null) {
                return minecraft.gameDirectory.toPath().resolve("logs").resolve(DIAGNOSTIC_FILE_NAME);
            }
        } catch (Throwable ignored) {
            // Vulkan init can run early enough that Minecraft may not be fully accessible.
        }
        return Path.of("logs", DIAGNOSTIC_FILE_NAME);
    }

    private static void writeLine(String event, String line) {
        if (!allowEvent(event)) return;
        writeLineDirect(line);
    }

    private static void writeLineDirect(String line) {
        synchronized (FILE_LOCK) {
            try {
                Path log = diagnosticLogPath();
                Path parent = log.getParent();
                if (parent != null) Files.createDirectories(parent);
                if (!headerWritten) {
                    String header = "=== Combatant Vulkan diagnostics ===\n" +
                            "modId=simplefullbright\n" +
                            "entrypoint=combatant.client.Combatant\n" +
                            "path=" + log.toAbsolutePath() + '\n' +
                            "stencil=runtime-failsafe-only" + '\n' +
                            "msaa=config-and-capability-controlled" + '\n' +
                            "hardDisableLabels=" + VulkanRuntimeGuards.FORCE_DISABLE_VULKAN_DEBUG_LABELS + '\n' +
                            "hardDisableCheckpoints=" + VulkanRuntimeGuards.FORCE_DISABLE_VULKAN_CHECKPOINTS + '\n' +
                            "maxBytes=" + MAX_FILE_BYTES + '\n' +
                            "maxEventLines=" + MAX_EVENT_LINES + '\n' +
                            "verboseDraw=" + VERBOSE_DRAW_EVENTS + '\n';
                    Files.writeString(log, header,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE);
                    WRITTEN_BYTES.set(header.getBytes(StandardCharsets.UTF_8).length);
                    headerWritten = true;
                }
                if (MAX_FILE_BYTES > 0 && WRITTEN_BYTES.get() >= MAX_FILE_BYTES) {
                    if (!truncated) {
                        String marker = formatLine("file.truncated", "maxBytes", MAX_FILE_BYTES) + System.lineSeparator();
                        Files.writeString(log, marker, StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE);
                        WRITTEN_BYTES.addAndGet(marker.getBytes(StandardCharsets.UTF_8).length);
                        truncated = true;
                    }
                    return;
                }
                String out = line + System.lineSeparator();
                Files.writeString(log, out,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND,
                        StandardOpenOption.WRITE);
                WRITTEN_BYTES.addAndGet(out.getBytes(StandardCharsets.UTF_8).length);
            } catch (IOException ignored) {
                // The normal Minecraft log still receives LOGGER output for explicit breadcrumb(...).
            }
        }
    }

    private static boolean bool(String key, boolean fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) return fallback;
        return Boolean.parseBoolean(value);
    }

    private static int intProp(String key, int fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static long longProp(String key, long fallback) {
        String value = System.getProperty(key);
        if (value == null || value.isBlank()) return fallback;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
