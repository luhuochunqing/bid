package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.projectworkflow.entity.ProjectDocument;

/**
 * 可替换的项目文档外部集成边界。
 * 当前由 no-op 实现兜底，后续可由 bid-result 模块在不形成强编译依赖的前提下接入。
 */
public interface ProjectDocumentBindingGateway {

    void onDocumentCreated(ProjectDocument document);

    void onDocumentDeleted(ProjectDocument document);
}
