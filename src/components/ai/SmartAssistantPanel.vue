<template>
  <el-drawer
    v-model="drawerVisible"
    direction="rtl"
    :size="360"
    class="smart-assistant-drawer"
    :with-header="false"
  >
    <div class="panel-container">
      <!-- 面板头部 -->
      <div class="panel-header">
        <div class="header-title">
          <el-icon :size="18"><MagicStick /></el-icon>
          <span>智能助手</span>
        </div>
        <el-button
          link
          :icon="Close"
          @click="handleClose"
          class="close-btn"
        />
      </div>

      <!-- 面板内容 -->
      <div class="panel-content">
        <!-- 投标前 - 决策支持 -->
        <div v-if="showDemoFeatures" class="feature-group">
          <div class="group-header">
            <el-tag type="info" size="small">投标前</el-tag>
            <span class="group-title">决策支持</span>
          </div>
          <div class="feature-list">
            <div
              class="feature-item"
              @click="handleFeatureClick('open-competition-intel')"
            >
              <div class="feature-icon">
                <el-icon :size="20"><TrendCharts /></el-icon>
              </div>
              <div class="feature-info">
                <div class="feature-title">竞争情报</div>
                <div class="feature-desc">{{ competitionText }}</div>
              </div>
            </div>
            <div
              class="feature-item"
              @click="handleFeatureClick('open-roi-analysis')"
            >
              <div class="feature-icon">
                <el-icon :size="20"><PieChart /></el-icon>
              </div>
              <div class="feature-info">
                <div class="feature-title">ROI核算</div>
                <div class="feature-desc">{{ roiText }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 投标中 - 协作提效 -->
        <div class="feature-group">
          <div class="group-header">
            <el-tag type="primary" size="small">投标中</el-tag>
            <span class="group-title">协作提效</span>
          </div>
          <div class="feature-list">
            <div
              class="feature-item"
              @click="handleFeatureClick('open-score-coverage')"
            >
              <div class="feature-icon">
                <el-icon :size="20"><Grid /></el-icon>
              </div>
              <div class="feature-info">
                <div class="feature-title">评分点覆盖</div>
                <div class="feature-desc">{{ scoreCoverageText }}</div>
              </div>
            </div>
            <div
              class="feature-item"
              @click="handleFeatureClick('open-compliance-check')"
            >
              <div class="feature-icon">
                <el-icon :size="20"><Lock /></el-icon>
              </div>
              <div class="feature-info">
                <div class="feature-title">合规雷达</div>
                <div class="feature-desc">{{ complianceText }}</div>
              </div>
            </div>
            <div
              class="feature-item"
              @click="handleFeatureClick('open-version-control')"
            >
              <div class="feature-icon">
                <el-icon :size="20"><Clock /></el-icon>
              </div>
              <div class="feature-info">
                <div class="feature-title">版本管理</div>
                <div class="feature-desc">3个版本历史</div>
              </div>
            </div>
            <div
              class="feature-item"
              @click="handleFeatureClick('open-collaboration')"
            >
              <div class="feature-icon">
                <el-icon :size="20"><User /></el-icon>
              </div>
              <div class="feature-info">
                <div class="feature-title">协作中心</div>
                <div class="feature-desc">{{ collaborationText }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 投标后 - 跟踪复盘 -->
        <div class="feature-group">
          <div class="group-header">
            <el-tag type="success" size="small">投标后</el-tag>
            <span class="group-title">跟踪复盘</span>
          </div>
          <div class="feature-list">
            <div
              class="feature-item"
              @click="handleFeatureClick('open-auto-tasks')"
            >
              <div class="feature-icon">
                <el-icon :size="20"><Bell /></el-icon>
              </div>
              <div class="feature-info">
                <div class="feature-title">自动化节点</div>
                <div class="feature-desc">2个待办提醒</div>
              </div>
            </div>
            <div
              class="feature-item"
              @click="handleFeatureClick('open-mobile-card')"
            >
              <div class="feature-icon">
                <el-icon :size="20"><Iphone /></el-icon>
              </div>
              <div class="feature-info">
                <div class="feature-title">移动端卡片</div>
                <div class="feature-desc">分享到手机</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import {
  MagicStick,
  Close,
  TrendCharts,
  PieChart,
  Grid,
  Lock,
  Clock,
  User,
  Bell,
  Iphone
} from '@element-plus/icons-vue'
import { aiApi } from '@/api'

const props = defineProps({
  projectId: String,
  visible: {
    type: Boolean,
    default: false
  },
  showDemoFeatures: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits([
  'update:visible',
  'open-competition-intel',
  'open-roi-analysis',
  'open-score-coverage',
  'open-compliance-check',
  'open-version-control',
  'open-collaboration',
  'open-auto-tasks',
  'open-mobile-card'
])

const aiCards = ref({
  score: null,
  competition: [],
  compliance: [],
  roi: null
})

const drawerVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

const isNumericProjectId = computed(() => /^\d+$/.test(String(props.projectId || '')))
const shouldUseRealCards = computed(() => !props.showDemoFeatures && isNumericProjectId.value)

const scoreCoverageText = computed(() => {
  if (shouldUseRealCards.value && aiCards.value.score) {
    return `综合分: ${aiCards.value.score.overallScore || 0}`
  }
  return '覆盖率: 68%'
})

const complianceText = computed(() => {
  if (shouldUseRealCards.value && aiCards.value.compliance.length > 0) {
    const latest = aiCards.value.compliance[0]
    return `最新状态: ${latest.overallStatus || 'UNKNOWN'}`
  }
  return '废标风险检查'
})

const competitionText = computed(() => {
  if (shouldUseRealCards.value && aiCards.value.competition.length > 0) {
    return `已分析 ${aiCards.value.competition.length} 个对手`
  }
  return '分析本标可能对手'
})

const roiText = computed(() => {
  if (shouldUseRealCards.value && aiCards.value.roi) {
    return `ROI: ${Number(aiCards.value.roi.roiPercentage || 0).toFixed(1)}%`
  }
  return '投入产出预测'
})

const collaborationText = computed(() => {
  if (shouldUseRealCards.value) {
    return '真实协作流程'
  }
  return '5人协作中'
})

const loadAiCards = async () => {
  if (!shouldUseRealCards.value) return
  const response = await aiApi.project.getCards(props.projectId)
  if (response?.success && response.data) {
    aiCards.value = response.data
  }
}

const handleClose = () => {
  emit('update:visible', false)
}

const handleFeatureClick = (eventName) => {
  emit(eventName)
}

watch(
  () => [props.projectId, props.showDemoFeatures, props.visible],
  async ([, , visible]) => {
    if (visible) {
      await loadAiCards()
    }
  },
  { immediate: true }
)
</script>

<style scoped>
.smart-assistant-drawer {
  transition: width 0.3s ease;
}

.smart-assistant-drawer :deep(.el-drawer__body) {
  padding: 0;
  background: var(--bg-subtle);
}

.panel-container {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.smart-assistant-panel.is-collapsed {
  width: 0 !important;
  border-left: none;
}

.panel-container {
  width: 360px;
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-subtle);
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: white;
  border-bottom: 1px solid var(--gray-250);
  flex-shrink: 0;
}

.header-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 500;
  color: var(--gray-750);
}

.header-title .el-icon {
  color: #409eff;
}

.close-btn {
  font-size: 18px;
  color: var(--text-muted);
}

.close-btn:hover {
  color: #409eff;
}

.panel-content {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

/* 滚动条样式 */
.panel-content::-webkit-scrollbar {
  width: 6px;
}

.panel-content::-webkit-scrollbar-thumb {
  background: #dcdfe6;
  border-radius: 3px;
}

.panel-content::-webkit-scrollbar-thumb:hover {
  background: #c0c4cc;
}

.feature-group {
  background: white;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.05);
}

.feature-group:last-child {
  margin-bottom: 0;
}

.group-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid #f0f0f0;
}

.group-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--text-secondary-ui);
}

.feature-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: #fafafa;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.feature-item:hover {
  background: #ecf5ff;
  transform: translateX(2px);
}

.feature-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
  color: white;
  flex-shrink: 0;
}

.feature-item:nth-child(2n) .feature-icon {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.feature-item:nth-child(3n) .feature-icon {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.feature-info {
  flex: 1;
  min-width: 0;
}

.feature-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--gray-750);
  margin-bottom: 2px;
}

.feature-desc {
  font-size: 12px;
  color: var(--text-muted);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* 移动端响应式 */
@media (max-width: 768px) {
  .smart-assistant-panel {
    position: fixed !important;
    right: 0;
    top: 0;
    height: 100vh;
    z-index: 1000;
    box-shadow: -2px 0 8px rgba(0, 0, 0, 0.1);
  }

  .panel-container {
    width: 100%;
    max-width: 320px;
  }
}
</style>
