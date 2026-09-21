package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将文本层签章结论与页面视觉证据融合为唯一 signature_seal 结果。
 *
 * <p>反假边界：视觉不可用、无正向观察或无法定位页面时，文本层 PASS 一律降级人工复核
 * （项目 1382 假通过根因：视觉兜底被调用侧前置条件短路）。
 */
public final class SealVisionFusionPolicy {

    private static final double MIN_CONFIDENCE = 0.70;

    private SealVisionFusionPolicy() {
    }

    // REUSE-OK: backend/src/main/java/com/xiyu/bid/scoreparse/domain/ScoreItemMergePolicy.java — 不同领域的签章状态融合，需追加页级视觉证据与人工复核语义。
    public static QualityCheckItemResult merge(
            final QualityCheckItemResult textResult,
            final SealVisionResult vision) {
        if (vision == null || !vision.available()) {
            return QualityCheckItemResult.manualReview(
                    com.xiyu.bid.compliance.domain.BidQualityCheckItem.SIGNATURE_SEAL,
                    "视觉印章检测不可用，文本结果无法证明页面级签章完整性",
                    "请人工逐页核对需盖章页面；视觉检测原因：" +
                            (vision == null ? "结果缺失" : vision.unavailableReason()),
                    textResult.evidences());
        }

        List<QualityEvidence> evidences = new ArrayList<>(textResult.evidences());
        Set<Integer> detectedPages = vision.observations().stream()
                .filter(SealVisionFusionPolicy::isPositive)
                .map(SealVisionResult.Observation::pageNumber)
                .collect(Collectors.toSet());
        vision.observations().stream()
                .filter(SealVisionFusionPolicy::isPositive)
                .map(SealVisionFusionPolicy::toEvidence)
                .forEach(evidences::add);

        if (detectedPages.isEmpty()) {
            return QualityCheckItemResult.manualReview(
                    com.xiyu.bid.compliance.domain.BidQualityCheckItem.SIGNATURE_SEAL,
                    "视觉逐页检测未识别到可信印章或签字",
                    "请人工核对需盖章页面，确认图片清晰且实际完成签章", evidences);
        }

        List<Integer> missingPages = vision.targetPages().stream()
                .filter(page -> !detectedPages.contains(page))
                .toList();
        if (!missingPages.isEmpty()) {
            return QualityCheckItemResult.manualReview(
                    com.xiyu.bid.compliance.domain.BidQualityCheckItem.SIGNATURE_SEAL,
                    "视觉检测未在目标盖章页发现印章：" + formatPages(missingPages),
                    "请人工核对目标页是否实际盖章，必要时重新上传清晰扫描件", evidences);
        }

        // 无页面级定位（如 .docx 仅抽到内嵌图片、无页码线索）时无法确认页面覆盖，不得维持 PASS
        if (vision.targetPages().isEmpty()) {
            return QualityCheckItemResult.manualReview(
                    com.xiyu.bid.compliance.domain.BidQualityCheckItem.SIGNATURE_SEAL,
                    "视觉通道未能定位需核验页面，无法确认页面级签章覆盖",
                    "请人工核对投标函、技术方案末尾、承诺函等需盖章页面；非分页文档（.docx）暂不提供页面图像，签章需人工确认",
                    evidences);
        }

        boolean legalRep = hasType(vision, SealVisionResult.SealType.LEGAL_REP_SEAL);
        boolean company = hasType(vision, SealVisionResult.SealType.COMPANY_SEAL);
        if (textResult.status() == QualityCheckStatus.FAIL && legalRep && company) {
            return QualityCheckItemResult.pass(
                    com.xiyu.bid.compliance.domain.BidQualityCheckItem.SIGNATURE_SEAL,
                    "视觉识别确认法定代表人签章与单位公章，目标页面覆盖完整",
                    evidences);
        }
        return new QualityCheckItemResult(
                textResult.itemCode(), textResult.category(), textResult.title(), textResult.description(),
                textResult.status(), textResult.severity(), textResult.order(), textResult.conclusion(),
                textResult.suggestion(), evidences, textResult.details());
    }

    private static boolean isPositive(final SealVisionResult.Observation observation) {
        return observation.detected() && observation.confidence() >= MIN_CONFIDENCE;
    }

    private static boolean hasType(final SealVisionResult result, final SealVisionResult.SealType type) {
        return result.observations().stream().anyMatch(item -> isPositive(item) && item.type() == type);
    }

    private static QualityEvidence toEvidence(final SealVisionResult.Observation observation) {
        String quote = "AI视觉识别 " + observation.type() + " confidence="
                + observation.confidence() + " x=" + observation.x() + " y=" + observation.y()
                + " width=" + observation.width() + " height=" + observation.height();
        return new QualityEvidence("BID_IMAGE", "页面印章视觉识别", observation.pageNumber(), quote,
                null, observation.confidence(), null, null);
    }

    private static String formatPages(final List<Integer> pages) {
        return pages.stream().map(page -> "第" + page + "页").collect(Collectors.joining("、"));
    }
}
