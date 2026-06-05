package com.xiyu.bid.personnel.application.service;

import com.xiyu.bid.personnel.domain.model.importtask.PersonnelImportTask;
import com.xiyu.bid.personnel.domain.port.PersonnelImportTaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 人员证书批量导入任务应用服务（命令式外壳）
 * 负责任务的创建、状态流转等编排，不承载复杂业务规则
 */
@Service
@RequiredArgsConstructor
public class PersonnelImportTaskAppService {

    private final PersonnelImportTaskRepository importTaskRepository;

    /**
     * 创建一个新的导入任务
     */
    @Transactional
    public PersonnelImportTask createNewTask(Long currentUserId) {
        String taskNo = generateTaskNo();
        PersonnelImportTask newTask = PersonnelImportTask.createNew(taskNo, currentUserId);
        return importTaskRepository.save(newTask);
    }

    private String generateTaskNo() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "IMP-" + datePart + "-" + randomPart;
    }
}
