# Testing Governance Implementation Plan

## Phase 1: 基础设施加固 (Infrastructure Hardening)
- [x] **任务 1.1: 修复后端测试基线**: 解决 ROLE_ID 缺失和循环依赖问题，使 615 个测试全绿。
- [x] **任务 1.2: 启用 Jacoco 覆盖率报告**: 修复 pom.xml，建立后端覆盖率可视化基准。
- [x] **任务 1.3: 前端 Vitest 环境搭建**: 安装 Vitest + Vue Test Utils，并在 Bidding/List.vue 落地第一个组件测试。

## Phase 2: 分层验证补齐 (Layered Validation)
- [x] **任务 2.1: 后端核心 Service 补盲**: 针对 TenderService, QualificationService 等“裸奔”模块，编写 JUnit5+Mockito 单元测试。
- [x] **任务 2.2: 前端关键 Logic 覆盖**: 为 Pinia Stores (bidding, user) 编写独立单元测试，并覆盖至少 3 个高频操作组件。
- [x] **任务 2.3: E2E 稳定性优化**: 扫描并重构 e2e/ 下的硬编码等待 (Hard Sleep)，优化为基于状态的等待逻辑。

## Phase 3: 工作流与门禁自动化 (Workflow & Gates)
- [x] **任务 3.1: 本地 Pre-commit Hook**: 配置 Git Hook，在提交前自动运行变更模块的 Vitest 或 Maven 测试。
- [x] **任务 3.2: CI 覆盖率红线**: 修改 GitHub Actions，配置 Jacoco 报告上传，设置覆盖率下降熔断逻辑。
- [x] **任务 3.3: 启用 AI 自校对指令**: 在 AGENTS.md 或项目中建立标准指令集，要求 AI 每次提交必须附带测试运行证据。

## Phase 4: 常态化治理 (Ongoing Maintenance)
- [ ] **任务 4.1: 技术债清理**: 扫描 TECHNICAL_DEBT.md，批量修复存量的测试报错。
- [ ] **任务 4.2: TDD 闭环实战**: 在接下来的一个新需求中，演示完整的“先写测试，再改代码”流程。
