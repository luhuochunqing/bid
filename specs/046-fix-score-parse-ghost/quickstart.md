# Quickstart: fix-score-parse-ghost 验证

## 前置

- 本地开发环境：主工作区 `npm run dev:all`（后端 18089，MySQL xiyu_bid_main）
- 测试类均可用 `mvn test -Dtest=<类>` 独立运行，不依赖环境

## 场景 1：幽灵项过滤（spec 场景 1，project 212 复现）

```bash
cd backend && mvn test -Dtest=GhostCandidateFilterTest
mvn test -Dtest=WeightOverloadTrimPolicyTest
```

- 断言：dim=detail="得分"（无 code 无 sourceText）被滤；有 code 或有 sourceText 的真实项零误删；超载剔除后合计回 100。

## 场景 2：编号排序落库（spec 场景 2）

```bash
cd backend && mvn test -Dtest=ScoreItemOrderPolicyTest
mvn test -Dtest=ScoreItemPersistenceServiceTest
```

- 断言：乱序候选（6,7,1,2,3,4-1,4-2,5）落库 itemIndex = 编号数值序；空 code 落 `""` 不再补行号。

## 场景 3：chunk 失败可见（spec 场景 3）

```bash
cd backend && mvn test -Dtest=OpenAiScoreAnalyzerTest
```

- 断言：单 chunk 首次失败触发重试 1 次；二次成功不计数；二次仍失败 skippedChunks+1 且不阻断。

## 场景 4：端到端集成（spec 场景 4）

```bash
cd backend && mvn test -Dtest=ScoreParseAppServiceTest
```

- 断言：AppService 编排链上过滤/剔除/落库 skippedChunks 均生效；真实项全保留。

## 全量回归

```bash
cd backend && mvn test -Dtest='ScoreParse*Test,Score*PolicyTest,WeightSumCheckTest,ItemCountCheckTest'
mvn test -Dtest=ArchitectureTest,FPJavaArchitectureTest,MaintainabilityArchitectureTest
```

## 测试环境真实验证（部署后）

1. 用 project 212 同源招标文件重新触发解析
2. `GET /api/projects/212/score-parse/items` 检查：
   - 无 dim="得分" 行；`summary.totalWeight`=100；itemIndex 按编号序
   - `meta.skippedChunks` 存在（0 或正数）
