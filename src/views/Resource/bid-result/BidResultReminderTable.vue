<template>
  <el-card shadow="never">
    <template #header>
      <div class="header">
        <div>
          <div class="title">资料上传提醒</div>
          <div class="subtitle">提醒销售上传中标通知书或分析报告，并支持上传完成回写。</div>
        </div>
      </div>
    </template>

    <el-table :data="rows" stripe>
      <el-table-column prop="projectName" label="项目名称" min-width="180" />
      <el-table-column prop="owner" label="负责人" width="120" />
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          <el-tag :type="row.type === 'notice' ? 'warning' : 'info'" size="small">
            {{ row.type === 'notice' ? '中标通知书' : '分析报告' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTypeMap[row.status] || 'info'" size="small">
            {{ statusTextMap[row.status] || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="提醒时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.remindTime) }}</template>
      </el-table-column>
      <el-table-column prop="lastReminderComment" label="最近说明" min-width="180" />
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="$emit('upload', row)">上传资料</el-button>
          <el-button v-if="row.lastResultId" link @click="$emit('remind-again', row)">再次提醒</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
const statusTypeMap = {
  pending: 'danger',
  reminded: 'warning',
  uploaded: 'success'
}

const statusTextMap = {
  pending: '待上传',
  reminded: '已提醒',
  uploaded: '已上传'
}

defineProps({
  rows: {
    type: Array,
    default: () => []
  },
  formatDateTime: {
    type: Function,
    required: true
  }
})

defineEmits(['upload', 'remind-again'])
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
