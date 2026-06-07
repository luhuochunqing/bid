<template>
  <el-dialog
    v-model="modelVisible"
    title="⚠️ 检测到重复标讯"
    width="720px"
    :close-on-click-modal="false"
    @update:model-value="$emit('update:visible', $event)"
  >
    <!-- 分区1：当前录入内容 -->
    <div class="dup-section">
      <div class="dup-section-title">您正在录入</div>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="项目名称">{{ currentTender.title || '-' }}</el-descriptions-item>
        <el-descriptions-item label="招标主体">{{ currentTender.purchaserName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="报名截止">{{ formatDate(currentTender.registrationDeadline) || '-' }}</el-descriptions-item>
        <el-descriptions-item label="开标时间">{{ formatDate(currentTender.bidOpeningTime) || '-' }}</el-descriptions-item>
      </el-descriptions>
    </div>

    <!-- 分区2：重复标讯列表 -->
    <div class="dup-section">
      <div class="dup-section-title">系统中已存在以下重复标讯</div>
      <el-table :data="duplicates" border size="small" class="dup-table">
        <el-table-column prop="title" label="项目名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="purchaserName" label="招标主体" min-width="120" show-overflow-tooltip />
        <el-table-column prop="registrationDeadline" label="报名截止" width="150">
          <template #default="{ row }">{{ formatDate(row.registrationDeadline) }}</template>
        </el-table-column>
        <el-table-column prop="bidOpeningTime" label="开标时间" width="150">
          <template #default="{ row }">{{ formatDate(row.bidOpeningTime) }}</template>
        </el-table-column>
        <el-table-column label="来源平台" width="100">
          <template #default>
            <span class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="viewDetail(row)">
              查看详情
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <template #footer>
      <el-button @click="handleCancel">取消</el-button>
      <el-button type="warning" :loading="notifying" @click="handleNotifyAdmin">
        通知管理员复核
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  duplicates: { type: Array, default: () => [] },
  currentTender: { type: Object, default: () => ({}) },
})

const emit = defineEmits(['update:visible', 'notify-admin'])

const modelVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val),
})

const notifying = ref(false)

function formatDate(value) {
  if (!value) return '-'
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const pad = (n) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function viewDetail(row) {
  if (row.id) {
    window.open(`/bidding/${row.id}`, '_blank')
  }
}

function handleCancel() {
  modelVisible.value = false
}

async function handleNotifyAdmin() {
  notifying.value = true
  try {
    emit('notify-admin', { duplicates: props.duplicates, currentTender: props.currentTender })
  } finally {
    notifying.value = false
  }
}
</script>

<style scoped>
.dup-section {
  margin-bottom: 20px;
}

.dup-section:last-child {
  margin-bottom: 0;
}

.dup-section-title {
  font-weight: 600;
  font-size: 14px;
  color: var(--el-text-color-primary);
  margin-bottom: 10px;
}

.dup-table {
  max-height: 300px;
  overflow-y: auto;
}

.text-muted {
  color: var(--el-text-color-secondary);
}
</style>
