<template>
  <el-table
    :data="applications"
    stripe
    size="small"
    max-height="400"
    style="width: 100%"
  >
    <el-table-column type="index" label="序号" width="55" fixed />
    <el-table-column label="申请人" min-width="110">
      <template #default="{ row }">{{ formatDisplayName(row.applicantName, row.applicantEmployeeNumber) }}</template>
    </el-table-column>
    <el-table-column prop="purpose" label="使用目的" min-width="140" show-overflow-tooltip />
    <el-table-column label="申请时间" min-width="140">
      <template #default="{ row }">{{ formatDisplayDateTime(row.createdAt) || '-' }}</template>
    </el-table-column>
    <el-table-column label="借用期限" min-width="80">
      <template #default="{ row }">
        <el-tag
          :type="row.borrowDurationType === 'LONG_TERM' ? 'primary' : 'info'"
          size="small"
        >{{ borrowDurationTypeLabel(row.borrowDurationType) }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column label="盖章承诺书" min-width="110">
      <template #default="{ row }">
        <a
          v-if="row.commitmentLetterUrl"
          class="commitment-letter-link"
          :href="getCommitmentLetterDownloadUrl(row.commitmentLetterUrl)"
          target="_blank"
          rel="noopener noreferrer"
        >
          <el-icon><Document /></el-icon>下载
        </a>
        <span v-else class="text-muted">未提交</span>
      </template>
    </el-table-column>
    <el-table-column prop="expectedReturnDate" label="预计归还" min-width="100">
      <template #default="{ row }">{{ row.expectedReturnDate || '-' }}</template>
    </el-table-column>
    <el-table-column prop="actualReturnDate" label="实际归还" min-width="100">
      <template #default="{ row }">{{ row.actualReturnDate || '-' }}</template>
    </el-table-column>
    <el-table-column label="状态" min-width="85">
      <template #default="{ row }">
        <el-tag
          :type="borrowStatusTagType(row)"
          size="small"
        >{{ row.statusLabel }}</el-tag>
      </template>
    </el-table-column>
  </el-table>
  <el-empty v-if="!applications.length" description="暂无借用记录" :image-size="60" />
</template>

<script setup>
// CO-515: 从 CADetailDialog.vue 抽出借用记录表格组件（保持父组件在 line-budget 内）
import { Document } from '@element-plus/icons-vue'
import {
  caApplicationStatusTagType,
  borrowDurationTypeLabel
} from '../composables/useCaBorrowEligibility'
import { formatDisplayName } from '@/utils/formatDisplayName'
import { formatDisplayDateTime } from '@/utils/formatDisplayDate'

defineProps({
  applications: { type: Array, default: () => [] }
})

// CO-515: 借用记录状态标签颜色 — 长期借用已批准用 primary 区分（保管员已转给申请人）
function borrowStatusTagType(row) {
  if (row.borrowDurationType === 'LONG_TERM' && row.status === 'APPROVED') {
    return 'primary'
  }
  return caApplicationStatusTagType(row.status)
}

// CO-515: 盖章承诺书下载链接 — 从 URL 提取文件名，拼装后端下载端点
function getCommitmentLetterDownloadUrl(url) {
  if (!url) return ''
  const filename = url.split('/').pop() || url
  return `/api/ca-certificates/commitment-letter/files/${filename}`
}
</script>

<style scoped>
.commitment-letter-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  color: var(--el-color-primary);
  text-decoration: none;
  font-size: 13px;
}

.commitment-letter-link:hover {
  text-decoration: underline;
}

.text-muted {
  color: var(--el-text-color-secondary);
  font-size: 13px;
}
</style>
