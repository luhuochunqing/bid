package com.xiyu.bid.analytics.service;

import com.xiyu.bid.analytics.dto.CompetitorAnalysisRequest;
import com.xiyu.bid.analytics.dto.CompetitorAnalysisResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompetitorAnalysisService {

    private final CompetitorAnalysisAssemblerService assemblerService;

    public CompetitorAnalysisResponse analyze(CompetitorAnalysisRequest request) {
        return assemblerService.analyze(request);
    }

    public List<String> getTenderEntities() {
        return assemblerService.getTenderEntities();
    }

    public List<String> getProjectNames(String query) {
        return assemblerService.getProjectNames(query);
    }
}
