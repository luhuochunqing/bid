<template>
  <el-drawer
    v-model="visible"
    title="AI 智能推荐案例"
    size="680px"
    append-to-body
    @open="handleOpen"
  >
    <div class="case-slice-drawer">
      <!-- 评分项为空时的提示 -->
      <el-alert
        v-if="!scoreDraftsLoading && scoreDrafts.length === 0"
        type="warning"
        :closable="false"
        show-icon
      >
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
        <el-select
          v-model="selectedScoringItem"
          placeholder="选择评分项自动检索"
          clearable
          filterable
          style="width: 100%"
          @change="handleScoringItemChange"
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

      <!-- 关键词搜索 -->
      <div class="drawer-section">
        <label class="section-label">关键词搜索</label>
        <el-input
          v-model="searchQuery"
          placeholder="输入关键词进一步检索..."
          clearable
          @keyup.enter="handleSearch"
        >
          <template #append>
            <el-button type="primary" @click="handleSearch">搜索</el-button>
          </template>
        </el-input>
      </div>

      <!-- 推荐结果 -->
      <div class="drawer-section">
        <label class="section-label">
          推荐结果
          <el-tag v-if="hasSearched" size="small" type="info">{{ recommendations.length }} 个结果</el-tag>
        </label>
        <div v-loading="loading" class="results-container">
          <template v-if="recommendations.length > 0">
            <div
              v-for="item in recommendations"
              :key="item.id"
              class="recommend-card"
              :class="{ 'high-similarity': item.similarity >= 0.8 }"
            >
              <div class="card-header">
                <div class="title-row">
                  <el-tag v-if="item.similarity >= 0.8" size="small" type="danger" effect="dark">高匹配</el-tag>
                  <el-tag size="small" type="warning">{{ (item.similarity * 100).toFixed(1) }}%</el-tag>
                  <h4 class="card-title">{{ item.title }}</h4>
                </div>
                <div class="tag-row">
                  <el-tag size="small" type="primary" effect="plain">{{ item.docxLabel }}</el-tag>
                  <el-tag size="small" type="success" effect="plain">{{ item.projectDir }}</el-tag>
                </div>
              </div>
              <div class="card-preview">{{ item.textPreview }}</div>
              <div class="card-meta">
                <span>段落数: {{ item.paraCount }}</span>
                <span>字数: {{ item.textLength }}</span>
              </div>
              <div class="card-footer">
                <el-button type="success" size="small" @click="handleCopy(item)">复制到剪贴板</el-button>
              </div>
            </div>
          </template>
          <el-empty
            v-else-if="!loading && hasSearched"
            description="暂无匹配的案例切片"
            :image-size="80"
          />
          <div v-else class="select-hint">选择评分项或输入关键词搜索相关案例切片</div>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { caseSlicesApi } from '@/api'
import { projectsApi } from '@/api'

const props = defineProps({
  projectId: { type: [String, Number], required: true },
})

const emit = defineEmits(['go-to-score-parse'])

const visible = defineModel({ type: Boolean, default: false })

const scoreDrafts = ref([])
const scoreDraftsLoading = ref(false)
const selectedScoringItem = ref('')
const searchQuery = ref('')
const recommendations = ref([])
const loading = ref(false)
const hasSearched = ref(false)

const scoringItemGroups = computed(() => {
  const groups = {}
  scoreDrafts.value.forEach(d => {
    const cat = d.category || '其他'
    if (!groups[cat]) groups[cat] = { category: cat, items: [] }
    groups[cat].items.push(d)
  })
  return Object.values(groups)
})

async function handleOpen() {
  // 加载评分项列表
  scoreDraftsLoading.value = true
  try {
    const result = await projectsApi.getScoreDrafts(props.projectId)
    scoreDrafts.value = Array.isArray(result?.data) ? result.data : []
  } catch {
    scoreDrafts.value = []
  } finally {
    scoreDraftsLoading.value = false
  }
  // 默认选中第一项并触发检索
  if (scoreDrafts.value.length > 0) {
    selectedScoringItem.value = scoreDrafts.value[0].scoreItemTitle
    await searchByQuery(selectedScoringItem.value)
  }
}

function handleGoToScoreParse() {
  visible.value = false
  emit('go-to-score-parse')
}

async function handleScoringItemChange() {
  if (selectedScoringItem.value) {
    searchQuery.value = ''
    await searchByQuery(selectedScoringItem.value)
  } else {
    recommendations.value = []
    hasSearched.value = false
  }
}

async function handleSearch() {
  if (!searchQuery.value.trim()) {
    recommendations.value = []
    hasSearched.value = false
    return
  }
  selectedScoringItem.value = ''
  await searchByQuery(searchQuery.value.trim())
}

async function searchByQuery(query) {
  if (!query) {
    recommendations.value = []
    hasSearched.value = false
    return
  }
  loading.value = true
  hasSearched.value = true
  try {
    const result = await caseSlicesApi.recommendByQuery(query, 20)
    recommendations.value = Array.isArray(result?.data) ? result.data : []
  } catch {
    recommendations.value = []
  } finally {
    loading.value = false
  }
}

async function handleCopy(item) {
  const text = item.textPreview || item.title || ''
  try {
    await navigator.clipboard.writeText(text)
    ElMessage.success({ message: '已复制到剪贴板，可粘贴到标书文档中', duration: 3000 })
  } catch {
    ElMessage.error('复制到剪贴板失败')
  }
}
</script>

<style scoped>
.case-slice-drawer { display: flex; flex-direction: column; gap: 20px; }
.drawer-section { display: flex; flex-direction: column; gap: 8px; }
.section-label { font-size: 14px; font-weight: 600; color: var(--text-secondary-ui); }
.results-container { min-height: 300px; }
.select-hint { color: var(--text-muted); font-size: 14px; text-align: center; padding: 40px 0; }

.recommend-card {
  background: var(--bg-card); border: 1px solid var(--gray-250); border-radius: 8px;
  padding: 16px; margin-bottom: 12px; transition: box-shadow 0.3s;
}
.recommend-card:hover { box-shadow: 0 4px 12px var(--gray-200); }
.recommend-card.high-similarity { border-color: var(--el-color-danger); background: linear-gradient(135deg, var(--el-color-danger-light-9) 0%, transparent 30%); }

.card-header { margin-bottom: 8px; }
.title-row { display: flex; align-items: center; gap: 6px; margin-bottom: 8px; flex-wrap: wrap; }
.card-title { margin: 0; font-size: 15px; font-weight: 600; color: var(--gray-750); flex: 1; line-height: 1.5; }
.tag-row { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }

.card-preview { font-size: 13px; color: var(--text-secondary-ui); line-height: 1.6; margin-bottom: 12px; display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden; }
.card-meta { font-size: 12px; color: var(--text-muted); display: flex; gap: 16px; margin-bottom: 12px; }
.card-footer { display: flex; align-items: center; justify-content: flex-end; gap: 8px; padding-top: 10px; border-top: 1px solid var(--gray-250); }

.empty-score-alert { display: flex; align-items: center; gap: 12px; }
.empty-score-alert span { flex: 1; }
</style>
