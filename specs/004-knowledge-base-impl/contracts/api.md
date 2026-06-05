# API Contract: 知识库模块接口定义

## 1. 项目档案 (Project Archive)

### 1.1 项目档案台账列表分页查询
*   **Method**: `GET`
*   **Path**: `/api/archive`
*   **Query Parameters**:
    *   `projectName` (String, optional) - 项目名称模糊匹配
    *   `documentCategories` (List<String>, optional) - 文档分类枚举过滤
    *   `projectStatus` (List<String>, optional) - 项目状态过滤
    *   `projectType` (List<String>, optional) - 工业电商/综合/集采等
    *   `uploadTimeStart` (String, optional) - 上传日期区间起 (YYYY-MM-DD)
    *   `uploadTimeEnd` (String, optional) - 上传日期区间止 (YYYY-MM-DD)
    *   `closeTimeStart` (String, optional) - 结项日期区间起 (YYYY-MM-DD)
    *   `closeTimeEnd` (String, optional) - 结项日期区间止 (YYYY-MM-DD)
    *   `page` (int, default: 0)
    *   `size` (int, default: 10)
*   **Response (200 OK)**:
    ```json
    {
      "content": [
        {
          "archiveId": 12,
          "projectName": "西域数智化投标系统一期",
          "projectType": "综合",
          "projectStatus": "CLOSED",
          "bidResult": "AWARDED",
          "fileCount": 156,
          "fileCategoryDetails": {
            "TENDER": 12,
            "BID": 45,
            "CONTRACT": 5,
            "PROCESS": 80,
            "RETROSPECTIVE": 1,
            "OTHER": 13
          },
          "lastUploadedAt": "2026-05-18T14:07:56Z",
          "projectManager": "张三",
          "bidManager": "李四"
        }
      ],
      "totalElements": 1
    }
    ```

### 1.2 查看项目档案只读详情
*   **Method**: `GET`
*   **Path**: `/api/archive/{id}`
*   **Response (200 OK)**:
    ```json
    {
      "archiveId": 12,
      "projectName": "西域数智化投标系统一期",
      "projectType": "综合",
      "projectStatus": "CLOSED",
      "files": [
        {
          "fileId": 101,
          "fileName": "投标响应书-技术部分.docx",
          "category": "BID",
          "uploadUser": "王五",
          "uploadedAt": "2026-05-17T12:00:00Z",
          "fileSize": 2516582
        }
      ],
      "logs": [
        {
          "logId": 998,
          "time": "2026-05-17T14:07:56Z",
          "operator": "张三",
          "actionType": "DOWNLOAD",
          "content": "下载了“投标响应书-技术部分.docx”"
        }
      ]
    }
    ```

### 1.3 📊 导出台账 Excel
*   **Method**: `POST`
*   **Path**: `/api/archive/export-excel`
*   **Request Body**: 同查询过滤条件
*   **Response (200 OK)**: 二进制 Excel 文件流
*   **Headers**:
    *   `Content-Disposition`: `attachment; filename=方案管理-项目档案台账-202605201430.xlsx`

### 1.4 📦 导出文件包 ZIP
*   **Method**: `POST`
*   **Path**: `/api/archive/export-zip`
*   **Request Body**: 同查询过滤条件
*   **Response (200 OK)**: 二进制 ZIP 文件流
*   **Headers**:
    *   `Content-Disposition`: `attachment; filename=方案管理-项目档案文件包-202605201430.zip`

---

## 2. 案例库 (Knowledge Case)

### 2.1 案例平铺卡片网格分页查询
*   **Method**: `GET`
*   **Path**: `/api/cases`
*   **Query Parameters**:
    *   `keyword` (String, optional) - 关键词检索
    *   `scoringCategory` (String, optional) - 评分项分类
    *   `customerType` (String, optional) - 客户类型
    *   `projectType` (String, optional) - 项目类型
    *   `sortBy` (String, default: "created") - "created" (最新) 或 "reuse" (最热)
*   **Response (200 OK)**:
    ```json
    {
      "content": [
        {
          "caseId": 45,
          "scoringTitle": "技术实力响应",
          "responseTextSummary": "我司深耕工业电商系统多年，技术团队规模达200人，曾交付了...",
          "projectType": "综合",
          "customerType": "国有企业",
          "reuseCount": 18,
          "createdAt": "2026-05-19"
        }
      ],
      "totalElements": 1
    }
    ```

### 2.2 案例一键复用计数
*   **Method**: `POST`
*   **Path**: `/api/cases/{id}/reuse`
*   **Response (200 OK)**:
    ```json
    {
      "caseId": 45,
      "newReuseCount": 19
    }
    ```

### 2.3 案例下架
*   **Method**: `POST`
*   **Path**: `/api/cases/{id}/off-shelf`
*   **Response (200 OK)**:
    ```json
    {
      "caseId": 45,
      "status": "OFF_SHELF"
    }
    ```

---

## 3. 资质证书借阅鉴权 (Qualification Expiration & Borrow)

### 3.1 资质证书附件预览/下载权限校验
*   **Method**: `GET`
*   **Path**: `/api/qualification/{id}/check-borrow`
*   **Query Parameters**:
    *   `projectId` (BIGINT) - 当前关联的投标项目 ID
*   **Response (200 OK)**:
    ```json
    {
      "allowed": true,
      "reason": "已关联通过审批的资质借阅流程",
      "borrowRecordId": 1209
    }
    ```
*   **Response (403 Forbidden)**:
    ```json
    {
      "allowed": false,
      "reason": "未绑定已审批通过的借阅流程，请先提交借阅审批"
    }
    ```
