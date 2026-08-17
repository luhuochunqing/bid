<template>
  <el-drawer v-model="visible" title="AI 智能推荐" size="680px" append-to-body @open="handleOpen">
    <div class="ai-recommend-drawer">
      <!-- 评分项为空时的提示 -->
      <el-alert v-if="!scoreDraftsLoading && scoreDrafts.length === 0" type="warning" :closable="false" show-icon>
        <template #title>
          <div class="empty-score-alert">
            <span>暂无评分项数据，请先在任务看板进行「AI 评分标准解析」</span>
            <el-button type="primary" size="small" @click="handleGoToScoreParse">前往解析</el-button>
          </div>
        </template>
      </el-alert>

      <!-- 评分项筛选 -->
      <div class="drawer-section">
        <label class="section-label">评分项</label>
        <el-select v-model="selectedItem" placeholder="请选择评分项" clearable filterable style="width: 100%" @change="handleItemChange">
          <el-option-group v-for="group in scoringItemGroups" :key="group.category" :label="group.category">
            <el-option v-for="item in group.items" :key="item.id" :label="item.scoreItemTitle" :value="item.scoreItemTitle" />
          </el-option-group>
        </el-select>
      </div>

      <!-- 关键词二次过滤 -->
      <div class="drawer-section">
        <label class="section-label">关键词过滤</label>
        <el-input v-model="keywordFilter" placeholder="输入关键词进一步筛选..." clearable />
      </div>

      <!-- 匹配案例列表 -->
      <div class="drawer-section">
        <label class="section-label">
          匹配案例
          <el-tag v-if="selectedItem" size="small" type="info">{{ filteredCases.length }} 个结果</el-tag>
        </label>
        <div v-loading="loadingCases" class="cases-container">
          <template v-if="filteredCases.length > 0">
            <div
              v-for="item in filteredCases"
              :key="item.caseId"
              class="recommend-case-card"
              :class="{ 'high-quality': item.scoreLabel === '优质' }"
            >
              <div class="case-card-header">
                <div class="title-row">
                  <el-tag v-if="item.scoreLabel === '优质'" size="small" type="danger" effect="dark">优质</el-tag>
                  <el-tag size="small" type="warning">{{ item.score }}%</el-tag>
                  <h4 class="case-title">{{ item.scoringTitle }}</h4>
                </div>
                <div class="tag-row mt-2">
                  <el-tag size="small" type="primary" effect="plain">{{ getProjectTypeLabel(item.projectType) }}</el-tag>
                  <el-tag size="small" type="success" effect="plain">{{ getCustomerTypeLabel(item.customerType) }}</el-tag>
                  <el-tag v-if="item.bidResult" size="small" :type="item.bidResult === 'WON' ? 'success' : 'info'" effect="plain">
                    {{ item.bidResult === 'WON' ? '中标' : '未中标' }}
                  </el-tag>
                </div>
              </div>
              <div class="case-requirement">评分项原文: {{ (item.requirementRaw || "").slice(0, 80) }}{{ (item.requirementRaw || "").length > 80 ? "..." : "" }}</div>
              <div class="case-summary">{{ item.responseTextSummary }}</div>
              <div class="case-meta">
                <span>来源: {{ item.sourceProjectName }}</span>
                <span>复用 {{ item.reuseCount }} 次</span>
              </div>
              <div class="case-card-footer">
                <el-button type="primary" size="small" link @click="viewDetail(item)">详情</el-button>
                <el-button type="success" size="small" @click="handleReuse(item)">复用</el-button>
              </div>
            </div>
          </template>
          <el-empty v-else-if="!loadingCases && selectedItem" description="暂无与本评分项相似的历史案例" :image-size="80" />
          <div v-else-if="!selectedItem && defaultCases.length > 0" class="select-hint">基于项目基本信息推荐 {{ defaultCases.length }} 条案例，选择评分项可进一步筛选</div>
          <div v-else class="select-hint">请先选择一个评分项查看匹配的历史案例</div>
        </div>
      </div>
    </div>

    <!-- 双面板详情弹窗 -->
    <AiRecommendDualPaneDialog v-model="detailVisible" :selected-case="selectedCase" @reuse="handleReuse" />
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { casesApi } from '@/api/modules/knowledge.js'
import { projectsApi } from '@/api'
import { getProjectTypeLabel, getCustomerTypeLabel } from '@/views/Knowledge/views/caseLabels.js'
import AiRecommendDualPaneDialog from './AiRecommendDualPaneDialog.vue'

const props = defineProps({ projectId: { type: [String, Number], required: true } })
const emit = defineEmits(['go-to-score-parse'])
const visible = defineModel({ type: Boolean, default: false })

const scoreDrafts = ref([])
const scoreDraftsLoading = ref(false)
const selectedItem = ref('')
const keywordFilter = ref('')
const cases = ref([])
const loadingCases = ref(false)
const detailVisible = ref(false)
const selectedCase = ref(null)
const defaultCases = ref([])

function handleGoToScoreParse() {
  visible.value = false
  emit('go-to-score-parse')
}

const scoringItemGroups = computed(() => {
  const groups = {}
  scoreDrafts.value.forEach(d => {
    const cat = d.category || '其他'
    if (!groups[cat]) groups[cat] = { category: cat, items: [] }
    groups[cat].items.push(d)
  })
  return Object.values(groups)
})

