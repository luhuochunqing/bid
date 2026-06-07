<template>
  <div class="case-detail-page">
    <div v-if="caseData" class="detail-content">
      <CaseDetailHeaderCard
        :case-data="caseData"
        @edit="handleEdit"
        @share="handleShare"
        @use-case="handleUseCase"
      />

      <el-card class="section-card" shadow="never">
        <template #header>
          <div class="card-title-with-icon">
            <el-icon><Document /></el-icon>
            项目概述
          </div>
        </template>
        <div class="content-text">
          {{ caseData.description || '-' }}
        </div>
      </el-card>

      <el-card class="section-card" shadow="never">
        <template #header>
          <div class="card-title-with-icon">
            <el-icon><Star /></el-icon>
            项目亮点
          </div>
        </template>
        <div class="highlights-list">
          <div v-for="(highlight, index) in caseData.highlights" :key="index" class="highlight-item">
            <el-icon class="highlight-icon"><CircleCheckFilled /></el-icon>
            <span>{{ highlight }}</span>
          </div>
        </div>
      </el-card>

      <el-card v-if="caseData.technologies && caseData.technologies.length" class="section-card" shadow="never">
        <template #header>
          <div class="card-title-with-icon">
            <el-icon><Cpu /></el-icon>
            技术架构
          </div>
        </template>
        <div class="tech-tags">
          <el-tag
            v-for="tech in caseData.technologies"
            :key="tech"
            type="info"
            effect="plain"
            size="large"
          >
            {{ tech }}
          </el-tag>
        </div>
      </el-card>

      <el-card class="related-cases-card" shadow="never">
        <template #header>
          <div class="card-title-with-icon">
            <el-icon><Briefcase /></el-icon>
            相关案例
          </div>
        </template>
        <div class="related-cases-list">
          <div
            v-for="relatedCase in relatedCases"
            :key="relatedCase.id"
            class="related-case-item"
            @click="handleViewRelated(relatedCase.id)"
          >
            <div class="related-case-icon">
              <el-icon><Document /></el-icon>
            </div>
            <div class="related-case-content">
              <h5 class="related-case-title">{{ relatedCase.title }}</h5>
              <div class="related-case-meta">
                <span>{{ relatedCase.customer }}</span>
                <span>{{ relatedCase.amount }}万元</span>
              </div>
            </div>
            <div class="related-case-arrow">
              <el-icon><ArrowRight /></el-icon>
            </div>
          </div>
        </div>
      </el-card>
    </div>

    <CaseDetailEditDialog
      v-model="editDialogVisible"
      v-model:form="editForm"
      :saving="saving"
      @save="handleSaveEdit"
    />

    <div v-if="loading && !caseData" class="loading-container">
      <el-skeleton :rows="6" animated />
    </div>

    <div v-if="!caseData && !loading" class="empty-container">
      <el-empty description="案例不存在或已删除">
        <el-button type="primary" @click="router.push('/knowledge/case')">
          返回案例列表
        </el-button>
      </el-empty>
    </div>
  </div>
</template>

<script setup>
import { ArrowRight, Briefcase, CircleCheckFilled, Cpu, Document, Star } from '@element-plus/icons-vue'
import CaseDetailEditDialog from './components/case/CaseDetailEditDialog.vue'
import CaseDetailHeaderCard from './components/case/CaseDetailHeaderCard.vue'
import { useCaseDetailPage } from './components/case/useCaseDetailPage.js'

const {
  caseData,
  editDialogVisible,
  editForm,
  handleEdit,
  handleSaveEdit,
  handleShare,
  handleUseCase,
  handleViewRelated,
  loading,
  relatedCases,
  router,
  saving
} = useCaseDetailPage()
</script>

<style scoped>
.case-detail-page {
  padding: 20px;
  background-color: var(--bg-subtle);
  min-height: 100vh;
}

.detail-content {
  max-width: 1200px;
  margin: 0 auto;
}

.section-card {
  margin-bottom: 20px;
}

.card-title-with-icon {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--gray-750);
}

.card-title-with-icon .el-icon {
  color: #409eff;
  font-size: 18px;
}

.content-text {
  font-size: 14px;
  color: var(--text-secondary-ui);
  line-height: 1.8;
  white-space: pre-line;
}

.highlights-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.highlight-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 14px;
  color: var(--text-secondary-ui);
}

.highlight-icon {
  color: #67c23a;
  font-size: 18px;
  flex-shrink: 0;
  margin-top: 2px;
}

.tech-tags {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.related-cases-card {
  margin-bottom: 20px;
}

.related-cases-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.related-case-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  background: var(--bg-subtle);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.3s;
}

.related-case-item:hover {
  background: #ecf5ff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
}

.related-case-icon {
  width: 40px;
  height: 40px;
  border-radius: 8px;
  background: linear-gradient(135deg, #409eff, #66b1ff);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.related-case-icon .el-icon {
  font-size: 20px;
  color: var(--bg-card);
}

.related-case-content {
  flex: 1;
  min-width: 0;
}

.related-case-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--gray-750);
  margin: 0 0 4px 0;
}

.related-case-meta {
  display: flex;
  gap: 12px;
  font-size: 12px;
  color: var(--text-muted);
}

.related-case-arrow {
  color: #c0c4cc;
  transition: color 0.3s;
}

.related-case-item:hover .related-case-arrow {
  color: #409eff;
}

.loading-container,
.empty-container {
  padding: 40px;
  background: var(--bg-card);
  border-radius: 8px;
}

@media (max-width: 768px) {
  .case-detail-page {
    padding: 12px;
  }

  .tech-tags {
    gap: 8px;
  }
}
</style>
