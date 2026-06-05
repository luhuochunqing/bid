export function useProjectDetailProjectOps(context) {
  const { router, route, project, projectStore } = context

  const handleEdit = () => router.push(`/document/editor/${route.params.id}`)

  const handleSubmitApproval = () => {
    context.approvalMode.value = 'submit'
    context.currentApprovalItem.value = {
      projectId: project.value?.id,
      projectName: project.value?.name,
      title: `${project.value?.name || '当前项目'} - ${context.approvalType.value.typeName}`,
      description: `发起 ${project.value?.name || '当前项目'} 的审批流程。`,
    }
    context.approvalDialogVisible.value = true
  }

  const handleApprovalSuccess = async () => {
    if (context.approvalMode.value === 'submit') {
      await projectStore.updateProject(route.params.id, { status: 'reviewing' })
    }
    await context.loadApprovalHistory(route.params.id)
  }

  const handleQuickApprove = () => {
    context.approvalMode.value = 'approve'
    context.currentApprovalItem.value = context.currentApproval.value || {}
    context.approvalDialogVisible.value = true
  }
  const handleQuickReject = () => {
    context.approvalMode.value = 'reject'
    context.currentApprovalItem.value = context.currentApproval.value || {}
    context.approvalDialogVisible.value = true
  }

  const handleRecordResult = () => {
    context.resultForm.value = { result: '', amount: null, contractPeriod: null, skuCount: '', noticeFile: '', competitors: [], techHighlights: '', priceStrategy: '', customerFeedback: '', improvements: '' }
    context.noticeFileList.value = []
    context.resultDialogVisible.value = true
  }
  const handleUploadSuccess = (response) => {
    if (response.code === 200) {
      context.resultForm.value.noticeFile = response.data.url
      context.message.success('上传成功')
    } else {
      context.message.error(response.msg || '上传失败')
    }
  }
  const handleUploadRemove = () => { context.resultForm.value.noticeFile = '' }
  const addCompetitor = () => {
    context.competitorForm.value = { name: '', skuCount: '', category: '', discount: '', payment: '' }
    context.competitorDialogVisible.value = true
  }
  const confirmAddCompetitor = () => {
    if (!context.competitorForm.value.name) return context.message.warning('请输入竞争对手公司名称')
    context.resultForm.value.competitors.push({ ...context.competitorForm.value })
    context.competitorDialogVisible.value = false
    context.message.success('添加成功')
  }
  const removeCompetitor = (index) => context.resultForm.value.competitors.splice(index, 1)
  const handleSaveResult = async () => {
    if (!context.resultForm.value.result) return context.message.warning('请选择投标结果')
    if (context.resultForm.value.result === 'won' && !context.resultForm.value.amount) return context.message.warning('请填写中标金额')
    try {
      await projectStore.updateProject(route.params.id, {
        status: context.resultForm.value.result,
        resultAmount: context.resultForm.value.amount,
        resultData: { contractPeriod: context.resultForm.value.contractPeriod, skuCount: context.resultForm.value.skuCount, noticeFile: context.resultForm.value.noticeFile, competitors: context.resultForm.value.competitors, techHighlights: context.resultForm.value.techHighlights, priceStrategy: context.resultForm.value.priceStrategy, customerFeedback: context.resultForm.value.customerFeedback, improvements: context.resultForm.value.improvements },
      })
      context.message.success('结果录入成功')
      context.resultDialogVisible.value = false
    } catch {
      context.message.error('结果录入失败')
    }
  }

  return {
    handleEdit,
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
  }
}
