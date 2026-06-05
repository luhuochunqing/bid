// Input: query/command services
// Output: compatibility facade for legacy tender service callers
// Pos: Service/兼容门面
// 维护声明: 新业务逻辑放入 TenderQueryService 或 TenderCommandService；本类仅保留旧调用入口.

package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.tender.dto.TenderDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TenderService {

    private final TenderQueryService tenderQueryService;
    private final TenderCommandService tenderCommandService;

    public List<TenderDTO> getAllTenders() {
        return tenderQueryService.searchTenders(TenderSearchCriteria.empty());
    }

    public List<TenderDTO> searchTenders(TenderSearchCriteria criteria) {
        return tenderQueryService.searchTenders(criteria);
    }

    public TenderDTO getTenderById(Long id) {
        return tenderQueryService.getTenderById(id);
    }

    public TenderDTO createTender(TenderDTO tenderDTO) {
        return tenderCommandService.createTender(tenderDTO);
    }

    public TenderDTO updateTender(Long id, TenderDTO tenderDTO) {
        return tenderCommandService.updateTender(id, tenderDTO);
    }

    public void deleteTender(Long id) {
        tenderCommandService.deleteTender(id);
    }

    public List<TenderDTO> getTendersByStatus(Tender.Status status) {
        return tenderQueryService.getTendersByStatus(status);
    }

    public List<TenderDTO> getTendersBySource(String source) {
        return tenderQueryService.getTendersBySource(source);
    }

    public TenderDTO analyzeTender(Long id) {
        return tenderCommandService.analyzeTender(id);
    }

    public Map<Tender.Status, Long> getTenderStatistics() {
        return tenderQueryService.getTenderStatistics();
    }
}
