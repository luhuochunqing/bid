package com.xiyu.bid.compliance.domain;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/** 页面图像抽取端口，质检域不依赖具体 Sidecar 或 scoreparse 模块。 */
@FunctionalInterface
public interface PageImageExtractor {
    List<PageImage> extract(Path file, String fileName, Set<Integer> pages)
            throws IOException, InterruptedException;
}
