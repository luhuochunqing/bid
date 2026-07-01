// Input: organizationApi
// Output: CRM 工号行内编辑 composable
// Pos: src/views/System/organization/ - CO-152 CRM 工号按用户维度管理
// 维护声明:
//   - 维护人: [your-name]
//   - 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { organizationApi } from '@/api/modules/organization.js'

/**
 * CO-152: 用户 CRM 工号行内编辑 composable。
 * 提供 CRM 工号列的编辑/保存/取消能力，调用 PUT /api/admin/users/{id} 整体更新。
 * @returns {{editingCrmId, editCrmValue, crmLoading, startEditCrm, cancelEditCrm, saveCrmSalesNo}}
 */
export function useUserCrmEdit() {
  const editingCrmId = ref(null)
  const editCrmValue = ref('')
  const crmLoading = ref(null)

  function startEditCrm(row) {
    editingCrmId.value = row.id
    editCrmValue.value = row.crmSalesNo || ''
  }

  function cancelEditCrm() {
    editingCrmId.value = null
    editCrmValue.value = ''
  }

  async function saveCrmSalesNo(row) {
    const newValue = editCrmValue.value.trim()
    if (newValue === (row.crmSalesNo || '')) {
      cancelEditCrm()
      return
    }
    crmLoading.value = row.id
    try {
      const payload = {
        username: row.username,
        email: row.email,
        fullName: row.fullName,
        phone: row.phone || null,
        departmentCode: row.departmentCode || null,
        departmentName: row.departmentName || null,
        employeeNumber: row.employeeNumber || null,
        crmSalesNo: newValue || null,
        roleId: row.roleId,
        enabled: row.enabled,
      }
      const res = await organizationApi.updateUser(row.id, payload)
      const updated = res?.data?.data || res?.data || res
      row.crmSalesNo = updated?.crmSalesNo ?? newValue
      editingCrmId.value = null
      editCrmValue.value = ''
      ElMessage.success(newValue ? 'CRM 工号已更新' : 'CRM 工号已清空')
    } catch {
      ElMessage.error('CRM 工号更新失败')
    } finally {
      crmLoading.value = null
    }
  }

  return { editingCrmId, editCrmValue, crmLoading, startEditCrm, cancelEditCrm, saveCrmSalesNo }
}
