// Input: task form value, selected files, and current user store
// Output: backend task assignee and attachment upload payloads
// Pos: src/composables/projectDetail/ - Task payload and attachment upload helper
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

export function createTaskAssigneePayload(data = {}, userStore = {}) {
  return {
    assigneeId: data?.assigneeId ?? userStore.currentUser?.id ?? null,
    assigneeName: data?.owner || data?.assignee || userStore.userName,
    assigneeDeptCode: data?.assigneeDeptCode || '',
    assigneeDeptName: data?.assigneeDeptName || data?.department || '',
    assigneeRoleCode: data?.assigneeRoleCode || '',
    assigneeRoleName: data?.assigneeRoleName || data?.roleName || '',
  }
}

export function normalizeTaskAttachmentFiles(attachments = []) {
  return (Array.isArray(attachments) ? attachments : [attachments])
    .map((item) => item?.raw || item?.file || item)
    .filter(Boolean)
}

export function createTaskAttachmentPayload(file, userStore = {}) {
  return {
    name: file?.name || '任务附件',
    deliverableType: 'DOCUMENT',
    file,
    uploaderId: userStore.currentUser?.id ?? null,
    uploaderName: userStore.userName,
  }
}

export async function uploadTaskAttachments(task, attachments, { projectStore, projectId, userStore } = {}) {
  for (const file of normalizeTaskAttachmentFiles(attachments)) {
    const saved = await projectStore?.addDeliverable?.(projectId, task.id, createTaskAttachmentPayload(file, userStore))
    if (!saved) continue
    task.deliverables = [saved, ...(task.deliverables || []).filter((item) => String(item.id) !== String(saved.id))]
    task.hasDeliverable = true
  }
}
