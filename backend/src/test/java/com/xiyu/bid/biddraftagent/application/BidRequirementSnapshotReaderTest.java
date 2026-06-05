package com.xiyu.bid.biddraftagent.application;

import com.xiyu.bid.biddraftagent.entity.BidRequirementItem;
import com.xiyu.bid.biddraftagent.entity.BidTenderDocumentSnapshot;
import com.xiyu.bid.biddraftagent.repository.BidRequirementItemRepository;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BidRequirementSnapshotReaderTest {

    private BidRequirementItemRepository requirementItemRepository;
    private BidTenderDocumentSnapshotRepository documentSnapshotRepository;
    private BidRequirementSnapshotReader reader;

    @BeforeEach
    void setUp() {
        requirementItemRepository = mock(BidRequirementItemRepository.class);
        documentSnapshotRepository = mock(BidTenderDocumentSnapshotRepository.class);
        reader = new BidRequirementSnapshotReader(requirementItemRepository, documentSnapshotRepository);
    }

    @Test
    void latestRequirementsForProject_shouldReadOnlyLatestTenderDocumentSnapshotItems() {
        when(documentSnapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(11L))
                .thenReturn(Optional.of(BidTenderDocumentSnapshot.builder()
                        .projectId(11L)
                        .projectDocumentId(202L)
                        .build()));
        when(requirementItemRepository.findByProjectIdAndProjectDocumentIdOrderByCreatedAtDesc(11L, 202L))
                .thenReturn(List.of(requirement("technical", "实施方案")));

        List<BidRequirementItem> items = reader.latestRequirementsForProject(11L);

        assertThat(items).extracting(BidRequirementItem::getTitle)
                .containsExactly("实施方案");
        verify(requirementItemRepository, never()).findByProjectIdOrderByCreatedAtDesc(any());
    }

    @Test
    void latestRequirementsForProject_shouldReturnEmptyWhenNoSnapshotExists() {
        when(documentSnapshotRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(11L))
                .thenReturn(Optional.empty());

        assertThat(reader.latestRequirementsForProject(11L)).isEmpty();
        verify(requirementItemRepository, never()).findByProjectIdOrderByCreatedAtDesc(any());
    }

    @Test
    void requirementsForSnapshot_shouldRejectUnknownSnapshot() {
        when(documentSnapshotRepository.findByIdAndProjectId(404L, 11L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reader.requirementsForSnapshot(11L, 404L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void requirementsForSnapshot_shouldReadSnapshotDocumentItems() {
        when(documentSnapshotRepository.findByIdAndProjectId(99L, 11L))
                .thenReturn(Optional.of(BidTenderDocumentSnapshot.builder()
                        .projectId(11L)
                        .projectDocumentId(303L)
                        .build()));
        when(requirementItemRepository.findByProjectIdAndProjectDocumentIdOrderByCreatedAtDesc(11L, 303L))
                .thenReturn(List.of(requirement("commercial", "商务响应")));

        List<BidRequirementItem> items = reader.requirementsForSnapshot(11L, 99L);

        assertThat(items).extracting(BidRequirementItem::getTitle)
                .containsExactly("商务响应");
        verify(requirementItemRepository, never()).findByProjectIdOrderByCreatedAtDesc(any());
    }

    private BidRequirementItem requirement(String category, String title) {
        return BidRequirementItem.builder()
                .category(category)
                .title(title)
                .build();
    }
}
