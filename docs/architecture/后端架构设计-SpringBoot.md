# 西域智慧供应链投标管理平台 - 后端架构设计 (Spring Boot)

> 基于Spring Boot 3 + Spring Security + MyBatis-Plus的企业级后端系统

---

## 一、技术栈选型

```
核心框架:      Spring Boot 3.2
JDK版本:       Java 17 (LTS)
持久层:        MyBatis-Plus 3.5
数据库:        MySQL 8.0
缓存:          Redis 7
搜索:          Elasticsearch 8
消息队列:      RabbitMQ / RocketMQ
安全认证:      Spring Security + JWT
文档:          SpringDoc OpenAPI 3
监控:          Spring Boot Actuator + Prometheus
部署:          Docker + Kubernetes
```

---

## 二、项目结构

```
bidding-backend/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/
│   │   │       └── xiyu/
│   │   │           └── bidding/
│   │   │               ├── BiddingApplication.java      # 启动类
│   │   │               │
│   │   │               ├── common/                       # 公共模块
│   │   │               │   ├── annotation/              # 自定义注解
│   │   │               │   ├── aspect/                  # AOP切面
│   │   │               │   ├── config/                  # 配置类
│   │   │               │   ├── constant/                # 常量定义
│   │   │               │   ├── converter/               # 类型转换器
│   │   │               │   ├── enums/                   # 枚举类
│   │   │               │   ├── exception/               # 异常类
│   │   │               │   ├── handler/                 # 异常处理器
│   │   │               │   ├── response/                # 统一响应
│   │   │               │   └── util/                    # 工具类
│   │   │               │
│   │   │               ├── modules/                      # 业务模块
│   │   │               │   │
│   │   │               │   ├── auth/                    # 认证模块
│   │   │               │   │   ├── controller/
│   │   │               │   │   ├── service/
│   │   │               │   │   ├── mapper/
│   │   │               │   │   ├── entity/
│   │   │               │   │   ├── dto/
│   │   │               │   │   ├── vo/
│   │   │               │   │   └── security/
│   │   │               │   │
│   │   │               │   ├── user/                    # 用户模块
│   │   │               │   │   ├── controller/
│   │   │               │   │   ├── service/
│   │   │               │   │   ├── mapper/
│   │   │               │   │   └── entity/
│   │   │               │   │
│   │   │               │   ├── tender/                  # 标讯模块
│   │   │               │   │   ├── controller/
│   │   │               │   │   ├── service/
│   │   │               │   │   ├── mapper/
│   │   │               │   │   └── entity/
│   │   │               │   │
│   │   │               │   ├── project/                 # 项目模块
│   │   │               │   ├── task/                    # 任务模块
│   │   │               │   ├── knowledge/               # 知识库模块
│   │   │               │   │   ├── qualification/
│   │   │               │   │   ├── case/
│   │   │               │   │   └── template/
│   │   │               │   ├── resource/                # 资源模块
│   │   │               │   │   ├── expense/
│   │   │               │   │   ├── account/
│   │   │               │   │   └── bar/
│   │   │               │   ├── approval/                # 审批模块
│   │   │               │   ├── ai/                      # AI服务模块
│   │   │               │   ├── analytics/               # 数据分析模块
│   │   │               │   ├── notification/            # 通知模块
│   │   │               │   ├── system/                  # 系统管理模块
│   │   │               │   │   ├── controller/
│   │   │               │   │   ├── service/
│   │   │               │   │   └── mapper/
│   │   │               │   │
│   │   │               │   └── integration/             # 第三方集成
│   │   │               │       ├── dingtalk/
│   │   │               │       ├── wechat/
│   │   │               │       └── feishu/
│   │   │               │
│   │   │               ├── security/                     # 安全配置
│   │   │               │   ├── SecurityConfig.java
│   │   │               │   ├── JwtAuthenticationFilter.java
│   │   │               │   └── JwtTokenProvider.java
│   │   │               │
│   │   │               └── config/                       # 配置类
│   │   │                   ├── MybatisPlusConfig.java
│   │   │                   ├── RedisConfig.java
│   │   │                   ├── ThreadPoolConfig.java
│   │   │                   └── WebConfig.java
│   │   │
│   │   └── resources/
│   │       ├── application.yml                    # 主配置
│   │       ├── application-dev.yml                # 开发环境
│   │       ├── application-prod.yml               # 生产环境
│   │       ├── application-local.yml              # 本地环境
│   │       ├── mapper/                             # MyBatis XML
│   │       │   └── **/Mapper.xml
│   │       └── logback-spring.xml                  # 日志配置
│   │
│   └── test/                                      # 测试代码
│       └── java/
│
├── Dockerfile
├── pom.xml
└── README.md
```

