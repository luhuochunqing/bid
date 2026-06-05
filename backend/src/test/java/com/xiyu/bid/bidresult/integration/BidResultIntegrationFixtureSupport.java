package com.xiyu.bid.bidresult.integration;

import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.entity.BidResultReminder;
import com.xiyu.bid.bidresult.entity.CompetitorWinRecord;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.bidresult.repository.BidResultReminderRepository;
import com.xiyu.bid.bidresult.repository.CompetitorWinRecordRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.testsupport.integration.ProjectIntegrationFixtureSupport;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

final class BidResultIntegrationFixtureSupport {

    private final BidResultFetchResultRepository fetchResultRepository;
    private final BidResultReminderRepository reminderRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final CompetitorWinRecordRepository competitorWinRecordRepository;
    private final ProjectIntegrationFixtureSupport projectFixtures;
    private final User adminUser;

    BidResultIntegrationFixtureSupport(
            ProjectRepository projectRepository,
            BidResultFetchResultRepository fetchResultRepository,
            BidResultReminderRepository reminderRepository,
            ProjectDocumentRepository projectDocumentRepository,
            CompetitorWinRecordRepository competitorWinRecordRepository,
            User adminUser
    ) {
        this.fetchResultRepository = fetchResultRepository;
        this.reminderRepository = reminderRepository;
        this.projectDocumentRepository = projectDocumentRepository;
        this.competitorWinRecordRepository = competitorWinRecordRepository;
        this.projectFixtures = new ProjectIntegrationFixtureSupport(projectRepository);
        this.adminUser = adminUser;
    }

    Project createProject(String name, Long tenderId) {
        return projectFixtures.createPreparingProject(name, tenderId, adminUser);
    }

    BidResultFetchResult saveFetchResult(
            Project project,
            BidResultFetchResult.Result result,
            BidResultFetchResult.Status status,
            BidResultFetchResult.RegistrationType registrationType,
            LocalDateTime fetchTime
    ) {
        return saveFetchResult(project, result, status, registrationType, fetchTime, "query-view", 10);
    }

    BidResultFetchResult saveFetchResult(
            Project project,
            BidResultFetchResult.Result result,
            BidResultFetchResult.Status status,
            BidResultFetchResult.RegistrationType registrationType,
            LocalDateTime fetchTime,
            String remark,
            Integer skuCount
    ) {
        return fetchResultRepository.save(BidResultFetchResult.builder()
                .source(registrationType.name())
                .tenderId(project.getTenderId())
                .projectId(project.getId())
                .projectName(project.getName())
                .result(result)
                .amount(result == BidResultFetchResult.Result.WON ? BigDecimal.valueOf(880000) : null)
                .fetchTime(fetchTime)
                .status(status)
                .confirmedAt(status == BidResultFetchResult.Status.CONFIRMED ? fetchTime.plusMinutes(10) : null)
                .confirmedBy(status == BidResultFetchResult.Status.CONFIRMED ? adminUser.getId() : null)
                .registrationType(registrationType)
                .remark(remark)
                .skuCount(skuCount)
                .build());
    }

    void saveReminder(
            Project project,
            BidResultReminder.ReminderType reminderType,
            BidResultReminder.ReminderStatus status,
            LocalDateTime remindTime,
            String comment,
            Long lastResultId,
            Long attachmentDocumentId,
            Long uploadedBy,
            LocalDateTime uploadedAt
    ) {
        reminderRepository.save(BidResultReminder.builder()
                .projectId(project.getId())
                .projectName(project.getName())
                .ownerId(adminUser.getId())
                .ownerName(adminUser.getFullName())
                .reminderType(reminderType)
                .status(status)
                .remindTime(remindTime)
                .lastReminderComment(comment)
                .lastResultId(lastResultId)
                .attachmentDocumentId(attachmentDocumentId)
                .uploadedBy(uploadedBy)
                .uploadedAt(uploadedAt)
                .createdBy(adminUser.getId())
                .createdByName(adminUser.getFullName())
                .build());
    }

    ProjectDocument saveProjectDocument(
            Long projectId,
            String documentCategory,
            String linkedEntityType,
            Long linkedEntityId,
            String fileName
    ) {
        return projectDocumentRepository.save(ProjectDocument.builder()
                .projectId(projectId)
                .name(fileName)
                .size("3MB")
                .fileType(MediaType.APPLICATION_PDF_VALUE)
                .documentCategory(documentCategory)
                .linkedEntityType(linkedEntityType)
                .linkedEntityId(linkedEntityId)
                .fileUrl("https://files.example.com/bid/" + fileName)
                .uploaderId(adminUser.getId())
                .uploaderName(adminUser.getFullName())
                .build());
    }

    void saveCompetitorWin(
            Project project,
            Long competitorId,
            String competitorName,
            LocalDate wonAt,
            int skuCount,
            String category
    ) {
        competitorWinRecordRepository.save(CompetitorWinRecord.builder()
                .competitorId(competitorId)
                .competitorName(competitorName)
                .projectId(project.getId())
                .projectName(project.getName())
                .skuCount(skuCount)
                .category(category)
                .discount("92折")
                .paymentTerms("月结30天")
                .wonAt(wonAt)
                .amount(BigDecimal.valueOf(560000))
                .notes("详情视图竞对")
                .recordedBy(adminUser.getId())
                .recordedByName(adminUser.getFullName())
                .build());
    }

    BidResultReminder reminderFor(Long projectId, BidResultReminder.ReminderType reminderType) {
        return reminderRepository.findFirstByProjectIdAndReminderTypeOrderByRemindTimeDesc(projectId, reminderType)
                .orElseThrow();
    }

    BidResultReminder.ReminderType reminderTypeFor(BidResultFetchResult.Result result) {
        return result == BidResultFetchResult.Result.WON
                ? BidResultReminder.ReminderType.NOTICE
                : BidResultReminder.ReminderType.REPORT;
    }
}
