<template>
  <el-card class="case-card" shadow="hover" :class="{ 'is-pinned': caseData.pinned }" @click="$emit('view-detail', caseData.caseId || caseData.id)">
    <div class="case-card-header">
      <div class="title-row">
        <el-tag v-if="caseData.pinned" size="small" type="warning" effect="dark" class="pinned-badge">置顶</el-tag>
        <span class="scoring-title">{{ caseData.scoringTitle }}</span>
      </div>
      <div class="tag-row mt-2">
        <el-tag size="small" type="primary" effect="plain">{{ getProjectTypeLabel(caseData.projectType) }}</el-tag>
        <el-tag size="small" type="success" effect="plain" class="ml-1">{{ getCustomerTypeLabel(caseData.customerType) }}</el-tag>
      </div>
    </div>
    <div class="case-card-body">
      <p class="summary-text">{{ truncatedSummary }}</p>
    </div>
    <div class="case-card-footer">
      <div class="info-meta">
        <span class="reuse-stat"><el-icon class="icon-stat"><CopyDocument /></el-icon>复用 <strong>{{ caseData.reuseCount }}</strong> 次</span>
        <span class="date-stat ml-2">{{ caseData.createdAt }}</span>
      </div>
      <el-button size="small" type="success" @click.stop="$emit('reuse', caseData)">
        <el-icon style="vertical-align:-.2em;margin-right:4px"><CopyDocument /></el-icon>复用
      </el-button>
    </div>
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { CopyDocument } from '@element-plus/icons-vue'
import { getProjectTypeLabel, getCustomerTypeLabel } from '../caseLabels.js'
const props = defineProps({ caseData: Object, canManage: Boolean })
defineEmits(['view-detail', 'reuse'])
const truncatedSummary = computed(() => {
  const text = props.caseData?.responseTextSummary || ''
  return text.length > 80 ? text.slice(0, 80) + '...' : text
})
</script>

<style scoped lang="scss">
.case-card {
  height: 100%;
  border-radius: 8px;
  transition: all 0.25s ease;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  border: 1px solid var(--el-border-color-lighter);
  cursor: pointer;
  &:hover { transform: translateY(-4px); box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08); }
  &.is-pinned { border-color: var(--el-color-warning); background: linear-gradient(135deg, var(--el-color-warning-light-9) 0%, transparent 30%); }
}
.case-card-header { border-bottom: 1px dashed var(--el-border-color-lighter); padding-bottom: 12px; }
.title-row { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; }
.pinned-badge { flex-shrink: 0; }
.scoring-title { font-size: 15px; font-weight: 600; color: var(--el-text-color-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.tag-row { display: flex; align-items: center; }
.case-card-body { padding: 12px 0; flex-grow: 1; }
.summary-text { font-size: 13px; color: var(--el-text-color-regular); line-height: 1.6; margin: 0; display: -webkit-box; -webkit-line-clamp: 4; -webkit-box-orient: vertical; overflow: hidden; height: 83px; }
.case-card-footer { border-top: 1px dashed var(--el-border-color-lighter); padding-top: 12px; display: flex; justify-content: space-between; align-items: center; }
.info-meta { font-size: 12px; color: var(--el-text-color-placeholder); display: flex; align-items: center; }
.icon-stat { vertical-align: middle; margin-right: 2px; }
.mt-2 { margin-top: 8px; }
.ml-1 { margin-right: 4px; }
.ml-2 { margin-left: 8px; }
</style>
