// Input: keyword and status filter
// Output: qualification DTO list (read-only, used by export/import services)
// Pos: Service/业务支撑层
// 维护声明: 仅维护导出处需要的扁平查询；CRUD 在 QualificationService。
package com.xiyu.bid.qualification.service;

import com.xiyu.bid.businessqualification.application.service.ListQualificationsAppService;
import com.xiyu.bid.qualification.dto.QualificationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QualificationFlatQuery {

    private final ListQualificationsAppService listQualificationsAppService;
    private final QualificationDtoMapper mapper;

    public List<QualificationDTO> listAll(String keyword, List<String> status) {
        return listQualificationsAppService.list(
                mapper.toCriteria(null, null, null, null, status, null, null, null, null, keyword)
        ).stream().map(mapper::toDto).toList();
    }
}
