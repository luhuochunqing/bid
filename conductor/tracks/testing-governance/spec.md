# Testing Infrastructure & TDD Workflow Governance

## 核心目标
解决“不敢放手让 AI 改代码”的问题。通过构建分层验证体系、落地 TDD 闭环、强化 CI 门禁，将测试从“负担”转变为“生产力护城河”。

## 策略组件 (五位一体)

### 1. 落地 TDD 闭环
- **逻辑**: AI 必须先编写/更新测试用例，验证失败后才允许修改功能逻辑。
- **产物**: 测试用例即“规格说明书”。

### 2. 构建分层验证体系
- **前端单元/组件测试**: Vitest + Vue Test Utils (秒级反馈)。
- **后端集成测试**: @DataJpaTest / @WebMvcTest (逻辑验证)。
- **E2E 回归测试**: Playwright (全链路业务闭环)。

### 3. 地毯式覆盖补盲 (Sub-agent)
- **任务**: 自动识别未覆盖的服务类，按照项目风格批量生成基准测试。

### 4. 强化 CI 门禁
- **逻辑**: Jacoco 覆盖率下降时禁止合并。
- **本地 Hook**: Commit 前强制运行相关模块测试。

### 5. AI 自我校对 (Self-Correction)
- **指令**: 激活 `verification-before-completion` 技能，AI 提交前必须自证清白。
