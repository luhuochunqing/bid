package com.xiyu.bid.bidresult.service;

import com.xiyu.bid.bidresult.dto.BidResultSyncResponseDTO;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.entity.BidResultSyncLog;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.bidresult.repository.BidResultSyncLogRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BidResultSyncAppService {

    private final BidResultFetchResultRepository fetchResultRepository;
    private final BidResultSyncLogRepository syncLogRepository;
    private final ProjectRepository projectRepository;
    private final BidResultProjectAccessGuard accessGuard;

    @Transactional
    public BidResultSyncResponseDTO syncInternal(Long userId, String userName) {
        int affected = seedMockResults(BidResultSyncLog.OperationType.SYNC, BidResultFetchResult.Status.CONFIRMED);
        syncLogRepository.save(BidResultSyncLog.builder()
                .operationType(BidResultSyncLog.OperationType.SYNC)
                .source("internal-mock")
                .message("已同步内部 ERP/CRM 数据 (Mock)")
                .affectedCount(affected)
                .operatorId(userId)
                .operatorName(userName)
                .build());
        return BidResultSyncResponseDTO.builder()
                .affectedCount(affected)
                .message("已同步内部 ERP/CRM 数据 (Mock)")
                .build();
    }

    @Transactional
    public BidResultSyncResponseDTO fetchPublicResults(Long userId, String userName) {
        int affected = seedMockResults(BidResultSyncLog.OperationType.FETCH, BidResultFetchResult.Status.PENDING);
        syncLogRepository.save(BidResultSyncLog.builder()
                .operationType(BidResultSyncLog.OperationType.FETCH)
                .source("public-mock")
                .message("已完成公开投标信息同步 (Mock)")
                .affectedCount(affected)
                .operatorId(userId)
                .operatorName(userName)
                .build());
        return BidResultSyncResponseDTO.builder()
                .affectedCount(affected)
                .message("已完成公开投标信息同步 (Mock)")
                .build();
    }

    private int seedMockResults(BidResultSyncLog.OperationType type, BidResultFetchResult.Status status) {
        List<Project> projects = accessGuard.filterAccessible(projectRepository.findAll(), Project::getId)
                .stream()
                .limit(3)
                .toList();
        int affected = 0;
        for (int i = 0; i < projects.size(); i++) {
            Project project = projects.get(i);
            BidResultFetchResult entity = BidResultFetchResult.builder()
                    .source(type == BidResultSyncLog.OperationType.SYNC ? "内部系统" : "公开信息")
                    .tenderId(project.getTenderId())
                    .projectId(project.getId())
                    .projectName(project.getName())
                    .result(i % 2 == 0 ? BidResultFetchResult.Result.WON : BidResultFetchResult.Result.LOST)
                    .amount(type == BidResultSyncLog.OperationType.SYNC ? BigDecimal.valueOf(100000 + i * 5000L) : null)
                    .fetchTime(LocalDateTime.now().minusHours(i))
                    .status(status)
                    .confirmedAt(status == BidResultFetchResult.Status.CONFIRMED ? LocalDateTime.now() : null)
                    .registrationType(type == BidResultSyncLog.OperationType.SYNC
                            ? BidResultFetchResult.RegistrationType.SYNC
                            : BidResultFetchResult.RegistrationType.FETCH)
                    .remark(type == BidResultSyncLog.OperationType.SYNC ? "内部同步结果" : "公开同步待确认")
                    .build();
            fetchResultRepository.save(entity);
            affected++;
        }
        return affected;
    }
}
