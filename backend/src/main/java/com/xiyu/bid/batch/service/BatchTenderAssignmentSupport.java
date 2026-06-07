// Input: assignee repository and assignment request context
// Output: tender assignee and assignment record
// Pos: Service/业务支撑层
// 维护声明: 仅维护批量标讯分配的人与记录组装；标讯状态变更和持久化留在应用服务。
package com.xiyu.bid.batch.service;

import com.xiyu.bid.batch.dto.BatchTenderAssignRequest;
import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import com.xiyu.bid.batch.repository.TenderAssignmentRecordRepository;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
class BatchTenderAssignmentSupport {

    private final UserRepository userRepository;
    private final TenderAssignmentRecordRepository assignmentRecordRepository;

    User resolveAssignee(Long assigneeId) {
        User user = userRepository.findById(assigneeId)
                .orElseThrow(() -> new IllegalArgumentException("所选项目负责人不存在或已停用"));
        if (Boolean.FALSE.equals(user.getEnabled())) {
            throw new IllegalArgumentException("所选项目负责人不存在或已停用");
        }
        return user;
    }

    TenderAssignmentRecord buildRecord(Long tenderId, User assignee, BatchTenderAssignRequest request, User currentUser) {
        return TenderAssignmentRecord.builder()
                .tenderId(tenderId)
                .assigneeId(assignee.getId())
                .assigneeName(assignee.getFullName())
                .assignedById(currentUser == null ? null : currentUser.getId())
                .assignedByName(currentUser == null ? "system" : currentUser.getFullName())
                .remark(request.getRemark())
                .build();
    }

    void saveRecords(List<TenderAssignmentRecord> records) {
        if (!records.isEmpty()) {
            assignmentRecordRepository.saveAll(records);
        }
    }
}
