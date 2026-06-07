package com.xiyu.bid.bidresult.service;

import com.xiyu.bid.bidresult.core.CompetitorReportComputation;
import com.xiyu.bid.bidresult.dto.BidResultCompetitorReportRowDTO;
import com.xiyu.bid.bidresult.dto.CompetitorReportRowAssembler;
import com.xiyu.bid.bidresult.dto.CompetitorWinAssembler;
import com.xiyu.bid.bidresult.repository.CompetitorWinRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompetitorReportQueryService {

    private final CompetitorWinRecordRepository competitorWinRecordRepository;
    private final BidResultProjectAccessGuard accessGuard;

    public List<BidResultCompetitorReportRowDTO> getCompetitorReport() {
        return CompetitorReportComputation.aggregate(
                        accessGuard.filterAccessible(
                                        competitorWinRecordRepository.findAllByOrderByWonAtDesc(),
                                        competitorWin -> competitorWin.getProjectId()
                                ).stream()
                                .map(CompetitorWinAssembler::toRow)
                                .toList()
                ).stream()
                .map(CompetitorReportRowAssembler::toDto)
                .toList();
    }
}
