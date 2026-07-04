package com.xiyu.bid.bootstrap;

import com.xiyu.bid.casework.application.CaseSliceJsonlImporter;
import com.xiyu.bid.casework.application.service.BatchEmbeddingAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * CLI entry point for importing historical bid case slices and generating embeddings.
 *
 * <p>Activated only when the program argument {@code --import-case-slices=true}
 * is present. It first imports JSONL metadata, then triggers the batch
 * embedding service, and finally refreshes the in-memory vector cache so that
 * recommendations are immediately available.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseSliceImportCommandLineRunner implements CommandLineRunner {

    static final String IMPORT_FLAG = "--import-case-slices=true";

    private final CaseSliceJsonlImporter importer;
    private final BatchEmbeddingAppService batchEmbeddingAppService;
    private final BidCaseSliceVectorCacheInitializer cacheInitializer;

    @Override
    public void run(String... args) {
        if (!isImportEnabled(args)) {
            return;
        }
        log.info("Starting case slice import workflow");
        CaseSliceJsonlImporter.ImportResult importResult = importer.importAll();
        log.info("Case slice import finished: imported={}, skipped={}, files={}",
                importResult.imported(), importResult.skipped(), importResult.filesProcessed());

        BatchEmbeddingAppService.EmbeddingResult embedResult = batchEmbeddingAppService.embedAllUnprocessed();
        log.info("Batch embedding finished: processed={}, failed={}, remaining={}",
                embedResult.processed(), embedResult.failed(), embedResult.remaining());

        cacheInitializer.refreshCache();
    }

    private static boolean isImportEnabled(String[] args) {
        for (String arg : args) {
            if (IMPORT_FLAG.equals(arg)) {
                return true;
            }
        }
        return false;
    }
}
