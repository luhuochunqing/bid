package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/** Explicit project-scoped refresh never reparses files, activates candidates or writes stage 2. */
@Service
@RequiredArgsConstructor
public class ScoreEstimateRefreshService {
    private final ProjectAccessScopeService access;
    private final ScoreItemRepository items;
    private final EstimatedScoreService estimates;
    private final ScoreEstimateFreshness freshness;

    public void refresh(Long projectId) {
        access.assertCurrentUserCanAccessProject(projectId);
        if (items.findByProjectIdOrderByItemIndexAsc(projectId).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"没有已生效评分项，无法更新预判");
        if (!estimates.refreshForProject(projectId))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"评分规则已变化，请刷新后重试");
        boolean stale=items.findByProjectIdOrderByItemIndexAsc(projectId).stream()
                .anyMatch(i -> "STALE".equals(freshness.status(i,java.time.LocalDate.now())));
        if (stale) throw new ResponseStatusException(HttpStatus.CONFLICT,"预判输入已变化或计算失败，请重新更新");
    }
}
