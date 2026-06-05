// Input: selected qualification and borrow dialog visibility refs
// Output: OA-backed qualification borrow form state and submit handlers
// Pos: src/views/Knowledge/components/qualification/ - Qualification borrow workflow composition
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { isFeatureUnavailableResponse, workflowFormApi } from '@/api'

export function useQualificationBorrowWorkflow({ currentQualification, borrowDialogVisible }) {
  const borrowFormSchema = ref({ fields: [] })
  const borrowForm = reactive({
    borrower: '',
    department: '',
    projectId: '',
    purpose: '',
    expectedReturnDate: '',
    remark: ''
  })

  function resetBorrowForm() {
    Object.assign(borrowForm, {
      borrower: '',
      department: '',
      projectId: '',
      purpose: '',
      expectedReturnDate: '',
      remark: '',
      qualificationName: currentQualification.value?.name || ''
    })
  }

  async function openBorrowDialog(row) {
    currentQualification.value = row
    resetBorrowForm()
    borrowForm.qualificationName = row?.name || ''
    const definition = await workflowFormApi.getFormDefinition('QUALIFICATION_BORROW')
    borrowFormSchema.value = definition?.data || { fields: [] }
    borrowDialogVisible.value = true
  }

  async function handleConfirmBorrow() {
    if (!currentQualification.value?.id) {
      ElMessage.warning('请先从资质列表选择待借阅资质')
      return
    }
    if (!borrowForm.borrower || !borrowForm.projectId || !borrowForm.purpose || !borrowForm.expectedReturnDate) {
      ElMessage.warning('请填写必填项')
      return
    }
    const projectId = Number(borrowForm.projectId)
    if (!Number.isInteger(projectId) || projectId <= 0) {
      ElMessage.warning('请选择有效项目')
      return
    }

    const result = await workflowFormApi.submitWorkflowForm('QUALIFICATION_BORROW', {
      templateCode: 'QUALIFICATION_BORROW',
      businessType: 'QUALIFICATION_BORROW',
      projectId,
      applicantName: borrowForm.borrower,
      formData: {
        qualificationId: String(currentQualification.value.id),
        borrower: borrowForm.borrower,
        department: borrowForm.department,
        projectId: String(projectId),
        purpose: borrowForm.purpose,
        expectedReturnDate: borrowForm.expectedReturnDate,
        remark: borrowForm.remark
      }
    })

    if (result?.success) {
      borrowDialogVisible.value = false
      ElMessage.success('资质借阅申请已提交 OA 审批')
      return
    }

    if (isFeatureUnavailableResponse(result)) {
      ElMessage.warning(result.msg || '资质借阅接口暂未接入')
      return
    }

    ElMessage.error(result?.msg || '借阅申请提交失败')
  }

  return {
    borrowForm,
    borrowFormSchema,
    handleConfirmBorrow,
    openBorrowDialog
  }
}

export default useQualificationBorrowWorkflow
