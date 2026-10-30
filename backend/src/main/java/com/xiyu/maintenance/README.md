# Maintenance 启动根

> 一旦我所属的文件夹有所变化，请更新我。

该包放置与正常服务启动根隔离的运维入口。`CaseProvenancePreviewApplication` 用于案例来源预演与受守卫回填：
两种模式都关闭 Web、Flyway 与调度并排除其他 Runner；`preview` 强制 Hikari 连接只读，`apply` 仅开放必要写入能力，仍由完整回执和数据库条件更新双重守卫。正常服务仍由
`com.xiyu.bid.XiyuBidApplication` 启动。
