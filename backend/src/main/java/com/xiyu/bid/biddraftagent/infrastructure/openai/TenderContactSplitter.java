package com.xiyu.bid.biddraftagent.infrastructure.openai;

import java.util.Map;
import java.util.regex.Pattern;

public final class TenderContactSplitter {

    private static final Pattern CONTACT_SEPARATOR = Pattern.compile("[、,，;；/\\s]+");

    private TenderContactSplitter() {
    }

    public static void splitMultiContactNamesIfNeeded(Map<String, Object> data) {
        Object rawName = data.get("contactName");
        Object rawName2 = data.get("contactName2");
        if (rawName == null) return;
        String name = String.valueOf(rawName).trim();
        String name2 = rawName2 != null ? String.valueOf(rawName2).trim() : "";
        if (!name2.isEmpty()) return;
        if (!CONTACT_SEPARATOR.matcher(name).find()) return;
        String[] parts = CONTACT_SEPARATOR.split(name);
        if (parts.length < 2) return;
        String first = parts[0].trim();
        String second = parts[1].trim();
        if (first.isEmpty() || second.isEmpty()) return;
        if (!looksLikePersonName(first) || !looksLikePersonName(second)) return;
        data.put("contactName", first);
        data.put("contactName2", second);
        splitCorrespondingContactField(data, "contactPhone", parts);
        splitCorrespondingContactField(data, "contactLandline", parts);
        splitCorrespondingContactField(data, "contactEmail", parts);
    }

    private static boolean looksLikePersonName(String text) {
        if (text == null || text.isEmpty()) return false;
        if (text.length() > 20) return false;
        if (text.matches(".*\\d.*")) return false;
        return true;
    }

    private static void splitCorrespondingContactField(Map<String, Object> data, String field, String[] nameParts) {
        Object raw = data.get(field);
        if (raw == null) return;
        String value = String.valueOf(raw).trim();
        if (value.isEmpty()) return;
        String[] valParts = CONTACT_SEPARATOR.split(value);
        if (valParts.length != nameParts.length) return;
        String field2 = field + "2";
        if (data.get(field2) != null && !String.valueOf(data.get(field2)).trim().isEmpty()) return;
        data.put(field, valParts[0].trim());
        data.put(field2, valParts[1].trim());
    }
}
