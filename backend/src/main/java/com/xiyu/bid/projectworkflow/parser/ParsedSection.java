package com.xiyu.bid.projectworkflow.parser;

import java.util.List;

record ParsedSection(
        String category,
        int sectionIndex,
        List<DraftSeed> seeds
) {
}
