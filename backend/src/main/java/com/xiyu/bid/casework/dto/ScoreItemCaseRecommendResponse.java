package com.xiyu.bid.casework.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreItemCaseRecommendResponse {

    private long total;

    /** 本评分项有原始证明要求，当前推荐无法自动核验。 */
    private boolean evidenceReviewRequired;

    private List<Item> items;

    /**
     * 资质证书匹配结果列表（当评分项为资质证书类时直连企业资质库返回）
     */
    private List<CertificateItem> matchedCertificates;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CertificateItem {
        private Long id;
        private String name;
        private String level;
        private String certificateNo;
        private String issuer;
        private String issueDate;
        private String expiryDate;
        private String status;
        private String certScope;
        private String fileUrl;
        private String fileName;
        private Integer matchScore;
        private String matchLabel;
        private List<String> matchReasons;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Item {
        private Long id;
        private Long sourceProjectId;
        private String sourceProjectName;
        private String scoringPointTitle;
        private String scoringCategory;
        private Integer matchScore;
        private String matchLabel;
        private List<String> matchReasons;
        private List<String> matchedKeywords;
        private String requirementRaw;
        private String responseText;
        private String responseTextSummary;
        private String customerType;
        private String projectType;
        private String bidResult;
        private Integer reuseCount;
        private Boolean isPinned;
        private String createdAt;
        private String bidEntity;
        private String projectStatus;
        private String subTopic;
        private Integer qualityScore;
        private String sourceSectionTitle;
        private String sourceSectionPath;
        private String sourceDocumentName;
        private Integer sourcePageNumber;
        private Integer sourceSectionIndex;
    }
}
