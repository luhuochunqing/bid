> 一旦我所属的文件夹有所变化，请更新我。

# Casework 模块

案例模块负责案例正文、搜索、相关推荐、引用、分享以及从项目快照晋升案例的能力。
这里采用最小可行的 application / domain / infrastructure 分层：controller 只做协议适配，application 负责编排，domain 承载搜索与推荐的规则，infrastructure 负责实体映射和快照客户端。

| 文件 | 地位 | 功能 |
|------|------|------|
| `application/` | 子目录 | 案例用例编排层 |
| `domain/` | 子目录 | 案例搜索与推荐领域对象 |
| `infrastructure/` | 子目录 | 案例持久化与外部快照适配 |
| `entity/` | 子目录 | 案例记录实体边界 |
| `entity/CaseReferenceRecord.java` | Entity | 案例引用记录实体 |
| `entity/CaseShareRecord.java` | Entity | 案例分享记录实体 |
| `infrastructure/persistence/CaseMapper.java` | Mapper | 案例实体与 DTO 映射 |
| `application/service/CaseCrudAppService.java` | AppService | 案例增删改查编排 |
| `application/service/CaseSearchAppService.java` | AppService | 案例搜索、搜索选项与相关推荐 |
| `application/service/CasePromotionAppService.java` | AppService | 从项目快照晋升案例 |
| `application/service/CaseShareAppService.java` | AppService | 案例分享记录 |
| `application/service/CaseReferenceAppService.java` | AppService | 案例引用记录 |
| `application/service/CaseProvenanceBackfillAppService.java` | AppService | 存量案例来源章节重算（幂等；判据为「现有来源标题不是已验证章节」；定位成功写回真实章节，失败清空） |
| `application/MarkdownHeadingParser.java` | Domain Util | Markdown 标题解析与章节扫描（构建层级树与祖先路径） |
| `application/MarkdownTitleRules.java` | Domain Util | 标题分类契约（`isSentenceLikeTitle` 句子门 + `isChapterTitle` 章节号契约——编号在串首**且编号后有标题文字**，字母列表标记不算章节号，前后端同规则，由共享夹具双向锚定） |
| `application/MarkdownSourceSectionIndex.java` | Domain Util | Markdown 来源章节索引与定位器（唯一命中且命中章节为已验证章节才写来源字段，其余一律返回 null） |
| `repository/` | 子目录 | 案例记录数据访问边界 |
| `repository/CaseReferenceRecordRepository.java` | Repository | 案例引用记录访问 |
| `repository/CaseShareRecordRepository.java` | Repository | 案例分享记录访问 |
| `dto/` | 子目录 | 案例记录传输边界 |
| `dto/CaseReferenceRecordDTO.java` | DTO | 案例引用记录传输对象 |
| `dto/CaseShareRecordDTO.java` | DTO | 案例分享记录传输对象 |
| `dto/CaseSearchOptionsDTO.java` | DTO | 案例搜索选项传输对象 |
| `dto/CaseRecommendationDTO.java` | DTO | 案例相关推荐传输对象 |
| `dto/CasePromoteFromProjectRequest.java` | DTO | 从项目快照晋升案例请求 |
