<template>
  <el-card
    v-if="approvalHistory.length > 0 || projectStatus === 'reviewing'"
    class="approval-status-card"
  >
    <template #header>
      <div class="card-title">
        <el-icon><Select /></el-icon>
        <span>审批状态</span>
        <el-tag v-if="currentApproval" :type="getApprovalStatusType(currentApproval.status)" size="small">
          {{ getApprovalStatusText(currentApproval.status) }}
        </el-tag>
      </div>
    </template>

    <div v-if="currentApproval" class="current-approval">
      <div class="approval-info-grid">
        <div class="approval-info-item">
          <span class="label">审批类型</span>
          <span>{{ currentApproval.typeName }}</span>
        </div>
        <div class="approval-info-item">
          <span class="label">申请人</span>
          <span>{{ currentApproval.applicantName }}（{{ currentApproval.applicantDept }}）</span>
        </div>
        <div class="approval-info-item">
          <span class="label">提交时间</span>
          <span>{{ currentApproval.submitTime }}</span>
        </div>
        <div v-if="currentApproval.currentApproverName" class="approval-info-item">
          <span class="label">当前审批人</span>
          <span class="approver">{{ currentApproval.currentApproverName }}</span>
        </div>
      </div>

      <div v-if="currentApproval.status === 'PENDING' && canApproveCurrent" class="approval-actions">
        <el-button type="success" :icon="CircleCheck" @click="$emit('quick-approve')">通过</el-button>
        <el-button type="danger" :icon="CircleClose" @click="$emit('quick-reject')">驳回</el-button>
      </div>
    </div>

    <el-empty v-else description="暂无审批记录" :image-size="60" />
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { CircleCheck, CircleClose, Select } from '@element-plus/icons-vue'

defineEmits(['quick-approve', 'quick-reject'])

const props = defineProps({
  approvalHistory: {
    type: Array,
    default: () => [],
  },
  projectStatus: {
    type: String,
    default: '',
  },
  canApproveCurrent: {
    type: Boolean,
    default: false,
  },
})

const currentApproval = computed(() => props.approvalHistory[0] || null)

function getApprovalStatusType(status) {
  const map = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', CANCELLED: 'info' }
  return map[String(status || '').toUpperCase()] || 'info'
}

function getApprovalStatusText(status) {
  const map = { PENDING: '待审批', APPROVED: '已通过', REJECTED: '已驳回', CANCELLED: '已取消' }
  return map[String(status || '').toUpperCase()] || (status || '未知状态')
}
</script>

<style scoped>
.approval-status-card {
  margin-bottom: 20px;
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-weight: 500;
}

.current-approval {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.approval-info-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.approval-info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px 14px;
  background: #f8fafc;
  border: 1px solid #e5e7eb;
  border-radius: 10px;
}

.approval-info-item .label {
  font-size: 12px;
  color: var(--gray-650);
}

.approval-info-item .approver {
  color: #1d4ed8;
  font-weight: 600;
}

.approval-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
</style>
