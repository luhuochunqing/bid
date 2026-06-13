// Input: qualification multipart files and upload parameters
// Output: import results and updated qualification attachment DTOs
// Pos: Service/Web服务适配层
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.qualification.service;

import com.xiyu.bid.businessqualification.application.service.ImportQualificationAppService;
import com.xiyu.bid.businessqualification.domain.port.QualificationFileStorage;
import com.xiyu.bid.exception.InvalidArgumentException;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@RequiredArgsConstructor
@Transactional
public class QualificationWebService {

    private final ImportQualificationAppService importQualificationAppService;
    private final QualificationService qualificationService;
    private final QualificationQueryService qualificationQueryService;
    private final QualificationFileStorage fileStorage;

    public ImportQualificationAppService.ImportSummary importFromExcel(MultipartFile file, String operatorName) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new InvalidArgumentException("上传文件不能为空");
        }
        return importQualificationAppService.importFromExcel(file, operatorName);
    }

    public QualificationDTO uploadAttachment(Long id, MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new InvalidArgumentException("上传文件不能为空");
        }
        QualificationDTO dto = qualificationQueryService.getQualificationById(id);
        String originalName = file.getOriginalFilename();

        String url = fileStorage.storeAttachment(
                id,
                file.getBytes(),
                originalName,
                file.getContentType()
        );
        dto.setFileUrl(url);
        return qualificationService.updateQualification(id, dto);
    }
}
