<template>
  <el-drawer v-model="internalVisible" :title="caseData?.scoringPointTitle || caseData?.scoringTitle || '案例详情'" size="560px" destroy-on-close>
    <div v-if="caseData" class="drawer-container" v-loading="loading">
      <!-- 1. 评分项详细要素原文（浅色背景展示区） -->
      <div class="section-title">评分项详细要素原文</div>
      <div class="content-box requirement-box">{{ caseData.requirementRaw || '暂无要求' }}</div>

      <!-- 2. 应答片段全文（标注总字数，浅色背景展示区） -->
      <div class="section-title mt-4">
        <span>应答片段全文</span>
      </div>
      <div class="word-count-hint">共 {{ (caseData?.responseText || '').length }} 字</div>
      <div class="content-box response-box">{{ caseData.responseText || '暂无应答内容' }}</div>

      <!-- 3. 案例元信息（双列网格布局展示） -->
      <div class="section-title mt-4">案例元信息</div>
      <el-descriptions :column="2" border class="case-desc">
        <el-descriptions-item label="项目名称">{{ caseData.sourceProjectName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="招标主体">{{ caseData.ownerUnit || '-' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间"><DateTimeDisplay :value="caseData.createdAt" /></el-descriptions-item>
        <el-descriptions-item label="项目状态"><el-tag size="small" :type="bidResultType">{{ bidResultLabel }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="客户类型"><el-tag size="small" type="success">{{ getCustomerTypeLabel(caseData.customerType) || '-' }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="项目类型"><el-tag size="small" type="primary">{{ getProjectTypeLabel(caseData.projectType) || '-' }}</el-tag></el-descriptions-item>
      </el-descriptions>

      <!-- 底部操作栏：查看源项目/标书原文靠左，复用靠右 -->
      <div class="drawer-actions mt-6">
        <el-button plain @click="$emit('go-source', caseData)"><el-icon><Link /></el-icon>查看源项目</el-button>
        <el-button plain @click="$emit('open-bid', caseData)"><el-icon><Document /></el-icon>标书原文</el-button>
        <el-button type="primary" class="reuse-btn" @click="$emit('reuse', caseData)">
          <el-icon style="vertical-align:-.2em;margin-right:4px"><CopyDocument /></el-icon>复用
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed } from 'vue'
import { Link, Document, CopyDocument } from '@element-plus/icons-vue'
import DateTimeDisplay from '@/components/common/DateTimeDisplay.vue'
import { getProjectTypeLabel, getCustomerTypeLabel } from '../caseLabels.js'

const props = defineProps({
  modelValue: Boolean,
  caseData: Object,
  loading: Boolean
})
const emit = defineEmits(['update:modelValue', 'reuse', 'go-source', 'open-bid'])

const internalVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const bidResultLabel = computed(() => {
  const r = props.caseData?.bidResult
  return r === 'WON' ? '已中标' : r === 'LOST' ? '未中标' : '未知'
})
const bidResultType = computed(() => {
  const r = props.caseData?.bidResult
  return r === 'WON' ? 'success' : r === 'LOST' ? 'danger' : 'info'
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
.reuse-btn { margin-left: auto; }
.mt-4 { margin-top: 16px; }
.mt-6 { margin-top: 24px; }
</style>
