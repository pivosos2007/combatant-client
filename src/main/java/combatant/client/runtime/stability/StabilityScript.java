/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.runtime.stability;

import com.google.gson.Gson;
import combatant.client.config.values.ConfigValue;
import combatant.client.features.command.CommandOutput;
import combatant.client.features.module.Module;
import combatant.client.features.module.ModuleManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.multiplayer.ServerData;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Line-based test scenario for anticheat runs on a local server ({@code combatant/stability/<name>.txt}).
 * Every step is logged with a wall-clock timestamp to {@code combatant/stability/script.log}, so violations
 * printed by the server (e.g. Grim verbose output) can be matched to the step that caused them.
 *
 * <pre>
 * cmd give @s end_crystal 64      server command (no slash)
 * chat hello                      chat message
 * wait 40                         ticks
 * on autocrystal / off autocrystal
 * set autocrystal target {"players_only":false}   module config value, JSON as stored in profiles
 * key forward on                  forward|back|left|right|jump|sneak|sprint|use|attack
 * look 90 10                      yaw pitch (client view)
 * mark crystal-start              label in the log
 * probe                           logs position, speed, gliding and held items without sending anything
 * screenshot storageesp-glow      saves screenshots/storageesp-glow.png
 * </pre>
 *
 * Only runs in singleplayer or on a loopback server; held keys and enabled modules are released when it ends.
 */
public final class StabilityScript {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
    private static final Gson GSON = new Gson();
    private static final int MESSAGE_SPACING_TICKS = 5;

    private static boolean registered;
    private static Deque<String> steps = new ArrayDeque<>();
    private static int waitTicks;
    private static boolean running;
    private static boolean quitWhenDone;

