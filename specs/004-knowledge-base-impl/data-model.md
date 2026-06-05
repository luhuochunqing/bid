# Data Model: 知识库模块数据模型

## 1. 实体定义与数据库关系

### 1.1 ProjectArchive (项目档案)
*   **物理表名**：`project_archive`
*   **定位**：项目档案只读索引，与项目主体一对一绑定。
*   **字段定义**：
    | 字段名 | 类型 | 约束 | 说明 |
    |--------|------|------|------|
    | `id` | `BIGINT` | Primary Key, Auto Increment | 主键 ID |
    | `project_id` | `BIGINT` | Unique, Not Null | 关联的项目 ID |
    | `project_name` | `VARCHAR(200)` | Not Null | 项目名称 |
    | `archive_status` | `VARCHAR(50)` | Not Null | 归档状态 (ACTIVE, CLOSED) |
    | `created_at` | `TIMESTAMP` | Not Null | 创建时间 |
    | `updated_at` | `TIMESTAMP` | Not Null | 最后更新时间 |

### 1.2 ArchiveFile (归档文件)
*   **物理表名**：`archive_file`
*   **定位**：归档的文件清单。
*   **字段定义**：
    | 字段名 | 类型 | 约束 | 说明 |
    |--------|------|------|------|
    | `id` | `BIGINT` | Primary Key, Auto Increment | 主键 ID |
    | `archive_id` | `BIGINT` | Foreign Key (`project_archive.id`) | 所属的项目档案 ID |
    | `file_name` | `VARCHAR(255)` | Not Null | 原始文件名 |
    | `document_category` | `VARCHAR(50)` | Not Null | 文档分类枚举 (TENDER, BID, CONTRACT, PROCESS, RETROSPECTIVE, OTHER) |
    | `file_path` | `VARCHAR(500)` | Not Null | 物理存储相对路径 |
    | `file_size` | `BIGINT` | Not Null | 文件大小 (Bytes) |
    | `upload_user_id` | `BIGINT` | Not Null | 上传人用户 ID |
    | `upload_user_name` | `VARCHAR(100)` | Not Null | 上传人姓名 |
    | `created_at` | `TIMESTAMP` | Not Null | 上传时间 |

### 1.3 ArchiveLog (档案安全审计日志)
*   **物理表名**：`archive_log`
*   **定位**：操作审计日志。
*   **字段定义**：
    | 字段名 | 类型 | 约束 | 说明 |
    |--------|------|------|------|
    | `id` | `BIGINT` | Primary Key, Auto Increment | 主键 ID |
    | `archive_id` | `BIGINT` | Not Null | 关联的档案 ID |
    | `operator_id` | `BIGINT` | Not Null | 操作人 ID |
    | `operator_name` | `VARCHAR(100)` | Not Null | 操作人姓名 |
    | `action_type` | `VARCHAR(50)` | Not Null | 操作类型 (PREVIEW, DOWNLOAD, EXPORT) |
    | `action_content` | `VARCHAR(500)` | Not Null | 具体操作内容描述 |
    | `created_at` | `TIMESTAMP` | Not Null | 操作时间 |

### 1.4 KnowledgeCase (案例库案例)
*   **物理表名**：`knowledge_case`
*   **定位**：AI 沉淀的标书问答案例。
*   **字段定义**：
    | 字段名 | 类型 | 约束 | 说明 |
    |--------|------|------|------|
    | `id` | `BIGINT` | Primary Key, Auto Increment | 主键 ID |
    | `source_project_id` | `BIGINT` | Not Null | 来源项目 ID |
    | `source_project_name` | `VARCHAR(200)` | Not Null | 来源项目名称 |
    | `scoring_point_title` | `VARCHAR(200)` | Not Null | 评分项简短标题 (AI 提炼) |
    | `requirement_raw` | `TEXT` | Not Null | 招标评分要求原文 |
    | `response_text` | `TEXT` | Not Null | 标书应答片段原文 |
    | `reuse_count` | `INT` | Not Null, Default 0 | 复用次数 |
    | `status` | `VARCHAR(50)` | Not Null | 状态 (ACTIVE, OFF_SHELF) |
    | `customer_type` | `VARCHAR(50)` | | 客户类型 (国企/央企/KA) |
    | `project_type` | `VARCHAR(50)` | | 项目类型 (工业电商/综合/集采等) |
    | `created_at` | `TIMESTAMP` | Not Null | 创建时间 |

### 1.5 QualificationCertificate (资质证书修改扩展)
*   **物理表名**：扩展已有 `qualification_certificate` 表或映射实体，确保有以下字段。
*   **核心字段**：
    | 字段名 | 类型 | 约束 | 说明 |
    |--------|------|------|------|
    | `id` | `BIGINT` | Primary Key | 主键 ID |
    | `name` | `VARCHAR(200)` | Not Null | 证书名称 |
    | `level` | `VARCHAR(50)` | Not Null | 等级 |
    | `cert_no` | `VARCHAR(120)` | Unique, Not Null | 证书编号 |
    | `auth_agency` | `VARCHAR(200)` | Not Null | 发证认证机构 |
    | `issue_date` | `DATE` | Not Null | 发证日期 |
    | `expiry_date` | `DATE` | Not Null | 证书有效期 |
    | `agent_company` | `VARCHAR(200)` | | 代理机构 |
    | `agent_contact` | `VARCHAR(100)` | | 代理人联系方式 |
    | `auth_scope` | `VARCHAR(1000)` | | 认证范围 |
    | `status` | `VARCHAR(50)` | Not Null | 状态 (IN_STOCK, OFF_SHELF) |
    | `attachment_path` | `VARCHAR(500)` | Not Null | 附件物理路径 |

---

## 2. 状态机与转换流

### 2.1 案例沉淀任务状态机 (AI 异步提取)
*   `PENDING` -> (后台拉取文件启动) -> `PROCESSING`
*   `PROCESSING` -> (处理成功并录入案例库) -> `COMPLETED`
*   `PROCESSING` -> (侧车崩溃/提取失败) -> `FAILED`

### 2.2 案例上下架状态
*   `ACTIVE` -> (管理员操作下架) -> `OFF_SHELF` (软删除，前台隐藏)

---

## 3. 核心业务校验逻辑与 Pure Core 表达

为了遵循 **FP-Java Profile**，我们将设计无副作用的领域校验层：
*   **`QualificationValidationPolicy` (资质有效性规则)**：
    *   `boolean isExpiryDateValid(LocalDate issue, LocalDate expiry)` -> 返回 `expiry.isAfter(issue)`
    *   `boolean isAboutToExpire(LocalDate expiry, LocalDate current, int remindDays)` -> 返回 `current.plusDays(remindDays).isAfter(expiry)`
*   **`ArchiveLogFactory` (操作日志模型生成)**：
    *   `ArchiveLog createDownloadLog(ProjectArchive archive, Long userId, String userName, String fileName, LocalDateTime time)` -> 构造并返回包含格式化操作内容的只读 Record。
*   **所有 Domain 逻辑全部声明为 `final` 方法且严禁包含 `void`，保证所有计算必须有返回值且无隐式副作用。**
