一旦我所属的文件夹有所变化，请更新我。

# file

大文件 OBS 直传模块 — 通过华为云 OBS 实现浏览器直传大文件，支持分片上传、断点续传、进度显示。

## 文件清单

| 文件 | 功能 |
|------|------|
| `adapter/web/FileUploadController.java` | REST API 控制器（upload-token / completed / download-url） |
| `application/IssueUploadTokenUseCase.java` | 签发 OBS 临时凭证用例 |
| `application/CompleteUploadUseCase.java` | 上传完成通知用例 |
| `application/GetDownloadUrlUseCase.java` | 生成预签名下载 URL 用例 |
| `application/BidFileUploadedEvent.java` | 上传完成领域事件 |
| `application/BidFileUploadedEventHandler.java` | 上传完成事件处理器 |
| `domain/BidFileRepository.java` | 文件仓库接口 |
| `domain/BidFileStatus.java` | 文件状态枚举（UPLOADING/COMPLETED/FAILED） |
| `entity/BidFile.java` | JPA 实体 |
| `infrastructure/obs/ObsProperties.java` | OBS 配置绑定 |
| `infrastructure/obs/HuaweiObsTokenService.java` | IAM STS 临时凭证签发 |
| `infrastructure/obs/ObsMetadataService.java` | OBS 对象元数据查询 |
| `infrastructure/persistence/BidFileJpaRepository.java` | Spring Data JPA 仓库 |
| `dto/UploadTokenRequest.java` | 申请凭证请求 DTO |
| `dto/UploadTokenResponse.java` | 申请凭证响应 DTO |
| `dto/UploadCompletedRequest.java` | 完成通知请求 DTO |
| `dto/DownloadUrlResponse.java` | 下载 URL 响应 DTO |
