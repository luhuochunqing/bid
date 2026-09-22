<template>
  <div class="expense-page">
    <ExpenseFiltersBar
      v-model="searchForm"
      :project-options="projectOptions"
      :department-options="departmentOptions"
      :loading="loading"
      @search="handleSearch"
      @reset="handleReset"
    />

    <ExpenseSummaryCards :summary="summary" />

    <ExpenseLedgerTable
      :items="ledgerItems"
      :loading="loading"
      @detail="handleDetail"
      @return="handleReturn"
      @open-apply="showApplyDialog = true"
      @export="handleExport"
    />

    <DepositTrackingTable
      :items="depositItems"
      :overdue-count="overdueCount"
      @remind="openRemindDialog"
      @confirm-return="handleConfirmReturn"
    />

    <ApprovalRecordsTable
      :items="approvalItems"
      @approve="openApprovalDialog"
    />

    <el-dialog v-model="showApplyDialog" title="费用申请" width="500px">
      <el-form :model="applyForm" label-width="100px">
        <el-form-item label="费用类型">
          <el-select v-model="applyForm.type">
            <el-option label="保证金" value="保证金" />
            <el-option label="标书费" value="标书费" />
            <el-option label="差旅费" value="差旅费" />
            <el-option label="材料费" value="材料费" />
            <el-option label="其他" value="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="关联项目">
          <el-select v-model="applyForm.projectId" placeholder="请选择">
            <el-option
              v-for="project in projectOptions"
              :key="project.id"
              :label="project.name"
              :value="project.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="金额">
          <el-input-number v-model="applyForm.amount" :min="0" :precision="2" />
        </el-form-item>
        <el-form-item label="申请说明">
          <el-input v-model="applyForm.remark" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showApplyDialog = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitApply">提交申请</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showRemindDialog" title="发送保证金归还提醒" width="500px">
      <div v-if="currentRemindItem">
        <el-descriptions :column="1" border>
          <el-descriptions-item label="项目">{{ currentRemindItem.project || currentRemindItem.projectName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="部门">{{ currentRemindItem.departmentName || currentRemindItem.department || '-' }}</el-descriptions-item>
          <el-descriptions-item label="金额">¥{{ currentRemindItem.amount.toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="应退日期">{{ currentRemindItem.expectedReturn }}</el-descriptions-item>
        </el-descriptions>
      </div>
      <template #footer>
        <el-button @click="showRemindDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmRemind">发送提醒</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showApprovalDialog" title="费用审批" width="500px">
      <el-form :model="approvalForm" label-width="80px">
        <el-form-item label="审批结果">
          <el-radio-group v-model="approvalForm.result">
            <el-radio value="approved">通过</el-radio>
            <el-radio value="rejected">拒绝</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="审批意见">
          <el-input v-model="approvalForm.comment" type="textarea" :rows="3" placeholder="请输入审批意见" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showApprovalDialog = false">取消</el-button>
        <el-button type="primary" @click="confirmApproval">提交审批</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="showDetailDialog" title="费用详情" width="620px">
      <el-descriptions v-if="currentExpenseDetail" :column="1" border>
        <el-descriptions-item label="项目">{{ currentExpenseDetail.projectName || currentExpenseDetail.project }}</el-descriptions-item>
        <el-descriptions-item label="部门">{{ currentExpenseDetail.departmentName || currentExpenseDetail.department || '-' }}</el-descriptions-item>
        <el-descriptions-item label="费用类型">{{ currentExpenseDetail.type }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ Number(currentExpenseDetail.amount || 0).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="发生日期">{{ currentExpenseDetail.date || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ currentExpenseDetail.statusLabel || currentExpenseDetail.backendStatus || '-' }}</el-descriptions-item>
        <el-descriptions-item label="审批意见">{{ currentExpenseDetail.approvalComment || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备注">{{ currentExpenseDetail.description || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import ExpenseFiltersBar from './ExpenseFiltersBar.vue'
import ExpenseSummaryCards from './ExpenseSummaryCards.vue'
import ExpenseLedgerTable from './ExpenseLedgerTable.vue'
import DepositTrackingTable from './DepositTrackingTable.vue'
import ApprovalRecordsTable from './ApprovalRecordsTable.vue'
import { useExpenseLedgerPage } from './useExpenseLedgerPage.js'

const {
  loading,
  projectOptions,
  departmentOptions,
  searchForm,
  ledgerItems,
  summary,
  depositItems,
  overdueCount,
  approvalItems,
  showApplyDialog,
  showRemindDialog,
  showApprovalDialog,
  showDetailDialog,
  currentRemindItem,
  currentExpenseDetail,
  applyForm,
  approvalForm,
  handleSearch,
  handleReset,
  handleExport,
  handleDetail,
  handleReturn,
  handleConfirmReturn,
  handleSubmitApply,
  openApprovalDialog,
  confirmApproval,
  openRemindDialog,
  confirmRemind
} = useExpenseLedgerPage()
</script>

<style scoped lang="scss">
.expense-page {
  padding: 20px;
}
</style>
