// Input: query criteria and paging parameters
// Output: mapped qualification DTOs and page objects
// Pos: Service/查询编排层
// 维护声明: 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.qualification.service;

import com.xiyu.bid.businessqualification.application.service.ListQualificationsAppService;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import com.xiyu.bid.qualification.dto.QualificationOverviewDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QualificationQueryService {

    private final ListQualificationsAppService listQualificationsAppService;
    private final QualificationDtoMapper mapper;
    private final BusinessQualificationRepository businessQualificationRepository;

    public List<QualificationDTO> getAllQualifications(
            String subjectType, String subjectName, String category, String level, List<String> status,
            Integer expiringWithinDays, LocalDate expiringFrom, LocalDate expiringTo, String issuer, String keyword
    ) {
        return listQualificationsAppService.list(mapper.toCriteria(
                        subjectType, subjectName, category, level, status,
                        expiringWithinDays, expiringFrom, expiringTo, issuer, keyword))
                .stream().map(mapper::toDto).toList();
    }

    public Page<QualificationDTO> getAllQualifications(
            String subjectType, String subjectName, String category, String level, List<String> status,
            Integer expiringWithinDays, LocalDate expiringFrom, LocalDate expiringTo, String issuer, String keyword,
            int page, int size
    ) {
        return listQualificationsAppService.list(
                mapper.toCriteria(subjectType, subjectName, category, level, status,
                        expiringWithinDays, expiringFrom, expiringTo, issuer, keyword),
                page, size
        ).map(mapper::toDto);
    }

    public QualificationDTO getQualificationById(Long id) {
        return mapper.toDto(listQualificationsAppService.get(id));
    }

    public List<QualificationDTO> getQualificationsByType(com.xiyu.bid.entity.Qualification.Type type) {
        String category = type == null ? null : mapper.toUpsertCommand(QualificationDTO.builder().type(type).build())
                .getCategory().name();
        return getAllQualifications(null, null, category, null, null, null, null, null, null, null);
    }

    public List<QualificationDTO> getValidQualifications() {
        return getAllQualifications(null, null, null, null, null, null, null, null, null, null).stream()
                .filter(item -> !"expired".equals(item.getStatus()))
                .toList();
    }

    public QualificationOverviewDTO getOverview() {
        return mapper.toOverview(getAllQualifications(null, null, null, null, null, null, null, null, null, null));
    }

    public List<String> getAllLevels() {
        return businessQualificationRepository.findAllLevels();
    }
}
