-- V1003__tender_keyword_subscription.sql
-- 标讯关键词订阅功能：用户可设置关键词组合，系统每日匹配新增标讯并推送通知

-- 1. 标讯关键词订阅表
CREATE TABLE `tender_keyword_subscription` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`         BIGINT       NOT NULL COMMENT '用户ID',
  `name`            VARCHAR(100) NOT NULL COMMENT '订阅名称',
  `logic_operator`  VARCHAR(10)  NOT NULL DEFAULT 'OR' COMMENT '关键词逻辑关系：AND（全部匹配）/ OR（任一匹配）',
  `status`          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE（启用）/ PAUSED（暂停）',
  `last_matched_at` DATETIME              DEFAULT NULL COMMENT '上次匹配时间',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_tks_user` (`user_id`),
  KEY `idx_tks_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标讯关键词订阅';

-- 2. 订阅关键词表
CREATE TABLE `tender_keyword_subscription_keyword` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `subscription_id` BIGINT       NOT NULL COMMENT '订阅ID',
  `keyword`         VARCHAR(200) NOT NULL COMMENT '关键词',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_tksk_sub` (`subscription_id`),
  CONSTRAINT `fk_tksk_sub` FOREIGN KEY (`subscription_id`) REFERENCES `tender_keyword_subscription` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订阅关键词';

-- 3. 标讯关键词匹配日志表
CREATE TABLE `tender_keyword_match_log` (
  `id`              BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `subscription_id` BIGINT        NOT NULL COMMENT '订阅ID',
  `user_id`         BIGINT        NOT NULL COMMENT '用户ID',
  `tender_id`       BIGINT        NOT NULL COMMENT '标讯ID',
  `tender_title`    VARCHAR(500)  NOT NULL COMMENT '标讯标题',
  `matched_keywords` VARCHAR(1000) DEFAULT NULL COMMENT '命中的关键词列表（JSON数组）',
  `notified`        TINYINT(1)    NOT NULL DEFAULT 0 COMMENT '是否已通知用户',
  `notified_at`     DATETIME       DEFAULT NULL COMMENT '通知时间',
  `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_tkml_sub` (`subscription_id`),
  KEY `idx_tkml_user` (`user_id`),
  KEY `idx_tkml_tender` (`tender_id`),
  KEY `idx_tkml_notified` (`notified`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标讯关键词匹配日志';
