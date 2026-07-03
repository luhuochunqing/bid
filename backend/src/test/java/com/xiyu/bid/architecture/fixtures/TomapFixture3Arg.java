package com.xiyu.bid.architecture.fixtures;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Test fixture: 3-arg Collectors.toMap call (should pass
 * toMapMustHaveMergeFunction rule).
 * Used by ArchitectureTest#tomapRule_shouldFlag2ArgAndPass3Arg.
 */
public class TomapFixture3Arg {

    public Map<Integer, String> toMap(List<String> items) {
        return items.stream()
            .collect(Collectors.toMap(String::length, Function.identity(), (a, b) -> a));
    }
}
