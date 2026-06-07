package com.xiyu.bid.bootstrap;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalDevProjectInitializerTest {

    @Test
    void seedProjects_createsPendingInitiationProjectForSalesUser() {
        UserRepository userRepository = mock(UserRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        LocalDevProjectInitializer initializer = new LocalDevProjectInitializer(userRepository, projectRepository);

        User sales = new User();
        sales.setId(7L);
        sales.setUsername("sales");
        sales.setFullName("张销售");

        when(userRepository.findByUsername("sales")).thenReturn(Optional.of(sales));
        when(projectRepository.findByNameContainingIgnoreCase("本地联调-销售待立项验证项目")).thenReturn(java.util.List.of());

        initializer.seedProjects();

        ArgumentCaptor<Project> projectCaptor = ArgumentCaptor.forClass(Project.class);
        verify(projectRepository).save(projectCaptor.capture());
        Project saved = projectCaptor.getValue();
        assertThat(saved.getName()).isEqualTo("本地联调-销售待立项验证项目");
        assertThat(saved.getStatus()).isEqualTo(Project.Status.INITIATED);
        assertThat(saved.getStage()).isEqualTo("INITIATED");
        assertThat(saved.getManagerId()).isEqualTo(7L);
        assertThat(saved.getTeamMembers()).contains(7L);
    }

    @Test
    void seedProjects_skipsCreationWhenSeedProjectAlreadyExists() {
        UserRepository userRepository = mock(UserRepository.class);
        ProjectRepository projectRepository = mock(ProjectRepository.class);
        LocalDevProjectInitializer initializer = new LocalDevProjectInitializer(userRepository, projectRepository);

        User sales = new User();
        sales.setId(7L);
        sales.setUsername("sales");

        Project existing = new Project();
        existing.setId(88L);
        existing.setName("本地联调-销售待立项验证项目");

        when(userRepository.findByUsername("sales")).thenReturn(Optional.of(sales));
        when(projectRepository.findByNameContainingIgnoreCase("本地联调-销售待立项验证项目")).thenReturn(java.util.List.of(existing));

        initializer.seedProjects();

        verify(projectRepository, never()).save(any(Project.class));
    }
}
