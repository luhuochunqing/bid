// Input: hasDeposit × returnStatus × evidence 矩阵
// Output: JUnit5 断言覆盖 §3.3.1.6 结项闸门规则（含 4 种退回状态）
// Pos: backend test source - pure JUnit5
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import com.xiyu.bid.project.core.ProjectClosureGatePolicy.ClosureInput;
import com.xiyu.bid.project.core.ProjectClosureGatePolicy.Decision;
import com.xiyu.bid.project.core.ProjectClosureGatePolicy.DepositReturnStatus;
import com.xiyu.bid.project.core.ProjectClosureGatePolicy.DepositSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectClosureGatePolicyTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2026, 5, 1, 10, 0);
    private static final Long DOC = 88L;
    private static final BigDecimal AMOUNT_100 = new BigDecimal("100.00");
    private static final BigDecimal AMOUNT_50 = new BigDecimal("50.00");

    // ----- Allow 路径 -----

    @Test
    void noDeposit_allowed() {
        var d = ProjectClosureGatePolicy.decide(DepositSnapshot.none(), ClosureInput.EMPTY);
        assertTrue(d.allowed());
    }

    @Test
    void hasDeposit_fullyReturned_withDateAndDoc_allowed() {
        var d = ProjectClosureGatePolicy.decide(
                DepositSnapshot.returned(WHEN, DOC), ClosureInput.EMPTY);
        assertTrue(d.allowed());
    }

    @Test
    void hasDeposit_fullyReturned_constructor_allowed() {
        var snap = new DepositSnapshot(true, DepositReturnStatus.FULLY_RETURNED, WHEN, DOC, null, null);
        var d = ProjectClosureGatePolicy.decide(snap, ClosureInput.EMPTY);
        assertTrue(d.allowed());
    }

    @Test
    void hasDeposit_transferredToFee_withAmountAndDoc_allowed() {
        var snap = new DepositSnapshot(true, DepositReturnStatus.TRANSFERRED_TO_FEE, null, DOC, AMOUNT_100, null);
        var d = ProjectClosureGatePolicy.decide(snap, ClosureInput.EMPTY);
        assertTrue(d.allowed());
    }

    @Test
    void hasDeposit_partialReturn_withAllFields_allowed() {
        var snap = new DepositSnapshot(true, DepositReturnStatus.PARTIAL_RETURN_PARTIAL_TRANSFER, null, DOC, AMOUNT_50, AMOUNT_50);
        var d = ProjectClosureGatePolicy.decide(snap, ClosureInput.EMPTY);
        assertTrue(d.allowed());
    }

    // ----- Deny 路径：保证金未退回 -----

    @Test
    void hasDeposit_notReturned_denied() {
        var d = ProjectClosureGatePolicy.decide(DepositSnapshot.notReturned(), ClosureInput.EMPTY);
        assertFalse(d.allowed());
        var deny = assertInstanceOf(Decision.Deny.class, d);
        assertTrue(deny.reasons().contains("保证金未退回"));
    }

    @Test
    void hasDeposit_notReturned_explicit_denied() {
        var snap = new DepositSnapshot(true, DepositReturnStatus.NOT_RETURNED, null, null, null, null);
        var d = ProjectClosureGatePolicy.decide(snap, ClosureInput.EMPTY);
        var deny = assertInstanceOf(Decision.Deny.class, d);
        assertEquals("保证金未退回", deny.reasons().get(0));
    }

    @Test
    void hasDeposit_naStatus_deniedAsAnomaly() {
        var snap = new DepositSnapshot(true, DepositReturnStatus.NA, null, null, null, null);
        var d = ProjectClosureGatePolicy.decide(snap, ClosureInput.EMPTY);
        var deny = assertInstanceOf(Decision.Deny.class, d);
        assertTrue(deny.reasons().get(0).contains("保证金状态异常"));
    }

    // ----- Deny 路径：全部退回但缺凭证/日期 -----

    @Test
    void fullyReturned_missingDate_denied() {
        var snap = new DepositSnapshot(true, DepositReturnStatus.FULLY_RETURNED, null, DOC, null, null);
        var d = ProjectClosureGatePolicy.decide(snap, ClosureInput.EMPTY);
        var deny = assertInstanceOf(Decision.Deny.class, d);
        assertTrue(deny.reasons().contains("缺少保证金退回日期"));
    }

    @Test
    void fullyReturned_missingEvidence_denied() {
        var snap = new DepositSnapshot(true, DepositReturnStatus.FULLY_RETURNED, WHEN, null, null, null);
        var d = ProjectClosureGatePolicy.decide(snap, ClosureInput.EMPTY);
        var deny = assertInstanceOf(Decision.Deny.class, d);
        assertTrue(deny.reasons().contains("缺少保证金退回凭证"));
    }

    @Test
    void fullyReturned_evidenceZero_denied() {
        var snap = new DepositSnapshot(true, DepositReturnStatus.FULLY_RETURNED, WHEN, 0L, null, null);
        var d = ProjectClosureGatePolicy.decide(snap, ClosureInput.EMPTY);
        var deny = assertInstanceOf(Decision.Deny.class, d);
        assertTrue(deny.reasons().contains("缺少保证金退回凭证"));
    }

    @Test
    void fullyReturned_missingBoth_denied_twoReasons() {
        var snap = new DepositSnapshot(true, DepositReturnStatus.FULLY_RETURNED, null, null, null, null);
        var d = ProjectClosureGatePolicy.decide(snap, ClosureInput.EMPTY);
        var deny = assertInstanceOf(Decision.Deny.class, d);
        assertEquals(2, deny.reasons().size());
        assertTrue(deny.reasons().contains("缺少保证金退回日期"));
        assertTrue(deny.reasons().contains("缺少保证金退回凭证"));
    }

    // ----- Deny 路径：转平台服务费缺金额/凭证 -----

    @Test
    void transferredToFee_missingAmount_denied() {
        var snap = new DepositSnapshot(true, DepositReturnStatus.TRANSFERRED_TO_FEE, null, DOC, null, null);
        var d = ProjectClosureGatePolicy.decide(snap, ClosureInput.EMPTY);
        var deny = assertInstanceOf(Decision.Deny.class, d);
        assertTrue(deny.reasons().contains("缺少转平台服务费金额"));
    }

    @Test
    void transferredToFee_missingDoc_denied() {
        var snap = new DepositSnapshot(true, DepositReturnStatus.TRANSFERRED_TO_FEE, null, null, AMOUNT_100, null);
        var d = ProjectClosureGatePolicy.decide(snap, ClosureInput.EMPTY);
        var deny = assertInstanceOf(Decision.Deny.class, d);
        assertTrue(deny.reasons().contains("缺少转服务费证明文件"));
    }

    @Test
    void transferredToFee_zeroAmount_denied() {
        var snap = new DepositSnapshot(true, DepositReturnStatus.TRANSFERRED_TO_FEE, null, DOC, BigDecimal.ZERO, null);
        var d = ProjectClosureGatePolicy.decide(snap, ClosureInput.EMPTY);
        var deny = assertInstanceOf(Decision.Deny.class, d);
        assertTrue(deny.reasons().contains("缺少转平台服务费金额"));
    }

    // ----- Deny 路径：部分退回缺字段 -----

    @Test
    void partialReturn_missingReturnedAmount_denied() {
        var snap = new DepositSnapshot(true, DepositReturnStatus.PARTIAL_RETURN_PARTIAL_TRANSFER, null, DOC, AMOUNT_50, null);
        var d = ProjectClosureGatePolicy.decide(snap, ClosureInput.EMPTY);
        var deny = assertInstanceOf(Decision.Deny.class, d);
        assertTrue(deny.reasons().contains("缺少退回金额"));
    }

    @Test
    void partialReturn_missingTransferAmount_denied() {
        var snap = new DepositSnapshot(true, DepositReturnStatus.PARTIAL_RETURN_PARTIAL_TRANSFER, null, DOC, null, AMOUNT_50);
        var d = ProjectClosureGatePolicy.decide(snap, ClosureInput.EMPTY);
        var deny = assertInstanceOf(Decision.Deny.class, d);
        assertTrue(deny.reasons().contains("缺少转平台服务费金额"));
    }

    @Test
    void partialReturn_missingDoc_denied() {
        var snap = new DepositSnapshot(true, DepositReturnStatus.PARTIAL_RETURN_PARTIAL_TRANSFER, null, null, AMOUNT_50, AMOUNT_50);
        var d = ProjectClosureGatePolicy.decide(snap, ClosureInput.EMPTY);
        var deny = assertInstanceOf(Decision.Deny.class, d);
        assertTrue(deny.reasons().contains("缺少证明文件"));
    }

    // ----- reasonText 与 null 防御 -----

    @Test
    void deny_reasonText_joinsWithSemicolon() {
        var snap = new DepositSnapshot(true, DepositReturnStatus.FULLY_RETURNED, null, null, null, null);
        var d = (Decision.Deny) ProjectClosureGatePolicy.decide(snap, ClosureInput.EMPTY);
        assertTrue(d.reasonText().contains("；"));
    }

    @Test
    void nullSnapshot_throws() {
        assertThrows(NullPointerException.class,
                () -> ProjectClosureGatePolicy.decide(null, ClosureInput.EMPTY));
    }

    @Test
    void nullInput_throws() {
        assertThrows(NullPointerException.class,
                () -> ProjectClosureGatePolicy.decide(DepositSnapshot.none(), null));
    }

    // ----- 工厂方法 -----

    @Test
    void factory_none_isNotHasDeposit() {
        DepositSnapshot s = DepositSnapshot.none();
        assertFalse(s.hasDeposit());
        assertEquals(DepositReturnStatus.NA, s.returnStatus());
    }

    @Test
    void factory_notReturned_isHasDepositNotReturned() {
        DepositSnapshot s = DepositSnapshot.notReturned();
        assertTrue(s.hasDeposit());
        assertEquals(DepositReturnStatus.NOT_RETURNED, s.returnStatus());
    }

    @Test
    void factory_returned_isHasDepositFullyReturned() {
        DepositSnapshot s = DepositSnapshot.returned(WHEN, DOC);
        assertTrue(s.hasDeposit());
        assertEquals(DepositReturnStatus.FULLY_RETURNED, s.returnStatus());
        assertEquals(DOC, s.evidenceDocId());
        assertEquals(WHEN, s.returnDate());
    }
}
