package com.xiyu.bid.projectworkflow.parser;

import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ScoreDraftDraftAssembler {

    private final ProjectScoreDraftMapper draftMapper;

    public List<ProjectScoreDraft> assemble(Long projectId, String fileName, List<ParsedSection> sections) {
        List<ProjectScoreDraft> drafts = new ArrayList<>();
        for (ParsedSection section : sections) {
            int rowIndex = 0;
            for (DraftSeed seed : section.seeds()) {
                drafts.add(draftMapper.buildDraft(projectId, fileName, section.category(), seed, section.sectionIndex(), rowIndex++));
            }
        }
        return drafts;
    }
}
