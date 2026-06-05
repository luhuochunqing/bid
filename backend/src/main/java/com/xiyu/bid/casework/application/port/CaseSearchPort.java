package com.xiyu.bid.casework.application.port;

import com.xiyu.bid.casework.domain.model.CaseSearchCriteria;
import com.xiyu.bid.entity.Case;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;

public interface CaseSearchPort {

    Page<Case> search(CaseSearchCriteria criteria, Pageable pageable);

    List<Case> findRelatedCandidates(Long excludedCaseId, Pageable pageable);

    List<String> findDistinctProductLines();

    List<String> findDistinctStatuses();

    List<String> findDistinctVisibilities();

    List<String> findDistinctTags();

    Sort resolveSort(String sort);
}
