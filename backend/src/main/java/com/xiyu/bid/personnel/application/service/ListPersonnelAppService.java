package com.xiyu.bid.personnel.application.service;

import com.xiyu.bid.common.domain.PagedResult;
import com.xiyu.bid.personnel.application.command.PersonnelListCriteria;
import com.xiyu.bid.personnel.application.dto.PersonnelDTO;
import com.xiyu.bid.personnel.application.mapper.PersonnelMapper;
import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListPersonnelAppService {

    private final PersonnelRepository repository;
    private final PersonnelMapper mapper;

    @Transactional(readOnly = true)
    public List<PersonnelDTO> list(PersonnelListCriteria criteria) {
        return repository.findAll(criteria).stream()
                .map(mapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public PagedResult<PersonnelDTO> listPageable(PersonnelListCriteria criteria, int pageNumber, int pageSize) {
        var page = repository.findAllPageable(criteria, pageNumber, pageSize);
        List<PersonnelDTO> dtos = page.content().stream().map(mapper::toDTO).toList();
        return new PagedResult<>(dtos, page.totalElements(), page.totalPages(), page.pageNumber(), page.pageSize(), page.hasNext(), page.hasPrevious());
    }

    @Transactional(readOnly = true)
    public PersonnelDTO get(Long id) {
        Personnel p = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Personnel", String.valueOf(id)));
        return mapper.toDTO(p);
    }
}
