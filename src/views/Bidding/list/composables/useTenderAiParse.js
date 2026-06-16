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
      // Step 1: 上传即保存，获取 fileUrl / storagePath
      let storedDoc = null
      try {
        const storeResponse = await tendersApi.storeTenderDocument(uploadFile, { entityId: 'create-tender' })
        if (storeResponse?.success && storeResponse.data) {
          storedDoc = storeResponse.data
          applySourceDocumentMetadata(uploadFile, storedDoc)
        }
      } catch (storeErr) {
        console.warn('文件存储失败，继续尝试 AI 解析:', storeErr?.message || storeErr)
      }

      // Step 2: AI 解析（优先用 parseExisting 避免重复上传）
      const parseResponse = storedDoc?.storagePath
        ? await tendersApi.parseExistingTenderDocument({
            storagePath: storedDoc.storagePath,
            fileName: uploadFile.name,
            contentType: uploadFile.type,
            entityId: 'create-tender',
          })
        : await tendersApi.parseTenderIntakeDocument(uploadFile, { entityId: 'create-tender' })
      if (!parseResponse?.success) throw new Error(parseResponse?.msg || '文档自动识别失败')
      applyParsedFields(parseResponse.data)
      if (!form.value.sourceDocumentFileUrl) {
        applySourceDocumentMetadata(uploadFile, parseResponse.data)
      }
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

  function applySourceDocumentMetadata(file, result) {
    const fileUrl = result?.fileUrl || result?.documentId || result?.document?.fileUrl || ''
    if (!fileUrl) return
    form.value.sourceDocumentName = file?.name || result?.documentName || '招标文件'
    form.value.sourceDocumentFileType = file?.type || result?.contentType || ''
    form.value.sourceDocumentFileUrl = fileUrl
  }

  return {
    parsingDocument, handleFileChange, handlePastedTextParse,
    ACCEPT_FILE_TYPES,
  }
}
