package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.brandauth.manufacturer.domain.valueobject.ProductLine;
import com.xiyu.bid.scoreparse.domain.KnowledgeCategoryPolicy;
import com.xiyu.bid.scoreparse.domain.KnowledgeEvidenceConstraints;
import com.xiyu.bid.scoreparse.dto.BrandMatchedItem;
import com.xiyu.bid.scoreparse.dto.PersonMatchedItem;
import com.xiyu.bid.scoreparse.dto.ProjectMatchedItem;
import com.xiyu.bid.scoreparse.dto.WarehouseMatchedItem;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/** Applies pure factual constraints to candidate DTOs and versions the resulting estimate. */
final class EstimateEvidenceSupport {
    private EstimateEvidenceSupport() {}
    static List<?> filter(List<?> rows,KnowledgeEvidenceConstraints constraints,
            java.util.function.Predicate<BrandMatchedItem> brandConditions) {
        return rows.stream().filter(row -> switch(row) {
            case ProjectMatchedItem r -> constraints.acceptsProject(r.signingDate(),r.contractAmount(),r.projectType(),r.customerType());
            case WarehouseMatchedItem r -> constraints.acceptsWarehouse(r.region(),r.province(),r.area());
            case PersonMatchedItem r -> constraints.acceptsPerson(r.technicalTitle(),r.hitCertificates());
            case BrandMatchedItem r -> brandConditions.test(r);
            default -> throw new IllegalArgumentException("知识库记录类型不支持，未完成预判");
        }).toList();
    }
    static java.util.function.Predicate<BrandMatchedItem> brandConditions(KnowledgeEvidenceConstraints constraints,String detail) {
        var productLines=Arrays.stream(ProductLine.values())
                .filter(line->detail!=null&&detail.contains(line.getDisplayName())).toList();
        return row -> constraints.acceptsBrand(row.brandName(),row.authEndDate())
                && (productLines.isEmpty()||productLines.stream().anyMatch(line->line.name().equals(row.productLine())));
    }
    static boolean applies(ScoreItem row) {
        return "OBJECTIVE".equals(row.getScoreType()) && List.of("PROJECT","WAREHOUSE","PERSON","BRAND").contains(
                new KnowledgeCategoryPolicy().categorize(CertificateScoreEstimateSupport.factorName(row),row.getDetail()));
    }
    static void seal(ScoreItem row,LocalDate day) {
        row.setEstimatedAt(LocalDateTime.now());
        row.setEstimateInputFingerprint(fingerprint(row,day));
    }
    static String fingerprint(ScoreItem row,LocalDate day) {
        String inputs="knowledge-hard-conditions:v1:"+new Inputs(row.getCode(),row.getDim(),row.getDetail(),row.getWeight(),day);
        return CertificateScoreEstimateSupport.sealedFingerprint(row,inputs);
    }
    private record Inputs(String code,String dim,String detail,java.math.BigDecimal weight,LocalDate day) {}
}
