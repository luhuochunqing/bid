# Data Model: 基于真实数据底座的历史案例精准推荐

## 1. 数据库变更

### `knowledge_case` 表结构变更

| 列名 | 类型 | 是否可空 | 默认值 | 说明 |
|---|---|---|---|---|
| `sub_topic` | `VARCHAR(50)` | YES | NULL | 业务子主题枚举（如 `SYS_ARCH`, `SLA_SERVICE`, `EMERGENCY_PLAN`） |
| `quality_score` | `INT` | NO | 60 | 案例文本充实度与质量评分（0~100） |

索引：
- `idx_knowledge_case_sub_topic` (`sub_topic`)

### Flyway 脚本说明
- 新增迁移版本：`V1211__add_sub_topic_to_knowledge_case.sql`
- 配对回滚脚本：`U1211__add_sub_topic_to_knowledge_case.sql`

```sql
-- V1211__add_sub_topic_to_knowledge_case.sql
ALTER TABLE knowledge_case
    ADD COLUMN sub_topic VARCHAR(50) NULL COMMENT '业务子主题（如 SLA_SERVICE, SYS_ARCH, LOGISTICS_WAREHOUSE）',
    ADD COLUMN quality_score INT NOT NULL DEFAULT 60 COMMENT '内容充实度与质量评分（0-100）',
    ADD INDEX idx_knowledge_case_sub_topic (sub_topic);
```

```sql
-- U1211__add_sub_topic_to_knowledge_case.sql
ALTER TABLE knowledge_case
    DROP INDEX idx_knowledge_case_sub_topic,
    DROP COLUMN sub_topic,
    DROP COLUMN quality_score;
```

---

## 2. 领域模型与枚举定义

### 业务子主题枚举 `CaseSubTopic`

```java
public enum CaseSubTopic {
    SYS_ARCH("系统架构", "系统总体架构、技术路线、技术选型与拓扑设计"),
    SLA_SERVICE("售后运维", "7x24小时售后服务、运维保障、SLA与故障恢复"),
    EMERGENCY_PLAN("应急保障", "应急预案、突发事件处理、备品备件储备"),
    INTEGRATION("系统集成", "接口规范、系统对接、数据迁移与ERP联通"),
    SECURITY_COMPLIANCE("安全合规", "网络安全、等保合规、数据加密与保密承诺"),
    DELIVERY_PROGRESS("项目交付", "实施周期、进度规划、里程碑与交付节点"),
    LOGISTICS_WAREHOUSE("仓储物流", "仓储布局、物流配送时效与供应链保障"),
    QUALITY_CONTROL("质量控制", "质量保证体系、检验检测与验收标准"),
    ORG_STAFF("组织团队", "项目组织架构、人员资质要求与岗位职责"),
    COMMERCIAL_TERMS("商务响应", "商务条款偏离响应、付款要求与交付承诺"),
    OTHER("其他方案", "通用概述或其他非专门方案");

    private final String title;
    private final String description;
}
```

---

## 3. 评分加权模型

满分 100 分，各维度严格收敛：

1. **大类互斥门禁（Hard Gate）**：
   - 若评分项为 `TECHNICAL`，而案例分类为 `COMMERCIAL`，得分强制置为 `0`；
2. **专属客户实体匹配（最高 25 分）**：
   - 命中当前项目专名（如“三峡”、“建设银行”）加 25 分；
3. **业务子主题精准匹配（最高 25 分）**：
   - 评分项推断子主题与案例 `sub_topic` 完全一致加 25 分；
4. **特征词 Jaccard 重叠度（最高 25 分）**：
   - 剔除 `STOP_WORDS`，基于并列实体匹配计算（0~25 分）；
5. **项目类型/行业一致性（最高 15 分）**：
   - 同行业/同大类加 15 分；
6. **案例质量加权（最高 10 分）**：
   - `quality_score * 0.1`（0~10 分）。
