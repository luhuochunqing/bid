<template>
  <div class="ai-center-page">
    <!-- 顶部标题栏 -->
    <div class="page-header">
      <div class="header-left">
        <h2 class="page-title">AI 智能中心</h2>
      </div>
      <div class="header-actions">
        <el-button @click="handleSave">
          <el-icon><Document /></el-icon>
          保存
        </el-button>
        <el-button @click="handleReset">
          <el-icon><RefreshLeft /></el-icon>
          重置
        </el-button>
        <el-button type="primary" @click="handleExport">
          <el-icon><Download /></el-icon>
          导出
        </el-button>
      </div>
    </div>

    <!-- 主内容区 -->
    <div class="main-content">
      <el-tabs v-model="activeTab" class="ai-tabs">
        <!-- 投标准备 Tab -->
        <el-tab-pane label="投标准备" name="prepare">
          <div class="tab-content">
            <FeatureCard
              v-for="feature in prepareFeatures"
              :key="feature.id"
              :feature="feature"
              @toggle="handleToggle"
              @configure="handleConfigure"
            />
          </div>
        </el-tab-pane>

        <!-- 标书编制 Tab -->
        <el-tab-pane label="标书编制" name="compile">
          <div class="tab-content">
            <FeatureCard
              v-for="feature in compileFeatures"
              :key="feature.id"
              :feature="feature"
              @toggle="handleToggle"
              @configure="handleConfigure"
            />
          </div>
        </el-tab-pane>

        <!-- 团队协作 Tab -->
        <el-tab-pane label="团队协作" name="collab">
          <div class="tab-content">
            <FeatureCard
              v-for="feature in collabFeatures"
              :key="feature.id"
              :feature="feature"
              @toggle="handleToggle"
              @configure="handleConfigure"
            />
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 配置对话框 -->
    <ConfigDialog
      v-model="showConfigDialog"
      :config-id="currentConfigId"
      :config-data="currentConfigData"
      @save="handleSaveConfig"
      @test="handleTestConfig"
    />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Document, RefreshLeft, Download } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import FeatureCard from './components/FeatureCard.vue'
import ConfigDialog from '@/components/ai/ConfigDialog.vue'
import { getConfigById } from '@/config/ai-prompts'

// Tab 切换
const activeTab = ref('prepare')
const router = useRouter()

// 配置对话框状态
const showConfigDialog = ref(false)
const currentConfigId = ref('')
const currentConfigData = ref(null)

// 投标准备功能列表
const prepareFeatures = ref([
  {
    id: 'ai-analysis',
    icon: 'analysis',
    name: 'AI 分析',
    description: '智能分析招标文件，提取关键信息和风险点',
    enabled: true,
    stats: { usageCount: 128, accuracy: 94.5 },
    promptTemplate: '请分析以下招标文件，提取项目概况、资格要求、评分标准等核心要素...'
  },
  {
    id: 'score-coverage',
    icon: 'score',
    name: '评分点覆盖',
    description: '自动匹配评分点与响应内容，确保不漏项',
    enabled: true,
    stats: { usageCount: 96, accuracy: 91.2 },
    promptTemplate: '根据招标文件评分标准，检查投标文件中各评分点的响应完整性...'
  },
  {
    id: 'competition-intel',
    icon: 'intel',
    name: '竞争情报',
    description: '分析竞争对手策略，提供差异化建议',
    enabled: false,
    stats: { usageCount: 42, accuracy: 87.8 },
    promptTemplate: '基于历史中标数据分析竞争对手的报价策略、技术方案特点...'
  },
  {
    id: 'roi-analysis',
    icon: 'roi',
    name: 'ROI 核算',
    description: '智能测算项目投入产出比，辅助决策',
    enabled: true,
    stats: { usageCount: 67, accuracy: 89.3 },
    promptTemplate: '根据项目规模、资源投入、历史数据，综合测算项目的预期收益和投资回报率...'
  }
])

// 标书编制功能列表
const compileFeatures = ref([
  {
    id: 'smart-assembly',
    icon: 'assembly',
    name: '智能装配',
    description: '基于模板自动组装标书内容，提升编制效率',
    enabled: true,
    stats: { usageCount: 156, accuracy: 92.7 },
    promptTemplate: '根据招标要求，从知识库中匹配相关内容，自动生成技术方案章节...'
  },
  {
    id: 'compliance-radar',
    icon: 'compliance',
    name: '合规雷达',
    description: '全方位扫描标书合规风险，智能预警',
    enabled: true,
    stats: { usageCount: 189, accuracy: 96.1 },
    promptTemplate: '检查投标文件的完整性、格式合规性、关键条款响应情况...'
  },
  {
    id: 'solution-reuse',
    icon: 'reuse',
    name: '历史方案提取与复用',
    description: '检索历史投标方案，智能匹配推荐、快速复用',
    enabled: true,
    stats: { usageCount: 0, accuracy: 0 },
    promptTemplate: '根据项目需求，从历史方案库中搜索并推荐最匹配的投标方案...',
    link: '/ai-center/solution-reuse'
  }
])

