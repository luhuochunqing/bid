package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BidChapterDirtySetTest {

    @Test
    void marksOnlyChangedChapter() {
        String original = "# 商务\n原商务\n# 技术\n原技术\n";
        String changed = "# 商务\n原商务\n# 技术\n新技术\n";
        var previous = BidChapterDirtySet.toHashMap(BidChapterDirtySet.split(original));
        List<String> dirty = BidChapterDirtySet.dirtyTitles(BidChapterDirtySet.split(changed), previous);
        assertThat(dirty).containsExactly("技术");
    }

    @Test
    void emptyWhenNoHeadings() {
        assertThat(BidChapterDirtySet.split("没有任何标题的一大段文字")).isEmpty();
    }
}
