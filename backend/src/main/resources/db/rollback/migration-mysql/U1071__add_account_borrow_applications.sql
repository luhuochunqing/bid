-- Input: V1071__add_account_borrow_applications.sql
-- U1071: Rollback - drop account_borrow_applications table
-- Gitee Issue IJTGNY: 回滚时先判断表是否存在，安全移除

DROP TABLE IF EXISTS account_borrow_applications;
