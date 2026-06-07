<template>
  <div class="score-coverage">
    <!-- 总体覆盖率 -->
    <div class="coverage-header">
      <div class="coverage-info">
        <h3 class="coverage-title">评分点覆盖率</h3>
        <div class="coverage-percentage">
          <span class="percentage-value">{{ overallPercentage }}%</span>
          <span class="percentage-label">总覆盖率</span>
        </div>
      </div>
      <div class="coverage-chart">
        <el-progress
          type="circle"
          :percentage="overallPercentage"
          :width="120"
          :stroke-width="10"
          :color="progressColor"
        >
          <template #default="{ percentage }">
            <span class="progress-value">{{ percentage }}%</span>
          </template>
        </el-progress>
      </div>
    </div>

    <!-- 分类覆盖进度 -->
    <el-divider content-position="left">分类覆盖率</el-divider>
    <div class="category-progress">
      <div
        v-for="category in scoreCategories"
        :key="category.name"
        class="category-item"
      >
        <div class="category-header">
          <span class="category-name">{{ category.name }}</span>
          <span class="category-weight">权重: {{ category.weight }}%</span>
        </div>
        <div class="category-score">
          <span class="score-covered">{{ category.covered }}</span>
          <span class="score-separator">/</span>
          <span class="score-total">{{ category.total }}</span>
          <span class="score-percentage" :class="getPercentageClass(category.percentage)">
            {{ formatPercentage(category.percentage) }}%
          </span>
        </div>
        <el-progress
          :percentage="formatPercentage(category.percentage)"
          :stroke-width="8"
          :show-text="false"
          :color="getCategoryColor(category.percentage)"
        />
        <div v-if="category.gaps && category.gaps.length > 0" class="category-gaps">
          <el-tag
            v-for="(gap, index) in category.gaps"
            :key="index"
            type="danger"
            size="small"
            class="gap-tag"
          >
            {{ gap }}
          </el-tag>
        </div>
      </div>
    </div>

    <!-- 证据缺口清单 -->
    <el-divider content-position="left">证据缺口清单</el-divider>
    <el-table
      :data="gapItems"
      border
      stripe
      size="small"
      class="gap-table"
    >
      <el-table-column prop="category" label="类别" width="80">
        <template #default="{ row }">
          <el-tag :type="getCategoryTagType(row.category)" size="small">
            {{ row.category }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="scorePoint" label="评分点" min-width="120" />
      <el-table-column prop="required" label="所需证据" min-width="180" />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'covered' ? 'success' : 'danger'" size="small">
            {{ row.status === 'covered' ? '已覆盖' : '缺失' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.status === 'missing'"
            type="primary"
            size="small"
            link
            @click="fillFromKnowledge(row)"
          >
            从知识库补齐
          </el-button>
          <span v-else class="text-success">已完成</span>
        </template>
      </el-table-column>
    </el-table>

    <!-- 空状态 -->
    <el-empty
      v-if="gapItems.length === 0"
      description="暂无证据缺口"
      :image-size="100"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  scoreCategories: {
    type: Array,
    default: () => []
  },
  gapItems: {
    type: Array,
    default: () => []
  }
})

// 格式化百分比，保留2位小数
const formatPercentage = (value) => {
  return Number(value).toFixed(2)
}

// 计算总体覆盖率（加权平均）
const overallPercentage = computed(() => {
  if (!props.scoreCategories || props.scoreCategories.length === 0) {
    return 0
  }

  let totalWeight = 0
  let weightedSum = 0

  props.scoreCategories.forEach(category => {
    weightedSum += category.percentage * category.weight
    totalWeight += category.weight
  })

  return totalWeight > 0 ? Math.round(weightedSum / totalWeight) : 0
})

// 根据百分比获取颜色
const getCategoryColor = (percentage) => {
  if (percentage >= 80) return 'var(--color-success)'
  if (percentage >= 50) return 'var(--color-warning)'
  return 'var(--color-danger)'
}

// 进度条颜色
const progressColor = computed(() => {
  return getCategoryColor(overallPercentage.value)
})

// 根据百分比获取样式类
const getPercentageClass = (percentage) => {
  if (percentage >= 80) return 'percentage-high'
  if (percentage >= 50) return 'percentage-medium'
  return 'percentage-low'
}

// 根据类别获取标签类型
const getCategoryTagType = (category) => {
  const typeMap = {
    '技术': 'primary',
    '商务': 'success',
    '案例': 'warning',
    '服务': 'info'
  }
  return typeMap[category] || ''
}

// 从知识库补齐证据
const fillFromKnowledge = (_row) => {
  // 触发事件，由父组件处理
  // 实际项目中可以调用 emit
}

// 定义 emit（供父组件监听）
defineEmits(['fill-from-knowledge'])
</script>

<style scoped>
.score-coverage {
  padding: 16px;
}

.coverage-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
}

.coverage-info {
  flex: 1;
}

.coverage-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-primary);
  margin: 0 0 12px 0;
}

.coverage-percentage {
  display: flex;
  flex-direction: column;
}

.percentage-value {
  font-size: 36px;
  font-weight: 600;
  line-height: 1;
  color: var(--el-color-primary);
}

.percentage-label {
  font-size: 14px;
  color: var(--text-muted);
  margin-top: 4px;
}

.coverage-chart {
  flex-shrink: 0;
}

.progress-value {
  font-size: 24px;
  font-weight: 600;
  color: var(--gray-750);
}

.category-progress {
  margin-bottom: 24px;
}

.category-item {
  margin-bottom: 20px;
  padding: 16px;
  background-color: var(--bg-subtle);
  border-radius: 8px;
}

.category-item:last-child {
  margin-bottom: 0;
}

.category-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.category-name {
  font-size: 15px;
  font-weight: 500;
  color: var(--gray-750);
}

.category-weight {
  font-size: 12px;
  color: var(--text-muted);
}

.category-score {
  display: flex;
  align-items: baseline;
  margin-bottom: 8px;
  gap: 4px;
}

.score-covered {
  font-size: 16px;
  font-weight: 600;
  color: var(--gray-750);
}

.score-separator {
  color: var(--text-muted);
}

.score-total {
  font-size: 14px;
  color: var(--text-muted);
}

.score-percentage {
  margin-left: auto;
  font-size: 14px;
  font-weight: 500;
}

.percentage-high {
  color: var(--color-success);
}

.percentage-medium {
  color: var(--color-warning);
}

.percentage-low {
  color: var(--color-danger);
}

.category-gaps {
  margin-top: 12px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.gap-tag {
  max-width: 100%;
}

.gap-table {
  margin-top: 16px;
}

.text-success {
  color: var(--color-success);
  font-size: 12px;
}

/* 响应式样式 */
@media (max-width: 768px) {
  .coverage-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 16px;
  }

  .coverage-chart {
    width: 100%;
    display: flex;
    justify-content: center;
  }

  .percentage-value {
    font-size: 28px;
  }

  .category-item {
    padding: 12px;
  }

  .category-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 4px;
  }

  .score-percentage {
    margin-left: 0;
  }

  :deep(.el-table) {
    font-size: 12px;
  }

  :deep(.el-table .cell) {
    padding: 8px 4px;
  }
}
</style>
