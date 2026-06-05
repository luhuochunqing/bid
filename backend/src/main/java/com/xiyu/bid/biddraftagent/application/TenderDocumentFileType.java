package com.xiyu.bid.biddraftagent.application;

import java.util.Locale;

final class TenderDocumentFileType {

    private static final int PROJECT_DOCUMENT_FILE_TYPE_LIMIT = 50;

    private TenderDocumentFileType() {
    }

    static String toProjectDocumentType(String fileName, String contentType) {
        String extension = extensionOf(fileName);
        if (extension != null) {
            return extension;
        }
        String normalizedContentType = trimToNull(contentType);
        if (normalizedContentType == null) {
            return null;
        }
        String mapped = mapKnownContentType(normalizedContentType);
        if (mapped != null) {
            return mapped;
        }
        return normalizedContentType.length() <= PROJECT_DOCUMENT_FILE_TYPE_LIMIT
                ? normalizedContentType
                : normalizedContentType.substring(0, PROJECT_DOCUMENT_FILE_TYPE_LIMIT);
    }

    private static String extensionOf(String fileName) {
        String normalizedFileName = trimToNull(fileName);
        if (normalizedFileName == null) {
            return null;
        }
        int dotIndex = normalizedFileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == normalizedFileName.length() - 1) {
            return null;
        }
        return normalizedFileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private static String mapKnownContentType(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx";
            case "application/msword" -> "doc";
            case "application/pdf" -> "pdf";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
            case "application/vnd.ms-excel" -> "xls";
            default -> null;
        };
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