---

## 三、Maven依赖

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.xiyu</groupId>
    <artifactId>bidding-platform</artifactId>
    <version>1.0.0</version>
    <name>西域智慧供应链投标管理平台</name>
    <description>企业级投标管理系统</description>

    <properties>
        <java.version>17</java.version>
        <mybatis-plus.version>3.5.5</mybatis-plus.version>
        <druid.version>1.2.20</druid.version>
        <jwt.version>0.12.3</jwt.version>
        <knife4j.version>4.3.0</knife4j.version>
        <hutool.version>5.8.23</hutool.version>
        <easyexcel.version>3.3.2</easyexcel.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Security -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- Spring Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Spring AOP -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>

        <!-- Redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-pool2</artifactId>
        </dependency>

        <!-- RabbitMQ -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-amqp</artifactId>
        </dependency>

        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>

        <!-- MyBatis-Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- Druid 数据源 -->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid-spring-boot-starter</artifactId>
            <version>${druid.version}</version>
        </dependency>

        <!-- JWT -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jwt.version}</version>
        </dependency>

        <!-- Knife4j API文档 -->
        <dependency>
            <groupId>com.github.xiaoymin</groupId>
            <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
            <version>${knife4j.version}</version>
        </dependency>

        <!-- Hutool工具类 -->
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
            <version>${hutool.version}</version>
        </dependency>

        <!-- EasyExcel -->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>easyexcel</artifactId>
            <version>${easyexcel.version}</version>
        </dependency>

        <!-- HTTP客户端 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- 监控 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 四、核心代码实现

### 4.1 统一响应结构

```java
// common/response/Result.java
package com.xiyu.bidding.common.response;

import lombok.Data;
import java.io.Serializable;

@Data
public class Result<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private Boolean success;
    private String code;
    private String message;
    private T data;
    private Long timestamp;

    public Result() {
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> ok() {
        Result<T> result = new Result<>();
        result.setSuccess(true);
        result.setCode("200");
        result.setMessage("操作成功");
        return result;
    }

    public static <T> Result<T> ok(T data) {
        Result<T> result = ok();
        result.setData(data);
        return result;
    }

    public static <T> Result<T> ok(String message, T data) {
        Result<T> result = ok(data);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> fail(String code, String message) {
        Result<T> result = new Result<>();
        result.setSuccess(false);
        result.setCode(code);
        result.setMessage(message);
        return result;
    }

    public static <T> Result<T> fail(BusinessException ex) {
        return fail(ex.getCode(), ex.getMessage());
    }
}

// 分页响应
@Data
public class PageResult<T> implements Serializable {

    private Long total;
    private List<T> records;
    private Long current;
    private Long size;
    private Long pages;

    public static <T> PageResult<T> of(com.baomidou.mybatisplus.extension.plugins.pagination.Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setRecords(page.getRecords());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setPages(page.getPages());
        return result;
    }
}
```

### 4.2 统一异常处理

