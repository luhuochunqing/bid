// Input: 招标硬性要求关键词与交易平台（LLM 结构化提取产物）
// Output: KnowledgeQueryOutcome（真实匹配记录 KnowledgeMatch + 查询失败源集合）
// Pos: compliance/service/ - 知识库按需真实检索（资质委托、品牌授权、人员、业绩、CA证书库检索）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.xiyu.bid.brandauth.domain.model.BrandAuthorization;
import com.xiyu.bid.brandauth.domain.port.BrandAuthorizationRepository;
import com.xiyu.bid.brandauth.domain.valueobject.AuthorizationStatus;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.compliance.domain.policy.CaCertificateEvidencePort;
import com.xiyu.bid.compliance.domain.policy.KnowledgeMatch;
import com.xiyu.bid.compliance.domain.policy.KnowledgeQueryOutcome;
import com.xiyu.bid.compliance.domain.policy.PerformanceEvidencePort;
import com.xiyu.bid.compliance.domain.policy.QualificationMatchPolicy;
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
 * 知识库按需真实检索加载器（支持资质/品牌/人员/业绩/CA证书真实台账检索）.
 *
 * <p>各库走 Repository / EvidencePort 真实查询，每条匹配输出结构化 {@link KnowledgeMatch}；
 * 单库超时/异常记录失败源，由引擎将依赖该库的检查项置 MANUAL，不阻断其他项。
 */
@Service
@Slf4j
public class KnowledgeBaseOnDemandLoader {

    /** 单库匹配记录上限（避免证据链过长）. */
    static final int MAX_MATCHES_PER_LIBRARY = 20;
    /** 业绩库匹配记录上限. */
    static final int MAX_PERFORMANCE_MATCHES = 10;

    private final QualificationQueryDelegate qualificationQueryDelegate;
    private final PersonnelRepository personnelRepository;
    private final BrandAuthorizationRepository brandAuthorizationRepository;
    private final PerformanceEvidencePort performanceEvidencePort;
    private final CaCertificateEvidencePort caCertificateEvidencePort;

    @Autowired(required = false)
    public KnowledgeBaseOnDemandLoader(
            @Autowired(required = false) final QualificationQueryDelegate qualificationQueryDelegate,
            @Autowired(required = false) final PersonnelRepository personnelRepository,
            @Autowired(required = false) final BrandAuthorizationRepository brandAuthorizationRepository,
            @Autowired(required = false) final PerformanceEvidencePort performanceEvidencePort,
            @Autowired(required = false) final CaCertificateEvidencePort caCertificateEvidencePort) {
        this.qualificationQueryDelegate = qualificationQueryDelegate;
        this.personnelRepository = personnelRepository;
        this.brandAuthorizationRepository = brandAuthorizationRepository;
        this.performanceEvidencePort = performanceEvidencePort;
        this.caCertificateEvidencePort = caCertificateEvidencePort;
    }

    public KnowledgeBaseOnDemandLoader(
            final QualificationRepository qualificationRepository,
            final BusinessQualificationRepository businessQualificationRepository,
            final PersonnelRepository personnelRepository,
            final BrandAuthorizationRepository brandAuthorizationRepository,
            final PerformanceEvidencePort performanceEvidencePort,
            final CaCertificateEvidencePort caCertificateEvidencePort) {
        this(new QualificationQueryDelegate(qualificationRepository, businessQualificationRepository),
                personnelRepository, brandAuthorizationRepository, performanceEvidencePort, caCertificateEvidencePort);
    }

    public KnowledgeBaseOnDemandLoader(
            final QualificationRepository qr, final BusinessQualificationRepository bqr,
            final PersonnelRepository pr, final BrandAuthorizationRepository bar,
            final PerformanceEvidencePort pep) {
        this(qr, bqr, pr, bar, pep, null);
    }

