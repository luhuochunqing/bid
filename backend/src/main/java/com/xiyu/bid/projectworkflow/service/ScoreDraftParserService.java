// Input: projectworkflow repositories, DTOs, and score draft document extraction support
// Output: Score Draft Parser business service operations
// Pos: Service/业务层
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.projectworkflow.parser.FileType;
import com.xiyu.bid.projectworkflow.parser.ScoreDraftDraftAssembler;
import com.xiyu.bid.projectworkflow.parser.ScoreDraftDocumentTextExtractor;
import com.xiyu.bid.projectworkflow.parser.ScoreDraftFileTypePolicy;
import com.xiyu.bid.projectworkflow.parser.ScoreDraftTextParser;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ScoreDraftParserService {

    private static final long MAX_FILE_SIZE_BYTES = 30L * 1024L * 1024L;

    private final ScoreDraftFileTypePolicy fileTypePolicy;
    private final ScoreDraftDocumentTextExtractor documentTextExtractor;
    private final ScoreDraftTextParser scoreDraftTextParser;
    private final ScoreDraftDraftAssembler scoreDraftDraftAssembler;

    public List<ProjectScoreDraft> parse(Long projectId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传评分标准文件");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new IllegalArgumentException("评分标准文件不能超过 30MB");
        }

        String fileName = Optional.ofNullable(file.getOriginalFilename())
                .filter(name -> !name.isBlank())
                .orElse("评分标准文件");
        FileType fileType = fileTypePolicy.detect(fileName);
        String extractedText = extractText(file, fileType);
        if (extractedText == null || extractedText.isBlank()) {
            throw new IllegalArgumentException("未能从评分标准文件提取有效正文；扫描件 PDF 请先 OCR");
        }
        List<ProjectScoreDraft> drafts = scoreDraftDraftAssembler.assemble(
                projectId,
                fileName,
                scoreDraftTextParser.parse(fileName, extractedText)
        );
        if (drafts.isEmpty()) {
            throw new IllegalArgumentException("未识别到规整评分标准，请上传包含商务/技术/价格评分表的 Word、Excel 或文本型 PDF 文件");
        }
        return drafts;
    }

    private String extractText(MultipartFile file, FileType fileType) {
        try (var inputStream = file.getInputStream()) {
            return documentTextExtractor.extract(inputStream, fileType);
        } catch (IOException ex) {
            throw new IllegalStateException("读取评分标准文件失败", ex);
        }
    }
}
