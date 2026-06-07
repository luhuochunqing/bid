// Input: project detail DTO and current user name
// Output: pure activity timeline helpers for project detail sidebar
// Pos: src/composables/projectDetail/ - Project detail composition helpers
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

function pad(value) {
  return String(value).padStart(2, '0')
}

export function formatProjectActivityTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function resolveProjectActivityUser(project = {}, fallbackUser = '') {
  return project.createdByName
    || project.creatorName
    || project.ownerName
    || project.managerName
    || fallbackUser
    || '系统'
}

export function buildProjectBaselineActivities(project = {}, fallbackUser = '') {
  if (!project?.id) return []
  return [{
    id: `project-created-${project.id}`,
    user: resolveProjectActivityUser(project, fallbackUser),
    action: '创建了项目',
    time: formatProjectActivityTime(project.createdAt || project.createTime || project.updatedAt || project.updateTime)
  }]
}
