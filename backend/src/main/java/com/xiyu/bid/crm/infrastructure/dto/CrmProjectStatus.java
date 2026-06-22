// Pos: crm/infrastructure/dto/
// 被 WebhookEventListener 和 ProjectResultConfirmedWebhookListener 共用。
package com.xiyu.bid.crm.infrastructure.dto;

/**
 * CRM bidInfoSync projectStatus 枚举常量。
 * <p>CRM projectStatus 枚举（来自 CRM 商机操作记录原文）：
 * 1-跟进中 2-中标 3-丢标 4-流标 5-投标中 6-弃标。
 * <p>供两个 webhook listener 统一引用，避免跨类重复声明。
 */
public final class CrmProjectStatus {

    private CrmProjectStatus() {}

    /** CRM 跟进中（1），对应本平台 Tender.Status.EVALUATED */
    public static final int FOLLOW_UP = 1;

    /** CRM 中标（2），对应本平台 BidResultType.WON */
    public static final int WON = 2;

    /** CRM 丢标（3），对应本平台 BidResultType.LOST */
    public static final int LOST = 3;

    /** CRM 流标（4），对应本平台 BidResultType.FAILED */
    public static final int FAILED = 4;

    /** CRM 投标中（5），对应本平台 Tender.Status.BIDDING */
    public static final int BIDDING = 5;

    /** CRM 弃标（6），对应本平台 Tender.Status.ABANDONED / BidResultType.ABANDONED */
    public static final int ABANDONED = 6;
}
