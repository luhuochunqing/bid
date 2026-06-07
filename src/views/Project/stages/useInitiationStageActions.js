import { ElMessage, ElMessageBox } from 'element-plus'

const PROJECT_TYPE_SUBMIT_MAP = {
  OFFICE: 'OFFICE',
  COMPREHENSIVE: 'COMPREHENSIVE',
  GROUP_PURCHASE: 'COLLECTIVE',
  COLLECTIVE: 'COLLECTIVE',
  INDUSTRIAL_EC: 'INDUSTRIAL',
  INDUSTRIAL: 'INDUSTRIAL',
  OTHER: 'OTHER',
  办公: 'OFFICE',
  综合: 'COMPREHENSIVE',
  集采: 'COLLECTIVE',
  工业品: 'INDUSTRIAL',
  其他: 'OTHER',
  公开招标: 'COMPREHENSIVE',
}

const CUSTOMER_TYPE_SUBMIT_MAP = {
  GOVERNMENT_INSTITUTION: 'GOVERNMENT',
  GOVERNMENT: 'GOVERNMENT',
  CENTRAL_SOE: 'CENTRAL_SOE',
  LOCAL_SOE: 'LOCAL_SOE',
  PRIVATE_ENTERPRISE: 'PRIVATE',
  PRIVATE: 'PRIVATE',
  FOREIGN_HK_MACAO_TW: 'FOREIGN',
  FOREIGN: 'FOREIGN',
  政府机关: 'GOVERNMENT',
  '政府机关/事业单位/高校': 'GOVERNMENT',
  政府: 'GOVERNMENT',
  央企: 'CENTRAL_SOE',
  地方国企: 'LOCAL_SOE',
  民企: 'PRIVATE',
  企业客户: 'PRIVATE',
  个人: 'PRIVATE',
  政府: 'GOVERNMENT',
  港澳台及外企: 'FOREIGN',
}

