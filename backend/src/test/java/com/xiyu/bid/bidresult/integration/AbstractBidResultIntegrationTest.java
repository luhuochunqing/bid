package com.xiyu.bid.bidresult.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.bidresult.repository.BidResultReminderRepository;
import com.xiyu.bid.bidresult.repository.BidResultSyncLogRepository;
import com.xiyu.bid.bidresult.repository.CompetitorWinRecordRepository;
import com.xiyu.bid.competitionintel.repository.CompetitorRepository;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

abstract class AbstractBidResultIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ProjectRepository projectRepository;

    @Autowired
    protected BidResultFetchResultRepository fetchResultRepository;

    @Autowired
    protected BidResultReminderRepository reminderRepository;

    @Autowired
    protected BidResultSyncLogRepository syncLogRepository;

    @Autowired
    protected ProjectDocumentRepository projectDocumentRepository;

    @Autowired
    protected CompetitorRepository competitorRepository;

    @Autowired
    protected CompetitorWinRecordRepository competitorWinRecordRepository;

    protected User adminUser;
    protected BidResultIntegrationFixtureSupport fixtures;
    protected BidResultIntegrationHttpSupport httpSupport;

    @BeforeEach
    void resetIntegrationFixture() {
        BidResultIntegrationCleanupSupport cleanup = new BidResultIntegrationCleanupSupport(
                competitorWinRecordRepository,
                projectDocumentRepository,
                reminderRepository,
                syncLogRepository,
                fetchResultRepository,
                competitorRepository,
                projectRepository,
                userRepository
        );
        cleanup.reset();

        BidResultIntegrationSupportAssembler assembler = new BidResultIntegrationSupportAssembler(
                userRepository,
                projectRepository,
                fetchResultRepository,
                reminderRepository,
                projectDocumentRepository,
                competitorWinRecordRepository,
                mockMvc,
                objectMapper
        );
        BidResultIntegrationSession session = assembler.assemble();
        adminUser = session.adminUser();
        fixtures = session.fixtures();
        httpSupport = session.httpSupport();
    }
}
