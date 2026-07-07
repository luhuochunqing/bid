<template>
  <!-- CO-530: 审核日志附件下载区 -->
  <section v-if="qualification?.auditLogFileUrl" class="qd-audit-log" data-testid="qd-audit-log">
    <h4 class="qd-section-title">审核日志附件</h4>
    <div class="qd-attachment-card">
      <div class="qd-att-icon">
        <el-icon :size="28"><Document /></el-icon>
      </div>
      <div class="qd-att-info">
        <div class="qd-att-name">{{ auditLogDisplayName }}</div>
      </div>
      <div class="qd-att-actions">
        <el-button size="small" link type="primary" data-testid="qd-audit-log-download" @click="handleDownloadAuditLog">下载</el-button>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import { Document } from '@element-plus/icons-vue'
import http from '@/api/client'

const props = defineProps({
  qualification: { type: Object, default: null }
})

// CO-530: 审核日志附件显示名（从 auditLogFileUrl 中提取原始文件名）
const auditLogDisplayName = computed(() => {
  const url = props.qualification?.auditLogFileUrl
  if (!url) return '审核日志附件'
  const underscoreIdx = url.indexOf('_')
  return underscoreIdx >= 0 && underscoreIdx < url.length - 1
    ? url.substring(underscoreIdx + 1)
    : url
})

// CO-530: 下载审核日志附件（axios 携带认证头获取 blob）
const handleDownloadAuditLog = () => {
  const id = props.qualification?.id
  if (!id) return
  http.get(`/api/knowledge/qualifications/${id}/audit-log/download`, { responseType: 'blob' })
    .then((blob) => {
      const url = URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.setAttribute('download', auditLogDisplayName.value || '审核日志附件')
      document.body.appendChild(link)
      link.click()
      link.remove()
      URL.revokeObjectURL(url)
    })
    .catch(() => {
      // 错误已由全局拦截器提示
    })
}
</script>

<style scoped lang="scss">
.qd-audit-log {
  margin-bottom: 24px;
}
.qd-section-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 0 0 12px;
}
.qd-attachment-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  margin-bottom: 8px;
  transition: all 0.2s;
  &:hover { box-shadow: 0 2px 8px rgba(0,0,0,.05); }
}
.qd-att-icon {
  color: var(--el-color-primary);
  flex-shrink: 0;
}
.qd-att-info {
  flex: 1;
  min-width: 0;
}
.qd-att-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.qd-att-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}
</style>
