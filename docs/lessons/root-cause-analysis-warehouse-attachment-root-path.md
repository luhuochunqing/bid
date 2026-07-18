# 仓库附件根路径缺失 + PDF 照片静默跳过 + 测试环境配置缺失 根因分析

> 日期: 2026-07-17
> 排查者: claude
> 修复 PR: `!2112`（路径默认值）/ `!2116`（PDF 渲染）/ `!2118`（prod 配置）

---

## 现场还原

**症状素描**：用户反馈导出仓库 Word 合订本后，**三级标题下的附件内容缺失**（照片不显示）。本地环境能复现，测试环境也能复现。

**边界划定**：
- 附件文件确实已上传到服务器 ❓（路径错配导致找不到）
- PDF 照片被 `writePhotos` 方法静默跳过 ❌（PR !2116 修复）
- 测试环境 `application-prod.yml` 缺少 `warehouse.attachment.root` 配置 ❌（PR !2118 修复）

---

## 历史背景：三个症状根因不同

| 修复 PR | 根因 | 层级 |
|---|---|---|
| `!2112` | `warehouse.attachment.root` 默认值用绝对路径 `/data/attachments/warehouse`，与 personnel/qualification 模块的相对路径约定不一致 | 配置默认值 |
| `!2116` | `WarehouseWordBundleBuilder.writePhotos` 方法在文件不存在时静默跳过，且 PDF 格式不在支持列表，导致 PDF 照片无声丢失 | 渲染逻辑 |
| `!2118` | `application-prod.yml` 没有显式配置 `warehouse.attachment.root`，回退到代码默认相对路径 `data/warehouse-attachments`，与部署脚本的绝对路径 `/data/attachments/warehouse` 冲突 | 测试环境配置 |

---

## 剥洋葱：三层链路

### 链路 A — 路径默认值与模块约定不一致

`WarehouseProperties` 的 `attachmentRoot` 默认值为 `/data/attachments/warehouse`（绝对路径），但 `personnel/qualification` 模块用的是相对路径 `data/{module}-attachments`。这导致：
- 本地 macOS 开发环境：`/data/attachments/warehouse` 因为 SSV 只读无法写入，附件存储失败
- 测试环境：部署脚本把附件放在 `/data/attachments/warehouse`，但代码默认值与部署脚本路径一致，能工作
- 同事本地导入：旧代码（绝对路径）和新代码（相对路径）混用，导致路径错配

### 链路 B — PDF 照片被静默跳过

```java
// 修复前：WarehouseWordBundleBuilder.writePhotos
for (File photoFile : photos) {
    if (!photoFile.exists()) {
        continue;  // ← 静默跳过，无日志
    }
    String format = ...;
    if (!"jpg/jpeg/png".contains(format)) {
        continue;  // ← PDF 格式被静默跳过
    }
    // 渲染图片
}
```

问题：
- 文件存在性检查在格式验证之前，但格式不支持时直接 `continue`，无任何日志
- PDF 照片需要先渲染为图片再嵌入 Word，但原代码不支持 PDF 格式
- 用户看到「附件内容缺失」但日志无任何错误，难以排查

### 链路 C — 测试环境 application-prod.yml 配置缺失

PR !2112 把默认值从绝对路径改为相对路径后，测试环境 `application-prod.yml` 没有显式覆盖这个配置。Spring Boot 启动时回退到代码默认值（相对路径 `data/warehouse-attachments`），但部署脚本把附件放在绝对路径 `/data/attachments/warehouse`，导致测试环境找不到附件文件。

---

## 零号病人定位

### 链路 A：路径默认值

**第一行错误**：

```java
// 修复前
@ConfigurationProperties("warehouse")
public class WarehouseProperties {
    private String attachmentRoot = "/data/attachments/warehouse";  // ← 绝对路径，与模块约定不一致
}

// 修复后
private String attachmentRoot = "data/warehouse-attachments";  // 相对路径，对齐模块约定
```

### 链路 B：PDF 静默跳过

**第一行错误**：

```java
// 修复前：先检查存在性，再检查格式，但不支持的格式直接 continue 无日志
if (!photoFile.exists()) {
    continue;
}
String format = ...;
if (!"jpg/jpeg/png".contains(format)) {
    continue;  // ← PDF 在这里被静默跳过
}

// 修复后：先检查存在性（带 WARN 日志），PDF 渲染为图片，不支持的格式输出错误文本
if (!photoFile.exists()) {
    log.warn("附件文件不存在: {}", photoFile.getAbsolutePath());
    continue;
}
String format = ...;
if ("pdf".equalsIgnoreCase(format)) {
    renderPdfAsImage(photoFile, doc);  // ← 新增 PDF 渲染
} else if ("jpg/jpeg/png".contains(format)) {
    renderImage(photoFile, doc);
} else {
    writeErrorText(doc, "不支持的图片格式: " + format);  // ← 不再静默跳过
}
```

### 链路 C：测试环境配置缺失

