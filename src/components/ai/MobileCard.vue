<template>
  <el-dialog
    v-model="dialogVisible"
    title="移动端战情卡片"
    width="420px"
    :close-on-click-modal="false"
    destroy-on-close
    center
  >
    <div class="mobile-preview-container">
      <!-- 手机边框 -->
      <div class="phone-frame">
        <!-- 状态栏 -->
        <div class="status-bar">
          <span class="time">9:41</span>
          <div class="status-icons">
            <span class="icon">📶</span>
            <span class="icon">📡</span>
            <span class="icon">🔋</span>
          </div>
        </div>

        <!-- 应用内容 -->
        <div class="app-content" v-if="currentData">
          <!-- 顶部项目信息 -->
          <div class="project-header">
            <div class="project-name">{{ currentData.projectName }}</div>
            <el-tag :type="getStageType(currentData.stage)" size="small">
              {{ currentData.stage }}
            </el-tag>
          </div>

          <!-- 倒计时 -->
          <div class="countdown-section">
            <div class="countdown-label">距开标</div>
            <div class="countdown-value">{{ currentData.countdown }}</div>
          </div>

          <!-- 赢面评分 -->
          <div class="win-score-section">
            <div class="score-circle" :class="getScoreClass(currentData.winScore)">
              <div class="score-value">{{ currentData.winScore }}</div>
              <div class="score-label">赢面</div>
            </div>
            <div class="score-detail">
              <div class="score-label">负责人: {{ currentData.owner }}</div>
            </div>
          </div>

          <!-- 客户信息 -->
          <div class="info-card">
            <div class="card-title">
              <span class="title-icon"></span>
              客户信息
            </div>
            <div class="info-row">
              <span class="info-label">客户</span>
              <span class="info-value">{{ currentData.customer }}</span>
            </div>
            <div class="info-row">
              <span class="info-label">联系人</span>
              <span class="info-value">{{ currentData.contact }}</span>
            </div>
          </div>

          <!-- 下一步动作 -->
          <div class="info-card">
            <div class="card-title">
              <span class="title-icon">📋</span>
              下一步动作
            </div>
            <div class="task-list">
              <div
                v-for="(task, index) in currentData.nextTasks"
                :key="index"
                class="task-item"
              >
                <div class="task-dot"></div>
                <div class="task-content">
                  <div class="task-title">{{ task.title }}</div>
                  <div class="task-due">{{ task.due }}</div>
                </div>
              </div>
            </div>
          </div>

          <!-- 风险提示 -->
          <div class="info-card" v-if="currentData.risks?.length">
            <div class="card-title">
              <span class="title-icon">⚠️</span>
              风险提示
            </div>
            <div class="risk-list">
              <div
                v-for="(risk, index) in currentData.risks"
                :key="index"
                class="risk-item"
                :class="risk.level"
              >
                <el-icon class="risk-icon"><Warning /></el-icon>
                <span class="risk-text">{{ risk.text }}</span>
              </div>
            </div>
          </div>

          <!-- 底部导航 -->
          <div class="bottom-nav">
            <div class="nav-item active">
              <span class="nav-icon"></span>
              <span class="nav-label">战情</span>
            </div>
            <div class="nav-item">
              <span class="nav-icon">💬</span>
              <span class="nav-label">消息</span>
            </div>
            <div class="nav-item">
              <span class="nav-icon">📁</span>
              <span class="nav-label">文档</span>
            </div>
            <div class="nav-item">
              <span class="nav-icon"></span>
              <span class="nav-label">设置</span>
            </div>
          </div>
        </div>

        <!-- 无数据状态 -->
        <div class="app-content no-data" v-else>
          <el-empty description="暂无卡片数据" />
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="handleClose">关闭</el-button>
      <el-button type="success" @click="handleShare">
        <el-icon><Share /></el-icon>
        分享到手机
      </el-button>
      <el-button type="primary" @click="handleOpenInMobile">
        <el-icon><Cellphone /></el-icon>
        在手机中打开
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { Share, Cellphone, Warning } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false
  },
  projectId: {
    type: String,
    default: ''
  },
  data: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['update:modelValue', 'share', 'open-mobile'])

const dialogVisible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const currentData = computed(() => {
  const projectData = props.data?.[props.projectId] || null
  if (projectData) {
    return projectData
  }

  return null
})

const getStageType = (stage) => {
  const types = {
    '投标准备中': 'primary',
    '资格预审': 'info',
    '已投标': 'success',
    '评标中': 'warning',
    '已中标': 'success',
    '未中标': 'danger'
  }
  return types[stage] || 'info'
}

