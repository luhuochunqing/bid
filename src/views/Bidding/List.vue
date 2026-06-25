<template>
  <div class="bidding-list-page">
    <BiddingPageHeader
      :customer-opportunity-enabled="customerOpportunityCenterEnabled"
      :can-create-tender="canCreateTender"
      :can-bulk-import="canBulkImport"
      :can-sync-external-source="canSyncExternalSource"
      :fetching-tenders="sourceConfig.fetchingTenders.value"
      @open-customer-opportunities="handleOpenCustomerOpportunityCenter"
      @open-source-config="openSourceConfig"
      @sync-external="sourceConfig.syncExternalTenders"
      @open-bulk-import="bulkImport.openBulkImport"
    />
    <TenderSearchCard v-model="searchForm" @search="handleSearch" @reset="handleReset" />
    <SourceStatusCard :source-config="sourceConfig.sourceConfig.value" :last-sync-time="sourceConfig.lastSyncTime.value" />
    <AiRecommendSection :tenders="filteredRecommendTenders" @view-all="handleViewAllRecommend" @view-detail="handleViewDetail" />
    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header-content">
          <div class="card-header-row">
            <span class="card-title">标讯列表</span>
            <div class="card-actions">
              <el-button size="small" type="success" @click="marketInsight.showMarketInsight.value = true"><el-icon><TrendCharts /></el-icon>市场洞察</el-button>
              <el-button size="small" @click="handleExport"><el-icon><Download /></el-icon>导出</el-button>
              <el-button v-if="canSyncExternalSource" size="small" type="warning" :loading="sourceConfig.fetchingTenders.value" @click="sourceConfig.syncExternalTenders"><el-icon><Refresh /></el-icon>一键获取标讯</el-button>
            </div>
          </div>
          <el-radio-group v-model="viewMode" size="small" class="status-filter">
            <el-radio-button value="all">全部 ({{ statusCounts.all }})</el-radio-button>
            <el-radio-button value="PENDING_ASSIGNMENT">待分配 ({{ statusCounts.pendingAssignment }})</el-radio-button>
            <el-radio-button value="TRACKING">跟踪中 ({{ statusCounts.tracking }})</el-radio-button>
            <el-radio-button value="EVALUATED">已评估 ({{ statusCounts.evaluated }})</el-radio-button>
            <el-radio-button value="BIDDING">投标中 ({{ statusCounts.bidding }})</el-radio-button>
            <el-radio-button value="WON">已中标 ({{ statusCounts.won }})</el-radio-button>
            <el-radio-button value="LOST">未中标 ({{ statusCounts.lost }})</el-radio-button>
            <el-radio-button value="ABANDONED">已放弃 ({{ statusCounts.abandoned }})</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <TenderBatchActionBar
        :selected-count="selection.selectedTenders.value.length"
        :select-all-checked="selection.selectAllChecked.value"
        :is-indeterminate="selection.isIndeterminate.value"
        :can-manage-tenders="canManageTenders"
        @select-all="selection.handleSelectAll"
        @distribute="distribution.openDistributeDialog"
        @claim="batchActions.handleBatchClaim"
        @follow="batchActions.handleBatchFollow"
        @clear="selection.handleClearSelection"
      />
      <TenderTable
        v-if="!isMobile"
        :ref="(instance) => { selection.tableRef.value = instance }"
        :rows="displayTenders"
        :loading="loading"
        :can-manage-tenders="canManageTenders"
        :can-delete-tenders="canDeleteTenders"
        :can-transfer="canShowTransfer"
        :show-ai-entry="showTenderAiEntry"
        :is-admin="isAdmin"
        @selection-change="selection.handleSelectionChange"
        @view-detail="handleViewDetail"
        @ai-analysis="handleAIAnalysis"
        @participate="handleParticipate"
        @distribute="distribution.openSingleDistribute"
        @claim="batchActions.handleSingleClaim"
        @assign="distribution.openAssignDialog"
        @evaluate="handleEvaluate"
        @status-change="batchActions.handleUpdateStatus"
        @delete="batchActions.handleDeleteTender"
        @set-reminder="handleSetReminder"
        @transfer="handleTransfer"
      />
      <TenderMobileCards
        v-else
        :rows="displayTenders"
        :can-manage-tenders="canManageTenders"
        :can-delete-tenders="canDeleteTenders"
        :show-ai-entry="showTenderAiEntry"
        :is-admin="isAdmin"
        :current-user-id="currentUserId"
        @view-detail="handleViewDetail"
        @ai-analysis="handleAIAnalysis"
        @participate="handleParticipate"
        @status-change="batchActions.handleUpdateStatus"
        @delete="batchActions.handleDeleteTender"
      />
      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[15, 20, 30, 50, 100]"
          :total="filteredTenders.length"
          layout="total, sizes, prev, pager, next, jumper"
        />
      </div>
    </el-card>
    <DistributeDialog
      v-model="distribution.showDistributeDialog.value"
      :selected-tenders="selection.selectedTenders.value"
      :candidates="distribution.candidates.value"
      :preview="distribution.distributionPreview.value"
      v-model:form="distribution.distributeForm.value"
      :loading="distribution.distributeLoading.value"
      @reset="distribution.resetDistributeForm"
      @submit="distribution.handleDistribute"
    />
    <AssignDialog
      v-model="distribution.showAssignDialog.value"
      :tender-id="distribution.activeTender.value.id"
      :tender-title="distribution.activeTender.value.title"
      :loading="distribution.assignLoading.value"
      @reset="distribution.resetAssignForm"
      @submit="distribution.handleAssign"
    />
    <SourceConfigDialog
      v-model="sourceConfig.showSourceConfig.value"
      v-model:source-config="sourceConfig.sourceConfig.value"
      :saving="sourceConfig.savingConfig.value"
      :testing="sourceConfig.testingConnection.value"
      @save="sourceConfig.saveSourceConfig"
      @test="sourceConfig.testConnection"
    />
    <BulkImportDialog
      v-model="bulkImport.showBulkImport.value"
      :selected-file="bulkImport.selectedFile.value"
      :result="bulkImport.importResult.value"
      :template-downloading="bulkImport.templateDownloading.value"
      :importing="bulkImport.importing.value"
      @reset="bulkImport.resetImport"
      @download-template="bulkImport.downloadImportTemplate"
      @file-change="bulkImport.handleFileChange"
      @submit="bulkImport.submitBulkImport"
    />
    <ManualTenderDialog
      v-model="manualCreate.showManualAdd.value"
      :ref="(instance) => { manualCreate.manualFormRef.value = instance }"
      v-model:form="manualCreate.manualForm.value"
      :saving="manualCreate.savingManual.value"
      :parsing-document="manualCreate.parsingManualDocument.value"
      @reset="manualCreate.resetManualForm"
      @file-change="manualCreate.handleFileChange"
      @parse-pasted-text="manualCreate.handlePastedTextParse"
      @submit="manualCreate.saveManualTender"
    />
    <FetchResultDialog v-model="sourceConfig.fetchResult.value.visible" :result="sourceConfig.fetchResult.value" />
    <MarketInsightDialog
      v-model="marketInsight.showMarketInsight.value"
      v-model:active-tab="marketInsight.activeInsightTab.value"
      :loading="marketInsight.loadingTrendData.value"
      :industry-trends="marketInsight.industryTrends.value"
      :opportunities="marketInsight.potentialOpportunities.value"
      :industry-insight="marketInsight.industryInsight.value"
      :forecast-tips="marketInsight.forecastTips.value"
      @refresh="marketInsight.refreshTrendData"
    />
    <AiParsingDialog v-model="showParsingDialog" :progress="parseProgress" />
    <ReminderSettingsDialog v-model="reminderDialog.visible" :tender-id="reminderDialog.tenderId" @saved="handleReminderSaved" />
    <el-dialog v-model="transferDialog.visible" title="转派标讯" width="420px" :close-on-click-modal="false">
      <el-form label-width="100px">
        <el-form-item label="标讯"><span>{{ transferDialog.tender?.title }}</span></el-form-item>
        <el-form-item label="目标负责人">
          <UserPicker v-model="transferDialog.newOwnerId" mode="search" placeholder="搜索人员（姓名/工号/拼音）" style="width:100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="transferDialog.visible = false">取消</el-button>
        <el-button type="primary" :loading="transferDialog.loading" @click="handleTransferConfirm">确定转派</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { Download, Refresh, TrendCharts } from '@element-plus/icons-vue'
