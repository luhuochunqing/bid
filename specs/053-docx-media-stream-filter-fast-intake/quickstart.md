# Quickstart: 验证 Docx 媒体前置过滤与超大标书解析

## 1. 运行本地单元测试

```bash
cd backend
mvn test -Dtest=DocxMediaFilterTest,SidecarStreamDocumentAdapterTest,ArchitectureTest,FPJavaArchitectureTest
```

## 2. 远端真实部署验证

```bash
# 1. 编译打包
mvn package -DskipTests

# 2. 部署到测试机 172.16.38.78
scp backend/target/bid-platform-1.0.3.jar jetty@172.16.38.78:/opt/xiyu-bid/shared/backend/app.jar.new
ssh jetty@172.16.38.78 "mv /opt/xiyu-bid/shared/backend/app.jar.new /opt/xiyu-bid/shared/backend/app.jar && sudo systemctl restart xiyu-bid-backend"

# 3. 触发项目 1351 核查
# 观察核查任务是否秒级启动并成功解析，状态流转至 COMPLETED
```
