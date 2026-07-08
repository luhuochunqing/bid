# 大文件上传优化方案

> 来源：系统文档上传慢 + 不同浏览器上传速度差异大
> 仓库：xiyu-bid-poc（西域数智化投标管理平台）
> 生成时间：2026-07-07

---

## 1. 背景与问题

### 1.1 问题描述

系统中的标讯文件、项目文档等均为大文件上传，不同浏览器上传速度差异显著，部分用户上传 50MB 文件耗时超过 60 秒，弱网环境下频繁超时失败。

**方案演进**：初步评估时考虑了 HTTP/2、Nginx 直传、自研分片上传、Tus 协议等多种方案。经过进一步确认，标书文件常见大小为数百 MB 至 3GB，自研分片上传在可靠性、运维成本和扩展性上难以满足要求。因此，**最终确定采用华为云 OBS 直传方案**：业务服务器只签发临时凭证和维护状态，浏览器通过华为云 OBS BrowserJS SDK 直接分片上传到对象存储。

> 详细技术设计：[华为云 OBS 大文件上传技术设计文档](file:///workspace/docs/plans/2026-07-07-huawei-obs-large-file-upload-design.md)

### 1.2 当前架构现状

| 维度 | 现状 | 文件位置 |
|---|---|---|
| 前端上传组件 | Element Plus `el-upload`，单文件一次性上传，默认限制 10MB | [ProjectFileUpload.vue](file:///workspace/src/components/common/ProjectFileUpload.vue) |
| 前端 API 封装 | axios `multipart/form-data`，无分片、无断点续传 | [upload.js](file:///workspace/src/api/upload.js) |
| 后端接收 | Spring Boot `MultipartFile`，全量加载到内存或临时文件 | [ProjectDocumentController](file:///workspace/backend/src/main/java/com/xiyu/bid/projectworkflow/controller/ProjectDocumentController.java#L85-L115) |
| 后端存储 | 本地文件系统 `Files.write(bytes)`，SHA-256 去重 | [LocalDocumentStorage](file:///workspace/backend/src/main/java/com/xiyu/bid/docinsight/infrastructure/storage/LocalDocumentStorage.java) |
| Nginx 配置 | HTTP/1.1，`proxy_read_timeout 60s`，请求体全量缓存 | [nginx.conf](file:///workspace/docker/nginx.conf) |
| 文件大小限制 | 前端 10MB / 后端 50MB | [application.yml](file:///workspace/backend/src/main/resources/application.yml#L6-L8) |
| Web Worker | 无 | - |
| 对象存储 | 无（代码中的 "OSS" 指外部 HR 系统，非对象存储） | - |
| Tus/分片协议 | 无 | - |

### 1.3 浏览器速度差异根因

| 差异根因 | 影响浏览器 | 影响 |
|---|---|---|
| HTTP/2 多路复用支持 | Chrome/Edge/Firefox 支持，旧 Safari 不支持 | 单连接并行 vs 串行，差距 30-50% |
| TLS 1.3 握手优化 | 现代浏览器支持 1-RTT，旧浏览器 2-RTT | 连接建立时间差 100-300ms |
| 并发连接数限制 | Chrome 6个/域名，Safari 4-6个不等 | 多文件并发差距 |
| `Blob.slice()` 性能 | Chrome V8 最快，Firefox 较慢 | 分片切割耗时差异 |
| 内存管理策略 | Safari 对大 Blob 容易触发回收暂停 | 大文件上传卡顿 |
| `fetch` Streaming 支持 | Chrome/Firefox 支持，Safari 部分支持 | 流式上传兼容性 |

---

## 2. 全维度优化方案

优化从 6 个维度入手，每个维度有独立的收益和改造成本。

### 2.1 维度一：协议层优化

#### 2.1.1 启用 HTTP/2（零代码改动）

当前 [nginx.conf](file:///workspace/docker/nginx.conf#L1-L2) 仅 `listen 80`，未启用 HTTP/2。HTTP/2 的多路复用让浏览器在单个 TCP 连接上并行发送多个请求，**不需要任何代码改动**就能提升多文件上传的并发性能。

```nginx
# 改动前
listen 80;

# 改动后（需要 TLS）
listen 443 ssl http2;
ssl_certificate     /etc/nginx/ssl/cert.pem;
ssl_certificate_key /etc/nginx/ssl/key.pem;
```

如果无法启用 TLS（内网环境），可以使用 HTTP/1.1 连接池优化：
```nginx
keepalive_timeout 65s;
keepalive_requests 100;
```

#### 2.1.2 Tus 协议（可选，标准化断点续传）

[Tus](https://tus.io/) 是 HTTP 扩展协议，专为可恢复上传设计。优点是标准化、有成熟的客户端和服务端库。

- 后端：[tus-java-store](https://github.com/tomdesair/tus-java-store)
- 前端：[tus-js-client](https://github.com/tus/tus-js-client)

```javascript
// 前端使用 tus-js-client
import * as tus from 'tus-js-client'

const upload = new tus.Upload(file, {
  endpoint: '/api/tus/',
  retryDelays: [0, 1000, 3000, 5000],
  chunkSize: 2 * 1024 * 1024, // 2MB
  metadata: { filename: file.name, filetype: file.type },
  onProgress: (bytesUploaded, bytesTotal) => {
    const percentage = ((bytesUploaded / bytesTotal) * 100).toFixed(2)
    console.log(`上传进度: ${percentage}%`)
  },
  onSuccess: () => console.log('上传完成'),
})
upload.start()
```

**评估**：Tus 协议需要后端引入新依赖和存储适配，改造成本中等，但标准化程度高，长期维护成本低。

### 2.2 维度二：Nginx 传输层优化

#### 2.2.1 关闭请求体缓冲（关键配置）

默认情况下 nginx 会先把整个请求体缓存到磁盘再转发到后端。对于大文件上传，这意味着**多一次磁盘写入 + 延迟**。关闭后 nginx 直接流式转发到后端。

```nginx
location /api/ {
    proxy_pass http://host.docker.internal:18080;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    # ── 大文件上传优化 ──
    proxy_read_timeout 300s;              # 读取超时从 60s → 300s
    proxy_send_timeout 300s;              # 发送超时
    proxy_request_buffering off;          # 关键！流式转发，不缓存请求体
    client_max_body_size 500m;            # 允许大文件
    client_body_buffer_size 1m;           # 缓冲区大小
}
```

#### 2.2.2 Nginx 直传方案（绕过 Spring Boot）

对于超大文件（>100MB），可让 nginx 直接接收文件落盘，完成后只通知后端文件路径，后端完全不中转文件内容。

```nginx
# 大文件直传端点
location /api/direct-upload/ {
    client_body_temp_path /data/shared/uploads/tmp;
    client_body_in_file_only on;
    client_body_buffer_size 1m;
    client_max_body_size 500m;

    proxy_pass http://host.docker.internal:18080/api/upload/notify;
    proxy_set_header X-File-Path $request_body_file;
    proxy_set_header X-Original-Filename $arg_filename;
    proxy_set_header X-File-Size $content_length;
}
```

**评估**：此方案后端只需处理一个轻量 JSON 通知，内存占用几乎为零，但需要 nginx 和后端共享文件系统（当前 `storage-root: /data/shared/tenders` 已满足）。

### 2.3 维度三：前端计算优化

#### 2.3.1 Web Worker 计算文件 Hash

当前前端**完全没有使用 Web Worker**。大文件 hash 计算在主线程会冻结 UI 数秒。通过 Web Worker 将计算移到后台线程：

```javascript
// src/workers/hashWorker.js
self.onmessage = async (e) => {
  const { file, chunkSize } = e.data
  const totalChunks = Math.ceil(file.size / chunkSize)
  let offset = 0

  // 使用 SubtleCrypto 流式计算 SHA-256
  const hasher = await crypto.subtle.digest('SHA-256', new ArrayBuffer(0))
  // 注意：SubtleCrypto 不支持增量更新，需用 SparkMD5 或逐块读取

  for (let i = 0; i < totalChunks; i++) {
    const chunk = file.slice(offset, offset + chunkSize)
    const buffer = await chunk.arrayBuffer()
    // 增量 hash 计算（使用 SparkMD5 或类似库）
    offset += chunkSize
    self.postMessage({
      type: 'progress',
      chunkIndex: i,
      total: totalChunks,
      percent: ((i + 1) / totalChunks * 100).toFixed(1)
    })
  }

  self.postMessage({ type: 'done', hash: 'computed_hash' })
}
```

```javascript
// 组件中使用
const worker = new Worker(new URL('@/workers/hashWorker.js', import.meta.url), { type: 'module' })
worker.onmessage = (e) => {
  if (e.data.type === 'progress') {
    progressRef.value = e.data.percent
  }
  if (e.data.type === 'done') {
    fileHash = e.data.hash
    // 开始上传
  }
}
worker.postMessage({ file, chunkSize: 2 * 1024 * 1024 })
```

#### 2.3.2 Stream API 流式读取

使用 `File.stream()`（ReadableStream）避免一次性将文件读入内存：

```javascript
async function* readChunks(file, chunkSize) {
  const stream = file.stream()
  const reader = stream.getReader()
  let buffer = new Uint8Array(0)

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      if (buffer.length > 0) yield buffer
      break
    }
    // 拼接 buffer 并按 chunkSize 切分
    const merged = new Uint8Array(buffer.length + value.length)
    merged.set(buffer)
    merged.set(value, buffer.length)

    while (merged.length >= chunkSize) {
      yield merged.slice(0, chunkSize)
      buffer = merged.slice(chunkSize)
    }
  }
}
```

#### 2.3.3 Service Worker 缓存 Chunk（可选）

浏览器崩溃或意外关闭后，已上传的 chunk 可通过 Service Worker 缓存恢复：

```javascript
// sw.js — Service Worker
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url)
  if (url.pathname.startsWith('/api/upload/chunk')) {
    // 缓存 chunk 上传响应
    event.respondWith(
      caches.open('upload-chunks').then(async (cache) => {
        const cached = await cache.match(event.request)
        if (cached) return cached
        const response = await fetch(event.request)
        if (response.ok) cache.put(event.request, response.clone())
        return response
      })
    )
  }
})
```

### 2.4 维度四：分片上传 + 断点续传

> **重要更新（2026-07-07）**：考虑到标书文件常见大小为数百 MB 至 3GB，自研分片上传方案（本维度下文）在可靠性、运维成本和扩展性上不如对象存储原生分片上传。经过评估，**最终决定采用华为云 OBS 直传方案**作为 3GB 大文件上传的实现路径。
>
> 详细设计请见：[华为云 OBS 大文件上传技术设计文档](file:///workspace/docs/plans/2026-07-07-huawei-obs-large-file-upload-design.md)
>
> 下文保留的自研分片上传方案仅作为本地存储场景下的备用参考，不再作为本期实施主路径。

这是解决大文件上传的核心方案，也是业界最常用的方案之一。

#### 2.4.1 整体流程

```
┌──────────────────────────────────────────────────────────────────┐
│                         前端上传流程                              │
├──────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. 用户选择文件                                                 │
│  2. Web Worker 计算文件 hash (SHA-256/MD5)                       │
│  3. 请求初始化: POST /api/upload/init                            │
│     ├─ 参数: fileHash, fileName, fileSize, totalChunks          │
│     └─ 返回: uploadId, uploadedChunks(断点续传)                 │
│  4. 跳过 uploadedChunks 中的分片                                 │
│  5. 并行上传分片 (并发度=3):                                     │
│     POST /api/upload/chunk                                       │
│     ├─ 参数: uploadId, chunkIndex, chunkData                    │
│     └─ 返回: { chunkIndex, received: true }                    │
│  6. 全部分片上传完成                                             │
│  7. 请求合并: POST /api/upload/complete                         │
│     ├─ 参数: uploadId                                           │
│     └─ 返回: fileUrl, fileId                                    │
│  8. 上传完成，通知业务模块                                       │
│                                                                  │
└──────────────────────────────────────────────────────────────────┘
```

#### 2.4.2 后端 API 设计

复用已有的 [TenderUploadController](file:///workspace/backend/src/main/java/com/xiyu/bid/tenderupload/controller/TenderUploadController.java) 的 `init → complete` 模式，增加 `chunk` 端点：

```java
// 新增: 分片上传控制器
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ChunkedUploadController {

    private final ChunkedUploadService chunkedUploadService;

    /** 初始化上传会话，返回 uploadId 和已上传分片列表（断点续传） */
    @PostMapping("/init")
    public ResponseEntity<ApiResponse<UploadInitResponse>> init(
            @Valid @RequestBody UploadInitRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(chunkedUploadService.init(request, userDetails)));
    }

    /** 接收单个分片 */
    @PostMapping(value = "/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<ChunkReceiveResponse>> receiveChunk(
            @RequestParam String uploadId,
            @RequestParam int chunkIndex,
            @RequestParam MultipartFile chunk) {
        return ResponseEntity.ok(
                ApiResponse.success(chunkedUploadService.receiveChunk(uploadId, chunkIndex, chunk)));
    }

    /** 合并所有分片，生成最终文件 */
    @PostMapping("/complete")
    public ResponseEntity<ApiResponse<UploadCompleteResponse>> complete(
            @RequestParam String uploadId,
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(
                ApiResponse.success(chunkedUploadService.complete(uploadId, userDetails)));
    }

    /** 取消上传，清理临时分片 */
    @DeleteMapping("/{uploadId}")
    public ResponseEntity<ApiResponse<Void>> cancel(@PathVariable String uploadId) {
        chunkedUploadService.cancel(uploadId);
        return ResponseEntity.ok(ApiResponse.success("上传已取消", null));
    }
}
```

```java
// 分片上传服务核心逻辑
@Service
@RequiredArgsConstructor
@Slf4j
public class ChunkedUploadService {

    private final ChunkedUploadSessionRepository sessionRepository;
    private final LocalDocumentStorage documentStorage;
    private final StorageGuardService storageGuardService;
    private final AuthService authService;

    @Value("${app.upload.chunk-dir:/data/shared/uploads/chunks}")
    private String chunkDir;

    @Value("${app.upload.chunk-size:2097152}")  // 默认 2MB
    private long chunkSize;

    @Transactional
    public UploadInitResponse init(UploadInitRequest request, UserDetails userDetails) {
        Long userId = authService.resolveUserIdByUsername(userDetails.getUsername());
        String fileHash = request.getFileHash();

        // 断点续传：检查是否已有同 hash 的上传会话
        Optional<ChunkedUploadSession> existing = sessionRepository
                .findByFileHashAndUserId(fileHash, userId);

        if (existing.isPresent() && existing.get().getStatus() == UploadStatus.UPLOADING) {
            List<Integer> uploadedChunks = sessionRepository
                    .findChunkIndexesByUploadId(existing.get().getUploadId());
            return UploadInitResponse.builder()
                    .uploadId(existing.get().getUploadId())
                    .uploadedChunks(uploadedChunks)
                    .resumed(true)
                    .build();
        }

        // 新建上传会话
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        ChunkedUploadSession session = ChunkedUploadSession.builder()
                .uploadId(uploadId)
                .userId(userId)
                .fileHash(fileHash)
                .fileName(request.getFileName())
                .fileSize(request.getFileSize())
                .totalChunks(request.getTotalChunks())
                .status(UploadStatus.UPLOADING)
                .build();
        sessionRepository.save(session);

        return UploadInitResponse.builder()
                .uploadId(uploadId)
                .uploadedChunks(Collections.emptyList())
                .resumed(false)
                .build();
    }

    @Transactional
    public ChunkReceiveResponse receiveChunk(String uploadId, int chunkIndex, MultipartFile chunk) {
        ChunkedUploadSession session = sessionRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("上传会话不存在"));

        if (session.getStatus() != UploadStatus.UPLOADING) {
            throw new IllegalStateException("上传会话状态不允许接收分片");
        }

        // 分片写入临时目录
        Path chunkPath = Path.of(chunkDir, uploadId, String.format("chunk-%06d", chunkIndex));
        try {
            Files.createDirectories(chunkPath.getParent());
            // 流式写入，避免内存溢出
            try (InputStream is = chunk.getInputStream();
                 OutputStream os = Files.newOutputStream(chunkPath)) {
                is.transferTo(os);
            }
        } catch (IOException e) {
            throw new IllegalStateException("分片写入失败: chunk-" + chunkIndex, e);
        }

        // 记录已接收分片
        sessionRepository.saveReceivedChunk(uploadId, chunkIndex);

        return ChunkReceiveResponse.builder()
                .chunkIndex(chunkIndex)
                .received(true)
                .build();
    }

    @Transactional
    public UploadCompleteResponse complete(String uploadId, UserDetails userDetails) {
        ChunkedUploadSession session = sessionRepository.findByUploadId(uploadId)
                .orElseThrow(() -> new IllegalArgumentException("上传会话不存在"));

        // 校验所有分片已上传
        List<Integer> receivedChunks = sessionRepository.findChunkIndexesByUploadId(uploadId);
        if (receivedChunks.size() != session.getTotalChunks()) {
            throw new IllegalStateException(String.format(
                    "分片不完整: 已接收 %d/%d", receivedChunks.size(), session.getTotalChunks()));
        }

        // 按顺序合并分片
        Path tempDir = Path.of(chunkDir, uploadId);
        Path targetPath = documentStorage.getUploadRoot()
                .resolve("chunked")
                .resolve(uploadId)
                .resolve(session.getFileName());

        try {
            Files.createDirectories(targetPath.getParent());
            try (OutputStream os = Files.newOutputStream(targetPath)) {
                for (int i = 0; i < session.getTotalChunks(); i++) {
                    Path chunkPath = tempDir.resolve(String.format("chunk-%06d", i));
                    Files.copy(chunkPath, os);
                }
            }
            // 清理临时分片
            tempDir.toFile().delete();
        } catch (IOException e) {
            throw new IllegalStateException("分片合并失败", e);
        }

        // 更新会话状态
        session.setStatus(UploadStatus.COMPLETED);
        sessionRepository.save(session);

        String fileUrl = "doc-insight://chunked/" + uploadId + "/" + session.getFileName();
        return UploadCompleteResponse.builder()
                .fileUrl(fileUrl)
                .fileSize(session.getFileSize())
                .build();
    }

    @Transactional
    public void cancel(String uploadId) {
        sessionRepository.deleteByUploadId(uploadId);
        // 清理临时文件
        Path tempDir = Path.of(chunkDir, uploadId);
        if (Files.exists(tempDir)) {
            try {
                Files.walk(tempDir)
                        .sorted(Comparator.reverseOrder())
                        .forEach(p -> { try { Files.delete(p); } catch (IOException ignored) {} });
            } catch (IOException e) {
                log.warn("清理临时分片失败: {}", uploadId, e);
            }
        }
    }
}
```

#### 2.4.3 前端分片上传实现

```javascript
// src/utils/chunkedUpload.js

const DEFAULT_CHUNK_SIZE = 2 * 1024 * 1024  // 2MB
const MAX_CONCURRENCY = 3                     // 最大并发数

/**
 * 分片上传 + 断点续传
 * @param {File} file - 要上传的文件
 * @param {Object} options - 配置项
 * @param {Function} options.onProgress - 进度回调 (0-100)
 * @param {Function} options.onChunkComplete - 单分片完成回调
 * @param {Function} options.onComplete - 上传完成回调
 * @param {Function} options.onError - 错误回调
 */
export async function chunkedUpload(file, options = {}) {
  const {
    chunkSize = DEFAULT_CHUNK_SIZE,
    onProgress = () => {},
    onChunkComplete = () => {},
    onComplete = () => {},
    onError = () => {},
  } = options

  try {
    // 1. 计算文件 hash（Web Worker）
    const fileHash = await calculateFileHash(file, (percent) => {
      onProgress({ phase: 'hashing', percent })
    })

    const totalChunks = Math.ceil(file.size / chunkSize)

    // 2. 初始化上传会话（支持断点续传）
    const initResponse = await httpClient.post('/api/upload/init', {
      fileHash,
      fileName: file.name,
      fileSize: file.size,
      totalChunks,
    })

    const { uploadId, uploadedChunks = [], resumed } = initResponse.data.data
    const uploadedSet = new Set(uploadedChunks)

    // 3. 构建待上传分片列表
    const pendingChunks = []
    for (let i = 0; i < totalChunks; i++) {
      if (!uploadedSet.has(i)) {
        const start = i * chunkSize
        const end = Math.min(start + chunkSize, file.size)
        pendingChunks.push({ index: i, start, end })
      }
    }

    // 4. 并行上传分片（控制并发数）
    let completedChunks = uploadedChunks.length
    const uploadChunk = async ({ index, start, end }) => {
      const chunk = file.slice(start, end)
      const formData = new FormData()
      formData.append('uploadId', uploadId)
      formData.append('chunkIndex', index)
      formData.append('chunk', chunk)

      await httpClient.post('/api/upload/chunk', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
        timeout: 120000,  // 单分片超时 2 分钟
      })

      completedChunks++
      const percent = (completedChunks / totalChunks * 100).toFixed(1)
      onProgress({ phase: 'uploading', percent })
      onChunkComplete({ index, completed: completedChunks, total: totalChunks })
    }

    // 并发控制
    const queue = [...pendingChunks]
    const workers = []
    for (let i = 0; i < Math.min(MAX_CONCURRENCY, queue.length); i++) {
      workers.push((async () => {
        while (queue.length > 0) {
          const chunk = queue.shift()
          if (chunk) await uploadChunk(chunk)
        }
      })())
    }
    await Promise.all(workers)

    // 5. 所有分片上传完成，请求合并
    const completeResponse = await httpClient.post('/api/upload/complete', null, {
      params: { uploadId },
    })

    onComplete(completeResponse.data.data)
    return completeResponse.data.data
  } catch (error) {
    onError(error)
    throw error
  }
}

/**
 * 在 Web Worker 中计算文件 hash
 */
function calculateFileHash(file, onProgress) {
  return new Promise((resolve, reject) => {
    const worker = new Worker(new URL('@/workers/hashWorker.js', import.meta.url), { type: 'module' })
    worker.onmessage = (e) => {
      if (e.data.type === 'progress') {
        onProgress(e.data.percent)
      }
      if (e.data.type === 'done') {
        worker.terminate()
        resolve(e.data.hash)
      }
      if (e.data.type === 'error') {
        worker.terminate()
        reject(new Error(e.data.message))
      }
    }
    worker.postMessage({ file, chunkSize: DEFAULT_CHUNK_SIZE })
  })
}
```

#### 2.4.4 动态分片大小

根据网络状况动态调整分片大小，弱网用小片（减少重传成本），强网用大片（减少请求开销）：

```javascript
async function detectOptimalChunkSize() {
  const testSize = 100 * 1024  // 100KB 探测包
  const start = performance.now()
  try {
    await fetch('/api/upload/speed-test', {
      method: 'POST',
      body: new ArrayBuffer(testSize),
    })
    const duration = (performance.now() - start) / 1000
    const speed = testSize / duration  // bytes/sec

    if (speed > 10 * 1024 * 1024) return 5 * 1024 * 1024   // >10MB/s → 5MB chunks
    if (speed > 2 * 1024 * 1024)  return 2 * 1024 * 1024   // >2MB/s  → 2MB chunks
    return 512 * 1024                                         // 弱网 → 512KB chunks
  } catch {
    return 1 * 1024 * 1024  // 默认 1MB
  }
}
```

### 2.5 维度五：后端接收优化

#### 2.5.1 Spring Boot Multipart 配置优化

```yaml
# application.yml
spring:
  servlet:
    multipart:
      max-file-size: 200MB
      max-request-size: 200MB
      file-size-threshold: 0           # 立即写磁盘，不占内存
      location: /data/shared/uploads/tmp  # 临时文件目录
```

#### 2.5.2 流式接收（避免 OOM）

当前 [LocalDocumentStorage.store()](file:///workspace/backend/src/main/java/com/xiyu/bid/docinsight/infrastructure/storage/LocalDocumentStorage.java#L32-L50) 接收 `byte[]` 参数，整个文件在内存中。改为流式写入：

```java
// 新增流式存储方法
public StoredDocument storeStream(String category, String entityId, String fileName,
                                   String contentType, InputStream inputStream, long fileSize) {
    Path targetDir = uploadRoot.resolve(category).resolve(entityId);
    Path targetPath = targetDir.resolve(fileName);

    try {
        Files.createDirectories(targetDir);
        // 流式写入，内存占用恒定
        try (InputStream is = inputStream;
             OutputStream os = Files.newOutputStream(targetPath)) {
            is.transferTo(os);
        }
    } catch (IOException ex) {
        throw new IllegalStateException("Failed to store document", ex);
    }

    return new StoredDocument(
            "doc-insight://" + category + "/" + entityId + "/" + fileName,
            targetPath.toAbsolutePath().toString(),
            null  // hash 延迟计算
    );
}
```

#### 2.5.3 数据库表设计

```sql
-- 分片上传会话表
CREATE TABLE chunked_upload_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    upload_id VARCHAR(32) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    file_hash VARCHAR(64) NOT NULL,         -- 文件整体 hash
    file_name VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL,
    total_chunks INT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'UPLOADING',  -- UPLOADING / COMPLETED / CANCELLED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_file_hash_user (file_hash, user_id),
    INDEX idx_upload_id (upload_id)
);

-- 已接收分片记录表
CREATE TABLE chunked_upload_chunk (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    upload_id VARCHAR(32) NOT NULL,
    chunk_index INT NOT NULL,
    received_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_upload_chunk (upload_id, chunk_index),
    INDEX idx_upload_id (upload_id)
);
```

### 2.6 维度六：浏览器兼容性处理

#### 2.6.1 浏览器能力检测

```javascript
const browserCapabilities = {
  http2: async () => {
    try {
      const entry = performance.getEntriesByName(window.location.origin)[0]
      return entry?.nextHopProtocol === 'h2'
    } catch {
      return false
    }
  },
  webWorker: () => typeof Worker !== 'undefined',
  fileStream: () => 'stream' in File.prototype,
  serviceWorker: () => 'serviceWorker' in navigator,
  cryptoSubtle: () => 'subtle' in crypto,
}

async function chooseUploadStrategy(file) {
  const caps = {
    http2: await browserCapabilities.http2(),
    webWorker: browserCapabilities.webWorker(),
    fileStream: browserCapabilities.fileStream(),
  }

  // 小文件（<5MB）且 HTTP/2 环境下直接上传，无需分片
  if (file.size < 5 * 1024 * 1024 && caps.http2) {
    return 'direct'
  }

  // 大文件 + 支持 Web Worker → 分片上传
  if (caps.webWorker && file.size > 5 * 1024 * 1024) {
    return 'chunked-worker'
  }

  // 大文件但不支持 Web Worker → 降级分片（主线程计算 hash）
  if (file.size > 5 * 1024 * 1024) {
    return 'chunked-fallback'
  }

  return 'direct'
}
```

#### 2.6.2 降级策略

```javascript
async function uploadFile(file, options) {
  const strategy = await chooseUploadStrategy(file)

  switch (strategy) {
    case 'direct':
      // 直接上传（小文件 + HTTP/2）
      return directUpload(file, options)

    case 'chunked-worker':
      // 分片上传 + Web Worker hash
      return chunkedUpload(file, { ...options, useWorker: true })

    case 'chunked-fallback':
      // 分片上传 + 主线程 hash（降级）
      return chunkedUpload(file, { ...options, useWorker: false })

    default:
      return directUpload(file, options)
  }
}
```

---

## 3. 分阶段实施计划

### Phase 1：零代码改动优化（立即生效）

**目标**：通过配置变更消除大部分浏览器差异和超时问题

| # | 改动项 | 文件 | 预期效果 |
|---|---|---|---|
| 1 | 启用 HTTP/2（如有 TLS） | [nginx.conf](file:///workspace/docker/nginx.conf) | 多文件并行上传，速度提升 30-50% |
| 2 | `proxy_request_buffering off` | [nginx.conf](file:///workspace/docker/nginx.conf#L18-L25) | 流式转发，减少一次磁盘写入 |
| 3 | `proxy_read_timeout` 60s → 300s | [nginx.conf](file:///workspace/docker/nginx.conf#L24) | 大文件不再超时 |
| 4 | `client_max_body_size` → 500m | [nginx.conf](file:///workspace/docker/nginx.conf) | 允许大文件通过 nginx |
| 5 | `max-file-size` 50MB → 200MB | [application.yml](file:///workspace/backend/src/main/resources/application.yml#L6-L8) | 后端允许接收大文件 |
| 6 | `file-size-threshold: 0` | [application.yml](file:///workspace/backend/src/main/resources/application.yml#L6-L8) | 立即写磁盘，避免 OOM |
| 7 | 前端 `maxSizeMb` 10 → 100 | [ProjectFileUpload.vue](file:///workspace/src/components/common/ProjectFileUpload.vue#L53) | 前端允许选择大文件 |
| 8 | axios `timeout` 调大 | [client.js](file:///workspace/src/api/client.js#L30) | 避免前端超时断开 |

### Phase 2：华为云 OBS 直传核心实现（中等成本）

**目标**：实现 3GB 级标书文件稳定上传，业务服务器不中转文件流

| # | 改动项 | 类型 | 说明 |
|---|---|---|---|
| 1 | 引入华为云 OBS & IAM SDK | 后端 | `backend/pom.xml` 增加 `huaweicloud-sdk-obs`、`huaweicloud-sdk-iam` |
| 2 | 新增 OBS 配置 | 后端 | `application.yml` 配置 endpoint/region/bucket/ak/sk/agency-urn |
| 3 | 新增 `FileUploadController` | 后端 | `/api/files/upload-token`、`/api/files/{id}/completed`、`/api/files/{id}/download-url` |
| 4 | 新增 `HuaweiObsTokenService` | 后端 | 调用 IAM STS `AssumeAgency` 签发临时凭证 |
| 5 | 新增 `BidFile` 领域模型 | 后端 | 上传记录与状态机 |
| 6 | Flyway 迁移脚本 | 后端 | 创建 `bid_file` 表 |
| 7 | 新增 `useObsUpload` composable | 前端 | 封装 OBS BrowserJS SDK `multipartUpload` |
| 8 | 标讯上传入口接入 OBS | 前端 | 优先改造 `ProjectDetailBidAgentTenderUpload.vue` |
| 9 | 新增 OBS 上传进度组件 | 前端 | 显示分片上传进度、速度、剩余时间 |

### Phase 3：后处理与下载闭环（中等成本）

| # | 改动项 | 说明 |
|---|---|---|
| 1 | MD5/ETag 校验 | `completed` 接口校验对象大小与 ETag |
| 2 | 病毒扫描占位 | 接入扫描服务前保留扩展点 |
| 3 | OCR 占位 | 标书内容解析扩展点 |
| 4 | 预签名下载 URL | 只有 `COMPLETED` 状态才生成下载链接 |
| 5 | 业务表改造 | `tender_document` 等表增加 `bid_file_id` |

### Phase 4：全平台迁移（未来，按需）

| # | 改动项 | 说明 |
|---|---|---|
| 1 | 项目文档接入 OBS | 改造 `ProjectFileUpload.vue` 支持 OBS |
| 2 | 资质/人员附件接入 OBS | 改造知识库相关上传入口 |
| 3 | 导入类文件保留限制 | Excel/CSV 导入仍维持 5-10MB，不走 OBS |
| 4 | 本地存储下线 | 大文件全部迁移到 OBS 后移除本地大文件接收逻辑 |

---

## 4. 新增文件清单

> 以下清单按华为云 OBS 直传方案更新。详细接口定义与状态机请参见 [华为云 OBS 大文件上传技术设计文档](file:///workspace/docs/plans/2026-07-07-huawei-obs-large-file-upload-design.md)。

### 后端

```
backend/src/main/java/com/xiyu/bid/file/
├── adapter/
│   └── web/
│       └── FileUploadController.java           # /api/files/* 接口
├── application/
│   ├── IssueUploadTokenUseCase.java            # 签发 STS 凭证
│   ├── CompleteUploadUseCase.java              # 完成上传校验
│   ├── GetDownloadUrlUseCase.java              # 预签名下载 URL
│   └── BidFileUploadedEventHandler.java        # 异步后处理监听器
├── domain/
│   ├── BidFile.java                            # 上传记录聚合根
│   ├── BidFileStatus.java                      # 状态机枚举
│   └── BidFileRepository.java                  # 仓库接口
├── infrastructure/
│   ├── obs/
│   │   ├── HuaweiObsTokenService.java          # IAM STS 临时凭证
│   │   ├── ObsMetadataService.java             # OBS 对象元数据查询
│   │   └── ObsProperties.java                  # 配置绑定
│   └── persistence/
│       └── BidFileJpaRepository.java           # JPA 实现
└── dto/
    ├── UploadTokenRequest.java
    ├── UploadTokenResponse.java
    ├── UploadCompletedRequest.java
    └── DownloadUrlResponse.java
```

### 前端

```
src/
├── api/
│   └── modules/
│       └── files.js                            # /api/files/* 封装
├── composables/
│   └── useObsUpload.js                         # OBS 分片上传封装
└── components/
    └── common/
        └── ObsUploadProgress.vue               # OBS 上传进度组件
```

### 配置变更

```
backend/pom.xml                                # 新增 huaweicloud-sdk-obs / huaweicloud-sdk-iam
docker/nginx.conf                              # HTTP/2 + 流式转发 + 超时调大（保留）
backend/src/main/resources/application.yml     # OBS 配置 + multipart 配置优化
backend/src/main/resources/db/migration-mysql/V{next}__bid_file_table.sql
.env.example                                   # 新增 XIYU_OBS_* 环境变量
```

---

## 5. 方案对比矩阵

| 方案 | 改造成本 | 性能提升 | 可靠性 | 适用文件大小 | 推荐度 |
|---|---|---|---|---|---|
| Phase 1: nginx + 配置优化 | 极低 | 30-50% | 中 | <200MB | ★★★★★ |
| **Phase 2: 华为云 OBS 直传** | **中** | **80-90%** | **极高** | **无限制** | **★★★★★（推荐）** |
| 自研分片上传 + Web Worker | 高 | 60-80% | 高 | <2GB | ★★★☆☆ |
| Nginx 直传 | 低 | 50-70% | 中 | <500MB | ★★★☆☆ |
| Tus 协议 + 本地存储 | 中 | 60-80% | 极高 | <2GB | ★★★☆☆ |
| MinIO 私有部署 | 高 | 80-90% | 高 | 无限制 | ★★☆☆☆ |

---

## 6. 风险评估

| 风险 | 影响 | 缓解措施 |
|---|---|---|
| 华为云 IAM STS 调用失败 | 无法签发凭证 | 增加重试；短暂失败可降级为服务端预签名 URL |
| 临时凭证过期 | 大文件上传中断 | 前端监控过期时间，OBS SDK 自动重试 |
| OBS 分片合并失败 | 文件不完整 | `completed` 接口校验对象大小与 ETag |
| Bucket 费用超支 | 成本问题 | 配置生命周期策略，清理 FAILED/未完成分片 |
| 病毒扫描/OCR 服务不可用 | 文件卡在中间状态 | 提供后台管理接口手动重试或标记跳过 |
| 前端临时凭证泄露 | 安全风险 | 凭证有效期短、权限最小化、HTTPS 传输 |
| 桶策略配置错误 | 数据暴露或上传失败 | 使用私有桶 + 最小权限委托 + CORS 白名单 |

---

## 7. 验收标准

### Phase 1 验收

- [ ] 50MB 文件上传成功率 > 99%
- [ ] 50MB 文件上传耗时 < 30s（局域网环境）
- [ ] Chrome / Firefox / Safari / Edge 上传速度差异 < 20%
- [ ] 上传过程中无 504 Gateway Timeout

### Phase 2 验收（OBS 直传）

- [ ] 3GB 标书文件上传成功率 > 99%（稳定网络）
- [ ] 上传过程中断网恢复后可断点续传
- [ ] 上传进度实时显示，UI 无卡顿
- [ ] 临时凭证不暴露永久 AK/SK
- [ ] 只有 `COMPLETED` 状态的文件才能下载
- [ ] 下载 URL 有效期可控，过期后无法访问

---

## 8. 附录

### 8.1 业界方案参考

| 方案 | 代表产品 | 特点 |
|---|---|---|
| 对象存储 SDK 分片上传 | 华为云 OBS、阿里云 OSS、AWS S3 | SDK 自动分片/重试/合并，工业级可靠 |
| Tus 协议 | Cloudflare Stream、Vimeo | HTTP 标准扩展，跨语言支持 |
| 预签名 URL | AWS S3、阿里云 OSS | 前端直传存储，后端不中转 |
| WebSocket 上传 | Telegram | 双向通信，实时进度 |
| gRPC 流式 | Google Drive | 高性能二进制协议 |

### 8.2 参考链接

- [华为云 OBS SDK 概述](https://support.huaweicloud.com/sdkreference-obs/obs_02_0001.html)
- [华为云 BrowserJS SDK 开发指南](https://support.huaweicloud.com/sdk-browserjs-devg-obs/obs_24_0001.html)
- [华为云 Java SDK 开发指南](https://support.huaweicloud.com/sdk-java-devg-obs/obs_21_0001.html)
- [华为云 IAM 临时安全凭证](https://support.huaweicloud.com/usermanual-iam5/iam_01_1238.html)
- [tus.io - Resumable File Uploads](https://tus.io/)
- [Nginx - client_body_in_file_only](https://nginx.org/en/docs/http/ngx_http_core_module.html#client_body_in_file_only)
- [Spring Boot - Multipart Configuration](https://docs.spring.io/spring-boot/docs/current/reference/htmlsingle/#web.servlet.spring-mvc.multipart)
