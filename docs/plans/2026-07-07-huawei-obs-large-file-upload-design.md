# 华为云 OBS 大文件上传技术设计文档

> 文档状态：设计稿  
> 创建日期：2026-07-07  
> 目标：解决西域数智化投标管理平台 3GB 级标书/文档上传的可靠性、进度可视性与浏览器差异问题。

---

## 1. 背景与目标

### 1.1 当前问题

- 标书文件常见大小为数百 MB 至 3GB，当前上传链路（浏览器 → Nginx → Spring Boot → 本地磁盘）无法稳定支撑。
- 前端多处硬编码 10MB 限制，即使配置放宽到 200MB，单次 HTTP 上传在弱网/公网下仍极易超时、失败且无法恢复。
- 不同浏览器对 `Blob.slice()`、并发连接、HTTP/2 的支持差异导致上传速度不稳定。

### 1.2 设计目标

| 目标 | 说明 |
|---|---|
| 支持 3GB+ 文件 | 标书、资质包、图纸等超大附件可稳定上传。 |
| 业务服务器不中转文件流 | 文件数据流直接由浏览器写入华为云 OBS，Spring Boot 只处理元数据与状态。 |
| 断点续传 | 网络中断后可从失败分片继续上传，不重传整个文件。 |
| 进度可视化 | 前端可实时获取上传进度。 |
| 状态机驱动 | 文件必须经过 Uploading → Uploaded → （校验/扫描/OCR） → Completed 才能被下载。 |
| 安全可控 | 前端不持有永久 AK/SK，使用 STS 临时凭证；Bucket 私有，下载使用预签名 URL。 |

---

## 2. 总体架构

### 2.1 数据流

```
┌─────────────┐     ① 申请上传凭证      ┌─────────────────┐
│   浏览器     │ ─────────────────────→ │   业务后端        │
│             │                         │  Spring Boot    │
│             │ ←────────────────────── │                 │
│             │   ② 临时 AK/SK/Token    │                 │
│             │   + bucket/endpoint     │                 │
│             │   + objectKey/uploadId  │                 │
│             │                          └────────┬────────┘
│             │                                   │
│             │ ③ ObsClient.multipartUpload       │ ④ 调用华为云 IAM STS
│             │    直传华为云 OBS                  │    AssumeAgency
│             │ ─────────────────────────────→   │
│             │                                   │
│             │ ←──────────────────────────────  │
│             │   OBS 返回 objectKey/etag         │
│             │                                   │
│             │ ⑤ 通知上传完成                    │
│             │ ─────────────────────────────→   │
│             │                                   │
│             │ ⑥ 后端异步: MD5校验→病毒扫描→OCR  │
│             │ ←──────────────────────────────  │
│             │   ⑦ 状态变为 Completed            │
└─────────────┘                                   │
                                                  ▼
                                           ┌──────────┐
                                           │ 华为云 OBS │
                                           │  Bucket   │
                                           └──────────┘
```

### 2.2 角色边界

| 组件 | 职责 |
|---|---|
| 浏览器 | 持有临时凭证，调用 OBS BrowserJS SDK 完成分片上传；上报完成事件。 |
| Spring Boot | 签发 STS 临时凭证；创建并维护 `BidFile` 记录；接收完成通知；触发并跟踪后处理流程；生成下载预签名 URL。 |
| 华为云 IAM STS | 按委托策略颁发临时 AK/SK/SecurityToken。 |
| 华为云 OBS | 接收并存储分片对象；合并分片；提供对象元数据（ETag/Size）。 |
| 后处理服务（可选） | 病毒扫描、OCR、内容解析等。 |

---

## 3. 对象存储接入策略

### 3.1 为什么用 STS 临时凭证而不是预签名 URL

3GB 分片上传需要多次 OBS API 调用：
- `InitiateMultipartUpload`
- 多次 `UploadPart`
- `ListParts`
- `CompleteMultipartUpload`

预签名 URL 模式需要为每一次请求单独生成签名 URL，实现复杂且 stateful。STS 临时凭证模式下，前端直接初始化 `ObsClient`，由 SDK 自动管理签名，**实现最简单、功能最完整**。

