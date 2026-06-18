import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { tendersApi } from '@/api/modules/tenders.js'

const ACCEPT_FILE_TYPES = '.pdf,.doc,.docx,application/pdf,application/msword,application/vnd.openxmlformats-officedocument.wordprocessingml.document'
const PASTED_TEXT_MAX_LENGTH = 500000
const MAX_FILE_SIZE = 50 * 1024 * 1024

export function useTenderAiParse(form) {
  const parsingDocument = ref(false)

  watch(() => form.value.pastedText, (val) => {
    if (val && val.length > PASTED_TEXT_MAX_LENGTH) {
      form.value.pastedText = val.substring(0, PASTED_TEXT_MAX_LENGTH)
    }
  })

  function resolveUploadFile(file) {
    if (file instanceof File || file instanceof Blob) return file
    if (file?.raw instanceof File || file?.raw instanceof Blob) return file.raw
    return null
  }

  function isSupportedParseFile(file) {
    const name = String(file?.name || '').toLowerCase()
    return name.endsWith('.pdf') || name.endsWith('.doc') || name.endsWith('.docx')
  }

  async function handleFileChange(file, fileList) {
    form.value.attachments = fileList
    const uploadFile = resolveUploadFile(file)
    if (!uploadFile) return
    if (uploadFile.size > MAX_FILE_SIZE) {
      ElMessage.warning(`文件 "${uploadFile.name}" 超过 50MB 限制`)
      form.value.attachments = fileList.filter(f => {
        const fFile = resolveUploadFile(f)
        return fFile && fFile.size <= MAX_FILE_SIZE
      })
      return
    }
    if (!isSupportedParseFile(uploadFile)) {
      ElMessage.warning(`文件 "${uploadFile.name}" 格式不支持，仅支持 PDF/Word 文件`)
      form.value.attachments = fileList.filter(f => isSupportedParseFile(resolveUploadFile(f)))
      return
    }
    await runAiParse(async () => {
      const response = await tendersApi.parseTenderIntakeDocument(uploadFile, { entityId: 'create-tender' })
      if (!response?.success) throw new Error(response?.msg || '文档自动识别失败')
      applyParsedFields(response.data)
      applyAttachmentFileUrl(uploadFile, response.data)
      return 'DeepSeek/AI 已识别附件内容，可继续编辑后保存'
    })
  }

  async function handlePastedTextParse() {
    const text = form.value.pastedText?.trim()
    if (!text) { ElMessage.warning('请先粘贴标讯正文'); return }
    await runAiParse(async () => {
      const response = await tendersApi.parseTenderIntakeText(text, { entityId: 'create-tender' })
      if (!response?.success) throw new Error(response?.msg || '粘贴文本识别失败')
      applyParsedFields(response.data)
      return 'DeepSeek/AI 已识别粘贴文本，可继续编辑后保存'
    })
  }

  async function runAiParse(parseFn) {
    parsingDocument.value = true
    try {
      const msg = await parseFn()
      if (msg) ElMessage.success(msg)
    } catch (error) {
      const timedOut = error?.code === 'ECONNABORTED'
      ElMessage.warning(timedOut ? 'AI 解析超时，可继续手动填写' : '自动识别失败，可继续手动填写')
    } finally {
      parsingDocument.value = false
    }
  }

  function applyParsedFields(data) {
    if (!data) return

    // 扫描件检测：后端返回警告时提示用户
    const warnings = data?.warnings || []
    const scannedWarning = warnings.find(w => w.includes('SCANNED_DOCUMENT'))
    if (scannedWarning) {
      ElMessage.warning('该文件为扫描件，无法自动识别。请使用粘贴识别功能或手动填写。')
      return
    }

    const extracted = data?.extractedData && typeof data.extractedData === 'object' ? data.extractedData : null
    // Two mapping sets cover different AI field naming conventions:
    //   [0] AI returns form-style names (landline, phone, …)
    //   [1] AI returns API-style names (contactTel, contactName, …)
    // First non-empty value wins for each target field.
    const mappings = [
      {
        title: 'title', region: 'region', tenderAgency: 'purchaser',
        deadline: 'deadline', bidOpeningTime: 'bidOpeningTime',
        customerType: 'customerType', priority: 'priority',
        contact: 'contact', phone: 'phone', landline: 'landline',
        mail: 'mail', description: 'description', tenderInfo: 'tenderInfo',
        projectType: 'projectType',
      },
      {
        tenderTitle: 'title', projectName: 'title',
        tenderAgency: 'purchaser',
        deadline: 'deadline', bidOpeningTime: 'bidOpeningTime',
        region: 'region', customerType: 'customerType', priority: 'priority',
        contactName: 'contact', contactPhone: 'phone',
        contactTel: 'landline', contactLandline: 'landline',
        contactTel2: 'landline2', contactLandline2: 'landline2',
        contactEmail: 'mail', tenderScope: 'description',
      },
    ]
    const sources = [data, extracted].filter(Boolean)
    for (const src of sources) {
      for (const mapping of mappings) {
        for (const [from, to] of Object.entries(mapping)) {
          const value = src[from]
          if (value === undefined || value === null || value === '') continue
          if (form.value[to] === undefined || form.value[to] === null || form.value[to] === '') {
            form.value[to] = value
          }
        }
      }
    }
  }

  function applyAttachmentFileUrl(file, parseResult) {
    const fileUrl = parseResult?.documentId || ''
    if (!fileUrl) return
    // 把 AI 解析返回的 documentId 写入 attachments 中对应文件的 fileUrl
    const targetName = file?.name || ''
    const attachments = form.value.attachments || []
    const idx = attachments.findIndex(f => {
      const fFile = resolveUploadFile(f)
      return fFile?.name === targetName || f?.name === targetName
    })
    if (idx >= 0) {
      attachments[idx] = {
        ...attachments[idx],
        name: attachments[idx].name || targetName,
        type: attachments[idx].type || file?.type || '',
        url: fileUrl,
        fileUrl,
        fileName: attachments[idx].fileName || targetName,
        fileType: attachments[idx].fileType || file?.type || '',
      }
    }
  }

  return {
    parsingDocument, handleFileChange, handlePastedTextParse,
    ACCEPT_FILE_TYPES,
  }
}
