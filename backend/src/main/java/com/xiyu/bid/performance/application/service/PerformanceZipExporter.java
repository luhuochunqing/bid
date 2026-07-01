package com.xiyu.bid.performance.application.service;

import com.xiyu.bid.common.util.ZipEntryDeduplicator;
import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.mapper.PerformanceMapper;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.port.PerformanceAlertConfigRepository;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 业绩 ZIP 导出服务（含附件）.
 * 打包结构：
 *   _台账.xlsx  — 业绩台账 Excel
 *   合同名称_1/合同协议.pdf
 *   合同名称_1/商城截图.png
 *   合同名称_2/...
 */
@Service
@RequiredArgsConstructor
@Slf4j
public final class PerformanceZipExporter {

    /** 业绩仓储. */
    private final PerformanceRepository repository;
    /** 业绩 Mapper. */
    private final PerformanceMapper mapper;
    /** 提醒配置仓储. */
    private final PerformanceAlertConfigRepository alertConfigRepository;
    /** Excel 导出器. */
    private final PerformanceExcelExporter excelExporter;

    /** 默认提醒配置. */
    private static final PerformanceAlertConfig DEFAULT_CONFIG =
            new PerformanceAlertConfig(null, 180, 90, true);
    /** HTTP 客户端连接超时秒数. */
    private static final int HTTP_TIMEOUT_SECONDS = 30;
    /** HTTP 客户端. */
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
            .build();

    /**
     * 导出 ZIP（含 Excel 台账 + 附件）.
     *
     * @param ids 记录 ID 列表，null 或空表示按 criteria 全量导出
     * @param criteria 搜索条件，null 时退化为 PerformanceSearchCriteria.empty()
     * @return ZIP 字节数组
     * @throws IOException IO 异常
     */
    public byte[] exportZip(final List<Long> ids,
                            final PerformanceSearchCriteria criteria)
            throws IOException {
        List<PerformanceDTO> records;
        if (ids != null && !ids.isEmpty()) {
            records = ids.stream()
                    .map(id -> mapper.toDTO(
                            repository.findById(id).orElse(null)))
                    .filter(r -> r != null)
                    .toList();
        } else {
            var config = alertConfigRepository.findActive()
                    .orElse(DEFAULT_CONFIG);
            var effectiveCriteria = criteria != null
                    ? criteria
                    : PerformanceSearchCriteria.empty();
            records = repository.findAll(
                            effectiveCriteria, config)
                    .stream()
                    .map(mapper::toDTO)
                    .toList();
        }

        byte[] excelBytes = excelExporter.export(ids, criteria);

        var out = new ByteArrayOutputStream();
        try (var zipOut = new ZipOutputStream(out)) {
            // 1. 写入 Excel 台账
            ZipEntry excelEntry = new ZipEntry("_台账.xlsx");
            zipOut.putNextEntry(excelEntry);
            zipOut.write(excelBytes);
            zipOut.closeEntry();

            // 2. 遍历记录，按合同名称分文件夹打包附件
            ZipEntryDeduplicator dedup = new ZipEntryDeduplicator();
            for (int i = 0; i < records.size(); i++) {
                PerformanceDTO record = records.get(i);
                String folderName = safeFolderName(record.contractName())
                        + "_" + (i + 1);
                List<PerformanceDTO.AttachmentDTO> attachments =
                        record.attachments();
                if (attachments == null || attachments.isEmpty()) {
                    continue;
                }
                for (PerformanceDTO.AttachmentDTO att : attachments) {
                    if (att.fileUrl() == null
                            || att.fileUrl().isBlank()) {
                        continue;
                    }
                    String fileName = ZipEntryDeduplicator.safeFileName(att.fileName());
                    if (fileName.isEmpty()) {
                        fileName = "attachment_" + att.id();
                    }
                    String zipPath = dedup.deduplicate(folderName + "/" + fileName);

                    ZipEntry entry = new ZipEntry(zipPath);
                    zipOut.putNextEntry(entry);

                    try {
                        byte[] fileBytes = downloadFile(att.fileUrl());
                        zipOut.write(fileBytes);
                    } catch (IOException | InterruptedException e) {
                        log.warn("下载附件失败: {} - {}",
                                att.fileUrl(), e.getMessage());
                        zipOut.write(("下载失败: " + e.getMessage())
                                .getBytes(StandardCharsets.UTF_8));
                    }
                    zipOut.closeEntry();
                }
            }
            zipOut.finish();
        }
        return out.toByteArray();
    }

    /**
     * 下载远程文件.
     *
     * @param url 文件 URL
     * @return 文件字节数组
     * @throws IOException          IO 异常
     * @throws InterruptedException 中断异常
     */
    private static String safeFolderName(String name) {
        String safe = ZipEntryDeduplicator.safeFileName(name);
        return safe.isEmpty() ? "unnamed_contract" : safe;
    }

    private byte[] downloadFile(final String url)
            throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .GET()
                .build();
        HttpResponse<byte[]> response = HTTP_CLIENT.send(
                request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 200
                && response.statusCode() < 300) {
            return response.body();
        }
        throw new IOException("HTTP " + response.statusCode()
                + " for " + url);
    }

}
