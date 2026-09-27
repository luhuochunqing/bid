package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** File-by-file shell: one unavailable source never discards other acquired artifacts. */
final class SourceArtifactAcquisition {
    private SourceArtifactAcquisition() {}
    record Result(DocumentArtifact artifact, List<String> diagnostics) {}

    static Result acquire(List<LoadedBidFile> files, List<TenderTextSource> sources,
                          SidecarDocumentArtifactExtractor extractor) {
        List<DocumentArtifact> artifacts = new ArrayList<>();
        List<String> diagnostics = new ArrayList<>();
        boolean interrupted = false;
        for (int i = 0; i < files.size(); i++) {
            DocumentArtifact artifact = null;
            String failure = null;
            if (interrupted) failure = "Interrupted";
            else if (extractor == null) failure = "ExtractorUnavailable";
            else {
                String name = i < sources.size() ? sources.get(i).fileName() : "tender-" + i;
                try {
                    artifact = switch (files.get(i)) {
                        case LoadedBidFile.InMemory memory -> extractor.extract(memory.content(), name);
                        case LoadedBidFile.OnDisk disk -> extractor.extract(disk.tempFile(), name);
                    };
                    if (artifact == null || artifact.artifact() == null) failure = "MissingArtifact";
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    interrupted = true;
                    failure = "Interrupted";
                } catch (IOException | RuntimeException exception) {
                    failure = exception.getClass().getSimpleName();
                }
            }
            // Null slots preserve the original document index and therefore locator identity.
            artifacts.add(artifact);
            if (failure != null) diagnostics.add("SOURCE_ARTIFACT_UNAVAILABLE:doc" + i + ":" + failure);
        }
        return new Result(SourceArtifactBundle.combine(artifacts), List.copyOf(diagnostics));
    }
}
