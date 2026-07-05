-- U1137: 回滚平台账户导出白名单种子数据
-- Source: V1137__seed_platform_account_export_whitelist.sql
-- Input: 无
-- Data rollback required: 删除 system_settings 中的导出白名单配置行

DELETE FROM system_settings WHERE config_key = 'platform_account_export_whitelist';