### 3.2 临时凭证获取方式

华为云提供两种方式：

| 方式 | API | 适用场景 |
|---|---|---|
| 委托令牌 | `AssumeAgency` | 推荐。后端用永久 AK/SK 调用 IAM，指定委托名称，获取临时凭证。 |
| Token 换临时密钥 | `CreateTemporaryAccessKeyByToken` | 已有用户 Token 时使用，不适合服务端场景。 |

**本设计采用 `AssumeAgency`**：
- 在华为云 IAM 中创建委托（Agency），授权策略仅包含目标 Bucket 的 `obs:object:PutObject`、`obs:object:GetObject`、`obs:object:DeleteObject`、`obs:bucket:ListBucketMultipartUploads` 等必要权限。
- 后端永久 AK/SK 仅保存在服务器环境变量/密钥管理服务中，不暴露给前端。

### 3.3 临时凭证有效期

- 建议有效期：**1 小时**（3600 秒）。
- 对于 3GB 文件，1 小时在 10Mbps 带宽下足够；如果网络更差，可延长至 4 小时，但需权衡安全风险。
- 前端应在凭证过期前主动刷新或提示用户重试。

### 3.4 Bucket 与对象命名

- **Bucket**：私有化，禁止公开读取。
- **对象前缀**：`bids/{yyyy}/{mm}/{uploadId}/{safeFileName}`
  - 示例：`bids/2026/07/018f3b2c-xxx/某项目标书.pdf`
- **uploadId**：业务系统生成的唯一标识，与 `bid_file.upload_id` 对应，便于追溯。
- **safeFileName**：去除路径遍历风险的文件名。

---

## 4. 前端设计

### 4.1 SDK 选型

使用华为云官方 BrowserJS SDK：

```bash
npm install esdk-obs-browserjs
```

### 4.2 封装 `useObsUpload` Composable

```javascript
// src/composables/useObsUpload.js
import { ref } from 'vue'
import ObsClient from 'esdk-obs-browserjs'
import { filesApi } from '@/api/index.js'

export function useObsUpload() {
  const progress = ref(0)
  const status = ref('idle') // idle / getting-token / uploading / completed / error
  const error = ref(null)

  async function upload(file, options = {}) {
    status.value = 'getting-token'
    progress.value = 0
    error.value = null

    // 1. 业务后端申请上传凭证
    const token = await filesApi.getUploadToken({
      fileName: file.name,
      fileSize: file.size,
      fileHash: options.fileHash, // 可选，整文件 MD5
      businessType: options.businessType || 'tender',
    })

    // 2. 初始化 OBS 客户端
    const obsClient = new ObsClient({
      access_key_id: token.ak,
      secret_access_key: token.sk,
      security_token: token.securityToken,
      server: token.endpoint,
      timeout: 120 * 1000, // 单请求超时 120s
    })

    status.value = 'uploading'

    // 3. 分片上传
    return new Promise((resolve, reject) => {
      obsClient.multipartUpload({
        Bucket: token.bucket,
        Key: token.objectKey,
        SourceFile: file,
        PartSize: options.partSize || 10 * 1024 * 1024, // 默认 10MB/片
        ProgressCallback: (bytesUploaded, bytesTotal) => {
          progress.value = Math.round((bytesUploaded / bytesTotal) * 100)
        },
      }, async (err, result) => {
        if (err) {
          status.value = 'error'
          error.value = err
          reject(err)
          return
        }

        // 4. 通知业务后端上传完成
        await filesApi.completeUpload(token.uploadId, {
          objectKey: token.objectKey,
          etag: result.InterfaceResult?.ETag,
          bucket: token.bucket,
        })

        status.value = 'completed'
        progress.value = 100
        resolve({ uploadId: token.uploadId, objectKey: token.objectKey })
      })
    })
  }

  return { upload, progress, status, error }
}
```

### 4.3 使用示例

