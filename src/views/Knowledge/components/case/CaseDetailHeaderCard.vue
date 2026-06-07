<template>
  <el-card class="info-card" shadow="never">
    <template #header>
      <div class="card-header">
        <div class="header-left">
          <h2 class="case-title">{{ caseData.title }}</h2>
          <div class="tags-row">
            <el-tag v-for="tag in caseData.tags" :key="tag" size="small">
              {{ tag }}
            </el-tag>
          </div>
        </div>
        <div class="header-right">
          <el-button plain @click="$emit('edit')">
            <el-icon><Edit /></el-icon>
            编辑案例
          </el-button>
          <el-button type="primary" @click="$emit('use-case')">
            <el-icon><DocumentCopy /></el-icon>
            引用此案例
          </el-button>
          <el-button @click="$emit('share')">
            <el-icon><Share /></el-icon>
            分享
          </el-button>
        </div>
      </div>
    </template>

    <el-descriptions :column="3" border>
      <el-descriptions-item label="客户名称">
        {{ caseData.customer || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="项目金额">
        <span class="amount-text">{{ formatAmount(caseData.amount) }}</span>
      </el-descriptions-item>
      <el-descriptions-item label="项目年份">
        {{ caseData.year || '-' }}年
      </el-descriptions-item>
      <el-descriptions-item label="所属地区">
        {{ caseData.location || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="所属行业">
        {{ getIndustryLabel(caseData.industry) }}
      </el-descriptions-item>
      <el-descriptions-item label="实施周期">
        {{ caseData.projectPeriod || caseData.period || '-' }}
      </el-descriptions-item>
      <el-descriptions-item label="引用次数" :span="3">
        <span class="use-count-text">
          <el-icon><View /></el-icon>
          已被引用 {{ caseData.useCount || 0 }} 次
        </span>
      </el-descriptions-item>
    </el-descriptions>
  </el-card>
</template>

<script setup>
import { DocumentCopy, Edit, Share, View } from '@element-plus/icons-vue'
import { formatAmount, getIndustryLabel } from './caseMeta.js'

defineProps({
  caseData: {
    type: Object,
    required: true
  }
})

defineEmits(['edit', 'share', 'use-case'])
</script>

<style scoped lang="scss">
.info-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 20px;
}

.header-left {
  flex: 1;
}

.case-title {
  font-size: 20px;
  font-weight: 600;
  color: var(--gray-750);
  margin: 0 0 12px 0;
  line-height: 1.5;
}

.tags-row {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.header-right {
  flex-shrink: 0;
  display: flex;
  gap: 12px;
}

.amount-text {
  font-size: 18px;
  font-weight: 600;
  color: #f56c6c;
}

.use-count-text {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  color: #409eff;
}

:deep(.el-card__header) {
  padding: 16px 20px;
  border-bottom: 1px solid var(--gray-250);
}

:deep(.el-card__body) {
  padding: 20px;
}

:deep(.el-descriptions__label) {
  font-weight: 500;
  background-color: #fafafa;
}

@media (max-width: 768px) {
  .card-header {
    flex-direction: column;
    align-items: flex-start;
    gap: 12px;
  }

  .header-right {
    width: 100%;
    flex-wrap: wrap;
  }

  .header-right .el-button {
    width: 100%;
  }

  .case-title {
    font-size: 18px;
  }

  :deep(.el-descriptions) {
    font-size: 12px;
  }

  :deep(.el-descriptions__label) {
    width: 100px !important;
  }
}
</style>
