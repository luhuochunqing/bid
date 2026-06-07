# 竞争情报模块快速参考

## API 端点速查

| 方法 | 路径 | 权限 | 描述 |
|------|------|------|------|
| GET | /api/ai/competition/competitors | ADMIN, MANAGER, STAFF | 获取所有竞争对手 |
| POST | /api/ai/competition/competitors | ADMIN, MANAGER | 创建竞争对手 |
| GET | /api/ai/competition/project/{projectId} | ADMIN, MANAGER, STAFF | 获取项目竞争分析 |
| POST | /api/ai/competition/project/{projectId}/analyze | ADMIN, MANAGER | 分析项目竞争 |
| POST | /api/ai/competition/analysis | ADMIN, MANAGER | 创建竞争分析 |
| GET | /api/ai/competition/competitor/{id}/history | ADMIN, MANAGER, STAFF | 获取历史表现 |

## 请求示例

### 创建竞争对手
```bash
curl -X POST http://localhost:8080/api/ai/competition/competitors \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{
    "name": "竞企A",
    "industry": "建筑业",
    "strengths": "资质齐全，技术实力强",
    "weaknesses": "报价偏高",
    "marketShare": 25.5,
    "typicalBidRangeMin": 1000000,
    "typicalBidRangeMax": 1500000
  }'
```

### 创建竞争分析
```bash
curl -X POST http://localhost:8080/api/ai/competition/analysis \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {token}" \
  -d '{
    "projectId": 100,
    "competitorId": 1,
    "winProbability": 70.0,
    "competitiveAdvantage": "技术领先",
    "recommendedStrategy": "强调创新",
    "riskFactors": "价格竞争"
  }'
```

### 分析项目竞争
```bash
curl -X POST http://localhost:8080/api/ai/competition/project/100/analyze \
  -H "Authorization: Bearer {token}"
```

## 数据验证规则

### 竞争对手
- name: 必填，1-200字符
- industry: 可选，最多100字符
- marketShare: 可选，0-100
- typicalBidRangeMin: 可选，>=0
- typicalBidRangeMax: 可选，>=0且>=min

### 竞争分析
- projectId: 必填
- competitorId: 可选
- winProbability: 可选，0-100
- 所有文本字段：最多5000字符

## 服务方法速查

```java
// 竞争对手管理
competitionIntelService.createCompetitor(request)
competitionIntelService.getAllCompetitors()

// 竞争分析
competitionIntelService.createAnalysis(request)
competitionIntelService.getAnalysisByProject(projectId)
competitionIntelService.analyzeCompetition(projectId)

// 历史数据
competitionIntelService.getHistoricalPerformance(competitorId)
```

## 测试文件

| 测试类型 | 文件路径 | 测试数量 |
|---------|---------|---------|
| 实体测试 | entity/CompetitorTest.java | 8 |
| 实体测试 | entity/CompetitionAnalysisTest.java | 7 |
| 服务测试 | CompetitionIntelServiceTest.java | 25+ |
| 集成测试 | integration/CompetitionIntelControllerIntegrationTest.java | 15+ |

## 数据库表

### competitors
```sql
CREATE TABLE competitors (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    industry VARCHAR(100),
    strengths TEXT,
    weaknesses TEXT,
    market_share DECIMAL(5,2),
    typical_bid_range_min DECIMAL(19,2),
    typical_bid_range_max DECIMAL(19,2),
    created_at TIMESTAMP NOT NULL,
    INDEX idx_competitor_name (name),
    INDEX idx_competitor_industry (industry)
);
```

### competition_analyses
```sql
CREATE TABLE competition_analyses (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    competitor_id BIGINT,
    analysis_date TIMESTAMP,
    win_probability DECIMAL(5,2),
    competitive_advantage TEXT,
    recommended_strategy TEXT,
    risk_factors TEXT,
    INDEX idx_analysis_project (project_id),
    INDEX idx_analysis_competitor (competitor_id),
    INDEX idx_analysis_date (analysis_date),
    INDEX idx_analysis_project_competitor (project_id, competitor_id)
);
```

## 常见用例

### 1. 添加新竞争对手并分析
```java
// 创建竞争对手
CompetitorCreateRequest compRequest = CompetitorCreateRequest.builder()
    .name("新竞企")
    .industry("建筑业")
    .marketShare(new BigDecimal("15.0"))
    .build();
CompetitorDTO competitor = service.createCompetitor(compRequest);

// 为项目创建分析
AnalysisCreateRequest analysisRequest = AnalysisCreateRequest.builder()
    .projectId(100L)
    .competitorId(competitor.getId())
    .winProbability(new BigDecimal("60.0"))
    .build();
CompetitionAnalysisDTO analysis = service.createAnalysis(analysisRequest);
```

### 2. 查看竞争对手历史表现
```java
List<CompetitionAnalysisDTO> history =
    service.getHistoricalPerformance(competitorId);
```

### 3. 获取项目的所有竞争分析
```java
List<CompetitionAnalysisDTO> analyses =
    service.getAnalysisByProject(projectId);
```

## 注意事项

1. 所有金额字段使用BigDecimal类型
2. 市场份额和胜率都是0-100的百分比值
3. 投标范围的最小值不能大于最大值
4. 审计日志会自动记录创建和分析操作
5. 权限控制基于角色（ADMIN, MANAGER, STAFF）
