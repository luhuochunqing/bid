package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.dto.CaseDTO;
import com.xiyu.bid.casework.infrastructure.persistence.CaseMapper;
import com.xiyu.bid.entity.Case;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.CaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CaseCrudAppService {

    private final CaseRepository caseRepository;
    private final CaseMapper caseMapper;

    public CaseDTO create(CaseDTO dto) {
        Case saved = caseRepository.save(caseMapper.toEntity(dto));
        return caseMapper.toDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<CaseDTO> findAll() {
        return caseRepository.findAll(PageRequest.of(0, 1000)).stream().map(caseMapper::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public CaseDTO findById(Long id) {
        return caseMapper.toDTO(findEntityById(id));
    }

    public CaseDTO update(Long id, CaseDTO dto) {
        Case existing = findEntityById(id);
        caseMapper.applyUpdates(existing, dto);
        return caseMapper.toDTO(caseRepository.save(existing));
    }

    public void delete(Long id) {
        if (!caseRepository.existsById(id)) {
            throw new ResourceNotFoundException("Case", id.toString());
        }
        caseRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<CaseDTO> findByIndustry(CaseDTO.Industry industry) {
        return caseRepository.findByIndustry(Case.Industry.valueOf(industry.name()), PageRequest.of(0, 1000))
                .stream()
                .map(caseMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CaseDTO> findByOutcome(CaseDTO.Outcome outcome) {
        return caseRepository.findByOutcome(Case.Outcome.valueOf(outcome.name()), PageRequest.of(0, 1000))
                .stream()
                .map(caseMapper::toDTO)
                .toList();
    }

    private Case findEntityById(Long id) {
        return caseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Case", id.toString()));
    }
}