```java
// common/exception/BusinessException.java
package com.xiyu.bidding.common.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final String code;

    public BusinessException(String message) {
        super(message);
        this.code = "500";
    }

    public BusinessException(String code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }
}

// ============================================

// common/exception/ErrorCode.java
package com.xiyu.bidding.common.exception;

import lombok.Getter;

@Getter
public enum ErrorCode {

    // 通用错误
    SUCCESS("200", "操作成功"),
    BAD_REQUEST("400", "请求参数错误"),
    UNAUTHORIZED("401", "未授权"),
    FORBIDDEN("403", "禁止访问"),
    NOT_FOUND("404", "资源不存在"),
    INTERNAL_ERROR("500", "服务器内部错误"),

    // 业务错误
    TENDER_NOT_FOUND("1001", "标讯不存在"),
    PROJECT_NOT_FOUND("1002", "项目不存在"),
    TASK_NOT_FOUND("1003", "任务不存在"),
    QUALIFICATION_EXPIRED("2001", "资质已过期"),
    INSUFFICIENT_BALANCE("3001", "余额不足"),

    // 用户错误
    USER_NOT_FOUND("4001", "用户不存在"),
    USER_LOCKED("4002", "用户已锁定"),
    INVALID_CREDENTIALS("4003", "用户名或密码错误"),
    TOKEN_EXPIRED("4004", "令牌已过期"),
    TOKEN_INVALID("4005", "令牌无效"),

    // 权限错误
    NO_PERMISSION("5001", "无权限操作"),
    DATA_SCOPE_EXCEEDED("5002", "超出数据范围");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }
}

// ============================================

// common/handler/GlobalExceptionHandler.java
package com.xiyu.bidding.common.handler;

import com.xiyu.bidding.common.exception.BusinessException;
import com.xiyu.bidding.common.exception.ErrorCode;
import com.xiyu.bidding.common.response.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e, HttpServletRequest request) {
        log.warn("业务异常: uri={}, message={}", request.getRequestURI(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常 (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数校验失败";
        log.warn("参数校验失败: {}", message);
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 参数绑定异常
     */
    @ExceptionHandler(BindException.class)
    public Result<?> handleBindException(BindException e) {
        FieldError fieldError = e.getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : "参数绑定失败";
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 约束违反异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<?> handleConstraintViolationException(ConstraintViolationException e) {
        String message = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .findFirst()
                .orElse("参数校验失败");
        return Result.fail(ErrorCode.BAD_REQUEST.getCode(), message);
    }

    /**
     * 系统异常
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常: uri={}", request.getRequestURI(), e);
        return Result.fail(ErrorCode.INTERNAL_ERROR.getCode(), "系统繁忙，请稍后重试");
    }
}
```

### 4.3 实体类

```java
// modules/tender/entity/Tender.java
package com.xiyu.bidding.modules.tender.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("tenders")
public class Tender {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String title;

    private String source;

    private String sourceUrl;

    private String projectCode;

    private BigDecimal budget;

    private String region;

    private String industry;

    private String purchaser;

    private String purchaserType;

    private LocalDateTime deadline;

    private LocalDateTime openingDate;

    private LocalDateTime publishDate;

    private String description;

    private String requirements;

    private Integer aiScore;

    private String winProbability;

    private String recommendation;

    private TenderStatus status;

    private String creatorId;

    private String assignedTo;

    private FollowUpStatus followUpStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}

// ============================================

// modules/tender/entity/TenderStatus.java
package com.xiyu.bidding.modules.tender.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonValue;

public enum TenderStatus {

    NEW("新建"),
    CONTACTED("已联系"),
    FOLLOWING("跟进中"),
    QUOTING("报价中"),
    BIDDING("投标中"),
    ABANDONED("已放弃"),
    CONVERTED("已转项目");

    @EnumValue
    private final String value;

    TenderStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }
}
```

### 4.4 Mapper

