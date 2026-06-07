<template>
  <el-drawer v-model="internalVisible" :title="caseData?.scoringPointTitle || caseData?.scoringTitle || '案例详情'" size="560px" destroy-on-close>
    <div v-if="caseData" class="drawer-container" v-loading="loading">
      <!-- 1. 评分项原文（浅色背景展示区） -->
      <div class="section-title">评分项原文</div>
      <div class="content-box requirement-box">{{ caseData.requirementRaw }}</div>

      <!-- 2. 应答片段全文（标注总字数，浅色背景展示区） -->
      <div class="section-title mt-4">
        <span>应答片段全文</span>
      </div>
      <div class="word-count-hint">共 {{ (caseData?.responseText || '').length }} 字</div>
      <div class="content-box response-box">{{ caseData.responseText }}</div>

      <!-- 3. 案例元信息（双列网格布局展示） -->
      <div class="section-title mt-4">案例元信息</div>
      <el-descriptions :column="2" border class="case-desc">
        <el-descriptions-item label="来源项目">
          <el-link type="primary" @click="$emit('go-source', caseData)">{{ caseData.sourceProjectName }}</el-link>
        </el-descriptions-item>
        <el-descriptions-item label="中标结果"><el-tag size="small" :type="bidResultType">{{ bidResultLabel }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="项目类型"><el-tag size="small">{{ getProjectTypeLabel(caseData.projectType) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="客户类型"><el-tag size="small" type="success">{{ getCustomerTypeLabel(caseData.customerType) }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="复用次数">{{ caseData.reuseCount }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ formatDate(caseData.createdAt) }}</el-descriptions-item>
      </el-descriptions>

      <!-- 4. 相似案例（默认展示3条） -->
      <div class="section-title mt-6">相似案例</div>
      <div class="related-cases-list">
        <el-empty v-if="relatedCases.length === 0" description="暂无与本评分项相似的历史案例" :image-size="40" />
        <div v-for="rel in relatedCases" :key="rel.id || rel.caseId" class="related-case-item" @click="$emit('switch-case', rel.id || rel.caseId)">
          <div class="rel-header">
            <span class="rel-title">{{ rel.scoringPointTitle || rel.scoringTitle || rel.title }}</span>
            <span class="rel-reuse-count">
              <el-icon style="vertical-align:-.15em"><CopyDocument /></el-icon>
              {{ rel.reuseCount ?? 0 }}
            </span>
          </div>
        </div>
      </div>

      <!-- 5. 复用记录（默认展示最近3条） -->
      <div class="section-title mt-6">复用记录</div>
      <div v-if="reuseHistory.length === 0" class="empty-hint">暂无复用记录</div>
      <template v-else>
        <el-timeline>
          <el-timeline-item v-for="(r, i) in displayedReuseHistory" :key="i" :timestamp="r.time" placement="top">
            <span>{{ r.referencedByName }}</span>
            <span v-if="r.referencedProjectName" class="reuse-project">{{ r.referencedProjectName }}</span>
          </el-timeline-item>
        </el-timeline>
        <div v-if="reuseHistory.length > 3" class="expand-history-btn">
          <el-link type="primary" @click="showAllReuse = !showAllReuse">
            {{ showAllReuse ? '收起' : `查看更多（共${reuseHistory.length}条）` }}
          </el-link>
        </div>
      </template>

      <!-- 底部操作栏 -->
      <div class="drawer-actions mt-6">
        <el-button type="success" @click="$emit('reuse', caseData)">
          <el-icon style="vertical-align:-.2em;margin-right:4px"><CopyDocument /></el-icon>复用
        </el-button>
        <el-button plain @click="$emit('go-source', caseData)"><el-icon><Link /></el-icon>查看源项目</el-button>
        <el-button plain @click="$emit('open-bid', caseData)"><el-icon><Document /></el-icon>标书原文</el-button>
      </div>
      <div v-if="canManage" class="drawer-actions mt-2">
        <el-button size="small" @click="$emit('toggle-pin', caseData)">{{ caseData.pinned ? '取消置顶' : '置顶' }}</el-button>
        <el-button size="small" type="danger" plain @click="$emit('off-shelf', caseData)">下架</el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Link, Document, CopyDocument } from '@element-plus/icons-vue'
import { getProjectTypeLabel, getCustomerTypeLabel } from '../caseLabels.js'

const props = defineProps({
  modelValue: Boolean,
  caseData: Object,
  canManage: Boolean,
  loading: Boolean,
  relatedCases: { type: Array, default: () => [] },
  reuseHistory: { type: Array, default: () => [] }
})
const emit = defineEmits(['update:modelValue', 'reuse', 'switch-case', 'go-source', 'open-bid', 'toggle-pin', 'off-shelf'])

const internalVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const bidResultLabel = computed(() => {
  const r = props.caseData?.bidResult
  return r === 'WON' ? '中标' : r === 'LOST' ? '未中标' : r === 'CANCELED' ? '流标' : r || '-'
})
const bidResultType = computed(() => {
  const r = props.caseData?.bidResult
  return r === 'WON' ? 'success' : r === 'LOST' ? 'info' : r === 'CANCELED' ? 'danger' : 'info'
})
const formatDate = (dateStr) => {
  if (!dateStr) return '-'
  return dateStr.substring(0, 10)
}

const showAllReuse = ref(false)
const displayedReuseHistory = computed(() => {
  if (showAllReuse.value) return props.reuseHistory
  return props.reuseHistory.slice(0, 3)
})
</script>

<style scoped>
.drawer-container { padding: 0 16px 24px 16px; }
.section-title { font-size: 15px; font-weight: 600; color: var(--el-text-color-primary); margin-bottom: 12px; border-left: 4px solid var(--el-color-primary); padding-left: 8px; }
.case-desc { margin-bottom: 16px; }
.content-box { background-color: var(--el-fill-color-light); border-radius: 6px; padding: 12px; font-size: 13px; line-height: 1.6; color: var(--el-text-color-regular); white-space: pre-wrap; border: 1px solid var(--el-border-color-lighter); }
.requirement-box { max-height: 150px; overflow-y: auto; font-family: 'Courier New', Courier, monospace; }
.response-box { max-height: 240px; overflow-y: auto; }
.word-count-hint { font-size: 12px; color: var(--el-text-color-secondary); text-align: right; margin-bottom: 6px; padding-right: 4px; }
.drawer-actions { display: flex; gap: 12px; flex-wrap: wrap; }
.empty-hint { font-size: 13px; color: var(--el-text-color-placeholder); padding: 12px 0; }
.related-cases-list { display: flex; flex-direction: column; gap: 12px; margin-top: 12px; }
.related-case-item { padding: 12px; background-color: var(--el-fill-color-blank); border: 1px solid var(--el-border-color-lighter); border-radius: 6px; cursor: pointer; transition: all .2s; }
.related-case-item:hover { border-color: var(--el-color-primary); background-color: var(--el-color-primary-light-9); }
.related-case-item .rel-header { display: flex; justify-content: space-between; align-items: center; }
.related-case-item .rel-title { font-weight: 600; font-size: 13px; color: var(--el-text-color-primary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; max-width: 70%; }
.rel-reuse-count { font-size: 12px; color: var(--el-text-color-placeholder); white-space: nowrap; display: flex; align-items: center; gap: 2px; }
.reuse-project { color: var(--el-text-color-secondary); font-size: 12px; margin-left: 6px; }
.expand-history-btn { text-align: center; margin-top: 8px; }
.mt-4 { margin-top: 16px; }
.mt-6 { margin-top: 24px; }
.mt-2 { margin-top: 8px; }
</style>
