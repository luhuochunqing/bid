// Input: task form value, selected files, and current user store
// Output: backend task assignee, attachment upload, and deliverable upload payloads
// Pos: src/composables/projectDetail/ - Task payload, attachment upload, and deliverable upload helper
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
    .filter((file) => Boolean(file))
}

export function createTaskAttachmentPayload(file, userStore = {}) {
  return {
    name: file?.name || '任务附件',
    documentCategory: 'TASK_ATTACHMENT',
    file,
    uploaderId: userStore.currentUser?.id ?? null,
    uploaderName: userStore.userName,
  }
}

export async function uploadTaskAttachments(task, attachments, { projectStore, projectId, userStore } = {}) {
  for (const file of normalizeTaskAttachmentFiles(attachments)) {
    const saved = await projectStore?.uploadTaskAttachment?.(projectId, task.id, createTaskAttachmentPayload(file, userStore))
    if (!saved) continue
    task.attachments = [saved, ...(task.attachments || []).filter((item) => String(item.id) !== String(saved.id))]
  }
}

export async function uploadTaskAttachmentsWithFallback(task, attachments, deps, fallbackMessage, message) {
  if (!attachments?.length) return true
  try {
    await uploadTaskAttachments(task, attachments, deps)
    return true
  } catch (error) {
    console.warn('[uploadTaskAttachments] 任务附件上传失败', error)
    message?.warning?.(fallbackMessage)
    return false
  }
}

export function createTaskDeliverablePayload(file, userStore = {}) {
  return {
    name: file?.name || '任务交付物',
    deliverableType: 'DOCUMENT',
    file,
    uploaderId: userStore.currentUser?.id ?? null,
    uploaderName: userStore.userName,
  }
}

export async function uploadTaskDeliverables(task, deliverableFiles, { projectStore, projectId, userStore } = {}) {
  for (const file of normalizeTaskAttachmentFiles(deliverableFiles)) {
    const saved = await projectStore?.addDeliverable?.(projectId, task.id, createTaskDeliverablePayload(file, userStore))
    if (!saved) continue
    task.deliverables = [
      ...(task.deliverables || []).filter((item) => String(item.id) !== String(saved.id)),
      saved,
    ]
    task.hasDeliverable = task.deliverables.length > 0
  }
}

export async function uploadTaskDeliverablesWithFallback(task, deliverableFiles, deps, fallbackMessage, message) {
  if (!deliverableFiles?.length) return true
  try {
    await uploadTaskDeliverables(task, deliverableFiles, deps)
    return true
  } catch (error) {
    console.warn('[uploadTaskDeliverables] 任务交付物上传失败', error)
    message?.warning?.(fallbackMessage)
    return false
  }
}

export async function uploadTaskFilesWithFallback(task, data, deps, messages, message) {
  const results = [
    await uploadTaskAttachmentsWithFallback(task, data.attachments, deps, messages.attachments, message),
    await uploadTaskDeliverablesWithFallback(task, data.deliverableFiles, deps, messages.deliverables, message),
  ]
  return results.every(Boolean)
}
