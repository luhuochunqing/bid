import { computed, ref } from 'vue'

export function useProjectDetailWorkflow(context) {
  const { userStore, route, projectStore, isApiProject } = context

  const activeProcessTab = ref('draft')
  const bidProcess = ref({
    initiated: false,
    initiator: '',
    initiateTime: '',
    currentStep: 0,
    steps: {
      draft: { completed: false, time: '' },
      review: { completed: false, time: '' },
      seal: { completed: false, time: '' },
      submit: { completed: false, time: '' },
    },
    deliverables: [],
  })

  const draftFileList = ref([])
  const draftForm = ref({
    preparer: userStore.userName,
    templateId: '',
    files: [],
    remark: '',
  })

  const reviewers = ref([
    { id: 'U002', name: '张经理', role: 'business', status: 'pending', comment: '', reviewTime: '' },
    { id: 'U004', name: '李工', role: 'tech', status: 'approved', comment: '技术方案符合要求', reviewTime: '2025-02-26 10:30' },
  ])
  const reviewerForm = ref({ userId: '', role: '' })
  const sealFileList = ref([])
  const sealForm = ref({ sealTypes: [], reason: '', files: [], count: 1, expectedTime: '' })
  const submitForm = ref({
    checkList: [],
    packageType: 'paper',
    sealRequirement: '',
    deliveryMethod: 'online',
    deliveryTime: '',
    deliveryAddress: '',
    remark: '',
  })
  const templates = ref([])

  const availableReviewers = computed(() => {
    const existingIds = reviewers.value.map((r) => r.id)
    return (userStore.users || []).filter((u) => !existingIds.includes(u.id))
  })

  const getStepOrder = (step) => ({ draft: 0, review: 1, seal: 2, submit: 3 }[step] ?? 0)
  const getStepStatusText = (step) => {
    const stepData = bidProcess.value.steps[step]
    if (stepData.completed) return '已完成'
    if (bidProcess.value.currentStep > getStepOrder(step)) return '进行中'
    return '待开始'
  }
  const canOperateStep = (step) => !bidProcess.value.steps[step].completed && getStepOrder(step) <= bidProcess.value.currentStep
  const getCurrentPhaseType = () => ['info', 'primary', 'warning', 'success'][bidProcess.value.currentStep] || 'info'
  const getCurrentPhaseText = () => ['初稿编制', '内部评审', '用印申请', '封装提交', '已完成'][bidProcess.value.currentStep] || '初稿编制'
  const getProcessProgress = () => Math.round((Object.values(bidProcess.value.steps).filter((s) => s.completed).length / 4) * 100)
  const getReviewerRoleType = (role) => ({ tech: 'primary', business: 'success', legal: 'warning', finance: 'danger' }[role] || 'info')
  const getReviewerRoleText = (role) => ({ tech: '技术评审', business: '商务评审', legal: '法务评审', finance: '财务评审' }[role] || role)
  const getReviewStatusType = (status) => ({ pending: 'info', approved: 'success', rejected: 'danger' }[status] || 'info')
  const getReviewStatusText = (status) => ({ pending: '待评审', approved: '已通过', rejected: '未通过' }[status] || status)
  const getReviewProgress = () => reviewers.value.length ? Math.round((reviewers.value.filter((r) => r.status !== 'pending').length / reviewers.value.length) * 100) : 0
  const getReviewedCount = () => reviewers.value.filter((r) => r.status !== 'pending').length
  const canCompleteReview = () => reviewers.value.length > 0 && reviewers.value.every((r) => r.status === 'approved') && !bidProcess.value.steps.review.completed

  const handleInitiateProcess = () => {
    bidProcess.value = { initiated: true, initiator: userStore.userName, initiateTime: new Date().toLocaleString('zh-CN'), currentStep: 0, steps: { draft: { completed: false, time: '' }, review: { completed: false, time: '' }, seal: { completed: false, time: '' }, submit: { completed: false, time: '' } }, deliverables: [] }
    context.message.success('标书编制流程已发起')
    context.processDialogVisible.value = true
    activeProcessTab.value = 'draft'
  }
  const handleDraftSubmit = () => { activeProcessTab.value = 'draft'; context.processDialogVisible.value = true }
  const handleDraftFileSuccess = (response, file) => { draftForm.value.files.push({ name: file.name, url: response.data?.url || file.url }) }
  const ensureDemoUpload = () => true
  const handleSaveDraft = () => {
    if (!draftForm.value.templateId) return context.message.warning('请选择使用模板')
    bidProcess.value.steps.draft = { completed: true, time: new Date().toLocaleString('zh-CN') }
    bidProcess.value.currentStep = 1
    bidProcess.value.deliverables.push({ name: '标书初稿', type: '初稿', uploader: userStore.userName, time: new Date().toLocaleString('zh-CN') })
    context.message.success('初稿已保存')
    context.processDialogVisible.value = false
  }
  const handleReview = () => { if (!bidProcess.value.steps.draft.completed) return context.message.warning('请先完成初稿编制'); activeProcessTab.value = 'review'; context.processDialogVisible.value = true }
  const handleAddReviewer = () => { reviewerForm.value = { userId: '', role: '' }; context.reviewerDialogVisible.value = true }
  const handleConfirmAddReviewer = () => {
    if (!reviewerForm.value.userId || !reviewerForm.value.role) return context.message.warning('请填写完整信息')
    const user = (userStore.users || []).find((u) => u.id === reviewerForm.value.userId)
    if (!user) return context.message.warning('未找到评审人信息')
    reviewers.value.push({ id: user.id, name: user.name, role: reviewerForm.value.role, status: 'pending', comment: '', reviewTime: '' })
    context.reviewerDialogVisible.value = false
    context.message.success('评审人已添加')
  }
  const handleRemindReviewer = (reviewer) => context.message.success(`已提醒 ${reviewer.name} 进行评审`)
  const handleRemoveReviewer = (index) => reviewers.value.splice(index, 1)
  const handleCompleteReview = () => {
    bidProcess.value.steps.review = { completed: true, time: new Date().toLocaleString('zh-CN') }
    bidProcess.value.currentStep = 2
    bidProcess.value.deliverables.push({ name: '评审报告', type: '评审', uploader: userStore.userName, time: new Date().toLocaleString('zh-CN') })
    context.message.success('评审已完成')
    context.processDialogVisible.value = false
  }
  const handleSealApply = () => { if (!bidProcess.value.steps.review.completed) return context.message.warning('请先完成内部评审'); activeProcessTab.value = 'seal'; context.processDialogVisible.value = true }
  const handleSealFileSuccess = (response, file) => { sealForm.value.files.push({ name: file.name, url: response.data?.url || file.url }) }
  const handleSubmitSeal = () => {
    if (sealForm.value.sealTypes.length === 0) return context.message.warning('请选择用印类型')
    bidProcess.value.steps.seal = { completed: true, time: new Date().toLocaleString('zh-CN') }
    bidProcess.value.currentStep = 3
    bidProcess.value.deliverables.push({ name: '用印文件', type: '用印', uploader: userStore.userName, time: new Date().toLocaleString('zh-CN') })
    context.message.success('用印申请已提交')
    context.processDialogVisible.value = false
  }
  const handleSubmit = () => { if (!bidProcess.value.steps.seal.completed) return context.message.warning('请先完成用印申请'); activeProcessTab.value = 'submit'; context.processDialogVisible.value = true }
  const handleSubmitPackage = () => {
    if (submitForm.value.checkList.length < 6) return context.message.warning('请完成所有封装检查项')
    bidProcess.value.steps.submit = { completed: true, time: new Date().toLocaleString('zh-CN') }
    bidProcess.value.currentStep = 4
    bidProcess.value.deliverables.push({ name: '封装标书', type: '封装', uploader: userStore.userName, time: new Date().toLocaleString('zh-CN') })
    context.message.success('标书已封装提交')
    context.processDialogVisible.value = false
    projectStore.updateProject(route.params.id, { status: 'bidding' })
  }
  const handleDownloadDeliverable = (item) => {
    if (isApiProject.value && item.url && item.url !== '#') {
      const link = document.createElement('a')
      link.href = item.url
      link.download = item.name || ''
      link.target = '_blank'
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)
      return context.message.success(`已下载 ${item.name}`)
    }
    const blob = new Blob([`演示交付物：${item.name}\n类型：${item.type || ''}\n上传者：${item.uploader || ''}`], { type: 'text/plain;charset=utf-8' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = item.name
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(link.href)
    context.message.success(`已下载 ${item.name}`)
  }

  return {
    activeProcessTab, bidProcess, draftFileList, draftForm, reviewers, reviewerForm, sealFileList, sealForm, submitForm, templates,
    availableReviewers, getStepStatusText, canOperateStep, getCurrentPhaseType, getCurrentPhaseText, getProcessProgress,
    getReviewerRoleType, getReviewerRoleText, getReviewStatusType, getReviewStatusText, getReviewProgress, getReviewedCount, canCompleteReview,
    handleInitiateProcess, handleDraftSubmit, handleDraftFileSuccess, ensureDemoUpload, handleSaveDraft, handleReview, handleAddReviewer,
    handleConfirmAddReviewer, handleRemindReviewer, handleRemoveReviewer, handleCompleteReview, handleSealApply, handleSealFileSuccess,
    handleSubmitSeal, handleSubmit, handleSubmitPackage, handleDownloadDeliverable,
  }
}
