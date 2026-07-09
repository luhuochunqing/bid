// Input: task form value, selected files, and current user store
// Output: backend task assignee, attachment upload, and deliverable upload payloads
// Pos: src/composables/projectDetail/ - Task payload, attachment upload, and deliverable upload helper
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { parallelUpload } from '@/utils/parallelUpload.js'

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

export function isFileLike(value) {
  if (!value) return false
  // File 继承自 Blob，两者都有 size 和 type
  // 优先 instanceof（最准确），fallback 到 duck-typing（兼容 jsdom 等环境）
  if (typeof File !== 'undefined' && value instanceof File) return true
  if (typeof Blob !== 'undefined' && value instanceof Blob) return true
  return typeof value === 'object'
    && typeof value.size === 'number'
    && typeof value.type === 'string'
    && typeof value.name === 'string'
}

// CO-529: 区分"真正需要上传的新文件"与"已保存的附件记录"。
// 已保存记录来自后端 DTO，有 id 但无 raw/file；新文件是 File/Blob 或带 raw/file 的 wrapper。
function isNewUploadItem(item) {
  return item && (item.raw || item.file || !item.id)
}

export function normalizeTaskAttachmentFiles(attachments = []) {
  return (Array.isArray(attachments) ? attachments : [attachments])
    .map((item) => item?.raw || item?.file || item)
    .filter((file) => isFileLike(file))
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
  // CO-529: 提交时 localValue.attachments 会混有已保存记录，
  // 只把真正需要上传的新文件交给 normalizeTaskAttachmentFiles。
  const items = Array.isArray(attachments) ? attachments : [attachments]
  const uploadItems = items.filter(isNewUploadItem)
  const files = normalizeTaskAttachmentFiles(uploadItems)
  // CO-519: 用户选了文件但全部提取失败（非 File 对象），明确抛错避免静默失败
  // 上游 uploadTaskAttachmentsWithFallback 会 catch 并给用户友好提示
  const inputCount = uploadItems.filter(Boolean).length
  if (inputCount > 0 && files.length === 0) {
    throw new Error('文件读取失败，请刷新页面后重新选择；如仍失败，请尝试更换浏览器（推荐 Chrome 最新版）')
  }
  // L-07: 并发上传（上限 3），任意文件失败不阻塞其他文件，但最终汇总抛错保持原语义
  const { successes, failures } = await parallelUpload(
    files,
    (file) => projectStore?.uploadTaskAttachment?.(projectId, task.id, createTaskAttachmentPayload(file, userStore)),
    { concurrency: 3 },
  )
  successes.forEach(({ result: saved }) => {
    if (!saved) return
    task.attachments = [saved, ...(task.attachments || []).filter((item) => String(item.id) !== String(saved.id))]
  })
  if (failures.length > 0) {
    throw new Error(`附件上传失败 ${failures.length} 个文件`)
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
  const files = normalizeTaskAttachmentFiles(deliverableFiles)
  // CO-519: 同附件，交付物也要检测提取失败
  const inputCount = Array.isArray(deliverableFiles) ? deliverableFiles.filter(Boolean).length : (deliverableFiles ? 1 : 0)
  if (inputCount > 0 && files.length === 0) {
    throw new Error('文件读取失败，请刷新页面后重新选择；如仍失败，请尝试更换浏览器（推荐 Chrome 最新版）')
  }
  // L-07: 并发上传（上限 3），任意文件失败不阻塞其他文件，但最终汇总抛错保持原语义
  const { successes, failures } = await parallelUpload(
    files,
    (file) => projectStore?.addDeliverable?.(projectId, task.id, createTaskDeliverablePayload(file, userStore)),
    { concurrency: 3 },
  )
  successes.forEach(({ result: saved }) => {
    if (!saved) return
    task.deliverables = [
      ...(task.deliverables || []).filter((item) => String(item.id) !== String(saved.id)),
      saved,
    ]
  })
  task.hasDeliverable = (task.deliverables || []).length > 0
  if (failures.length > 0) {
    throw new Error(`交付物上传失败 ${failures.length} 个文件`)
  }
}

export function canCurrentUserUploadTaskDeliverables(task, userStore = {}) {
  const currentUserId = userStore.currentUser?.id
  return currentUserId != null && task?.assigneeId != null && String(currentUserId) === String(task.assigneeId)
}

export async function uploadTaskDeliverablesWithFallback(task, deliverableFiles, deps, fallbackMessage, message) {
  if (!deliverableFiles?.length) {
    // XIYU-1E 关联排查：deliverableFiles 为空时静默跳过会导致用户误以为上传成功。
    // 增加可观测日志，便于排查"用户选了文件但 ElUpload 状态异常导致文件列表丢失"的情况。
    console.warn('[uploadTaskDeliverables] deliverableFiles 为空，跳过上传', {
      taskId: task?.id,
      taskName: task?.name,
      hint: '若用户选择了文件但此处显示为空，可能是 ElUpload 组件状态不一致（file to be removed not found）',
    })
    return true
  }
  if (!canCurrentUserUploadTaskDeliverables(task, deps?.userStore)) {
    message?.warning?.('仅任务执行人本人可上传交付物，请让执行人打开任务后上传')
    return false
  }
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
  // CO-529: 附件失败不应阻塞交付物上传和任务流转
  // 附件是辅助材料，失败时只给用户提示，不影响后续流程
  // 交付物是任务完成的核心证据，失败时必须阻塞任务流转
  // 之前用 results.every(Boolean) 导致附件失败时整体返回 false，阻塞了任务流转
  await uploadTaskAttachmentsWithFallback(task, data.attachments, deps, messages.attachments, message)
  return uploadTaskDeliverablesWithFallback(task, data.deliverableFiles, deps, messages.deliverables, message)
}
