<template>
  <el-row :gutter="16" class="stat-row">
    <el-col :xs="12" :sm="8" :lg="4">
      <div class="stat-item primary">
        <div class="stat-value">¥{{ summary.totalAmount.toFixed(2) }}</div>
        <div class="stat-label">台账总额(元)</div>
      </div>
    </el-col>
    <el-col :xs="12" :sm="8" :lg="4">
      <div class="stat-item warning">
        <div class="stat-value">¥{{ summary.pendingApprovalAmount.toFixed(2) }}</div>
        <div class="stat-label">待审批金额</div>
      </div>
    </el-col>
    <el-col :xs="12" :sm="8" :lg="4">
      <div class="stat-item success">
        <div class="stat-value">¥{{ summary.approvedAmount.toFixed(2) }}</div>
        <div class="stat-label">待支付金额</div>
      </div>
    </el-col>
    <el-col :xs="12" :sm="8" :lg="4">
      <div class="stat-item neutral">
        <div class="stat-value">{{ summary.recordCount }}</div>
        <div class="stat-label">台账笔数</div>
      </div>
    </el-col>
    <el-col :xs="12" :sm="8" :lg="4">
      <div class="stat-item danger">
        <div class="stat-value">{{ summary.depositCount }}</div>
        <div class="stat-label">保证金笔数</div>
      </div>
    </el-col>
    <el-col :xs="12" :sm="8" :lg="4">
      <div class="stat-item accent">
        <div class="stat-value">{{ summary.pendingReturnCount }}</div>
        <div class="stat-label">待退还笔数</div>
      </div>
    </el-col>
  </el-row>
</template>

<script setup>
// Input: backend expense ledger summary
// Output: summary cards rendered from real ledger statistics
// Pos: src/views/Resource/expense/ - Expense summary cards
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

defineProps({
  summary: {
    type: Object,
    required: true
  }
})
</script>

<style scoped>
.stat-row {
  margin-bottom: 20px;
}

.stat-item {
  border-radius: 12px;
  padding: 18px 16px;
  color: var(--bg-card);
  text-align: center;
}

.primary { background: linear-gradient(135deg, #2563eb, #1d4ed8); }
.warning { background: linear-gradient(135deg, #f59e0b, #d97706); }
.success { background: linear-gradient(135deg, var(--color-success), var(--color-success-dark)); }
.neutral { background: linear-gradient(135deg, var(--text-slate), var(--sidebar-text-secondary)); }
.danger { background: linear-gradient(135deg, var(--color-danger), var(--color-danger)); }
.accent { background: linear-gradient(135deg, #0f766e, #115e59); }

.stat-value {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 6px;
}

.stat-label {
  font-size: 13px;
  opacity: 0.9;
}
</style>
