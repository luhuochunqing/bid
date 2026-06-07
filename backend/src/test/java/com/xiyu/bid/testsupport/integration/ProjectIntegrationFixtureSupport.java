package com.xiyu.bid.testsupport.integration;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.ProjectRepository;

import java.time.LocalDateTime;
import java.util.List;

public final class ProjectIntegrationFixtureSupport {

    private final ProjectRepository projectRepository;

    public ProjectIntegrationFixtureSupport(ProjectRepository projectRepository) {
        this.projectRepository = projectRepository;
    }

    public Project createPreparingProject(String name, Long tenderId, User ownerUser) {
        return createPreparingProject(
                name,
                tenderId,
                ownerUser.getId(),
                List.of(ownerUser.getId()),
                LocalDateTime.of(2026, 4, 1, 9, 0),
                LocalDateTime.of(2026, 4, 30, 18, 0)
        );
    }

    public Project createPreparingProject(
            String name,
            Long tenderId,
            Long managerId,
            List<Long> teamMembers,
            LocalDateTime startDate,
            LocalDateTime endDate
    ) {
        return projectRepository.save(Project.builder()
                .name(name)
                .tenderId(tenderId)
                .status(Project.Status.BIDDING)
                .managerId(managerId)
                .teamMembers(teamMembers)
                .startDate(startDate)
                .endDate(endDate)
                .build());
    }
}
