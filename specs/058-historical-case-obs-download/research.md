# Research: 历史案例原文直接下载

## R1: 签发链路的实现位置

- **Decision**: 新增 `CaseDownloadUrlAppService`（casework.application），依赖 `KnowledgeCaseRepository` + `ObsDownloadUrlGateway` 端口；不扩展 `ObsShareUrlSigner`（其语义是 `obs-direct:{uploadId}` 伪协议，历史案例无 `bid_file` 记录）
- **Rationale**: 语义分离：伪协议面向系统上传文件，直签 objectKey 面向历史导入资产；两者失败语义不同
- **Alternatives considered**: ① 给 `bid_file` 补登记 8,144 条记录走伪协议——数据量大且语义扭曲，拒绝；② 前端直拼签名——密钥泄露，违反宪法，拒绝

## R2: 签发域名（标准域名 vs CNAME）

- **Decision**: 阶段 5 抽测时以标准虚拟主机域名（`ehsy-widbid.obs.cn-east-3.myhuaweicloud.com`）行为为准；`HuaweiObsDownloadUrlGateway` 的 CNAME 下载客户端若对中文 key 异常，端点降级走标准域名签发
- **Rationale**: 2026-09-01 实测通过的是标准域名；CNAME（`widbid-obs.ehsy.com`）未验证中文对象键
- **Alternatives considered**: 直接禁用 CNAME——影响系统现有下载链路，不做

## R3: OBS V2 签名中文对象键

- **Decision**: 由 SDK `createTemporarySignature` 处理（`HuaweiObsDownloadUrlGateway` 内部），不在应用层手拼签名
- **Rationale**: 手写签名实测确认 CanonicalizedResource 需 percent-encode，SDK 内部已处理；自拼签名属重复造轮子且易错
- **Alternatives considered**: 应用层手拼——已在验证阶段踩坑（SignatureDoesNotMatch），拒绝

## R4: 前端下载触发方式

- **Decision**: 取签名 URL 后用隐藏 `<a download>` 触发（同源跨域均可，OBS 响应带 Content-Disposition 时用服务侧文件名）；文件名取 objectKey basename
- **Rationale**: `window.open` 会新开标签且文件名不可控；a 标签方式与现有 `triggerBlobDownload` 一致
- **Alternatives considered**: fetch blob 中转——大文件内存压力，拒绝

## R5: 权限模型

- **Decision**: 与案例列表一致（登录可访问案例库即可签发），不做项目级校验
- **Rationale**: 历史案例无项目归属；案例库本就是全员可见素材库
- **Alternatives considered**: 加角色限制——无业务依据，增加复杂度，拒绝
