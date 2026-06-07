<template>
  <div class="ai-analysis-page">
    <div class="page-header">
      <div class="ai-header-left">
        <div class="ai-title-section">
          <h2 class="ai-page-title">{{ tenderInfo?.title || '加载中...' }}</h2>
          <el-tag type="success" size="small">AI分析报告</el-tag>
        </div>
      </div>
      <div class="ai-header-actions">
        <el-button @click="handleExport">
          <el-icon><Download /></el-icon>
          导出报告
        </el-button>
      </div>
    </div>

    <el-alert
      v-if="loadError"
      class="ai-load-error"
      :title="loadError"
      type="error"
      show-icon
      :closable="false"
    />

    <div v-if="analysisData" class="main-content">
      <div class="ai-left-section">
        <el-card class="score-card" shadow="never">
          <div class="win-score-display">
            <div
              class="score-circle"
              :class="getScoreLevelClass(analysisData.winScore)"
              :style="{ '--score': analysisData.winScore }"
            >
              <div class="score-inner">
                <div class="score-value">{{ analysisData.winScore }}</div>
                <div class="score-label">AI分析分</div>
              </div>
            </div>
            <div v-if="analysisData.suggestion" class="score-suggestion">
              <el-icon><Guide /></el-icon>
              {{ analysisData.suggestion }}
            </div>
          </div>

          <MatchScorePanel
            :score="matchScoreForDisplay"
            :loading="matchScoreLoading"
            :error="matchScoreError"
            :show-action="false"
            compact
          />

          <div v-if="matchScoreForDisplay?.dimensionSummaries?.length" class="radar-section">
            <h4 class="ai-section-title">维度分析</h4>
            <WinScoreChart :dimension-scores="matchScoreForDisplay.dimensionSummaries" />
          </div>
        </el-card>
      </div>

      <div class="ai-right-section">
        <el-card class="detail-card" shadow="never">
          <template #header>
            <div class="ai-card-header">
              <span>维度详情</span>
              <el-button link type="primary" size="small" @click="expandAll = !expandAll">
                {{ expandAll ? '收起全部' : '展开全部' }}
              </el-button>
            </div>
          </template>
          <el-collapse v-model="activeDimensions" accordion>
            <el-collapse-item v-for="dim in dimensionDetails" :key="dim.name" :name="dim.name">
              <template #title>
                <div class="collapse-title">
                  <span>{{ dim.name }}</span>
                  <el-tag :type="getDimensionTagType(dim.score)" size="small">{{ dim.score }}分</el-tag>
                </div>
              </template>
              <div class="collapse-content">
                <div class="detail-item">
                  <span class="detail-label">评估说明：</span>
                  <span class="detail-value">{{ dim.description }}</span>
                </div>
                <div class="detail-item">
                  <span class="detail-label">改进建议：</span>
                  <span class="detail-value suggestion">{{ dim.suggestion }}</span>
                </div>
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-card>

        <el-card class="risk-card" shadow="never">
          <template #header>
            <div class="ai-card-header">
              <el-icon class="ai-header-icon"><Warning /></el-icon>
              <span>关键风险</span>
              <el-badge :value="analysisData.risks.length" type="danger" />
            </div>
          </template>
          <div class="risk-list">
            <div
              v-for="(risk, index) in analysisData.risks"
              :key="index"
              class="risk-item"
              :class="'risk-' + risk.level"
            >
              <div class="risk-header">
                <el-tag :type="risk.level === 'high' ? 'danger' : 'warning'" size="small">
                  {{ risk.level === 'high' ? '高风险' : '中风险' }}
                </el-tag>
                <span class="risk-desc">{{ risk.desc }}</span>
              </div>
              <div class="risk-action">
                <el-icon><Guide /></el-icon>
                建议操作：{{ risk.action }}
              </div>
            </div>
          </div>
        </el-card>

        <el-card class="task-card" shadow="never">
          <template #header>
            <div class="ai-card-header">
              <el-icon class="ai-header-icon"><List /></el-icon>
              <span>任务清单 (同步到日程)</span>
              <el-button link type="primary" size="small" @click="handleSyncTasks">
                <el-icon><Refresh /></el-icon>
                同步到日程
              </el-button>
            </div>
          </template>
          <div class="task-list">
            <div v-for="task in analysisData.autoTasks" :key="task.id" class="task-item">
              <el-checkbox v-model="task.completed" @change="handleTaskCheck(task)">
                <span class="task-title" :class="{ completed: task.completed }">{{ task.title }}</span>
              </el-checkbox>
              <div class="task-meta">
                <el-tag :type="getPriorityTagType(task.priority)" size="small">
                  {{ task.priority === 'high' ? '高优先级' : '中优先级' }}
                </el-tag>
                <span class="task-owner">
                  <el-icon><User /></el-icon>
                  {{ task.owner }}
                </span>
                <span class="task-due">
                  <el-icon><Calendar /></el-icon>
                  {{ task.dueDate }}
                </span>
              </div>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <el-card v-else class="empty-card" shadow="never">
      <el-empty description="当前模式下暂无可用的 AI 分析报告" />
    </el-card>

    <div class="bottom-actions">
      <el-button size="large" @click="handleAddToPool">
        <el-icon><Star /></el-icon>
        加入意向池
      </el-button>
      <el-button type="primary" size="large" @click="handleCreateProject">
        <el-icon><Plus /></el-icon>
        创建投标项目
      </el-button>
    </div>

    <el-dialog
      v-model="showParsingDialog"
      title="AI分析中"
      width="480px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :show-close="false"
    >
      <div class="parsing-content">
        <div class="parsing-animation">
          <div class="parsing-spinner"></div>
        </div>
        <p class="parsing-text">正在解析招标文件...</p>
        <el-progress :percentage="parseProgress" :stroke-width="12" :color="progressColors" />
        <p class="parsing-hint">AI正在分析标书文档，提取关键信息</p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { Calendar, Download, Guide, List, Plus, Refresh, Star, User, Warning } from '@element-plus/icons-vue'
import WinScoreChart from '@/components/ai/WinScoreChart.vue'
import MatchScorePanel from '../match-scoring/MatchScorePanel.vue'
import { useAiAnalysisPage } from './useAiAnalysisPage.js'
import './styles/ai-analysis-layout.css'
import './styles/ai-analysis-overrides.css'

const {
  tenderInfo,
  analysisData,
  expandAll,
  activeDimensions,
  showParsingDialog,
  parseProgress,
  loadError,
  matchScoreForDisplay,
  matchScoreLoading,
  matchScoreError,
  progressColors,
  dimensionDetails,
  getScoreLevelClass,
  getDimensionTagType,
  getPriorityTagType,
  handleExport,
  handleSyncTasks,
  handleTaskCheck,
  handleAddToPool,
  handleCreateProject,
} = useAiAnalysisPage()
</script>