```vue
<script setup>
import { useObsUpload } from '@/composables/useObsUpload.js'

const { upload, progress, status, error } = useObsUpload()

async function handleFileChange(file) {
  try {
    const result = await upload(file, {
      businessType: 'tender',
      partSize: 20 * 1024 * 1024, // 3GB 文件可用 20MB 分片
    })
    // 拿到 uploadId 后继续业务操作，如关联到标讯记录
  } catch (e) {
    console.error('上传失败', e)
  }
}
</script>
```

### 4.4 浏览器能力降级

对于不支持 BrowserJS SDK 的环境（如 IE11），可提示用户升级浏览器。华为云 BrowserJS SDK 要求完整支持 HTML5。

---

## 5. 后端设计

### 5.1 依赖引入

```xml
<!-- backend/pom.xml -->
<dependency>
    <groupId>com.huaweicloud</groupId>
    <artifactId>huaweicloud-sdk-obs</artifactId>
    <version>3.24.3</version>
</dependency>
<dependency>
    <groupId>com.huaweicloud</groupId>
    <artifactId>huaweicloud-sdk-iam</artifactId>
    <version>3.24.3</version>
</dependency>
```

### 5.2 配置项

```yaml
# backend/src/main/resources/application.yml
xiyu:
  obs:
    enabled: true
    endpoint: ${XIYU_OBS_ENDPOINT:https://obs.cn-east-3.myhuaweicloud.com}
    region: ${XIYU_OBS_REGION:cn-east-3}
    bucket: ${XIYU_OBS_BUCKET:xiyu-bid-documents}
    ak: ${XIYU_OBS_ACCESS_KEY}
    sk: ${XIYU_OBS_SECRET_KEY}
    agency-urn: ${XIYU_OBS_AGENCY_URN}  # 委托 URN
    token-duration-seconds: ${XIYU_OBS_TOKEN_DURATION:3600}
    # 临时凭证可执行的操作范围
    allowed-actions:
      - obs:object:PutObject
      - obs:object:GetObject
      - obs:object:DeleteObject
      - obs:bucket:ListBucketMultipartUploads
```

### 5.3 核心领域模型

```java
// backend/src/main/java/com/xiyu/bid/file/domain/BidFile.java
@Entity
@Table(name = "bid_file")
public class BidFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "upload_id", nullable = false, unique = true, length = 64)
    private String uploadId;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BidFileStatus status;

    @Column(name = "original_name", nullable = false, length = 500)
    private String originalName;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "bucket", nullable = false, length = 100)
    private String bucket;

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "file_hash", length = 64)
    private String fileHash;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    // getters / setters / business methods
}
```

```java
// backend/src/main/java/com/xiyu/bid/file/domain/BidFileStatus.java
public enum BidFileStatus {
    UPLOADING,        // 已签发凭证，等待前端上传
    UPLOADED,         // 前端通知上传完成
    MD5_CHECKING,     // 校验 MD5/ETag
    VIRUS_SCANNING,   // 病毒扫描中
    OCR_PROCESSING,   // OCR/内容解析中
    COMPLETED,        // 全部完成，可下载
    FAILED            // 任意环节失败
}
```

### 5.4 控制器

```java
// backend/src/main/java/com/xiyu/bid/file/adapter/web/FileUploadController.java
@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileUploadController {

    private final IssueUploadTokenUseCase issueUploadTokenUseCase;
    private final CompleteUploadUseCase completeUploadUseCase;
    private final GetDownloadUrlUseCase getDownloadUrlUseCase;

    @PostMapping("/upload-token")
    public ApiResponse<UploadTokenResponse> issueUploadToken(
            @RequestBody @Valid UploadTokenRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.success(issueUploadTokenUseCase.execute(request, user.getUserId()));
    }

    @PostMapping("/{uploadId}/completed")
    public ApiResponse<Void> completeUpload(
            @PathVariable String uploadId,
            @RequestBody @Valid UploadCompletedRequest request,
            @AuthenticationPrincipal UserPrincipal user) {
        completeUploadUseCase.execute(uploadId, request, user.getUserId());
        return ApiResponse.success("已收到上传完成通知", null);
    }

    @GetMapping("/{uploadId}/download-url")
    public ApiResponse<DownloadUrlResponse> getDownloadUrl(
            @PathVariable String uploadId,
            @RequestParam(defaultValue = "300") int expireSeconds,
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.success(getDownloadUrlUseCase.execute(uploadId, expireSeconds, user.getUserId()));
    }
}
```

