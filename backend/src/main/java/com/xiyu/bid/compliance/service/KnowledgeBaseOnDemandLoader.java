// Input: 招标硬性要求关键词（LLM 结构化提取产物）
// Output: KnowledgeQueryOutcome（四库真实匹配记录 KnowledgeMatch + 查询失败源集合）
// Pos: compliance/service/ - 四知识库按需真实检索（spec 049 US3 T033-T037，research.md R4；业绩库经 PerformanceEvidencePort 依赖倒置）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.brandauth.domain.model.BrandAuthorization;
import com.xiyu.bid.brandauth.domain.port.BrandAuthorizationRepository;
import com.xiyu.bid.brandauth.domain.valueobject.AuthorizationStatus;
import com.xiyu.bid.compliance.domain.policy.KnowledgeMatch;
import com.xiyu.bid.compliance.domain.policy.KnowledgeQueryOutcome;
import com.xiyu.bid.compliance.domain.policy.PerformanceEvidencePort;
import com.xiyu.bid.entity.Qualification;
import com.xiyu.bid.personnel.application.command.PersonnelListCriteria;
import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
import com.xiyu.bid.personnel.domain.valueobject.Certificate;
import com.xiyu.bid.repository.QualificationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 四知识库按需真实检索加载器（spec 049 第三步）.
 *
 * <p>资质/品牌授权/人员/业绩四库全部走 Repository 真实查询，每条匹配输出
 * 结构化 {@link KnowledgeMatch}（source + recordId + content 均来自 DB 记录字段，
 * 禁止常量文案）；单库超时/异常记录失败源，由引擎将依赖该库的检查项置 MANUAL，
 * 不阻断其他项（PRD §5.1）.
 */
@Service
@Slf4j
public class KnowledgeBaseOnDemandLoader {

    /** 单库匹配记录上限（避免证据链过长）. */
    static final int MAX_MATCHES_PER_LIBRARY = 20;
    /** 业绩库匹配记录上限. */
    static final int MAX_PERFORMANCE_MATCHES = 10;

    private final QualificationRepository qualificationRepository;
    private final PersonnelRepository personnelRepository;
    private final BrandAuthorizationRepository brandAuthorizationRepository;
    private final PerformanceEvidencePort performanceEvidencePort;

    @Autowired(required = false)
    public KnowledgeBaseOnDemandLoader(
            final QualificationRepository qualificationRepository,
            final PersonnelRepository personnelRepository,
            final BrandAuthorizationRepository brandAuthorizationRepository,
            final PerformanceEvidencePort performanceEvidencePort) {
        this.qualificationRepository = qualificationRepository;
        this.personnelRepository = personnelRepository;
        this.brandAuthorizationRepository = brandAuthorizationRepository;
        this.performanceEvidencePort = performanceEvidencePort;
    }

    public KnowledgeBaseOnDemandLoader() {
        this(null, null, null, null);
    }

    /**
     * 四知识库真实检索.
     *
     * @param requiredCerts 招标硬性资格/资质要求关键词（来自 LLM 结构化提取）
     * @return 四库真实匹配记录 + 查询失败的库来源集合
     */
    public KnowledgeQueryOutcome queryKnowledgeBases(final List<String> requiredCerts) {
        List<KnowledgeMatch> matches = new ArrayList<>();
        Set<String> failedSources = new LinkedHashSet<>();
        collect(matches, failedSources, KnowledgeMatch.SOURCE_QUALIFICATION,
                () -> matchQualifications(requiredCerts));
        collect(matches, failedSources, KnowledgeMatch.SOURCE_BRAND_AUTHORIZATION,
                this::matchBrandAuthorizations);
        collect(matches, failedSources, KnowledgeMatch.SOURCE_PERSONNEL,
                () -> matchPersonnel(requiredCerts));
        collect(matches, failedSources, KnowledgeMatch.SOURCE_PERFORMANCE,
                this::matchPerformances);
        return new KnowledgeQueryOutcome(matches, failedSources);
    }

    /**
     * 单库查询执行：超时/异常记录失败源并打堆栈（Constitution VII §3），不阻断其他库.
     */
    private void collect(
            final List<KnowledgeMatch> matches,
            final Set<String> failedSources,
            final String source,
            final Supplier<List<KnowledgeMatch>> query) {
        try {
            matches.addAll(query.get());
        } catch (RuntimeException e) {
            log.error("知识库 {} 查询失败，依赖该库的检查项将降级为 MANUAL: {}", source, e.getMessage(), e);
            failedSources.add(source);
        }
    }

