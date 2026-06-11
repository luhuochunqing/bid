<template>
  <div class="template-container">
    <div class="page-header">
      <div>
        <h2 class="page-title">模板库</h2>
        <p class="page-subtitle">产品类型、行业、文档类型是正式分类主入口，历史大类只作为辅助浏览视图保留。</p>
      </div>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="openCreateDialog">
          新建模板
        </el-button>
      </div>
    </div>

    <TemplateFilterPanel
      v-model:filters="filters"
      :all-tags="allTags"
      :product-type-options="PRODUCT_TYPE_OPTIONS"
      :industry-options="INDUSTRY_OPTIONS"
      :document-type-options="DOCUMENT_TYPE_OPTIONS"
      @search="handleSearch"
      @reset="handleReset"
    />

    <section class="category-tabs" aria-label="历史大类辅助视图">
      <div class="tabs-head">
        <span class="tabs-eyebrow">历史大类辅助视图</span>
        <p class="tabs-hint">只在需要回看旧分类口径时切换；正式筛选以上方工作区为准。</p>
      </div>
      <el-tabs v-model="activeCategory" @tab-change="handleCategoryChange">
        <el-tab-pane
          v-for="tab in categoryTabs"
          :key="tab.name"
          :name="tab.name"
        >
          <template #label>
            <span class="tab-label">
              <el-icon><component :is="tab.icon" /></el-icon>
              {{ tab.label }}
            </span>
          </template>
        </el-tab-pane>
      </el-tabs>
    </section>

    <TemplateFilterSummaryBar
      :items="filterSummaryItems"
      @clear="handleReset"
    />

    <FeaturePlaceholder
      v-if="featurePlaceholder"
      class="placeholder-card"
      :title="featurePlaceholder.title"
      :message="featurePlaceholder.message"
      :hint="featurePlaceholder.hint"
    />
    <TemplateWorkspaceState
      v-else-if="workspaceEmptyState"
      :state="workspaceEmptyState"
      @create="openCreateDialog"
      @clear="handleReset"
    />
    <section v-else class="results-section" aria-label="模板资产结果区">
      <div class="results-header">
        <div>
          <p class="results-eyebrow">模板资产结果</p>
          <h3 class="results-title">按正式三维分类沉淀的标准模板资产</h3>
        </div>
        <p class="results-meta">共 {{ filteredTemplates.length }} 份模板，支持预览、复制、版本查看与直接使用。</p>
      </div>
    <TemplateListTable
      :templates="pagedTemplates"
      :loading="loading"
      :page="pagination.page"
      :page-size="pagination.pageSize"
      :total="filteredTemplates.length"
      @preview="handlePreview"
      @use-template="handleUseTemplate"
      @more-action="handleMoreAction"
      @update:page="pagination.page = $event"
      @update:page-size="pagination.pageSize = $event"
    />
    </section>

    <TemplatePreviewDialog
      v-model:visible="previewDialogVisible"
      v-model:active-tab="activePreviewTab"
      :template="previewTemplate"
      @use-template="handleUseTemplate"
      @download="handleDownload"
    />

    <TemplateUseDialog
      v-model:visible="useTemplateDialogVisible"
      :template="selectedTemplate"
      v-model:form="useTemplateForm"
      :projects="inProgressProjects"
      @confirm="confirmUseTemplate"
    />

    <TemplateUpsertDialog
      v-model:visible="upsertDialogVisible"
      :mode="upsertMode"
      v-model:form="templateForm"
      :errors="templateFormErrors"
      :submit-error="submitError"
      :category-options="categoryOptions"
      :product-type-options="PRODUCT_TYPE_OPTIONS"
      :industry-options="INDUSTRY_OPTIONS"
      :document-type-options="DOCUMENT_TYPE_OPTIONS"
      :submitting="upsertSubmitting"
      @submit="submitTemplate"
    />

    <TemplateVersionDialog
      v-model:visible="versionDialogVisible"
      :versions="versionHistory"
      :placeholder="versionPlaceholder"
    />
  </div>
</template>

