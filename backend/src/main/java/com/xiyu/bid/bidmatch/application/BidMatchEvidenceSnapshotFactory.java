package com.xiyu.bid.bidmatch.application;

import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.entity.Case;
import com.xiyu.bid.entity.Tender;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class BidMatchEvidenceSnapshotFactory {

    private BidMatchEvidenceSnapshotFactory() {
    }

    static Map<String, Object> snapshot(
            Tender tender,
            List<Case> wonCases,
            List<BusinessQualification> qualifications,
            List<BidResultFetchResult> confirmedWins
    ) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("tender", tenderSnapshot(tender));
        snapshot.put("caseEvidence", Map.of(
                "wonCount", wonCases.size(),
                "caseIds", wonCases.stream().map(Case::getId).filter(Objects::nonNull).sorted().toList()
        ));
        snapshot.put("qualificationEvidence", Map.of(
                "validCount", qualifications.size(),
                "qualificationIds", qualifications.stream()
                        .map(BusinessQualification::id)
                        .filter(Objects::nonNull)
                        .sorted()
                        .toList()
        ));
        snapshot.put("bidResultEvidence", Map.of(
                "confirmedWinCount", confirmedWins.size(),
                "resultIds", confirmedWins.stream()
                        .map(BidResultFetchResult::getId)
                        .filter(Objects::nonNull)
                        .sorted()
                        .toList()
        ));
        return snapshot;
    }

    static String fingerprint(Map<String, Object> snapshot) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = snapshot.toString().getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("生成投标匹配证据指纹失败", ex);
        }
    }

    private static Map<String, Object> tenderSnapshot(Tender tender) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", tender.getId());
        snapshot.put("title", tender.getTitle());
        snapshot.put("budget", tender.getBudget());
        snapshot.put("region", tender.getRegion());
        snapshot.put("industry", tender.getIndustry());
        snapshot.put("source", tender.getSource());
        snapshot.put("purchaserName", tender.getPurchaserName());
        snapshot.put("updatedAt", tender.getUpdatedAt() == null
                ? null
                : tender.getUpdatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return snapshot;
    }
}
