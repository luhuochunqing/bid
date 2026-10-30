// Input: 生产只读案例来源预演启动参数与既有环境配置
// Output: 无 Web/Flyway/调度/其他 runner 的只读 Spring 上下文
// Pos: maintenance 独立启动根——隔离正常服务启动副作用
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.maintenance;

import com.xiyu.bid.XiyuBidApplication;
import com.xiyu.bid.bootstrap.CaseProvenanceBackfillCommandLineRunner;
import com.xiyu.bid.casework.application.service.CaseProvenanceBackfillAppService;
import com.xiyu.bid.casework.application.service.CaseProvenanceBackfillArtifactWriter;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@EntityScan("com.xiyu.bid")
@EnableJpaRepositories("com.xiyu.bid")
@ComponentScan(
        basePackages = "com.xiyu.bid",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = XiyuBidApplication.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = ApplicationRunner.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = CommandLineRunner.class)
        })
public class CaseProvenancePreviewApplication {
    @Bean
    CaseProvenanceBackfillCommandLineRunner caseProvenanceBackfillCommandLineRunner(
            CaseProvenanceBackfillAppService service,
            CaseProvenanceBackfillArtifactWriter artifactWriter) {
        return new CaseProvenanceBackfillCommandLineRunner(service, artifactWriter);
    }
}