// 团队协作功能列表
const collabFeatures = ref([
  {
    id: 'version-control',
    icon: 'version',
    name: '版本管理',
    description: '智能追踪文档变更，支持版本对比和回溯',
    enabled: true,
    stats: { usageCount: 234, accuracy: 99.2 },
    promptTemplate: '记录文档每次修改的内容摘要，自动生成变更说明...'
  },
  {
    id: 'collab-center',
    icon: 'collab',
    name: '协作中心',
    description: '团队任务分配与进度跟踪，智能提醒',
    enabled: true,
    stats: { usageCount: 178, accuracy: 93.5 },
    promptTemplate: '根据项目进度和人员负荷，智能分配任务并设置关键节点提醒...'
  },
  {
    id: 'auto-tasks',
    icon: 'tasks',
    name: '自动化任务',
    description: '自定义自动化规则，减少重复操作',
    enabled: false,
    stats: { usageCount: 55, accuracy: 88.9 },
    promptTemplate: '配置触发条件和执行动作，实现投标流程自动化处理...'
  }
])

// 处理启用/禁用切换
const handleToggle = (featureId, enabled) => {
  const allFeatures = [
    ...prepareFeatures.value,
    ...compileFeatures.value,
    ...collabFeatures.value
  ]
  const feature = allFeatures.find(f => f.id === featureId)
  if (feature) {
    feature.enabled = enabled
    ElMessage.success(`${feature.name} 已${enabled ? '启用' : '禁用'}`)
  }
}

// 处理配置按钮点击
const handleConfigure = (featureId) => {
  const allFeatures = [
    ...prepareFeatures.value,
    ...compileFeatures.value,
    ...collabFeatures.value
  ]
  const feature = allFeatures.find(f => f.id === featureId)
  if (feature?.link) { router.push(feature.link); return }

  currentConfigId.value = featureId
  const config = getConfigById(featureId)
  if (config) {
    currentConfigData.value = config
    showConfigDialog.value = true
  } else {
    // 使用本地功能数据作为后备
    if (feature) {
      currentConfigData.value = {
        id: feature.id,
        name: feature.name,
        promptTemplate: {
          role: '你是一位资深的投标专家',
          task: feature.promptTemplate,
          outputFormat: '请以 JSON 格式输出分析结果'
        },
        formConfig: {
          winRateWeights: { technical: 30, commercial: 30, price: 20, service: 20 },
          riskThreshold: 'medium'
        }
      }
      showConfigDialog.value = true
    }
  }
}

// 保存配置
const handleSaveConfig = () => {
  ElMessage.success('配置保存成功')
}

// 测试配置
const handleTestConfig = () => {
  ElMessage.info('正在测试配置...')
  setTimeout(() => {
    ElMessage.success('测试运行完成')
  }, 2000)
}

// 保存配置
const handleSave = async () => {
  try {
    await ElMessageBox.confirm('确认保存当前配置？', '保存确认', {
      type: 'info'
    })
    ElMessage.success('配置保存成功')
  } catch {
    // 用户取消
  }
}

// 重置配置
const handleReset = async () => {
  try {
    await ElMessageBox.confirm('确认重置所有配置为默认值？', '重置确认', {
      type: 'warning'
    })
    ElMessage.success('配置已重置')
  } catch {
    // 用户取消
  }
}

// 导出配置
const handleExport = () => {
  ElMessage.success('配置导出成功')
}
</script>

<script>
export default {
  name: 'AICenter'
}
</script>

<style scoped>
.ai-center-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-subtle);
}
/* 顶部标题栏 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  background: var(--bg-card);
  border-bottom: 1px solid var(--gray-250);
}
.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--text-primary);
}
.header-actions {
  display: flex;
  gap: 12px;
}

/* 主内容区 */
.main-content {
  flex: 1;
  padding: 24px;
  overflow: auto;
}

/* Tab 样式 */
.ai-tabs {
  background: var(--bg-card);
  border-radius: var(--card-border-radius, 8px);
  box-shadow: var(--card-shadow, 0 1px 3px rgba(0, 0, 0, 0.08), 0 1px 2px rgba(0, 0, 0, 0.04));
  border: var(--card-border, 1px solid var(--gray-100));
}

.ai-tabs :deep(.el-tabs__header) {
  margin: 0;
  padding: 0 24px;
  border-bottom: 1px solid var(--gray-250);
}

.ai-tabs :deep(.el-tabs__nav-wrap) {
  padding: 16px 0;
}
.ai-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  font-weight: 500;
  padding: 0 24px;
  height: 40px;
  line-height: 40px;
}
.ai-tabs :deep(.el-tabs__content) {
  padding: 24px;
}

/* Tab 内容区 */

@media (max-width: 768px) {
  .tab-content {
    grid-template-columns: 1fr;
  }

  .page-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .header-actions {
    width: 100%;
    justify-content: flex-end;
  }
}

/* ==================== Button ==================== */
.header-actions .el-button {
  min-width: 90px;
  height: 38px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
}
.header-actions .el-button--primary {
  background: linear-gradient(135deg, var(--brand-xiyu-logo), var(--brand-xiyu-logo-hover));
  border: none;
}

/* ==================== Tab ==================== */
.ai-tabs :deep(.el-tabs__item) {
  font-size: 15px;
  font-weight: 500;
  color: var(--text-slate);
}
.ai-tabs :deep(.el-tabs__item.is-active) {
  color: var(--accent-blue);
  font-weight: 600;
}
</style>
