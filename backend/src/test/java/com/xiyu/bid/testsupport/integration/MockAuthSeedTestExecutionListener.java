// Input: TestExecutionContext + UserRepository bean（存在时）
// Output: 每个测试方法执行前重播集成测试 mock 用户种子
// Pos: TestSupport/集成测试种子
// 维护声明:
//   为什么用 beforeTestExecution 而不是 beforeTestClass/beforeTestMethod：
//   部分 @SpringBootTest 集成测试非 @Transactional，且在 @BeforeEach 里
//   userRepository.deleteAll()（如 DocumentExportIntegrationTest）。beforeTestExecution
//   在 JUnit @BeforeEach 之后、@Test 之前触发，可保证种子在清理后仍然在场。
//   通过 spring.factories 全局注册，无需逐个测试类标注。
package com.xiyu.bid.testsupport.integration;

import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

public class MockAuthSeedTestExecutionListener implements TestExecutionListener {

    @Override
    public void beforeTestExecution(TestContext testContext) {
        // H2-only 守卫在 seeder 内部（Testcontainers MySQL 上下文绝不写库）
        IntegrationMockUserSeeder.ensureSeededIfH2(testContext.getApplicationContext());
    }
}
