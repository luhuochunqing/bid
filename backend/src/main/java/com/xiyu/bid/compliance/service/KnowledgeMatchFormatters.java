// Input: 知识库实体对象（资质/品牌授权/人员资质等）
// Output: 格式化后的知识匹配内容与模型转换
// Pos: compliance/service/ - 知识库匹配结果格式化辅助类（降低 KnowledgeBaseOnDemandLoader 行数）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.brandauth.domain.model.BrandAuthorization;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.entity.Qualification;
import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.valueobject.Certificate;

import java.time.LocalDate;

/**
 * 知识库记录描述与格式化辅助工具类（包私有）.
 */
final class KnowledgeMatchFormatters {

    private KnowledgeMatchFormatters() {
    }

    static Qualification toLegacyQualification(final BusinessQualification bq) {
        LocalDate issue = bq.validityPeriod() != null ? bq.validityPeriod().issueDate() : null;
        LocalDate expiry = bq.validityPeriod() != null ? bq.validityPeriod().expiryDate() : null;
        return Qualification.builder().id(bq.id()).name(bq.name()).level(bq.level())
                .issueDate(issue).expiryDate(expiry).fileUrl(bq.fileUrl()).build();
    }

    /** 资质记录摘要（DB 字段拼接，禁止常量文案）. */
    static String describe(final Qualification q) {
        String level = q.getLevel() != null ? "（等级: " + q.getLevel() + "）" : "";
        String expiry = q.getExpiryDate() != null ? "，有效期至 " + q.getExpiryDate() : "";
        return "资质证书: " + q.getName() + level + expiry;
    }

    /** 品牌授权记录摘要（品牌、期限、范围）. */
    static String describe(final BrandAuthorization auth) {
        StringBuilder sb = new StringBuilder("品牌授权: ").append(auth.brandName()).append("（");
        if (auth.supplierName() != null) {
            sb.append("供应商: ").append(auth.supplierName());
        }
        if (auth.scope() != null) {
            if (auth.supplierName() != null) {
                sb.append("，");
            }
            sb.append("范围: ").append(auth.scope());
            if (auth.scopeDetail() != null) {
                sb.append("/").append(auth.scopeDetail());
            }
        }
        sb.append("，期限: ").append(auth.startDate()).append(" 至 ").append(auth.endDate()).append("）");
        return sb.toString();
    }

    /** 人员及其资质证书摘要. */
    static String describe(final Personnel p, final Certificate cert) {
        StringBuilder sb = new StringBuilder("人员: ").append(p.name());
        if (p.employeeNumber() != null) {
            sb.append("（工号: ").append(p.employeeNumber()).append("）");
        }
        sb.append("，资质证书: ").append(cert.name());
        if (cert.expiryDate() != null) {
            sb.append("，有效期至 ").append(cert.expiryDate());
        }
        return sb.toString();
    }
}
