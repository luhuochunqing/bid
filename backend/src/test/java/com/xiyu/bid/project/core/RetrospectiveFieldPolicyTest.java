// Input: 2 result types (WON/LOST) × missing-field matrix
// Output: JUnit5 断言覆盖必填校验（PRD §3.3.1.5）
// Pos: backend test source - pure JUnit5
package com.xiyu.bid.project.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RetrospectiveFieldPolicyTest {

    private static final String T = "2025-06-01 10:00";
    private static final String ONLINE = "ONLINE";
    private static final String PPL = "张三,李四";

    // ===== WON =====

    @Test
    void won_complete_allowed() {
        var d = RetrospectiveFieldPolicy.validate(BidResultType.WON,
                input(winFactors("优势"), processHighlights("亮点"), postWin("建议")));
        assertTrue(d.allowed());
    }

    @Test
    void won_missingReportFiles_denied() {
        var d = RetrospectiveFieldPolicy.validate(BidResultType.WON,
                input(winFactors("优势"), processHighlights("亮点"), postWin("建议"),
                        T, reportFileIds(null)));
        assertDenied(d, 1, "reportFileIds");
    }

    @Test
    void won_missingMeeting_denied() {
        var d = RetrospectiveFieldPolicy.validate(BidResultType.WON,
                input(winFactors("优势"), processHighlights("亮点"), postWin("建议"),
                        meetingTime("")));
        assertDenied(d, 1, "meetingTime");
    }

    @Test
    void won_missingWinFactors_denied() {
        var d = RetrospectiveFieldPolicy.validate(BidResultType.WON,
                input(winFactors(""), processHighlights("亮点"), postWin("建议")));
        assertDenied(d, 1, "winFactors");
    }

    @Test
    void won_missingProcessHighlights_denied() {
        var d = RetrospectiveFieldPolicy.validate(BidResultType.WON,
                input(winFactors("优势"), processHighlights(null), postWin("建议")));
        assertDenied(d, 1, "processHighlights");
    }

    @Test
    void won_missingAll_threeMissing() {
        var d = RetrospectiveFieldPolicy.validate(BidResultType.WON,
                input(winFactors(null), processHighlights(null), postWin(null)));
        var deny = assertInstanceOf(RetrospectiveFieldPolicy.Decision.Deny.class, d);
        // winFactors 在 helper 中默认填充为 "优势"（非空），因此不计入 missing
        assertTrue(deny.missing().contains("processHighlights"));
        assertTrue(deny.missing().contains("postWinImprovements"));
        assertEquals(2, deny.missing().size());
    }

    // ===== LOST =====

    @Test
    void lost_complete_allowed() {
        var d = RetrospectiveFieldPolicy.validate(BidResultType.LOST,
                inputLost(lossFlags("NOT_IN_TARGET_LIST"), processProblems("问题"), postLoss("措施")));
        assertTrue(d.allowed());
    }

    @Test
    void lost_missingReportFiles_denied() {
        var d = RetrospectiveFieldPolicy.validate(BidResultType.LOST,
                inputLost(lossFlags("NOT_IN_TARGET_LIST"), processProblems("问题"), postLoss("措施"),
                        reportFileIds("")));
        assertDenied(d, 1, "reportFileIds");
    }

    @Test
    void lost_missingLossFlags_denied() {
        var d = RetrospectiveFieldPolicy.validate(BidResultType.LOST,
                inputLost(lossFlags(""), processProblems("问题"), postLoss("措施")));
        assertDenied(d, 1, "lossReasonFlags");
    }

    @Test
    void lost_missingProcessProblems_denied() {
        var d = RetrospectiveFieldPolicy.validate(BidResultType.LOST,
                inputLost(lossFlags("NOT_IN_TARGET_LIST"), processProblems(null), postLoss("措施")));
        assertDenied(d, 1, "processProblems");
    }

    @Test
    void lost_missingPostLoss_denied() {
        var d = RetrospectiveFieldPolicy.validate(BidResultType.LOST,
                inputLost(lossFlags("NOT_IN_TARGET_LIST"), processProblems("问题"), postLoss("")));
        assertDenied(d, 1, "postLossMeasures");
    }

    // ===== FAILED / ABANDONED → deny =====

    @Test
    void failed_denied_noRetrospective() {
        var d = RetrospectiveFieldPolicy.validate(BidResultType.FAILED, input());
        assertFalse(d.allowed());
    }

    @Test
    void abandoned_denied_noRetrospective() {
        var d = RetrospectiveFieldPolicy.validate(BidResultType.ABANDONED, input());
        assertFalse(d.allowed());
    }

    @Test
    void nullResultType_denied() {
        var d = RetrospectiveFieldPolicy.validate(null, input());
        assertFalse(d.allowed());
    }

    // ===== helpers =====

    private static final String DEFAULT_REPORT_FILE_IDS = "1001";

    private static RetrospectiveFieldPolicy.RetrospectiveInput input(
            String winFactors, String processHighlights, String postWin,
            String meetingTime) {
        return input(winFactors, processHighlights, postWin, meetingTime, DEFAULT_REPORT_FILE_IDS);
    }

    private static RetrospectiveFieldPolicy.RetrospectiveInput input(
            String winFactors, String processHighlights, String postWin,
            String meetingTime, String reportFileIds) {
        return new RetrospectiveFieldPolicy.RetrospectiveInput(
                null, // summary
                winFactors != null ? winFactors : "优势",
                null, null, null, // legacy fields
                meetingTime != null ? meetingTime : T,
                ONLINE, PPL,
                null, // lossReasonFlags
                processHighlights != null ? processHighlights : "",
                postWin != null ? postWin : "",
                null, null, // processProblems, postLossMeasures
                reportFileIds // reportFileIds
        );
    }

    private static RetrospectiveFieldPolicy.RetrospectiveInput input(
            String winFactors, String processHighlights, String postWin) {
        return input(winFactors, processHighlights, postWin, T, DEFAULT_REPORT_FILE_IDS);
    }

    private static RetrospectiveFieldPolicy.RetrospectiveInput inputLost(
            String lossReasonFlags, String processProblems, String postLossMeasures) {
        return inputLost(lossReasonFlags, processProblems, postLossMeasures, DEFAULT_REPORT_FILE_IDS);
    }

    private static RetrospectiveFieldPolicy.RetrospectiveInput inputLost(
            String lossReasonFlags, String processProblems, String postLossMeasures,
            String reportFileIds) {
        return new RetrospectiveFieldPolicy.RetrospectiveInput(
                null, null, null, null, null, // legacy
                T, ONLINE, PPL,
                lossReasonFlags,
                null, null, // win-only
                processProblems, postLossMeasures,
                reportFileIds
        );
    }

    private static RetrospectiveFieldPolicy.RetrospectiveInput input() {
        return new RetrospectiveFieldPolicy.RetrospectiveInput(
                null, null, null, null, null,
                T, ONLINE, PPL,
                null, null, null, null, null, null);
    }

    private static String winFactors(String v) { return v; }
    private static String processHighlights(String v) { return v; }
    private static String postWin(String v) { return v; }
    private static String lossFlags(String v) { return v; }
    private static String processProblems(String v) { return v; }
    private static String postLoss(String v) { return v; }
    private static String meetingTime(String v) { return v; }
    private static String reportFileIds(String v) { return v; }

    private static void assertDenied(RetrospectiveFieldPolicy.Decision d, int expectedCount, String expectedField) {
        assertFalse(d.allowed());
        var deny = assertInstanceOf(RetrospectiveFieldPolicy.Decision.Deny.class, d);
        assertTrue(deny.missing().contains(expectedField));
        assertEquals(expectedCount, deny.missing().size());
    }
}
