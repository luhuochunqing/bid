// CO-152 Review D2-2: 部门行内编辑 + 状态切换 composable
// 抽离自 OrganizationManagement.vue 控制文件行数
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { organizationApi } from '@/api/modules/organization.js'

export function useDeptEdit(departments, userList) {
  const editingDeptId = ref(null)
  const editDeptCode = ref('')
  const statusLoading = ref(null)

  function startEditDept(row) {
    editingDeptId.value = row.id
    editDeptCode.value = row.departmentCode || ''
  }

  function onDeptSelectChange(value) {
    const dept = departments.value.find(d => d.departmentCode === value)
    const user = userList.value.find(u => u.id === editingDeptId.value)
    if (!user || !dept) return

    organizationApi.updateUserOrganization(user.id, dept.departmentCode, dept.departmentName)
      .then(() => {
        user.departmentCode = dept.departmentCode
        user.departmentName = dept.departmentName
        editingDeptId.value = null
        ElMessage.success('部门已更新')
      })
      .catch(() => {})
  }

  async function onStatusChange(row, val) {
    statusLoading.value = row.id
    try {
      await organizationApi.updateUserStatus(row.id, val)
      ElMessage.success(val ? '已启用' : '已停用')
    } catch {
      row.enabled = !val
    } finally {
      statusLoading.value = null
    }
  }

  return { editingDeptId, editDeptCode, statusLoading, startEditDept, onDeptSelectChange, onStatusChange }
}
