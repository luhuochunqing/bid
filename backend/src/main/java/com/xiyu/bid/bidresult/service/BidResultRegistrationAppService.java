package com.xiyu.bid.bidresult.service;

import com.xiyu.bid.bidresult.core.AwardRegistration;
import com.xiyu.bid.bidresult.core.AwardRegistrationValidation;
import com.xiyu.bid.bidresult.dto.BidResultFetchResultAssembler;
import com.xiyu.bid.bidresult.dto.BidResultFetchResultDTO;
import com.xiyu.bid.bidresult.dto.BidResultRegisterRequest;
import com.xiyu.bid.bidresult.dto.BidResultUpdateRequest;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BidResultRegistrationAppService {

    private static final String MANUAL_SOURCE = "人工登记";

    private final BidResultFetchResultRepository fetchResultRepository;
    private final ProjectRepository projectRepository;
    private final BidResultReminderAppService reminderAppService;
    private final BidResultProjectAccessGuard accessGuard;
    private final BidResultRegistrationFactory registrationFactory = new BidResultRegistrationFactory();
    private final BidResultResultParser resultParser = new BidResultResultParser();

    @Transactional
    public BidResultFetchResultDTO register(BidResultRegisterRequest request, Long operatorId, String operatorName) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project not found: " + request.getProjectId()));
        accessGuard.assertCanAccess(project.getId());
        AwardRegistration registration = registrationFactory.fromRegisterRequest(project, request);
        validate(registration);
        BidResultFetchResult entity = registrationFactory.applyRegistration(
                BidResultFetchResult.builder()
                        .source(MANUAL_SOURCE)
                        .tenderId(project.getTenderId())
                        .projectId(project.getId())
                        .projectName(project.getName())
                        .status(BidResultFetchResult.Status.CONFIRMED)
                        .confirmedAt(LocalDateTime.now())
                        .confirmedBy(operatorId)
                        .registrationType(BidResultFetchResult.RegistrationType.MANUAL)
                        .fetchTime(LocalDateTime.now())
                        .result(resultParser.toEntityResult(registration.result()))
                        .build(),
                registration
        );
        BidResultFetchResult saved = fetchResultRepository.save(entity);
        reminderAppService.ensurePendingReminderForResult(saved, "结果已登记，请及时上传资料", operatorId, operatorName);
        return BidResultFetchResultAssembler.toDto(saved);
    }

    @Transactional
    public BidResultFetchResultDTO update(Long id, BidResultUpdateRequest request, Long operatorId, String operatorName) {
        BidResultFetchResult current = fetchResultRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Bid result fetch record not found: " + id));
        accessGuard.assertCanAccess(current.getProjectId());
        if (current.getStatus() == BidResultFetchResult.Status.IGNORED) {
            throw new BusinessException("已忽略的结果不可编辑，请先恢复为待确认");
        }
        AwardRegistration registration = registrationFactory.fromUpdateRequest(current, request);
        validate(registration);
        BidResultFetchResult merged = registrationFactory.applyRegistration(current, registration);
        BidResultFetchResult saved = fetchResultRepository.save(merged);
        reminderAppService.ensurePendingReminderForResult(saved, "结果已更新，请及时上传资料", operatorId, operatorName);
        return BidResultFetchResultAssembler.toDto(saved);
    }

    private void validate(AwardRegistration registration) {
        AwardRegistrationValidation.ValidationResult validation = AwardRegistrationValidation.validate(registration);
        if (!validation.valid()) {
            throw new BusinessException(String.join("; ", validation.errors()));
        }
    }
}