### 5.5 签发 STS 凭证

```java
// backend/src/main/java/com/xiyu/bid/file/infrastructure/obs/HuaweiObsTokenService.java
@Service
@RequiredArgsConstructor
public class HuaweiObsTokenService {

    private final ObsProperties obsProperties;

    public TemporaryCredentials issueToken(String uploadId, long fileSize) {
        // 使用华为云 IAM SDK 调用 AssumeAgency
        ICredential auth = new BasicCredentials()
                .withAk(obsProperties.getAk())
                .withSk(obsProperties.getSk());

        StsClient client = StsClient.newBuilder()
                .withCredential(auth)
                .withRegion(StsRegion.valueOf(obsProperties.getRegion()))
                .build();

        AssumeAgencyReqBody body = new AssumeAgencyReqBody()
                .withAgencyUrn(obsProperties.getAgencyUrn())
                .withAgencySessionName("xiyu-bid-" + uploadId)
                .withDurationSeconds(obsProperties.getTokenDurationSeconds().longValue());

        AssumeAgencyRequest request = new AssumeAgencyRequest().withBody(body);
        AssumeAgencyResponse response = client.assumeAgency(request);
        AssumeAgencyResponse.Credentials creds = response.getCredentials();

        return new TemporaryCredentials(
                creds.getAccessKey(),
                creds.getSecretKey(),
                creds.getSecuritytoken(),
                creds.getExpiresAt()
        );
    }
}
```

### 5.6 完成上传与后处理

```java
// backend/src/main/java/com/xiyu/bid/file/application/CompleteUploadUseCase.java
@Service
@RequiredArgsConstructor
@Transactional
public class CompleteUploadUseCase {

    private final BidFileRepository bidFileRepository;
    private final ObsMetadataService obsMetadataService;
    private final ApplicationEventPublisher eventPublisher;

    public void execute(String uploadId, UploadCompletedRequest request, Long operatorId) {
        BidFile bidFile = bidFileRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new NotFoundException("上传记录不存在"));

        // 权限校验：只有创建者或管理员可以完成
        if (!bidFile.getCreatorId().equals(operatorId)) {
            throw new ForbiddenException("无权操作该上传记录");
        }

        // 校验 OBS 对象是否存在、大小是否匹配
        ObsObjectMetadata metadata = obsMetadataService.getMetadata(
                bidFile.getBucket(), request.getObjectKey());

        if (metadata == null || !metadata.getContentLength().equals(bidFile.getFileSize())) {
            bidFile.fail("OBS 对象不存在或大小不匹配");
            bidFileRepository.save(bidFile);
            throw new BusinessException("上传文件校验失败");
        }

        bidFile.setObjectKey(request.getObjectKey());
        bidFile.setStatus(BidFileStatus.UPLOADED);
        bidFileRepository.save(bidFile);

        // 触发异步后处理
        eventPublisher.publishEvent(new BidFileUploadedEvent(uploadId));
    }
}
```

### 5.7 异步后处理监听器

