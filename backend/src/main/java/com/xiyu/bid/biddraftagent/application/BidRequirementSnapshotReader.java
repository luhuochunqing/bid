package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.entity.BidRequirementItem;
import com.xiyu.bid.biddraftagent.entity.BidTenderDocumentSnapshot;
import com.xiyu.bid.biddraftagent.repository.BidRequirementItemRepository;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class BidRequirementSnapshotReader {

    private final BidRequirementItemRepository requirementItemRepository;
    private final BidTenderDocumentSnapshotRepository documentSnapshotRepository;

    public List<BidRequirementItem> latestRequirementsForProject(Long projectId) {
        return documentSnapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(projectId)
                .map(BidTenderDocumentSnapshot::getProjectDocumentId)
                .map(documentId -> requirementItemRepository.findByProjectIdAndProjectDocumentIdOrderByCreatedAtDesc(
                        projectId,
                        documentId
                ))
                .orElse(List.of());
    }

    public List<BidRequirementItem> requirementsForSnapshot(Long projectId, Long snapshotId) {
        return documentSnapshotRepository.findByIdAndProjectId(snapshotId, projectId)
                .map(BidTenderDocumentSnapshot::getProjectDocumentId)
                .map(documentId -> requirementItemRepository.findByProjectIdAndProjectDocumentIdOrderByCreatedAtDesc(
                        projectId,
                        documentId
                ))
                .orElseThrow(() -> new ResourceNotFoundException("BidTenderDocumentSnapshot", String.valueOf(snapshotId)));
    }
}
