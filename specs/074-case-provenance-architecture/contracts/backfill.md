# 回填端口

findActiveCandidates(): List<Candidate>，严格ACTIVE，只读快照。
saveAll(List<Update>): void，按项目批量写五字段。所有ID必须存在；缺失抛IllegalStateException，整批回滚。其余持久化错误继续传播。
不引入HTTP接口；BackfillResult与命令行入口不变。
