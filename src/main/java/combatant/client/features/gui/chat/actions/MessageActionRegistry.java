package combatant.client.features.gui.chat.actions;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Local, bounded, transport-independent message action broker. No Minecraft or renderer dependency. */
public final class MessageActionRegistry {
    public enum Tone { NORMAL, WARNING, DANGER }
    public enum Result { EXECUTED, UNAVAILABLE, DISABLED }
    public record Context(String token, String type, Map<String, String> data) {
        public Context {
            Objects.requireNonNull(token);
            Objects.requireNonNull(type);
            data = Map.copyOf(data);
        }
        public String data(String key) { return data.get(key); }
    }
    public record Action(String id, String label, Tone tone, boolean enabled) {
        public Action {
            Objects.requireNonNull(id);
            Objects.requireNonNull(label);
            Objects.requireNonNull(tone);
        }
    }
    public interface Provider {
        List<Action> actions(Context context);
        void execute(Context context, String actionId);
    }
    public record OfferedAction(String owner, Action action) {
        public String qualifiedId() { return owner + ":" + action.id(); }
    }
    public interface Registration extends AutoCloseable { @Override void close(); }
    private record Entry(Context context, long createdAt) { }
    private static final int MAX_MESSAGES = 512;
    private static final long TTL_MS = 30L * 60_000L;
    private final SecureRandom random = new SecureRandom();
    private final LinkedHashMap<String, Entry> messages = new LinkedHashMap<>();
    private final LinkedHashMap<String, Provider> providers = new LinkedHashMap<>();

    public synchronized Registration register(String owner, Provider provider) {
        requireId(owner);
        Objects.requireNonNull(provider);
        if (providers.putIfAbsent(owner, provider) != null)
            throw new IllegalArgumentException("Duplicate message action provider: " + owner);
        return () -> {
            synchronized (MessageActionRegistry.this) { providers.remove(owner, provider); }
        };
    }
    public synchronized Context publish(String type, Map<String, String> data) {
        requireId(type);
        Objects.requireNonNull(data);
        prune();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        Context context = new Context(token, type, data);
        messages.put(token, new Entry(context, System.currentTimeMillis()));
        prune();
        return context;
    }
    public synchronized Context resolve(String token) {
        if (token == null) return null;
        prune();
        Entry entry = messages.get(token);
        return entry == null ? null : entry.context();
    }
    public synchronized List<OfferedAction> actions(String token) {
        Context context = resolve(token);
        if (context == null) return List.of();
        List<OfferedAction> result = new ArrayList<>();
        for (var entry : List.copyOf(providers.entrySet())) {
            List<Action> actions = entry.getValue().actions(context);
            if (actions == null) continue;
            for (Action action : actions) {
                if (action == null) continue;
                requireId(action.id());
                result.add(new OfferedAction(entry.getKey(), action));
                if (result.size() >= 32) return List.copyOf(result);
            }
        }
        return List.copyOf(result);
    }
    /** Re-evaluates the provider's current policy on every invocation. Never executes a serialized callback. */
    public Result invoke(String token, String qualifiedId) {
        Context context;
        Provider provider;
        String actionId;
        synchronized (this) {
            context = resolve(token);
            if (context == null || qualifiedId == null) return Result.UNAVAILABLE;
            int split = qualifiedId.lastIndexOf(':');
            if (split <= 0 || split == qualifiedId.length() - 1) return Result.UNAVAILABLE;
            provider = providers.get(qualifiedId.substring(0, split));
            actionId = qualifiedId.substring(split + 1);
            if (provider == null) return Result.UNAVAILABLE;
        }
        List<Action> offered = provider.actions(context);
        if (offered == null) return Result.UNAVAILABLE;
        for (Action action : offered) {
            if (action != null && action.id().equals(actionId)) {
                if (!action.enabled()) return Result.DISABLED;
                synchronized (this) {
                    if (resolve(token) == null || providers.get(qualifiedId.substring(0, qualifiedId.lastIndexOf(':'))) != provider)
                        return Result.UNAVAILABLE;
                }
                provider.execute(context, actionId);
                return Result.EXECUTED;
            }
        }
        return Result.UNAVAILABLE;
    }
    public synchronized void revoke(String token) { messages.remove(token); }
    public synchronized void clearMessages() { messages.clear(); }
    public synchronized int messageCount() { prune(); return messages.size(); }
    private void prune() {
        long now = System.currentTimeMillis();
        messages.entrySet().removeIf(e -> now - e.getValue().createdAt() > TTL_MS);
        while (messages.size() > MAX_MESSAGES) messages.remove(messages.keySet().iterator().next());
    }
    private static void requireId(String id) {
        if (id == null || !id.matches("[a-z0-9_.-]+(?::[a-z0-9_.-]+)*"))
            throw new IllegalArgumentException("Invalid namespaced identifier: " + id);
    }
}
