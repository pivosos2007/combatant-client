/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

/**
 * Базовый тип для всех конфиг-значений.
 */
public abstract class ConfigValue<T> {

    private final String name; // <---- ЭТО ТЕБЕ НУЖНО
    protected T value;
    private boolean sensitive;

    public ConfigValue(String name, T def) {
        this.name = name;
        this.value = def;
    }

    /**
     * ключ в конфиге
     */
    public String getName() {
        return name;
    }

    /**
     * Текущее значение
     */
    public T get() {
        return value;
    }

    /**
     * Установить новое значение
     */
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
     * Сериализация:
     * Возвращает примитив (Boolean, Number, String) или структуру (Map/List).
     */
    public abstract Object toJson();

    /**
     * Десериализация:
     * На вход прилетает то же самое, что было возвращено в toJson().
     */
    public abstract void fromJson(Object json);

    /**
     * Для UI
     */
    public String toDisplay() {
        return String.valueOf(value);
    }
}
