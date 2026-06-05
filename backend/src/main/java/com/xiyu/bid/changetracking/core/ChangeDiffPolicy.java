package com.xiyu.bid.changetracking.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

public final class ChangeDiffPolicy {

    private static final int MAX_CHANGES = 50;

    private ChangeDiffPolicy() {}

    public static List<FieldChange> diff(Map<String, Object> before, Map<String, Object> after) {
        if (before == null || after == null) {
            return List.of();
        }
        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(before.keySet());
        allKeys.addAll(after.keySet());

        List<FieldChange> changes = new ArrayList<>();
        for (String key : allKeys) {
            if (changes.size() >= MAX_CHANGES) break;
            Object beforeVal = before.get(key);
            Object afterVal = after.get(key);
            if (!Objects.equals(beforeVal, afterVal)) {
                changes.add(new FieldChange(key, beforeVal, afterVal));
            }
        }
        return List.copyOf(changes);
    }
}
