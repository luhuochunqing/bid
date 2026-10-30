# Maintenance 启动根

> 一旦我所属的文件夹有所变化，请更新我。

该包放置与正常服务启动根隔离的运维入口。`CaseProvenancePreviewApplication` 仅用于案例来源只读预演：
关闭 Web、Flyway 与调度，排除其他 Runner，并强制 Hikari 连接只读；正常服务仍由
`com.xiyu.bid.XiyuBidApplication` 启动。
