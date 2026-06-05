package com.xiyu.bid.documenteditor.imports;

import com.xiyu.bid.documenteditor.dto.DraftTreeSkippedSectionDTO;
import com.xiyu.bid.documenteditor.dto.DraftTreeUpsertResultDTO;
import com.xiyu.bid.documenteditor.entity.DocumentSection;
import com.xiyu.bid.documenteditor.entity.DocumentSectionLock;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * State object for document draft tree import process.
 */
public final class DocumentDraftTreeImportState {

    private final Long projectId;
    private final Long structureId;
    private final boolean structureCreated;
    private final Map<String, DocumentSection> sectionsByStableKey;
    private final Map<String, DocumentSection> sectionsByTitle;
    private final Map<Long, DocumentSectionLock> locksBySectionId;
    private final ImportStats stats = new ImportStats();

    public DocumentDraftTreeImportState(
            Long pProjectId,
            Long pStructureId,
            boolean pStructureCreated,
            Map<String, DocumentSection> pSectionsByStableKey,
            Map<String, DocumentSection> pSectionsByTitle,
            Map<Long, DocumentSectionLock> pLocksBySectionId
    ) {
        this.projectId = pProjectId;
        this.structureId = pStructureId;
        this.structureCreated = pStructureCreated;
        this.sectionsByStableKey = pSectionsByStableKey;
        this.sectionsByTitle = pSectionsByTitle;
        this.locksBySectionId = pLocksBySectionId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public Long getStructureId() {
        return structureId;
    }

    public boolean isStructureCreated() {
        return structureCreated;
    }

    public Map<String, DocumentSection> getSectionsByStableKey() {
        return sectionsByStableKey;
    }

    public Map<String, DocumentSection> getSectionsByTitle() {
        return sectionsByTitle;
    }

    public Map<Long, DocumentSectionLock> getLocksBySectionId() {
        return locksBySectionId;
    }

    public ImportStats getStats() {
        return stats;
    }

    public DraftTreeUpsertResultDTO toResult() {
        return DraftTreeUpsertResultDTO.builder()
                .projectId(projectId)
                .structureId(structureId)
                .structureCreated(structureCreated)
                .totalSections(stats.getTotal())
                .createdSections(stats.getCreated())
                .updatedSections(stats.getUpdated())
                .skippedSectionsCount(stats.getSkipped())
                .skippedSections(stats.getSkippedSections())
                .build();
    }

    /**
     * Statistics for the import process.
     */
    public static final class ImportStats {
        private int total;
        private int created;
        private int updated;
        private int skipped;
        private final List<DraftTreeSkippedSectionDTO> skippedSections =
                new ArrayList<>();

        public int getTotal() {
            return total;
        }

        public void setTotal(int total) {
            this.total = total;
        }

        public void incrementTotal() {
            this.total++;
        }

        public int getCreated() {
            return created;
        }

        public void setCreated(int created) {
            this.created = created;
        }

        public void incrementCreated() {
            this.created++;
        }

        public int getUpdated() {
            return updated;
        }

        public void setUpdated(int updated) {
            this.updated = updated;
        }

        public void incrementUpdated() {
            this.updated++;
        }

        public int getSkipped() {
            return skipped;
        }

        public void setSkipped(int skipped) {
            this.skipped = skipped;
        }

        public void incrementSkipped() {
            this.skipped++;
        }

        public List<DraftTreeSkippedSectionDTO> getSkippedSections() {
            return skippedSections;
        }
    }
}
