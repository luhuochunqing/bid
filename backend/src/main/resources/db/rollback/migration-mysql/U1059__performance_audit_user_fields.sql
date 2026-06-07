-- U1054: Revert audit user fields on performance_record

ALTER TABLE performance_record
    DROP COLUMN updated_by;

ALTER TABLE performance_record
    MODIFY COLUMN created_by BIGINT COMMENT '创建人（用户ID）';
