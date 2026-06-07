package com.xiyu.bid.docinsight.infrastructure.openai;

import com.xiyu.bid.docinsight.application.DocumentAnalysisInput;
import com.xiyu.bid.docinsight.application.DocumentAnalysisResult;
import com.xiyu.bid.docinsight.application.DocumentAnalyzer;
import com.xiyu.bid.docinsight.domain.DocumentChunk;
import com.xiyu.bid.docinsight.domain.StructuralDocumentChunker;
import lombok.extern.slf4j.Slf4j;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public abstract class BaseOpenAiDocumentAnalyzer<T> implements DocumentAnalyzer {

    private final StructuralDocumentChunker structuralChunker;

    protected BaseOpenAiDocumentAnalyzer(StructuralDocumentChunker structuralChunker) {
        this.structuralChunker = structuralChunker;
    }

    protected StructuralDocumentChunker getStructuralChunker() {
        return structuralChunker;
    }

    @Override
    public DocumentAnalysisResult analyze(DocumentAnalysisInput input) {
        log.info("Analyzing document {} using profile {}", input.documentId(), input.profileCode());
        
        List<T> partResults = new ArrayList<>();
        List<DocumentChunk> chunks = input.chunks();
        
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            String prompt = buildPrompt(input, chunk, i + 1, chunks.size());
            T result = requestAi(prompt, input);
            partResults.add(result);
        }
        
        return mergeAndMap(input, partResults);
    }

    protected abstract String buildPrompt(DocumentAnalysisInput input, DocumentChunk chunk, int index, int total);
    
    protected abstract T requestAi(String prompt);

    protected T requestAi(String prompt, DocumentAnalysisInput input) {
        return requestAi(prompt);
    }
    
    protected abstract DocumentAnalysisResult mergeAndMap(DocumentAnalysisInput input, List<T> results);

    protected String getSectionContext(DocumentChunk chunk) {
        return chunk.sectionPath().isEmpty() ? "" : 
                "\n当前正文所属章节路径: " + String.join(" > ", chunk.sectionPath());
    }
}