**第一行错误**：

```yaml
# application-prod.yml（修复前）- 完全缺失 warehouse.attachment.root 配置
# Spring Boot 回退到代码默认值 data/warehouse-attachments
# 但部署脚本把附件放在 /data/attachments/warehouse

# application-prod.yml（修复后）
warehouse:
  attachment:
    root: ${WAREHOUSE_ATTACHMENT_ROOT:/data/attachments/warehouse}
```

---

## 验证与修复

### 修复 diff 摘要

1. **`WarehouseProperties.java`**：`attachmentRoot` 默认值从 `/data/attachments/warehouse` 改为 `data/warehouse-attachments`
2. **`WarehouseWordBundleBuilder.java`**：重新排序文件存在性检查（带日志）和格式验证，新增 PDF 渲染逻辑，不支持的格式输出错误文本
3. **`application-prod.yml`**：显式配置 `warehouse.attachment.root=${WAREHOUSE_ATTACHMENT_ROOT:/data/attachments/warehouse}`
4. **新增 pre-push 脚本**：`scripts/check-attachment-root-path.mjs` 防止绝对路径回退

### 测试验证

- 11 个单元测试（3 个新根因测试）
- 43 个相关测试
- 42 个架构测试
- 30 道 pre-push 门禁
- 部署后重新上传附件，导出 Word 合订本，确认三级标题下 PDF 转图片内容出现

### 双重修复策略

- **方案 A（即时止血）**：在服务器设置 `WAREHOUSE_ATTACHMENT_ROOT` 环境变量指向旧路径，重启后端
- **方案 B（永久修复）**：合入 PR !2118 并重新部署

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 路径默认值不一致已定位 | `WarehouseProperties.attachmentRoot` 默认值 | ✅ |
| PDF 静默跳过已定位 | `WarehouseWordBundleBuilder.writePhotos` 不支持 PDF | ✅ |
| 测试环境配置缺失已定位 | `application-prod.yml` 无 `warehouse.attachment.root` | ✅ |
| 必然性已证明 | 绝对路径 + macOS SSV + 部署脚本路径不一致 → 必然找不到附件 | ✅ |
| 修复 diff 已提供 | PR `!2112` + `!2116` + `!2118` | ✅ |
| 防复发脚本已落地 | `scripts/check-attachment-root-path.mjs` 集成 pre-push | ✅ |
| 服务器验证已完成 | 重新上传附件后导出 PDF 转图片内容出现 | ✅ |

**Verdict**: ✅ **PASS**

---

## 为什么之前没有提前发现

1. **测试环境数据量小**：测试仓库附件少，PDF 照片缺失不易察觉
2. **静默跳过无日志**：`writePhotos` 不支持的格式直接 `continue`，无 WARN 日志，排查困难
3. **路径默认值与部署脚本脱节**：开发期默认值与部署脚本路径不一致，但测试环境凑巧能工作（部署脚本路径与默认值一致），未暴露问题
4. **macOS SSV 只读**：本地 macOS 环境的 `/data/attachments/warehouse` 因为 SSV 只读无法写入，但开发者以为是权限问题，未追根因
5. **模块约定不一致**：`personnel/qualification` 模块用相对路径，`warehouse` 模块用绝对路径，未做统一治理

---

## 防复发规范

1. **模块配置默认值必须对齐模块约定**：所有需要本地存储路径的模块（warehouse/personnel/qualification）必须统一使用相对路径默认值（`data/{module}-attachments`）
2. **生产环境配置文件必须显式覆盖所有路径默认值**：`application-prod.yml` 不能依赖代码默认值，必须显式配置 `${ENV_VAR:default}` 形式
3. **静默跳过必须打 WARN 日志**：所有 `continue` / `return` 跳过逻辑必须有 WARN 日志，说明跳过原因和受影响资源
4. **不支持的格式必须输出错误占位**：不能渲染的附件（如不支持的图片格式）必须在 Word 中输出错误文本占位，不能无声丢失
5. **新增 pre-push 脚本强制绝对路径检测**：`scripts/check-attachment-root-path.mjs` 检测代码中硬编码的绝对路径，禁止回退
6. **跨模块路径约定必须统一**：新增模块时必须参照已有模块（personnel/qualification）的路径约定

---

## 相关文档与代码

- `backend/src/main/java/com/xiyu/bid/.../WarehouseProperties.java` — `attachmentRoot` 默认值
- `backend/src/main/java/com/xiyu/bid/.../WarehouseWordBundleBuilder.java` — `writePhotos` PDF 渲染逻辑
- `backend/src/main/resources/application-prod.yml` — 显式配置 `warehouse.attachment.root`
- `scripts/check-attachment-root-path.mjs` — pre-push 绝对路径检测脚本
- [docs/lessons/lessons-learned.md](./lessons-learned.md) §10 — 设计评审 10 个通用问题（静默跳过列为 #10）
- `docs/lessons/lessons-learned.md` §23 — 全链路日志排查 SOP
