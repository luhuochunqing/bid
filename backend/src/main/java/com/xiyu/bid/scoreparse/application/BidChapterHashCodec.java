package com.xiyu.bid.scoreparse.application;

import java.util.LinkedHashMap;
import java.util.Map;

/** 章节标题→哈希的紧凑 JSON。 */
final class BidChapterHashCodec {

    private BidChapterHashCodec() {
    }

    static String encode(Map<String, String> hashes) {
        if (hashes == null || hashes.isEmpty()) {
            return null;
        }
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : hashes.entrySet()) {
            if (!first) {
                json.append(',');
            }
            first = false;
            json.append('"').append(entry.getKey().replace("\\", "\\\\").replace("\"", "\\\""))
                    .append("\":\"").append(entry.getValue()).append('"');
        }
        return json.append('}').toString();
    }

    static Map<String, String> decode(String json) {
        Map<String, String> map = new LinkedHashMap<>();
        if (json == null || json.length() < 3) {
            return map;
        }
        for (String pair : json.substring(1, json.length() - 1).split(",")) {
            int colon = pair.indexOf("\":\"");
            if (colon < 1) {
                continue;
            }
            map.put(pair.substring(1, colon), pair.substring(colon + 3, pair.length() - 1));
        }
        return map;
    }
}
