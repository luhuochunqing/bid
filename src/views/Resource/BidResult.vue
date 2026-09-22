<template>
  <div class="bid-result-page" v-loading="pageLoading">
    <div class="page-header">
      <div>
        <h2 class="page-title">投标结果闭环</h2>
        <p class="page-subtitle">人工登记、外部结果确认、资料上传和竞争对手分析统一收口到这里。</p>
      </div>
    </div>

    <BidResultOverviewCards
      :overview="overview"
      :syncing="syncing"
      :fetching="fetching"
      :sending="sending"
      :report-loading="reportLoading"
      @sync="syncInternal"
      @fetch="fetchPublic"
      @send-all-reminders="sendAllReminders"
      @show-report="showReport"
    />

    <div class="content-grid">
      <div class="left-column">
        <BidResultRegisterPanel
          :form="registerForm"
          :projects="projects"
          :saving="saving"
          @add-competitor="addRegisterCompetitor"
          @remove-competitor="removeRegisterCompetitor"
          @reset="resetRegisterForm"
          @submit="submitRegister"
        />
      </div>

      <div class="right-column">
        <BidResultFetchTable
          :rows="fetchResults"
          :selected-ids="selectedFetchIds"
          :format-amount="formatAmount"
          :format-date-time="formatDateTime"
          @selection-change="handleFetchSelectionChange"
          @confirm="openConfirmDialog"
          @confirm-batch="submitConfirmBatch"
          @ignore="openIgnoreDialog"
          @prefill="prefillRegister"
        />

        <BidResultReminderTable
          class="reminder-table"
          :rows="reminderRecords"
          :format-date-time="formatDateTime"
          @upload="openUploadDialog"
          @remind-again="sendReminderAgain"
        />
      </div>
    </div>

    <BidResultConfirmDialog
      :visible="confirmDialogVisible"
      :saving="confirmSaving"
      :fetch-record="currentFetchRecord"
      v-model:form="confirmForm"
      :projects="projects"
      @add-competitor="addConfirmCompetitor"
      @remove-competitor="removeConfirmCompetitor"
      @close="confirmDialogVisible = false"
      @submit="submitConfirm"
    />

    <BidResultIgnoreDialog
      :visible="ignoreDialogVisible"
      :reason="ignoreReason"
      :submitting="ignoreSubmitting"
      @update:reason="ignoreReason = $event"
      @close="ignoreDialogVisible = false"
      @submit="submitIgnore"
    />

    <BidResultUploadDialog
      :visible="uploadDialogVisible"
      :saving="uploadSaving"
      :target="currentReminderRecord"
      v-model:form="uploadForm"
      @close="uploadDialogVisible = false"
      @submit="submitUpload"
    />

    <BidResultCompetitorReportDialog
      :visible="reportVisible"
      :rows="competitorReport"
      @close="reportVisible = false"
    />
  </div>
</template>

<script setup>
import { onMounted } from 'vue'

import BidResultCompetitorReportDialog from './bid-result/BidResultCompetitorReportDialog.vue'
import BidResultConfirmDialog from './bid-result/BidResultConfirmDialog.vue'
import BidResultFetchTable from './bid-result/BidResultFetchTable.vue'
import BidResultIgnoreDialog from './bid-result/BidResultIgnoreDialog.vue'
import BidResultOverviewCards from './bid-result/BidResultOverviewCards.vue'
import BidResultRegisterPanel from './bid-result/BidResultRegisterPanel.vue'
import BidResultReminderTable from './bid-result/BidResultReminderTable.vue'
import BidResultUploadDialog from './bid-result/BidResultUploadDialog.vue'
import { useBidResultPage } from './bid-result/useBidResultPage.js'

const state = useBidResultPage()

const {
  pageLoading,
  saving,
  syncing,
  fetching,
  sending,
  reportLoading,
  ignoreSubmitting,
  uploadSaving,
  confirmSaving,
  overview,
  projects,
  fetchResults,
  reminderRecords,
  competitorReport,
  selectedFetchIds,
  registerForm,
  confirmForm,
  uploadForm,
  confirmDialogVisible,
  ignoreDialogVisible,
  reportVisible,
  uploadDialogVisible,
  currentFetchRecord,
  currentReminderRecord,
  ignoreReason,
  formatDateTime,
  formatAmount,
  handleFetchSelectionChange,
  addRegisterCompetitor,
  removeRegisterCompetitor,
  addConfirmCompetitor,
  removeConfirmCompetitor,
  loadPage,
  resetRegisterForm,
  submitRegister,
  syncInternal,
  fetchPublic,
  openConfirmDialog,
  submitConfirm,
  openIgnoreDialog,
  submitIgnore,
  submitConfirmBatch,
  sendReminderAgain,
  sendAllReminders,
  openUploadDialog,
  submitUpload,
  showReport,
  prefillRegister
} = state

onMounted(loadPage)
</script>

<style scoped>
.bid-result-page {
  padding: 20px;
}

.page-header {
  margin-bottom: 20px;
}

.page-title {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 600;
}

.page-subtitle {
  margin: 0;
  color: var(--text-muted);
  font-size: 14px;
}

.content-grid {
  display: grid;
  grid-template-columns: minmax(360px, 420px) minmax(0, 1fr);
  gap: 16px;
}

.right-column,
.left-column {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

@media (max-width: 1100px) {
  .content-grid {
    grid-template-columns: 1fr;
  }
}
</style>
