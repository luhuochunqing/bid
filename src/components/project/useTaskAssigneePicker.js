import { formatDisplayName } from '@/utils/formatDisplayName.js'
import { useUserPicker } from '@/composables/useUserPicker.js'

/**
 * P1.5: TaskForm 专用 assignee picker composable.
 *
 * 复用 useUserPicker 的候选人加载逻辑（统一端点 + 竞态保护 + 卸载清理），
 * 只保留 TaskForm 特有的字段同步和已选候选人补全逻辑。
 */
export function useTaskAssigneePicker({ localValue, userStore }) {
  // P1.5: 复用 useUserPicker 的加载逻辑，统一走 /api/users/assignable-candidates
  const { options: candidateOptions, loading: loadingCandidates, loadCandidates } = useUserPicker({
    mode: 'candidates',
    context: 'task',
  })

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
    const selected = selectedId == null ? null : candidateOptions.value.find((person) => Number(person.id) === selectedId)
    if (selected) return applyAssignee(selected)

    const fromTask = candidateFromTaskValue()
    if (fromTask?.id != null) {
      candidateOptions.value = upsertCandidate(candidateOptions.value, fromTask)
      return applyAssignee(fromTask)
    }
  }

  async function loadAssignees() {
    await loadCandidates()
    ensureSelectedAssignee()
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
    assigneeOptions: candidateOptions,
    loadingAssignees: loadingCandidates,
    loadAssignees,
    ensureSelectedAssignee,
    handleAssigneeSelect,
  }
}
