<template>
  <el-drawer
    v-model="visible"
    title="AI 智能推荐"
    size="680px"
    append-to-body
    @open="handleOpen"
  >
    <div class="ai-recommend-drawer">
      <!-- 评分项筛选 -->
      <div class="drawer-section">
        <label class="section-label">评分项</label>
        <el-select
          v-model="selectedItem"
          placeholder="请选择评分项"
          clearable
          filterable
          style="width: 100%"
          @change="handleItemChange"
        >
          <el-option-group
            v-for="group in scoringItemGroups"
            :key="group.category"
            :label="group.category"
          >
            <el-option
              v-for="item in group.items"
              :key="item.id"
              :label="item.scoreItemTitle"
              :value="item.scoreItemTitle"
            />
          </el-option-group>
        </el-select>
      </div>

      <!-- 关键词二次过滤 -->
      <div class="drawer-section">
        <label class="section-label">关键词过滤</label>
        <el-input
          v-model="keywordFilter"
          placeholder="输入关键词进一步筛选..."
          clearable
          @input="debouncedFilter"
        />
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
                  <el-tag
                    v-if="item.bidResult"
                    size="small"
                    :type="item.bidResult === 'WON' ? 'success' : 'info'"
                    effect="plain"
                  >
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
          <el-empty
            v-else-if="!loadingCases && selectedItem"
            description="暂无与本评分项相似的历史案例"
            :image-size="80"
          />
          <div v-else-if="!selectedItem && defaultCases.length > 0" class="select-hint">基于项目基本信息推荐 {{ defaultCases.length }} 条案例，选择评分项可进一步筛选</div>
          <div v-else class="select-hint">请先选择一个评分项查看匹配的历史案例</div>
        </div>
      </div>
    </div>

    <!-- 双面板详情弹窗 -->
    <el-dialog v-model="detailVisible" title="" width="1100px" :close-on-click-modal="false" destroy-on-close class="dual-pane-dialog">
      <div v-if="selectedCase" class="dual-pane-body">
        <div class="dual-pane-left">
          <div class="dp-section"><div class="dp-label">命中评分项</div><div class="dp-val">{{ selectedCase.scoringTitle }}</div></div>
          <div class="dp-section"><div class="dp-label">匹配度</div><div class="dp-val"><el-tag :type="selectedCase.score >= 85 ? 'danger' : 'primary'">{{ selectedCase.score }}% {{ selectedCase.scoreLabel }}</el-tag></div></div>
          <div class="dp-section"><div class="dp-label">来源项目</div><div class="dp-val">{{ selectedCase.sourceProjectName }}</div></div>
          <div class="dp-section"><div class="dp-label">中标结果</div><div class="dp-val">{{ selectedCase.bidResult === 'WON' ? '中标' : selectedCase.bidResult === 'LOST' ? '未中标' : '—' }}</div></div>
          <div class="dp-section"><div class="dp-label">复用次数</div><div class="dp-val">{{ selectedCase.reuseCount }} 次</div></div>
          <div class="dp-section"><div class="dp-label">应答原文</div><div class="dp-snippet">{{ selectedCase.responseTextSummary || selectedCase.responseText }}</div></div>
        </div>
        <div class="dual-pane-right">
          <div class="reader-head">📄 {{ selectedCase.docName || selectedCase.sourceProjectName || '来源标书' }}<span v-if="selectedCase.page"> · 第 {{ selectedCase.page }} 页</span></div>
          <div class="reader-page">
            <h5>{{ selectedCase.scoringTitle }}</h5>
            <div v-html="safeHtml(selectedCase.readerHTML || selectedCase.highlightedText || selectedCase.responseText)"></div>
          </div>
        </div>
      </div>
      <template #footer>
        <div class="dp-footer">
          <el-button type="primary" @click="handleReuse(selectedCase)">📋 复用此案例</el-button>
          <el-button @click="detailVisible = false">关闭</el-button>
        </div>
      </template>
    </el-dialog>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { casesApi } from '@/api/modules/knowledge.js'
import { projectsApi } from '@/api'
import { getProjectTypeLabel, getCustomerTypeLabel } from '@/views/Knowledge/views/caseLabels.js'
import { safeHtml } from '@/utils/safeHtml.js'

const props = defineProps({ projectId: { type: [String, Number], required: true } })
const visible = defineModel({ type: Boolean, default: false })

const scoreDrafts = ref([])
const selectedItem = ref('')
const keywordFilter = ref('')
const cases = ref([])
const loadingCases = ref(false)
const detailVisible = ref(false)
const selectedCase = ref(null)
const defaultCases = ref([])

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

let debounceTimer
function debouncedFilter() {
  clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {}, 200)
}

async function handleOpen() {
  try {
    const result = await projectsApi.getScoreDrafts(props.projectId)
    scoreDrafts.value = Array.isArray(result?.data) ? result.data : []
    // 默认选中第一项并触发检索
    if (scoreDrafts.value.length > 0) {
      selectedItem.value = scoreDrafts.value[0].scoreItemTitle
      await handleItemChange()
    }
  } catch { scoreDrafts.value = [] }
  // 立项即推荐：基于项目基本信息触发初步推荐
  try {
    const r = await casesApi.recommendForProject(props.projectId, '')
    defaultCases.value = (Array.isArray(r) ? r : []).map(normalizeRecommendCase)
    if (!selectedItem.value) cases.value = defaultCases.value
  } catch { /* ignore */ }
}


async function handleItemChange() {
  if (!selectedItem.value) { cases.value = [...defaultCases.value]; return }
  loadingCases.value = true
  try {
    const result = await casesApi.recommendCases(props.projectId, selectedItem.value, keywordFilter.value)
    cases.value = Array.isArray(result) ? result.map(normalizeRecommendCase) : []
  } catch { cases.value = [] }
  finally { loadingCases.value = false }
}

watch(keywordFilter, () => {
  if (selectedItem.value) {
    handleItemChange()
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
    // 调用后端复用计数 API
    try {
      await casesApi.reuseCase(item.caseId)
      item.reuseCount = (item.reuseCount || 0) + 1
    } catch {
      // 计数失败不影响用户体验
    }
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

.dual-pane-body { display: flex; gap: 0; min-height: 400px; }
.dual-pane-left { flex: 0 0 380px; padding: 0 16px 0 0; overflow-y: auto; border-right: 1px solid #eee; }
.dual-pane-right { flex: 1; padding: 0 0 0 16px; overflow-y: auto; }
.dp-section { margin-bottom: 14px; }
.dp-label { font-size: 11px; color: #888; font-weight: 600; margin-bottom: 4px; text-transform: uppercase; letter-spacing: .5px; }
.dp-val { font-size: 13px; color: #333; line-height: 1.6; }
.dp-snippet { background: #f5f8f6; border-left: 3px solid #2E7659; padding: 10px 12px; border-radius: 4px; font-size: 12px; line-height: 1.7; color: #444; }
.reader-head { font-size: 12px; color: #888; margin-bottom: 10px; }
.reader-page { background: #fff; border: 1px solid #e0e0e0; border-radius: 4px; padding: 16px 20px; font-size: 13px; line-height: 1.9; color: #444; }
.reader-page h5 { font-size: 14px; font-weight: 600; margin: 0 0 8px; color: #222; }
.reader-page :deep(.hit) { background: #fff4b8; padding: 1px 3px; border-radius: 2px; }
.dp-footer { display: flex; justify-content: flex-end; gap: 8px; }
</style>
