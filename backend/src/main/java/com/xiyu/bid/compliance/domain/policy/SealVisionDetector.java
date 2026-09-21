package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;

/** 视觉识别副作用边界，生产适配器负责调用真实多模态模型。 */
@FunctionalInterface
public interface SealVisionDetector {
    SealVisionResult detect(DocumentContent document);
}
