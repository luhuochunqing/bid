package com.xiyu.bid.bidresult.integration;

import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.bidresult.repository.BidResultReminderRepository;
import com.xiyu.bid.bidresult.repository.BidResultSyncLogRepository;
import com.xiyu.bid.bidresult.repository.CompetitorWinRecordRepository;
import com.xiyu.bid.competitionintel.repository.CompetitorRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;

final class BidResultIntegrationCleanupSupport {

    private final CompetitorWinRecordRepository competitorWinRecordRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final BidResultReminderRepository reminderRepository;
    private final BidResultSyncLogRepository syncLogRepository;
    private final BidResultFetchResultRepository fetchResultRepository;
    private final CompetitorRepository competitorRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    BidResultIntegrationCleanupSupport(
            CompetitorWinRecordRepository competitorWinRecordRepository,
            ProjectDocumentRepository projectDocumentRepository,
            BidResultReminderRepository reminderRepository,
            BidResultSyncLogRepository syncLogRepository,
            BidResultFetchResultRepository fetchResultRepository,
            CompetitorRepository competitorRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository
    ) {
        this.competitorWinRecordRepository = competitorWinRecordRepository;
        this.projectDocumentRepository = projectDocumentRepository;
        this.reminderRepository = reminderRepository;
        this.syncLogRepository = syncLogRepository;
        this.fetchResultRepository = fetchResultRepository;
        this.competitorRepository = competitorRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
    }

    void reset() {
        competitorWinRecordRepository.deleteAll();
        projectDocumentRepository.deleteAll();
        reminderRepository.deleteAll();
        syncLogRepository.deleteAll();
        fetchResultRepository.deleteAll();
        competitorRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();
    }
}