```java
// modules/tender/mapper/TenderMapper.java
package com.xiyu.bidding.modules.tender.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiyu.bidding.modules.tender.entity.Tender;
import com.xiyu.bidding.modules.tender.query.TenderQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TenderMapper extends BaseMapper<Tender> {

    /**
     * 分页查询标讯（带关联信息）
     */
    IPage<Tender> selectPageWithInfo(Page<Tender> page, @Param("query") TenderQuery query);

    /**
     * 查询高分标讯
     */
    List<Tender> selectHighScoreTenders(@Param("minScore") Integer minScore);

    /**
     * 统计标讯数据
     */
    TenderStatistics selectStatistics(@Param("creatorId") String creatorId);
}

// modules/tender/query/TenderStatistics.java
package com.xiyu.bidding.modules.tender.query;

import lombok.Data;
import java.util.Map;

@Data
public class TenderStatistics {
    private Long total;
    private Map<String, Long> byStatus;
    private Long highScore;
    private Long thisMonth;
}

// modules/tender/mapper/TenderMapper.xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.xiyu.bidding.modules.tender.mapper.TenderMapper">

    <resultMap id="BaseResultMap" type="com.xiyu.bidding.modules.tender.entity.Tender">
        <id column="id" property="id"/>
        <result column="title" property="title"/>
        <result column="status" property="status"/>
        <!-- 其他字段 -->
    </resultMap>

    <!-- 分页查询（带关联） -->
    <select id="selectPageWithInfo" resultType="com.xiyu.bidding.modules.tender.entity.Tender">
        SELECT
            t.*,
            c1.name as creatorName,
            c1.avatar as creatorAvatar,
            c2.name as assigneeName,
            c2.avatar as assigneeAvatar
        FROM tenders t
        LEFT JOIN users c1 ON t.creator_id = c1.id
        LEFT JOIN users c2 ON t.assigned_to = c2.id
        <where>
            t.deleted = 0
            <if test="query.status != null and query.status != ''">
                AND t.status = #{query.status}
            </if>
            <if test="query.region != null and query.region != ''">
                AND t.region = #{query.region}
            </if>
            <if test="query.industry != null and query.industry != ''">
                AND t.industry = #{query.industry}
            </if>
            <if test="query.aiScore != null">
                AND t.ai_score &gt;= #{query.aiScore}
            </if>
            <if test="query.keyword != null and query.keyword != ''">
                AND (t.title LIKE CONCAT('%', #{query.keyword}, '%')
                     OR t.purchaser LIKE CONCAT('%', #{query.keyword}, '%'))
            </if>
        </where>
        ORDER BY t.created_at DESC
    </select>

    <!-- 高分标讯 -->
    <select id="selectHighScoreTenders" resultType="com.xiyu.bidding.modules.tender.entity.Tender">
        SELECT t.*,
               c1.name as creatorName,
               c1.avatar as creatorAvatar
        FROM tenders t
        LEFT JOIN users c1 ON t.creator_id = c1.id
        WHERE t.deleted = 0
          AND t.ai_score &gt;= #{minScore}
          AND t.status IN ('NEW', 'CONTACTED', 'FOLLOWING')
        ORDER BY t.ai_score DESC
        LIMIT 50
    </select>

</mapper>
```

### 4.5 Service

