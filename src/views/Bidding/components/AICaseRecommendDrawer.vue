<template>
  <el-drawer
    v-model="drawerVisible"
    title="AI 智能推荐案例"
    size="680px"
    direction="rtl"
    destroy-on-close
    class="ai-case-drawer"
  >
    <template #header>
      <div class="drawer-header-custom">
        <div class="header-main">
          <span class="header-icon">✨</span>
          <span class="header-title">AI 智能推荐案例</span>
          <el-badge v-if="totalCases > 0" :value="totalCases" class="case-count-badge" type="primary" />
        </div>
        <span class="header-desc">基于 AI 评分标准解析与历史投标案例库，精准推荐高契合度应答参考</span>
      </div>
    </template>

    <div class="drawer-body">
      <!-- 评分项缺失警告与解析引导 (PRD 5.1 契约) -->
      <el-alert
        v-if="!loading && scoreItems.length === 0"
        type="warning"
        show-icon
        :closable="false"
        class="no-score-item-alert"
      >
        <template #title>
          <span class="alert-title">暂无评分项数据，请先在任务看板进行「AI 评分标准解析」</span>
        </template>
        <template #default>
          <div class="alert-action">
            <el-button type="primary" link size="small" @click="$emit('goto-score-parse')">
              前往解析 →
            </el-button>
          </div>
        </template>
      </el-alert>

      <!-- 检索与筛选栏 -->
      <div class="filter-card">
        <div class="filter-row">
          <div class="filter-item item-score-select">
            <span class="filter-label">评分项：</span>
            <ScoreItemSelect
              v-model="selectedScoreItemId"
              :items="scoreItems"
              placeholder="选择关联评分项"
              @change="handleScoreItemChange"
            />
          </div>
        </div>

        <div class="filter-row keyword-row">
          <el-input
            v-model="searchKeyword"
            placeholder="输入技术关键词快速检索（如：高可用、双活、应急预案）"
            clearable
            class="keyword-input"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          >
            <template #prefix>
              <span class="search-icon">🔍</span>
            </template>
            <template #append>
              <el-button type="primary" :loading="loading" @click="handleSearch">
                检索
              </el-button>
            </template>
          </el-input>
        </div>
      </div>

      <!-- 推荐列表区域 -->
      <div
        v-loading="loading"
        element-loading-text="AI 正在根据评分项要素匹配案例..."
        class="case-list-container"
      >
        <div v-if="recommendedCases.length > 0" class="case-list">
          <AICaseRecommendCard
            v-for="caseItem in recommendedCases"
            :key="caseItem.id"
            :case-item="caseItem"
            @view-detail="openDetail"
            @quick-reuse="openReuseModal"
          />
        </div>

        <!-- 空状态 -->
        <div v-else-if="!loading" class="empty-wrap">
          <div class="empty-icon">📂</div>
          <div class="empty-title">暂无推荐案例</div>
          <div class="empty-desc">建议选择上方评分项或更换关键词进行深度语义检索</div>
        </div>
      </div>
    </div>

    <!-- 弹窗子组件 -->
    <AICaseDetailModal
      v-model="detailModalVisible"
      :case-detail="activeCase"
      @reuse="handleDetailModalReuse"
    />

    <AICaseReuseSectionModal
      v-model="reuseModalVisible"
      :case-data="activeCase"
      :project-id="projectId"
      :score-item-id="selectedScoreItemId"
      :default-chapter="currentDefaultChapter"
      :section-tree="sectionTree"
      @reuse-success="handleReuseSuccess"
    />
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { notifyErrorUnlessRateLimit } from '@/api/error-utils.js'
import { caseRecommendationApi } from '@/api/modules/caseRecommendation'
import AICaseRecommendCard from './AICaseRecommendCard.vue'
import AICaseDetailModal from './AICaseDetailModal.vue'
import AICaseReuseSectionModal from './AICaseReuseSectionModal.vue'
import ScoreItemSelect from '@/components/common/ScoreItemSelect.vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  projectId: {
    type: [Number, String],
    required: true
  },
  defaultScoreItemId: {
    type: [Number, String],
    default: null
  },
  sectionTree: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue', 'case-reused', 'goto-score-parse'])

const drawerVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const scoreItems = ref([])
const selectedScoreItemId = ref(null)
const searchKeyword = ref('')
const recommendedCases = ref([])
const totalCases = ref(0)

const detailModalVisible = ref(false)
const reuseModalVisible = ref(false)
const activeCase = ref(null)

// PRD 4.2：复用定位弹窗章节树默认选中当前评分项对应章节。
// 映射优先级：显式 targetChapter 字段（后端章节关联落地后直接生效）
// → dim 类别关键词回退到内置章节树（商务→商务标响应 / 技术→技术服务方案）
// → 空（由用户在弹窗中手选，PRD 允许修改）
const currentDefaultChapter = computed(() => {
  if (!selectedScoreItemId.value) return ''
  const item = scoreItems.value.find((s) => s.id === selectedScoreItemId.value)
  if (!item) return ''
  if (item.targetChapter) return item.targetChapter
  const dim = item.dim || ''
  if (dim.includes('商务') || dim.includes('报价') || dim.includes('资格')) return '第1章 商务标响应'
  if (dim.includes('技术') || dim.includes('方案') || dim.includes('服务')) return '第2章 技术服务方案'
  return ''
})

const loadScoreItems = async () => {
  if (!props.projectId) return
  try {
    const res = await caseRecommendationApi.getScoreItems(props.projectId)
    if (res && res.data) {
      scoreItems.value = res.data
    } else if (Array.isArray(res)) {
      scoreItems.value = res
    }
  } catch (err) {
    console.warn('获取评分项列表失败，支持关键词直接检索', err)
  }
}

const fetchRecommendations = async () => {
  if (!props.projectId) return
  loading.value = true
  try {
    const res = await caseRecommendationApi.recommendCases(props.projectId, {
      scoreItemId: selectedScoreItemId.value,
      keyword: searchKeyword.value.trim(),
      page: 0,
      size: 20
    })
    if (res && res.items) {
      recommendedCases.value = res.items
      totalCases.value = res.total || res.items.length
    } else {
      recommendedCases.value = []
      totalCases.value = 0
    }
  } catch (err) {
    notifyErrorUnlessRateLimit(err, '获取推荐案例失败')
    recommendedCases.value = []
  } finally {
    loading.value = false
  }
}

const handleScoreItemChange = () => {
  fetchRecommendations()
}

const handleSearch = () => {
  fetchRecommendations()
}

const openDetail = (caseItem) => {
  activeCase.value = caseItem
  detailModalVisible.value = true
}

const openReuseModal = (caseItem) => {
  activeCase.value = caseItem
  reuseModalVisible.value = true
}

const handleDetailModalReuse = (caseItem) => {
  detailModalVisible.value = false
  openReuseModal(caseItem)
}

const handleReuseSuccess = (payload) => {
  if (activeCase.value) {
    activeCase.value.reuseCount = (activeCase.value.reuseCount || 0) + 1
  }
  emit('case-reused', payload)
}

watch(
  () => drawerVisible.value,
  (visible) => {
    if (visible) {
      if (props.defaultScoreItemId) {
        selectedScoreItemId.value = props.defaultScoreItemId
      }
      loadScoreItems()
      fetchRecommendations()
    }
  }
)

onMounted(() => {
  if (drawerVisible.value) {
    loadScoreItems()
    fetchRecommendations()
  }
})
</script>

<style scoped>
@import './AICaseRecommendDrawer.css';
</style>