import AiParsingDialog from './list/components/AiParsingDialog.vue'
import AiRecommendSection from './list/components/AiRecommendSection.vue'
import AssignDialog from './list/components/AssignDialog.vue'
import BiddingPageHeader from './list/components/BiddingPageHeader.vue'
import BulkImportDialog from './list/components/BulkImportDialog.vue'
import DistributeDialog from './list/components/DistributeDialog.vue'
import FetchResultDialog from './list/components/FetchResultDialog.vue'
import ManualTenderDialog from './list/components/ManualTenderDialog.vue'
import MarketInsightDialog from './list/components/MarketInsightDialog.vue'
import ReminderSettingsDialog from './list/components/ReminderSettingsDialog.vue'
import SourceConfigDialog from './list/components/SourceConfigDialog.vue'
import SourceStatusCard from './list/components/SourceStatusCard.vue'
import TenderBatchActionBar from './list/components/TenderBatchActionBar.vue'
import TenderMobileCards from './list/components/TenderMobileCards.vue'
import TenderSearchCard from './list/components/TenderSearchCard.vue'
import TenderTable from './list/components/TenderTable.vue'
import { useTenderListPage } from './list/useTenderListPage.js'
import { tendersApi } from '@/api/modules/tenders'
import { useBiddingStore } from '@/stores/bidding'
import { ElMessage } from 'element-plus'
import { computed, reactive, ref } from 'vue'
import UserPicker from '@/components/common/UserPicker.vue'
import './list/styles/list-page.css'
import './list/styles/table.css'
import './list/styles/mobile-page.css'
import { formatUserLabel } from '@/utils/formatUserLabel.js'

