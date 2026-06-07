package com.xiyu.bid.biddraftagent.infrastructure.documenteditor;

import com.xiyu.bid.biddraftagent.application.BidDraftAgentDocumentSection;
import com.xiyu.bid.biddraftagent.application.BidDraftAgentDocumentWritePlan;
import com.xiyu.bid.biddraftagent.application.BidDraftAgentDocumentWriteResult;
import com.xiyu.bid.biddraftagent.application.BidDraftAgentDocumentWriter;
import com.xiyu.bid.biddraftagent.application.BidDraftAgentSkippedSection;
import com.xiyu.bid.documenteditor.dto.DraftTreeSkippedSectionDTO;
import com.xiyu.bid.documenteditor.dto.DraftTreeUpsertNodeRequest;
import com.xiyu.bid.documenteditor.dto.DraftTreeUpsertRequest;
import com.xiyu.bid.documenteditor.dto.DraftTreeUpsertResultDTO;
import com.xiyu.bid.documenteditor.entity.SectionType;
import com.xiyu.bid.documenteditor.service.DocumentEditorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SpringDocumentEditorBidDraftAgentWriter implements BidDraftAgentDocumentWriter {

    private final DocumentEditorService documentEditorService;

    @Override
    public BidDraftAgentDocumentWriteResult write(Long projectId, BidDraftAgentDocumentWritePlan plan) {
        DraftTreeUpsertResultDTO result = documentEditorService.upsertDraftTree(projectId, toRequest(plan));
        return new BidDraftAgentDocumentWriteResult(
                result.getProjectId(),
                result.getStructureId(),
                Boolean.TRUE.equals(result.getStructureCreated()),
                valueOrZero(result.getTotalSections()),
                valueOrZero(result.getCreatedSections()),
                valueOrZero(result.getUpdatedSections()),
                valueOrZero(result.getSkippedSectionsCount()),
                result.getSkippedSections().stream().map(this::toSkippedSection).toList()
        );
    }

    private DraftTreeUpsertRequest toRequest(BidDraftAgentDocumentWritePlan plan) {
        return DraftTreeUpsertRequest.builder()
                .structureName(plan.structureName())
                .sections(plan.sections().stream()
                        .map(section -> toNode(plan.runId(), section, SectionType.CHAPTER))
                        .toList())
                .build();
    }

    private DraftTreeUpsertNodeRequest toNode(
            String runId,
            BidDraftAgentDocumentSection section,
            SectionType sectionType
    ) {
        return DraftTreeUpsertNodeRequest.builder()
                .sectionKey(section.sectionKey())
                .title(section.title())
                .sectionType(sectionType)
                .content(section.content())
                .runId(runId)
                .sourceReferences(section.sourceReferences())
                .confidence(section.confidence())
                .manual(section.manual())
                .children(section.children().stream()
                        .map(child -> toNode(runId, child, SectionType.SECTION))
                        .toList())
                .build();
    }

    private BidDraftAgentSkippedSection toSkippedSection(DraftTreeSkippedSectionDTO skipped) {
        return new BidDraftAgentSkippedSection(
                skipped.getSectionId(),
                skipped.getSectionKey(),
                skipped.getTitle(),
                Boolean.TRUE.equals(skipped.getLocked()),
                skipped.getReason()
        );
    }

    private int valueOrZero(Integer value) {
        return value == null ? 0 : value;
    }
}
