# API Contract: 历史项目批量导入接口

**Feature**: 041-historical-project-import
**Date**: 2026-08-06

## 接口清单

### 1. 批量导入历史项目

**POST** `/api/projects/import/historical`

**权限**: `@PreAuthorize("hasAuthority('project:import')")`

**Content-Type**: `multipart/form-data`

**请求参数**:

| 参数 | 类型 | 必填 | 说明 |
|---|---|---|---|
| file | File | ✅ | Excel 采集表（.xlsx），19 列，符合采集表模板 |

**响应**（200 OK）:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "totalRows": 100,
    "successCount": 95,
    "failureCount": 3,
    "skippedCount": 2,
    "failures": [
      {
        "rowNumber": 15,
        "projectName": "XX市公安局办公设备采购项目",
        "reason": "投标负责人工号 999999 未找到对应用户",
        "field": "投标负责人"
      },
      {
        "rowNumber": 42,
        "projectName": "",
        "reason": "必填字段缺失：项目名称",
        "field": "项目名称"
      },
      {
        "rowNumber": 78,
        "projectName": "YY项目",
        "reason": "状态值「投标中」不在允许范围（仅支持已中标/未中标/已流标/已放弃）",
        "field": "项目状态"
      }
    ],
    "skipped": [
      {
        "rowNumber": 5,
        "projectName": "已存在的项目",
        "reason": "项目已存在（external_id 匹配），跳过"
      }
    ]
  }
}
```

**错误响应**:

| HTTP Status | 场景 | 响应 |
|---|---|---|
| 400 | 文件为空或非 .xlsx 格式 | `{"code": 400, "message": "请上传 .xlsx 格式的采集表"}` |
| 400 | 行数超过 500 上限 | `{"code": 400, "message": "单次导入上限 500 行，当前 520 行，请分批上传"}` |
| 403 | 无 project:import 权限 | `{"code": 403, "message": "无导入权限"}` |
| 500 | 服务器内部错误 | `{"code": 500, "message": "导入过程中发生异常，请联系管理员"}` |

---

### 2. 下载采集表模板

**GET** `/api/projects/import/historical/template`

**权限**: `@PreAuthorize("hasAuthority('project:import')")`

**响应**: Excel 文件下载（.xlsx），包含 19 列表头 + 1 行示例数据 + 数据验证下拉（状态/优先级/客户类型）

**Content-Type**: `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`
**Content-Disposition**: `attachment; filename="历史投标项目信息采集表.xlsx"`

---

## Excel 采集表格式

### 列定义

| 列 | 字段名 | 必填 | 格式 | 说明 |
|---|---|---|---|---|
| A | 项目名称 | ✅ | 文本 | 最长 500 字符 |
| B | 招标主体 | ✅ | 文本 | 最长 255 字符 |
| C | 来源平台 | ✅ | 文本 | 最长 255 字符 |
| D | 项目状态 | ✅ | 下拉 | 已中标/未中标/已流标/已放弃 |
| E | 总部所在地 | ✅ | 文本 | 最长 100 字符 |
| F | 客户类型 | ✅ | 下拉 | 政府机关/事业单位/高校/央企/地方国企/民企/港澳台及外企 |
| G | 项目类型 | ✅ | 下拉 | 办公/综合/集采/工业品/其他 |
| H | 优先级 | ✅ | 下拉 | S级/A级/B级/C级 |
| I | 客户营收(亿) | ❌ | 数字 | 单位为亿，如 10.5 表示 10.5 亿 |
| J | 计划入围供应商数 | ❌ | 整数 | |
| K | 开标时间 | ✅ | 日期 | yyyy-MM-dd 或 yyyy-MM-dd HH:mm |
| L | 投标月份 | ❌ | 文本 | yyyy-MM，可从开标时间推导 |
| M | 联系人1姓名 | ❌ | 文本 | 最长 100 字符 |
| N | 联系人1电话 | ❌ | 文本 | 最长 50 字符 |
| O | 项目负责人 | ✅ | 文本 | 格式"姓名（工号）"，如"李四（123456）" |
| P | 投标负责人 | ✅ | 文本 | 格式同上 |
| Q | 项目服务周期(年) | ✅ | 数字 | 保留 1 位小数，如 3.5 |
| R | 项目服务周期截止时间 | ✅ | 日期 | yyyy-MM-dd |
| S | 项目阶段 | ✅ | 文本 | 历史项目固定"项目结项" |

### 数据验证（下拉选项）

- D 列（项目状态）：已中标、未中标、已流标、已放弃
- F 列（客户类型）：政府机关、事业单位、高校、央企、地方国企、民企、港澳台及外企
- G 列（项目类型）：办公、综合、集采、工业品、其他
- H 列（优先级）：S级、A级、B级、C级

---

## 幂等性

同一份采集表重复导入时，系统按 `external_id = HISTORICAL_{MD5(项目名称+招标主体)}` 去重。已存在的项目跳过，记入 `skipped` 清单，不创建重复记录。

---

## 性能约束

- 单次导入上限 500 行（FR-012）
- 500 行导入耗时 <5 分钟（SC-001 留 5x 余量）
- 同步导入，无需异步任务
- 每行独立事务，失败行不回滚已成功行
