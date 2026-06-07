package com.xiyu.bid.shared.infrastructure;

public final class SensitiveDataMasker {

    private SensitiveDataMasker() {
    }

    public static String maskToken(String token) {
        if (token == null || token.length() <= 8) {
            return "****";
        }
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    public static String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) {
            return "****";
        }
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "****";
        }
        int at = email.indexOf('@');
        String localPart = email.substring(0, at);
        String domain = email.substring(at);
        if (localPart.length() <= 2) {
            return "*" + domain;
        }
        return localPart.charAt(0) + "***" + domain;
    }
}
