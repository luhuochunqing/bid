package com.xiyu.bid.versionhistory.service;

import java.util.ArrayList;
import java.util.List;

final class VersionDiffCalculator {

    private VersionDiffCalculator() {
    }

    static List<String> compute(String content1, String content2) {
        List<String> differences = new ArrayList<>();

        if (content1 == null && content2 == null) {
            return differences;
        }
        if (content1 == null) {
            differences.add("Content added: " + sanitizeForLog(content2));
            return differences;
        }
        if (content2 == null) {
            differences.add("Content removed: " + sanitizeForLog(content1));
            return differences;
        }

        String[] lines1 = content1.split("\\n");
        String[] lines2 = content2.split("\\n");
        int maxLines = Math.max(lines1.length, lines2.length);

        for (int i = 0; i < maxLines; i++) {
            if (i >= lines1.length) {
                differences.add("Line " + (i + 1) + " added: " + sanitizeForLog(lines2[i]));
            } else if (i >= lines2.length) {
                differences.add("Line " + (i + 1) + " removed: " + sanitizeForLog(lines1[i]));
            } else if (!lines1[i].equals(lines2[i])) {
                differences.add("Line " + (i + 1) + " changed from '"
                        + sanitizeForLog(lines1[i]) + "' to '" + sanitizeForLog(lines2[i]) + "'");
            }
        }

        return differences;
    }

    private static String sanitizeForLog(String content) {
        if (content == null) {
            return "";
        }
        return content.replaceAll("[\\r\\n\\t\\x00\\x1b]", "");
    }
}
