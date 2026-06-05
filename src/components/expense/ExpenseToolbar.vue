<template>
  <el-card class="search-card">
    <el-form :inline="true" :model="searchForm">
      <el-form-item label="项目名称">
        <el-input v-model="searchForm.project" placeholder="请输入" clearable />
      </el-form-item>
      <el-form-item label="费用类型">
        <el-select v-model="searchForm.type" placeholder="全部" clearable>
          <el-option label="保证金" value="保证金" />
          <el-option label="标书费" value="标书费" />
          <el-option label="差旅费" value="差旅费" />
          <el-option label="其他" value="其他" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="searchForm.status" placeholder="全部" clearable>
          <el-option label="已支付" value="paid" />
          <el-option label="待支付" value="pending" />
          <el-option label="已退还" value="returned" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="$emit('search')">
          <el-icon><Search /></el-icon>
          搜索
        </el-button>
        <el-button @click="$emit('reset')">重置</el-button>
      </el-form-item>
    </el-form>
  </el-card>

  <el-card class="table-card">
    <template #header>
      <div class="card-header">
        <span>费用台账</span>
        <div class="header-actions">
          <el-button type="primary" @click="$emit('apply')">
            <el-icon><Plus /></el-icon>
            费用申请
          </el-button>
          <el-button @click="$emit('export')">
            <el-icon><Download /></el-icon>
            导出
          </el-button>
        </div>
      </div>
    </template>

    <el-row :gutter="16" class="stat-row">
      <el-col :xs="12" :sm="6">
        <div class="stat-item">
          <div class="stat-value">¥{{ stats.totalPaid }}万</div>
          <div class="stat-label">已支付总额</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-item">
          <div class="stat-value">¥{{ stats.totalPending }}万</div>
          <div class="stat-label">待支付总额</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-item">
          <div class="stat-value">{{ stats.depositCount }}</div>
          <div class="stat-label">保证金笔数</div>
        </div>
      </el-col>
      <el-col :xs="12" :sm="6">
        <div class="stat-item warning">
          <div class="stat-value">{{ stats.warningCount }}</div>
          <div class="stat-label">待退还提醒</div>
        </div>
      </el-col>
    </el-row>

    <slot />
  </el-card>
</template>

<script setup>
import { Download, Plus, Search } from '@element-plus/icons-vue'

defineModel('searchForm', { type: Object, required: true })

defineProps({
  stats: {
    type: Object,
    required: true
  }
})

defineEmits(['search', 'reset', 'apply', 'export'])
</script>

<style scoped lang="scss">
.search-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.stat-row {
  margin-bottom: 20px;
}

.stat-item {
  padding: 20px;
  text-align: center;
  color: var(--bg-card);
  border-radius: 8px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);

  &.warning {
    background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
  }
}

.stat-value {
  margin-bottom: 8px;
  font-size: 24px;
  font-weight: 700;
}

.stat-label {
  font-size: 14px;
  opacity: 0.9;
}

.card-header :deep(.el-button) {
  min-width: 110px;
  height: 38px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
}

@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions {
    flex-direction: column;
  }

  .header-actions :deep(.el-button) {
    width: 100%;
  }
}
</style>
