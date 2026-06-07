// Input: project create task form rows
// Output: pure task create API payload normalizers
// Pos: src/views/Project/create/composables/ - Project create pure helpers
// 一旦我被更新，务必更新所属文件夹文档。

const normalizeTaskPriorityForApi = (priority) => ({
  high: 'HIGH',
  medium: 'MEDIUM',
  low: 'LOW',
  urgent: 'URGENT',
}[String(priority || '').trim().toLowerCase()] || 'MEDIUM')

const normalizeTaskDueDateForApi = (value) => {
  if (!value) return null
  const normalized = String(value)
  return normalized.includes('T') ? normalized : `${normalized}T00:00:00`
}

export function buildTaskCreatePayloadsFromRows(tasks = []) {
  return tasks
    .map((task) => ({
      title: String(task.name || '').trim(),
      description: '',
      assigneeName: String(task.owner || '').trim(),
      priority: normalizeTaskPriorityForApi(task.priority),
      dueDate: normalizeTaskDueDateForApi(task.deadline),
    }))
    .filter((task) => task.title)
}
