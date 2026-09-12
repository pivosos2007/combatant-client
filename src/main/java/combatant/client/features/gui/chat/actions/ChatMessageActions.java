package combatant.client.features.gui.chat.actions;

import combatant.client.features.command.CommandOutput;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.Collections;
import java.util.Map;
import java.util.List;
import java.util.WeakHashMap;

/** Client-side adapter. Other modules depend on this API, not on BetterChatRenderer or ErrorHandler. */
public enum ChatMessageActions {
    ;
    private static final MessageActionRegistry REGISTRY = new MessageActionRegistry();
    private static final Map<Component, String> BINDINGS = Collections.synchronizedMap(new WeakHashMap<>());
    public static MessageActionRegistry registry() { return REGISTRY; }
    public static MessageActionRegistry.Registration register(String owner, MessageActionRegistry.Provider provider) {
        return MessageActionProviderIsolation.register(REGISTRY, owner, provider);
    }
    public static List<MessageActionRegistry.OfferedAction> actions(String token) {
        return MessageActionProviderIsolation.actions(REGISTRY, token);
    }
    public static MessageActionRegistry.Context publish(String type, Map<String, String> data) {
        return REGISTRY.publish(type, data);
    }
    public static void send(Component body, CommandOutput.Tone tone, MessageActionRegistry.Context context) {
        CommandOutput.send(body, tone, context);
    }
    public static void bind(Component component, String token) {
        if (component != null && token != null) BINDINGS.put(component, token);
    }
    public static String tokenOf(Component component) { return component == null ? null : BINDINGS.get(component); }
    public static void copyBinding(Component from, Component to) { bind(to, tokenOf(from)); }
    public static MutableComponent button(String token, String qualifiedId, String label, int color) {
        return Component.literal(label).withStyle(style -> style.withColor(color & 0xFFFFFF).withUnderlined(true)
                .withClickEvent(new ClickEvent.RunCommand(command(token, qualifiedId))));
    }
    public static String command(String token, String qualifiedId) {
        return "@chataction " + token + " " + qualifiedId;
    }
    public static boolean isLocalActionCommand(String command) {
        return command != null && command.startsWith("@chataction ");
    }
    public static MessageActionRegistry.Result invoke(String token, String qualifiedId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || !mc.isSameThread()) return MessageActionRegistry.Result.UNAVAILABLE;
        return MessageActionProviderIsolation.invoke(REGISTRY, token, qualifiedId);
    }
    public static void clearSession() {
        REGISTRY.clearMessages();
        BINDINGS.clear();
    }
    /** Releases provider callbacks and their isolation keys during client runtime replacement. */
    public static void shutdown() {
        MessageActionProviderIsolation.shutdown();
        clearSession();
    }
    /** Remove executable local tokens before writing history; preserve ordinary server click events. */
    public static Component persistentCopy(Component source) {
        if (source == null) return Component.empty();
        // plainCopy preserves the contents without sharing the original sibling list.
        MutableComponent copy = source.plainCopy();
        Style style = source.getStyle();
        if (style.getClickEvent() instanceof ClickEvent.RunCommand run && isLocalActionCommand(run.command()))
            style = style.withClickEvent(null);
        copy.setStyle(style);
        for (Component sibling : source.getSiblings()) copy.append(persistentCopy(sibling));
        return copy;
    }
}
