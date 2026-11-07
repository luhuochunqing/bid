# 材料角色与定位来源契约 R2

用户于本轮明确“继续修复”，沿用既有工作树，交付上限working_tree。原三轮失败记录保持；本周期至多三轮独立审查，不能以本文件掩盖旧失败。

## 规格
- R2-AC01：须提供服务协议且对应字段留白，规则及真实Engine出口均不允许模型PASS；协议类别不能覆盖本次应答义务。
- R2-AC02：明确只要求参考模板/样例时，其用途不得被提升为实际签署应答；与实际应答义务冲突时人工，不假通过也不武断FAIL。
- R2-AC03：有全文但pages=[]时，仍分析明确留白/违规，结论不因无分页丢失；所有该路径证据pageNumber=null，摘录必须来自实际输入。
- R2-AC04：实际提供的解析页号与原位摘录保留；相同占位符不重新全文定位。评分及资格两条路径均覆盖。
- R2-AC05：既有1101项相关行为及前端保持；不改变接口、库表或上线数据。未完成原件/视觉/线上验收仍明确保留。

## 实现计划
1. 新增业务失败测试，使用真实BidDocumentQualityEngine以及可控LLM端口输出，不新增Mock业务路径。
2. ScoringResponsePolicy把需求角色显式表达为实际应答、参考样例、冲突及未知。匹配已存在的评分名称/明确指令对象，不扩大泛词白名单；明确角色决定后才看材料类别。
3. 新增纯值EvidenceTextSlice(Integer pageNumber,String text)统一分析与证据来源。provided pages保留原页号；纯全文为null，不造页号。只给评分和资格两个真实调用者使用，不创建通用框架。
4. 资格占位和违规证据直接用同一切片与命中区间。证书参考检索仍使用原文档，不改变材料匹配语义。
5. Mode B删除重复兜底，关联/三架构验证、独立全diff审查。无代码提交与外部写入。

## 验证命令
mvn test -Dtest=Quality1360RoleAndLocationTest；mvn test -Dtest=com.xiyu.bid.compliance.**,FPJavaArchitectureTest,ArchitectureTest,MaintainabilityArchitectureTest；node scripts/check-line-budgets.mjs；git diff --check。

## 风险边界
输入pages可能由上游虚拟分块产生，本周期不添加全链路物理页来源API；仅不再把本策略内部合成编号当成来源。参考样例与实际应答发生冲突时保留人工，不能从“提供模板”推断“模板字段已填写”。
