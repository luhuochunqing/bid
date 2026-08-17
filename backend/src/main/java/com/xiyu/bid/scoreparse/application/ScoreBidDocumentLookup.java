package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.application.ExtractedTenderDocument;
import com.xiyu.bid.biddraftagent.application.LoadedTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentStorage;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;

import java.util.List;

/** 投标文件查找与正文读取。 */
final class ScoreBidDocumentLookup {

    private final ProjectDocumentRepository projectDocumentRepository;
    private final TenderDocumentStorage documentStorage;
    private final TenderDocumentTextExtractor textExtractor;

    ScoreBidDocumentLookup(ProjectDocumentRepository projectDocumentRepository,
                           TenderDocumentStorage documentStorage,
                           TenderDocumentTextExtractor textExtractor) {
        this.projectDocumentRepository = projectDocumentRepository;
        this.documentStorage = documentStorage;
        this.textExtractor = textExtractor;
    }

    String latestName(Long projectId) {
        List<ProjectDocument> docs = findBidDocuments(projectId);
        return docs.isEmpty() ? null : docs.get(0).getName();
    }

    List<ProjectDocument> findBidDocuments(Long projectId) {
        for (String cat : List.of("BID", "BID_FILE", "BID_DOCUMENT")) {
            List<ProjectDocument> docs = projectDocumentRepository
                    .findByProjectIdAndFiltersOrderByCreatedAtDesc(projectId, cat, null, null);
            if (!docs.isEmpty()) {
                return docs;
            }
        }
        return List.of();
    }

    byte[] loadBytes(String fileUrl) {
        return documentStorage.loadByFileUrl(fileUrl)
                .map(LoadedTenderDocument::content)
                .orElseThrow(() -> new IllegalStateException("投标文件加载失败"));
    }

    String loadText(Long projectId) {
        List<ProjectDocument> bidDocs = findBidDocuments(projectId);
        if (bidDocs.isEmpty()) {
            throw new IllegalStateException("投标文件不存在，项目: " + projectId);
        }
        ProjectDocument bidDoc = bidDocs.get(0);
        LoadedTenderDocument loaded = documentStorage.loadByFileUrl(bidDoc.getFileUrl())
                .orElseThrow(() -> new IllegalStateException("投标文件加载失败: " + bidDoc.getFileUrl()));
        ExtractedTenderDocument extracted = textExtractor.extract(bidDoc.getName(), null, loaded.content());
        return extracted.text();
    }
}