    /**
     * 资质库：按招标硬性要求关键词定向匹配资质证书（保留 name contains 真查询）.
     */
    List<KnowledgeMatch> matchQualifications(final List<String> requiredCerts) {
        if (qualificationRepository == null) {
            throw new IllegalStateException("企业资质库仓储不可用");
        }
        if (requiredCerts == null || requiredCerts.isEmpty()) {
            return List.of();
        }
        List<Qualification> all = qualificationRepository.findAll();
        List<KnowledgeMatch> matches = new ArrayList<>();
        for (String kw : requiredCerts) {
            if (kw == null || kw.isBlank()) {
                continue;
            }
            for (Qualification q : all) {
                if (q.getName() != null && q.getName().contains(kw)) {
                    matches.add(new KnowledgeMatch(
                            KnowledgeMatch.SOURCE_QUALIFICATION,
                            String.valueOf(q.getId()), describe(q), true));
                    break;
                }
            }
        }
        return matches;
    }

    /**
     * 品牌授权库：真实查询有效授权记录（品牌、期限、范围）.
     */
    List<KnowledgeMatch> matchBrandAuthorizations() {
        if (brandAuthorizationRepository == null) {
            throw new IllegalStateException("品牌授权库仓储不可用");
        }
        List<KnowledgeMatch> matches = new ArrayList<>();
        for (BrandAuthorization auth : brandAuthorizationRepository.findAll()) {
            if (auth.status() != AuthorizationStatus.ACTIVE
                    && auth.status() != AuthorizationStatus.EXPIRING_SOON) {
                continue;
            }
            matches.add(new KnowledgeMatch(
                    KnowledgeMatch.SOURCE_BRAND_AUTHORIZATION,
                    String.valueOf(auth.id()), describe(auth), true));
            if (matches.size() >= MAX_MATCHES_PER_LIBRARY) {
                break;
            }
        }
        return matches;
    }

    /**
     * 人员库：真实查询持有招标要求资质证书的人员.
     */
    List<KnowledgeMatch> matchPersonnel(final List<String> requiredCerts) {
        if (personnelRepository == null) {
            throw new IllegalStateException("人员库仓储不可用");
        }
        if (requiredCerts == null || requiredCerts.isEmpty()) {
            return List.of();
        }
        List<Personnel> all = personnelRepository.findAll(
                PersonnelListCriteria.of(null, null, null, null, null));
        List<KnowledgeMatch> matches = new ArrayList<>();
        for (Personnel p : all) {
            if (p.certificates() == null) {
                continue;
            }
            for (Certificate cert : p.certificates()) {
                if (cert.name() == null || !containsAnyKeyword(cert.name(), requiredCerts)) {
                    continue;
                }
                matches.add(new KnowledgeMatch(
                        KnowledgeMatch.SOURCE_PERSONNEL,
                        String.valueOf(p.id()), describe(p, cert), true));
                if (matches.size() >= MAX_MATCHES_PER_LIBRARY) {
                    return matches;
                }
                break;
            }
        }
        return matches;
    }

    /**
     * 业绩库：经 {@link PerformanceEvidencePort} 真实业绩记录查询（依赖倒置，
     * 避免 compliance→performance 模块环；替换误注入的 WarehouseRepository）.
     */
    List<KnowledgeMatch> matchPerformances() {
        if (performanceEvidencePort == null) {
            throw new IllegalStateException("业绩库仓储不可用");
        }
        return performanceEvidencePort.findPerformanceMatches(MAX_PERFORMANCE_MATCHES);
    }

    private static boolean containsAnyKeyword(final String text, final List<String> keywords) {
        for (String kw : keywords) {
            if (kw != null && !kw.isBlank() && text.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    /** 资质记录摘要（DB 字段拼接，禁止常量文案）. */
    private static String describe(final Qualification q) {
        StringBuilder sb = new StringBuilder("资质证书: ").append(q.getName());
        if (q.getLevel() != null) {
            sb.append("（等级: ").append(q.getLevel()).append("）");
        }
        if (q.getExpiryDate() != null) {
            sb.append("，有效期至 ").append(q.getExpiryDate());
        }
        return sb.toString();
    }

    /** 品牌授权记录摘要（品牌、期限、范围）. */
    private static String describe(final BrandAuthorization auth) {
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
    private static String describe(final Personnel p, final Certificate cert) {
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