```java
// backend/src/main/java/com/xiyu/bid/file/application/BidFileUploadedEventHandler.java
@Component
@RequiredArgsConstructor
public class BidFileUploadedEventHandler {

    private final BidFileRepository bidFileRepository;
    private final ObsMetadataService obsMetadataService;

    @Async("fileProcessingExecutor")
    @EventListener
    public void handle(BidFileUploadedEvent event) {
        String uploadId = event.getUploadId();
        BidFile bidFile = bidFileRepository.findByUploadId(uploadId).orElse(null);
        if (bidFile == null) return;

        try {
            // 1. MD5 校验
            bidFile.transitionTo(BidFileStatus.MD5_CHECKING);
            bidFileRepository.save(bidFile);
            validateEtag(bidFile);

            // 2. 病毒扫描（占位，接入具体服务）
            bidFile.transitionTo(BidFileStatus.VIRUS_SCANNING);
            bidFileRepository.save(bidFile);
            // virusScanService.scan(bidFile);

            // 3. OCR / 内容解析（占位）
            bidFile.transitionTo(BidFileStatus.OCR_PROCESSING);
            bidFileRepository.save(bidFile);
            // ocrService.extract(bidFile);

            // 4. 完成
            bidFile.transitionTo(BidFileStatus.COMPLETED);
            bidFile.setCompletedAt(Instant.now());
            bidFileRepository.save(bidFile);

        } catch (Exception e) {
            bidFile.fail(e.getMessage());
            bidFileRepository.save(bidFile);
        }
    }

    private void validateEtag(BidFile bidFile) {
        // OBS 非加密上传时 ETag 为文件 MD5
        // 如果业务上传时提供了 fileHash，可对比校验
        String etag = obsMetadataService.getEtag(bidFile.getBucket(), bidFile.getObjectKey());
        if (bidFile.getFileHash() != null && !bidFile.getFileHash().equalsIgnoreCase(etag)) {
            throw new BusinessException("文件 MD5 校验失败");
        }
    }
}
```

---

## 6. 数据模型

### 6.1 `bid_file` 表

```sql
CREATE TABLE bid_file (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    upload_id VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    original_name VARCHAR(500) NOT NULL,
    object_key VARCHAR(500) NOT NULL,
    bucket VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    file_hash VARCHAR(64),
    mime_type VARCHAR(100),
    creator_id BIGINT NOT NULL,
    error_message VARCHAR(2000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    INDEX idx_status (status),
    INDEX idx_creator (creator_id),
    INDEX idx_created_at (created_at)
);
```

### 6.2 业务表改造示例

以标讯附件为例，现有表新增 `bid_file_id`：

```sql
ALTER TABLE tender_document
ADD COLUMN bid_file_id BIGINT NULL,
ADD CONSTRAINT fk_tender_document_bid_file
    FOREIGN KEY (bid_file_id) REFERENCES bid_file(id);
```

下载时通过 `bid_file_id` 查询 `bid_file` 状态，只有 `COMPLETED` 才生成下载 URL。

---

## 7. API 设计

### 7.1 `POST /api/files/upload-token`

**请求体**

```json
{
  "fileName": "某项目标书.pdf",
  "fileSize": 3221225472,
  "fileHash": "d41d8cd98f00b204e9800998ecf8427e",
  "businessType": "tender",
  "mimeType": "application/pdf"
}
```

**响应体**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "uploadId": "018f3b2c-1234-7abc-8def-0123456789ab",
    "ak": "QIA...",
    "sk": "******",
    "securityToken": "gQpjbi1ub3J0aC0x...",
    "expiresAt": "2026-07-07T14:00:00Z",
    "bucket": "xiyu-bid-documents",
    "endpoint": "https://obs.cn-east-3.myhuaweicloud.com",
    "region": "cn-east-3",
    "objectKey": "bids/2026/07/018f3b2c-xxx/某项目标书.pdf"
  }
}
```

### 7.2 `POST /api/files/{uploadId}/completed`

**请求体**

```json
{
  "objectKey": "bids/2026/07/018f3b2c-xxx/某项目标书.pdf",
  "etag": "\"d41d8cd98f00b204e9800998ecf8427e\"",
  "bucket": "xiyu-bid-documents"
}
```

### 7.3 `GET /api/files/{uploadId}/download-url?expireSeconds=300`

**响应体**

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "url": "https://obs.cn-east-3.myhuaweicloud.com/xiyu-bid-documents/...?AccessKeyId=...&Expires=...&Signature=...",
    "expiresAt": "2026-07-07T13:05:00Z"
  }
}
```

---

## 8. 安全设计

### 8.1 凭证安全

- 前端**永不保存**永久 AK/SK。
- 临时凭证通过 HTTPS 下发，有效期可控。
- 后端调用 IAM STS 时使用最小权限委托。

