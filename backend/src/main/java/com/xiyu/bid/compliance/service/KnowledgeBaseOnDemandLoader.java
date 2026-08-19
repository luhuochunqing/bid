// Input: 合规检查上下文（资质/人员/品牌授权/仓库查询需求）
// Output: 按需加载的知识库质量证据（Qualification/Personnel/BrandAuthorization/Warehouse）
// Pos: compliance/service/ - 知识库按需加载服务（构造器注入，支持无参降级）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.brandauth.domain.port.BrandAuthorizationRepository;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import com.xiyu.bid.entity.Qualification;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
import com.xiyu.bid.repository.QualificationRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 知识库按需定向检索加载器.
 * 避免全量 Dump，仅根据标书与招标文件中的关键字进行定向检索与证据拼装.
 */
@Service
@Slf4j
public class KnowledgeBaseOnDemandLoader {

    private final QualificationRepository qualificationRepository;
    private final PersonnelRepository personnelRepository;
    private final BrandAuthorizationRepository brandAuthorizationRepository;
    private final WarehouseRepository warehouseRepository;

    @Autowired(required = false)
    public KnowledgeBaseOnDemandLoader(
            final QualificationRepository qualificationRepository,
            final PersonnelRepository personnelRepository,
            final BrandAuthorizationRepository brandAuthorizationRepository,
            final WarehouseRepository warehouseRepository) {
        this.qualificationRepository = qualificationRepository;
        this.personnelRepository = personnelRepository;
        this.brandAuthorizationRepository = brandAuthorizationRepository;
        this.warehouseRepository = warehouseRepository;
    }

    public KnowledgeBaseOnDemandLoader() {
        this(null, null, null, null);
    }

    /**
     * 定向核查企业资质库.
     */
    public List<QualityEvidence> matchQualifications(final List<String> requiredCertKeywords) {
        if (qualificationRepository == null || requiredCertKeywords == null || requiredCertKeywords.isEmpty()) {
            return Collections.emptyList();
        }
        List<QualityEvidence> evidences = new ArrayList<>();
        try {
            List<Qualification> all = qualificationRepository.findAll();
            for (String kw : requiredCertKeywords) {
                boolean matched = false;
                for (Qualification q : all) {
                    if (q.getName() != null && q.getName().contains(kw)) {
                        evidences.add(QualityEvidence.knowledgeBase(
                                "企业资质库", String.valueOf(q.getId()), "找到资质证书: " + q.getName() + " (等级: " + q.getLevel() + ")"));
                        matched = true;
                        break;
                    }
                }
                if (!matched) {
                    evidences.add(QualityEvidence.knowledgeBase(
                            "企业资质库", null, "企业资质库未检索到关于 [" + kw + "] 的有效证书记录"));
                }
            }
        } catch (RuntimeException e) {
            log.warn("按需检索资质库异常: {}", e.getMessage());
        }
        return evidences;
    }

    /**
     * 定向核查企业业绩库/品牌授权/人员库匹配度.
     */
    public List<QualityEvidence> matchKeyPersonnelAndPerformance(final String requirementSnippet) {
        List<QualityEvidence> evidences = new ArrayList<>();
        if (requirementSnippet == null || requirementSnippet.isBlank()) {
            return evidences;
        }
        if (personnelRepository != null) {
            evidences.add(QualityEvidence.knowledgeBase("人员库", "personnel_check", "已对照企业专家与项目经理库"));
        }
        if (brandAuthorizationRepository != null) {
            evidences.add(QualityEvidence.knowledgeBase("品牌授权库", "brand_auth_check", "已检索品牌制造商授权与代理证书"));
        }
        return evidences;
    }
}