const {
  searchForm, viewMode, isMobile, loading, currentPage, pageSize,
  filteredTenders, filteredRecommendTenders, displayTenders, statusCounts,
  canManageTenders, canCreateTender, canBulkImport, canDeleteTenders, canSyncExternalSource,
  customerOpportunityCenterEnabled, showTenderAiEntry,
  showParsingDialog, parseProgress,
  selection, sourceConfig, manualCreate, bulkImport, marketInsight,
  batchActions, distribution,
  handleSearch, handleReset, handleExport,
  handleViewDetail, handleParticipate, handleViewAllRecommend,
  handleOpenCustomerOpportunityCenter, openManualAdd, openSourceConfig,
  handleAIAnalysis, isAdmin, handleEvaluate,
} = useTenderListPage()

// ---- 收藏状态 ----
// --- useTenderTransfer (内联：唯一引用 + ≤80行) ---
const canShowTransfer = computed(() => canManageTenders.value)
const transferDialog = reactive({
  visible: false,
  tender: null,
  newOwnerId: null,
  loading: false,
})
async function handleTransfer(row) {
  transferDialog.tender = row
  transferDialog.newOwnerId = null
  transferDialog.visible = true
}
async function handleTransferConfirm() {
  if (!transferDialog.newOwnerId) return ElMessage.warning('请选择目标负责人')
  transferDialog.loading = true
  try {
    const result = await tendersApi.transferTender(transferDialog.tender.id, {
      newOwnerId: transferDialog.newOwnerId,
    })
    if (result?.success !== false) {
      ElMessage.success('转派成功')
      transferDialog.visible = false
      const biddingStore = useBiddingStore()
      await biddingStore.getTenders()
    } else {
      ElMessage.error(result?.msg || '转派失败')
    }
  } catch {
    ElMessage.error('转派失败，请重试')
  } finally {
    transferDialog.loading = false
  }
}

const reminderDialog = reactive({ visible: false, tenderId: null })

function handleSetReminder(row) {
  reminderDialog.tenderId = row.id
  reminderDialog.visible = true
}

function handleReminderSaved() {}

defineExpose({ handleSetReminder })
</script>
