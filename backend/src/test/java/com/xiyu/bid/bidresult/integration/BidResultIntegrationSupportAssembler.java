package com.xiyu.bid.bidresult.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.bidresult.repository.BidResultReminderRepository;
import com.xiyu.bid.bidresult.repository.CompetitorWinRecordRepository;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.testsupport.integration.IntegrationUserSupport;
import org.springframework.test.web.servlet.MockMvc;

final class BidResultIntegrationSupportAssembler {

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final BidResultFetchResultRepository fetchResultRepository;
    private final BidResultReminderRepository reminderRepository;
    private final ProjectDocumentRepository projectDocumentRepository;
    private final CompetitorWinRecordRepository competitorWinRecordRepository;
    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;

    BidResultIntegrationSupportAssembler(
            UserRepository userRepository,
            ProjectRepository projectRepository,
            BidResultFetchResultRepository fetchResultRepository,
            BidResultReminderRepository reminderRepository,
            ProjectDocumentRepository projectDocumentRepository,
            CompetitorWinRecordRepository competitorWinRecordRepository,
            MockMvc mockMvc,
            ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.fetchResultRepository = fetchResultRepository;
        this.reminderRepository = reminderRepository;
        this.projectDocumentRepository = projectDocumentRepository;
        this.competitorWinRecordRepository = competitorWinRecordRepository;
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    BidResultIntegrationSession assemble() {
        IntegrationUserSupport users = new IntegrationUserSupport(userRepository);
        User adminUser = users.createAdminUser();
        BidResultIntegrationFixtureSupport fixtures = new BidResultIntegrationFixtureSupport(
                projectRepository,
                fetchResultRepository,
                reminderRepository,
                projectDocumentRepository,
                competitorWinRecordRepository,
                adminUser
        );
        BidResultIntegrationHttpSupport httpSupport = new BidResultIntegrationHttpSupport(
                mockMvc,
                objectMapper,
                adminUser
        );
        return new BidResultIntegrationSession(adminUser, fixtures, httpSupport);
    }
}
