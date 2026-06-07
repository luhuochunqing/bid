// Input: profileCode
// Output: profile constants and pure profile predicates
// Pos: docinsight/domain — DocInsight profile pure core
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.docinsight.domain;

import java.util.Locale;
import java.util.Set;

public final class DocInsightProfiles {

    public static final String TENDER = "TENDER";
    public static final String TENDER_INTAKE = "TENDER_INTAKE";

    private static final Set<String> PROJECT_BOUND_PROFILES = Set.of(TENDER);
    private static final Set<String> TENDER_EXTRACTION_PROFILES = Set.of(TENDER, TENDER_INTAKE);

    private DocInsightProfiles() {
    }

    public static boolean requiresProjectAccess(String profileCode) {
        return PROJECT_BOUND_PROFILES.contains(normalize(profileCode));
    }

    public static boolean supportsTenderExtraction(String profileCode) {
        return TENDER_EXTRACTION_PROFILES.contains(normalize(profileCode));
    }

    public static boolean isTenderIntake(String profileCode) {
        return TENDER_INTAKE.equals(normalize(profileCode));
    }

    private static String normalize(String profileCode) {
        return profileCode == null ? "" : profileCode.trim().toUpperCase(Locale.ROOT);
    }
}
