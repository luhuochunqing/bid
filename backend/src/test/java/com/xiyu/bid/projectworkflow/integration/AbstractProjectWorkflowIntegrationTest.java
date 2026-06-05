package com.xiyu.bid.projectworkflow.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.repository.BidRequirementItemRepository;
import com.xiyu.bid.biddraftagent.repository.BidTenderDocumentSnapshotRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectReminderRepository;
import com.xiyu.bid.projectworkflow.repository.ProjectShareLinkRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.testsupport.integration.IntegrationUserSupport;
import com.xiyu.bid.testsupport.integration.ProjectDocumentApiTestSupport;
import com.xiyu.bid.testsupport.integration.ProjectIntegrationFixtureSupport;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

abstract class AbstractProjectWorkflowIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected ProjectRepository projectRepository;

    @Autowired
    protected TaskRepository taskRepository;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected ProjectDocumentRepository projectDocumentRepository;

    @Autowired
    protected ProjectReminderRepository projectReminderRepository;

    @Autowired
    protected ProjectShareLinkRepository projectShareLinkRepository;

    @Autowired
    protected BidRequirementItemRepository bidRequirementItemRepository;

    @Autowired
    protected BidTenderDocumentSnapshotRepository bidTenderDocumentSnapshotRepository;

    protected Project project;
    protected User ownerUser;
    protected ProjectDocumentApiTestSupport documentApiSupport;

    @BeforeEach
    void resetProjectWorkflowFixtures() {
        ProjectWorkflowIntegrationCleanupSupport cleanup = new ProjectWorkflowIntegrationCleanupSupport(
                projectShareLinkRepository,
                projectReminderRepository,
                projectDocumentRepository,
                taskRepository,
                projectRepository,
                userRepository
        );
        cleanup.reset();
        bidRequirementItemRepository.deleteAll();
        bidTenderDocumentSnapshotRepository.deleteAll();

        IntegrationUserSupport users = new IntegrationUserSupport(userRepository);
        ownerUser = users.createAdminUser("lizong-test", "lizong-test@example.com", "李总");

        ProjectIntegrationFixtureSupport projectFixtures = new ProjectIntegrationFixtureSupport(projectRepository);
        project = projectFixtures.createPreparingProject("项目详情工作流回归", 5001L, ownerUser);

        documentApiSupport = new ProjectDocumentApiTestSupport(mockMvc, objectMapper);
    }
}
