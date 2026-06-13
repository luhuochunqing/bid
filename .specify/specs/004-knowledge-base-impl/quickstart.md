# Quickstart: 知识库模块启动与验证指南

## 1. 验证编译与依赖

### 后端依赖编译
在工作区根目录下，进入后端目录并清理编译以验证是否有构建错误：
```bash
cd backend
mvn clean compile
```

### 前端依赖编译
在根目录下运行 `pnpm` 构建命令，确保前端依赖和打包通过：
```bash
pnpm install
npm run build
```

---

## 2. 自动化架构测试 (Architecture Check)

项目架构采用 ArchUnit 进行门禁拦截，测试分层和 FP-Java 原则。在开发完成或修改代码后，必须运行以下命令以确保没有违反分层依赖：
```bash
cd backend
mvn test -Dtest=FPJavaArchitectureTest
mvn test -Dtest=MaintainabilityArchitectureTest
```

---

## 3. 运行本地联调服务 (Stable Start)

我们推荐使用项目统一配置的本地联调启动命令。该命令会自动拉取 FastAPI sidecar（MarkItDown 文档转换）、MySQL、前端及后端服务：

```bash
export XIYU_DEV_CONFIRMED=1
npm run dev:stable:start
```

检查服务的当前运行状态：
```bash
npm run dev:stable:status
```

---

## 4. 手动验证路径

1. **项目归档与 Popover 校验**：
   *   启动后，登录系统。
   *   点击左侧导航栏的 **方案管理** -> **项目档案**。
   *   在台账列表中，鼠标悬浮在“归档文件数”单元格，等待 0.5s，检查是否弹出了各类文件的分项明细浮层。
   *   点击项目行任意非操作列，验证右侧滑出的只读抽屉 Drawer 宽度为 60% 且展示正常。
2. **台账与文件包 ZIP 导出**：
   *   点击“📊 导出台账”，下载后检查 Excel 内的两个 Sheet 页。
   *   点击“📦 导出文件包”，解包验证目录是否有 `_台账.xlsx` 且结构为 `[项目名]/[文档分类]/[文件名]`。
3. **AI 案例复用**：
   *   进入 **案例库** Tab。
   *   在案例卡片下点击 **📋 复用**，检查系统 Toast 提示并且内容成功写入系统剪贴板。
