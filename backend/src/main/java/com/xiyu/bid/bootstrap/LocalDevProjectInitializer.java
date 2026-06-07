// Input: local dev Spring profile, user repository, project repository
// Output: one pending-initiation project accessible to sales for browser verification
// Pos: Bootstrap/本地开发项目初始化
// 维护声明: 仅维护 dev profile 的本地联调验证项目；不要在这里扩散真实业务编排或生产数据规则。
package com.xiyu.bid.bootstrap;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class LocalDevProjectInitializer implements ApplicationRunner {

    static final String SALES_USERNAME = "sales";
    static final String SALES_PENDING_PROJECT_NAME = "本地联调-销售待立项验证项目";

    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    @Override
    public void run(ApplicationArguments args) {
        seedProjects();
    }

    void seedProjects() {
        User sales = userRepository.findByUsername(SALES_USERNAME)
                .orElseThrow(() -> new IllegalStateException("Required local dev user not found: " + SALES_USERNAME));
        boolean exists = projectRepository.findByNameContainingIgnoreCase(SALES_PENDING_PROJECT_NAME)
                .stream()
                .anyMatch(project -> SALES_PENDING_PROJECT_NAME.equals(project.getName()));
        if (exists) {
            log.info("Local dev project already exists: {}", SALES_PENDING_PROJECT_NAME);
            return;
        }

        Project project = new Project();
        project.setName(SALES_PENDING_PROJECT_NAME);
        project.setTenderId(resolveSeedTenderId());
        project.setStatus(Project.Status.INITIATED);
        project.setStage("INITIATED");
        project.setManagerId(sales.getId());
        project.setTeamMembers(new ArrayList<>(List.of(sales.getId())));
        project.setCustomer("西域本地联调客户");
        project.setCustomerType("企业客户");
        project.setIndustry("软件和信息技术服务业");
        project.setRegion("乌鲁木齐");
        project.setBudget(new BigDecimal("880000.00"));
        project.setDeadline(LocalDate.now().plusDays(15));
        project.setStartDate(LocalDateTime.now());
        project.setDescription("用于项目负责人上传招标文件、提交立项，以及投标管理员/组长审批流转的本地联调验证项目。");
        project.setSourceModule("LOCAL_DEV_SEED");
        project.setRemark("seed:sales-pending-initiation");

        projectRepository.save(project);
        log.info("Seeded local dev pending-initiation project for sales: {}", SALES_PENDING_PROJECT_NAME);
    }

    private Long resolveSeedTenderId() {
        return projectRepository.findAll().stream()
                .map(Project::getTenderId)
                .filter(java.util.Objects::nonNull)
                .max(Long::compareTo)
                .map(id -> id + 1)
                .orElse(100000L);
    }
}
