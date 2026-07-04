package com.xiyu.bid.casework.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.casework.infrastructure.BidCaseSlice;
import com.xiyu.bid.casework.infrastructure.BidCaseSliceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Imports {@link BidCaseSlice} metadata from JSONL files produced by
 * {@code slice_all_v9.py} ({@code /tmp/winbid_slices/project_*.jsonl}).
 *
 * <p>Pure parsing logic lives in the static {@link #parseSlice(JsonNode)}
 * method; this class orchestrates file discovery, line reading, and persistence.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CaseSliceJsonlImporter {

    static final String DEFAULT_BASE_DIR = "/tmp/winbid_slices";
    static final String FILE_PATTERN = "project_\\d+\\.jsonl";
    static final int MAX_TITLE_LENGTH = 500;
    static final int MAX_DOCX_FILE_LENGTH = 500;
    static final int MAX_PREVIEW_LENGTH = 300;

    private static final Pattern FILENAME_PATTERN = Pattern.compile(FILE_PATTERN);

    private final BidCaseSliceRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Imports all JSONL files under {@value #DEFAULT_BASE_DIR}.
     *
     * @return summary of the import run
     */
    public ImportResult importAll() {
        return importFromDirectory(Path.of(DEFAULT_BASE_DIR));
    }

    /**
     * Imports all JSONL files under the given directory.
     *
     * @param directory directory to scan
     * @return summary of the import run
     */
    public ImportResult importFromDirectory(Path directory) {
        Objects.requireNonNull(directory, "directory must not be null");
        List<Path> files = listJsonlFiles(directory);
        if (files.isEmpty()) {
            log.warn("No JSONL files matching {} found in {}", FILE_PATTERN, directory);
            return new ImportResult(0, 0, 0, 0);
        }

        int imported = 0;
        int skipped = 0;
        int duplicates = 0;
        for (Path file : files) {
            FileResult result = importFile(file);
            imported += result.imported();
            skipped += result.skipped();
            duplicates += result.duplicates();
        }
        log.info("Imported {} slices from {} files (skipped {} malformed, {} duplicates)",
                imported, files.size(), skipped, duplicates);
        return new ImportResult(imported, skipped, duplicates, files.size());
    }

    private List<Path> listJsonlFiles(Path directory) {
        if (!Files.isDirectory(directory)) {
            log.warn("Not a directory: {}", directory);
            return List.of();
        }
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> FILENAME_PATTERN.matcher(p.getFileName().toString()).matches())
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to list JSONL files in " + directory, e);
        }
    }

    private FileResult importFile(Path file) {
        List<BidCaseSlice> slices = new ArrayList<>();
        int skipped = 0;
        int duplicates = 0;
        try (Stream<String> lines = Files.lines(file)) {
            for (String line : (Iterable<String>) lines::iterator) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                try {
                    JsonNode node = objectMapper.readTree(trimmed);
                    Optional<BidCaseSlice> slice = parseSlice(node);
                    if (slice.isPresent()) {
                        BidCaseSlice s = slice.get();
                        if (isDuplicate(s)) {
                            duplicates++;
                        } else {
                            slices.add(s);
                        }
                    } else {
                        skipped++;
                    }
                } catch (IOException e) {
                    log.warn("Skipping malformed JSON line in {}: {}", file, e.getMessage());
                    skipped++;
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read JSONL file " + file, e);
        }
        if (!slices.isEmpty()) {
            repository.saveAll(slices);
        }
        if (duplicates > 0) {
            log.info("Skipped {} duplicate slices in {}", duplicates, file);
        }
        return new FileResult(slices.size(), skipped, duplicates);
    }

    private boolean isDuplicate(BidCaseSlice slice) {
        return repository.existsByProjectDirAndDocxFileAndSectionIdx(
                slice.getProjectDir(),
                slice.getDocxFile(),
                slice.getSectionIdx()
        );
    }

    /**
     * Parses a single JSONL record into a {@link BidCaseSlice} entity.
     *
     * <p>This is a pure function: it performs no IO and mutates no external state.</p>
     *
     * @param node parsed JSON node
     * @return slice entity, or empty if the record is invalid
     */
    public static Optional<BidCaseSlice> parseSlice(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Optional.empty();
        }
        String title = textValue(node, "title");
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        BidCaseSlice slice = new BidCaseSlice();
        slice.setProjectDir(truncate(textValue(node, "project"), 200));
        slice.setProjectIdx(intValue(node, "project_idx", 0));
        slice.setDocxFile(truncate(textValue(node, "docx_file"), MAX_DOCX_FILE_LENGTH));
        slice.setDocxLabel(truncate(textValue(node, "docx_label"), 20));
        slice.setSectionIdx(intValue(node, "section_idx", 0));
        slice.setLevel(intValue(node, "level", 1));
        slice.setTitle(truncate(title, MAX_TITLE_LENGTH));
        slice.setTextPreview(truncate(textValue(node, "text_preview"), MAX_PREVIEW_LENGTH));
        slice.setTextLength(intValue(node, "text_length", 0));
        slice.setParaCount(intValue(node, "para_count", 0));
        return Optional.of(slice);
    }

    private static String textValue(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static int intValue(JsonNode node, String field, int defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isNumber() ? defaultValue : value.asInt();
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() > maxLength ? value.substring(0, maxLength) : value;
    }

    public record ImportResult(int imported, int skipped, int duplicates, int filesProcessed) {
    }

    private record FileResult(int imported, int skipped, int duplicates) {
    }
}
