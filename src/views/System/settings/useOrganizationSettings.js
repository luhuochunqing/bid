// Input: settings/auth APIs
// Output: shared organization settings state and persistence actions
// Pos: Settings organization application-shell composable

import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { authApi, settingsApi } from '@/api'
import { persistRuntimeSettings } from '@/api/modules/settings'
import { useUserStore } from '@/stores/user'
import {
  buildRolePayload,
  buildUserOrganizationPayload,
  normalizeDeptTree,
  normalizeRoles,
  normalizeUsers
} from './organization-normalizers'

export function useOrganizationSettings() {
  const userStore = useUserStore()
  const loading = ref(false)
  const deptTree = ref([])
  const users = ref([])
  const roles = ref([])
  const userDataScope = ref([])
  const deptDataScope = ref([])

  const enabledRoles = computed(() => roles.value.filter((role) => role.enabled))
  const deptOptions = computed(() => deptTree.value.map((dept) => ({
    value: dept.deptCode,
    label: dept.deptName || dept.deptCode
  })))

  const applySnapshot = (payload = {}) => {
    deptTree.value = normalizeDeptTree(payload.deptTree)
    users.value = normalizeUsers(payload.users)
    roles.value = normalizeRoles(payload.roles)
    userDataScope.value = Array.isArray(payload.userDataScope) ? payload.userDataScope : []
    deptDataScope.value = Array.isArray(payload.deptDataScope) ? payload.deptDataScope : []
    persistRuntimeSettings({ roles: roles.value })
  }

  const refreshCurrentSession = async () => {
    if (!userStore.currentUser) return
    try {
      const result = await authApi.getCurrentUser()
      if (result?.success && result?.data) {
        userStore.applyAuthSession({ user: result.data }, true)
      }
    } catch (error) {
      // Session refresh failure is non-blocking; next API call will re-authenticate if needed
    }
  }

  const load = async () => {
    loading.value = true
    try {
      const result = await settingsApi.getDataScopeConfig()
      if (!result?.success) throw new Error(result?.msg || '加载组织配置失败')
      applySnapshot(result.data)
    } catch (error) {
      ElMessage.error(error?.message || '加载组织配置失败')
    } finally {
      loading.value = false
    }
  }

  const saveDepartments = async (rows) => {
    const result = await settingsApi.saveDepartmentTree(normalizeDeptTree(rows))
    if (!result?.success) throw new Error(result?.msg || '保存部门树失败')
    applySnapshot(result.data)
    ElMessage.success('部门树已保存')
    await refreshCurrentSession()
  }

  const saveUserOrganization = async (userId, form) => {
    const result = await settingsApi.updateUserOrganization(userId, buildUserOrganizationPayload(form))
    if (!result?.success) throw new Error(result?.msg || '保存用户归属失败')
    await load()
    await refreshCurrentSession()
    ElMessage.success('用户组织归属已保存')
  }

  const saveRole = async (role) => {
    const payload = buildRolePayload(role)
    const result = role.id
      ? await settingsApi.updateRole(role.id, payload)
      : await settingsApi.createRole(payload)
    if (!result?.success) throw new Error(result?.msg || '保存角色失败')
    await load()
    await refreshCurrentSession()
    ElMessage.success('角色已保存')
  }

  const toggleRole = async (role) => {
    const result = await settingsApi.updateRoleStatus(role.id, !role.enabled)
    if (!result?.success) throw new Error(result?.msg || '更新角色状态失败')
    await load()
    await refreshCurrentSession()
    ElMessage.success(`角色已${role.enabled ? '停用' : '启用'}`)
  }

  const resetRole = async (role) => {
    const result = await settingsApi.resetRole(role.id)
    if (!result?.success) throw new Error(result?.msg || '恢复角色默认配置失败')
    await load()
    await refreshCurrentSession()
    ElMessage.success('角色已恢复默认配置')
  }

  return {
    loading,
    deptTree,
    deptOptions,
    users,
    roles,
    enabledRoles,
    userDataScope,
    deptDataScope,
    load,
    saveDepartments,
    saveUserOrganization,
    saveRole,
    toggleRole,
    resetRole
  }
}