    private StabilityScript() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(StabilityScript::tick);
    }

    public static boolean running() {
        return running;
    }

    public static boolean allowedHere(Minecraft mc) {
        if (mc.player == null || mc.level == null) return false;
        if (mc.hasSingleplayerServer()) return true;
        ServerData server = mc.getCurrentServer();
        if (server == null || server.ip == null) return false;
        String host = server.ip.toLowerCase(Locale.ROOT);
        int colon = host.lastIndexOf(':');
        if (colon > 0 && host.indexOf(':') == colon) host = host.substring(0, colon);
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1");
    }

    /** Starts {@code combatant/stability/<name>.txt}; returns an error message when it cannot start. */
    public static String start(String name, boolean quit) {
        Minecraft mc = Minecraft.getInstance();
        if (running) return "A stability script is already running.";
        if (!allowedHere(mc)) return "Stability scripts only run in singleplayer or on a localhost server.";
        Path file = ModuleStabilityRun.directory().resolve(name.endsWith(".txt") ? name : name + ".txt");
        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            steps = new ArrayDeque<>();
            for (String line : lines) {
                String trimmed = line.strip();
                if (!trimmed.isEmpty() && !trimmed.startsWith("#")) steps.add(trimmed);
            }
        } catch (IOException e) {
            return "Cannot read " + file + ": " + e.getMessage();
        }
        waitTicks = 0;
        quitWhenDone = quit;
        running = true;
        log("START " + file.getFileName() + " on " + describeServer(mc));
        return null;
    }

    public static void stop(String reason) {
        if (!running) return;
        running = false;
        steps.clear();
        releaseKeys(Minecraft.getInstance());
        log("END " + reason);
        CommandOutput.send("Stability script " + reason + ". Log: " + logFile());
        if (quitWhenDone) Minecraft.getInstance().stop();
    }

    private static void tick(Minecraft mc) {
        if (!running) return;
        if (mc.player == null || mc.level == null) {
            stop("left the world");
            return;
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        while (running && waitTicks == 0 && !steps.isEmpty()) {
            String step = steps.pollFirst();
            try {
                execute(mc, step);
            } catch (RuntimeException e) {
                log("ERROR " + step + " -> " + e);
            }
        }
        if (running && waitTicks == 0 && steps.isEmpty()) stop("complete");
    }

    private static void execute(Minecraft mc, String step) {
        String[] parts = step.split("\\s+", 4);
        String op = parts[0].toLowerCase(Locale.ROOT);
        log(step);
        switch (op) {
            // Vanilla kicks for spam above 10 messages in quick succession; space them out.
            case "cmd" -> {
                mc.getConnection().sendCommand(step.substring(4).strip());
                waitTicks = MESSAGE_SPACING_TICKS;
            }
            case "chat" -> {
                mc.getConnection().sendChat(step.substring(5).strip());
                waitTicks = MESSAGE_SPACING_TICKS;
            }
            case "wait" -> waitTicks = Math.max(1, Integer.parseInt(parts[1]));
            case "on", "off" -> {
                Module module = requireModule(parts[1]);
                module.setEnabled(op.equals("on"));
                if (module.isEnabled() != op.equals("on")) log("WARN " + parts[1] + " did not turn " + op);
            }
            case "set" -> {
                Module module = requireModule(parts[1]);
                ConfigValue<?> value = module.getConfigValue(parts[2]);
                if (value == null) throw new IllegalArgumentException("no config value " + parts[2]);
                Object json = GSON.fromJson(parts[3], Object.class);
                if (json instanceof Map<?, ?> partial && value.toJson() instanceof Map<?, ?> current) {
                    @SuppressWarnings("unchecked")
                    Map<Object, Object> merged = new java.util.LinkedHashMap<>((Map<Object, Object>) current);
                    merged.putAll(partial);
                    json = merged;
                }
                value.fromJson(json);
                log("  " + parts[1] + "." + parts[2] + " = " + GSON.toJson(value.toJson()));
            }
            case "key" -> key(mc, parts[1]).setDown(parts[2].equalsIgnoreCase("on"));
            case "look" -> {
                mc.player.setYRot(Float.parseFloat(parts[1]));
                mc.player.setXRot(Math.max(-90.0f, Math.min(90.0f, Float.parseFloat(parts[2]))));
            }
            case "mark" -> {
            }
            case "probe" -> {
                var p = mc.player;
                log(String.format(Locale.ROOT, "  pos=%.2f %.2f %.2f speed=%.3f gliding=%s main=%s off=%s rockets=%d",
                        p.getX(), p.getY(), p.getZ(), p.getDeltaMovement().length(), p.isFallFlying(),
                        p.getMainHandItem().getItem(), p.getOffhandItem().getItem(),
                        p.getInventory().countItem(net.minecraft.world.item.Items.FIREWORK_ROCKET)));
            }
            case "screenshot" -> Screenshot.grab(mc.gameDirectory, parts[1] + ".png", mc.gameRenderer.mainRenderTarget(), 1,
                    message -> log("  " + message.getString()));
            default -> throw new IllegalArgumentException("unknown step");
        }
    }

    private static Module requireModule(String name) {
        Module module = ModuleManager.get(name);
        if (module == null) throw new IllegalArgumentException("no module " + name);
        return module;
    }

    private static KeyMapping key(Minecraft mc, String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "forward" -> mc.options.keyUp;
            case "back" -> mc.options.keyDown;
            case "left" -> mc.options.keyLeft;
            case "right" -> mc.options.keyRight;
            case "jump" -> mc.options.keyJump;
            case "sneak" -> mc.options.keyShift;
            case "sprint" -> mc.options.keySprint;
            case "use" -> mc.options.keyUse;
            case "attack" -> mc.options.keyAttack;
            default -> throw new IllegalArgumentException("unknown key " + name);
        };
    }

    private static void releaseKeys(Minecraft mc) {
        if (mc.options == null) return;
        for (KeyMapping mapping : new KeyMapping[]{mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft,
                mc.options.keyRight, mc.options.keyJump, mc.options.keyShift, mc.options.keySprint,
                mc.options.keyUse, mc.options.keyAttack}) {
            mapping.setDown(false);
        }
    }

    private static String describeServer(Minecraft mc) {
        if (mc.hasSingleplayerServer()) return "singleplayer";
        ServerData server = mc.getCurrentServer();
        return server != null ? server.ip : "?";
    }

    private static Path logFile() {
        return ModuleStabilityRun.directory().resolve("script.log");
    }

    private static void log(String line) {
        try {
            Files.createDirectories(ModuleStabilityRun.directory());
            Files.writeString(logFile(), LocalTime.now().format(TIME) + ' ' + line + '\n', StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }
}
