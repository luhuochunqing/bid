package com.xiyu.bid.warehouse.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WarehouseAttachmentExportPolicyTest {

    @Test
    void allScopeIncludesEveryType() {
        WarehouseAttachmentExportScope scope = new WarehouseAttachmentExportScope.All();
        assertThat(WarehouseAttachmentExportPolicy.isIncluded(scope, WarehouseAttachmentType.PROPERTY_CERTIFICATE)).isTrue();
        assertThat(WarehouseAttachmentExportPolicy.isIncluded(scope, WarehouseAttachmentType.INVOICE)).isTrue();
        assertThat(WarehouseAttachmentExportPolicy.isIncluded(scope, WarehouseAttachmentType.PHOTOS)).isTrue();
    }

    @Test
    void partialScopeIncludesOnlySelectedTypes() {
        WarehouseAttachmentExportScope scope = new WarehouseAttachmentExportScope.Partial(
                Set.of(WarehouseAttachmentType.PROPERTY_CERTIFICATE, WarehouseAttachmentType.PHOTOS));
        assertThat(WarehouseAttachmentExportPolicy.isIncluded(scope, WarehouseAttachmentType.PROPERTY_CERTIFICATE)).isTrue();
        assertThat(WarehouseAttachmentExportPolicy.isIncluded(scope, WarehouseAttachmentType.PHOTOS)).isTrue();
        assertThat(WarehouseAttachmentExportPolicy.isIncluded(scope, WarehouseAttachmentType.INVOICE)).isFalse();
    }

    @Test
    void filterKeepsIncludedAttachmentsOnly() {
        WarehouseAttachmentReadModel cert = attachment(WarehouseAttachmentType.PROPERTY_CERTIFICATE);
        WarehouseAttachmentReadModel invoice = attachment(WarehouseAttachmentType.INVOICE);
        WarehouseAttachmentReadModel photo = attachment(WarehouseAttachmentType.PHOTOS);

        Map<Long, List<WarehouseAttachmentReadModel>> input = Map.of(
                1L, List.of(cert, invoice, photo)
        );

        WarehouseAttachmentExportScope scope = new WarehouseAttachmentExportScope.Partial(
                Set.of(WarehouseAttachmentType.INVOICE));
        Map<Long, List<WarehouseAttachmentReadModel>> result = WarehouseAttachmentExportPolicy.filter(scope, input);

        assertThat(result).containsKey(1L);
        assertThat(result.get(1L)).containsExactly(invoice);
    }

    @Test
    void filterAllScopeKeepsEverything() {
        WarehouseAttachmentReadModel cert = attachment(WarehouseAttachmentType.PROPERTY_CERTIFICATE);
        WarehouseAttachmentReadModel photo = attachment(WarehouseAttachmentType.PHOTOS);
        Map<Long, List<WarehouseAttachmentReadModel>> input = Map.of(1L, List.of(cert, photo));

        Map<Long, List<WarehouseAttachmentReadModel>> result = WarehouseAttachmentExportPolicy.filter(
                new WarehouseAttachmentExportScope.All(), input);

        assertThat(result.get(1L)).containsExactly(cert, photo);
    }

    @Test
    void unknownTypeIsExcludedFromPartial() {
        WarehouseAttachmentExportScope scope = new WarehouseAttachmentExportScope.Partial(
                Set.of(WarehouseAttachmentType.PROPERTY_CERTIFICATE));
        assertThat(WarehouseAttachmentExportPolicy.isIncluded(scope, WarehouseAttachmentType.INVOICE)).isFalse();
    }

    @Test
    void isIncludedRejectsNullScope() {
        assertThatThrownBy(() -> WarehouseAttachmentExportPolicy.isIncluded(null, WarehouseAttachmentType.INVOICE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("attachmentScope must not be null");
    }

    @Test
    void filterRejectsNullScope() {
        assertThatThrownBy(() -> WarehouseAttachmentExportPolicy.filter(null, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("attachmentScope must not be null");
    }

    @Test
    void scopeFromStringDefaultsToAll() {
        WarehouseAttachmentExportScope scope = WarehouseAttachmentExportScope.from(null, Set.of());
        assertThat(scope).isEqualTo(new WarehouseAttachmentExportScope.All());
    }

    @Test
    void scopeFromStringIsCaseInsensitive() {
        WarehouseAttachmentExportScope scope = WarehouseAttachmentExportScope.from("partial", Set.of("INVOICE"));
        assertThat(scope).isInstanceOf(WarehouseAttachmentExportScope.Partial.class);
        assertThat(((WarehouseAttachmentExportScope.Partial) scope).types()).containsExactly(WarehouseAttachmentType.INVOICE);
    }

    @Test
    void scopeFromStringRejectsInvalidScope() {
        assertThatThrownBy(() -> WarehouseAttachmentExportScope.from("UNKNOWN", Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("仅支持 ALL 或 PARTIAL");
    }

    @Test
    void scopeFromStringRejectsPartialWithoutTypes() {
        assertThatThrownBy(() -> WarehouseAttachmentExportScope.from("PARTIAL", Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("至少指定一种附件类型");
    }

    @Test
    void scopeFromStringRejectsPartialWithInvalidType() {
        assertThatThrownBy(() -> WarehouseAttachmentExportScope.from("PARTIAL", Set.of("PROPERTY_CERTIFICATE", "UNKNOWN")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UNKNOWN");
    }

    private static WarehouseAttachmentReadModel attachment(WarehouseAttachmentType type) {
        return new WarehouseAttachmentReadModel() {
            @Override public Long getId() { return 1L; }
            @Override public WarehouseAttachmentType getType() { return type; }
            @Override public String getOriginalFilename() { return type.name() + ".pdf"; }
            @Override public String getStoredFilename() { return "stored.pdf"; }
            @Override public Long getFileSize() { return 1024L; }
            @Override public String getContentType() { return "application/pdf"; }
            @Override public Long getUploadedBy() { return 1L; }
            @Override public LocalDateTime getUploadedAt() { return LocalDateTime.now(); }
        };
    }
}
