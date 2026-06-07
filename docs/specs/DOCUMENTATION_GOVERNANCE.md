# 文档与注释治理规范

一旦我所属的文件夹有所变化，请更新我。

本规范定义仓库内文档、源码头注释与目录结构的同步规则，目标是让目录职责、关键文件角色和维护边界始终可追踪。
本规范默认可由执行开发任务的代理自主落实，不需要每次单独申请“是否顺手补文档”的许可。

## 1. 目标

- 目录结构变化后，受影响目录必须有可读的目录说明。
- 关键业务文件必须在文件头说明输入、输出和所在层次。
- 团队级结构变化才更新根 `README.md` 或 `docs/CHANGELOG.md`，避免把所有微小改动都堆到总文档。
- 所有强制项必须可由脚本检查，而不是依赖人工记忆。

## 2. 强制范围

### 2.1 强制维护 `README.md` 的目录

以下目录必须存在 `README.md`，并在目录职责或关键文件变化时同步更新：

- 仓库根目录
- `docs/`
- `scripts/`
- `src/api/`
- `src/api/modules/`
- `src/stores/`
- `src/views/`
- `src/components/`
- `src/router/`
- `src/config/`
- `src/styles/`
- `src/utils/`
- `backend/`

说明：

- 以上目录都已接入当前门禁。
- `backend/src/main/java/com/xiyu/bid/*` 下的一级业务模块目录已纳入第三阶段门禁。
- 后端一级模块 README 现在与前端强制目录一样，要求固定声明和文件清单表格。

### 2.2 强制添加源码头注释的文件

以下文件必须使用统一头注释：

- `src/api/index.js`
- `src/api/modules/**/*.js`
- `src/stores/**/*.js`
- `scripts/*.mjs`
- `scripts/*.sh`
- `scripts/release/**/*.mjs`
- `scripts/release/**/*.sh`
- `backend/src/main/java/com/xiyu/bid/{controller,service,config,auth,aspect,exception}/**/*.java`
- `backend/src/main/java/com/xiyu/bid/**/{controller,service,config}/**/*.java`

说明：

- 当前门禁覆盖“公共入口、数据边界、根级脚本工具、发布演练脚本、后端关键 Java 入口与编排文件”这类高杠杆文件。
- 页面组件、普通展示组件、样式文件、纯 DTO/常量文件暂不一刀切强制头注释。
- 后续如果某个目录进入稳定核心域，可再把它提升到强制范围。

## 3. 豁免范围

以下目录或文件默认豁免，不要求目录 README，也不要求源码头注释：

- 自动生成目录：`dist/`、`build/`、`coverage/`、`playwright-report/`、`test-results/`
- 运行产物目录：`target/`、`backend/docs/reports/`、`docs/reports/`
- 第三方依赖：`node_modules/`
- VCS/工作区目录：`.git/`、`.worktrees/`
- 静态资源叶子目录：图片、字体、图标等仅存放资源文件且无业务逻辑的目录
- 单文件临时输出、锁文件、二进制文件

如果某个目录虽然命中豁免模式，但承载了长期维护的业务逻辑，应把它显式加入强制范围，而不是继续按豁免处理。

## 4. README 规范

目录 `README.md` 必须满足以下约束：

1. 开头必须包含固定声明：`一旦我所属的文件夹有所变化，请更新我。`
2. 用 1 到 3 行说明该目录的职责、边界和它不负责什么。
3. 维护文件清单，至少覆盖关键入口文件、公共 API、复杂脚本和子目录边界。
4. 文件清单建议用表格，包含：
   - 文件
   - 地位
   - 功能

执行口径：

- 小目录（建议 12 个文件以内）写完整文件清单。
- 大目录优先列关键文件和子目录边界，不强求把每个低价值叶子文件都搬进 README。
- 如果目录结构发生变化但 README 未更新，视为违规。

## 5. 源码头注释规范

### 5.1 JavaScript / TypeScript / Vue / MJS

```js
// Input: [主要依赖、上游输入]
// Output: [导出对象、职责结果]
// Pos: [仓库中的位置/层次]
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
```

### 5.2 Shell

```bash
# Input: [主要依赖、上游输入]
# Output: [导出结果、脚本作用]
# Pos: [仓库中的位置/层次]
# 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
```

### 5.3 Java

```java
// Input: [主要依赖、上游输入]
// Output: [导出对象、职责结果]
// Pos: [仓库中的位置/层次]
// 维护声明: [边界、约束或维护提醒]
package com.xiyu.bid.xxx;
```

执行口径：

- 头注释必须位于文件顶部；Shell 文件允许保留 shebang，并把头注释放在 shebang 后。
- Java 文件允许把头注释放在 `package` 语句之前；检查器会兼容 `package` 在注释前后的两种位置。
- 注释要描述真实职责，不允许写成空洞模板话术。
- 如果文件职责发生变化但头注释未更新，视为违规。

## 6. 更新流程

发生代码或结构变更时，默认按下面流程执行：

