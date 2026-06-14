// Input: qualification store, user store, export/download helpers
// Output: qualification page state and handlers for the qualification view
// Pos: src/views/Knowledge/components/qualification/ - View composition logic

import { computed, onMounted, reactive, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { ElMessage, ElMessageBox } from 'element-plus'
import { isFeatureUnavailableResponse } from '@/api'
import { triggerDownload } from '@/api/modules/export'
import { getAccessToken } from '@/api/session.js'
import { useQualificationStore } from '@/stores/qualification'
import { useUserStore } from '@/stores/user'
import { useQualificationBorrowWorkflow } from './useQualificationBorrowWorkflow'
import { qualificationsApi } from '@/api/modules/qualification.js'

export function useQualificationPage() {
  const qualificationStore = useQualificationStore()
  const userStore = useUserStore()
  const {
    borrowFeaturePlaceholder,
    borrowLoading,
    borrowRecords,
    listFeaturePlaceholder,
    listLoading,
    qualifications
  } = storeToRefs(qualificationStore)

  const isAdmin = computed(() => userStore.hasPermission('knowledge:qualification:manage') || userStore.hasPermission('certificate.manage') || userStore.hasPermission('all'))

  const searchForm = reactive({
    name: '',
    type: '',
    status: '',
    level: ''
  })

  const pagination = reactive({
    page: 1,
    pageSize: 10
  })

  const uploadDialogVisible = ref(false)
  const detailDialogVisible = ref(false)
  const borrowDialogVisible = ref(false)
  const currentQualification = ref(null)

  const uploadForm = reactive({
    name: '',
    type: '',
    subjectType: 'company',
    subjectName: '',
    certificateNo: '',
    issuer: '',
    holderName: '',
    issueDate: '',
    expiryDate: '',
    file: null
  })

  const {
    borrowForm,
    borrowFormSchema,
    handleConfirmBorrow,
    openBorrowDialog
  } = useQualificationBorrowWorkflow({ currentQualification, borrowDialogVisible })

  const levelOptions = computed(() => {
    const levels = new Set()
    qualifications.value.forEach((item) => {
      if (item.level) levels.add(item.level)
    })
    return Array.from(levels).sort()
  })

  const selectedRows = ref([])
  const hasSelection = computed(() => selectedRows.value.length > 0)

  const filteredQualifications = computed(() => {
    const result = qualifications.value
      .filter((item) => !searchForm.name || item.name.toLowerCase().includes(searchForm.name.toLowerCase()))
      .filter((item) => !searchForm.type || item.type === searchForm.type)
      .filter((item) => !searchForm.status || item.status === searchForm.status)
      .filter((item) => !searchForm.level || item.level === searchForm.level)

    return [...result].sort((a, b) => a.remainingDays - b.remainingDays)
  })

  const pagedQualifications = computed(() => {
    const start = (pagination.page - 1) * pagination.pageSize
    return filteredQualifications.value.slice(start, start + pagination.pageSize)
  })

  function resetUploadForm() {
    Object.assign(uploadForm, {
      name: '',
      type: '',
      subjectType: 'company',
      subjectName: '',
      certificateNo: '',
      issuer: '',
      holderName: '',
      issueDate: '',
      expiryDate: '',
      file: null
    })
  }

  async function loadPageData() {
    try {
      await qualificationStore.loadQualifications()
      await qualificationStore.loadBorrowRecords()
    } catch (error) {
      console.error('Failed to load qualification page data:', error)
      ElMessage.error('资质页面数据加载失败，请稍后重试')
    }
  }

  function handleSearch() {
    pagination.page = 1
  }

  function handleReset() {
    searchForm.name = ''
    searchForm.type = ''
    searchForm.status = ''
    searchForm.level = ''
    pagination.page = 1
  }

  function handleSelectionChange(rows) {
    selectedRows.value = rows || []
  }

  async function handleBatchExport() {
    if (!selectedRows.value.length) {
      ElMessage.warning('请先选择要导出的资质证书')
      return
    }
    try {
      const ids = selectedRows.value.map((r) => r.id)
      const response = await qualificationsApi.exportList({ ids })
      const blob = new Blob([response.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' })
      triggerDownload(blob, '资质台账导出.xlsx')
      ElMessage.success('导出台账成功')
    } catch (error) {
      console.error('Batch export error:', error)
      ElMessage.error('导出台账失败')
    }
  }

  async function handleBatchDownload() {
    if (!selectedRows.value.length) {
      ElMessage.warning('请先选择要下载的资质证书')
      return
    }
    const ids = selectedRows.value.filter((r) => r.fileUrl).map((r) => r.id)
    if (!ids.length) {
      ElMessage.warning('所选资质证书均无可下载附件')
      return
    }
    try {
      const response = await qualificationsApi.batchDownload(ids)
      const blob = new Blob([response.data], { type: 'application/zip' })
      triggerDownload(blob, '资质附件批量下载.zip')
      ElMessage.success('批量下载成功')
    } catch (error) {
      console.error('Batch download error:', error)
      ElMessage.error('批量下载失败')
    }
  }

  async function handleImportLedger(file) {
    try {
      const response = await qualificationsApi.importLedger(file.raw || file)
      if (response?.code === 200 || response?.success) {
        ElMessage.success('导入台账成功')
        await loadPageData()
        return
      }
      ElMessage.error(response?.msg || '导入台账失败')
    } catch (error) {
      console.error('Import ledger error:', error)
      ElMessage.error('导入台账失败')
    }
  }

  async function handleExportList() {
    const [{ useExport }, { ExportType }] = await Promise.all([
      import('@/composables/useExport'),
      import('@/api')
    ])

    useExport().exportExcel(ExportType.QUALIFICATIONS, {
      name: searchForm.name || undefined,
      type: searchForm.type || undefined,
      status: searchForm.status || undefined
    }, '资质列表导出成功')
  }

  function handleUpload() {
    resetUploadForm()
    uploadDialogVisible.value = true
  }

  function handleFileChange(file) {
    uploadForm.file = file
  }

  async function handleConfirmUpload() {
    if (!uploadForm.name || !uploadForm.type || !uploadForm.expiryDate) {
      ElMessage.warning('请填写必填项')
      return
    }

    const result = await qualificationStore.createQualification({
      name: uploadForm.name,
      type: uploadForm.type,
      subjectType: uploadForm.subjectType,
      subjectName: uploadForm.subjectName,
      certificateNo: uploadForm.certificateNo,
      issueDate: uploadForm.issueDate,
      expiryDate: uploadForm.expiryDate,
      issuer: uploadForm.issuer,
      holderName: uploadForm.holderName,
      fileUrl: uploadForm.file?.name || ''
    })

    if (!result?.success) {
      ElMessage.error(result?.msg || '上传失败')
      return
    }

    uploadDialogVisible.value = false
    ElMessage.success('资质元数据已创建')
  }

  function handleView(row) {
    currentQualification.value = row
    detailDialogVisible.value = true
  }

  async function handleReturn(row) {
    ElMessageBox.confirm(
      `确认「${row.qualificationName}」已归还吗？`,
      '归还确认',
      {
        confirmButtonText: '确认归还',
        cancelButtonText: '取消',
        type: 'success'
      }
    ).then(async () => {
      const result = await qualificationStore.returnBorrow(row.id)
      if (result?.success) {
        ElMessage.success('归还成功')
        return
      }
      if (isFeatureUnavailableResponse(result)) {
        ElMessage.warning(result.msg || '资质归还接口暂未接入')
        return
      }
      ElMessage.error(result?.msg || '归还失败')
    }).catch(() => {})
  }

  function handleDownload(row) {
    if (!row?.fileUrl) {
      ElMessage.warning('当前资质暂无可下载附件')
      return
    }

    const token = getAccessToken()
    const filename = `${row.name}.${row.fileUrl.split('.').pop() || 'pdf'}`

    fetch(row.fileUrl, {
      credentials: 'include',
      headers: token ? { Authorization: `Bearer ${token}` } : {}
    })
      .then((response) => {
        if (!response.ok) throw new Error('下载失败')
        return response.blob()
      })
      .then((blob) => {
        triggerDownload(blob, filename)
        ElMessage.success(`下载成功：${row.name}`)
      })
      .catch((error) => {
        console.error('Download error:', error)
        ElMessage.error(`下载失败：${error.message}`)
      })
  }

  function handleDelete(row) {
    ElMessageBox.confirm(
      `确定要删除「${row.name}」吗？`,
      '删除确认',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    ).then(async () => {
      const result = await qualificationStore.deleteQualification(row.id)
      if (result?.success === false) {
        ElMessage.error(result?.msg || '删除失败')
        return
      }
      ElMessage.success('删除成功')
    }).catch(() => {})
  }

  function handlePageChange(page) {
    pagination.page = page
  }

  function handleSizeChange(size) {
    pagination.pageSize = size
    pagination.page = 1
  }

  onMounted(loadPageData)

  return {
    borrowDialogVisible,
    borrowFeaturePlaceholder,
    borrowForm,
    borrowFormSchema,
    borrowLoading,
    borrowRecords,
    currentQualification,
    detailDialogVisible,
    filteredQualifications,
    handleConfirmBorrow,
    handleConfirmUpload,
    handleDelete,
    handleDownload,
    handleExportList,
    handleFileChange,
    handlePageChange,
    handleReset,
    handleReturn,
    handleSearch,
    handleSizeChange,
    handleUpload,
    handleView,
    isAdmin,
    listFeaturePlaceholder,
    listLoading,
    openBorrowDialog,
    pagedQualifications,
    pagination,
    searchForm,
    uploadDialogVisible,
    uploadForm,
    selectedRows,
    hasSelection,
    levelOptions,
    handleSelectionChange,
    handleBatchExport,
    handleBatchDownload,
    handleImportLedger
  }
}

export default useQualificationPage
