package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.repository.ScoreItemRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.scoreparse.dto.ScoreEstimateRefreshDTO;
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

    public ScoreEstimateRefreshDTO.Refresh refresh(Long projectId) {
        access.assertCurrentUserCanAccessProject(projectId);
        if (items.findByProjectIdOrderByItemIndexAsc(projectId).isEmpty())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"没有已生效评分项，无法更新预判");
        var calculated = estimates.refreshItemsForProject(projectId);
        if (calculated == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"评分规则已变化，请刷新后重试");
        if (calculated.isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT,"未更新任何评分项，请刷新后重试");
        boolean stale=items.findByProjectIdOrderByItemIndexAsc(projectId).stream()
                .anyMatch(i -> "STALE".equals(freshness.status(i,java.time.LocalDate.now())));
        if (stale) throw new ResponseStatusException(HttpStatus.CONFLICT,"预判输入已变化或计算失败，请重新更新");
        var pending = calculated.stream().filter(i -> i.getEstScore() == null)
                .map(i -> new ScoreEstimateRefreshDTO.PendingItem(i.getId(), i.getDim(),
                        i.getEstBasis() == null || i.getEstBasis().isBlank() ? "缺少可自动计算的依据，需人工确认" : i.getEstBasis()))
                .toList();
        return new ScoreEstimateRefreshDTO.Refresh(calculated.size(), calculated.size() - pending.size(),
                pending.size(), pending, calculated.stream().map(i -> new ScoreEstimateRefreshDTO.SavedItem(
                        i.getId(), i.getCode(), i.getDim(), i.getDetail(), i.getWeight(), i.getScoreType(),
                        i.getStatusStage1(), i.getEstScore(), i.getEstBasis(), i.getKbHit())).toList());
    }
}
