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

  function handleFileRemove(file, fileList) { form.value.attachments = fileList }

  async function handleFileChange(file, fileList) {
    form.value.attachments = fileList
    const uploadFile = resolveUploadFile(file)
    const fileIndex = fileList.findIndex(f => {
      if (file?.uid && f?.uid) return f.uid === file.uid
      return f.name === file?.name
    })
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
      let storedDoc = null
      try {
        const storeResponse = await tendersApi.storeTenderDocument(uploadFile, { entityId: 'create-tender' })
        if (storeResponse?.success && storeResponse.data) {
          storedDoc = storeResponse.data
          applyAttachmentFileUrl(file, uploadFile, storedDoc, fileIndex)
        }
      } catch (storeError) {
        console.warn('标讯文件存储失败，回退到一站式解析:', storeError?.message || storeError)
      }

      const response = storedDoc?.storagePath
        ? await tendersApi.parseExistingTenderDocument({
            storagePath: storedDoc.storagePath,
            fileName: uploadFile.name,
            contentType: uploadFile.type,
            entityId: 'create-tender',
          })
        : await tendersApi.parseTenderIntakeDocument(uploadFile, { entityId: 'create-tender' })
      if (!response?.success) throw new Error(response?.msg || '文档自动识别失败')

      // AI 分析是增强功能 —— 文件存储成功是底线，AI 失败不应阻塞标讯创建
      const warnings = response?.data?.warnings || []
      const hasAiFailure = warnings.some(w => String(w).startsWith('AI_DOCUMENT_ANALYSIS_FAILED'))
      const hasScannedDoc = warnings.some(w => String(w).startsWith('SCANNED_DOCUMENT'))

      // 一站式解析回退时，仍需从解析结果回填 fileUrl。
      applyAttachmentFileUrl(file, uploadFile, response.data, fileIndex)

      if (hasScannedDoc) {
        return '该文件可能是扫描件，无法提取文字。文件已保存，请手动填写标讯信息。'
      }
      if (hasAiFailure) {
        return '文件已成功保存（可下载），但 AI 文档分析服务暂不可用，请手动填写标讯信息。'
      }

      // AI 成功：正常填充字段
      applyParsedFields(response.data)
      return 'DeepSeek/AI 已识别附件内容，可继续编辑后保存'
    })
  }

  async function handlePastedTextParse() {
    const text = form.value.pastedText?.trim()
    if (!text) { ElMessage.warning('请先粘贴标讯正文'); return }
    await runAiParse(async () => {
      const response = await tendersApi.parseTenderIntakeText(text, { entityId: 'create-tender' })
      if (!response?.success) throw new Error(response?.msg || '粘贴文本识别失败')

      const warnings = response?.data?.warnings || []
      const hasAiFailure = warnings.some(w => String(w).startsWith('AI_DOCUMENT_ANALYSIS_FAILED'))

      if (hasAiFailure) {
        return '文本已保存，但 AI 分析服务暂不可用，请手动填写标讯信息。'
      }

      applyParsedFields(response.data)
      return 'DeepSeek/AI 已识别粘贴文本，可继续编辑后保存'
    })
  }

  async function runAiParse(parseFn) {
    parsingDocument.value = true
    try {
      const msg = await parseFn()
      if (msg) {
        // AI 分析失败但文件保存成功时提示 warning，成功时提示 success
        if (msg.includes('暂不可用') || msg.includes('无法提取')) {
          ElMessage.warning(msg)
        } else {
          ElMessage.success(msg)
        }
      }
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

  function applyAttachmentFileUrl(file, uploadFile, parseResult, fileIndex) {
    const fileUrl = parseResult?.fileUrl || parseResult?.documentId || parseResult?.document?.fileUrl || ''
    if (!fileUrl) return
    // 用上传时记录的下标直接定位附件，避免同名文件覆盖错对象；与 useManualTenderCreate.js 保持一致。
    const attachments = form.value.attachments || []
    const target = fileIndex >= 0 && attachments[fileIndex] ? attachments[fileIndex] : null
    if (!target) return
    const targetName = file?.name || uploadFile?.name || ''
    const targetType = uploadFile?.type || file?.type || ''
    target.name = target.name || targetName
    target.type = target.type || targetType
    target.fileName = target.fileName || targetName
    target.fileType = target.fileType || targetType
    target.url = fileUrl
    target.fileUrl = fileUrl
  }

  return {
    parsingDocument, handleFileChange, handleFileRemove, handlePastedTextParse,
    ACCEPT_FILE_TYPES,
  }
}
