package com.xiyu.bid.resources.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CaCommitmentLetterUploadService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf", "image/jpeg", "image/png", "image/jpg");
    private static final long MAX_SIZE = 10 * 1024 * 1024; // 10MB

    @Value("${app.upload.ca-borrow-dir:uploads/ca-borrow}")
    private String uploadDir;

    public Map<String, String> upload(MultipartFile file) throws IOException {
        validateFile(file);

        Path uploadPath = resolveAbsoluteUploadPath();
        Files.createDirectories(uploadPath);

        String storedName = UUID.randomUUID() + "_" + sanitizeFilename(file.getOriginalFilename());
        Path dest = uploadPath.resolve(storedName).toAbsolutePath().normalize();
        file.transferTo(dest);

        String fileUrl = "/api/ca-certificates/commitment-letter/files/" + storedName;
        return Map.of("url", fileUrl);
    }

    public byte[] getFile(String filename) throws IOException {
        Path uploadPath = resolveAbsoluteUploadPath();
        Path filePath = uploadPath.resolve(sanitizeFilename(filename)).normalize();
        if (!filePath.startsWith(uploadPath)) {
            throw new IllegalArgumentException("Invalid file path");
        }
        if (!Files.exists(filePath)) {
            throw new IOException("File not found");
        }
        return Files.readAllBytes(filePath);
    }

    public String getContentType(String filename) throws IOException {
        Path uploadPath = resolveAbsoluteUploadPath();
        Path filePath = uploadPath.resolve(sanitizeFilename(filename)).normalize();
        String contentType = Files.probeContentType(filePath);
        return contentType != null ? contentType : MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    private void validateFile(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("文件大小不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("不支持的文件格式，仅支持 PDF、JPG、PNG");
        }
    }

    private Path resolveAbsoluteUploadPath() {
        Path p = Paths.get(uploadDir);
        if (!p.isAbsolute()) {
            p = Paths.get(System.getProperty("user.dir")).resolve(p).normalize();
        }
        return p;
    }

    private static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) return "file";
        return name.replaceAll("[^a-zA-Z0-9._\\-\\u4e00-\\u9fff]", "_");
    }
}