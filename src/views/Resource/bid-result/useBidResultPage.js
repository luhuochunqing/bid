// Input: route context plus bid result page state containers
// Output: composed state and actions for BidResult page shell
// Pos: src/views/Resource/bid-result/ - Bid result page composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { computed, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'

import { createBidResultPageActions } from './bidResultPage.actions.js'
import { createResultForm, createUploadForm, formatAmount, formatDateTime } from './bidResultPage.helpers.js'

export function useBidResultPage() {
  const route = useRoute()
  const initialProjectId = route.query.projectId ? Number(route.query.projectId) : null

  const pageLoading = ref(false)
  const saving = ref(false)
  const syncing = ref(false)
  const fetching = ref(false)
  const sending = ref(false)
  const reportLoading = ref(false)
  const ignoreSubmitting = ref(false)
  const uploadSaving = ref(false)
  const confirmSaving = ref(false)

  const overview = ref({
    lastSyncTime: '',
    pendingCount: 0,
    uploadPending: 0,
    competitorCount: 0
  })
  const projects = ref([])
  const fetchResults = ref([])
  const reminderRecords = ref([])
  const competitorReport = ref([])
  const selectedFetchIds = ref([])

  const registerForm = reactive(createResultForm(initialProjectId))
  const confirmForm = reactive(createResultForm(initialProjectId))
  const uploadForm = reactive(createUploadForm())

  const confirmDialogVisible = ref(false)
  const ignoreDialogVisible = ref(false)
  const reportVisible = ref(false)
  const uploadDialogVisible = ref(false)
  const currentFetchRecord = ref(null)
  const currentReminderRecord = ref(null)
  const ignoreReason = ref('')

  const actions = createBidResultPageActions({
    initialProjectId,
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
    ignoreReason
  })

  const availableProjects = computed(() => projects.value.filter((project) => project?.id && project?.name))

  return {
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
    projects: availableProjects,
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
    addRegisterCompetitor: () => actions.addCompetitor(registerForm),
    removeRegisterCompetitor: (index) => actions.removeCompetitor(registerForm, index),
    addConfirmCompetitor: () => actions.addCompetitor(confirmForm),
    removeConfirmCompetitor: (index) => actions.removeCompetitor(confirmForm, index),
    prefillRegister: (row) => actions.assignForm(registerForm, row),
    ...actions,
    showReport: () => actions.showReport(reportVisible)
  }
}
