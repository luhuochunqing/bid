// Input: Task form local state, mode accessor, current user store, and usersApi
// Output: task assignee select options and selection helpers
// Pos: src/components/project/ - Task form pure-ish UI state helper
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref } from 'vue'
import { usersApi } from '@/api/modules/users.js'

export function useTaskAssigneeOptions({ localValue, isCreateMode, userStore }) {
  const assigneeOptions = ref([])
  const loadingAssignees = ref(false)

  async function loadAssignees() {
    loadingAssignees.value = true
    try {
      assigneeOptions.value = normalizeCandidates(await usersApi.getTaskAssignmentCandidates())
    } catch (err) {
      console.error('[TaskForm] Failed to load task assignment candidates', err)
      assigneeOptions.value = []
    } finally {
      ensureSelectedAssignee()
      loadingAssignees.value = false
    }
  }

  function normalizeCandidates(candidates) {
    const byId = new Map()
    ;(Array.isArray(candidates) ? candidates : []).forEach((candidate) => {
      const normalized = normalizeCandidate(candidate)
      if (normalized?.userId != null) byId.set(Number(normalized.userId), normalized)
    })
    const current = currentUserCandidate()
    if (current?.userId != null && !byId.has(Number(current.userId))) {
      byId.set(Number(current.userId), current)
    }
    return Array.from(byId.values())
  }

  function normalizeCandidate(candidate = {}) {
    const id = candidate.userId ?? candidate.id
    if (id == null) return null
    return {
      userId: Number(id),
      name: candidate.name || candidate.fullName || candidate.username || `用户#${id}`,
      deptCode: candidate.deptCode || candidate.departmentCode || '',
      deptName: candidate.deptName || candidate.departmentName || '',
      roleCode: candidate.roleCode || '',
      roleName: candidate.roleName || candidate.role || '',
      enabled: candidate.enabled !== false,
    }
  }

  function currentUserCandidate() {
    const user = userStore.currentUser
    if (!user?.id) return null
    return normalizeCandidate({
      userId: user.id,
      name: user.name || user.fullName || userStore.userName,
      deptCode: user.departmentCode,
      deptName: user.departmentName,
      roleCode: user.roleCode || user.role,
      roleName: user.roleName,
      enabled: user.enabled !== false,
    })
  }

  function candidateFromTaskValue() {
    if (localValue.assigneeId == null && !localValue.owner && !localValue.assignee) return null
    return normalizeCandidate({
      userId: localValue.assigneeId,
      name: localValue.owner || localValue.assignee,
      deptCode: localValue.assigneeDeptCode,
      deptName: localValue.assigneeDeptName || localValue.department,
      roleCode: localValue.assigneeRoleCode,
      roleName: localValue.assigneeRoleName || localValue.roleName,
    })
  }

  function ensureSelectedAssignee() {
    const selectedId = localValue.assigneeId == null ? null : Number(localValue.assigneeId)
    const selected = selectedId == null ? null : assigneeOptions.value.find((person) => Number(person.userId) === selectedId)
    if (selected) return applyAssignee(selected)

    const fromTask = candidateFromTaskValue()
    if (fromTask?.userId != null) {
      assigneeOptions.value = upsertCandidate(assigneeOptions.value, fromTask)
      return applyAssignee(fromTask)
    }

    if (isCreateMode()) {
      const current = currentUserCandidate()
      if (current?.userId != null) {
        assigneeOptions.value = upsertCandidate(assigneeOptions.value, current)
        applyAssignee(current)
      }
    }
  }

  function upsertCandidate(list, candidate) {
    const next = new Map((list || []).map((item) => [Number(item.userId), item]))
    next.set(Number(candidate.userId), candidate)
    return Array.from(next.values())
  }

  function handleAssigneeChange(value) {
    const selected = assigneeOptions.value.find((person) => String(person.userId) === String(value))
    if (selected) applyAssignee(selected)
  }

  function applyAssignee(person) {
    assignIfChanged('assigneeId', Number(person.userId))
    assignIfChanged('owner', person.name)
    assignIfChanged('assignee', person.name)
    assignIfChanged('department', person.deptName || '')
    assignIfChanged('roleName', person.roleName || '')
    assignIfChanged('assigneeDeptCode', person.deptCode || '')
    assignIfChanged('assigneeDeptName', person.deptName || '')
    assignIfChanged('assigneeRoleCode', person.roleCode || '')
    assignIfChanged('assigneeRoleName', person.roleName || '')
  }

  function assignIfChanged(key, value) {
    if (localValue[key] !== value) {
      localValue[key] = value
    }
  }

  function assigneeLabel(person) {
    const meta = [person.deptName, person.roleName].filter(Boolean).join(' / ')
    return meta ? `${person.name}（${meta}）` : person.name
  }

  return { assigneeOptions, loadingAssignees, loadAssignees, ensureSelectedAssignee, handleAssigneeChange, assigneeLabel }
}
