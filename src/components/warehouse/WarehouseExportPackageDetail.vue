<template>
  <div class="package-detail">
    <div class="detail-title">📦 ZIP 包内容</div>
    <ul class="detail-list">
      <li>仓库信息台账.xlsx（{{ totalCount }} 条，24 列含系统字段）</li>
      <li v-if="hasAttachments && attachmentForms.includes('ATTACHMENTS_FOLDER')">attachments/</li>
      <li v-if="attachmentForms.includes('ATTACHMENTS_FOLDER') && summary.propertyCertCount" class="indent">产权证 {{ summary.propertyCertCount }} 份</li>
      <li v-if="attachmentForms.includes('ATTACHMENTS_FOLDER') && summary.invoiceCount" class="indent">发票 {{ summary.invoiceCount }} 份</li>
      <li v-if="attachmentForms.includes('ATTACHMENTS_FOLDER') && summary.photosCount" class="indent">照片 {{ summary.photosCount }} 张</li>
      <li v-if="attachmentForms.includes('ATTACHMENTS_FOLDER') && summary.leaseContractCount" class="indent">租赁合同 {{ summary.leaseContractCount }} 份</li>
      <li v-if="attachmentForms.includes('WORD_COMBINED')">仓库附件合订本.docx</li>
    </ul>
    <div class="meta-row"><span class="meta-label">导出范围：</span><span>{{ summary.filterSummary || '—' }}</span></div>
    <div class="meta-row"><span class="meta-label">附件范围：</span><span>{{ summary.attachmentScope || '—' }}</span></div>
    <div class="meta-row"><span class="meta-label">处理耗时：</span><span>{{ formatElapsed(summary.elapsedMs) }}</span></div>
    <div class="meta-row"><span class="meta-label">包大小：</span><span>{{ formatBytes(summary.zipBytes) }}</span></div>
    <div class="meta-row"><span class="meta-label">链接有效期：</span><span>7 天</span></div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { formatBytes } from '@/utils/formatBytes'

const props = defineProps({
  totalCount: { type: Number, default: 0 },
  summary: { type: Object, default: () => ({}) },
  attachmentForms: { type: Array, default: () => [] }
})

const hasAttachments = computed(() => {
  const s = props.summary || {}
  return (s.propertyCertCount || 0) + (s.invoiceCount || 0) + (s.photosCount || 0) + (s.leaseContractCount || 0) > 0
})

const formatElapsed = (ms) => {
  if (!ms || ms <= 0) return '—'
  if (ms < 1000) return `${ms} 毫秒`
  const s = Math.floor(ms / 1000)
  if (s < 60) return `${s} 秒`
  const m = Math.floor(s / 60)
  return `${m} 分 ${s % 60} 秒`
}
</script>

<style scoped>
.package-detail { margin-top: 12px; padding: 14px; background: var(--gray-50); border-radius: 6px; font-size: 13px; }
.detail-title { font-weight: 600; color: var(--text-primary-ui); margin-bottom: 8px; }
.detail-list { margin: 0 0 12px; padding-left: 18px; line-height: 1.9; }
.detail-list .indent { list-style: none; margin-left: -12px; color: var(--el-text-color-secondary); }
.meta-row { line-height: 1.9; color: var(--el-text-color-regular); }
.meta-label { display: inline-block; min-width: 88px; color: var(--el-text-color-secondary); }
</style>
