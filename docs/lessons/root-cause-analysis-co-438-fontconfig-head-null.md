# CO-438: POI autoSizeColumn "Fontconfig head is null" 根因分析

**日期**：2026-06-30
**Sentry Issue**：[7584632888](https://7d6512d48e44.sentry.io/issues/7584632888/)
**影响**：服务器上所有 Excel 导出功能（保证金导出、项目档案导出、案件导出、品牌授权导出等）的列宽自动调整失败

## 1. 症状

Sentry 上报 `NullPointerException: Fontconfig head is null`，堆栈指向：
```
sun.awt.FcFontManager.getFontConfigFont(FcFontManager.java:...)
sun.awt.X11FontManager.getFontConfigFont(X11FontManager.java:...)
java.awt.Font.createFont(Font.java:...)
org.apache.poi.ss.util.SheetUtil.getDefaultCharWidth(SheetUtil.java:...)
org.apache.poi.ss.util.SheetUtil.getColumnWidth(SheetUtil.java:...)
org.apache.poi.xssf.usermodel.XSSFSheet.autoSizeColumn(XSSFSheet.java:...)
```

## 2. 根因

**服务器 systemd 启动 Java 时未设置 `-Djava.awt.headless=true`**。

### 因果链

1. systemd ExecStart 只有 `-Dfile.encoding=UTF-8` 等编码参数，没有 `java.awt.headless`
2. Java AWT 在无显示器的 Linux 上不加 headless=true 会走 `X11FontManager`（而非 `HeadlessFontManager`）
3. X11FontManager 初始化需要 fontconfig 配置，但运行环境下 fontconfig head 加载失败
4. POI `autoSizeColumn()` → `SheetUtil.getDefaultCharWidth()` → `Font.createFont()` → 字体系统报 NPE

### 验证

```bash
# 服务器上直接运行 JDK 21 字体测试（加 headless=true）
java -Djava.awt.headless=true FontTest
# → SUCCESS，11 个字体可用（含 WenQuanYi Zen Hei 中文字体）

# 不加 headless 运行
java FontTest
# → FAILED: Fontconfig head is null
```

## 3. 修复方案（三层防御）

### 层 1：服务器 systemd 配置（根治）

```bash
# /etc/systemd/system/xiyu-bid-backend.service
ExecStart=/opt/xiyu-tools/jdk-21/bin/java -Djava.awt.headless=true -Dfile.encoding=UTF-8 ...
```

```bash
sudo systemctl daemon-reload && sudo systemctl restart xiyu-bid-backend
```

### 层 2：代码级兜底（启动类）

```java
// XiyuBidApplication.java
public static void main(String[] args) {
    System.setProperty("java.awt.headless", "true");
    SpringApplication.run(XiyuBidApplication.class, args);
}
```

即使 systemd 配置遗漏，代码也确保 headless 模式。

### 层 3：ExcelAutoSizeHelper 增强降级（防御）

```java
// ExcelAutoSizeHelper.java
boolean fontAvailable = true;
for (int i = 0; i < columnCount; i++) {
    if (!fontAvailable) {
        sheet.setColumnWidth(i, DEFAULT_FALLBACK_WIDTH);
        continue;
    }
    try {
        sheet.autoSizeColumn(i);
        // 宽度限制...
    } catch (RuntimeException e) {
        fontAvailable = false;  // 首列失败后跳过剩余 autoSize
        sheet.setColumnWidth(i, DEFAULT_FALLBACK_WIDTH);
    }
}
```

**关键改进**：之前每列独立 try-catch，字体系统损坏时每列都触发一次失败的字体初始化（Sentry 重复上报 + 性能浪费）。改为首列失败后整批降级。

### 层 4：架构测试防复发

```java
// ArchitectureTest.java
@ArchTest
public static final ArchRule business_code_should_not_call_sheet_autoSizeColumn_directly =
    noClasses()
        .that().resideInAnyPackage("com.xiyu.bid.brandauth..", ...)
        .should().callMethod(Sheet.class, "autoSizeColumn", int.class)
        .because("CO-438: 必须通过 ExcelAutoSizeHelper 统一处理");
```

禁止业务代码直接调用 `sheet.autoSizeColumn()`，新代码必须走 helper。

## 4. 并行 PR 教训

本 bug 同时有 3 个 Agent 提交 PR，产生了协调问题：

| PR | Agent | 策略 | 问题 |
|---|---|---|---|
| 1430 | Claude | 删 helper，全改固定列宽 | ① 丢失 autoSize 能力 ② 代码重复 ③ 混入 CO-430 无关改动 |
| 1433 | Qoder | 删 helper，裸调 autoSizeColumn | **完全没有 try-catch**，比修之前更危险 |
| 1432 | Cursor | 分支名/commit 写 excel-autosize | **实际改的是 Vue 导入对话框**，文不对题 |

### 教训

1. **删防御代码 ≠ 修 bug**：PR 1433 删掉了 ExcelAutoSizeHelper 的 try-catch，裸调 autoSizeColumn，这比不修更糟——之前至少有 catch 兜底，修完后服务器上每次导出都会直接抛异常
2. **不要因防御代码"没用到"就删除**：try-catch 是防御性编程，字体系统正常时不走 catch，但服务器环境一旦异常就是最后一道防线
3. **分支名和 commit message 必须与实际改动一致**：PR 1432 分支名 `excel-autosize-font-fallback` 但实际改的是 Vue 导入对话框，导致 review 时找错文件
4. **一个 PR 不要混入无关改动**：PR 1430 把 CO-430 路径修复和 Excel autoSize 改动混在一起，合入时无法选择性 cherry-pick
5. **多 Agent 同时修同一 bug 时需要协调**：应先通过 `who-touches.sh` 检查，或指定一个 Agent 统一修复

## 5. 验证清单

- [x] 单元测试：ExcelAutoSizeHelperTest 5 个测试全绿（含 mock 模拟字体异常降级）
- [x] 架构测试：ArchitectureTest 全绿（新增 autoSizeColumn 禁止直接调用规则）
- [x] 服务器验证：systemd 加 `-Djava.awt.headless=true`，后端健康检查通过
- [ ] 部署后验证：部署新版本后实际触发一次 Excel 导出，确认无 Sentry 报错

## 6. 相关文件

- [ExcelAutoSizeHelper.java](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/common/util/ExcelAutoSizeHelper.java)
- [XiyuBidApplication.java](file:///Users/user/xiyu/worktrees/codex/backend/src/main/java/com/xiyu/bid/XiyuBidApplication.java)
- [ArchitectureTest.java](file:///Users/user/xiyu/worktrees/codex/backend/src/test/java/com/xiyu/bid/ArchitectureTest.java)
- [ExcelAutoSizeHelperTest.java](file:///Users/user/xiyu/worktrees/codex/backend/src/test/java/com/xiyu/bid/common/util/ExcelAutoSizeHelperTest.java)
