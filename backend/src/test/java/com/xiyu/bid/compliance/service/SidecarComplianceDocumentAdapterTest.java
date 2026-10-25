package com.xiyu.bid.compliance.service;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.PageImage;
import com.xiyu.bid.compliance.domain.policy.AuthorizationLetterCheckPolicy;
import com.xiyu.bid.compliance.domain.policy.DocumentPageVisionDetector;
import com.xiyu.bid.compliance.domain.policy.ScanPageTextPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SidecarComplianceDocumentAdapterTest {
    @Test
    void selectedScannedPageFlowsThroughImagePortAndTextFusionWithoutChangingFileHash() {
        var adapter = new SidecarComplianceDocumentAdapter(null, (file, name, selected) -> {
            assertThat(selected).contains(10, 11, 1048).doesNotContain(500);
            return List.of(new PageImage(1, "image/png", "page", 11, 100, 100, "aGk="));
        });
        DocumentPageVisionDetector detector = document -> List.of(new ScanPageTextPolicy.Transcription(11,
                "授权委托书\n法定代表人：张三 委托代理人：李四 授权期限：自签署之日至投标有效期结束。", 0.95));
        ReflectionTestUtils.setField(adapter, "documentPageVisionDetector", detector);
        var original = new DocumentContent("bid.pdf", "frozen-hash", "正文", List.of(
                new DocumentContent.PageContent(10, "法定代表人身份证明", List.of()),
                new DocumentContent.PageContent(11, "11", List.of()),
                new DocumentContent.PageContent(500, "普通技术正文", List.of()),
                new DocumentContent.PageContent(1048, "承诺函 投标人：（盖章）", List.of())));
        var actual = adapter.withPageImages(Path.of("unused.pdf"), "bid.pdf", original);
        assertThat(actual.fileHash()).isEqualTo("frozen-hash");
        assertThat(AuthorizationLetterCheckPolicy.check(actual).evidences().getFirst().pageNumber()).isEqualTo(11);
    }
}