```java
// modules/tender/service/TenderService.java
package com.xiyu.bidding.modules.tender.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.xiyu.bidding.common.exception.BusinessException;
import com.xiyu.bidding.common.exception.ErrorCode;
import com.xiyu.bidding.modules.ai.service.AIAnalysisService;
import com.xiyu.bidding.modules.notification.service.NotificationService;
import com.xiyu.bidding.modules.tender.entity.Tender;
import com.xiyu.bidding.modules.tender.mapper.TenderMapper;
import com.xiyu.bidding.modules.tender.query.TenderQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenderService extends ServiceImpl<TenderMapper, Tender> {

    @Autowired
    private AIAnalysisService aiAnalysisService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 分页查询标讯
     */
    public IPage<Tender> getPage(Page<Tender> page, TenderQuery query) {
        return baseMapper.selectPageWithInfo(page, query);
    }

    /**
     * 获取标讯详情（带缓存）
     */
    public Tender getDetail(String id) {
        // 尝试从缓存获取
        String cacheKey = "tender:detail:" + id;
        Tender cached = (Tender) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 从数据库查询
        Tender tender = this.getById(id);
        if (tender == null) {
            throw new BusinessException(ErrorCode.TENDER_NOT_FOUND);
        }

        // 缓存5分钟
        redisTemplate.opsForValue().set(cacheKey, tender, 5, TimeUnit.MINUTES);

        return tender;
    }

    /**
     * 创建标讯
     */
    @Transactional(rollbackFor = Exception.class)
    public Tender create(Tender tender, String creatorId) {
        tender.setCreatorId(creatorId);
        tender.setStatus(TenderStatus.NEW);

        this.save(tender);

        // 异步触发AI分析
        this.asyncAnalyze(tender.getId());

        return tender;
    }

    /**
     * 更新标讯
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(Tender tender, String userId) {
        Tender existing = this.getById(tender.getId());
        if (existing == null) {
            throw new BusinessException(ErrorCode.TENDER_NOT_FOUND);
        }

        // 权限检查
        if (!existing.getCreatorId().equals(userId)
            && !existing.getAssignedTo().equals(userId)) {
            throw new BusinessException(ErrorCode.NO_PERMISSION);
        }

        this.updateById(tender);

        // 清除缓存
        redisTemplate.delete("tender:detail:" + tender.getId());
    }

    /**
     * 指派标讯
     */
    @Transactional(rollbackFor = Exception.class)
    public void assign(String id, String assigneeId) {
        Tender tender = this.getById(id);
        if (tender == null) {
            throw new BusinessException(ErrorCode.TENDER_NOT_FOUND);
        }

        tender.setAssignedTo(assigneeId);
        tender.setFollowUpStatus(FollowUpStatus.CONTACTED);
        this.updateById(tender);

        // 发送通知
        notificationService.sendNotification(
            assigneeId,
            "TENDER_ASSIGNED",
            "新标讯指派",
            "标讯 \"" + tender.getTitle() + "\" 已指派给您",
            "tender:" + id
        );
    }

    /**
     * AI分析
     */
    @Transactional(rollbackFor = Exception.class)
    public TenderAnalysis analyze(String id) {
        Tender tender = this.getById(id);
        if (tender == null) {
            throw new BusinessException(ErrorCode.TENDER_NOT_FOUND);
        }

        // 调用AI服务
        TenderAnalysis analysis = aiAnalysisService.analyzeTender(tender);

        // 保存分析结果
        tender.setAiScore(analysis.getAiScore());
        tender.setWinProbability(analysis.getWinProbability());
        this.updateById(tender);

        return analysis;
    }

    /**
     * 异步AI分析
     */
    private void asyncAnalyze(String tenderId) {
        // 使用线程池异步执行
        CompletableFuture.runAsync(() -> {
            try {
                this.analyze(tenderId);
            } catch (Exception e) {
                log.error("AI分析失败: tenderId={}", tenderId, e);
            }
        });
    }
}
```

### 4.6 Controller

