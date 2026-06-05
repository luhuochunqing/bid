-- MySQL dump 10.13  Distrib 8.0.45, for Linux (aarch64)
--
-- Host: localhost    Database: xiyu_bid
-- ------------------------------------------------------
-- Server version	8.0.45

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `accounts`
--

DROP TABLE IF EXISTS `accounts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `accounts` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `industry` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `region` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contact_info` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `credit_level` enum('A','B','C','D') COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('CLIENT','SUPPLIER','PARTNER','GOVERNMENT','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_qtv290mh55xhggmpwosf5ag0v` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `accounts`
--

LOCK TABLES `accounts` WRITE;
/*!40000 ALTER TABLE `accounts` DISABLE KEYS */;
/*!40000 ALTER TABLE `accounts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_analysis_jobs`
--

DROP TABLE IF EXISTS `ai_analysis_jobs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_analysis_jobs` (
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `requested_by` bigint DEFAULT NULL,
  `target_id` bigint NOT NULL,
  `error_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `analysis_type` enum('TENDER_ANALYSIS','PROJECT_SCORE_PREVIEW') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('PENDING','COMPLETED','FAILED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `target_type` enum('TENDER','PROJECT') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_job_target` (`target_type`,`target_id`),
  KEY `idx_ai_job_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_analysis_jobs`
--

LOCK TABLES `ai_analysis_jobs` WRITE;
/*!40000 ALTER TABLE `ai_analysis_jobs` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_analysis_jobs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_analysis_results`
--

DROP TABLE IF EXISTS `ai_analysis_results`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_analysis_results` (
  `score` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `job_id` bigint DEFAULT NULL,
  `project_id` bigint DEFAULT NULL,
  `tender_id` bigint DEFAULT NULL,
  `risk_level` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `suggestion` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payload_json` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `analysis_type` enum('TENDER_ANALYSIS','PROJECT_SCORE_PREVIEW') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_ai_result_tender` (`tender_id`,`created_at`),
  KEY `idx_ai_result_project` (`project_id`,`created_at`),
  KEY `idx_ai_result_job` (`job_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_analysis_results`
--

LOCK TABLES `ai_analysis_results` WRITE;
/*!40000 ALTER TABLE `ai_analysis_results` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_analysis_results` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `alert_history`
--

DROP TABLE IF EXISTS `alert_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alert_history` (
  `resolved` bit(1) NOT NULL,
  `acknowledged_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `resolved_at` datetime(6) DEFAULT NULL,
  `rule_id` bigint NOT NULL,
  `related_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `message` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `level` enum('LOW','MEDIUM','HIGH','CRITICAL') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `alert_history`
--

LOCK TABLES `alert_history` WRITE;
/*!40000 ALTER TABLE `alert_history` DISABLE KEYS */;
/*!40000 ALTER TABLE `alert_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `alert_rules`
--

DROP TABLE IF EXISTS `alert_rules`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alert_rules` (
  `enabled` bit(1) NOT NULL,
  `threshold` decimal(19,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `condition` enum('GREATER_THAN','LESS_THAN','EQUALS','CONTAINS') COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('DEADLINE','BUDGET','RISK','DOCUMENT','QUALIFICATION_EXPIRY','DEPOSIT_RETURN') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `alert_rules`
--

LOCK TABLES `alert_rules` WRITE;
/*!40000 ALTER TABLE `alert_rules` DISABLE KEYS */;
/*!40000 ALTER TABLE `alert_rules` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `approval_actions`
--

DROP TABLE IF EXISTS `approval_actions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `approval_actions` (
  `action_time` datetime(6) NOT NULL,
  `actor_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `approval_request_id` binary(16) NOT NULL,
  `id` binary(16) NOT NULL,
  `actor_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `comment` text COLLATE utf8mb4_unicode_ci,
  `action_type` enum('SUBMIT','APPROVE','REJECT','CANCEL') COLLATE utf8mb4_unicode_ci NOT NULL,
  `new_status` enum('PENDING','APPROVED','REJECTED','CANCELLED') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `previous_status` enum('PENDING','APPROVED','REJECTED','CANCELLED') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_approval_request_id` (`approval_request_id`),
  KEY `idx_action_time` (`action_time`),
  KEY `idx_actor_id` (`actor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `approval_actions`
--

LOCK TABLES `approval_actions` WRITE;
/*!40000 ALTER TABLE `approval_actions` DISABLE KEYS */;
/*!40000 ALTER TABLE `approval_actions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `approval_requests`
--

DROP TABLE IF EXISTS `approval_requests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `approval_requests` (
  `is_read` bit(1) DEFAULT NULL,
  `priority` int NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `current_approver_id` bigint DEFAULT NULL,
  `due_date` datetime(6) DEFAULT NULL,
  `project_id` bigint NOT NULL,
  `requester_id` bigint NOT NULL,
  `submitted_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  `id` binary(16) NOT NULL,
  `approval_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `current_approver_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `requester_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `project_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `attachment_ids` text COLLATE utf8mb4_unicode_ci,
  `description` text COLLATE utf8mb4_unicode_ci,
  `status` enum('PENDING','APPROVED','REJECTED','CANCELLED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_status` (`status`),
  KEY `idx_requester_id` (`requester_id`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_approval_type` (`approval_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `approval_requests`
--

LOCK TABLES `approval_requests` WRITE;
/*!40000 ALTER TABLE `approval_requests` DISABLE KEYS */;
/*!40000 ALTER TABLE `approval_requests` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `assembly_templates`
--

DROP TABLE IF EXISTS `assembly_templates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `assembly_templates` (
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `category` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `template_content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `variables` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `idx_template_category` (`category`),
  KEY `idx_template_created_by` (`created_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `assembly_templates`
--

LOCK TABLES `assembly_templates` WRITE;
/*!40000 ALTER TABLE `assembly_templates` DISABLE KEYS */;
/*!40000 ALTER TABLE `assembly_templates` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audit_logs`
--

DROP TABLE IF EXISTS `audit_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_logs` (
  `success` bit(1) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `timestamp` datetime(6) NOT NULL,
  `action` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `ip_address` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entity_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `entity_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `username` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `user_agent` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `new_value` text COLLATE utf8mb4_unicode_ci,
  `old_value` text COLLATE utf8mb4_unicode_ci,
  `user_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_audit_user` (`user_id`),
  KEY `idx_audit_action` (`action`),
  KEY `idx_audit_timestamp` (`timestamp`),
  KEY `idx_audit_entity` (`entity_type`,`entity_id`)
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_logs`
--

LOCK TABLES `audit_logs` WRITE;
/*!40000 ALTER TABLE `audit_logs` DISABLE KEYS */;
INSERT INTO `audit_logs` VALUES (0x01,1,'2026-04-22 14:01:27.460766','CREATE',NULL,NULL,'Qualification','org.springframework.security.core.userdetails.User [Username=uat_admin_685142, Password=[PROTECTED],','创建资质',NULL,NULL,NULL,NULL,'uat_admin_685142'),(0x01,2,'2026-04-22 14:01:27.508525','CREATE',NULL,NULL,'Case','org.springframework.security.core.userdetails.User [Username=uat_admin_685142, Password=[PROTECTED],','创建案例',NULL,NULL,NULL,NULL,'uat_admin_685142'),(0x01,3,'2026-04-22 14:01:27.589753','CREATE',NULL,NULL,'Template','org.springframework.security.core.userdetails.User [Username=uat_admin_685142, Password=[PROTECTED],','创建模板',NULL,NULL,NULL,NULL,'uat_admin_685142'),(0x01,4,'2026-04-22 14:01:27.644883','READ',NULL,'1','Case','org.springframework.security.core.userdetails.User [Username=uat_admin_685142, Password=[PROTECTED],','根据ID获取案例',NULL,NULL,NULL,NULL,'uat_admin_685142'),(0x01,5,'2026-04-22 14:01:27.695413','CREATE',NULL,NULL,'Expense','org.springframework.security.core.userdetails.User [Username=uat_admin_685142, Password=[PROTECTED],','Create expense record',NULL,NULL,NULL,NULL,'uat_admin_685142'),(0x01,6,'2026-04-22 14:01:27.765783','APPROVE',NULL,'1','Expense','org.springframework.security.core.userdetails.User [Username=uat_admin_685142, Password=[PROTECTED],','Approve or reject expense',NULL,NULL,NULL,NULL,'uat_admin_685142'),(0x01,7,'2026-04-22 14:01:27.821235','RETURN_REQUEST',NULL,'1','Expense','org.springframework.security.core.userdetails.User [Username=uat_admin_685142, Password=[PROTECTED],','Request expense return',NULL,NULL,NULL,NULL,'uat_admin_685142'),(0x01,8,'2026-04-22 14:01:27.869416','CONFIRM_RETURN',NULL,'1','Expense','org.springframework.security.core.userdetails.User [Username=uat_admin_685142, Password=[PROTECTED],','Confirm expense return',NULL,NULL,NULL,NULL,'uat_admin_685142'),(0x01,9,'2026-04-22 14:01:27.954985','CREATE',NULL,NULL,'BarAsset','org.springframework.security.core.userdetails.User [Username=uat_admin_685142, Password=[PROTECTED],','Create bar asset record',NULL,NULL,NULL,NULL,'uat_admin_685142'),(0x01,10,'2026-04-22 14:01:28.006161','CREATE',NULL,'1','BarCertificate','org.springframework.security.core.userdetails.User [Username=uat_admin_685142, Password=[PROTECTED],','Create BAR certificate',NULL,NULL,NULL,NULL,'uat_admin_685142'),(0x01,11,'2026-04-22 14:01:28.049900','BORROW',NULL,'1','BarCertificate','org.springframework.security.core.userdetails.User [Username=uat_admin_685142, Password=[PROTECTED],','Borrow BAR certificate',NULL,NULL,NULL,NULL,'uat_admin_685142'),(0x01,12,'2026-04-22 14:01:28.112055','RETURN',NULL,'1','BarCertificate','org.springframework.security.core.userdetails.User [Username=uat_admin_685142, Password=[PROTECTED],','Return BAR certificate',NULL,NULL,NULL,NULL,'uat_admin_685142'),(0x01,13,'2026-04-22 14:01:33.087964','CREATE',NULL,NULL,'Case','org.springframework.security.core.userdetails.User [Username=commercial_admin_1776837692353, Passwor','创建案例',NULL,NULL,NULL,NULL,'commercial_admin_1776837692353'),(0x01,14,'2026-04-22 14:01:33.113437','CREATE',NULL,NULL,'Expense','org.springframework.security.core.userdetails.User [Username=commercial_admin_1776837692353, Passwor','Create expense record',NULL,NULL,NULL,NULL,'commercial_admin_1776837692353'),(0x01,15,'2026-04-22 14:01:33.134282','CREATE',NULL,NULL,'BarAsset','org.springframework.security.core.userdetails.User [Username=commercial_admin_1776837692353, Passwor','Create bar asset record',NULL,NULL,NULL,NULL,'commercial_admin_1776837692353'),(0x01,16,'2026-04-22 14:01:34.213442','READ',NULL,'3','Task','org.springframework.security.core.userdetails.User [Username=commercial_admin_1776837692353, Passwor','获取我的任务',NULL,NULL,NULL,NULL,'commercial_admin_1776837692353'),(0x01,17,'2026-04-22 14:01:35.101280','READ',NULL,'1','Case','org.springframework.security.core.userdetails.User [Username=commercial_admin_1776837692353, Passwor','获取案例分页列表',NULL,NULL,NULL,NULL,'commercial_admin_1776837692353'),(0x01,18,'2026-04-22 14:01:37.143650','CREATE',NULL,NULL,'Case','org.springframework.security.core.userdetails.User [Username=commercial_detail_1776837696621, Passwo','创建案例',NULL,NULL,NULL,NULL,'commercial_detail_1776837696621'),(0x01,19,'2026-04-22 14:01:37.173300','CREATE',NULL,NULL,'Expense','org.springframework.security.core.userdetails.User [Username=commercial_detail_1776837696621, Passwo','Create expense record',NULL,NULL,NULL,NULL,'commercial_detail_1776837696621'),(0x01,20,'2026-04-22 14:01:37.199042','CREATE',NULL,NULL,'BarAsset','org.springframework.security.core.userdetails.User [Username=commercial_detail_1776837696621, Passwo','Create bar asset record',NULL,NULL,NULL,NULL,'commercial_detail_1776837696621'),(0x01,21,'2026-04-22 14:01:38.051907','READ',NULL,NULL,'Template','org.springframework.security.core.userdetails.User [Username=commercial_detail_1776837696621, Passwo','获取所有模板',NULL,NULL,NULL,NULL,'commercial_detail_1776837696621');
/*!40000 ALTER TABLE `audit_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bar_assets`
--

DROP TABLE IF EXISTS `bar_assets`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bar_assets` (
  `acquire_date` date NOT NULL,
  `asset_value` decimal(19,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('AVAILABLE','IN_USE','MAINTENANCE','RETIRED','DISPOSED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('EQUIPMENT','FACILITY','VEHICLE','INVENTORY','LICENSE','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bar_assets`
--

LOCK TABLES `bar_assets` WRITE;
/*!40000 ALTER TABLE `bar_assets` DISABLE KEYS */;
INSERT INTO `bar_assets` VALUES ('2025-01-01',20000.00,'2026-04-22 14:01:27.943910',1,'2026-04-22 14:01:27.943918','UAT BAR 685142','UAT asset','AVAILABLE','LICENSE'),('2025-03-01',68000.00,'2026-04-22 14:01:33.129379',2,'2026-04-22 14:01:33.129385','COMM BAR 1776837692353','商业主流程 BAR 资产','AVAILABLE','FACILITY'),('2025-03-01',68000.00,'2026-04-22 14:01:37.192693',3,'2026-04-22 14:01:37.192701','COMM BAR 1776837696621','商业主流程 BAR 资产','AVAILABLE','FACILITY');
/*!40000 ALTER TABLE `bar_assets` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bar_certificate_borrow_records`
--

DROP TABLE IF EXISTS `bar_certificate_borrow_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bar_certificate_borrow_records` (
  `expected_return_date` date DEFAULT NULL,
  `borrowed_at` datetime(6) NOT NULL,
  `certificate_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint DEFAULT NULL,
  `returned_at` datetime(6) DEFAULT NULL,
  `borrower` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `purpose` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('BORROWED','RETURNED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bar_certificate_borrow_records`
--

LOCK TABLES `bar_certificate_borrow_records` WRITE;
/*!40000 ALTER TABLE `bar_certificate_borrow_records` DISABLE KEYS */;
INSERT INTO `bar_certificate_borrow_records` VALUES ('2026-03-31','2026-04-22 14:01:28.039170',1,1,1,'2026-04-22 14:01:28.106118','uat_admin_685142','UAT borrow','Returned in UAT','RETURNED');
/*!40000 ALTER TABLE `bar_certificate_borrow_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bar_certificates`
--

DROP TABLE IF EXISTS `bar_certificates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bar_certificates` (
  `expected_return_date` date DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  `bar_asset_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `current_project_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `current_borrower` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `holder` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `provider` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `borrow_purpose` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `location` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `serial_no` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('AVAILABLE','BORROWED','EXPIRED','DISABLED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bar_certificates`
--

LOCK TABLES `bar_certificates` WRITE;
/*!40000 ALTER TABLE `bar_certificates` DISABLE KEYS */;
INSERT INTO `bar_certificates` VALUES (NULL,'2028-01-01',1,'2026-04-22 14:01:27.992810',NULL,1,'2026-04-22 14:01:28.106447',NULL,'uat_admin_685142','UAT Provider','UK',NULL,'UAT Cabinet','SERIAL-685142','Returned in UAT','AVAILABLE');
/*!40000 ALTER TABLE `bar_certificates` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bar_site_accounts`
--

DROP TABLE IF EXISTS `bar_site_accounts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bar_site_accounts` (
  `bar_asset_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `role` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `phone` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `username` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `email` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bar_site_accounts`
--

LOCK TABLES `bar_site_accounts` WRITE;
/*!40000 ALTER TABLE `bar_site_accounts` DISABLE KEYS */;
/*!40000 ALTER TABLE `bar_site_accounts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bar_site_attachments`
--

DROP TABLE IF EXISTS `bar_site_attachments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bar_site_attachments` (
  `bar_asset_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `uploaded_at` datetime(6) NOT NULL,
  `size` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `uploaded_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bar_site_attachments`
--

LOCK TABLES `bar_site_attachments` WRITE;
/*!40000 ALTER TABLE `bar_site_attachments` DISABLE KEYS */;
/*!40000 ALTER TABLE `bar_site_attachments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bar_site_sops`
--

DROP TABLE IF EXISTS `bar_site_sops`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bar_site_sops` (
  `bar_asset_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `estimated_time` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reset_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `unlock_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contacts_json` text COLLATE utf8mb4_unicode_ci,
  `faqs_json` text COLLATE utf8mb4_unicode_ci,
  `history_json` text COLLATE utf8mb4_unicode_ci,
  `required_docs_json` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_i8je16cbyau9qsiu4qoa5fx4d` (`bar_asset_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bar_site_sops`
--

LOCK TABLES `bar_site_sops` WRITE;
/*!40000 ALTER TABLE `bar_site_sops` DISABLE KEYS */;
/*!40000 ALTER TABLE `bar_site_sops` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bar_site_verifications`
--

DROP TABLE IF EXISTS `bar_site_verifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bar_site_verifications` (
  `bar_asset_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `verified_at` datetime(6) NOT NULL,
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `verified_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bar_site_verifications`
--

LOCK TABLES `bar_site_verifications` WRITE;
/*!40000 ALTER TABLE `bar_site_verifications` DISABLE KEYS */;
/*!40000 ALTER TABLE `bar_site_verifications` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bid_result_fetch_results`
--

DROP TABLE IF EXISTS `bid_result_fetch_results`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bid_result_fetch_results` (
  `amount` decimal(19,2) DEFAULT NULL,
  `contract_duration_months` int DEFAULT NULL,
  `contract_end_date` date DEFAULT NULL,
  `contract_start_date` date DEFAULT NULL,
  `sku_count` int DEFAULT NULL,
  `analysis_document_id` bigint DEFAULT NULL,
  `confirmed_at` datetime(6) DEFAULT NULL,
  `confirmed_by` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `fetch_time` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `notice_document_id` bigint DEFAULT NULL,
  `project_id` bigint DEFAULT NULL,
  `tender_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `source` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `project_name` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `win_announce_doc_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ignored_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `registration_type` enum('MANUAL','SYNC','FETCH') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `result` enum('WON','LOST') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('PENDING','CONFIRMED','IGNORED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bid_result_fetch_status` (`status`),
  KEY `idx_bid_result_fetch_project` (`project_id`),
  KEY `idx_bid_result_fetch_tender` (`tender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bid_result_fetch_results`
--

LOCK TABLES `bid_result_fetch_results` WRITE;
/*!40000 ALTER TABLE `bid_result_fetch_results` DISABLE KEYS */;
/*!40000 ALTER TABLE `bid_result_fetch_results` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bid_result_reminders`
--

DROP TABLE IF EXISTS `bid_result_reminders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bid_result_reminders` (
  `attachment_document_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `last_result_id` bigint DEFAULT NULL,
  `owner_id` bigint DEFAULT NULL,
  `project_id` bigint NOT NULL,
  `remind_time` datetime(6) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `uploaded_at` datetime(6) DEFAULT NULL,
  `uploaded_by` bigint DEFAULT NULL,
  `last_reminder_comment` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `project_name` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_by_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `owner_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reminder_type` enum('NOTICE','REPORT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('PENDING','REMINDED','UPLOADED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bid_result_reminder_project` (`project_id`),
  KEY `idx_bid_result_reminder_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bid_result_reminders`
--

LOCK TABLES `bid_result_reminders` WRITE;
/*!40000 ALTER TABLE `bid_result_reminders` DISABLE KEYS */;
/*!40000 ALTER TABLE `bid_result_reminders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `bid_result_sync_logs`
--

DROP TABLE IF EXISTS `bid_result_sync_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `bid_result_sync_logs` (
  `affected_count` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `operator_id` bigint DEFAULT NULL,
  `source` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `operator_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `operation_type` enum('SYNC','FETCH') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bid_result_sync_type` (`operation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `bid_result_sync_logs`
--

LOCK TABLES `bid_result_sync_logs` WRITE;
/*!40000 ALTER TABLE `bid_result_sync_logs` DISABLE KEYS */;
/*!40000 ALTER TABLE `bid_result_sync_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `business_qualifications`
--

DROP TABLE IF EXISTS `business_qualifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `business_qualifications` (
  `expected_return_date` date DEFAULT NULL,
  `expiry_date` date DEFAULT NULL,
  `issue_date` date DEFAULT NULL,
  `reminder_days` int NOT NULL,
  `reminder_enabled` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `last_reminded_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `current_project_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `certificate_no` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `current_borrower` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `current_department` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `holder_name` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `issuer` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `borrow_purpose` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category` enum('LICENSE','PRODUCT','PERSONNEL','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `current_borrow_status` enum('AVAILABLE','BORROWED','RETURNED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('VALID','EXPIRING','EXPIRED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `subject_type` enum('COMPANY','SUBSIDIARY') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `business_qualifications`
--

LOCK TABLES `business_qualifications` WRITE;
/*!40000 ALTER TABLE `business_qualifications` DISABLE KEYS */;
INSERT INTO `business_qualifications` VALUES (NULL,'2027-01-01','2025-01-01',30,0x01,'2026-04-22 14:01:27.435542',1,NULL,'2026-04-22 14:01:27.435550',NULL,NULL,NULL,NULL,NULL,NULL,'UAT 资质 685142','默认主体','/uat/qualification.pdf',NULL,'PRODUCT','AVAILABLE','VALID','COMPANY');
/*!40000 ALTER TABLE `business_qualifications` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `calendar_events`
--

DROP TABLE IF EXISTS `calendar_events`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `calendar_events` (
  `event_date` date NOT NULL,
  `is_urgent` bit(1) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `title` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `event_type` enum('DEADLINE','MEETING','MILESTONE','REMINDER','SUBMISSION','REVIEW') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_event_date` (`event_date`),
  KEY `idx_event_type` (`event_type`),
  KEY `idx_calendar_project_id` (`project_id`),
  KEY `idx_urgent` (`is_urgent`),
  KEY `idx_date_range` (`event_date`,`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `calendar_events`
--

LOCK TABLES `calendar_events` WRITE;
/*!40000 ALTER TABLE `calendar_events` DISABLE KEYS */;
/*!40000 ALTER TABLE `calendar_events` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `case_attachment_names`
--

DROP TABLE IF EXISTS `case_attachment_names`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `case_attachment_names` (
  `case_id` bigint NOT NULL,
  `attachment_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  KEY `FKei1fv1mtwa9xb5ufcqhty2ix4` (`case_id`),
  CONSTRAINT `FKei1fv1mtwa9xb5ufcqhty2ix4` FOREIGN KEY (`case_id`) REFERENCES `cases` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `case_attachment_names`
--

LOCK TABLES `case_attachment_names` WRITE;
/*!40000 ALTER TABLE `case_attachment_names` DISABLE KEYS */;
/*!40000 ALTER TABLE `case_attachment_names` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `case_highlights`
--

DROP TABLE IF EXISTS `case_highlights`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `case_highlights` (
  `case_id` bigint NOT NULL,
  `highlight` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  KEY `FKbv3me1wdrtmxs3q3kt94flu0k` (`case_id`),
  CONSTRAINT `FKbv3me1wdrtmxs3q3kt94flu0k` FOREIGN KEY (`case_id`) REFERENCES `cases` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `case_highlights`
--

LOCK TABLES `case_highlights` WRITE;
/*!40000 ALTER TABLE `case_highlights` DISABLE KEYS */;
INSERT INTO `case_highlights` VALUES (2,'高可用'),(2,'可追溯'),(3,'高可用'),(3,'可追溯');
/*!40000 ALTER TABLE `case_highlights` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `case_lessons_learned`
--

DROP TABLE IF EXISTS `case_lessons_learned`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `case_lessons_learned` (
  `case_id` bigint NOT NULL,
  `lesson_learned` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  KEY `FK86g2td5ti22l2yhsomuj0huuv` (`case_id`),
  CONSTRAINT `FK86g2td5ti22l2yhsomuj0huuv` FOREIGN KEY (`case_id`) REFERENCES `cases` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `case_lessons_learned`
--

LOCK TABLES `case_lessons_learned` WRITE;
/*!40000 ALTER TABLE `case_lessons_learned` DISABLE KEYS */;
/*!40000 ALTER TABLE `case_lessons_learned` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `case_reference_records`
--

DROP TABLE IF EXISTS `case_reference_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `case_reference_records` (
  `case_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `referenced_at` datetime(6) NOT NULL,
  `referenced_by` bigint DEFAULT NULL,
  `reference_context` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `reference_target` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `referenced_by_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `case_reference_records`
--

LOCK TABLES `case_reference_records` WRITE;
/*!40000 ALTER TABLE `case_reference_records` DISABLE KEYS */;
/*!40000 ALTER TABLE `case_reference_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `case_share_records`
--

DROP TABLE IF EXISTS `case_share_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `case_share_records` (
  `case_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_by_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `token` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_p7mn0hbtj3xf60qa6uvdmvu7` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `case_share_records`
--

LOCK TABLES `case_share_records` WRITE;
/*!40000 ALTER TABLE `case_share_records` DISABLE KEYS */;
/*!40000 ALTER TABLE `case_share_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `case_success_factors`
--

DROP TABLE IF EXISTS `case_success_factors`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `case_success_factors` (
  `case_id` bigint NOT NULL,
  `success_factor` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  KEY `FKhc1yltykgesh29adh8c1rveox` (`case_id`),
  CONSTRAINT `FKhc1yltykgesh29adh8c1rveox` FOREIGN KEY (`case_id`) REFERENCES `cases` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `case_success_factors`
--

LOCK TABLES `case_success_factors` WRITE;
/*!40000 ALTER TABLE `case_success_factors` DISABLE KEYS */;
/*!40000 ALTER TABLE `case_success_factors` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `case_tags`
--

DROP TABLE IF EXISTS `case_tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `case_tags` (
  `case_id` bigint NOT NULL,
  `tag` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  KEY `FK5lux2odxtf0cmef0shrqakd07` (`case_id`),
  CONSTRAINT `FK5lux2odxtf0cmef0shrqakd07` FOREIGN KEY (`case_id`) REFERENCES `cases` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `case_tags`
--

LOCK TABLES `case_tags` WRITE;
/*!40000 ALTER TABLE `case_tags` DISABLE KEYS */;
INSERT INTO `case_tags` VALUES (2,'商业'),(2,'投标'),(3,'商业'),(3,'投标');
/*!40000 ALTER TABLE `case_tags` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `case_technologies`
--

DROP TABLE IF EXISTS `case_technologies`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `case_technologies` (
  `case_id` bigint NOT NULL,
  `technology` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  KEY `FKsgesjrn67pmbsbjww06appe3g` (`case_id`),
  CONSTRAINT `FKsgesjrn67pmbsbjww06appe3g` FOREIGN KEY (`case_id`) REFERENCES `cases` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `case_technologies`
--

LOCK TABLES `case_technologies` WRITE;
/*!40000 ALTER TABLE `case_technologies` DISABLE KEYS */;
INSERT INTO `case_technologies` VALUES (2,'Vue'),(2,'Spring Boot'),(3,'Vue'),(3,'Spring Boot');
/*!40000 ALTER TABLE `case_technologies` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `cases`
--

DROP TABLE IF EXISTS `cases`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `cases` (
  `amount` decimal(38,2) NOT NULL,
  `project_date` date DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `published_at` datetime(6) DEFAULT NULL,
  `source_project_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `use_count` bigint NOT NULL,
  `view_count` bigint NOT NULL,
  `status` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `visibility` varchar(30) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `archive_summary` text COLLATE utf8mb4_unicode_ci,
  `customer_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `document_snapshot_text` text COLLATE utf8mb4_unicode_ci,
  `location_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `price_strategy` text COLLATE utf8mb4_unicode_ci,
  `product_line` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `project_period` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `search_document` text COLLATE utf8mb4_unicode_ci,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `industry` enum('REAL_ESTATE','INFRASTRUCTURE','MANUFACTURING','ENERGY','TRANSPORTATION','ENVIRONMENTAL','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `outcome` enum('WON','LOST','IN_PROGRESS') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `cases`
--

LOCK TABLES `cases` WRITE;
/*!40000 ALTER TABLE `cases` DISABLE KEYS */;
INSERT INTO `cases` VALUES (980000.00,'2025-06-01','2026-04-22 14:01:27.491204',1,NULL,NULL,'2026-04-22 14:01:27.491218',0,0,'DRAFT','INTERNAL',NULL,NULL,'UAT generated case',NULL,NULL,NULL,NULL,NULL,'uat 案例 685142 uat generated case draft internal','UAT 案例 685142','INFRASTRUCTURE','WON'),(520.00,'2025-04-01','2026-04-22 14:01:33.077787',2,NULL,NULL,'2026-04-22 14:01:33.077798',0,0,'DRAFT','INTERNAL',NULL,'商业客户','商业主流程案例',NULL,'上海',NULL,NULL,'2025-01-01 - 2025-12-31','comm 案例 1776837692353 商业主流程案例 商业客户 上海 2025-01-01 - 2025-12-31 draft internal 商业 投标 高可用 可追溯 vue spring boot','COMM 案例 1776837692353','INFRASTRUCTURE','WON'),(520.00,'2025-04-01','2026-04-22 14:01:37.131247',3,NULL,NULL,'2026-04-22 14:01:37.131262',0,0,'DRAFT','INTERNAL',NULL,'商业客户','商业主流程案例',NULL,'上海',NULL,NULL,'2025-01-01 - 2025-12-31','comm 案例 1776837696621 商业主流程案例 商业客户 上海 2025-01-01 - 2025-12-31 draft internal 商业 投标 高可用 可追溯 vue spring boot','COMM 案例 1776837696621','INFRASTRUCTURE','WON');
/*!40000 ALTER TABLE `cases` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `collaboration_threads`
--

DROP TABLE IF EXISTS `collaboration_threads`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `collaboration_threads` (
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `title` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('OPEN','IN_PROGRESS','RESOLVED','CLOSED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_thread_project` (`project_id`),
  KEY `idx_thread_status` (`status`),
  KEY `idx_thread_project_status` (`project_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `collaboration_threads`
--

LOCK TABLES `collaboration_threads` WRITE;
/*!40000 ALTER TABLE `collaboration_threads` DISABLE KEYS */;
/*!40000 ALTER TABLE `collaboration_threads` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `comments`
--

DROP TABLE IF EXISTS `comments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `comments` (
  `is_deleted` bit(1) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL,
  `thread_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `mentions` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_comment_thread` (`thread_id`),
  KEY `idx_comment_user` (`user_id`),
  KEY `idx_comment_parent` (`parent_id`),
  KEY `idx_comment_deleted` (`is_deleted`),
  KEY `idx_comment_thread_deleted` (`thread_id`,`is_deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `comments`
--

LOCK TABLES `comments` WRITE;
/*!40000 ALTER TABLE `comments` DISABLE KEYS */;
/*!40000 ALTER TABLE `comments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `competition_analyses`
--

DROP TABLE IF EXISTS `competition_analyses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `competition_analyses` (
  `win_probability` decimal(5,2) DEFAULT NULL,
  `analysis_date` datetime(6) DEFAULT NULL,
  `competitor_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `competitive_advantage` text COLLATE utf8mb4_unicode_ci,
  `recommended_strategy` text COLLATE utf8mb4_unicode_ci,
  `risk_factors` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `idx_analysis_project` (`project_id`),
  KEY `idx_analysis_competitor` (`competitor_id`),
  KEY `idx_analysis_date` (`analysis_date`),
  KEY `idx_analysis_project_competitor` (`project_id`,`competitor_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `competition_analyses`
--

LOCK TABLES `competition_analyses` WRITE;
/*!40000 ALTER TABLE `competition_analyses` DISABLE KEYS */;
/*!40000 ALTER TABLE `competition_analyses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `competitor_win_records`
--

DROP TABLE IF EXISTS `competitor_win_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `competitor_win_records` (
  `amount` decimal(19,2) DEFAULT NULL,
  `sku_count` int DEFAULT NULL,
  `won_at` date DEFAULT NULL,
  `competitor_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint DEFAULT NULL,
  `recorded_by` bigint DEFAULT NULL,
  `discount` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `recorded_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `competitor_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payment_terms` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `project_name` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `notes` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `competitor_win_records`
--

LOCK TABLES `competitor_win_records` WRITE;
/*!40000 ALTER TABLE `competitor_win_records` DISABLE KEYS */;
/*!40000 ALTER TABLE `competitor_win_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `competitors`
--

DROP TABLE IF EXISTS `competitors`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `competitors` (
  `market_share` decimal(5,2) DEFAULT NULL,
  `typical_bid_range_max` decimal(19,2) DEFAULT NULL,
  `typical_bid_range_min` decimal(19,2) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `industry` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `strengths` text COLLATE utf8mb4_unicode_ci,
  `weaknesses` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `idx_competitor_name` (`name`),
  KEY `idx_competitor_industry` (`industry`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `competitors`
--

LOCK TABLES `competitors` WRITE;
/*!40000 ALTER TABLE `competitors` DISABLE KEYS */;
/*!40000 ALTER TABLE `competitors` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `compliance_check_results`
--

DROP TABLE IF EXISTS `compliance_check_results`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `compliance_check_results` (
  `risk_score` int NOT NULL,
  `checked_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint DEFAULT NULL,
  `tender_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `checked_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `check_details` text COLLATE utf8mb4_unicode_ci,
  `overall_status` enum('COMPLIANT','NON_COMPLIANT','PARTIAL_COMPLIANT','WARNING') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_result_project` (`project_id`),
  KEY `idx_result_tender` (`tender_id`),
  KEY `idx_result_status` (`overall_status`),
  KEY `idx_result_checked_at` (`checked_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `compliance_check_results`
--

LOCK TABLES `compliance_check_results` WRITE;
/*!40000 ALTER TABLE `compliance_check_results` DISABLE KEYS */;
/*!40000 ALTER TABLE `compliance_check_results` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `compliance_rules`
--

DROP TABLE IF EXISTS `compliance_rules`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `compliance_rules` (
  `enabled` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rule_definition` text COLLATE utf8mb4_unicode_ci,
  `rule_type` enum('QUALIFICATION','DOCUMENT','FINANCIAL','EXPERIENCE','DEADLINE') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_rule_type` (`rule_type`),
  KEY `idx_rule_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `compliance_rules`
--

LOCK TABLES `compliance_rules` WRITE;
/*!40000 ALTER TABLE `compliance_rules` DISABLE KEYS */;
/*!40000 ALTER TABLE `compliance_rules` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `contract_borrow_applications`
--

DROP TABLE IF EXISTS `contract_borrow_applications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contract_borrow_applications` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `contract_no` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `contract_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `source_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `borrower_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `borrower_dept` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `customer_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `purpose` text COLLATE utf8mb4_unicode_ci,
  `borrow_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `expected_return_date` date DEFAULT NULL,
  `submitted_at` datetime(6) NOT NULL,
  `approver_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `rejection_reason` text COLLATE utf8mb4_unicode_ci,
  `rejected_at` datetime(6) DEFAULT NULL,
  `return_remark` text COLLATE utf8mb4_unicode_ci,
  `returned_at` datetime(6) DEFAULT NULL,
  `cancel_reason` text COLLATE utf8mb4_unicode_ci,
  `cancelled_at` datetime(6) DEFAULT NULL,
  `last_comment` text COLLATE utf8mb4_unicode_ci,
  `status` enum('PENDING_APPROVAL','APPROVED','REJECTED','BORROWED','RETURNED','CANCELLED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `version` bigint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_contract_borrow_status` (`status`),
  KEY `idx_contract_borrow_expected_return` (`expected_return_date`),
  KEY `idx_contract_borrow_borrower` (`borrower_name`),
  KEY `idx_contract_borrow_status_expected_return` (`status`,`expected_return_date`),
  KEY `idx_contract_borrow_submitted_at` (`submitted_at` DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `contract_borrow_applications`
--

LOCK TABLES `contract_borrow_applications` WRITE;
/*!40000 ALTER TABLE `contract_borrow_applications` DISABLE KEYS */;
/*!40000 ALTER TABLE `contract_borrow_applications` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `contract_borrow_events`
--

DROP TABLE IF EXISTS `contract_borrow_events`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contract_borrow_events` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `application_id` bigint NOT NULL,
  `event_type` enum('SUBMITTED','APPROVED','REJECTED','RETURNED','CANCELLED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status_after` enum('PENDING_APPROVAL','APPROVED','REJECTED','BORROWED','RETURNED','CANCELLED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `actor_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `comment` text COLLATE utf8mb4_unicode_ci,
  `created_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_contract_borrow_events_application` (`application_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `contract_borrow_events`
--

LOCK TABLES `contract_borrow_events` WRITE;
/*!40000 ALTER TABLE `contract_borrow_events` DISABLE KEYS */;
/*!40000 ALTER TABLE `contract_borrow_events` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `customer_predictions`
--

DROP TABLE IF EXISTS `customer_predictions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `customer_predictions` (
  `avg_budget` decimal(14,2) DEFAULT NULL,
  `confidence` decimal(3,2) DEFAULT NULL,
  `frequency` int DEFAULT NULL,
  `opportunity_score` int DEFAULT NULL,
  `predicted_budget_max` decimal(14,2) DEFAULT NULL,
  `predicted_budget_min` decimal(14,2) DEFAULT NULL,
  `converted_project_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `last_computed_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `predicted_window` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `cycle_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `industry` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `predicted_category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `purchaser_hash` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `period_months` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `region` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `sales_rep` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `evidence_record_ids` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `main_categories` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `purchaser_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reasoning_summary` text COLLATE utf8mb4_unicode_ci,
  `status` enum('WATCH','RECOMMEND','CONVERTED','CANCELLED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_cp_purchaser_hash` (`purchaser_hash`),
  KEY `idx_cp_status` (`status`),
  KEY `idx_cp_opportunity_score` (`opportunity_score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `customer_predictions`
--

LOCK TABLES `customer_predictions` WRITE;
/*!40000 ALTER TABLE `customer_predictions` DISABLE KEYS */;
/*!40000 ALTER TABLE `customer_predictions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `dimension_scores`
--

DROP TABLE IF EXISTS `dimension_scores`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `dimension_scores` (
  `score` int DEFAULT NULL,
  `weight` decimal(38,2) DEFAULT NULL,
  `analysis_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `dimension_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `comments` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `idx_dimension_analysis` (`analysis_id`),
  KEY `idx_dimension_name` (`dimension_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `dimension_scores`
--

LOCK TABLES `dimension_scores` WRITE;
/*!40000 ALTER TABLE `dimension_scores` DISABLE KEYS */;
/*!40000 ALTER TABLE `dimension_scores` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `document_archive_records`
--

DROP TABLE IF EXISTS `document_archive_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_archive_records` (
  `archived_at` datetime(6) NOT NULL,
  `archived_by` bigint DEFAULT NULL,
  `export_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `structure_id` bigint NOT NULL,
  `archive_reason` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `archived_by_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_document_archive_project` (`project_id`),
  KEY `idx_document_archive_structure` (`structure_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `document_archive_records`
--

LOCK TABLES `document_archive_records` WRITE;
/*!40000 ALTER TABLE `document_archive_records` DISABLE KEYS */;
/*!40000 ALTER TABLE `document_archive_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `document_assemblies`
--

DROP TABLE IF EXISTS `document_assemblies`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_assemblies` (
  `assembled_at` datetime(6) NOT NULL,
  `assembled_by` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `template_id` bigint DEFAULT NULL,
  `assembled_content` text COLLATE utf8mb4_unicode_ci,
  `variables` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `idx_assembly_project` (`project_id`),
  KEY `idx_assembly_template` (`template_id`),
  KEY `idx_assembly_project_template` (`project_id`,`template_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `document_assemblies`
--

LOCK TABLES `document_assemblies` WRITE;
/*!40000 ALTER TABLE `document_assemblies` DISABLE KEYS */;
/*!40000 ALTER TABLE `document_assemblies` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `document_assignments`
--

DROP TABLE IF EXISTS `document_assignments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_assignments` (
  `due_date` date DEFAULT NULL,
  `assigned_by` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `section_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `owner` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_px8r2avjc26q8jot3arnlm7jt` (`section_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `document_assignments`
--

LOCK TABLES `document_assignments` WRITE;
/*!40000 ALTER TABLE `document_assignments` DISABLE KEYS */;
/*!40000 ALTER TABLE `document_assignments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `document_export_files`
--

DROP TABLE IF EXISTS `document_export_files`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_export_files` (
  `export_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_2yxrqfdw37mkbsjr8cp6sqek8` (`export_id`),
  KEY `idx_document_export_file_export` (`export_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `document_export_files`
--

LOCK TABLES `document_export_files` WRITE;
/*!40000 ALTER TABLE `document_export_files` DISABLE KEYS */;
/*!40000 ALTER TABLE `document_export_files` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `document_exports`
--

DROP TABLE IF EXISTS `document_exports`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_exports` (
  `exported_at` datetime(6) NOT NULL,
  `exported_by` bigint DEFAULT NULL,
  `file_size` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `structure_id` bigint NOT NULL,
  `content_type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `exported_by_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `format` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `project_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_document_export_project` (`project_id`),
  KEY `idx_document_export_structure` (`structure_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `document_exports`
--

LOCK TABLES `document_exports` WRITE;
/*!40000 ALTER TABLE `document_exports` DISABLE KEYS */;
/*!40000 ALTER TABLE `document_exports` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `document_locks`
--

DROP TABLE IF EXISTS `document_locks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_locks` (
  `locked` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `locked_at` datetime(6) DEFAULT NULL,
  `locked_by` bigint DEFAULT NULL,
  `project_id` bigint NOT NULL,
  `section_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_qapdeuoy23y462v3ig6kyy9yb` (`section_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `document_locks`
--

LOCK TABLES `document_locks` WRITE;
/*!40000 ALTER TABLE `document_locks` DISABLE KEYS */;
/*!40000 ALTER TABLE `document_locks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `document_reminders`
--

DROP TABLE IF EXISTS `document_reminders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_reminders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `reminded_at` datetime(6) NOT NULL,
  `reminded_by` bigint DEFAULT NULL,
  `section_id` bigint NOT NULL,
  `message` text COLLATE utf8mb4_unicode_ci,
  `recipient` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `document_reminders`
--

LOCK TABLES `document_reminders` WRITE;
/*!40000 ALTER TABLE `document_reminders` DISABLE KEYS */;
/*!40000 ALTER TABLE `document_reminders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `document_sections`
--

DROP TABLE IF EXISTS `document_sections`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_sections` (
  `order_index` int DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL,
  `structure_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `metadata` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `section_type` enum('CHAPTER','SECTION','SUBSECTION','TABLE','IMAGE','ATTACHMENT') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `document_sections`
--

LOCK TABLES `document_sections` WRITE;
/*!40000 ALTER TABLE `document_sections` DISABLE KEYS */;
/*!40000 ALTER TABLE `document_sections` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `document_structures`
--

DROP TABLE IF EXISTS `document_structures`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_structures` (
  `created_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `root_section_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `document_structures`
--

LOCK TABLES `document_structures` WRITE;
/*!40000 ALTER TABLE `document_structures` DISABLE KEYS */;
/*!40000 ALTER TABLE `document_structures` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `document_versions`
--

DROP TABLE IF EXISTS `document_versions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `document_versions` (
  `is_current` bit(1) DEFAULT NULL,
  `version_number` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `change_summary` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci,
  `document_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_path` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_document_version_project_id` (`project_id`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_project_current` (`project_id`,`is_current`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `document_versions`
--

LOCK TABLES `document_versions` WRITE;
/*!40000 ALTER TABLE `document_versions` DISABLE KEYS */;
/*!40000 ALTER TABLE `document_versions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `email_verification_tokens`
--

DROP TABLE IF EXISTS `email_verification_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `email_verification_tokens` (
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `verified_at` datetime(6) DEFAULT NULL,
  `token` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_ewmvysc7e9y6uy7og2c21axa9` (`token`),
  KEY `idx_email_verify_token` (`token`),
  KEY `idx_email_verify_user` (`user_id`),
  CONSTRAINT `FKi1c4mmamlb8keqt74k4lrtwhc` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `email_verification_tokens`
--

LOCK TABLES `email_verification_tokens` WRITE;
/*!40000 ALTER TABLE `email_verification_tokens` DISABLE KEYS */;
/*!40000 ALTER TABLE `email_verification_tokens` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `expense_approval_records`
--

DROP TABLE IF EXISTS `expense_approval_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `expense_approval_records` (
  `acted_at` datetime(6) NOT NULL,
  `expense_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `approver` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `comment` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `result` enum('APPROVED','REJECTED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `expense_approval_records`
--

LOCK TABLES `expense_approval_records` WRITE;
/*!40000 ALTER TABLE `expense_approval_records` DISABLE KEYS */;
INSERT INTO `expense_approval_records` VALUES ('2026-04-22 14:01:27.742765',1,1,'uat_admin_685142','UAT approval','APPROVED');
/*!40000 ALTER TABLE `expense_approval_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `expense_payment_records`
--

DROP TABLE IF EXISTS `expense_payment_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `expense_payment_records` (
  `amount` decimal(19,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `expense_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `paid_at` datetime(6) NOT NULL,
  `payment_method` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `paid_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payment_reference` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `expense_payment_records`
--

LOCK TABLES `expense_payment_records` WRITE;
/*!40000 ALTER TABLE `expense_payment_records` DISABLE KEYS */;
/*!40000 ALTER TABLE `expense_payment_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `expenses`
--

DROP TABLE IF EXISTS `expenses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `expenses` (
  `amount` decimal(19,2) NOT NULL,
  `date` date NOT NULL,
  `expected_return_date` date DEFAULT NULL,
  `approved_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `last_return_reminder_at` datetime(6) DEFAULT NULL,
  `project_id` bigint NOT NULL,
  `return_confirmed_at` datetime(6) DEFAULT NULL,
  `return_requested_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `approved_by` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expense_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `approval_comment` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `return_comment` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category` enum('MATERIAL','LABOR','EQUIPMENT','TRANSPORTATION','SUBCONTRACTING','OVERHEAD','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('PENDING_APPROVAL','APPROVED','REJECTED','PAID','RETURN_REQUESTED','RETURNED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `expenses`
--

LOCK TABLES `expenses` WRITE;
/*!40000 ALTER TABLE `expenses` DISABLE KEYS */;
INSERT INTO `expenses` VALUES (1200.00,'2026-03-10',NULL,'2026-04-22 14:01:27.741080','2026-04-22 14:01:27.678713',1,NULL,1,'2026-04-22 14:01:27.849980','2026-04-22 14:01:27.813358','2026-04-22 14:01:27.850394','uat_admin_685142','uat_admin_685142','保证金','UAT approval','UAT expense','Confirm return','MATERIAL','RETURNED'),(1888.88,'2025-03-18',NULL,NULL,'2026-04-22 14:01:33.108229',2,NULL,2,NULL,NULL,'2026-04-22 14:01:33.108237',NULL,'Commercial Admin','差旅费',NULL,'COMM 费用 1776837692353',NULL,'TRANSPORTATION','PENDING_APPROVAL'),(1888.88,'2025-03-18',NULL,NULL,'2026-04-22 14:01:37.159093',3,NULL,3,NULL,NULL,'2026-04-22 14:01:37.159100',NULL,'Commercial Detail Admin','差旅费',NULL,'COMM 费用 1776837696621',NULL,'TRANSPORTATION','PENDING_APPROVAL');
/*!40000 ALTER TABLE `expenses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `export_tasks`
--

DROP TABLE IF EXISTS `export_tasks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `export_tasks` (
  `progress` int DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint NOT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `file_size` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `data_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `export_params` text COLLATE utf8mb4_unicode_ci,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `export_type` enum('EXCEL','PDF') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('PENDING','PROCESSING','COMPLETED','FAILED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_export_user` (`created_by`),
  KEY `idx_export_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `export_tasks`
--

LOCK TABLES `export_tasks` WRITE;
/*!40000 ALTER TABLE `export_tasks` DISABLE KEYS */;
/*!40000 ALTER TABLE `export_tasks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `fees`
--

DROP TABLE IF EXISTS `fees`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `fees` (
  `amount` decimal(19,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `fee_date` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `payment_date` datetime(6) DEFAULT NULL,
  `project_id` bigint NOT NULL,
  `return_date` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `paid_by` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `return_to` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remarks` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `fee_type` enum('BID_BOND','SERVICE_FEE','DOCUMENT_FEE','TRAVEL_FEE','NOTARY_FEE','OTHER_FEE') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('PENDING','PAID','RETURNED','CANCELLED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_fee_project` (`project_id`),
  KEY `idx_fee_status` (`status`),
  KEY `idx_fee_type` (`fee_type`),
  KEY `idx_fee_project_status` (`project_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `fees`
--

LOCK TABLES `fees` WRITE;
/*!40000 ALTER TABLE `fees` DISABLE KEYS */;
/*!40000 ALTER TABLE `fees` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `script` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flyway_schema_history`
--

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'73','full schema baseline','SQL_BASELINE','B73__full_schema_baseline.sql',-218674639,'xiyu_user','2026-04-22 06:01:03',4810,1),(2,'74','contract borrow schema','SQL','V74__contract_borrow_schema.sql',-618069106,'xiyu_user','2026-04-22 06:01:03',88,1),(3,'75','tender search dimensions','SQL','V75__tender_search_dimensions.sql',1302395489,'xiyu_user','2026-04-22 06:01:03',332,1),(4,'76','project customer type dimension','SQL','V76__project_customer_type_dimension.sql',2061555806,'xiyu_user','2026-04-22 06:01:03',58,1),(5,'77','contract borrow and customer type indexes','SQL','V77__contract_borrow_and_customer_type_indexes.sql',35168608,'xiyu_user','2026-04-22 06:01:03',95,1),(6,'78','tender normalized search indexes','SQL','V78__tender_normalized_search_indexes.sql',1580620560,'xiyu_user','2026-04-22 06:01:04',394,1),(7,'79','tender trigram search indexes','SQL','V79__tender_trigram_search_indexes.sql',-1613408014,'xiyu_user','2026-04-22 06:01:04',1,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `historical_project_snapshots`
--

DROP TABLE IF EXISTS `historical_project_snapshots`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `historical_project_snapshots` (
  `archive_record_id` bigint NOT NULL,
  `captured_at` datetime(6) NOT NULL,
  `export_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `project_name` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `archive_summary` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `customer_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `document_snapshot_text` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_line` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `recommended_tags` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_c3q79d6kv4gwat3my93qyaewn` (`archive_record_id`),
  KEY `idx_history_snapshot_project` (`project_id`),
  KEY `idx_history_snapshot_archive` (`archive_record_id`),
  KEY `idx_history_snapshot_export` (`export_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `historical_project_snapshots`
--

LOCK TABLES `historical_project_snapshots` WRITE;
/*!40000 ALTER TABLE `historical_project_snapshots` DISABLE KEYS */;
/*!40000 ALTER TABLE `historical_project_snapshots` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `password_reset_tokens`
--

DROP TABLE IF EXISTS `password_reset_tokens`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `password_reset_tokens` (
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `used_at` datetime(6) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `token` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_71lqwbwtklmljk3qlsugr1mig` (`token`),
  KEY `idx_password_reset_token` (`token`),
  KEY `idx_password_reset_user` (`user_id`),
  KEY `idx_password_reset_expires` (`expires_at`),
  CONSTRAINT `FKk3ndxg5xp6v7wd4gjyusp15gq` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `password_reset_tokens`
--

LOCK TABLES `password_reset_tokens` WRITE;
/*!40000 ALTER TABLE `password_reset_tokens` DISABLE KEYS */;
/*!40000 ALTER TABLE `password_reset_tokens` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `platform_accounts`
--

DROP TABLE IF EXISTS `platform_accounts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `platform_accounts` (
  `return_count` int DEFAULT NULL,
  `borrowed_at` datetime(6) DEFAULT NULL,
  `borrowed_by` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `due_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `account_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `platform_type` enum('GOV_PROCUREMENT','BIDDING_PLATFORM','CONSTRUCTION_PLATFORM','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('AVAILABLE','IN_USE','MAINTENANCE','DISABLED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_14e3v80s7wywpcjk713rniikd` (`username`),
  KEY `idx_platform_username` (`username`),
  KEY `idx_platform_status` (`status`),
  KEY `idx_platform_type` (`platform_type`),
  KEY `idx_platform_borrowed_by` (`borrowed_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `platform_accounts`
--

LOCK TABLES `platform_accounts` WRITE;
/*!40000 ALTER TABLE `platform_accounts` DISABLE KEYS */;
/*!40000 ALTER TABLE `platform_accounts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_documents`
--

DROP TABLE IF EXISTS `project_documents`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_documents` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `linked_entity_id` bigint DEFAULT NULL,
  `project_id` bigint NOT NULL,
  `uploader_id` bigint DEFAULT NULL,
  `file_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `document_category` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_size` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `linked_entity_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `uploader_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_documents`
--

LOCK TABLES `project_documents` WRITE;
/*!40000 ALTER TABLE `project_documents` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_documents` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_group_members`
--

DROP TABLE IF EXISTS `project_group_members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_group_members` (
  `project_group_id` bigint NOT NULL,
  `user_id` bigint DEFAULT NULL,
  KEY `FKr0fmvpgokjn2mpptv7qat6shh` (`project_group_id`),
  CONSTRAINT `FKr0fmvpgokjn2mpptv7qat6shh` FOREIGN KEY (`project_group_id`) REFERENCES `project_groups` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_group_members`
--

LOCK TABLES `project_group_members` WRITE;
/*!40000 ALTER TABLE `project_group_members` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_group_members` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_group_projects`
--

DROP TABLE IF EXISTS `project_group_projects`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_group_projects` (
  `project_group_id` bigint NOT NULL,
  `project_id` bigint DEFAULT NULL,
  KEY `FKq6hhbhmbsib75deebi5e5tth4` (`project_group_id`),
  CONSTRAINT `FKq6hhbhmbsib75deebi5e5tth4` FOREIGN KEY (`project_group_id`) REFERENCES `project_groups` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_group_projects`
--

LOCK TABLES `project_group_projects` WRITE;
/*!40000 ALTER TABLE `project_group_projects` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_group_projects` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_group_role_access`
--

DROP TABLE IF EXISTS `project_group_role_access`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_group_role_access` (
  `project_group_id` bigint NOT NULL,
  `role_code` enum('ADMIN','MANAGER','STAFF') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  KEY `FKao9yo4xloylqq0iphrna0rm03` (`project_group_id`),
  CONSTRAINT `FKao9yo4xloylqq0iphrna0rm03` FOREIGN KEY (`project_group_id`) REFERENCES `project_groups` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_group_role_access`
--

LOCK TABLES `project_group_role_access` WRITE;
/*!40000 ALTER TABLE `project_group_role_access` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_group_role_access` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_groups`
--

DROP TABLE IF EXISTS `project_groups`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_groups` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `manager_user_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `group_code` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `group_name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `visibility` enum('ALL','MEMBERS','MANAGER','CUSTOM') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_n1sq2ky02kvteuxw8r5oo9q7e` (`group_code`),
  KEY `idx_project_group_manager` (`manager_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_groups`
--

LOCK TABLES `project_groups` WRITE;
/*!40000 ALTER TABLE `project_groups` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_groups` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_quality_checks`
--

DROP TABLE IF EXISTS `project_quality_checks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_quality_checks` (
  `empty` bit(1) NOT NULL,
  `checked_at` datetime(6) NOT NULL,
  `document_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `document_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `summary` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_quality_checks`
--

LOCK TABLES `project_quality_checks` WRITE;
/*!40000 ALTER TABLE `project_quality_checks` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_quality_checks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_quality_issues`
--

DROP TABLE IF EXISTS `project_quality_issues`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_quality_issues` (
  `adopted` bit(1) NOT NULL,
  `ignored` bit(1) NOT NULL,
  `check_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `location_label` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `original_text` text COLLATE utf8mb4_unicode_ci,
  `suggestion_text` text COLLATE utf8mb4_unicode_ci,
  `type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_quality_issues`
--

LOCK TABLES `project_quality_issues` WRITE;
/*!40000 ALTER TABLE `project_quality_issues` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_quality_issues` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_reminders`
--

DROP TABLE IF EXISTS `project_reminders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_reminders` (
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `remind_at` datetime(6) NOT NULL,
  `created_by_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `message` text COLLATE utf8mb4_unicode_ci,
  `recipient` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_reminders`
--

LOCK TABLES `project_reminders` WRITE;
/*!40000 ALTER TABLE `project_reminders` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_reminders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_score_drafts`
--

DROP TABLE IF EXISTS `project_score_drafts`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_score_drafts` (
  `source_page` int DEFAULT NULL,
  `source_row_index` int NOT NULL,
  `source_table_index` int NOT NULL,
  `assignee_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `due_date` datetime(6) DEFAULT NULL,
  `generated_task_id` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `category` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `task_action` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `assignee_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `score_value_text` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `generated_task_description` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `generated_task_title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `score_item_title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `score_rule_text` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `skip_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `suggested_deliverables` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('DRAFT','READY','SKIPPED','GENERATED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_score_drafts`
--

LOCK TABLES `project_score_drafts` WRITE;
/*!40000 ALTER TABLE `project_score_drafts` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_score_drafts` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_score_previews`
--

DROP TABLE IF EXISTS `project_score_previews`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_score_previews` (
  `budget` decimal(19,2) DEFAULT NULL,
  `win_score` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint DEFAULT NULL,
  `tender_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `win_level` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `industry` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payload_json` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `project_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `tags_json` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `idx_score_preview_project` (`project_id`,`created_at`),
  KEY `idx_score_preview_tender` (`tender_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_score_previews`
--

LOCK TABLES `project_score_previews` WRITE;
/*!40000 ALTER TABLE `project_score_previews` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_score_previews` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_share_links`
--

DROP TABLE IF EXISTS `project_share_links`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_share_links` (
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `created_by_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `token` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_b8ltlmqyet5wpswegfyihvluk` (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_share_links`
--

LOCK TABLES `project_share_links` WRITE;
/*!40000 ALTER TABLE `project_share_links` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_share_links` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_team_members`
--

DROP TABLE IF EXISTS `project_team_members`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_team_members` (
  `member_id` bigint DEFAULT NULL,
  `project_id` bigint NOT NULL,
  KEY `FKrplt19ljycvlrk9fy72yep75e` (`project_id`),
  CONSTRAINT `FKrplt19ljycvlrk9fy72yep75e` FOREIGN KEY (`project_id`) REFERENCES `projects` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_team_members`
--

LOCK TABLES `project_team_members` WRITE;
/*!40000 ALTER TABLE `project_team_members` DISABLE KEYS */;
INSERT INTO `project_team_members` VALUES (2,1),(3,2),(4,3);
/*!40000 ALTER TABLE `project_team_members` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `projects`
--

DROP TABLE IF EXISTS `projects`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `projects` (
  `budget` decimal(14,2) DEFAULT NULL,
  `deadline` date DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `end_date` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `manager_id` bigint NOT NULL,
  `start_date` datetime(6) DEFAULT NULL,
  `tender_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `industry` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `customer_manager` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `customer_manager_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `region` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_customer_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_module` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_opportunity_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tags_json` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `ai_analysis_json` text COLLATE utf8mb4_unicode_ci,
  `competitor_analysis_json` text COLLATE utf8mb4_unicode_ci,
  `customer` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `platform` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` text COLLATE utf8mb4_unicode_ci,
  `source_customer` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source_reasoning_summary` text COLLATE utf8mb4_unicode_ci,
  `tasks_json` text COLLATE utf8mb4_unicode_ci,
  `status` enum('INITIATED','PREPARING','REVIEWING','SEALING','BIDDING','ARCHIVED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `customer_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_project_status` (`status`),
  KEY `idx_project_manager` (`manager_id`),
  KEY `idx_project_tender` (`tender_id`),
  KEY `idx_project_dates` (`start_date`,`end_date`),
  KEY `idx_project_customer_type` (`customer_type`),
  KEY `idx_project_customer_type_status` (`customer_type`,`status`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `projects`
--

LOCK TABLES `projects` WRITE;
/*!40000 ALTER TABLE `projects` DISABLE KEYS */;
INSERT INTO `projects` VALUES (NULL,NULL,'2026-04-22 14:01:27.139708','2026-05-22 06:01:27.094000',1,2,'2026-04-22 06:01:27.094000',1,'2026-04-22 14:01:27.139724',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'UAT 项目 685142',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'BIDDING',NULL),(NULL,NULL,'2026-04-22 14:01:33.040731','2026-05-02 14:01:33.000000',2,3,'2026-04-22 14:01:33.000000',2,'2026-04-22 14:01:33.040743',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'COMM 项目 1776837692353',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'BIDDING',NULL),(NULL,NULL,'2026-04-22 14:01:37.106656','2026-05-02 14:01:37.000000',3,4,'2026-04-22 14:01:37.000000',3,'2026-04-22 14:01:37.106666',NULL,NULL,NULL,NULL,NULL,NULL,NULL,'COMM 项目 1776837696621',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'BIDDING',NULL);
/*!40000 ALTER TABLE `projects` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qualification_attachments`
--

DROP TABLE IF EXISTS `qualification_attachments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qualification_attachments` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `qualification_id` bigint NOT NULL,
  `uploaded_at` datetime(6) NOT NULL,
  `file_url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qualification_attachments`
--

LOCK TABLES `qualification_attachments` WRITE;
/*!40000 ALTER TABLE `qualification_attachments` DISABLE KEYS */;
INSERT INTO `qualification_attachments` VALUES ('2026-04-22 14:01:27.449099',1,1,'2026-04-22 14:01:27.433143','/uat/qualification.pdf','UAT 资质 685142.pdf');
/*!40000 ALTER TABLE `qualification_attachments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qualification_loan_records`
--

DROP TABLE IF EXISTS `qualification_loan_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qualification_loan_records` (
  `expected_return_date` date DEFAULT NULL,
  `borrowed_at` datetime(6) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `qualification_id` bigint NOT NULL,
  `returned_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `project_id` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `borrower` varchar(120) COLLATE utf8mb4_unicode_ci NOT NULL,
  `department` varchar(120) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `return_remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `purpose` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('AVAILABLE','BORROWED','RETURNED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qualification_loan_records`
--

LOCK TABLES `qualification_loan_records` WRITE;
/*!40000 ALTER TABLE `qualification_loan_records` DISABLE KEYS */;
/*!40000 ALTER TABLE `qualification_loan_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `qualifications`
--

DROP TABLE IF EXISTS `qualifications`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `qualifications` (
  `expiry_date` date DEFAULT NULL,
  `issue_date` date DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `file_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `level` enum('FIRST','SECOND','THIRD','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` enum('CONSTRUCTION','DESIGN','SERVICE','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `qualifications`
--

LOCK TABLES `qualifications` WRITE;
/*!40000 ALTER TABLE `qualifications` DISABLE KEYS */;
/*!40000 ALTER TABLE `qualifications` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `refresh_sessions`
--

DROP TABLE IF EXISTS `refresh_sessions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `refresh_sessions` (
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `last_seen_at` datetime(6) DEFAULT NULL,
  `revoked_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `user_id` bigint NOT NULL,
  `ip_address` varchar(45) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `token_hash` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL,
  `user_agent` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `device_info` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_8dm8ib9n3bwiy2fjx1411w08d` (`token_hash`),
  KEY `FK9ndfh1op1iy3xuqyi6gxkseg0` (`user_id`),
  CONSTRAINT `FK9ndfh1op1iy3xuqyi6gxkseg0` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `refresh_sessions`
--

LOCK TABLES `refresh_sessions` WRITE;
/*!40000 ALTER TABLE `refresh_sessions` DISABLE KEYS */;
INSERT INTO `refresh_sessions` VALUES ('2026-04-22 14:01:26.851669','2026-04-29 14:01:26.850174',1,NULL,NULL,'2026-04-22 14:01:26.851679',2,NULL,'72f85c447c4e14f3fdd4e10ad2c59c2a63a2cddf7a2d56d790429b2be80333a4',NULL,NULL),('2026-04-22 14:01:32.966420','2026-04-29 14:01:32.966235',2,NULL,NULL,'2026-04-22 14:01:32.966427',3,NULL,'50d96909fe3a5f3f11d62ce863133b50992f052065e58fafedf893b6c01dc8a2',NULL,NULL),('2026-04-22 14:01:37.032380','2026-04-29 14:01:37.032220',3,NULL,NULL,'2026-04-22 14:01:37.032385',4,NULL,'4ea1036486aedb5f77068250cc37109a1921ab591b88abd9a4faff2587da96a1',NULL,NULL);
/*!40000 ALTER TABLE `refresh_sessions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `roi_analyses`
--

DROP TABLE IF EXISTS `roi_analyses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roi_analyses` (
  `estimated_cost` decimal(19,2) DEFAULT NULL,
  `estimated_profit` decimal(19,2) DEFAULT NULL,
  `estimated_revenue` decimal(19,2) DEFAULT NULL,
  `payback_period_months` int DEFAULT NULL,
  `roi_percentage` decimal(10,2) DEFAULT NULL,
  `analysis_date` datetime(6) DEFAULT NULL,
  `created_by` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `assumptions` text COLLATE utf8mb4_unicode_ci,
  `risk_factors` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `idx_roi_project` (`project_id`),
  KEY `idx_roi_analysis_date` (`analysis_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `roi_analyses`
--

LOCK TABLES `roi_analyses` WRITE;
/*!40000 ALTER TABLE `roi_analyses` DISABLE KEYS */;
/*!40000 ALTER TABLE `roi_analyses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `roles`
--

DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `roles` (
  `enabled` bit(1) NOT NULL,
  `is_system` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `data_scope` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `allowed_depts` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `allowed_projects` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `menu_permissions` varchar(4000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_ch1113horj4qr56f91omojv8` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `roles`
--

LOCK TABLES `roles` WRITE;
/*!40000 ALTER TABLE `roles` DISABLE KEYS */;
INSERT INTO `roles` VALUES (0x01,0x01,'2026-04-22 06:01:03.199292',1,'2026-04-22 06:01:03.199292','all','admin','管理员',NULL,NULL,'all','系统管理员，拥有所有权限'),(0x01,0x01,'2026-04-22 06:01:03.199292',2,'2026-04-22 06:01:03.199292','dept','manager','经理',NULL,NULL,'dashboard,bidding,project,knowledge,resource,analytics,settings','部门经理，可查看项目、知识库、资源与分析数据'),(0x01,0x01,'2026-04-22 06:01:03.199292',3,'2026-04-22 06:01:03.199292','self','staff','员工',NULL,NULL,'dashboard,bidding,project,knowledge,resource','业务人员，可查看工作台、标讯、项目、知识库与资源');
/*!40000 ALTER TABLE `roles` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `score_analyses`
--

DROP TABLE IF EXISTS `score_analyses`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `score_analyses` (
  `is_ai_generated` bit(1) DEFAULT NULL,
  `overall_score` int DEFAULT NULL,
  `analysis_date` datetime(6) DEFAULT NULL,
  `analyst_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `summary` text COLLATE utf8mb4_unicode_ci,
  `risk_level` enum('LOW','MEDIUM','HIGH') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_score_analysis_project` (`project_id`),
  KEY `idx_score_analysis_date` (`analysis_date`),
  KEY `idx_score_analysis_risk` (`risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `score_analyses`
--

LOCK TABLES `score_analyses` WRITE;
/*!40000 ALTER TABLE `score_analyses` DISABLE KEYS */;
/*!40000 ALTER TABLE `score_analyses` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `system_settings`
--

DROP TABLE IF EXISTS `system_settings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_settings` (
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) NOT NULL,
  `config_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `payload_json` text COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_cv6i1lu658ukkwnk4cuvj0ow0` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `system_settings`
--

LOCK TABLES `system_settings` WRITE;
/*!40000 ALTER TABLE `system_settings` DISABLE KEYS */;
/*!40000 ALTER TABLE `system_settings` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `task_deliverables`
--

DROP TABLE IF EXISTS `task_deliverables`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `task_deliverables` (
  `version` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `task_id` bigint NOT NULL,
  `uploader_id` bigint DEFAULT NULL,
  `file_size` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_type` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `uploader_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `storage_path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `storage_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `deliverable_type` enum('DOCUMENT','QUALIFICATION','TECHNICAL','QUOTATION','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_th3pfdl5atat5ggp5w92f03x` (`storage_key`),
  KEY `idx_task_del_task_id` (`task_id`),
  KEY `idx_task_del_type` (`deliverable_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `task_deliverables`
--

LOCK TABLES `task_deliverables` WRITE;
/*!40000 ALTER TABLE `task_deliverables` DISABLE KEYS */;
/*!40000 ALTER TABLE `task_deliverables` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tasks`
--

DROP TABLE IF EXISTS `tasks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tasks` (
  `assignee_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `due_date` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `assignee_role_code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assignee_dept_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assignee_dept_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assignee_role_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `title` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `priority` enum('LOW','MEDIUM','HIGH','URGENT') COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` enum('TODO','IN_PROGRESS','REVIEW','COMPLETED','CANCELLED') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tasks`
--

LOCK TABLES `tasks` WRITE;
/*!40000 ALTER TABLE `tasks` DISABLE KEYS */;
/*!40000 ALTER TABLE `tasks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `template_download_records`
--

DROP TABLE IF EXISTS `template_download_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `template_download_records` (
  `downloaded_at` datetime(6) NOT NULL,
  `downloaded_by` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK6hyrt9u60k493mra3vqtg13b5` (`template_id`),
  CONSTRAINT `FK6hyrt9u60k493mra3vqtg13b5` FOREIGN KEY (`template_id`) REFERENCES `templates` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `template_download_records`
--

LOCK TABLES `template_download_records` WRITE;
/*!40000 ALTER TABLE `template_download_records` DISABLE KEYS */;
/*!40000 ALTER TABLE `template_download_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `template_tags`
--

DROP TABLE IF EXISTS `template_tags`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `template_tags` (
  `template_id` bigint NOT NULL,
  `tag` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  KEY `FKb690bd7lptenw09h0u23iwila` (`template_id`),
  CONSTRAINT `FKb690bd7lptenw09h0u23iwila` FOREIGN KEY (`template_id`) REFERENCES `templates` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `template_tags`
--

LOCK TABLES `template_tags` WRITE;
/*!40000 ALTER TABLE `template_tags` DISABLE KEYS */;
INSERT INTO `template_tags` VALUES (1,'uat'),(1,'release');
/*!40000 ALTER TABLE `template_tags` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `template_use_records`
--

DROP TABLE IF EXISTS `template_use_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `template_use_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `project_id` bigint DEFAULT NULL,
  `template_id` bigint NOT NULL,
  `used_at` datetime(6) NOT NULL,
  `used_by` bigint DEFAULT NULL,
  `applied_options` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `doc_type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `document_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK3ia252hoqeioht3vqvxqgc05s` (`template_id`),
  CONSTRAINT `FK3ia252hoqeioht3vqvxqgc05s` FOREIGN KEY (`template_id`) REFERENCES `templates` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `template_use_records`
--

LOCK TABLES `template_use_records` WRITE;
/*!40000 ALTER TABLE `template_use_records` DISABLE KEYS */;
/*!40000 ALTER TABLE `template_use_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `template_versions`
--

DROP TABLE IF EXISTS `template_versions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `template_versions` (
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `template_id` bigint NOT NULL,
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `snapshot_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `version` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9gy13bl9n6n45605pb93htaxx` (`template_id`),
  CONSTRAINT `FK9gy13bl9n6n45605pb93htaxx` FOREIGN KEY (`template_id`) REFERENCES `templates` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `template_versions`
--

LOCK TABLES `template_versions` WRITE;
/*!40000 ALTER TABLE `template_versions` DISABLE KEYS */;
INSERT INTO `template_versions` VALUES ('2026-04-22 14:01:27.559721',2,1,1,'初始版本','UAT 模板 685142','1.0');
/*!40000 ALTER TABLE `template_versions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `templates`
--

DROP TABLE IF EXISTS `templates`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `templates` (
  `created_at` datetime(6) NOT NULL,
  `created_by` bigint DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `description` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `current_version` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `document_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_size` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `file_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `industry` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `product_type` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category` enum('TECHNICAL','COMMERCIAL','LEGAL','QUALIFICATION','CONTRACT','OTHER') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `templates`
--

LOCK TABLES `templates` WRITE;
/*!40000 ALTER TABLE `templates` DISABLE KEYS */;
INSERT INTO `templates` VALUES ('2026-04-22 14:01:27.531078',2,1,'2026-04-22 14:01:27.531087',NULL,'1.0','技术方案','未知','/uat/template.docx','政府','UAT 模板 685142','智慧城市','TECHNICAL');
/*!40000 ALTER TABLE `templates` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tender_assignment_records`
--

DROP TABLE IF EXISTS `tender_assignment_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tender_assignment_records` (
  `assigned_at` datetime(6) NOT NULL,
  `assigned_by_id` bigint DEFAULT NULL,
  `assignee_id` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `tender_id` bigint NOT NULL,
  `assigned_by_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `assignee_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `remark` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tender_assignment_records`
--

LOCK TABLES `tender_assignment_records` WRITE;
/*!40000 ALTER TABLE `tender_assignment_records` DISABLE KEYS */;
/*!40000 ALTER TABLE `tender_assignment_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tenders`
--

DROP TABLE IF EXISTS `tenders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tenders` (
  `ai_score` int DEFAULT NULL,
  `budget` decimal(19,2) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `deadline` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `external_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `source` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `title` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL,
  `original_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `risk_level` enum('LOW','MEDIUM','HIGH') COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` enum('PENDING','TRACKING','BIDDED','ABANDONED') COLLATE utf8mb4_unicode_ci NOT NULL,
  `region` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `industry` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `purchaser_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `purchaser_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `publish_date` date DEFAULT NULL,
  `contact_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `contact_phone` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `tags` text COLLATE utf8mb4_unicode_ci,
  `source_normalized` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `region_normalized` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `industry_normalized` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `purchaser_hash_normalized` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `purchaser_name_normalized` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `search_text_normalized` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_i07ipdk97nopwineqty0816ml` (`external_id`),
  KEY `idx_tender_status` (`status`),
  KEY `idx_tender_source` (`source`),
  KEY `idx_tender_deadline` (`deadline`),
  KEY `idx_tender_ai_score` (`ai_score`),
  KEY `idx_tender_region` (`region`),
  KEY `idx_tender_industry` (`industry`),
  KEY `idx_tender_purchaser_hash` (`purchaser_hash`),
  KEY `idx_tender_status_region_industry` (`status`,`region`,`industry`),
  KEY `idx_tender_source_normalized` (`source_normalized`),
  KEY `idx_tender_region_normalized` (`region_normalized`),
  KEY `idx_tender_industry_normalized` (`industry_normalized`),
  KEY `idx_tender_purchaser_hash_normalized` (`purchaser_hash_normalized`),
  KEY `idx_tender_status_region_industry_normalized` (`status`,`region_normalized`,`industry_normalized`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tenders`
--

LOCK TABLES `tenders` WRITE;
/*!40000 ALTER TABLE `tenders` DISABLE KEYS */;
INSERT INTO `tenders` VALUES (88,1250000.00,'2026-04-22 14:01:27.071285','2026-05-06 06:01:26.999000',1,'2026-04-22 14:01:27.071293',NULL,'UAT','UAT 标讯 685142',NULL,'LOW','TRACKING',NULL,NULL,NULL,NULL,'2026-04-22',NULL,NULL,NULL,'','uat','','','','','uat 标讯 685142      uat'),(87,880000.00,'2026-04-22 14:01:33.008281','2026-05-06 14:01:32.000000',2,'2026-04-22 14:01:33.008290',NULL,'Playwright','COMM 标讯 1776837692353',NULL,'LOW','TRACKING',NULL,NULL,NULL,NULL,'2026-04-22',NULL,NULL,NULL,'','playwright','','','','','comm 标讯 1776837692353      playwright'),(87,880000.00,'2026-04-22 14:01:37.077957','2026-05-06 14:01:37.000000',3,'2026-04-22 14:01:37.077962',NULL,'Playwright','COMM 标讯 1776837696621',NULL,'LOW','TRACKING',NULL,NULL,NULL,NULL,'2026-04-22',NULL,NULL,NULL,'','playwright','','','','','comm 标讯 1776837696621      playwright');
/*!40000 ALTER TABLE `tenders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `email_verified` bit(1) NOT NULL,
  `enabled` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `phone` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `department_code` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `department_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `full_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `username` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `role` enum('ADMIN','MANAGER','STAFF') COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UK_r43af9ap4edm43mmtq01oddj6` (`username`),
  KEY `FKp56c1712k691lhsyewcssf40f` (`role_id`),
  CONSTRAINT `FKp56c1712k691lhsyewcssf40f` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (0x00,0x01,'2026-04-22 14:01:22.615864',1,1,'2026-04-22 14:01:22.615884',NULL,NULL,NULL,'admin@xiyu-local','系统管理员','$2a$10$AlANhhNRaAMu3DacO6PU7ewM/HIwePW0WJLXnToM2ym0bH5gBTDj2','admin','ADMIN'),(0x00,0x01,'2026-04-22 14:01:25.629004',2,1,'2026-04-22 14:01:25.629041',NULL,NULL,NULL,'uat_admin_685142@example.com','Go Live UAT Admin','$2a$10$Mq17yh.eolz9IILQDCik2.b./G0HPGukpkeXLuRu1913iFL0QZoAa','uat_admin_685142','ADMIN'),(0x00,0x01,'2026-04-22 14:01:32.640349',3,1,'2026-04-22 14:01:32.640460',NULL,NULL,NULL,'commercial_admin_1776837692353@example.com','Commercial Admin','$2a$10$RxAuf62yKsMDRALxxBv2hu4kHujtPwEat25MeKQkFXEVAWHbtt40C','commercial_admin_1776837692353','ADMIN'),(0x00,0x01,'2026-04-22 14:01:36.822312',4,1,'2026-04-22 14:01:36.822329',NULL,NULL,NULL,'commercial_detail_1776837696621@example.com','Commercial Detail Admin','$2a$10$i7Jxb2ZvvXuPuM8nL.Tg0.XpBU.aQpOHU.YtYJpXFHKOgMbg8Lu.6','commercial_detail_1776837696621','ADMIN');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Dumping routines for database 'xiyu_bid'
--
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-04-22  6:01:41