export function useInitiationStageActions({
  props,
  emit,
  form,
  custFixedRows,
  bidDocFiles,
  userStore,
  projectLifecycleApi,
  projectsApi,
  tendersApi,
  usersApi,
  projectsState,
  leaderOptions,
  assistantOptions,
}) {
  const {
    existing,
    saving,
    submitting,
    approving,
    rejecting,
    uploadingDoc,
    errorMsg,
    reviewStatus,
    fieldLocked,
    approvalForm,
  } = projectsState

  function beforeUploadDoc(file) {
    const valid = ['.pdf', '.doc', '.docx'].some((ext) => file.name.toLowerCase().endsWith(ext))
    if (!valid) {
      ElMessage.error('仅支持 PDF/Word 格式')
      return false
    }
    return true
  }

  async function handleDocBeforeUpload(file) {
    if (!beforeUploadDoc(file)) return false
    uploadingDoc.value = true
    errorMsg.value = ''
    try {
      const formData = new FormData()
      formData.set('file', file)
      formData.set('name', file.name)
      formData.set('size', `${Math.max(1, Math.round((file.size || 1024 * 1024) / 1024 / 1024))}MB`)
      formData.set('fileType', file.type || 'application/octet-stream')
      formData.set('documentCategory', 'TENDER_DOCUMENT')
      formData.set('linkedEntityType', 'PROJECT_INITIATION')
      formData.set('linkedEntityId', String(props.projectId))
      formData.set('uploaderId', String(userStore.currentUser?.id || ''))
      formData.set('uploaderName', userStore.userName || '')
      const result = await projectsApi.uploadDocument(props.projectId, formData)
      if (!result?.success || !result?.data) throw new Error(result?.msg || '招标文件上传失败')
      form.tenderDocumentId = result.data.id
      bidDocFiles.value = [{ name: result.data.name || file.name, url: result.data.fileUrl || '', uploader: result.data.uploader || userStore.userName, status: 'success' }]
      ElMessage.success(`招标文件上传成功：${result.data.name || file.name}`)
    } catch (e) {
      errorMsg.value = e?.response?.data?.msg || e?.message || '招标文件上传失败'
      bidDocFiles.value = []
    } finally {
      uploadingDoc.value = false
    }
    return false
  }

  function onDepositChange(val) {
    if (val === 'NO') {
      form.depositAmount = 0
      form.depositPaymentMethod = ''
    }
  }

  function buildPayload() {
    const pType = PROJECT_TYPE_SUBMIT_MAP[form.projectType] || form.projectType
    const cType = CUSTOMER_TYPE_SUBMIT_MAP[form.customerType] || form.customerType
    return {
      ...form,
      projectType: pType && String(pType).trim() ? pType : null,
      customerType: cType && String(cType).trim() ? cType : null,
      annualRevenue: form.customerRevenue || form.annualRevenue,
      customerInfoRows: custFixedRows.value,
    }
  }

  async function load() {
    try {
      const response = await projectLifecycleApi.getInitiation(props.projectId)
      const data = response?.data || response
      if (!data) return
      Object.assign(form, data)
      if (data.tenderDocumentId) {
        try {
          const docResp = await projectsApi.getDocuments(props.projectId, {
            documentCategory: 'TENDER_DOCUMENT',
            linkedEntityType: 'PROJECT_INITIATION',
            linkedEntityId: props.projectId,
          })
          const docs = docResp?.data || []
          if (docs.length > 0) {
            const doc = docs[0]
            bidDocFiles.value = [{ name: doc.name, url: doc.fileUrl || '', uploader: doc.uploader || '', status: 'success' }]
          }
        } catch (e) {
          console.warn('[InitiationStage] failed to load tender document', e)
        }
      }
      if (data.primaryLeadUserId) {
        approvalForm.biddingLeaderId = data.primaryLeadUserId
        loadUserLabel(data.primaryLeadUserId, 'leader').catch(() => {})
      }
      if (data.secondaryLeadUserId) {
        approvalForm.biddingAssistantId = data.secondaryLeadUserId
        loadUserLabel(data.secondaryLeadUserId, 'assistant').catch(() => {})
      }
      if (data.customerInfoRows) {
        custFixedRows.value = data.customerInfoRows
      } else if (data.customerInfoJson) {
        try {
          custFixedRows.value = JSON.parse(data.customerInfoJson)
        } catch {
          // ignore malformed JSON
        }
      }
      // 修复：从 API 恢复招标文件列表，避免回切 tab 后上传组件显示为空
      if (data.tenderDocumentId) {
        bidDocFiles.value = [{
          name: data.tenderDocumentName || data.tenderDocFileName || '招标文件',
          url: data.tenderDocumentUrl || data.tenderDocFileUrl || '',
          status: 'success',
        }]
      } else {
        bidDocFiles.value = []
      }
      // 修复：确保已分配人员姓名字段在 APPROVED 状态可读（后端可能以 biddingLeaderName / biddingAssistantName 返回）
      if (data.biddingLeaderName) form.biddingLeaderName = data.biddingLeaderName
      if (data.biddingAssistantName) form.biddingAssistantName = data.biddingAssistantName
      existing.value = true
      reviewStatus.value = data.reviewStatus || ''
      fieldLocked.value = !!data.bidOpenTime && !!data.ownerUnit
    } catch (e) {
      if (e?.response?.status === 404) {
        await autoFillFromTender()
        return
      }
      console.warn(e)
    }
  }

  async function autoFillFromTender() {
    try {
      const projectResp = await projectsApi.getDetail(props.projectId)
      const project = projectResp?.data || projectResp
      const tenderId = project?.tenderId
      const tender = tenderId ? (await tendersApi.getDetail(tenderId).then(r => r?.data || r).catch(() => null)) : null
      const src = tender || project || {}
      const bidTime = src.bidOpeningTime || src.bidOpenTime || ''
      Object.assign(form, {
        bidOpenTime: bidTime,
        bidMonth: bidTime ? new Date(bidTime).toISOString().slice(0, 7).replace('-', '/') : '',
        projectType: src.projectType || '',
        customerType: src.customerType || '',
        ownerUnit: src.purchaserName || src.ownerUnit || '',
        projectLeaderName: src.projectManagerName || src.projectLeaderName || '',
        leaderDepartment: src.department || src.leaderDepartment || '',
        biddingLeaderName: src.biddingPersonName || src.biddingLeaderName || '',
        biddingPlatform: src.platform || src.biddingPlatform || '',
        headquartersLocation: src.region || src.headquartersLocation || '',
        contactName: src.contactName || '',
        contactPhone: src.contactPhone || '',
        contactTel: src.contactTel || '',
        contactMail: src.contactMail || '',
        contactName2: src.contactName2 || '',
        contactPhone2: src.contactPhone2 || '',
        contactTel2: src.contactTel2 || '',
        contactMail2: src.contactMail2 || '',
        customerRevenue: src.annualRevenue ?? form.customerRevenue,
        annualRevenue: src.annualRevenue ?? form.annualRevenue,
        annualEcommerceAmount: src.budget ?? form.annualEcommerceAmount,
        expectedBidders: src.expectedBidders ?? form.expectedBidders,
        riskAssessment: src.riskAssessment || '',
        riskMitigationPlan: src.riskMitigationPlan || '',
        tenderAdverseItems: src.tenderAdverseItems || src.unfavorableItems || '',
        supportNeeded: src.supportNeeded || '',
        projectPlanGap: src.projectPlanGap || '',
        projectName: src.projectName || src.name || '',
        tenderId,
        createTime: project.createdAt ? new Date(project.createdAt).toLocaleString('zh-CN') : '',
      })
      // 从标讯评估表补充字段
      if (tenderId) {
        try {
          const evalResp = await tendersApi.getEvaluation(tenderId)
          const evaluation = evalResp?.data || evalResp
          if (evaluation && evaluation.evaluationBasic) {
            const b = evaluation.evaluationBasic
            Object.assign(form, {
              expectedBidders: b.plannedShortlistedCount ?? form.expectedBidders,
              annualEcommerceAmount: b.mroOfficeFlowAmount ?? form.annualEcommerceAmount,
              customerRevenue: b.customerRevenue ?? form.customerRevenue,
              annualRevenue: b.customerRevenue ?? form.annualRevenue,
              tenderAdverseItems: b.unfavorableItems ?? form.tenderAdverseItems,
              riskAssessment: b.riskAssessment ?? form.riskAssessment,
              riskMitigationPlan: b.contingencyPlan ?? form.riskMitigationPlan,
              pmUnderstandsProcess: b.processKnowledge ?? form.pmUnderstandsProcess,
              supportNeeded: b.supportNotes ?? form.supportNeeded,
              projectPlanGap: b.projectPlanGap ?? form.projectPlanGap,
            })
          }
          // 评估表客户信息矩阵 EAV → 立项表单 CustomerInfoRow
          const evalCustomerInfos = evaluation?.evaluationCustomerInfos
          if (Array.isArray(evalCustomerInfos) && evalCustomerInfos.length > 0) {
            const INFO_KEY_MAP = {
              NAME: 'name', POSITION: 'position', XIYU_CONTACT: 'xiyuContact',
              CONTACTED: 'reached', CONTACT_METHOD: 'reachMethod',
              TENDENCY: 'preference', INFO_TENDENCY_BASIS: 'preferenceBasis',
              HIGH_LEVEL_EXCHANGE: 'hasHighLevelMeeting', GUIDED_BID: 'guideBid',
              CAN_GET_KEY_INFO: 'canGetKeyInfo', CAN_REMOVE_ADVERSE: 'canRemoveAdverse',
              KEY_TARGET: 'isKeyTarget', CAN_SYNC_EVAL: 'canSyncEval',
              INFO_CLEAR_WINNER_BID: 'canConfirmWin', INFO_WIN_RATE_IMPACT: 'winRateImpact',
            }
            // 按 roleKey 分组
            const roleMap = {}
            evalCustomerInfos.forEach(e => {
              if (!roleMap[e.roleKey]) roleMap[e.roleKey] = {}
              const fieldKey = INFO_KEY_MAP[e.infoKey]
              if (fieldKey) roleMap[e.roleKey][fieldKey] = e.value
            })
            // 映射到 custFixedRows
            const ROW_ROLE_MAP = {
              PROJECT_HIGHEST_DECISION_MAKER: '项目最高决策人',
              MATERIALS_COMPANY_CHAIRMAN: '物资公司董事长',
              MATERIALS_COMPANY_ELECTRONICS_LEADER: '物资公司分管电商领导',
              ELECTRONICS_COMPANY_CHAIRMAN: '电商公司董事长',
              ELECTRONICS_COMPANY_GENERAL_MANAGER: '电商公司总经理',
              ELECTRONICS_COMPANY_DEPUTY_GENERAL_MANAGER: '电商公司副总经理',
              ELECTRONICS_COMPANY_OPERATIONS_LEADER: '电商公司运营负责人',
              BID_DOCUMENT_PREPARER: '招标文件制作人',
              OTHER_KEY_DECISION_MAKER_1: '其他关键决策人1',
              OTHER_KEY_DECISION_MAKER_2: '其他关键决策人2',
              OTHER_KEY_DECISION_MAKER_3: '其他关键决策人3',
              EXPERT_1: '专家1',
              EXPERT_2: '专家2',
              EXPERT_3: '专家3',
            }
            custFixedRows.value = custFixedRows.value.map(row => {
              const roleKey = Object.keys(ROW_ROLE_MAP).find(k => ROW_ROLE_MAP[k] === row.role)
              if (!roleKey || !roleMap[roleKey]) return row
              return { ...row, ...roleMap[roleKey] }
            })
          }
        } catch (evalErr) {
          console.warn('[InitiationStage] auto-fill evaluation data failed', evalErr)
        }
      }
    } catch (e) {
      console.warn('[InitiationStage] auto-fill from tender failed', e)
    }
  }

  async function handleApprove() {
    if (!approvalForm.biddingLeaderId) return ElMessage.warning('请选择投标负责人')
    approving.value = true
    errorMsg.value = ''
    try {
      await projectLifecycleApi.approveInitiation(props.projectId, {
        primaryLeadUserId: approvalForm.biddingLeaderId,
        secondaryLeadUserId: approvalForm.biddingAssistantId || null,
      })
      ElMessage.success('已通过，项目进入标书制作阶段')
      emit('updated')
      await load()
    } catch (e) {
      errorMsg.value = e?.response?.data?.msg || '审批失败'
    } finally {
      approving.value = false
    }
  }

  async function handleReject() {
    try {
      const { value: reason } = await ElMessageBox.prompt('请填写驳回原因', '驳回立项', {
        confirmButtonText: '确认驳回',
        cancelButtonText: '取消',
        inputType: 'textarea',
        inputErrorMessage: '驳回原因不能为空',
        inputValidator: (value) => Boolean(value && value.trim()),
      })
      if (!reason || !reason.trim()) return
      rejecting.value = true
      errorMsg.value = ''
      try {
        await projectLifecycleApi.rejectInitiation(props.projectId, { rejectionReason: reason.trim() })
        ElMessage.success('已驳回')
        emit('updated')
        await load()
      } catch (e) {
        errorMsg.value = e?.response?.data?.msg || '驳回失败'
      } finally {
        rejecting.value = false
      }
    } catch {
      // user cancelled
    }
  }

  async function saveDraft() {
    saving.value = true
    errorMsg.value = ''
    try {
      if (existing.value) {
        await projectLifecycleApi.updateInitiation(props.projectId, buildPayload())
      } else {
        await projectLifecycleApi.submitInitiation(props.projectId, buildPayload())
      }
      ElMessage.success('草稿已保存')
      existing.value = true
      await load()
    } catch (e) {
      errorMsg.value = e?.response?.data?.msg || '保存失败'
    } finally {
      saving.value = false
    }
  }

  async function submit() {
    if (form.needDeposit === 'YES' && !form.depositPaymentMethod) {
      return ElMessage.warning('请选择保证金缴纳方式')
    }
    submitting.value = true
    errorMsg.value = ''
    try {
      await projectLifecycleApi.submitInitiation(props.projectId, buildPayload())
      ElMessage.success('立项已提交')
      fieldLocked.value = true
      existing.value = true
      emit('updated')
      await load()
    } catch (e) {
      errorMsg.value = e?.response?.data?.msg || '提交失败'
    } finally {
      submitting.value = false
    }
  }

  async function loadUserLabel(userId, target) {
    try {
      const users = await usersApi.getByIds([userId])
      const u = Array.isArray(users) ? users[0] : null
      if (!u) return
      const label = u.name + '（' + (u.employeeId || '') + '）- ' + (u.departmentName || u.deptName || '')
      const option = { id: u.id, _label: label, name: u.name, employeeId: u.employeeId, departmentName: u.departmentName }
      if (target === 'leader') {
        approvalForm.biddingLeaderLabel = label
        leaderOptions.value = [option]
      } else {
        approvalForm.biddingAssistantLabel = label
        assistantOptions.value = [option]
      }
    } catch { /* ignore */ }
  }

  return {
    beforeUploadDoc,
    handleDocBeforeUpload,
    onDepositChange,
    handleApprove,
    handleReject,
    saveDraft,
    submit,
    load,
  }
}
