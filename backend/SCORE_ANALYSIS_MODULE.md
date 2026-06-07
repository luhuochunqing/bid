# 评分分析模块 (ScoreAnalysis Module)

## 概述

评分分析模块提供项目综合评分分析功能，支持多维度评分、风险等级评估、历史趋势跟踪和项目对比分析。

## 技术栈

- Java 21
- Spring Boot 3.x
- Spring Data JPA
- Lombok
- JUnit 5 + Mockito
- H2 (测试数据库)

## 模块结构

```
com.xiyu.bid.scoreanalysis/
├── RiskLevel.java                           # 风险等级枚举
├── entity/
│   ├── ScoreAnalysis.java                  # 评分分析实体
│   └── DimensionScore.java                 # 维度分数实体
├── dto/
│   ├── ScoreAnalysisDTO.java               # 评分分析DTO
│   ├── DimensionScoreDTO.java              # 维度分数DTO
│   └── ScoreAnalysisCreateRequest.java     # 创建请求DTO
├── repository/
│   ├── ScoreAnalysisRepository.java        # 评分分析Repository
│   └── DimensionScoreRepository.java       # 维度分数Repository
├── service/
│   └── ScoreAnalysisService.java           # 评分分析服务
└── controller/
    └── ScoreAnalysisController.java        # 评分分析控制器
```

## 核心功能

### 1. 评分分析实体

#### ScoreAnalysis
- `id`: 主键
- `projectId`: 关联项目ID
- `analysisDate`: 分析日期
- `overallScore`: 综合评分 (0-100)
- `riskLevel`: 风险等级 (LOW/MEDIUM/HIGH)
- `analystId`: 分析人ID
- `isAiGenerated`: 是否AI生成
- `summary`: 分析总结

#### DimensionScore
- `id`: 主键
- `analysisId`: 关联分析ID
- `dimensionName`: 维度名称
- `score`: 维度分数 (0-100)
- `weight`: 权重
- `comments`: 评语

### 2. 标准评分维度

| 维度名称 | 说明 | 默认权重 |
|---------|------|---------|
| 技术能力 | 技术方案、实施能力 | 30% |
| 财务实力 | 财务状况、资金实力 | 25% |
| 团队经验 | 团队资质、项目经验 | 20% |
| 历史业绩 | 过往项目业绩 | 15% |
| 合规性 | 资质、证书、合规 | 10% |

### 3. 风险等级评估

| 综合评分 | 风险等级 |
|---------|---------|
| 80-100 | LOW (低风险) |
| 60-79 | MEDIUM (中等风险) |
| 0-59 | HIGH (高风险) |

## API 端点

### 1. 获取项目评分分析
```
GET /api/ai/score-analysis/project/{projectId}
```

**响应示例:**
```json
{
  "success": true,
  "code": 200,
  "data": {
    "id": 1,
    "projectId": 100,
    "analysisDate": "2026-03-04T16:00:00",
    "overallScore": 85,
    "riskLevel": "LOW",
    "analystId": 10,
    "isAiGenerated": true,
    "summary": "综合评估优秀",
    "dimensions": [
      {
        "id": 1,
        "dimensionName": "技术能力",
        "score": 90,
        "weight": 0.30,
        "comments": "技术团队经验丰富"
      }
    ]
  }
}
```

### 2. 获取项目历史分析
```
GET /api/ai/score-analysis/project/{projectId}/history
```

**响应示例:**
```json
{
  "success": true,
  "code": 200,
  "message": "历史分析记录",
  "data": [
    {
      "id": 2,
      "overallScore": 85,
      "analysisDate": "2026-03-04T16:00:00",
      "riskLevel": "LOW"
    },
    {
      "id": 1,
      "overallScore": 75,
      "analysisDate": "2026-02-20T10:00:00",
      "riskLevel": "MEDIUM"
    }
  ]
}
```

### 3. 创建评分分析
```
POST /api/ai/score-analysis
Content-Type: application/json
```

**请求体:**
```json
{
  "projectId": 100,
  "analystId": 10,
  "isAiGenerated": true,
  "summary": "综合评估优秀",
  "dimensions": [
    {
      "dimensionName": "技术能力",
      "score": 90,
      "weight": 0.30,
      "comments": "技术团队经验丰富"
    },
    {
      "dimensionName": "财务实力",
      "score": 85,
      "weight": 0.25,
      "comments": "财务状况良好"
    }
  ]
}
```

### 4. 比较两个项目的评分
```
GET /api/ai/score-analysis/compare/{id1}/{id2}
```

**响应示例:**
```json
{
  "success": true,
  "code": 200,
  "message": "项目比较结果",
  "data": [
    {
      "projectId": 100,
      "overallScore": 85,
      "riskLevel": "LOW"
    },
    {
      "projectId": 200,
      "overallScore": 72,
      "riskLevel": "MEDIUM"
    }
  ]
}
```

## 业务逻辑

### 1. 加权评分计算

综合分数采用加权平均算法：

```
总分 = Σ(维度分数 × 维度权重)
```

