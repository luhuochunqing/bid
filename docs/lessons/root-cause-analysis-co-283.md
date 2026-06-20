# CRM 标讯文件下载 URL 双重嵌套 根因分析

> Issue: [CO-283](https://linear.app/ericforai/issue/CO-283/通过crm调用接口创建的标讯点击标讯文件未触发下载跳转报错)
> 日期: 2026-06-20
> 排查者: kimi
> 修复 PR: `!889` / `agent/kimi/co-283-crm-tender-file-download`

---

## 现场还原

**症状素描**：通过 CRM 调用接口创建的标讯，在前端详情页点击标讯文件时，浏览器未触发下载跳转，直接报错。手动把文件链接复制出来可见 URL 被嵌套了两层 `/api/doc-insight/download?fileUrl=`。

**边界划定**：
- 本地上传、标讯解析等内部流程生成的 `doc-insight://...` 附件下载正常 ✅
- 只有 CRM 推送的标讯附件会触发双重嵌套 ❌
- CRM 传入的附件 URL 本身已是 `/api/doc-insight/download?fileUrl=https%3A%2F%2F...` 这种可直接点击的格式

---

## 剥洋葱：逆向调用链

### Layer 1 — 前端详情页

前端 `BasicInfoReadOnly.vue` 渲染附件列表，调用 `normalizeDownloadUrl` 处理 `fileUrl`：

```javascript
// src/views/Bidding/detail/components/BasicInfoReadOnly.vue
function normalizeDownloadUrl(url) {
  if (!url) return ''
  if (url.startsWith('doc-insight://')) {
    return `${API_BASE_URL}/api/doc-insight/download?fileUrl=${encodeURIComponent(url)}`
  }
  if (url.startsWith('/api/') && API_BASE_URL) {
    return `${API_BASE_URL}${url}`
  }
  return url
}
```

前端只处理 `doc-insight://` 与 `/api/` 前缀，**不会对 `/api/doc-insight/download` 做二次包装**。因此问题不在前端。

### Layer 2 — 后端详情查询

标讯详情由 `TenderQueryService.getTenderById()` 返回，附件 URL 经过 `TenderIntegrationMapper.toDownloadUrl()`：

```java
// backend/src/main/java/com/xiyu/bid/tender/service/TenderQueryService.java:60-66
dto.setAttachments(attachments.stream()
        .map(a -> TenderAttachmentDTO.builder()
                .fileName(a.getFileName())
                .fileType(a.getFileType())
                .fileUrl(TenderIntegrationMapper.toDownloadUrl(a.getFileUrl()))
                .build())
        .collect(Collectors.toList()));
```

`TenderMapper.toDTO()` 也调用了同名工具方法：

```java
// backend/src/main/java/com/xiyu/bid/tender/service/TenderMapper.java:52-55
.attachments(tender.getAttachments() == null || tender.getAttachments().isEmpty() ? Collections.emptyList()
        : tender.getAttachments().stream().map(a -> TenderAttachmentDTO.builder()
                .fileName(a.getFileName()).fileType(a.getFileType()).fileUrl(toDownloadUrl(a.getFileUrl())).build())
                .collect(Collectors.toList()))
```

### Layer 3 — URL 转换工具

`TenderIntegrationMapper.toDownloadUrl()` 无条件包装所有非空 URL：

```java
// backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationMapper.java:214-224（修复前）
public static String toDownloadUrl(String u) {
    if (u == null || u.isBlank()) {
        return u;
    }
    return "/api/doc-insight/download?fileUrl=" + URLEncoder.encode(u, StandardCharsets.UTF_8);
}
```

只要 URL 非空，就把整个字符串编码后塞进 `/api/doc-insight/download?fileUrl=`。CRM 传入的 `/api/doc-insight/download?fileUrl=https%3A%2F%2F...` 被再次编码，变成：

```
/api/doc-insight/download?fileUrl=%2Fapi%2Fdoc-insight%2Fdownload%3FfileUrl%3Dhttps%253A%252F%252F...
```

---

## 零号病人定位

**第一行错误**：

```java
// backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationMapper.java:218
return "/api/doc-insight/download?fileUrl=" + URLEncoder.encode(u, StandardCharsets.UTF_8);
```

**必然性解释**：

1. CRM 按约定传入的附件 URL 已经是我们系统的下载代理格式 `/api/doc-insight/download?...`
2. 后端在 DTO 转换阶段无条件对附件 URL 做「下载 URL 化」
3. 该工具方法把 `u` 当作 `doc-insight://` 原始路径处理，没有判断 `u` 是否已是下载地址
4. 二次编码后的 URL 被前端识别为 `/api/...` 相对路径，点击时命中 `DocInsightController.download`
5. `DocInsightController` 把嵌套后的 `/api/doc-insight/download?...` 当作外部文件 URL 代理，解析失败

**状态变迁图**：

```
CRM 推送 tender
  → 附件 fileUrl="/api/doc-insight/download?fileUrl=https%3A%2F%2F..."
  → 后端 TenderIntegrationService 保存 tender（此时 URL 正确）
  → 用户打开标讯详情
  → TenderQueryService 加载附件并调用 toDownloadUrl()
  → URL 被二次嵌套
  → 前端展示错误链接
  → 用户点击 → DocInsightController 解析失败
```

---

## 验证与修复

### 修复 diff

把 `toDownloadUrl` 改为幂等，仅对 `doc-insight://` 做转换，对已是下载地址的 URL 直接返回：

```java
// backend/src/main/java/com/xiyu/bid/integration/external/TenderIntegrationMapper.java:214-226
public static String toDownloadUrl(String u) {
    if (u == null || u.isBlank()) {
        return u;
    }
    // 已是下载地址，避免 CO-283 双重嵌套
    if (u.startsWith("/api/doc-insight/download?")) {
        return u;
    }
    if (u.startsWith("doc-insight://")) {
        return "/api/doc-insight/download?fileUrl=" + URLEncoder.encode(u, StandardCharsets.UTF_8);
    }
    return u;
}
```

由于 `TenderMapper.toDownloadUrl()` 只是委托给 `TenderIntegrationMapper.toDownloadUrl()`，本修复同时覆盖：
- CRM 集成接口返回链路
- 普通标讯详情查询链路

**最小验证**：

新增单元测试覆盖幂等性与边界：

```java
// backend/src/test/java/com/xiyu/bid/integration/external/TenderIntegrationServiceMapToEntityTest.java
@Test
void toDownloadUrl_should_preserve_already_converted_download_url() {
    String alreadyConverted = "/api/doc-insight/download?fileUrl=https%3A%2F%2Fcrm.example.com%2Ffile.pdf";
    assertThat(TenderIntegrationMapper.toDownloadUrl(alreadyConverted)).isEqualTo(alreadyConverted);
}

@Test
void toDownloadUrl_should_convert_doc_insight_protocol() {
    assertThat(TenderIntegrationMapper.toDownloadUrl("doc-insight://bucket/key"))
            .isEqualTo("/api/doc-insight/download?fileUrl=" + URLEncoder.encode("doc-insight://bucket/key", StandardCharsets.UTF_8));
}

@Test
void toDownloadUrl_should_preserve_plain_external_url() {
    assertThat(TenderIntegrationMapper.toDownloadUrl("https://example.com/file.pdf"))
            .isEqualTo("https://example.com/file.pdf");
}
```

---

## 强制二元结论

| 条件 | 验证方式 | 状态 |
|------|---------|------|
| 零号病人已定位 | `TenderIntegrationMapper.toDownloadUrl()` 无条件包装所有非空 URL | ✅ |
| 必然性已证明 | CRM 传入已是下载 URL → 二次包装 → 嵌套 → 控制器解析失败 | ✅ |
| 最小验证已设计 | 新增幂等性单测（已转换 URL、doc-insight 协议、外部 URL） | ✅ |
| 修复 diff 已提供 | 见上文 | ✅ |
| 防复发测试已设计 | `TenderIntegrationServiceMapToEntityTest` 覆盖 | ✅ |

**Verdict**: ✅ **PASS**

### 防复发测试

1. 任何对 `toDownloadUrl()` 的改动必须通过「已转换下载地址幂等」测试。
2. 新增外部系统集成时，附件 URL 样本至少包含：原始文件 URL、`doc-insight://` 路径、已转换下载 URL 三种。
3. 对接外部系统的 URL 转换工具必须满足幂等：`f(f(x)) == f(x)`。

---

## 为什么之前没有提前发现

1. **单测只覆盖了 `doc-insight://` 转换路径**：`TenderIntegrationServiceMapToEntityTest` 原有测试只验证 `doc-insight://` 被正确转换，没有验证「已转换 URL 不再被转换」。
2. **CRM 真实 URL 格式与内部假设不一致**：后端代码假设所有附件 URL 要么是 `doc-insight://`，要么是原始外部 URL；没有考虑到 CRM 会直接回传我们系统的代理下载格式。
3. **统一 URL 转换时丢失了前置判断**：`c160c12a` 把 `TenderMapper.toDownloadUrl()` 改为委托 `TenderIntegrationMapper.toDownloadUrl()`，但后者原本没有前置协议判断，导致全局调用点都受到影响。
