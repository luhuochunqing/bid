package com.xiyu.bid.ai.compliance;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.PageImage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiDepositReceiptVisionDetectorTest {

    @Test
    void candidateImages_selectCuePageAndTwoPageNeighborhood() {
        var pages = List.of(
                new DocumentContent.PageContent(2, "普通正文", List.of()),
                new DocumentContent.PageContent(10, "投标保证金缴纳凭证", List.of()));
        var images = List.of(image(2), image(8), image(10), image(12), image(20));
        var document = new DocumentContent("bid.pdf", "hash", "正文", pages, images);

        assertThat(OpenAiDepositReceiptVisionDetector.candidateImages(document))
                .extracting(PageImage::pageHint).containsExactly(8, 10, 12);
    }

    private static PageImage image(final int page) {
        return new PageImage(page, "image/png", "page", page, 1000, 1400, "aGk=");
    }
}