```java
// modules/tender/controller/TenderController.java
package com.xiyu.bidding.modules.tender.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.xiyu.bidding.common.response.PageResult;
import com.xiyu.bidding.common.response.Result;
import com.xiyu.bidding.modules.tender.entity.Tender;
import com.xiyu.bidding.modules.tender.entity.TenderStatus;
import com.xiyu.bidding.modules.tender.query.TenderQuery;
import com.xiyu.bidding.modules.tender.service.TenderService;
import com.xiyu.bidding.modules.tender.vo.TenderDetailVO;
import com.xiyu.bidding.modules.auth.util.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;

@Tag(name = "标讯管理", description = "标讯相关接口")
@RestController
@RequestMapping("/api/v1/tenders")
@RequiredArgsConstructor
public class TenderController {

    private final TenderService tenderService;

    @Operation(summary = "获取标讯列表")
    @GetMapping
    public Result<IPage<Tender>> getPage(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "20") Long size,
            TenderQuery query
    ) {
        IPage<Tender> page = tenderService.getPage(
            new Page<>(current, size),
            query
        );
        return Result.ok(page);
    }

    @Operation(summary = "获取标讯详情")
    @GetMapping("/{id}")
    public Result<TenderDetailVO> getDetail(@PathVariable String id) {
        Tender tender = tenderService.getDetail(id);
        return Result.ok(TenderDetailVO.fromEntity(tender));
    }

    @Operation(summary = "创建标讯")
    @PostMapping
    public Result<Tender> create(@Valid @RequestBody TenderCreateDTO dto) {
        String userId = SecurityUtils.getCurrentUserId();
        Tender tender = tenderService.create(dto.toEntity(), userId);
        return Result.ok("标讯创建成功", tender);
    }

    @Operation(summary = "更新标讯")
    @PutMapping("/{id}")
    public Result<Void> update(
            @PathVariable String id,
            @Valid @RequestBody TenderUpdateDTO dto
    ) {
        String userId = SecurityUtils.getCurrentUserId();
        tenderService.update(dto.toEntity(id), userId);
        return Result.ok("更新成功");
    }

    @Operation(summary = "删除标讯")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        tenderService.delete(id, userId);
        return Result.ok("删除成功");
    }

    @Operation(summary = "指派标讯")
    @PostMapping("/{id}/assign")
    public Result<Void> assign(
            @PathVariable String id,
            @RequestBody AssignDTO dto
    ) {
        tenderService.assign(id, dto.getAssigneeId());
        return Result.ok("指派成功");
    }

    @Operation(summary = "AI分析")
    @PostMapping("/{id}/analyze")
    public Result<TenderAnalysis> analyze(@PathVariable String id) {
        TenderAnalysis analysis = tenderService.analyze(id);
        return Result.ok(analysis);
    }

    @Operation(summary = "批量操作")
    @PostMapping("/batch")
    public Result<Void> batchOperation(@RequestBody BatchOperationDTO dto) {
        tenderService.batchOperation(dto);
        return Result.ok("批量操作成功");
    }
}
```

### 4.7 Spring Security 配置

```java
// security/SecurityConfig.java
package com.xiyu.bidding.security;

import com.xiyu.bidding.security.filter.JwtAuthenticationFilter;
import com.xiyu.bidding.security.handler.JwtAccessDeniedHandler;
import com.xiyu.bidding.security.handler.JwtAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    /**
     * 密码编码器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 安全过滤链
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 禁用CSRF
            .csrf(csrf -> csrf.disable())

            // 配置CORS
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 无状态会话
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            // 配置授权
            .authorizeHttpRequests(auth -> auth
                // 公开接口
                .requestMatchers("/api/v1/auth/**").permitAll()
                .requestMatchers("/doc.html").permitAll()
                .requestMatchers("/swagger-ui/**").permitAll()
                .requestMatchers("/v3/api-docs/**").permitAll()
                // 其他接口需要认证
                .anyRequest().authenticated()
            )

            // 添加JWT过滤器
            .authenticationProvider(authenticationProvider())
            .addFilterBefore(jwtAuthenticationFilter(),
                           UsernamePasswordAuthenticationFilter.class)

            // 异常处理
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(new JwtAuthenticationEntryPoint())
                .accessDeniedHandler(new JwtAccessDeniedHandler())
            );

        return http.build();
    }

    /**
     * CORS配置
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("http://localhost:1314"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

### 4.8 定时任务

```java
// config/ScheduledConfig.java
package com.xiyu.bidding.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
public class ScheduledConfig {
    // 定时任务配置
}

// ============================================

// modules/warning/job/WarningJob.java
package com.xiyu.bidding.modules.warning.job;

