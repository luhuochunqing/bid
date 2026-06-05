<template>
  <div class="case-container">
    <div class="page-header">
      <h2 class="page-title">案例库</h2>
      <div class="header-actions">
        <el-button type="primary" :icon="Plus" @click="handleAdd">
          新增案例
        </el-button>
      </div>
    </div>

    <CaseSearchCard
      :amount-ranges="amountRanges"
      :common-tags="commonTags"
      :industries="industries"
      v-model:search-form="searchForm"
      :selected-tags="selectedTags"
      :years="years"
      @reset="handleReset"
      @search="handleSearch"
      @toggle-tag="toggleTag"
    />

    <CaseListGrid
      :cases="cases"
      :feature-placeholder="featurePlaceholder"
      :loading="loading"
      @view="handleView"
    />

    <div class="pagination-wrapper" v-if="pagination.total > 0">
      <el-pagination
        v-model:current-page="pagination.page"
        v-model:page-size="pagination.pageSize"
        :page-sizes="[12, 24, 48]"
        :total="pagination.total"
        layout="total, sizes, prev, pager, next, jumper"
        @size-change="handleSizeChange"
        @current-change="handlePageChange"
      />
    </div>

    <CaseFormDialog
      v-model="addCaseDialogVisible"
      v-model:active-tab="addCaseTab"
      v-model:case-form="caseForm"
      v-model:manual-case-form="manualCaseForm"
      :case-form-rules="caseFormRules"
      :industries="industries"
      :project-options="sourceProjectOptions"
      :project-options-loading="projectOptionsLoading"
      :saving="saving"
      @project-change="fillProjectCaseForm"
      @save="handleSaveCase"
    />
  </div>
</template>

<script setup>
import { Plus } from '@element-plus/icons-vue'
import CaseFormDialog from './components/case/CaseFormDialog.vue'
import CaseListGrid from './components/case/CaseListGrid.vue'
import CaseSearchCard from './components/case/CaseSearchCard.vue'
import { useCasePage } from './components/case/useCasePage.js'

const {
  addCaseDialogVisible,
  addCaseTab,
  amountRanges,
  caseForm,
  caseFormRules,
  cases,
  commonTags,
  featurePlaceholder,
  fillProjectCaseForm,
  handleAdd,
  handlePageChange,
  handleReset,
  handleSaveCase,
  handleSearch,
  handleSizeChange,
  handleView,
  industries,
  loading,
  manualCaseForm,
  pagination,
  projectOptionsLoading,
  saving,
  searchForm,
  selectedTags,
  sourceProjectOptions,
  toggleTag,
  years
} = useCasePage()
</script>

<style scoped lang="scss">
.case-container {
  padding: 20px;

  .page-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    margin-bottom: 20px;

    .page-title {
      font-size: 20px;
      font-weight: 600;
      color: var(--gray-750);
      margin: 0;
    }
  }

  .pagination-wrapper {
    margin-top: 20px;
    display: flex;
    justify-content: center;
  }
}

@media (max-width: 768px) {
  .case-container {
    padding: 12px;

    .page-header {
      margin-bottom: 12px;
      flex-direction: column;
      align-items: flex-start;
      gap: 12px;
    }

    .page-title {
      font-size: 20px;
    }
  }
}
</style>
