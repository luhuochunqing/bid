import { ref } from 'vue'
import { usersApi } from '@/api/modules/users.js'
import { formatDisplayName } from '@/utils/formatDisplayName.js'

/**
 * Task assignee picker composable.
 * Loads task assignment candidates and normalizes them for UserPicker.
 */
export function useTaskAssigneePicker({ localValue, userStore }) {
  const assigneeOptions = ref([])
  const loadingAssignees = ref(false)

  async function loadAssignees() {
    loadingAssignees.value = true
    try {
      const candidates = await usersApi.getTaskAssignmentCandidates()
      assigneeOptions.value = normalizeCandidates(candidates)
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
      if (normalized?.id != null) byId.set(Number(normalized.id), normalized)
    })
    const current = currentUserCandidate()
    if (current?.id != null && !byId.has(Number(current.id))) {
      byId.set(Number(current.id), current)
    }
    return Array.from(byId.values())
  }

  function normalizeCandidate(candidate = {}) {
    const id = candidate.userId ?? candidate.id
    if (id == null) return null
    return {
      id: Number(id),
      name: formatDisplayName(candidate.name || candidate.fullName || candidate.username, candidate.employeeNumber) || `用户#${id}`,
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
      name: formatDisplayName(user.name || user.fullName || userStore.userName, user.employeeNumber),
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

  function upsertCandidate(list, candidate) {
    const next = new Map((list || []).map((item) => [Number(item.id), item]))
    next.set(Number(candidate.id), candidate)
    return Array.from(next.values())
  }

  function ensureSelectedAssignee() {
    const selectedId = localValue.assigneeId == null ? null : Number(localValue.assigneeId)
    const selected = selectedId == null ? null : assigneeOptions.value.find((person) => Number(person.id) === selectedId)
    if (selected) return applyAssignee(selected)

    const fromTask = candidateFromTaskValue()
    if (fromTask?.id != null) {
      assigneeOptions.value = upsertCandidate(assigneeOptions.value, fromTask)
      return applyAssignee(fromTask)
    }
  }

  function handleAssigneeSelect(user) {
    if (!user) return
    const normalized = normalizeCandidate(user)
    if (normalized) applyAssignee(normalized)
  }

  function applyAssignee(person) {
    assignIfChanged('assigneeId', Number(person.id))
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

  return {
    assigneeOptions,
    loadingAssignees,
    loadAssignees,
    ensureSelectedAssignee,
    handleAssigneeSelect,
  }
}
