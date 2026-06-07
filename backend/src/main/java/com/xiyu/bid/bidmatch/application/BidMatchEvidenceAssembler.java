package com.xiyu.bid.bidmatch.application;

import com.xiyu.bid.bidmatch.domain.BidMatchEvidenceScope;
import com.xiyu.bid.bidmatch.domain.BidMatchEvidenceScopePolicy;
import com.xiyu.bid.bidmatch.domain.MatchEvidence;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.businessqualification.application.command.QualificationListCriteria;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.entity.Case;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.CaseRepository;
import com.xiyu.bid.repository.TenderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class BidMatchEvidenceAssembler {

    private static final int MAX_CASE_EVIDENCE = 200;
    private static final int MAX_BID_RESULT_EVIDENCE = 200;

    private final TenderRepository tenderRepository;
    private final CaseRepository caseRepository;
    private final BusinessQualificationRepository qualificationRepository;
    private final BidResultFetchResultRepository bidResultRepository;
    private final BidMatchEvidenceScopePolicy scopePolicy = new BidMatchEvidenceScopePolicy();

    public BidMatchEvidenceBundle assemble(Long tenderId) {
        Tender tender = tenderRepository.findById(tenderId)
                .orElseThrow(() -> new IllegalArgumentException("标讯不存在"));
        BidMatchEvidenceScope scope = scopePolicy.fromTender(
                tender.getTitle(),
                tender.getDescription(),
                tender.getTags(),
                tender.getRegion(),
                tender.getIndustry(),
                tender.getPurchaserName()
        );
        List<Case> wonCases = caseRepository.findScopedWonCasesForBidMatch(
                toCaseIndustry(scope.caseIndustryCode()),
                scope.keyword(),
                scope.purchaserName(),
                scope.region(),
                PageRequest.of(0, MAX_CASE_EVIDENCE)
        ).getContent();
        List<BusinessQualification> qualifications = findScopedQualifications(scope);
        List<BidResultFetchResult> confirmedWins = bidResultRepository.findScopedConfirmedWins(
                tenderId,
                scope.keyword(),
                PageRequest.of(0, MAX_BID_RESULT_EVIDENCE)
        );

        Map<String, String> texts = new LinkedHashMap<>();
        Map<String, BigDecimal> numbers = new LinkedHashMap<>();
        Set<String> presentKeys = new LinkedHashSet<>();
        fillTenderEvidence(tender, texts, numbers, presentKeys);
        fillCaseEvidence(wonCases, texts, numbers, presentKeys);
        fillQualificationEvidence(qualifications, texts, numbers, presentKeys);
        fillBidResultEvidence(confirmedWins, numbers, presentKeys);

        Map<String, Object> snapshot = BidMatchEvidenceSnapshotFactory.snapshot(
                tender,
                wonCases,
                qualifications,
                confirmedWins
        );
        String fingerprint = BidMatchEvidenceSnapshotFactory.fingerprint(snapshot);
        MatchEvidence evidence = new MatchEvidence(fingerprint, texts, numbers, presentKeys);
        return new BidMatchEvidenceBundle(evidence, snapshot, fingerprint);
    }

    private List<BusinessQualification> findScopedQualifications(BidMatchEvidenceScope scope) {
        if (!scope.hasKeyword()) {
            return List.of();
        }
        return qualificationRepository.findAll(
                QualificationListCriteria.builder()
                        .status("VALID")
                        .keyword(scope.keyword())
                        .build()
        );
    }

    private Case.Industry toCaseIndustry(String caseIndustryCode) {
        if (caseIndustryCode == null || caseIndustryCode.isBlank()) {
            return null;
        }
        return Case.Industry.valueOf(caseIndustryCode);
    }

    private void fillTenderEvidence(
            Tender tender,
            Map<String, String> texts,
            Map<String, BigDecimal> numbers,
            Set<String> presentKeys
    ) {
        texts.put("tender.searchText", joinText(
                tender.getTitle(),
                tender.getDescription(),
                tender.getTags(),
                tender.getRegion(),
                tender.getIndustry(),
                tender.getSource(),
                tender.getPurchaserName()
        ));
        markPresent("tender.searchText", texts.get("tender.searchText"), presentKeys);
        if (tender.getBudget() != null) {
            numbers.put("tender.budget", tender.getBudget());
            presentKeys.add("tender.budget");
        }
    }

    private void fillCaseEvidence(
            List<Case> wonCases,
            Map<String, String> texts,
            Map<String, BigDecimal> numbers,
            Set<String> presentKeys
    ) {
        numbers.put("case.wonCount", BigDecimal.valueOf(wonCases.size()));
        if (!wonCases.isEmpty()) {
            presentKeys.add("case.wonCount");
        }
        String caseText = wonCases.stream()
                .flatMap(this::caseText)
                .collect(Collectors.joining(" "));
        texts.put("case.searchText", caseText);
        markPresent("case.searchText", caseText, presentKeys);
    }

    private void fillQualificationEvidence(
            List<BusinessQualification> qualifications,
            Map<String, String> texts,
            Map<String, BigDecimal> numbers,
            Set<String> presentKeys
    ) {
        numbers.put("qualification.validCount", BigDecimal.valueOf(qualifications.size()));
        if (!qualifications.isEmpty()) {
            presentKeys.add("qualification.validCount");
            presentKeys.add("qualification.active");
        }
        String names = qualifications.stream()
                .flatMap(item -> Stream.of(item.name(), item.certificateNo(), item.issuer(), item.holderName()))
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
        texts.put("qualification.names", names);
        markPresent("qualification.names", names, presentKeys);
    }

    private void fillBidResultEvidence(
            List<BidResultFetchResult> confirmedWins,
            Map<String, BigDecimal> numbers,
            Set<String> presentKeys
    ) {
        numbers.put("bidResult.confirmedWinCount", BigDecimal.valueOf(confirmedWins.size()));
        if (!confirmedWins.isEmpty()) {
            presentKeys.add("bidResult.confirmedWinCount");
        }
    }

    private Stream<String> caseText(Case item) {
        List<String> values = new ArrayList<>();
        values.add(item.getTitle());
        values.add(item.getDescription());
        values.add(item.getCustomerName());
        values.add(item.getLocationName());
        values.add(item.getProductLine());
        values.add(item.getArchiveSummary());
        values.add(item.getSearchDocument());
        values.addAll(nullToEmpty(item.getTags()));
        values.addAll(nullToEmpty(item.getHighlights()));
        values.addAll(nullToEmpty(item.getTechnologies()));
        return values.stream().filter(value -> value != null && !value.isBlank());
    }

    private List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String joinText(String... values) {
        return Stream.of(values)
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(" "));
    }

    private void markPresent(String key, String value, Set<String> presentKeys) {
        if (value != null && !value.isBlank()) {
            presentKeys.add(key);
        }
    }
}
