package com.xiyu.bid.bidmatch.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

public record MatchEvidence(
        String fingerprint,
        Map<String, String> texts,
        Map<String, BigDecimal> numbers,
        Set<String> presentKeys
) {

    public MatchEvidence {
        texts = texts == null ? Map.of() : Map.copyOf(texts);
        numbers = numbers == null ? Map.of() : Map.copyOf(numbers);
        presentKeys = presentKeys == null ? Set.of() : Set.copyOf(presentKeys);
    }

    public boolean hasText(String key) {
        String value = texts.get(key);
        return value != null && !value.isBlank();
    }

    public boolean hasNumber(String key) {
        return numbers.containsKey(key);
    }

    public boolean hasEvidence(String key) {
        return presentKeys.contains(key) || hasText(key) || hasNumber(key);
    }
}