示例计算：
```
总分 = 90×0.30 + 85×0.25 + 80×0.20 + 88×0.15 + 95×0.10
     = 27 + 21.25 + 16 + 13.2 + 9.5
     = 86.95 → 87 (四舍五入)
```

### 2. 风险等级确定

根据综合评分自动确定风险等级：
- 分数 ≥ 80：LOW (低风险)
- 分数 60-79：MEDIUM (中等风险)
- 分数 < 60：HIGH (高风险)

## 测试覆盖

### 单元测试
- **ScoreAnalysisComprehensiveTest**: 综合测试套件
  - 实体创建和验证
  - 业务逻辑测试
  - 边界条件测试
  - 异常处理测试

### 测试统计
- **测试用例总数**: 13
- **通过率**: 100%
- **覆盖的测试场景**:
  - ✓ 实体创建 (Entity Creation)
  - ✓ 评分分析创建 (Create Analysis)
  - ✓ 加权分数计算 (Weighted Score Calculation)
  - ✓ 风险等级评估 (Risk Level Assessment)
  - ✓ 项目分析查询 (Get Analysis by Project)
  - ✓ 历史记录查询 (Get History)
  - ✓ 项目对比 (Compare Projects)
  - ✓ 空值处理 (Null/Empty Handling)
  - ✓ 边界值测试 (Boundary Values)

## 数据库设计

### score_analyses 表
```sql
CREATE TABLE score_analyses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    analysis_date TIMESTAMP,
    overall_score INT,
    risk_level VARCHAR(20),
    analyst_id BIGINT,
    is_ai_generated BOOLEAN,
    summary TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    INDEX idx_analysis_project (project_id),
    INDEX idx_analysis_date (analysis_date),
    INDEX idx_analysis_risk (risk_level)
);
```

### dimension_scores 表
```sql
CREATE TABLE dimension_scores (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    analysis_id BIGINT NOT NULL,
    dimension_name VARCHAR(100) NOT NULL,
    score INT,
    weight DECIMAL(10,8),
    comments TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    INDEX idx_dimension_analysis (analysis_id),
    INDEX idx_dimension_name (dimension_name)
);
```

## 使用示例

### Java Service 调用示例

```java
@Service
@RequiredArgsConstructor
public class ProjectEvaluationService {

    private final ScoreAnalysisService scoreAnalysisService;

    public void evaluateProject(Long projectId) {
        // 1. 创建评分分析
        List<DimensionScoreDTO> dimensions = Arrays.asList(
            DimensionScoreDTO.builder()
                .dimensionName("技术能力")
                .score(90)
                .weight(new BigDecimal("0.30"))
                .comments("技术团队经验丰富")
                .build(),
            // ... 其他维度
        );

        ScoreAnalysisCreateRequest request = ScoreAnalysisCreateRequest.builder()
            .projectId(projectId)
            .analystId(10L)
            .isAiGenerated(false)
            .summary("综合评估优秀")
            .dimensions(dimensions)
            .build();

        ApiResponse<ScoreAnalysisDTO> response =
            scoreAnalysisService.createAnalysis(request);

        if (response.isSuccess()) {
            ScoreAnalysisDTO analysis = response.getData();
            System.out.println("综合评分: " + analysis.getOverallScore());
            System.out.println("风险等级: " + analysis.getRiskLevel());
        }
    }

    public void compareProjects(Long projectId1, Long projectId2) {
        ApiResponse<List<ScoreAnalysisDTO>> response =
            scoreAnalysisService.compareProjects(projectId1, projectId2);

        if (response.isSuccess()) {
            List<ScoreAnalysisDTO> comparisons = response.getData();
            ScoreAnalysisDTO project1 = comparisons.get(0);
            ScoreAnalysisDTO project2 = comparisons.get(1);

            int scoreDiff = project1.getOverallScore() - project2.getOverallScore();
            System.out.println("评分差距: " + scoreDiff);
        }
    }
}
```

## 审计日志

所有创建操作都会通过 `@Auditable` 注解记录审计日志：

```java
@Auditable(
    action = "CREATE",
    entityType = "ScoreAnalysis",
    description = "创建评分分析"
)
public ApiResponse<ScoreAnalysisDTO> createAnalysis(...) {
    // 实现逻辑
}
```

## 未来扩展

### 计划功能
1. **AI自动评分**: 集成AI服务实现自动评分分析
2. **自定义维度**: 支持用户自定义评分维度和权重
3. **评分模板**: 提供行业评分模板
4. **趋势分析**: 增加评分趋势图表和预测
5. **批量评估**: 支持批量项目评分
6. **导出功能**: 支持导出评分报告（PDF/Excel）

### 性能优化
1. 添加Redis缓存常用查询
2. 实现分页查询
3. 优化历史数据查询

## 相关模块

- **合规检查模块** (Compliance): 合规性维度评分
- **资源管理模块** (Platform): 团队和案例数据支持
- **AI模块** (AI): AI辅助评分分析

## 版本历史

| 版本 | 日期 | 说明 |
|-----|------|-----|
| 1.0.0 | 2026-03-04 | 初始版本，支持基础评分分析功能 |

## 作者

TDD开发模式实现 - 测试先行

## 许可证

内部项目使用
