// Input: 招标硬性要求关键词或全部资质查询诉求
// Output: 匹配的企业资质记录列表 KnowledgeMatch，或全部有效资质列表 Qualification
// Pos: compliance/service/ - 企业资质库查询委托（优先业务资质库 BusinessQualificationRepository，兜底旧资质库 QualificationRepository）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.businessqualification.application.command.QualificationListCriteria;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.compliance.domain.policy.KnowledgeMatch;
import com.xiyu.bid.compliance.domain.policy.QualificationMatchPolicy;
import com.xiyu.bid.entity.Qualification;
import com.xiyu.bid.repository.QualificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 企业资质库查询委托（封装新业务资质库与旧资质库的优先回退逻辑）.
 */
@Component
@Slf4j
public class QualificationQueryDelegate {

    private final QualificationRepository qualificationRepository;
    private final BusinessQualificationRepository businessQualificationRepository;

    @Autowired(required = false)
    public QualificationQueryDelegate(
            @Autowired(required = false) final QualificationRepository qualificationRepository,
            @Autowired(required = false) final BusinessQualificationRepository businessQualificationRepository) {
        this.qualificationRepository = qualificationRepository;
        this.businessQualificationRepository = businessQualificationRepository;
    }

    public QualificationQueryDelegate(final QualificationRepository qualificationRepository) {
        this(qualificationRepository, null);
    }

    public QualificationQueryDelegate() {
        this(null, null);
    }

    /**
     * 资质库：按招标硬性要求关键词定向匹配资质证书（优先使用业务企业资质库）.
     *
     * @param requiredCerts 招标硬性资质关键词列表
     * @return 匹配结果列表
     */
    public List<KnowledgeMatch> matchQualifications(final List<String> requiredCerts) {
        if (qualificationRepository == null && businessQualificationRepository == null) {
            throw new IllegalStateException("企业资质库仓储不可用");
        }
        if (requiredCerts == null || requiredCerts.isEmpty()) {
            return List.of();
        }
        List<Qualification> all = queryAllQualificationsDirectly();
        List<KnowledgeMatch> matches = new ArrayList<>();
        for (String kw : QualificationMatchPolicy.expandCombinedIsoRequirements(requiredCerts)) {
            if (kw == null || kw.isBlank()) {
                continue;
            }
            for (Qualification q : all) {
                boolean valid = q.getExpiryDate() == null || !q.getExpiryDate().isBefore(LocalDate.now());
                if (valid && q.getName() != null && QualificationMatchPolicy.isQualificationMatch(kw, q.getName())) {
                    matches.add(new KnowledgeMatch(
                            KnowledgeMatch.SOURCE_QUALIFICATION,
                            String.valueOf(q.getId()), KnowledgeMatchFormatters.describe(q), true));
                    break;
                }
            }
        }
        return matches;
    }

    /**
     * 查询企业资质库全部资质记录（供核查比对清单组装）.
     *
     * @return 全部资质记录列表（发生异常时返回空列表）
     */
    public List<Qualification> findAllQualifications() {
        try {
            return queryAllQualificationsDirectly();
        } catch (RuntimeException e) {
            log.warn("获取企业资质库列表异常: {}", e.getMessage(), e);
            return List.of();
        }
    }

    private List<Qualification> queryAllQualificationsDirectly() {
        if (businessQualificationRepository != null) {
            List<BusinessQualification> bqList = businessQualificationRepository.findAll(
                    QualificationListCriteria.builder().build());
            if (bqList != null && !bqList.isEmpty()) {
                return bqList.stream()
                        .filter(bq -> !bq.retired())
                        .map(KnowledgeMatchFormatters::toLegacyQualification)
                        .toList();
            }
        }
        return qualificationRepository != null ? qualificationRepository.findAll() : List.of();
    }
}