import com.xiyu.bidding.modules.warning.service.WarningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WarningJob {

    private final WarningService warningService;

    /**
     * 每天早上8点执行预警检查
     * cron = "秒 分 时 日 月 周"
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void dailyWarningCheck() {
        log.info("开始执行每日预警检查");

        try {
            // 资质到期预警
            warningService.checkQualificationExpiry();

            // 保证金收回预警
            warningService.checkDepositRefund();

            // 项目截止预警
            warningService.checkProjectDeadline();

            log.info("每日预警检查完成");
        } catch (Exception e) {
            log.error("每日预警检查失败", e);
        }
    }

    /**
     * 每小时检查一次紧急预警
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void hourlyWarningCheck() {
        log.info("开始执行紧急预警检查");

        try {
            warningService.checkUrgentWarnings();
        } catch (Exception e) {
            log.error("紧急预警检查失败", e);
        }
    }
}
```

---

## 五、配置文件

```yaml
# application.yml
spring:
  application:
    name: bidding-platform
  profiles:
    active: @profiles.active@

  # 数据源配置
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/bidding_db?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: bidding_user
    password: ${DB_PASSWORD:bidding_pass}
    type: com.alibaba.druid.pool.DruidDataSource
    druid:
      initial-size: 5
      min-idle: 5
      max-active: 20
      max-wait: 60000
      validation-query: SELECT 1
      test-while-idle: true
      test-on-borrow: false
      test-on-return: false

  # Redis配置
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: ${REDIS_DB:0}
      timeout: 3000
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
          max-wait: -1ms

  # RabbitMQ配置
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}

  # Jackson配置
  jackson:
    time-zone: GMT+8
    date-format: yyyy-MM-dd HH:mm:ss
    serialization:
      write-dates-as-timestamps: false

# MyBatis-Plus配置
mybatis-plus:
  mapper-locations: classpath*:/mapper/**/*Mapper.xml
  type-aliases-package: com.xiyu.bidding.modules.**.entity
  configuration:
    map-underscore-to-camel-case: true
    cache-enabled: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      id-type: ASSIGN_ID
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

# JWT配置
jwt:
  secret: ${JWT_SECRET:your-secret-key-at-least-256-bits-long}
  expiration: ${JWT_EXPIRATION:604800}  # 7天

# 应用配置
app:
  name: 西域智慧供应链投标管理平台
  version: 1.0.0
  file:
    upload-path: /data/uploads
    max-size: 10MB
  ai:
    api-key: ${AI_API_KEY}
    api-url: ${AI_API_URL:https://api.openai.com/v1}
```

---

## 六、pom.xml 完整版

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
        <relativePath/>
    </parent>

    <groupId>com.xiyu</groupId>
    <artifactId>bidding-platform</artifactId>
    <version>1.0.0</version>
    <name>bidding-platform</name>
    <description>西域智慧供应链投标管理平台</description>

    <properties>
        <java.version>17</java.version>
        <maven.compiler.source>17</maven.compiler.source>
        <maven.compiler.target>17</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

        <!-- 依赖版本 -->
        <mybatis-plus.version>3.5.5</mybatis-plus.version>
        <druid.version>1.2.20</druid.version>
        <jwt.version>0.12.3</jwt.version>
        <knife4j.version>4.3.0</knife4j.version>
        <hutool.version>5.8.23</hutool.version>
        <easyexcel.version>3.3.2</easyexcel.version>
    </properties>

    <dependencies>
        <!-- Spring Boot Web -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Security -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>

        <!-- Spring Validation -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <!-- Spring AOP -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>

        <!-- Redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-pool2</artifactId>
        </dependency>

        <!-- RabbitMQ -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-amqp</artifactId>
        </dependency>

        <!-- MySQL -->
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
        </dependency>

        <!-- MyBatis-Plus -->
        <dependency>
            <groupId>com.baomidou</groupId>
            <artifactId>mybatis-plus-boot-starter</artifactId>
            <version>${mybatis-plus.version}</version>
        </dependency>

        <!-- Druid -->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>druid-spring-boot-3-starter</artifactId>
            <version>1.2.20</version>
        </dependency>

        <!-- JWT -->
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-api</artifactId>
            <version>${jwt.version}</version>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-impl</artifactId>
            <version>${jwt.version}</version>
            <scope>runtime</scope>
        </dependency>
        <dependency>
            <groupId>io.jsonwebtoken</groupId>
            <artifactId>jjwt-jackson</artifactId>
            <version>${jwt.version}</version>
            <scope>runtime</scope>
        </dependency>

        <!-- Knife4j API文档 -->
        <dependency>
            <groupId>com.github.xiaoymin</groupId>
            <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
            <version>${knife4j.version}</version>
        </dependency>

        <!-- Hutool工具 -->
        <dependency>
            <groupId>cn.hutool</groupId>
            <artifactId>hutool-all</artifactId>
            <version>${hutool.version}</version>
        </dependency>

        <!-- EasyExcel -->
        <dependency>
            <groupId>com.alibaba</groupId>
            <artifactId>easyexcel</artifactId>
            <version>${easyexcel.version}</version>
        </dependency>

        <!-- 监控 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- 测试 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## 七、Spring Boot vs NestJS 对比

| 对比项 | Spring Boot | NestJS |
|:-------|:-----------|:--------|
| **语言** | Java | TypeScript |
| **学习曲线** | 中等，Java生态成熟 | 中等，需熟悉装饰器 |
| **人才供应** | ⭐⭐⭐⭐⭐ 国内充足 | ⭐⭐ 相对较少 |
| **企业认可** | ⭐⭐⭐⭐⭐ 2B首选 | ⭐⭐⭐ 互联网公司 |
| **生态成熟** | ⭐⭐⭐⭐⭐ 非常成熟 | ⭐⭐⭐⭐ 快速发展 |
| **性能** | 优秀，JVM优化 | 优秀，V8引擎 |
| **部署运维** | ⭐⭐⭐⭐⭐ 工具链完善 | ⭐⭐⭐⭐ Node.js生态 |
| **适合场景** | 大型2B企业应用 | 创业公司、快速原型 |

---

**结论：对于国内2B项目，Spring Boot是更务实的选择。**


---

## 八、进阶架构模式：函数式内核，命令式外壳

为了解决 Service 层逻辑臃肿、难以进行单元测试以及 I/O 散乱的问题，项目引入了 **Functional Core, Imperative Shell** 模式。

### 8.1 核心定义

*   **函数式内核 (Functional Core)**: 包含所有的业务决策和状态计算。它是纯粹的 Java 代码，没有任何框架依赖。
*   **命令式外壳 (Imperative Shell)**: 负责流程编排。它处理所有的副作用（数据库读写、外部 API 调用、系统时间）。

### 8.2 模式规范

1.  **Core 类约束**:
    *   必须位于模块的 `core/` 包下。
    *   方法必须是纯函数（Pure Functions）。
    *   禁止注入任何 Spring Bean (Repository, Service)。
    *   禁止直接获取系统时间（需由参数传入）。
    *   返回计算后的实体或状态对象。

2.  **Service 类职责**:
    *   负责数据获取（I/O Read）。
    *   负责调用 Core 类的纯函数进行逻辑决策。
    *   负责持久化结果（I/O Write）。
    *   优化：将业务流程中的多次数据库写入合并为一次批量更新。

### 8.3 应用收益

*   **测试性**: 业务逻辑不再依赖 Spring 容器，可在毫秒级完成 JUnit 测试。
*   **确定性**: 相同的输入永远产生相同的输出。
*   **性能**: 强制将 I/O 推向边缘，自然减少了冗余的数据库往返。

> 示例参考：`BidResultReminderLogic.java` (内核) 与 `BidResultReminderService.java` (外壳)。
