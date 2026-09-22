<template>
  <el-card shadow="never">
    <template #header>
      <div class="header">
        <div>
          <div class="title">待确认外部结果</div>
          <div class="subtitle">公开信息同步后，需要人工补录确认。</div>
        </div>
        <el-button type="primary" size="small" :disabled="selectedIds.length === 0" @click="$emit('confirm-batch')">
          批量确认
        </el-button>
      </div>
    </template>

    <el-table :data="rows" stripe @selection-change="$emit('selection-change', $event)">
      <el-table-column type="selection" width="48" />
      <el-table-column prop="source" label="来源" width="130" />
      <el-table-column prop="projectName" label="项目名称" min-width="180" />
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag :type="row.result === 'won' ? 'success' : 'info'" size="small">
            {{ row.result === 'won' ? '中标' : '未中标' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="金额" width="120">
        <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
      </el-table-column>
      <el-table-column label="同步时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.fetchTime) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$emit('confirm', row)">确认补录</el-button>
          <el-button link type="danger" @click="$emit('ignore', row)">忽略</el-button>
          <el-button link @click="$emit('prefill', row)">带入登记</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
defineProps({
  rows: {
    type: Array,
    default: () => []
  },
  selectedIds: {
    type: Array,
    default: () => []
  },
  formatAmount: {
    type: Function,
    required: true
  },
  formatDateTime: {
    type: Function,
    required: true
  }
})

defineEmits(['confirm', 'confirm-batch', 'ignore', 'prefill', 'selection-change'])
</script>

<style scoped>
.header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}

.title {
  font-size: 16px;
  font-weight: 600;
}

.subtitle {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}
</style>
