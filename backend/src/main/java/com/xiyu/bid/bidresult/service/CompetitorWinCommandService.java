package com.xiyu.bid.bidresult.service;

import com.xiyu.bid.bidresult.core.CompetitorWinRegistration;
import com.xiyu.bid.bidresult.dto.CompetitorWinAssembler;
import com.xiyu.bid.bidresult.dto.CompetitorWinDTO;
import com.xiyu.bid.bidresult.dto.CompetitorWinRequest;
import com.xiyu.bid.bidresult.entity.CompetitorWinRecord;
import com.xiyu.bid.bidresult.repository.CompetitorWinRecordRepository;
import com.xiyu.bid.competitionintel.entity.Competitor;
import com.xiyu.bid.competitionintel.repository.CompetitorRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CompetitorWinCommandService {

    private final CompetitorWinRecordRepository competitorWinRecordRepository;
    private final CompetitorRepository competitorRepository;
    private final ProjectRepository projectRepository;
    private final BidResultProjectAccessGuard accessGuard;

    @Transactional
    public CompetitorWinDTO register(CompetitorWinRequest request, Long operatorId, String operatorName) {
        CompetitorWinRegistration registration = new CompetitorWinRegistration(
                request.getCompetitorId(),
                request.getCompetitorName(),
                request.getProjectId(),
                request.getSkuCount(),
                request.getCategory(),
                request.getDiscount(),
                request.getPaymentTerms(),
                request.getWonAt(),
                request.getAmount(),
                request.getNotes()
        );
        var validation = registration.validate();
        if (!validation.valid()) {
            throw new BusinessException(String.join("; ", validation.errors()));
        }
        accessGuard.assertCanAccess(request.getProjectId());
        Competitor competitor = resolveCompetitor(request);
        Optional<String> projectName = Optional.ofNullable(request.getProjectId())
                .flatMap(projectRepository::findById)
                .map(Project::getName);
        CompetitorWinRecord entity = CompetitorWinRecord.builder()
                .competitorId(competitor.getId())
                .competitorName(competitor.getName())
                .projectId(request.getProjectId())
                .projectName(projectName.orElse(null))
                .skuCount(Optional.ofNullable(request.getSkuCount()).orElse(0))
                .category(request.getCategory())
                .discount(request.getDiscount())
                .paymentTerms(request.getPaymentTerms())
                .wonAt(Optional.ofNullable(request.getWonAt()).orElse(LocalDate.now()))
                .amount(request.getAmount())
                .notes(request.getNotes())
                .recordedBy(operatorId)
                .recordedByName(operatorName)
                .build();
        return CompetitorWinAssembler.toDto(competitorWinRecordRepository.save(entity));
    }

    private Competitor resolveCompetitor(CompetitorWinRequest request) {
        if (request.getCompetitorId() != null) {
            return competitorRepository.findById(request.getCompetitorId())
                    .orElseThrow(() -> new ResourceNotFoundException("Competitor not found: " + request.getCompetitorId()));
        }
        String competitorName = Optional.ofNullable(request.getCompetitorName())
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .orElseThrow(() -> new BusinessException("竞争对手名称必填"));
        return competitorRepository.findByName(competitorName)
                .orElseGet(() -> competitorRepository.save(Competitor.builder().name(competitorName).build()));
    }
}
