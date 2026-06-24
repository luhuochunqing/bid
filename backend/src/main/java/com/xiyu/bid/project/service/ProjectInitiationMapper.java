// Input: ProjectInitiationDetails / InitiationDto / InitiationInput
// Output: 双向映射方法 — Service 不直接持有映射逻辑
// Pos: project/service/ - 纯映射
package com.xiyu.bid.project.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.project.core.InitiationFieldPolicy;
import com.xiyu.bid.project.dto.CustomerInfoRow;
import com.xiyu.bid.project.dto.InitiationDto;
import com.xiyu.bid.project.dto.InitiationViewDto;
import com.xiyu.bid.project.entity.ProjectInitiationDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectInitiationMapper {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");
    private final ObjectMapper objectMapper;

    @SuppressWarnings("deprecation")
    public InitiationFieldPolicy.InitiationInput toInput(InitiationDto d) {
        return new InitiationFieldPolicy.InitiationInput(
                d.getOwnerUnit(), d.getExpectedBidders(), d.getContractPeriodMonths(),
                d.getProjectType(), d.getCustomerType(), d.getAnnualRevenue(),
                d.getAnnualEcommerceAmount(),
                d.getBidOpenTime(), d.getOwnerUserId(), d.getDepartmentSnapshot(),
                d.getDepositAmount(), d.getDepositPaymentMethod(), d.getNeedDeposit(),
                d.getCompetitors(),
                d.getTenderAdverseItems(), d.getRiskAssessment(), d.getRiskMitigationPlan(),
                d.getPmUnderstandsProcess(), d.getSupportNeeded(), d.getProjectPlanGap(),
                d.getCustomerGrade(), d.getBidStatus(), d.getBiddingLeaderName(),
                d.getBiddingPlatform(), d.getBidResultStatus(), d.getProjectLeaderName(),
                d.getLeaderDepartment(), d.getHeadquartersLocation(),
                d.getAiRiskAssessmentNotes());
    }

    @SuppressWarnings("deprecation")
    public InitiationFieldPolicy.InitiationInput toInput(ProjectInitiationDetails e) {
        return new InitiationFieldPolicy.InitiationInput(
                e.getOwnerUnit(), e.getExpectedBidders(), e.getContractPeriodMonths(),
                parseProjectType(e.getProjectType()).orElse(null),
                parseCustomerType(e.getCustomerType()).orElse(null),
                e.getAnnualRevenue(), e.getAnnualEcommerceAmount(),
                e.getBidOpenTime(), e.getOwnerUserId(), e.getDepartmentSnapshot(),
                e.getDepositAmount(), e.getDepositPaymentMethod(), e.getNeedDeposit(),
                e.getCompetitors(),
                e.getTenderAdverseItems(), e.getRiskAssessment(), e.getRiskMitigationPlan(),
                e.getPmUnderstandsProcess(), e.getSupportNeeded(), e.getProjectPlanGap(),
                e.getCustomerGrade(), e.getBidStatus(), e.getBiddingLeaderName(),
                e.getBiddingPlatform(), e.getBidResultStatus(), e.getProjectLeaderName(),
                e.getLeaderDepartment(), e.getHeadquartersLocation(),
                e.getAiRiskAssessmentNotes());
    }

    @SuppressWarnings("deprecation")
    public InitiationDto toDto(InitiationFieldPolicy.InitiationInput in) {
        return InitiationDto.builder()
                .ownerUnit(in.ownerUnit()).expectedBidders(in.expectedBidders())
                .contractPeriodMonths(in.contractPeriodMonths())
                .projectType(in.projectType()).customerType(in.customerType())
                .annualRevenue(in.annualRevenue()).annualEcommerceAmount(in.annualEcommerceAmount())
                .bidOpenTime(in.bidOpenTime())
                .ownerUserId(in.ownerUserId()).departmentSnapshot(in.departmentSnapshot())
                .depositAmount(in.depositAmount()).depositPaymentMethod(in.depositPaymentMethod())
                .needDeposit(in.needDeposit())
                .competitors(in.competitors())
                .tenderAdverseItems(in.tenderAdverseItems()).riskAssessment(in.riskAssessment())
                .riskMitigationPlan(in.riskMitigationPlan()).pmUnderstandsProcess(in.pmUnderstandsProcess())
                .supportNeeded(in.supportNeeded()).projectPlanGap(in.projectPlanGap())
                .customerGrade(in.customerGrade()).bidStatus(in.bidStatus())
                .biddingLeaderName(in.biddingLeaderName()).biddingPlatform(in.biddingPlatform())
                .bidResultStatus(in.bidResultStatus()).projectLeaderName(in.projectLeaderName())
                .leaderDepartment(in.leaderDepartment()).headquartersLocation(in.headquartersLocation())
                .aiRiskAssessmentNotes(in.aiRiskAssessmentNotes())
                .build();
    }

    @SuppressWarnings("deprecation")
    public InitiationFieldPolicy.InitiationInput mergeForUpdate(
            InitiationFieldPolicy.InitiationInput base, InitiationDto patch) {
        return new InitiationFieldPolicy.InitiationInput(
                patch.getOwnerUnit() != null ? patch.getOwnerUnit() : base.ownerUnit(),
                patch.getExpectedBidders() != null ? patch.getExpectedBidders() : base.expectedBidders(),
                patch.getContractPeriodMonths() != null ? patch.getContractPeriodMonths() : base.contractPeriodMonths(),
                patch.getProjectType() != null ? patch.getProjectType() : base.projectType(),
                patch.getCustomerType() != null ? patch.getCustomerType() : base.customerType(),
                patch.getAnnualRevenue() != null ? patch.getAnnualRevenue() : base.annualRevenue(),
                patch.getAnnualEcommerceAmount() != null ? patch.getAnnualEcommerceAmount() : base.annualEcommerceAmount(),
                patch.getBidOpenTime() != null ? patch.getBidOpenTime() : base.bidOpenTime(),
                patch.getOwnerUserId() != null ? patch.getOwnerUserId() : base.ownerUserId(),
                patch.getDepartmentSnapshot() != null ? patch.getDepartmentSnapshot() : base.departmentSnapshot(),
                patch.getDepositAmount() != null ? patch.getDepositAmount() : base.depositAmount(),
                patch.getDepositPaymentMethod() != null ? patch.getDepositPaymentMethod() : base.depositPaymentMethod(),
                patch.getNeedDeposit() != null ? patch.getNeedDeposit() : base.needDeposit(),
                patch.getCompetitors() != null ? patch.getCompetitors() : base.competitors(),
                patch.getTenderAdverseItems() != null ? patch.getTenderAdverseItems() : base.tenderAdverseItems(),
                patch.getRiskAssessment() != null ? patch.getRiskAssessment() : base.riskAssessment(),
                patch.getRiskMitigationPlan() != null ? patch.getRiskMitigationPlan() : base.riskMitigationPlan(),
                patch.getPmUnderstandsProcess() != null ? patch.getPmUnderstandsProcess() : base.pmUnderstandsProcess(),
                patch.getSupportNeeded() != null ? patch.getSupportNeeded() : base.supportNeeded(),
                patch.getProjectPlanGap() != null ? patch.getProjectPlanGap() : base.projectPlanGap(),
                patch.getCustomerGrade() != null ? patch.getCustomerGrade() : base.customerGrade(),
                patch.getBidStatus() != null ? patch.getBidStatus() : base.bidStatus(),
                patch.getBiddingLeaderName() != null ? patch.getBiddingLeaderName() : base.biddingLeaderName(),
                patch.getBiddingPlatform() != null ? patch.getBiddingPlatform() : base.biddingPlatform(),
                patch.getBidResultStatus() != null ? patch.getBidResultStatus() : base.bidResultStatus(),
                patch.getProjectLeaderName() != null ? patch.getProjectLeaderName() : base.projectLeaderName(),
                patch.getLeaderDepartment() != null ? patch.getLeaderDepartment() : base.leaderDepartment(),
                patch.getHeadquartersLocation() != null ? patch.getHeadquartersLocation() : base.headquartersLocation(),
                patch.getAiRiskAssessmentNotes() != null ? patch.getAiRiskAssessmentNotes() : base.aiRiskAssessmentNotes());
    }

    @SuppressWarnings("deprecation")
    public void applyInput(ProjectInitiationDetails e, InitiationDto d) {
        if (d.getOwnerUnit() != null) e.setOwnerUnit(d.getOwnerUnit());
        if (d.getExpectedBidders() != null) e.setExpectedBidders(d.getExpectedBidders());
        if (d.getContractPeriodMonths() != null) e.setContractPeriodMonths(d.getContractPeriodMonths());
        if (d.getProjectType() != null) e.setProjectType(d.getProjectType().name());
        if (d.getCustomerType() != null) e.setCustomerType(d.getCustomerType().name());
        if (d.getAnnualRevenue() != null) e.setAnnualRevenue(d.getAnnualRevenue());
        if (d.getAnnualEcommerceAmount() != null) e.setAnnualEcommerceAmount(d.getAnnualEcommerceAmount());
        if (d.getBidOpenTime() != null) { e.setBidOpenTime(d.getBidOpenTime()); e.setBidMonth(d.getBidOpenTime().format(MONTH_FMT)); }
        if (d.getOwnerUserId() != null) e.setOwnerUserId(d.getOwnerUserId());
        if (d.getDepartmentSnapshot() != null) e.setDepartmentSnapshot(d.getDepartmentSnapshot());
        if (d.getDepositAmount() != null) e.setDepositAmount(d.getDepositAmount());
        if (d.getDepositPaymentMethod() != null) e.setDepositPaymentMethod(d.getDepositPaymentMethod());
        if (d.getNeedDeposit() != null) e.setNeedDeposit(d.getNeedDeposit());
        if (d.getCompetitors() != null) e.setCompetitors(d.getCompetitors());
        if (d.getTenderAdverseItems() != null) e.setTenderAdverseItems(d.getTenderAdverseItems());
        if (d.getRiskAssessment() != null) e.setRiskAssessment(d.getRiskAssessment());
        if (d.getRiskMitigationPlan() != null) e.setRiskMitigationPlan(d.getRiskMitigationPlan());
        if (d.getPmUnderstandsProcess() != null) e.setPmUnderstandsProcess(d.getPmUnderstandsProcess());
        if (d.getSupportNeeded() != null) e.setSupportNeeded(d.getSupportNeeded());
        if (d.getProjectPlanGap() != null) e.setProjectPlanGap(d.getProjectPlanGap());
        if (d.getCustomerGrade() != null) e.setCustomerGrade(d.getCustomerGrade());
        if (d.getBidStatus() != null) e.setBidStatus(d.getBidStatus());
        if (d.getBiddingLeaderName() != null) e.setBiddingLeaderName(d.getBiddingLeaderName());
        if (d.getBiddingPlatform() != null) e.setBiddingPlatform(d.getBiddingPlatform());
        if (d.getBidResultStatus() != null) e.setBidResultStatus(d.getBidResultStatus());
        if (d.getProjectLeaderName() != null) e.setProjectLeaderName(d.getProjectLeaderName());
        if (d.getLeaderDepartment() != null) e.setLeaderDepartment(d.getLeaderDepartment());
        if (d.getHeadquartersLocation() != null) e.setHeadquartersLocation(d.getHeadquartersLocation());
        if (d.getAiRiskAssessmentNotes() != null) e.setAiRiskAssessmentNotes(d.getAiRiskAssessmentNotes());
        if (d.getTenderDocumentId() != null) e.setTenderDocumentId(d.getTenderDocumentId());
        if (d.getCustomerInfoRows() != null) {
            try { e.setCustomerInfoJson(objectMapper.writeValueAsString(d.getCustomerInfoRows())); }
            catch (JsonProcessingException ex) { throw new RuntimeException("Failed to serialize customerInfoRows", ex); }
        }
    }

    @SuppressWarnings("deprecation")
    public InitiationViewDto toView(ProjectInitiationDetails e) {
        List<CustomerInfoRow> rows = null;
        if (e.getCustomerInfoJson() != null && !e.getCustomerInfoJson().isBlank()) {
            try { rows = objectMapper.readValue(e.getCustomerInfoJson(), new TypeReference<>() {}); }
            catch (JsonProcessingException ex) { log.debug("Customer info JSON parse failed", ex); }
        }
        return InitiationViewDto.builder()
                .id(e.getId()).projectId(e.getProjectId())
                .ownerUnit(e.getOwnerUnit()).expectedBidders(e.getExpectedBidders())
                .contractPeriodMonths(e.getContractPeriodMonths())
                .projectType(e.getProjectType()).customerType(e.getCustomerType())
                .annualRevenue(e.getAnnualRevenue()).annualEcommerceAmount(e.getAnnualEcommerceAmount())
                .bidOpenTime(e.getBidOpenTime()).bidMonth(e.getBidMonth())
                .ownerUserId(e.getOwnerUserId()).departmentSnapshot(e.getDepartmentSnapshot())
                .depositAmount(e.getDepositAmount()).depositPaymentMethod(e.getDepositPaymentMethod())
                .needDeposit(e.getNeedDeposit()).competitors(e.getCompetitors())
                .tenderAdverseItems(e.getTenderAdverseItems()).riskAssessment(e.getRiskAssessment())
                .riskMitigationPlan(e.getRiskMitigationPlan()).pmUnderstandsProcess(e.getPmUnderstandsProcess())
                .supportNeeded(e.getSupportNeeded()).projectPlanGap(e.getProjectPlanGap())
                .locked(e.getLocked()).reviewStatus(e.getReviewStatus())
                .rejectionReason(e.getRejectionReason())
                .reviewedBy(e.getReviewedBy()).reviewedAt(e.getReviewedAt())
                .aiRiskLevel(e.getAiRiskLevel()).aiRiskAssessmentNotes(e.getAiRiskAssessmentNotes())
                .tenderDocumentId(e.getTenderDocumentId())
                .customerGrade(e.getCustomerGrade()).bidStatus(e.getBidStatus())
                .biddingLeaderName(e.getBiddingLeaderName()).biddingPlatform(e.getBiddingPlatform())
                .bidResultStatus(e.getBidResultStatus()).projectLeaderName(e.getProjectLeaderName())
                .leaderDepartment(e.getLeaderDepartment()).headquartersLocation(e.getHeadquartersLocation())
                .customerInfoRows(rows).evalPrefilled(e.getEvalPrefilled())
                .createdAt(e.getCreatedAt()).updatedAt(e.getUpdatedAt())
                .build();
    }

    private Optional<InitiationFieldPolicy.ProjectType> parseProjectType(String v) {
        if (v == null) return Optional.empty();
        try {
            return Optional.of(InitiationFieldPolicy.ProjectType.valueOf(v));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private Optional<InitiationFieldPolicy.CustomerType> parseCustomerType(String v) {
        if (v == null) return Optional.empty();
        try {
            return Optional.of(InitiationFieldPolicy.CustomerType.valueOf(v));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
