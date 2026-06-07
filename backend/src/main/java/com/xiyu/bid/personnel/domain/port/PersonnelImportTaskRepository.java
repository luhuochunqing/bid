package com.xiyu.bid.personnel.domain.port;

import com.xiyu.bid.personnel.domain.model.importtask.PersonnelImportTask;
import java.util.Optional;

/**
 * 人员证书批量导入任务仓储接口（纯核心端口）
 */
public interface PersonnelImportTaskRepository {

    PersonnelImportTask save(PersonnelImportTask task);

    Optional<PersonnelImportTask> findById(Long id);

    Optional<PersonnelImportTask> findByTaskNo(String taskNo);

    PersonnelImportTask updateStatus(Long id, String newStatus);
}
