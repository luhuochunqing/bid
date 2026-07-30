package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.TenderRequirementProfile;
import com.xiyu.bid.entity.Tender;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link TenderRequirementSnapshotUpdater} 的根因行为测试。
 *
 * <p>背景：purchaserName 三次修复失效的零号病人定位在 {@code TenderRequirementSnapshotUpdater#apply}
 * 第 18 行（原条件 {@code isBlank(tender.getPurchaserName()) && !isBlank(profile.purchaserName())}）。
 * 原条件只在 tender 已有值为空时才写入新值，导致 AI 首次误识别后，重新分析的正确值永远无法覆盖旧错误值。
 *
 * <p>本测试直接验证根因行为（不依赖被改动的上游函数），确保：
 * <ol>
 *   <li>根因修复测试：tender 已有旧错误值时，新正确值能覆盖（根因场景）</li>
 *   <li>空值保留测试：新 profile 的 purchaserName 为空时，保留 tender 旧值（避免误伤）</li>
 *   <li>首次写入测试：tender 的 purchaserName 为空时，新值能写入（基本功能）</li>
 * </ol>
 *
 * <p>详见 lessons-learned 第 88 条。
 */
class TenderRequirementSnapshotUpdaterTest {

    private final TenderRequirementSnapshotUpdater updater = new TenderRequirementSnapshotUpdater();

    @Test
    void apply_shouldOverrideStalePurchaserNameWhenProfileHasNewValue() {
        // 根因场景：tender 已有 AI 误识别的旧值（代理机构名），重新分析后 profile 有正确值（招标主体）
        Tender tender = new Tender();
        tender.setPurchaserName("祥安招标代理有限公司");
        TenderRequirementProfile profile = buildProfileWithPurchaserName("张家口银行股份有限公司");

        updater.apply(tender, profile);

        // 期望：新正确值覆盖旧错误值（原 bug：因 tender.getPurchaserName() 非空，新值被丢弃）
        assertThat(tender.getPurchaserName()).isEqualTo("张家口银行股份有限公司");
    }

    @Test
    void apply_shouldKeepExistingPurchaserNameWhenProfileValueIsBlank() {
        // 避免误伤：profile 的 purchaserName 为空时（如正则未命中且 AI 也未返回），保留 tender 旧值
        Tender tender = new Tender();
        tender.setPurchaserName("张家口银行股份有限公司");
        TenderRequirementProfile profile = buildProfileWithPurchaserName("");

        updater.apply(tender, profile);

        assertThat(tender.getPurchaserName()).isEqualTo("张家口银行股份有限公司");
    }

    @Test
    void apply_shouldWritePurchaserNameWhenTenderValueIsNull() {
        // 基本功能：tender 首次分析（purchaserName 为 null），新值能正常写入
        Tender tender = new Tender();
        tender.setPurchaserName(null);
        TenderRequirementProfile profile = buildProfileWithPurchaserName("张家口银行股份有限公司");

        updater.apply(tender, profile);

        assertThat(tender.getPurchaserName()).isEqualTo("张家口银行股份有限公司");
    }

    @Test
    void apply_shouldTrimPurchaserNameWhenWriting() {
        // 边界场景：新值应 trim 后写入
        Tender tender = new Tender();
        tender.setPurchaserName(null);
        TenderRequirementProfile profile = buildProfileWithPurchaserName("  张家口银行股份有限公司  ");

        updater.apply(tender, profile);

        assertThat(tender.getPurchaserName()).isEqualTo("张家口银行股份有限公司");
    }

    private TenderRequirementProfile buildProfileWithPurchaserName(String purchaserName) {
        return new TenderRequirementProfile(
                null,
                null,
                null,
                purchaserName,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