### 8.2 Bucket 策略

- Bucket 为**私有读写**。
- 禁止公开桶策略与公开 ACL。
- 可配置桶策略限制只允许指定 VPC / IP 段访问。

### 8.3 CORS 配置

OBS Bucket 需要配置 CORS，允许前端域名发起 PUT/POST/GET 请求。

> **关键**：CORS 是 **Bucket 级别**配置，与是否使用自定义域名（`XIYU_OBS_DOWNLOAD_CUSTOM_DOMAIN`）无关。
> 只要前端发起的请求会跨域到 OBS（无论是标准 endpoint 还是 CNAME 自定义域名），都必须在 OBS 控制台为桶配置 CORS 规则。
> 后端 302 重定向到 OBS 预签名 URL 的场景下，浏览器会自动跟随重定向并对 OBS 域名发起 preflight，OBS 必须返回 `Access-Control-Allow-Origin` 才能成功。
>
> **配置入口**：华为云 OBS 控制台 → 桶 → 跨域访问规则（或 `obs://bucket/?cors`）

`AllowedOrigin` 必须包含**所有**会发起 OBS 请求的前端域名（生产 + 测试 + 本地开发）：

```xml
<CORSConfiguration>
  <CORSRule>
    <!-- 生产环境前端域名 -->
    <AllowedOrigin>https://bid.xiyu.com</AllowedOrigin>
    <AllowedOrigin>https://winbid.ehsy.com</AllowedOrigin>
    <!-- 测试环境前端域名 -->
    <AllowedOrigin>https://winbid-test.ehsy.com</AllowedOrigin>
    <!-- 本地开发（可选，按团队需要） -->
    <AllowedOrigin>http://localhost:1323</AllowedOrigin>
    <AllowedOrigin>http://127.0.0.1:1323</AllowedOrigin>

    <AllowedMethod>GET</AllowedMethod>
    <AllowedMethod>PUT</AllowedMethod>
    <AllowedMethod>POST</AllowedMethod>
    <AllowedMethod>HEAD</AllowedMethod>

    <AllowedHeader>*</AllowedHeader>

    <!-- ExposeHeader 必须包含 Content-Disposition / Content-Length，否则前端无法读取文件名和进度（CO-285 教训） -->
    <ExposeHeader>ETag</ExposeHeader>
    <ExposeHeader>x-obs-request-id</ExposeHeader>
    <ExposeHeader>Content-Disposition</ExposeHeader>
    <ExposeHeader>Content-Length</ExposeHeader>
  </CORSRule>
</CORSConfiguration>
```

> **新增前端域名时必须同步更新此 CORS 配置**，否则会出现下载接口 302 到 OBS 后 preflight 失败（详见 lessons-learned.md §74）。

### 8.4 下载鉴权

- 只有 `status = COMPLETED` 的文件才允许生成下载 URL。
- 下载 URL 有效期默认 5 分钟，最大不超过 1 小时。
- 下载 URL 按用户鉴权，防止越权访问他人文件。

---

## 9. 部署与配置

### 9.1 新增环境变量

```bash
# 华为云 OBS
XIYU_OBS_ENDPOINT=https://obs.cn-east-3.myhuaweicloud.com
XIYU_OBS_REGION=cn-east-3
XIYU_OBS_BUCKET=xiyu-bid-documents
XIYU_OBS_ACCESS_KEY=...
XIYU_OBS_SECRET_KEY=...
XIYU_OBS_AGENCY_URN=urn:fss:xxx::agency/xiyu-bid-upload
XIYU_OBS_TOKEN_DURATION=3600
```

### 9.2 Nginx 调整

由于文件流不再经过 Nginx 和业务后端，Nginx 层的 `client_max_body_size` 对 OBS 上传不再起作用。但为保障旧接口兼容及非 OBS 上传场景，保留 Phase 1 配置即可。

### 9.3 华为云侧准备