<script setup>
import {
  Document,
  DocumentCopy,
  Grid,
  Medal,
  Notebook,
  Operation,
  Plus,
  Tickets
} from '@element-plus/icons-vue'
import FeaturePlaceholder from '@/components/common/FeaturePlaceholder.vue'
import {
  DOCUMENT_TYPE_OPTIONS,
  INDUSTRY_OPTIONS,
  PRODUCT_TYPE_OPTIONS,
  TEMPLATE_CATEGORY_OPTIONS
} from '@/config/templateLibrary.js'
import TemplateFilterPanel from './components/template/TemplateFilterPanel.vue'
import TemplateFilterSummaryBar from './components/template/TemplateFilterSummaryBar.vue'
import TemplateListTable from './components/template/TemplateListTable.vue'
import TemplatePreviewDialog from './components/template/TemplatePreviewDialog.vue'
import TemplateUpsertDialog from './components/template/TemplateUpsertDialog.vue'
import TemplateUseDialog from './components/template/TemplateUseDialog.vue'
import TemplateVersionDialog from './components/template/TemplateVersionDialog.vue'
import TemplateWorkspaceState from './components/template/TemplateWorkspaceState.vue'
import { useTemplateLibraryPage } from './components/template/useTemplateLibraryPage.js'

const categoryTabs = [
  { name: 'all', label: '全部', icon: Grid },
  { name: 'technical', label: '技术方案', icon: Document },
  { name: 'commercial', label: '商务文件', icon: DocumentCopy },
  { name: 'implementation', label: '实施方案', icon: Operation },
  { name: 'quotation', label: '报价清单', icon: Tickets },
  { name: 'qualification', label: '资质文件', icon: Medal },
  { name: 'contract', label: '合同范本', icon: Notebook }
]

const categoryOptions = TEMPLATE_CATEGORY_OPTIONS

const {
  activeCategory,
  filters,
  pagination,
  loading,
  featurePlaceholder,
  versionPlaceholder,
  previewDialogVisible,
  previewTemplate,
  activePreviewTab,
  useTemplateDialogVisible,
  selectedTemplate,
  useTemplateForm,
  versionDialogVisible,
  versionHistory,
  upsertDialogVisible,
  upsertMode,
  templateForm,
  templateFormErrors,
  submitError,
  upsertSubmitting,
  inProgressProjects,
  allTags,
  filterSummaryItems,
  workspaceEmptyState,
  filteredTemplates,
  pagedTemplates,
  handleSearch,
  handleReset,
  handleCategoryChange,
  openCreateDialog,
  submitTemplate,
  handlePreview,
  handleUseTemplate,
  confirmUseTemplate,
  handleMoreAction,
  handleDownload
} = useTemplateLibraryPage()
</script>

<style scoped lang="scss">
.template-container {
  padding: 20px;
  background: var(--bg-page);
  min-height: 100vh;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 20px;
  gap: 16px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: #1f2937;
  margin: 0 0 6px;
}

.page-subtitle {
  margin: 0;
  color: var(--gray-650);
  line-height: 1.5;
}

.category-tabs {
  background: rgba(255, 255, 255, 0.84);
  border: 1px solid #dde7f2;
  border-radius: 18px;
  padding: 14px 20px 0;
  margin-bottom: 20px;
  margin-top: 16px;

  :deep(.el-tabs__header) {
    margin: 0;
  }
}

.tabs-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: baseline;
  margin-bottom: 8px;
}

.tabs-eyebrow {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: var(--gray-650);
}

.tabs-hint {
  margin: 0;
  color: #7b8797;
  font-size: 13px;
}

.tab-label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.placeholder-card {
  margin-top: 20px;
}

.results-section {
  margin-top: 18px;
}

.results-header {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: flex-end;
  margin-bottom: 12px;
}

.results-eyebrow {
  margin: 0 0 6px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.08em;
  color: #335d92;
}

.results-title {
  margin: 0;
  font-size: 20px;
  color: #1f2937;
}

.results-meta {
  margin: 0;
  max-width: 360px;
  color: var(--gray-650);
  line-height: 1.6;
  text-align: right;
}

@media (max-width: 768px) {
  .template-container {
    padding: 0;
  }

  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .category-tabs {
    border-radius: 8px;
    padding: 12px 12px 0;
    overflow: hidden;

    :deep(.el-tabs__nav-wrap) {
      overflow-x: auto;
      overflow-y: hidden;
      padding-bottom: 4px;
    }

    :deep(.el-tabs__nav-wrap::after) {
      display: none;
    }

    :deep(.el-tabs__nav-scroll) {
      overflow: visible;
    }

    :deep(.el-tabs__nav) {
      white-space: nowrap;
    }

    :deep(.el-tabs__item) {
      min-height: 40px;
      padding: 0 14px;
    }
  }

  .results-header {
    flex-direction: column;
    align-items: flex-start;
  }

  .tabs-head {
    flex-direction: column;
    align-items: flex-start;
  }

  .results-meta {
    max-width: none;
    text-align: left;
  }
}
</style>
