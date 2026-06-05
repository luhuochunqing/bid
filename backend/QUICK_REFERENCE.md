# XiYu Bid POC Backend - 快速参考

## 常用命令

```bash
# 启动应用 (H2内存数据库)
./start.sh
# 或
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 运行所有测试
mvn test

# 运行特定测试
mvn test -Dtest=JwtUtilTest
mvn test -Dtest=AuthIntegrationTest

# 清理并重新构建
mvn clean install

# 生成测试报告
mvn test jacoco:report
```

## API端点

### 认证相关

```bash
# 注册
POST /api/auth/register
Content-Type: application/json

{
  "username": "testuser",
  "password": "password123",
  "email": "test@example.com",
  "fullName": "Test User"
}

# 登录
POST /api/auth/login
Content-Type: application/json

{
  "username": "testuser",
  "password": "password123"
}
```

### 受保护端点

```bash
# 获取用户信息
GET /api/user/profile
Authorization: Bearer <token>

# 管理员仪表板
GET /api/admin/dashboard
Authorization: Bearer <token>

# 经理仪表板
GET /api/manager/dashboard
Authorization: Bearer <token>
```

### 公开端点

```bash
# 健康检查
GET /api/public/health
```

## 测试结果

```
Tests run: 31
Failures: 0
Errors: 0
Skipped: 0
Success: 100%
```

## 项目结构

```
com.xiyu.bid
├── auth/              # JWT和Security配置
├── config/            # Spring配置类
├── controller/        # REST控制器
├── dto/               # 数据传输对象
├── entity/            # JPA实体
├── repository/        # 数据访问层
├── service/           # 业务逻辑层
└── XiyuBidApplication # 主应用类
```

## 用户角色

- `ADMIN` - 管理员，完全访问权限
- `MANAGER` - 经理，管理权限
- `STAFF` - 普通员工，基础权限

## 配置文件

- `application.yml` - 生产配置
- `application-dev.yml` - 开发配置 (H2内存数据库)
- `application-test.yml` - 测试配置

## 端口

- 应用端口: `8080`
- H2控制台: `http://localhost:8080/h2-console`

## 默认用户

测试时会自动创建用户：
- 用户名: `testuser`
- 密码: `password123`
- 角色: `STAFF`

## 故障排查

### 应用无法启动
```bash
# 检查Java版本
java -version  # 需要 Java 21+

# 检查端口占用
lsof -i :8080
```

### 测试失败
```bash
# 清理并重新测试
mvn clean test

# 查看详细日志
mvn test -X
```

### 数据库连接问题
```bash
# 使用dev配置启动
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```
