<template>
  <el-drawer v-model="visible" title="AI 智能案例推荐" size="680px" append-to-body @open="handleOpen">
    <div class="ai-recommend-drawer">
      <el-alert v-if="!scoreItemsLoading && scoreItems.length === 0" type="warning" :closable="false" show-icon>
        <template #title>
          <div class="empty-score-alert">
            <span>暂无评分项数据，请先在任务看板进行「AI 评分标准解析」</span>
            <el-button type="primary" size="small" @click="handleGoToScoreParse">前往解析</el-button>
          </div>
        </template>
      </el-alert>

      <div class="drawer-section">
        <label class="section-label">评分项</label>
        <el-select v-model="selectedItemId" placeholder="请选择评分项" clearable filterable style="width: 100%" @change="handleItemChange">
          <el-option-group v-for="group in scoringItemGroups" :key="group.dim" :label="group.dim">
            <el-option v-for="item in group.items" :key="item.id" :label="optionTitle(item)" :value="item.id">
              <el-tooltip :content="optionFullText(item)" placement="right" :show-after="150">
                <div class="score-option-line">{{ optionFullText(item) }}</div>
              </el-tooltip>
            </el-option>
          </el-option-group>
        </el-select>
      </div>

      <div class="drawer-section">
        <label class="section-label">关键词过滤</label>
        <el-input v-model="keywordFilter" placeholder="输入关键词进一步筛选..." clearable />
      </div>

      <div class="drawer-section">
        <div class="section-label-row">
          <label class="section-label">
            匹配案例
            <el-tag v-if="selectedItemId" size="small" type="info">{{ filteredCases.length }} 个结果</el-tag>
          </label>
          <el-tooltip placement="top" :show-after="100">
            <template #content>
              <div class="match-rule-tooltip">
                <div class="tooltip-title">匹配度计算规则（满分 100 分）：</div>
                <div>• 评分项条款/标题契合：最高 40 分</div>
                <div>• 评分项类别一致：+20 分</div>
                <div>• 项目/客户类型一致：各 +15 分</div>
                <div>• 关键词命中：最高 15 分</div>
                <div>• 历史中标加成：+5 分</div>
                <div class="tooltip-sub">评级：≥80% 优质，≥60% 良好，&lt;60% 一般</div>
              </div>
            </template>
            <span class="help-hint"><el-icon><QuestionFilled /></el-icon> 评分依据说明</span>
          </el-tooltip>
        </div>
        <div v-loading="loadingCases" class="cases-container">
          <div v-if="filteredCases.length > 0" class="recommend-grid">
            <CaseCard
              v-for="item in filteredCases"
              :key="item.caseId"
              :case-data="item"
              :match-score="item.score"
              @view-detail="viewDetail"
              @reuse="handleReuse"
            />
          </div>
          <el-empty v-else-if="!loadingCases && selectedItemId" description="暂无与本评分项相似的历史案例" :image-size="80" />
          <div v-else-if="!selectedItemId && defaultCases.length > 0" class="select-hint">基于项目基本信息推荐 {{ defaultCases.length }} 条案例，选择评分项可进一步筛选</div>
          <div v-else class="select-hint">请先选择一个评分项查看匹配的历史案例</div>
        </div>
      </div>
    </div>

    <CaseDetailDialog
      v-model="detailVisible"
      append-to-body
      :case-data="detailCase"
      :loading="detailLoading"
      @reuse="handleReuseInDetail"
      @go-source="goToSourceProject"
      @open-bid="openBidDocument"
    />
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { QuestionFilled } from '@element-plus/icons-vue'
import { caseRecommendationApi } from '@/api/modules/caseRecommendation'
import { projectLifecycleApi } from '@/api/modules/projectLifecycle'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'
import CaseCard from '@/views/Knowledge/views/components/CaseCard.vue'
import CaseDetailDialog from '@/views/Knowledge/views/components/CaseDetailDialog.vue'