    public KnowledgeBaseOnDemandLoader(
            final QualificationRepository qr, final PersonnelRepository pr,
            final BrandAuthorizationRepository bar, final PerformanceEvidencePort pep) {
        this(qr, null, pr, bar, pep, null);
    }

    public KnowledgeBaseOnDemandLoader() {
        this((QualificationQueryDelegate) null, null, null, null, null);
    }

    /**
     * 知识库真实检索（兼容历史单参数签名）.
     */
    public KnowledgeQueryOutcome queryKnowledgeBases(final List<String> requiredCerts) {
        return queryKnowledgeBases(requiredCerts, null);
    }

    /**
     * 知识库真实检索（包含电子标 CA 证书台账按平台匹配）.
     *
     * @param requiredCerts 招标硬性资格/资质要求关键词（来自 LLM 结构化提取）
     * @param platformKeyword 招标交易平台关键词（来自招标文件解析）
     * @return 真实匹配记录 + 查询失败的库来源集合
     */
    public KnowledgeQueryOutcome queryKnowledgeBases(
            final List<String> requiredCerts, final String platformKeyword) {
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
        if (caCertificateEvidencePort != null) {
            collect(matches, failedSources, KnowledgeMatch.SOURCE_CA_CERTIFICATE,
                    () -> matchCaCertificates(platformKeyword));
        }
        return new KnowledgeQueryOutcome(matches, failedSources);
    }

    /**
     * 单库查询执行：超时/异常记录失败源并打堆栈，不阻断其他库.
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
     * 资质库：委托 {@link QualificationQueryDelegate} 按招标硬性要求关键词定向匹配资质证书.
     */
    List<KnowledgeMatch> matchQualifications(final List<String> requiredCerts) {
        if (qualificationQueryDelegate == null) {
            throw new IllegalStateException("企业资质库仓储不可用");
        }
        return qualificationQueryDelegate.matchQualifications(requiredCerts);
    }

    /**
     * 查询企业资质库全部资质记录（供核查比对清单组装）.
     */
    public List<Qualification> findAllQualifications() {
        if (qualificationQueryDelegate == null) {
            return List.of();
        }
        return qualificationQueryDelegate.findAllQualifications();
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
                    String.valueOf(auth.id()), KnowledgeMatchFormatters.describe(auth), true));
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
                if (cert.name() == null || !matchesAnyRequirement(cert.name(), requiredCerts)) {
                    continue;
                }
                matches.add(new KnowledgeMatch(
                        KnowledgeMatch.SOURCE_PERSONNEL,
                        String.valueOf(p.id()), KnowledgeMatchFormatters.describe(p, cert), true));
                if (matches.size() >= MAX_MATCHES_PER_LIBRARY) {
                    return matches;
                }
                break;
            }
        }
        return matches;
    }

    /**
     * 业绩库：经 {@link PerformanceEvidencePort} 真实业绩记录查询（依赖倒置）.
     */
    List<KnowledgeMatch> matchPerformances() {
        if (performanceEvidencePort == null) {
            throw new IllegalStateException("业绩库仓储不可用");
        }
        return performanceEvidencePort.findPerformanceMatches(MAX_PERFORMANCE_MATCHES);
    }

    /**
     * CA 证书库：经 {@link CaCertificateEvidencePort} 真实 CA 证书记录查询（依赖倒置）.
     */
    List<KnowledgeMatch> matchCaCertificates(final String platformKeyword) {
        if (caCertificateEvidencePort == null) {
            throw new IllegalStateException("CA证书库仓储不可用");
        }
        return caCertificateEvidencePort.findCaMatches(platformKeyword, MAX_MATCHES_PER_LIBRARY);
    }

    private static boolean matchesAnyRequirement(final String certName, final List<String> requiredCerts) {
        if (certName == null || requiredCerts == null) {
            return false;
        }
        return requiredCerts.stream().anyMatch(req -> QualificationMatchPolicy.isQualificationMatch(req, certName));
    }
}
