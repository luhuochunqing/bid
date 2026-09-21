package com.xiyu.bid.ai.compliance;

import com.xiyu.bid.compliance.domain.PageImage;
import com.xiyu.bid.compliance.domain.PageImageExtractor;
import com.xiyu.bid.scoreparse.application.SidecarImageExtractor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** 把既有 scoreparse Sidecar 图片客户端适配为质检域端口。 */
@Component
public class SidecarPageImageExtractorAdapter implements PageImageExtractor {

    private final SidecarImageExtractor delegate;

    public SidecarPageImageExtractorAdapter(final SidecarImageExtractor delegate) {
        this.delegate = delegate;
    }

    @Override
    public List<PageImage> extract(final Path file, final String fileName, final Set<Integer> pages)
            throws IOException, InterruptedException {
        return delegate.renderPages(file, fileName, pages).images().stream()
                .map(image -> new PageImage(image.index(), image.mimeType(), image.section(), image.pageHint(),
                        image.width(), image.height(), image.base64()))
                .toList();
    }
}