const props = defineProps({ projectId: { type: [String, Number], required: true } })
const emit = defineEmits(['go-to-score-parse'])
const visible = defineModel({ type: Boolean, default: false })

const scoreItems = ref([])
const scoreItemsLoading = ref(false)
const selectedItemId = ref(null)
const keywordFilter = ref('')
const cases = ref([])
const loadingCases = ref(false)
const defaultCases = ref([])

const detailVisible = ref(false)
const detailLoading = ref(false)
const detailCase = ref(null)

const handleGoToScoreParse = () => { visible.value = false; emit('go-to-score-parse') }

const scoringItemGroups = computed(() => {
  const groups = {}
  scoreItems.value.forEach(d => {
    const dim = d.dim || '其他'
    if (!groups[dim]) groups[dim] = { dim, items: [] }
    groups[dim].items.push(d)
  })
  return Object.values(groups)
})

const optionFullText = (item) => `${item.code ? item.code + ' ' : ''}${item.dim || ''} - ${item.detail || ''}`
const optionTitle = (item) => (item.code ? `${item.code} ${item.dim || ''}`.trim() : item.dim || '')

const filteredCases = computed(() => {
  if (selectedItemId.value) return cases.value.filter(c => (c.score ?? 0) >= 50)
  const kw = keywordFilter.value.trim().toLowerCase()
  if (!kw) return cases.value
  return cases.value.filter(c =>
    (c.scoringTitle || '').toLowerCase().includes(kw) ||
    (c.responseTextSummary || '').toLowerCase().includes(kw) ||
    (c.sourceProjectName || '').toLowerCase().includes(kw)
  )
})

async function handleOpen() {
  scoreItemsLoading.value = true
  try {
    const res = await caseRecommendationApi.getScoreItems(props.projectId)
    const items = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : [])
    scoreItems.value = items
    if (items.length > 0) {
      selectedItemId.value = items[0].id
      await handleItemChange()
    } else {
      await loadDefaultProjectCases()
    }
  } catch (err) {
    scoreItems.value = []
    notifyErrorUnlessRateLimit(err, '获取评分项失败')
    await loadDefaultProjectCases()
  } finally {
    scoreItemsLoading.value = false
  }
}

async function loadDefaultProjectCases() {
  if (defaultCases.value.length > 0) { cases.value = defaultCases.value; return }
  try {
    const res = await caseRecommendationApi.recommendCases(props.projectId, { keyword: '', page: 0, size: 20 })
    defaultCases.value = (res?.items || []).map(normalizeRecommendCase)
    if (!selectedItemId.value) cases.value = defaultCases.value
  } catch (err) {
    defaultCases.value = []
    notifyErrorUnlessRateLimit(err, '获取推荐案例失败')
  }
}

async function handleItemChange() {
  if (!selectedItemId.value) {
    cases.value = defaultCases.value.length === 0 ? (await loadDefaultProjectCases(), cases.value) : [...defaultCases.value]
    return
  }
  loadingCases.value = true
  try {
    const res = await caseRecommendationApi.recommendCases(props.projectId, {
      scoreItemId: selectedItemId.value,
      keyword: keywordFilter.value.trim(),
      page: 0,
      size: 20
    })
    cases.value = (res?.items || []).map(normalizeRecommendCase)
  } catch (err) {
    cases.value = []
    notifyErrorUnlessRateLimit(err, '获取推荐案例失败')
  } finally {
    loadingCases.value = false
  }
}

let keywordDebounceTimer
onBeforeUnmount(() => clearTimeout(keywordDebounceTimer))
watch(keywordFilter, () => {
  if (selectedItemId.value) {
    clearTimeout(keywordDebounceTimer)
    keywordDebounceTimer = setTimeout(() => handleItemChange(), 300)
  }
})

