package com.xiyu.bid.projectworkflow.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UploadValidationPolicyTest {

    @Test
    void emptyFileShouldBeRejected() {
        UploadValidationPolicy.ValidationResult r = UploadValidationPolicy.validate("a.pdf", "application/pdf", 0L);
        assertThat(r.valid()).isFalse();
        assertThat(r.message()).contains("上传");
    }

    @Test
    void overSizeShouldBeRejected() {
        long over = UploadValidationPolicy.MAX_BYTES + 1;
        UploadValidationPolicy.ValidationResult r = UploadValidationPolicy.validate("a.pdf", "application/pdf", over);
        assertThat(r.valid()).isFalse();
        assertThat(r.message()).contains("50MB");
    }

    @Test
    void disallowedExtensionShouldBeRejected() {
        UploadValidationPolicy.ValidationResult r = UploadValidationPolicy.validate("evil.exe", "application/octet-stream", 100L);
        assertThat(r.valid()).isFalse();
        assertThat(r.message()).contains("格式不支持");
    }

    @Test
    void unknownExtensionButAllowedContentTypeIsAccepted() {
        UploadValidationPolicy.ValidationResult r = UploadValidationPolicy.validate("noext", "application/pdf", 100L);
        assertThat(r.valid()).isTrue();
    }

    @Test
    void allAllowedExtensionsAreAccepted() {
        for (String ext : new String[]{"png", "jpg", "jpeg", "pdf", "doc", "docx", "xls", "xlsx"}) {
            UploadValidationPolicy.ValidationResult r = UploadValidationPolicy.validate("file." + ext, "application/octet-stream", 1024L);
            assertThat(r.valid()).as("extension " + ext).isTrue();
        }
    }

    @Test
    void caseInsensitiveExtensionShouldBeAccepted() {
        UploadValidationPolicy.ValidationResult r = UploadValidationPolicy.validate("REPORT.PDF", null, 1024L);
        assertThat(r.valid()).isTrue();
    }

    @Test
    void exactlyMaxBytesShouldBeAccepted() {
        UploadValidationPolicy.ValidationResult r = UploadValidationPolicy.validate("a.pdf", "application/pdf", UploadValidationPolicy.MAX_BYTES);
        assertThat(r.valid()).isTrue();
    }
}
