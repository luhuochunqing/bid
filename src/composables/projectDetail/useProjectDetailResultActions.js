export function useProjectDetailResultActions(context) {
  const { route, projectStore, message, state, approvalType, loadApprovalHistory, navigation } = context

  const handleSubmitApproval = () => {
    state.approvalMode.value = 'submit'
    state.currentApprovalItem.value = {
      projectId: state.project.value?.id,
      projectName: state.project.value?.name,
      title: `${state.project.value?.name || '当前项目'} - ${approvalType.value.typeName}`,
      description: `发起 ${state.project.value?.name || '当前项目'} 的审批流程。`,
    }
    state.approvalDialogVisible.value = true
  }

  const handleApprovalSuccess = async () => {
    if (state.approvalMode.value === 'submit') {
      await projectStore.updateProject(route.params.id, { status: 'reviewing' })
    }
    await loadApprovalHistory(route.params.id)
  }

  const handleQuickApprove = () => {
    state.approvalMode.value = 'approve'
    state.currentApprovalItem.value = state.currentApproval.value || {}
    state.approvalDialogVisible.value = true
  }

  const handleQuickReject = () => {
    state.approvalMode.value = 'reject'
    state.currentApprovalItem.value = state.currentApproval.value || {}
    state.approvalDialogVisible.value = true
  }

  const handleRecordResult = () => {
    state.resultForm.value = { result: '', amount: null, contractPeriod: null, skuCount: '', noticeFile: '', competitors: [], techHighlights: '', priceStrategy: '', customerFeedback: '', improvements: '' }
    state.noticeFileList.value = []
    state.resultDialogVisible.value = true
  }

  const handleUploadSuccess = (response) => {
    if (response.code === 200) {
      state.resultForm.value.noticeFile = response.data.url
      message.success('上传成功')
      return
    }
    message.error(response.msg || '上传失败')
  }

  const handleUploadRemove = () => { state.resultForm.value.noticeFile = '' }
  const addCompetitor = () => { state.competitorForm.value = { name: '', skuCount: '', category: '', discount: '', payment: '' }; state.competitorDialogVisible.value = true }
  const confirmAddCompetitor = () => {
    if (!state.competitorForm.value.name) return message.warning('请输入竞争对手公司名称')
    state.resultForm.value.competitors.push({ ...state.competitorForm.value })
    state.competitorDialogVisible.value = false
    message.success('添加成功')
  }
  const removeCompetitor = (index) => { state.resultForm.value.competitors.splice(index, 1) }

  const handleSaveResult = async () => {
    if (!state.resultForm.value.result) return message.warning('请选择投标结果')
    if (state.resultForm.value.result === 'won' && !state.resultForm.value.amount) return message.warning('请填写中标金额')

    try {
      await projectStore.updateProject(route.params.id, { status: state.resultForm.value.result, resultAmount: state.resultForm.value.amount, resultData: { ...state.resultForm.value } })
      message.success('结果录入成功')
      state.resultDialogVisible.value = false
    } catch {
      message.error('结果录入失败')
    }
  }

  return {
    handleSubmitApproval,
    handleApprovalSuccess,
    handleQuickApprove,
    handleQuickReject,
    handleRecordResult,
    handleUploadSuccess,
    handleUploadRemove,
    addCompetitor,
    confirmAddCompetitor,
    removeCompetitor,
    handleSaveResult,
    goToResultPage: navigation.goToResultPage,
  }
}