function normalizeRecommendCase(item) {
  const matchScore = item.matchScore ?? item.score ?? 0
  return {
    caseId: item.id ?? item.caseId,
    scoringTitle: item.scoringPointTitle || item.scoringTitle,
    responseTextSummary: item.responseTextSummary || item.responseText || '',
    projectType: item.projectType,
    customerType: item.customerType,
    createdAt: item.createdAt || '',
    score: matchScore,
    matchScore,
    matchLabel: item.matchLabel || (matchScore >= 80 ? '优质' : matchScore >= 60 ? '良好' : '一般'),
    matchReasons: item.matchReasons || [],
    matchedKeywords: item.matchedKeywords || [],
    responseText: item.responseText || '',
    sourceProjectName: item.sourceProjectName,
  }
}

async function viewDetail(caseId) {
  detailVisible.value = true
  detailLoading.value = true
  const current = cases.value.find(c => (c.caseId ?? c.id) === caseId)
  detailCase.value = current ? { ...current } : null
  try {
    const res = await caseRecommendationApi.getCaseDetail(caseId)
    const full = res?.data || res
    detailCase.value = {
      ...full,
      matchScore: current?.matchScore ?? current?.score ?? full?.matchScore,
      matchLabel: current?.matchLabel ?? full?.matchLabel,
      matchReasons: current?.matchReasons ?? full?.matchReasons,
      matchedKeywords: current?.matchedKeywords ?? full?.matchedKeywords,
    }
  } catch (err) {
    notifyErrorUnlessRateLimit(err, '获取案例详情失败')
    detailVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

async function copyAndRecordReuse(caseId, text) {
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    ElMessage.error('复制失败')
    return
  }
  try {
    await caseRecommendationApi.reuseCase(caseId)
  } catch (err) {
    notifyErrorUnlessRateLimit(err, '复用记录失败')
    return
  }
  ElMessage.success('已复制到剪贴板')
}

const handleReuse = (item) => copyAndRecordReuse(item.caseId, item.responseText || item.responseTextSummary || '')
const handleReuseInDetail = (d) => copyAndRecordReuse(d.caseId || d.id, d.responseText || '')
const goToSourceProject = (d) => {
  if (d?.sourceProjectId) window.open(`/bidding/detail/${d.sourceProjectId}`, '_blank')
  else ElMessage.warning('来源项目不可用')
}

async function openBidDocument(d) {
  if (!d?.sourceProjectId) { ElMessage.warning('标书原文暂不可用'); return }
  try {
    const res = await projectLifecycleApi.exportProjectArchive(d.sourceProjectId)
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = `标书原文-${d.sourceProjectId}.zip`
    a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('标书原文下载成功')
  } catch (e) {
    notifyErrorUnlessRateLimit(e, '标书原文下载失败')
  }
}
</script>

<style scoped>
.ai-recommend-drawer { display: flex; flex-direction: column; gap: 20px; }
.drawer-section { display: flex; flex-direction: column; gap: 8px; }
.section-label-row { display: flex; align-items: center; justify-content: space-between; }
.section-label { font-size: 14px; font-weight: 600; color: var(--text-secondary-ui); display: flex; align-items: center; gap: 8px; }
.help-hint { font-size: 12px; color: var(--el-color-primary); cursor: pointer; display: flex; align-items: center; gap: 4px; }
.help-hint:hover { opacity: 0.8; }
.match-rule-tooltip { font-size: 12px; line-height: 1.6; padding: 2px 4px; }
.tooltip-title { font-weight: 600; margin-bottom: 4px; }
.tooltip-sub { margin-top: 4px; font-size: 11px; color: var(--el-color-warning); }
.cases-container { min-height: 200px; }
.select-hint { color: var(--text-muted); font-size: 14px; text-align: center; padding: 40px 0; }
.score-option-line { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.recommend-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; align-items: start; }
@media (max-width: 720px) { .recommend-grid { grid-template-columns: 1fr; } }
.empty-score-alert { display: flex; align-items: center; gap: 12px; }
</style>
