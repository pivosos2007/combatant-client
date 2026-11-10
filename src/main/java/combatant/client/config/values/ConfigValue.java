/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

/**
 * Base type for configuration values.
 */
public abstract class ConfigValue<T> {

    private final String name; 
    protected T value;
    private boolean sensitive;

    public ConfigValue(String name, T def) {
        this.name = name;
        this.value = def;
    }

    /**
     * Persisted configuration key.
     */
    public String getName() {
        return name;
    }

    public T get() {
        return value;
    }

    public void set(T v) {
        this.value = v;
    }

    public boolean isSensitive() {
        return sensitive;
    }

    @SuppressWarnings("unchecked")
    public <V extends ConfigValue<T>> V sensitive() {
        this.sensitive = true;
        return (V) this;
    }

    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }

    /**
     * Serialization:
     * Returns a primitive or a Map/List representation.
     */
    public abstract Object toJson();

    /**
     * Deserialization:
     * Accepts the representation returned by toJson().
     */
    public abstract void fromJson(Object json);

    public String toDisplay() {
        return String.valueOf(value);
    }
}
