package com.xiyu.bid.tender.service;

import com.xiyu.bid.crm.application.CustomerManagerResult;
import com.xiyu.bid.crm.application.CompanySearchResult;
import com.xiyu.bid.crm.application.CrmCustomerManagerLookupService;
import com.xiyu.bid.crm.application.CrmCompanySearchService;
import com.xiyu.bid.crm.domain.CrmProjectMappingRepository;
import com.xiyu.bid.crm.domain.CrmProjectMapping;
import com.xiyu.bid.crm.domain.AssignmentResult;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenderAutoAssignmentService {

    private final CrmProjectMappingRepository mappingRepository;
    private final CrmCompanySearchService companySearchService;
    private final CrmCustomerManagerLookupService customerManagerLookupService;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AssignmentResult tryAutoAssign(final Tender tender) {
        if (tender == null || !StringUtils.hasText(tender.getPurchaserName())) {
            log.debug("Skip: tender or purchaserName is null/blank");
            return AssignmentResult.noMatch();
        }

        String purchaserName = tender.getPurchaserName().trim();
        log.debug("Attempting auto-assignment for: {}", purchaserName);

        Optional<CrmProjectMapping> mapping =
                mappingRepository.findByPurchaserName(purchaserName);

        if (mapping.isPresent()) {
            CrmProjectMapping m = mapping.get();
            if (!StringUtils.hasText(m.getProjectManagerName())) {
                log.warn("Local mapping has no projectManagerName for purchaser={}, skipping", purchaserName);
                return AssignmentResult.noMatch();
            }
            log.info("Auto-assignment matched: tender={}, purchaser={}, "
                    + "manager={}, dept={}",
                    tender.getId(), purchaserName,
                    m.getProjectManagerName(), m.getDepartmentName());
            return AssignmentResult.success(
                    m.getCrmProjectId(),
                    m.getProjectManagerId(),
                    m.getProjectManagerName(),
                    m.getDepartmentId(),
                    m.getDepartmentName());
        }

        log.debug("No mapping found for: {}", purchaserName);
        return AssignmentResult.noMatch();
    }

    @Transactional
    public AssignmentResult autoAssignIfPossible(final Tender tender) {
        if (tender == null) {
            log.warn("Auto-assignment skipped: tender is null");
            return AssignmentResult.noMatch();
        }

        AssignmentResult result = tryAutoAssign(tender);

        if (result.isMatched()) {
            log.info("Tender {} assigned to manager {} ({}) from local mapping",
                    tender.getId(),
                    result.projectManagerName(),
                    result.projectManagerId());
            return result;
        }

        AssignmentResult crmResult = tryAutoAssignFromCrm(tender);
        if (crmResult.isMatched()) {
            log.info("Tender {} assigned to manager {} ({}) from CRM",
                    tender.getId(),
                    crmResult.projectManagerName(),
                    crmResult.projectManagerId());
            return crmResult;
        }

        log.debug("Tender {} remains PENDING (no local or CRM mapping)",
                tender.getId());
        return AssignmentResult.noMatch();
    }

    @Transactional(readOnly = true)
    public AssignmentResult tryAutoAssignFromCrm(final Tender tender) {
        if (tender == null || !StringUtils.hasText(tender.getPurchaserName())) {
            log.debug("tryAutoAssignFromCrm skipped: tender or purchaserName is null/blank");
            return AssignmentResult.noMatch();
        }

        String purchaserName = tender.getPurchaserName().trim();
        log.debug("Attempting CRM auto-assignment for: {}", purchaserName);

        try {
            Optional<CompanySearchResult> company =
                    companySearchService.searchByName(purchaserName);
            if (company.isEmpty()) {
                log.debug("CRM step1 no exact company match for: {}", purchaserName);
                return AssignmentResult.noMatch();
            }

            Optional<CustomerManagerResult> manager =
                    customerManagerLookupService.findByCompanyId(company.get().id());
            if (manager.isEmpty()) {
                log.debug("CRM step2 no manager for companyId={}", company.get().id());
                return AssignmentResult.noMatch();
            }

            String saleNo = manager.get().saleNo();
            if (!StringUtils.hasText(saleNo)) {
                log.debug("CRM step2 manager has no saleNo for companyId={}", company.get().id());
                return AssignmentResult.noMatch();
            }
            log.info("CRM auto-assignment matched: tender={}, purchaser={}, companyId={}, saleNo={}",
                    tender.getId(), purchaserName, company.get().id(), saleNo);

            String managerName = resolveManagerNameByEmployeeNumber(saleNo);
            if (!StringUtils.hasText(managerName)) {
                log.warn("CRM 返回的工号 {} 在本地 User 表中无匹配，projectManagerName 为 null", saleNo);
            }

            return AssignmentResult.success(
                    null,
                    saleNo,
                    managerName,
                    null,
                    null
            );
        } catch (RuntimeException e) {
            log.warn("CRM auto-assignment failed for tender {}: {}",
                    tender.getId(), e.getMessage());
            return AssignmentResult.noMatch();
        }
    }

    public String resolveManagerNameByEmployeeNumber(final String employeeNumber) {
        if (!StringUtils.hasText(employeeNumber)) {
            return null;
        }
        return userRepository.findByEmployeeNumber(employeeNumber)
                .map(User::getFullName)
                .orElse(null);
    }
}
