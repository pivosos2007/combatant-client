/*
 * This file is part of the Combatant Client distribution.
 * Copyright (c) 2026 pivosos2007.
 *
 * Licensed under the GNU General Public License v3.0.
 */

package combatant.client.config.values;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class ModeValue extends ConfigValue<String> {

    private final List<String> options;

    public ModeValue(String name, String def, String... opts) {
        super(name, def);
        this.options = new ArrayList<>(Arrays.asList(opts));

        // если default не валидный → ставим первый вариант
        if (!options.contains(def)) {
            this.value = options.get(0);
        }
    }

    public List<String> getOptions() {
        return List.copyOf(options);
    }

    public boolean addOption(String option) {
        String normalized = normalizeOption(option);
        if (normalized.isEmpty() || options.contains(normalized)) return false;
        options.add(normalized);
        return true;
    }

    public int addOptions(Collection<String> values) {
        if (values == null || values.isEmpty()) return 0;
        int added = 0;
        for (String value : values) {
            if (addOption(value)) added++;
        }
        return added;
    }

    public boolean removeOption(String option) {
        String normalized = normalizeOption(option);
        if (normalized.isEmpty()) return false;
        if (Objects.equals(value, normalized)) return false;
        return options.remove(normalized);
    }

    public void next() {
        int i = options.indexOf(value);
        value = options.get((i + 1) % options.size());
    }

    @Override
    public Object toJson() {
        return value;
    }

    @Override
    public void fromJson(Object json) {
        if (json instanceof String s && options.contains(s)) {
            value = s;
        }
    }

    private static String normalizeOption(String option) {
        return option == null ? "" : option.trim();
    }
}
