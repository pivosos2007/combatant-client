/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

import java.util.*;

public class EnumValue<E extends Enum<E>> extends ConfigValue<E> {

    private final List<E> options;
    private final List<String> optionIds;
    private final Map<String, E> lookup;
    public EnumValue(String name, E def, Class<E> enumClass) {
        this(name, def, enumClass.getEnumConstants());
    }
    @SafeVarargs
    public EnumValue(String name, E def, E... opts) {
        super(name, def);
        this.options = List.of(opts);
        this.optionIds = new ArrayList<>(options.size());
        this.lookup = new HashMap<>();

        for (E opt : options) {
            String id = idOf(opt);
            optionIds.add(id);
            lookup.put(normalize(id), opt);
        }

        for (E opt : options) {
            if (!(opt instanceof AliasProvider aliasProvider)) continue;
            List<String> aliases = aliasProvider.aliases();
            if (aliases == null) continue;
            for (String alias : aliases) {
                if (alias == null || alias.isBlank()) continue;
                lookup.putIfAbsent(normalize(alias), opt);
            }
        }

        if (!options.contains(def) && !options.isEmpty()) {
            this.value = options.get(0);
        }
    }

    private static String normalize(String id) {
        return id == null ? "" : id.toLowerCase(Locale.ROOT);
    }

    public List<String> getOptions() {
        return optionIds;
    }

    public String getId() {
        return idOf(value);
    }

    public void setById(String id) {
        if (id == null) return;
        E resolved = lookup.get(normalize(id));
        if (resolved != null) {
            value = resolved;
        }
    }

    @Override
    public Object toJson() {
        return getId();
    }

    @Override
    public void fromJson(Object json) {
        if (json instanceof String s) {
            setById(s);
        }
    }

    private String idOf(E value) {
        if (value instanceof IdProvider idProvider) {
            return idProvider.getId();
        }
        return EnumIds.defaultId(value);
    }

    public interface IdProvider {
        default String getId() {
            return id();
        }

        default String id() {
            try {
                var method = getClass().getMethod("getId");
                if (method.getReturnType() == String.class && method.getParameterCount() == 0) {
                    return (String) method.invoke(this);
                }
            } catch (ReflectiveOperationException ignored) {
                // Fall back to the enum name below.
            }

            if (this instanceof Enum<?> enumValue) {
                return EnumIds.defaultId(enumValue);
            }

            return "";
        }
    }

    public interface AliasProvider {
        default List<String> aliases() {
            return List.of();
        }
    }
}