1. 创建 Bucket（私有）。
2. 创建 IAM 委托，绑定最小权限策略：
   ```json
   {
     "Version": "1.1",
     "Statement": [
       {
         "Effect": "Allow",
         "Action": [
           "obs:object:PutObject",
           "obs:object:GetObject",
           "obs:object:DeleteObject",
           "obs:bucket:ListBucketMultipartUploads"
         ],
         "Resource": [
           "OBS:*:*:bucket:xiyu-bid-documents",
           "OBS:*:*:object:xiyu-bid-documents/*"
         ]
       }
     ]
   }
   ```
3. **配置 Bucket CORS**（关键步骤，缺失会导致前端下载 302 重定向到 OBS 后 preflight 失败）：
   - 配置入口：华为云 OBS 控制台 → 桶 → 跨域访问规则
   - `AllowedOrigin` 必须包含**所有**会发起 OBS 请求的前端域名（参见 §8.3 完整示例）：
     - 生产：`https://bid.xiyu.com`、`https://winbid.ehsy.com`
     - 测试：`https://winbid-test.ehsy.com`
     - 本地：`http://localhost:1323`、`http://127.0.0.1:1323`（按需）
   - `AllowedMethod`：GET / PUT / POST / HEAD
   - `AllowedHeader`：`*`（或至少 `Content-Type`、`Authorization`、`x-amz-*`、`x-obs-*`）
   - `ExposeHeader`：`ETag`、`x-obs-request-id`、`Content-Disposition`、`Content-Length`
   - **绑定自定义域名（`XIYU_OBS_DOWNLOAD_CUSTOM_DOMAIN`）后也必须配置 CORS**，CORS 是 Bucket 级别配置，与域名映射无关
   - **新增前端域名时必须同步更新此配置**（参见 lessons-learned.md §74）
4. 为后端服务账号创建 AK/SK，并授权 `sts:assumeAgency` 权限。

---

## 10. 实施计划

| 阶段 | 任务 | 交付物 |
|---|---|---|
| **Phase A** | 后端接入华为云 OBS & IAM SDK；实现 `upload-token`、`completed` 接口；创建 `bid_file` 表。 | 可签发 STS 凭证，可创建上传记录。 |
| **Phase B** | 前端封装 `useObsUpload`；标讯上传入口优先接入 OBS 分片上传；展示进度。 | 3GB 标书可稳定上传。 |
| **Phase C** | 下载接口接入预签名 URL；完善状态机；接入 MD5 校验、病毒扫描、OCR 占位实现。 | 上传-下载完整闭环。 |
| **Phase D** | 逐步将项目文档、资质附件、人员附件等其他上传入口迁移到 OBS。 | 全平台统一大文件上传方案。 |

---

## 11. 风险与应对

| 风险 | 影响 | 应对措施 |
|---|---|---|
| 华为云 IAM STS 调用失败 | 无法上传 | 增加重试与降级：短暂失败可返回服务端预签名 URL 作为兜底。 |
| 临时凭证过期 | 大文件上传中断 | 前端监控过期时间，提前刷新；OBS SDK 自动重试。 |
| OBS 分片合并失败 | 文件不完整 | 后端 `completed` 接口校验对象大小与 ETag。 |
| 病毒扫描/OCR 服务不可用 | 文件卡在中间状态 | 提供后台管理接口手动重试或标记跳过。 |
| Bucket 费用超支 | 成本问题 | 配置生命周期策略，定期清理 FAILED/未完成的分片。 |

---

## 12. 参考文档

- [华为云 OBS SDK 概述](https://support.huaweicloud.com/sdkreference-obs/obs_02_0001.html)
- [华为云 BrowserJS SDK 开发指南](https://support.huaweicloud.com/sdk-browserjs-devg-obs/obs_24_0001.html)
- [华为云 Java SDK 开发指南](https://support.huaweicloud.com/sdk-java-devg-obs/obs_21_0001.html)
- [华为云 IAM 临时安全凭证](https://support.huaweicloud.com/usermanual-iam5/iam_01_1238.html)
- [华为云 OBS 分段上传](https://support.huaweicloud.com/sdk-java-devg-obs/obs_21_0607.html)
