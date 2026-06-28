<template>
  <div class="table-container">
    <el-table
      ref="innerTableRef"
      class="tender-table"
      :data="rows"
      v-loading="loading"
      stripe
      scrollbar-always-on
      @selection-change="$emit('selection-change', $event)"
    >
      <el-table-column type="selection" width="55" fixed="left" />
      <el-table-column type="index" label="序号" width="90" align="center" fixed="left" />

      <!-- 项目名称：核心区 = 项目名 + 状态 + 来源 + 操作按钮 -->
      <el-table-column prop="title" label="项目名称" min-width="260" fixed="left" class-name="tender-main-column">
        <template #default="{ row = {} } = {}">
          <el-link
            v-if="safeTenderUrl(row.originalUrl)"
            class="tender-title-link"
            :href="safeTenderUrl(row.originalUrl)"
            target="_blank"
            rel="noopener noreferrer"
            type="primary"
            underline="never"
          >
            <span class="tender-title-text">{{ row.title }}</span>
            <el-icon class="link-icon"><LinkIcon /></el-icon>
          </el-link>
          <span v-else class="tender-title-text tender-title-clickable" @click="$emit('view-detail', row.id)">
            {{ row.title }}
          </span>
        </template>
      </el-table-column>

      <!-- 来源平台 -->
      <el-table-column prop="source" label="来源平台" width="120" align="center">
        <template #default="{ row = {} } = {}">
          <el-tag v-if="row.source" size="small" :type="getSourceTagType(row.source)">{{ getSourceText(row.source) }}</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>

      <!-- 总部所在地 -->
      <el-table-column prop="region" label="总部所在地" width="130" align="center">
        <template #default="{ row = {} } = {}">{{ row.region || '-' }}</template>
      </el-table-column>

      <!-- 招标主体 -->
      <el-table-column prop="purchaserName" label="招标主体" width="150">
        <template #default="{ row = {} } = {}">{{ row.purchaserName || '-' }}</template>
      </el-table-column>

      <!-- 项目类型 -->
      <el-table-column prop="projectType" label="项目类型" width="110" align="center">
        <template #default="{ row = {} } = {}">{{ row.projectType || '-' }}</template>
      </el-table-column>

      <!-- 客户类型 -->
      <el-table-column prop="customerType" label="客户类型" width="110" align="center">
        <template #default="{ row = {} } = {}">{{ row.customerType || '-' }}</template>
      </el-table-column>

      <!-- 报名截止日期 -->
      <el-table-column prop="registrationDeadline" label="报名截止日期" width="160" align="center">
        <template #default="{ row = {} } = {}">
          <span
            class="date-cell"
            :class="{
              'date-urgent': isUrgentDeadline(row.registrationDeadline),
              'date-expired': isExpired(row.registrationDeadline)
            }"
          >
            {{ row.registrationDeadline ? formatDate(row.registrationDeadline) : '-' }}
          </span>
        </template>
      </el-table-column>

      <!-- 开标时间 -->
      <el-table-column prop="bidOpeningTime" label="开标时间" width="150" align="center">
        <template #default="{ row = {} } = {}">
          <span class="date-cell">{{ row.bidOpeningTime ? formatDate(row.bidOpeningTime) : '-' }}</span>
        </template>
      </el-table-column>

      <!-- 标讯状态 -->
      <el-table-column prop="status" label="标讯状态" width="120" align="center">
        <template #default="{ row = {} } = {}">
          <el-tag :type="getTenderStatusTagType(row.status)" size="small">{{ getTenderStatusText(row.status) }}</el-tag>
        </template>
      </el-table-column>

      <!-- 项目负责人 -->
      <el-table-column prop="projectManagerName" label="项目负责人" width="130" align="center">
        <template #default="{ row = {} } = {}">{{ row.projectManagerName || '待分配' }}</template>
      </el-table-column>

      <!-- 项目部门 -->
      <el-table-column prop="department" label="项目部门" width="120" align="center">
        <template #default="{ row = {} } = {}">{{ row.department || '-' }}</template>
      </el-table-column>

      <!-- 优先级 -->
      <el-table-column prop="priority" label="优先级" width="100" align="center">
        <template #default="{ row = {} } = {}">
          <el-tag v-if="row.priority" :type="getPriorityTagType(row.priority)" size="small">{{ row.priority }}级</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>

      <!-- 创建人 -->
      <el-table-column prop="creatorName" label="创建人" width="100" align="center">
        <template #default="{ row = {} } = {}">{{ row.creatorName || '' }}</template>
      </el-table-column>

      <!-- 创建时间 -->
      <el-table-column prop="createdAt" label="创建时间" width="160" align="center">
        <template #default="{ row = {} } = {}">
          <span class="date-cell">{{ row.createdAt ? formatDate(row.createdAt) : '-' }}</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { Link as LinkIcon } from '@element-plus/icons-vue'
import FavoriteButton from './FavoriteButton.vue'
import { getSourceTagType, getSourceText, safeTenderUrl } from '../helpers.js'
import { getTenderStatusTagType, getTenderStatusText } from '../../bidding-utils-status.js'

const props = defineProps({
  rows: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  canManageTenders: { type: Boolean, default: false },
  canDeleteTenders: { type: Boolean, default: false },
  showAiEntry: { type: Boolean, default: true },
  isAdmin: { type: Boolean, default: false },
  canTransfer: { type: Boolean, default: false },
})

const emit = defineEmits([
  'selection-change', 'view-detail', 'ai-analysis', 'participate',
  'distribute', 'edit', 'review', 'status-change', 'delete', 'set-reminder',
  'transfer', 'claim', 'assign',
])

const innerTableRef = ref(null)

const formatDate = (val) => {
  if (!val) return '-'
  const d = new Date(val)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const getPriorityTagType = (priority) => {
  const map = { S: 'danger', A: 'warning', B: 'primary', C: 'info' }
  return map[priority] || 'info'
}

const isUrgentDeadline = (deadline) => {
  if (!deadline) return false
  const now = Date.now()
  const deadlineTs = new Date(deadline).getTime()
  return deadlineTs > now && (deadlineTs - now) <= 3 * 24 * 60 * 60 * 1000
}

const isExpired = (deadline) => {
  if (!deadline) return false
  return new Date(deadline).getTime() < Date.now()
}
</script>

<style scoped>
.tender-table :deep(.el-table__header) .cell {
  white-space: nowrap !important;
  word-break: keep-all !important;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