const filteredCases = computed(() => {
  if (!keywordFilter.value.trim()) return cases.value
  const kw = keywordFilter.value.trim().toLowerCase()
  return cases.value.filter(c =>
    (c.scoringTitle || '').toLowerCase().includes(kw) ||
    (c.responseTextSummary || '').toLowerCase().includes(kw) ||
    (c.sourceProjectName || '').toLowerCase().includes(kw)
  )
})

async function handleOpen() {
  scoreDraftsLoading.value = true
  try {
    const result = await projectsApi.getScoreDrafts(props.projectId)
    scoreDrafts.value = Array.isArray(result?.data) ? result.data : []
    if (scoreDrafts.value.length > 0) {
      selectedItem.value = scoreDrafts.value[0].scoreItemTitle
      await handleItemChange()
    } else {
      await loadDefaultProjectCases()
    }
  } catch {
    scoreDrafts.value = []
    await loadDefaultProjectCases()
  } finally {
    scoreDraftsLoading.value = false
  }
}

async function loadDefaultProjectCases() {
  if (defaultCases.value.length > 0) {
    cases.value = defaultCases.value
    return
  }
  try {
    const r = await casesApi.recommendForProject(props.projectId, '')
    defaultCases.value = (Array.isArray(r) ? r : []).map(normalizeRecommendCase)
    if (!selectedItem.value) cases.value = defaultCases.value
  } catch {
    defaultCases.value = []
  }
}

async function handleItemChange() {
  if (!selectedItem.value) {
    if (defaultCases.value.length === 0) {
      await loadDefaultProjectCases()
    } else {
      cases.value = [...defaultCases.value]
    }
    return
  }
  loadingCases.value = true
  try {
    const result = await casesApi.recommendCases(props.projectId, selectedItem.value, keywordFilter.value)
    cases.value = Array.isArray(result) ? result.map(normalizeRecommendCase) : []
  } catch {
    cases.value = []
  } finally {
    loadingCases.value = false
  }
}

let keywordDebounceTimer
watch(keywordFilter, () => {
  if (selectedItem.value) {
    clearTimeout(keywordDebounceTimer)
    keywordDebounceTimer = setTimeout(() => {
      handleItemChange()
    }, 300)
  }
})

function normalizeRecommendCase(item) {
  return {
    caseId: item.caseId,
    scoringTitle: item.scoringTitle || item.scoringPointTitle,
    responseTextSummary: summarize(item.responseTextSummary || item.responseText || ''),
    projectType: item.projectType,
    customerType: item.customerType,
    bidResult: item.bidResult,
    reuseCount: item.reuseCount || 0,
    sourceProjectName: item.sourceProjectName,
    score: item.score || 0,
    scoreLabel: item.scoreLabel || '',
    matchReason: item.matchReason || '',
    highlightedText: item.highlightedText || '',
    responseText: item.responseText || '',
    requirementRaw: item.requirementRaw || '',
    docName: item.docName || item.sourceDocName || '',
    page: item.page || item.sourcePage || '',
    readerHTML: item.readerHTML || item.highlightedText || item.responseText || '',
  }
}

function summarize(text) {
  if (!text) return ''
  return text.length > 100 ? text.slice(0, 100) + '...' : text
}

function viewDetail(item) {
  selectedCase.value = item
  detailVisible.value = true
}

async function handleReuse(item) {
  const text = item.responseText || item.responseTextSummary || ''
  try {
    await navigator.clipboard.writeText(text)
    try {
      await casesApi.reuseCase(item.caseId)
      item.reuseCount = (item.reuseCount || 0) + 1
    } catch { /* ignore */ }
    ElMessage.success({ message: '已复制应答内容到剪贴板', duration: 3000 })
  } catch {
    ElMessage.error('复制到剪贴板失败')
  }
}
</script>

<style scoped>
.ai-recommend-drawer { display: flex; flex-direction: column; gap: 20px; }
.drawer-section { display: flex; flex-direction: column; gap: 8px; }
.section-label { font-size: 14px; font-weight: 600; color: var(--text-secondary-ui); }
.cases-container { min-height: 200px; }
.select-hint { color: var(--text-muted); font-size: 14px; text-align: center; padding: 40px 0; }
.recommend-case-card {
  background: var(--bg-card); border: 1px solid var(--gray-250); border-radius: 8px;
  padding: 16px; margin-bottom: 12px; transition: box-shadow 0.3s;
}
.recommend-case-card:hover { box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1); }
.recommend-case-card.high-quality { border-color: var(--el-color-danger); background: linear-gradient(135deg, var(--el-color-danger-light-9) 0%, transparent 30%); }
.case-card-header { margin-bottom: 8px; }
.title-row { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; flex-wrap: wrap; }
.case-title { margin: 0; font-size: 15px; font-weight: 600; color: var(--gray-750); flex: 1; line-height: 1.5; }
.tag-row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.case-requirement { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.5; margin-bottom: 6px; padding: 4px 8px; background: var(--el-fill-color-lighter); border-radius: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.case-summary { font-size: 13px; color: var(--text-secondary-ui); line-height: 1.6; margin-bottom: 12px; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.case-meta { font-size: 12px; color: var(--text-muted); display: flex; gap: 16px; margin-bottom: 12px; }
.case-card-footer { display: flex; align-items: center; justify-content: flex-end; gap: 8px; padding-top: 10px; border-top: 1px solid var(--gray-250); }
.mt-2 { margin-top: 8px; }
</style>