1. 完成代码或架构改动。
2. 识别受影响的强制目录与强制文件。
3. 更新对应目录下的 `README.md`。
4. 更新受影响文件的头注释。
5. 若发生下列变化，再更新根 `README.md` 或 `docs/CHANGELOG.md`：
   - 新增或删除一级模块
   - 架构边界变化
   - 团队级开发规则变化
   - 会影响跨域协作方式的流程变化
6. 运行检查脚本并修复违规项。

## 7. 自主执行默认授权

执行开发任务时，代理默认拥有以下自主动作：

- 修改受影响目录的 `README.md`
- 修改强制文件的头注释
- 新增目录 `README.md`
- 运行文档治理检查脚本
- 在不改变业务行为的前提下，修复仅涉及文档同步和注释同步的问题

以下情况仍应停下来确认：

- 需要大规模补文档，范围明显超出当前任务
- 规范本身需要扩张到新的大模块
- 文档更新会改变团队已经采用的术语或架构命名

## 8. 检查与门禁

仓库使用 `scripts/check-doc-governance.mjs` 进行文档治理检查。

当前检查器会阻断：

- 强制目录缺少 `README.md`
- `README.md` 缺少固定声明
- 强制目录缺少文件清单表格或等效目录结构块
- 强制文件缺少头注释四行
- 头注释缺少 `Input / Output / Pos / 维护声明`

门禁接入方式：

- `npm run check:doc-governance`
- `npm run build` 在前端构建前执行该检查

## 9. 维护策略

- 规范本身以“高价值、可执行、可持续”为准，不追求把所有文件都塞进强制范围。
- 新模块默认先补 README，再决定是否进入头注释强制范围。
- 如果某条规则长期触发噪音而不产生价值，应收紧范围，而不是放任团队全部忽略。

## 10. 第四阶段实现范围

第四阶段已经落地，当前门禁继续保持“高信号目录和高杠杆文件优先”，不追求一次性全仓覆盖。

### 10.1 后端关键 Java 文件头注释

第四阶段已纳入强制头注释的后端 Java 文件范围：

- `backend/src/main/java/com/xiyu/bid/**/controller/**/*.java`
- `backend/src/main/java/com/xiyu/bid/**/service/**/*.java`
- `backend/src/main/java/com/xiyu/bid/**/config/**/*.java`
- `backend/src/main/java/com/xiyu/bid/**/{aspect,exception,security,filter,interceptor,client,gateway,adapter}/**/*.java`
- 核心域中承载自定义查询或聚合语义的 `repository/**/*.java`

当前推荐优先覆盖的核心域：

- `ai`
- `competitionintel`
- `compliance`
- `roi`
- `scoreanalysis`
- `resources`
- `collaboration`
- `projectworkflow`
- `documenteditor`
- `documentexport`
- `analytics`
- `auth`
- `approval`
- `fees`
- `platform`
- `documents`
- `versionhistory`
- `casework`
- `alerts`
- `batch`
- `calendar`
- `export`

第四阶段暂不强制：

- `dto`
- `entity`
- `enum`
- `constants`
- 纯 CRUD repository
- 测试、生成物、迁移脚本、临时辅助类

执行原则：

- 先覆盖“入口、编排、配置、运行时边界、外部依赖适配”。
- 不把 `DTO / Entity / Enum` 一次性拉进门禁，避免用低价值注释淹没真正重要的边界说明。
- repository 只对有业务语义、自定义查询或聚合边界的文件强制，不对所有接口一刀切。

### 10.2 前端 `views/components` README 下沉粒度

第四阶段已把 README 下沉到“功能边界目录”，而不是每个叶子目录。

当前已纳入强制 README 的前端目录：

- `src/views/Bidding/`
- `src/views/Knowledge/`
- `src/views/Project/`
- `src/views/Resource/`
- `src/views/Resource/BAR/`
- `src/views/AI/`
- `src/components/ai/`
- `src/components/charts/`
- `src/components/common/`
- `src/components/layout/`

第四阶段仍默认豁免的叶子目录：

- `src/views/Analytics/`
- `src/views/Dashboard/`
- `src/views/Document/`
- `src/views/System/`
- `src/views/AI/components/`
- `src/components/project/`

README 下沉触发条件满足任一条即可：

1. 目录下有 3 个及以上源文件。
2. 目录本身是路由页、业务子域或可复用组件域边界。
3. 目录内存在跨文件约定、状态流转、API/DTO 适配或共享规范。

执行原则：

- README 只写目录职责、关键子目录分工、跨文件约束和对外接口。
- 低于 3 个文件且职责已被父级 README 覆盖的叶子目录，不强制单独建 README。
- 叶子目录是否强制，以“有没有独立协作边界”为判断标准，不以“目录存在”本身为判断标准。

### 10.3 第四阶段落地顺序

1. 已补齐 `src/views/**` 和 `src/components/**` 的目标子目录 README。
2. 已将后端 `controller / service / config / cross-cutting` 四类 Java 文件接入头注释门禁。
3. 下一步再评估核心域 repository 是否值得纳入同一批的后续子批次。
