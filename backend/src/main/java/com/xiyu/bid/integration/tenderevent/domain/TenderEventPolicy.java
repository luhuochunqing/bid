package com.xiyu.bid.integration.tenderevent.domain;

import com.xiyu.bid.entity.Tender;

/**
 * 标讯事件推送策略（纯核心、无副作用）。
 *
 * <p>仅在投标系统先于 CRM 系统存在的标讯创建时推送：
 * <ul>
 *   <li>人工录入 {@code MANUAL_SINGLE} → 推送</li>
 *   <li>批量导入 {@code BULK_IMPORT} → 推送</li>
 *   <li>第三方平台 {@code EXTERNAL_PLATFORM} → 推送</li>
 *   <li>CRM 推送创建 {@code CRM_OPPORTUNITY} → 不推送（避免回发循环）</li>
 * </ul>
 */
public final class TenderEventPolicy {

    private TenderEventPolicy() {
    }

    public static boolean shouldPublish(Tender.SourceType sourceType) {
        return sourceType == Tender.SourceType.MANUAL_SINGLE
                || sourceType == Tender.SourceType.BULK_IMPORT
                || sourceType == Tender.SourceType.EXTERNAL_PLATFORM;
    }
}