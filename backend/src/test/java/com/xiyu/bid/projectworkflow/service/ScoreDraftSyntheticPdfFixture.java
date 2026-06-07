package com.xiyu.bid.projectworkflow.service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ScoreDraftSyntheticPdfFixture {

    private ScoreDraftSyntheticPdfFixture() {
    }

    static byte[] build(List<String> lines) {
        PdfTextEncoding encoding = PdfTextEncoding.fromLines(lines);
        String pageContent = buildPageContent(lines, encoding);
        List<String> objects = List.of(
                "<< /Type /Catalog /Pages 2 0 R >>",
                "<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
                "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] "
                        + "/Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica "
                        + "/Encoding /WinAnsiEncoding /ToUnicode 6 0 R >>",
                streamObject(pageContent),
                streamObject(encoding.toUnicodeCMap())
        );
        return writePdf(objects);
    }

    private static String buildPageContent(List<String> lines, PdfTextEncoding encoding) {
        StringBuilder content = new StringBuilder("BT\n/F1 11 Tf\n72 720 Td\n16 TL\n");
        for (String line : lines) {
            content.append('<').append(encoding.encode(line)).append("> Tj\nT*\n");
        }
        return content.append("ET\n").toString();
    }

    private static String streamObject(String content) {
        int length = content.getBytes(StandardCharsets.ISO_8859_1).length;
        return "<< /Length " + length + " >>\nstream\n" + content + "endstream\n";
    }

    private static byte[] writePdf(List<String> objects) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();
        offsets.add(0);
        write(output, "%PDF-1.4\n");
        for (int index = 0; index < objects.size(); index++) {
            offsets.add(output.size());
            write(output, (index + 1) + " 0 obj\n");
            write(output, objects.get(index));
            write(output, "\nendobj\n");
        }
        int xrefOffset = output.size();
        write(output, "xref\n0 " + (objects.size() + 1) + "\n");
        write(output, "0000000000 65535 f \n");
        for (int index = 1; index < offsets.size(); index++) {
            write(output, "%010d 00000 n \n".formatted(offsets.get(index)));
        }
        write(output, "trailer\n<< /Size " + (objects.size() + 1) + " /Root 1 0 R >>\n");
        write(output, "startxref\n" + xrefOffset + "\n%%EOF\n");
        return output.toByteArray();
    }

    private static void write(ByteArrayOutputStream output, String value) {
        output.writeBytes(value.getBytes(StandardCharsets.ISO_8859_1));
    }

    private record PdfTextEncoding(Map<Integer, Integer> characterCodes) {
        private static final int FIRST_CODE = 33;

        private static PdfTextEncoding fromLines(List<String> lines) {
            Map<Integer, Integer> codes = new LinkedHashMap<>();
            for (String line : lines) {
                line.codePoints().forEach(codePoint -> {
                    if (!codes.containsKey(codePoint)) {
                        codes.put(codePoint, FIRST_CODE + codes.size());
                    }
                });
            }
            if (codes.size() > 223) {
                throw new IllegalArgumentException("PDF 测试文本字符集过大");
            }
            return new PdfTextEncoding(codes);
        }

        private String encode(String value) {
            StringBuilder hex = new StringBuilder();
            value.codePoints().forEach(codePoint -> hex.append(byteHex(characterCodes.get(codePoint))));
            return hex.toString();
        }

        private String toUnicodeCMap() {
            StringBuilder cmap = new StringBuilder("""
                    /CIDInit /ProcSet findresource begin
                    12 dict begin
                    begincmap
                    /CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def
                    /CMapName /ScoreDraftTestCMap def
                    /CMapType 2 def
                    1 begincodespacerange
                    <00> <FF>
                    endcodespacerange
                    """);
            cmap.append(characterCodes.size()).append(" beginbfchar\n");
            characterCodes.forEach((codePoint, code) ->
                    cmap.append('<').append(byteHex(code)).append("> <")
                            .append(utf16Hex(codePoint)).append(">\n"));
            return cmap.append("""
                    endbfchar
                    endcmap
                    CMapName currentdict /CMap defineresource pop
                    end
                    end
                    """).toString();
        }

        private static String byteHex(int value) {
            return "%02X".formatted(value);
        }

        private static String utf16Hex(int codePoint) {
            byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_16BE);
            StringBuilder hex = new StringBuilder();
            for (byte value : bytes) {
                hex.append("%02X".formatted(value & 0xff));
            }
            return hex.toString();
        }
    }
}
