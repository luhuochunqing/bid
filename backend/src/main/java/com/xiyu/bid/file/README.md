一旦我所属的文件夹有所变化，请更新我。

# file

大文件 OBS 直传模块 — 通过华为云 OBS 实现浏览器直传大文件，支持分片上传、断点续传、进度显示。

## 文件清单

| 文件 | 功能 |
|------|------|
| `adapter/web/FileUploadController.java` | REST API 控制器（upload-token / completed / download-url） |
| `application/IssueUploadTokenUseCase.java` | 签发 OBS 临时凭证用例（编排层，依赖 ObsTokenGateway + UploadPolicy） |
| `application/CompleteUploadUseCase.java` | 上传完成通知用例（编排层，依赖 ObsMetadataGateway + UploadCompletionPolicy） |
| `application/GetDownloadUrlUseCase.java` | 生成预签名下载 URL 用例（编排层，依赖 ObsDownloadUrlGateway + DownloadPolicy） |
| `application/BidFileUploadedEvent.java` | 上传完成领域事件 |
| `application/BidFileUploadedEventHandler.java` | 上传完成事件处理器 |
| `domain/BidFileRepository.java` | 文件仓库接口 |
| `domain/BidFileStatus.java` | 文件状态枚举（UPLOADING/COMPLETED/FAILED，含状态机方法） |
| `domain/ValidationResult.java` | 校验结果值对象（record，success/failure 工厂方法） |
| `domain/UploadPolicy.java` | 上传凭证生成策略（纯核心：generateUploadId / buildObjectKey） |
| `domain/UploadCompletionPolicy.java` | 上传完成校验策略（纯核心：归属/状态/大小/ETag 校验） |
| `domain/DownloadPolicy.java` | 下载策略（纯核心：校验 + clampExpireSeconds [60,3600]） |
| `domain/gateway/ObsTokenGateway.java` | OBS 临时凭证签发端口（Hexagonal 入站端口） |
| `domain/gateway/ObsMetadataGateway.java` | OBS 对象元数据查询端口 |
| `domain/gateway/ObsDownloadUrlGateway.java` | OBS 预签名下载 URL 签发端口 |
| `domain/model/TemporaryCredentials.java` | 临时凭证值对象（record） |
| `domain/model/SignedDownloadUrl.java` | 预签名下载 URL 值对象（record） |
| `entity/BidFile.java` | JPA 实体 |
| `config/FilePolicyConfig.java` | 纯核心 Policy Bean 注册（@Configuration + @Bean 模式 A） |
| `infrastructure/obs/ObsProperties.java` | OBS 配置绑定 |
| `infrastructure/obs/HuaweiObsTokenService.java` | IAM STS 临时凭证签发（implements ObsTokenGateway） |
| `infrastructure/obs/HuaweiObsMetadataGateway.java` | OBS 对象元数据查询（implements ObsMetadataGateway） |
| `infrastructure/obs/HuaweiObsDownloadUrlGateway.java` | OBS 预签名下载 URL 签发（implements ObsDownloadUrlGateway） |
| `infrastructure/persistence/BidFileJpaRepository.java` | Spring Data JPA 仓库 |
| `dto/UploadTokenRequest.java` | 申请凭证请求 DTO（record） |
| `dto/UploadTokenResponse.java` | 申请凭证响应 DTO（record） |
| `dto/UploadCompletedRequest.java` | 完成通知请求 DTO（record） |
| `dto/DownloadUrlResponse.java` | 下载 URL 响应 DTO（record） |
