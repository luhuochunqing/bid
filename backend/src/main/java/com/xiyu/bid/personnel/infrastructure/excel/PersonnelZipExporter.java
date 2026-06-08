package com.xiyu.bid.personnel.infrastructure.excel;

import com.xiyu.bid.personnel.application.dto.CertificateDTO;
import com.xiyu.bid.personnel.application.dto.PersonnelDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class PersonnelZipExporter {

    private static final int HTTP_TIMEOUT_SECONDS = 30;
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
            .build();

    private final PersonnelExcelExporter excelExporter;

    public byte[] exportZip(List<PersonnelDTO> personnelList) throws IOException {
        byte[] excelBytes = excelExporter.export(personnelList);

        Map<String, List<CertificateDTO>> certsByPersonnel = personnelList.stream()
                .filter(p -> p.certificates() != null && !p.certificates().isEmpty())
                .collect(Collectors.toMap(
                        PersonnelDTO::employeeNumber,
                        p -> p.certificates(),
                        (a, b) -> a
                ));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zipOut = new ZipOutputStream(out)) {

            ZipEntry excelEntry = new ZipEntry("_人员证书台账.xlsx");
            zipOut.putNextEntry(excelEntry);
            zipOut.write(excelBytes);
            zipOut.closeEntry();

            ZipEntry attachmentsFolder = new ZipEntry("_附件/");
            zipOut.putNextEntry(attachmentsFolder);
            zipOut.closeEntry();

            for (PersonnelDTO p : personnelList) {
                if (p.certificates() == null || p.certificates().isEmpty()) {
                    continue;
                }

                int certSeq = 1;
                for (CertificateDTO cert : p.certificates()) {
                    String fileName = generateAttachmentFileName(p, cert, certSeq);
                    String zipPath = "_附件/" + fileName;

                    ZipEntry entry = new ZipEntry(zipPath);
                    zipOut.putNextEntry(entry);

                    if (cert.attachmentUrl() != null && !cert.attachmentUrl().isBlank()) {
                        try {
                            byte[] fileBytes = downloadFile(cert.attachmentUrl());
                            zipOut.write(fileBytes);
                        } catch (IOException | InterruptedException e) {
                            log.warn("下载附件失败: {} - {}", cert.attachmentUrl(), e.getMessage());
                            zipOut.write(("下载失败: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
                        }
                    } else {
                        zipOut.write("附件不存在".getBytes(StandardCharsets.UTF_8));
                    }
                    zipOut.closeEntry();
                    certSeq++;
                }
            }

            zipOut.finish();
        }

        return out.toByteArray();
    }

    public String generateAttachmentFileName(PersonnelDTO personnel, CertificateDTO cert, int sequence) {
        String name = personnel.name() != null ? personnel.name() : "未知";
        String empNo = personnel.employeeNumber() != null ? personnel.employeeNumber() : "EMP";
        String certName = cert.name() != null
                ? cert.name().replaceAll("[\\\\/:*?\"<>|]", "_")
                : "未知证书";
        String ext = extractExtension(cert.attachmentUrl());

        return String.format("PER_%s_%s_%02d_%s.%s",
                name, empNo, sequence, certName, ext);
    }

    private String extractExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "pdf";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex + 1).toLowerCase();
        }
        return "pdf";
    }

    private byte[] downloadFile(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(HTTP_TIMEOUT_SECONDS))
                .GET()
                .build();

        HttpResponse<byte[]> response = HTTP_CLIENT.send(
                request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return response.body();
        }
        throw new IOException("HTTP " + response.statusCode() + " for " + url);
    }

    public void saveExportFile(Long taskId, byte[] zipBytes) throws IOException {
        String fileName = "personnel_export_" + taskId + "_" + System.currentTimeMillis() + ".zip";
        String dir = "data/personnel-exports";
        Files.createDirectories(Path.of(dir));
        Path path = Path.of(dir, fileName);
        Files.write(path, zipBytes);
    }
}
