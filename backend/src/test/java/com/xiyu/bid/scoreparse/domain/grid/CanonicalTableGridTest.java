// Tests for CanonicalTableGrid / CanonicalCell / MergedRegion / StructureValidationResult
// and CanonicalTableGridFactory.  Pure-unit, no Spring/JPA dependencies.
package com.xiyu.bid.scoreparse.domain.grid;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.MergedRegionDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanonicalTableGridTest {

    private static CanonicalCellDto originCell(
            String id, int row, int col, String raw,
            int rowspan, int colspan, boolean cont) {
        return new CanonicalCellDto(
                id, row, col, raw, raw.replaceAll("\\s+", " "),
                rowspan, colspan, cont,
                "/w:document[1]/w:body[1]/w:tbl[1]/w:tr[" + (row + 1) + "]/w:tc[1]");
    }

    private static CanonicalTable simple2x2() {
        return new CanonicalTable(
                "t-0000", null, null,
                2, 2,
                List.of(),
                List.of(
                        originCell("t-0000-r0-c0", 0, 0, "评分因素", 1, 1, false),
                        originCell("t-0000-r0-c1", 0, 1, "分值", 1, 1, false),
                        originCell("t-0000-r1-c0", 1, 0, "技术方案", 1, 1, false),
                        originCell("t-0000-r1-c1", 1, 1, "30", 1, 1, false)
                ),
                true);
    }

    @Nested
    @DisplayName("Cell record invariants (FP-Java: validate() not throw)")
    class CellInvariants {

        @Test
        void validate_rejects_zero_rowspan() {
            Optional<String> v = new CanonicalCell("c", 0, 0, "x", "x", 0, 1, false, "").validate();
            assertTrue(v.isPresent());
            assertTrue(v.get().contains("rowspan must be >= 1"));
        }

        @Test
        void validate_rejects_zero_colspan() {
            Optional<String> v = new CanonicalCell("c", 0, 0, "x", "x", 1, 0, false, "").validate();
            assertTrue(v.isPresent());
            assertTrue(v.get().contains("colspan must be >= 1"));
        }

        @Test
        void validate_rejects_negative_coordinates() {
            Optional<String> rowV = new CanonicalCell("c", -1, 0, "x", "x", 1, 1, false, "").validate();
            assertTrue(rowV.isPresent());
            Optional<String> colV = new CanonicalCell("c", 0, -1, "x", "x", 1, 1, false, "").validate();
            assertTrue(colV.isPresent());
        }

        @Test
        void validate_accepts_minimal_valid_cell() {
            Optional<String> v = new CanonicalCell("c", 0, 0, "x", "x", 1, 1, false, "").validate();
            assertTrue(v.isEmpty());
        }

        @Test
        void validate_rejects_blank_cell_id() {
            Optional<String> v = new CanonicalCell("", 0, 0, "x", "x", 1, 1, false, "").validate();
            assertTrue(v.isPresent());
            assertTrue(v.get().contains("cellId"));
        }

        @Test
        void usable_origin_requires_non_blank_text() {
            CanonicalCell blank = new CanonicalCell("c", 0, 0, "", "", 1, 1, false, "");
            assertFalse(blank.isUsableOrigin());

            CanonicalCell cont = new CanonicalCell("c", 0, 0, "x", "x", 1, 1, true, "");
            assertFalse(cont.isUsableOrigin());

            CanonicalCell good = new CanonicalCell("c", 0, 0, "技术方案", "技术方案", 1, 1, false, "");
            assertTrue(good.isUsableOrigin());
        }

        @Test
        void normalizes_null_strings_to_empty() {
            CanonicalCell cell = new CanonicalCell("c", 0, 0, null, null, 1, 1, false, null);
            assertEquals("", cell.rawText());
            assertEquals("", cell.normalizedText());
            assertEquals("", cell.sourceXmlPath());
        }
    }

    @Nested
    @DisplayName("MergedRegion invariants (FP-Java: validate() not throw)")
    class MergedRegionInvariants {

        @Test
        void validate_rejects_1x1_region() {
            Optional<String> v = new MergedRegion("c", 1, 1).validate();
            assertTrue(v.isPresent());
            assertTrue(v.get().contains("no-op"));
        }

        @Test
        void validate_rejects_blank_start_cell_id() {
            Optional<String> v = new MergedRegion("", 2, 1).validate();
            assertTrue(v.isPresent());
            assertTrue(v.get().contains("startCellId"));
        }

        @Test
        void validate_rejects_zero_or_negative_dimensions() {
            assertTrue(new MergedRegion("c", 0, 1).validate().isPresent());
            assertTrue(new MergedRegion("c", 1, 0).validate().isPresent());
            assertTrue(new MergedRegion("c", -1, 1).validate().isPresent());
            assertTrue(new MergedRegion("c", 1, -2).validate().isPresent());
        }

        @Test
        void validate_accepts_valid_2x1_and_1x2_regions() {
            assertTrue(new MergedRegion("c", 2, 1).validate().isEmpty());
            assertTrue(new MergedRegion("c", 1, 2).validate().isEmpty());
        }

        @Test
        void single_row_helper() {
            MergedRegion h = new MergedRegion("c", 1, 2);
            assertTrue(h.isSingleRow());
            assertFalse(h.isSingleColumn());
        }

        @Test
        void single_column_helper() {
            MergedRegion v = new MergedRegion("c", 2, 1);
            assertFalse(v.isSingleRow());
            assertTrue(v.isSingleColumn());
        }
    }

    @Nested
    @DisplayName("StructureValidationResult")
    class StructureValidationResultTests {

        @Test
        void empty_is_valid() {
            StructureValidationResult r = StructureValidationResult.empty();
            assertTrue(r.valid());
            assertEquals(StructureValidationResult.Status.VALID, r.status());
            assertTrue(r.violations().isEmpty());
        }

        @Test
        void ok_returns_valid() {
            StructureValidationResult r = StructureValidationResult.ok();
            assertTrue(r.valid());
        }

        @Test
        void incomplete_collects_violations() {
            StructureValidationResult r = StructureValidationResult.incomplete(
                    List.of(new StructureValidationResult.Violation("c1", "span mismatch")));
            assertFalse(r.valid());
            assertEquals(StructureValidationResult.Status.STRUCTURE_INCOMPLETE, r.status());
            assertEquals(1, r.violations().size());
            assertEquals("c1", r.violations().get(0).field());
        }
    }

    @Nested
    @DisplayName("CanonicalTableGridFactory: valid inputs")
    class ValidInputs {

        @Test
        void plain_table_validates() {
            CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(simple2x2());
            assertTrue(grid.isStructurallyValid());
            assertEquals(2, grid.rows());
            assertEquals(2, grid.columns());
            assertEquals(4, grid.cells().size());
            assertEquals(0, grid.mergedRegions().size());
            assertTrue(grid.findCell("t-0000-r0-c0").isPresent());
        }

        @Test
        void cells_in_row_returns_correct_subset() {
            CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(simple2x2());
            List<CanonicalCell> row0 = grid.cellsInRow(0);
            assertEquals(2, row0.size());
            assertTrue(row0.stream().allMatch(c -> c.row() == 0));
        }

        @Test
        void vmerge_region_validates() {
            CanonicalTable tbl = new CanonicalTable(
                    "t-0001", null, null, 3, 2,
                    List.of(new MergedRegionDto("t-0001-r1-c0", 2, 1)),
                    List.of(
                            originCell("t-0001-r0-c0", 0, 0, "评分因素", 1, 1, false),
                            originCell("t-0001-r0-c1", 0, 1, "分值", 1, 1, false),
                            originCell("t-0001-r1-c0", 1, 0, "技术方案", 2, 1, false),
                            originCell("t-0001-r1-c1", 1, 1, "30", 1, 1, false),
                            originCell("t-0001-r2-c0", 2, 0, "", 1, 1, true),
                            originCell("t-0001-r2-c1", 2, 1, "30", 1, 1, false)
                    ),
                    true);
            CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(tbl);
            assertTrue(grid.isStructurallyValid(), grid.validation().violations().toString());
            assertEquals(1, grid.mergedRegions().size());
            assertEquals(2, grid.mergedRegions().get(0).rowspan());
        }

        @Test
        void gridspan_region_validates() {
            CanonicalTable tbl = new CanonicalTable(
                    "t-0002", null, null, 2, 3,
                    List.of(new MergedRegionDto("t-0002-r0-c0", 1, 2)),
                    List.of(
                            originCell("t-0002-r0-c0", 0, 0, "评分标准", 1, 2, false),
                            originCell("t-0002-r0-c1", 0, 1, "", 1, 1, true),
                            originCell("t-0002-r0-c2", 0, 2, "备注", 1, 1, false),
                            originCell("t-0002-r1-c0", 1, 0, "优得30分", 1, 1, false),
                            originCell("t-0002-r1-c1", 1, 1, "良得20分", 1, 1, false),
                            originCell("t-0002-r1-c2", 1, 2, "无", 1, 1, false)
                    ),
                    true);
            CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(tbl);
            assertTrue(grid.isStructurallyValid(), grid.validation().violations().toString());
        }
    }

    @Nested
    @DisplayName("CanonicalTableGridFactory: STRUCTURE_INCOMPLETE paths")
    class IncompletePaths {

        @Test
        void duplicate_cell_id_is_rejected() {
            CanonicalTable tbl = new CanonicalTable(
                    "t-dup", null, null, 1, 2,
                    List.of(),
                    List.of(
                            originCell("dup", 0, 0, "a", 1, 1, false),
                            originCell("dup", 0, 1, "b", 1, 1, false)
                    ),
                    false);
            CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(tbl);
            assertFalse(grid.isStructurallyValid());
            assertTrue(grid.validation().violations().stream()
                    .anyMatch(v -> v.reason().contains("duplicate cellId")));
        }

        @Test
        void missing_cell_in_grid_is_rejected() {
            CanonicalTable tbl = new CanonicalTable(
                    "t-gap", null, null, 2, 2,
                    List.of(),
                    List.of(
                            originCell("t-gap-r0-c0", 0, 0, "a", 1, 1, false),
                            originCell("t-gap-r0-c1", 0, 1, "b", 1, 1, false)
                    ),
                    false);
            CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(tbl);
            assertFalse(grid.isStructurallyValid());
            assertTrue(grid.validation().violations().stream()
                    .anyMatch(v -> v.field().contains("grid[1]")));
        }

        @Test
        void merged_region_with_unknown_start_is_rejected() {
            CanonicalTable tbl = new CanonicalTable(
                    "t-orphan-region", null, null, 1, 1,
                    List.of(new MergedRegionDto("does-not-exist", 1, 2)),
                    List.of(originCell("t-orphan-region-r0-c0", 0, 0, "x", 1, 1, false)),
                    false);
            CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(tbl);
            assertFalse(grid.isStructurallyValid());
            assertTrue(grid.validation().violations().stream()
                    .anyMatch(v -> v.reason().contains("does not reference a cell")));
        }

        @Test
        void merged_region_span_mismatch_is_rejected() {
            CanonicalTable tbl = new CanonicalTable(
                    "t-span-mis", null, null, 2, 2,
                    List.of(new MergedRegionDto("t-span-mis-r0-c0", 2, 2)),
                    List.of(
                            originCell("t-span-mis-r0-c0", 0, 0, "x", 1, 1, false),
                            originCell("t-span-mis-r0-c1", 0, 1, "y", 1, 1, false),
                            originCell("t-span-mis-r1-c0", 1, 0, "", 1, 1, true),
                            originCell("t-span-mis-r1-c1", 1, 1, "z", 1, 1, false)
                    ),
                    false);
            CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(tbl);
            assertFalse(grid.isStructurallyValid());
            assertTrue(grid.validation().violations().stream()
                    .anyMatch(v -> v.reason().contains("span mismatch")));
        }

        @Test
        void missing_continuation_cell_is_rejected() {
            CanonicalTable tbl = new CanonicalTable(
                    "t-no-cont", null, null, 2, 1,
                    List.of(new MergedRegionDto("t-no-cont-r0-c0", 2, 1)),
                    List.of(
                            originCell("t-no-cont-r0-c0", 0, 0, "x", 2, 1, false),
                            originCell("t-no-cont-r1-c0", 1, 0, "y", 1, 1, false)
                    ),
                    false);
            CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(tbl);
            assertFalse(grid.isStructurallyValid());
            assertTrue(grid.validation().violations().stream()
                    .anyMatch(v -> v.reason().contains("expected isMergedContinue")));
        }

        @Test
        void null_payload_returns_invalid() {
            CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(null);
            assertFalse(grid.isStructurallyValid());
            assertEquals(0, grid.rows());
        }

        @Test
        void zero_dimensions_table_is_invalid() {
            CanonicalTable tbl = new CanonicalTable(
                    "t-zero", null, null, 0, 0,
                    List.of(),
                    List.of(),
                    false);
            CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(tbl);
            assertFalse(grid.isStructurallyValid());
        }
    }

    @Nested
    @DisplayName("DocumentArtifact meta invariants")
    class ArtifactMetaInvariants {

        @Test
        void validate_rejects_blank_sha256() {
            Optional<String> v = new ArtifactMeta("doc", "", "DOCX", "table-v2").validate();
            assertTrue(v.isPresent());
            assertTrue(v.get().contains("sha256"));
        }

        @Test
        void validate_rejects_blank_file_type() {
            Optional<String> v = new ArtifactMeta("doc", "abc", "", "table-v2").validate();
            assertTrue(v.isPresent());
            assertTrue(v.get().contains("fileType"));
        }

        @Test
        void validate_accepts_minimal_meta() {
            Optional<String> v = new ArtifactMeta("doc", "abc", "DOCX", "table-v2").validate();
            assertTrue(v.isEmpty());
        }

        @Test
        void supports_schema_version_constant() {
            assertEquals("2.2", DocumentArtifact.SUPPORTED_SCHEMA_VERSION);
            assertNotNull(DocumentArtifact.SUPPORTED_SCHEMA_VERSION);
        }
    }
}
