-- U1053: Remove file_size column from performance_attachment

ALTER TABLE performance_attachment
    DROP COLUMN file_size;
