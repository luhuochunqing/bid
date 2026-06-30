package com.xiyu.bid.common.util;

import java.util.HashSet;
import java.util.Set;

public final class ZipEntryDeduplicator {

    private final Set<String> usedPaths = new HashSet<>();

    public String deduplicate(String basePath) {
        if (basePath == null || basePath.isEmpty()) {
            basePath = "unnamed";
        }
        if (usedPaths.add(basePath)) {
            return basePath;
        }
        int dotIdx = findExtensionDot(basePath);
        int suffix = 1;
        String prefix;
        String ext;
        if (dotIdx > 0) {
            prefix = basePath.substring(0, dotIdx);
            ext = basePath.substring(dotIdx);
        } else {
            prefix = basePath;
            ext = "";
        }
        String candidate;
        do {
            candidate = prefix + "_" + suffix + ext;
            suffix++;
        } while (!usedPaths.add(candidate));
        return candidate;
    }

    public static String safeFileName(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        return name.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    }

    private static int findExtensionDot(String path) {
        int lastSlash = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        int dotIdx = path.lastIndexOf('.');
        if (dotIdx <= lastSlash + 1 || dotIdx == path.length() - 1) {
            return -1;
        }
        return dotIdx;
    }
}
