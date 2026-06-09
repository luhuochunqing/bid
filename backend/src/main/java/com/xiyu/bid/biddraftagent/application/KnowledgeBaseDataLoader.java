// Input: 四个知识库服务
// Output: KnowledgeBaseDataSnapshot（四库数据快照）
// Pos: biddraftagent/application — 四库数据加载器

package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.domain.validation.BrandAuthSummary;
import com.xiyu.bid.biddraftagent.domain.validation.PerformanceSummary;
import com.xiyu.bid.biddraftagent.domain.validation.PersonnelCertSummary;
import com.xiyu.bid.biddraftagent.domain.validation.QualificationSummary;
import com.xiyu.bid.brandauth.manufacturer.application.dto.ManufacturerAuthorizationDTO;
import com.xiyu.bid.brandauth.manufacturer.application.service.ListManufacturerAuthAppService;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.application.service.ListPerformanceAppService;
import com.xiyu.bid.personnel.application.command.PersonnelListCriteria;
import com.xiyu.bid.personnel.application.dto.PersonnelDTO;
import com.xiyu.bid.personnel.application.service.ListPersonnelAppService;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import com.xiyu.bid.qualification.service.QualificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 四库数据加载器。
 * 从资质库、人员库、品牌授权库、业绩库加载数据并映射为轻量 Summary records。
 */
@Service
@RequiredArgsConstructor
public class KnowledgeBaseDataLoader {

    private final QualificationService qualificationService;
    private final ListPersonnelAppService listPersonnelAppService;
    private final ListManufacturerAuthAppService listManufacturerAuthAppService;
    private final ListPerformanceAppService listPerformanceAppService;

    public record KnowledgeBaseDataSnapshot(
            List<QualificationSummary> qualifications,
            List<PersonnelCertSummary> personnelCerts,
            List<BrandAuthSummary> brandAuthorizations,
            List<PerformanceSummary> performanceRecords
    ) {}

    public KnowledgeBaseDataSnapshot loadAll() {
        return new KnowledgeBaseDataSnapshot(
                loadQualifications(),
                loadPersonnelCerts(),
                loadBrandAuthorizations(),
                loadPerformanceRecords()
        );
    }

    private List<QualificationSummary> loadQualifications() {
        List<QualificationDTO> dtos = qualificationService.getValidQualifications();
        return dtos.stream()
                .map(q -> new QualificationSummary(q.getId(), q.getName(), q.getExpiryDate()))
                .toList();
    }

    private List<PersonnelCertSummary> loadPersonnelCerts() {
        List<PersonnelDTO> personnelList = listPersonnelAppService.list(
                new PersonnelListCriteria(null, null, null, null,
                        null, null, null, null, null, null, null, null, null));
        List<PersonnelCertSummary> result = new ArrayList<>();
        for (PersonnelDTO p : personnelList) {
            if (p.certificates() == null) continue;
            for (var cert : p.certificates()) {
                result.add(new PersonnelCertSummary(
                        p.id(), p.name(), cert.name(), cert.expiryDate()));
            }
        }
        return result;
    }

    private List<BrandAuthSummary> loadBrandAuthorizations() {
        Page<ManufacturerAuthorizationDTO> page = listManufacturerAuthAppService.list(
                new ListManufacturerAuthAppService.ListFilter(
                        null, null, null, null, null, null, null, null, null, null, null, null),
                0, 500);
        return page.getContent().stream()
                .map(a -> new BrandAuthSummary(
                        a.id(), a.brandName(), a.productLine(),
                        a.manufacturerName(), a.authEndDate()))
                .toList();
    }

    private List<PerformanceSummary> loadPerformanceRecords() {
        List<PerformanceDTO> dtos = listPerformanceAppService.list(
                PerformanceSearchCriteria.empty());
        return dtos.stream()
                .map(p -> new PerformanceSummary(
                        p.id(), p.contractName(), p.signingEntity(), p.expiryDate()))
                .toList();
    }
}