const getScoreClass = (score) => {
  if (score >= 70) return 'high'
  if (score >= 50) return 'medium'
  return 'low'
}

const handleClose = () => {
  dialogVisible.value = false
}

const handleShare = () => {
  emit('share', currentData.value)
}

const handleOpenInMobile = () => {
  emit('open-mobile', currentData.value)
}
</script>

<style scoped>
.mobile-preview-container {
  display: flex;
  justify-content: center;
  padding: 20px 0;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 12px;
}

.phone-frame {
  width: 375px;
  height: 667px;
  background: var(--bg-card);
  border-radius: 40px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3), inset 0 0 0 2px var(--text-primary);
  overflow: hidden;
  position: relative;
}

.phone-frame::before {
  content: '';
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 150px;
  height: 30px;
  background: var(--text-primary);
  border-radius: 0 0 20px 20px;
  z-index: 10;
}

.status-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px 8px;
  background: var(--text-primary);
  color: var(--bg-card);
  font-size: 14px;
}

.time {
  font-weight: 600;
}

.status-icons {
  display: flex;
  gap: 6px;
}

.icon {
  font-size: 14px;
}

.app-content {
  height: calc(100% - 45px);
  overflow-y: auto;
  background: #f5f5f5;
}

.app-content::-webkit-scrollbar {
  width: 0;
}

/* 顶部项目信息 */
.project-header {
  background: linear-gradient(135deg, #409EFF 0%, #79bbff 100%);
  padding: 40px 20px 20px;
  color: var(--bg-card);
}

.project-name {
  font-size: 20px;
  font-weight: bold;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 倒计时 */
.countdown-section {
  background: var(--bg-card);
  margin: 16px;
  padding: 20px;
  border-radius: 16px;
  text-align: center;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.countdown-label {
  font-size: 14px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.countdown-value {
  font-size: 32px;
  font-weight: bold;
  color: #409EFF;
}

/* 赢面评分 */
.win-score-section {
  background: var(--bg-card);
  margin: 0 16px 16px;
  padding: 20px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  gap: 20px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.score-circle {
  width: 70px;
  height: 70px;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.score-circle.high {
  background: linear-gradient(135deg, #67C23A 0%, #95d475 100%);
}

.score-circle.medium {
  background: linear-gradient(135deg, #E6A23C 0%, #f0c78a 100%);
}

.score-circle.low {
  background: linear-gradient(135deg, #F56C6C 0%, #f89898 100%);
}

.score-value {
  font-size: 24px;
  font-weight: bold;
  color: var(--bg-card);
}

.score-label {
  font-size: 12px;
  color: rgba(255, 255, 255, 0.9);
}

.score-detail {
  flex: 1;
}

/* 信息卡片 */
.info-card {
  background: var(--bg-card);
  margin: 0 16px 16px;
  padding: 16px;
  border-radius: 16px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.card-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--gray-750);
  margin-bottom: 12px;
}

.title-icon {
  font-size: 18px;
}

.info-row {
  display: flex;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid #f0f0f0;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 14px;
  color: var(--text-muted);
}

.info-value {
  font-size: 14px;
  color: var(--gray-750);
  font-weight: 500;
}

/* 任务列表 */
.task-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.task-item {
  display: flex;
  gap: 12px;
}

.task-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #409EFF;
  margin-top: 6px;
  flex-shrink: 0;
}

.task-content {
  flex: 1;
}

.task-title {
  font-size: 14px;
  color: var(--gray-750);
  margin-bottom: 4px;
}

.task-due {
  font-size: 12px;
  color: var(--text-muted);
}

/* 风险列表 */
.risk-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.risk-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  border-radius: 8px;
  font-size: 13px;
}

.risk-item.high {
  background: #fef0f0;
  color: #F56C6C;
}

.risk-item.medium {
  background: #fdf6ec;
  color: #E6A23C;
}

.risk-item.low {
  background: #f4f4f5;
  color: var(--text-muted);
}

.risk-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.risk-text {
  flex: 1;
}

/* 底部导航 */
.bottom-nav {
  position: sticky;
  bottom: 0;
  display: flex;
  background: var(--bg-card);
  border-top: 1px solid var(--gray-250);
  padding: 8px 0;
  padding-bottom: max(8px, env(safe-area-inset-bottom));
}

.nav-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  color: var(--text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: color 0.3s;
}

.nav-item.active {
  color: #409EFF;
}

.nav-icon {
  font-size: 20px;
}

.nav-label {
  font-size: 11px;
}

.no-data {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

:deep(.el-empty) {
  padding: 60px 0;
}
</style>
