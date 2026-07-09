// Input: tenders API (store/parse-existing/parse), manual form validation ref, and refresh callback
// Output: manual tender dialog state, document backfill (store-then-parse flow), and create action
// Pos: src/views/Bidding/list/ - Manual tender creation composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useTenderObsUpload } from './composables/useTenderObsUpload.js'
import { MAX_FILE_SIZE_BYTES, MAX_FILE_SIZE_TEXT } from '@/composables/useObsUploadFallback.js'
import { createManualTenderForm } from './constants.js'
import { buildManualTenderPayload, normalizeManualTenderParseResult } from './helpers.js'

const PASTED_TEXT_MAX_LENGTH = 500000

const SUPPORTED_PARSE_EXTENSIONS = new Set(['.doc', '.docx', '.pdf'])

function resolveUploadFile(file) {
  if (file instanceof File || file instanceof Blob) return file
  if (file?.raw instanceof File || file?.raw instanceof Blob) return file.raw
  return null
}

function isSupportedParseFile(file) {
  const name = String(file?.name || '').toLowerCase()
  return [...SUPPORTED_PARSE_EXTENSIONS].some((extension) => name.endsWith(extension))
}

function validateFileSize(file) {
  const uploadFile = resolveUploadFile(file)
  if (!uploadFile) return { valid: false, message: '无法读取文件' }
  if (uploadFile.size > MAX_FILE_SIZE_BYTES) {
    return { valid: false, message: `文件 "${uploadFile.name}" 超过 ${MAX_FILE_SIZE_TEXT} 限制` }
  }
  return { valid: true }
}

function validateFileType(file) {
  const uploadFile = resolveUploadFile(file)
  if (!uploadFile) return { valid: false, message: '无法读取文件' }
  if (!isSupportedParseFile(uploadFile)) {
    return { valid: false, message: `文件 "${uploadFile.name}" 格式不支持，仅支持 PDF/Word 文件` }
  }
  return { valid: true }
}

function applyParsedFields(form, parsedFields) {
  for (const [key, value] of Object.entries(parsedFields)) {
    if (Array.isArray(value)) {
      if (value.length > 0) form[key] = value
      continue
    }
    if (value !== '' && value !== null && value !== undefined) {
      form[key] = value
    }
  }
}

/** 将文件元数据（fileUrl/fileType）回写到 attachments 数组中对应文件 */
function applySourceDocumentMetadata(form, file, result = {}) {
  const fileUrl = result?.fileUrl || result?.documentId || result?.document?.fileUrl || ''
  const fileType = file?.type || result?.contentType || ''
  // 将 URL 和 fileType 回写到 attachments 数组中对应文件
  if (Array.isArray(form.attachments)) {
    const target = form.attachments.find(f => (f.uid && f.uid === file?.uid) || f.name === file?.name)
    if (target) {
      if (fileType) target.fileType = fileType
      if (fileUrl) {
        target.url = fileUrl
        target.fileUrl = fileUrl
      }
    }
  }
}

function hasGlobalHttpErrorMessage(error) {
  return Boolean(error?.isAxiosError || error?.response || error?.code === 'ECONNABORTED')
}

async function parseAndBackfill({ form, source, warningMessage }) {
  const response = await source.parse()
  if (!response?.success) {
    throw new Error(response?.msg || warningMessage)
  }
  applyParsedFields(form, normalizeManualTenderParseResult(response.data))
  applySourceDocumentMetadata(form, source.file, response.data)
}

export function useManualTenderCreate({ tendersApi, refreshTenderList, canCreateTender }) {
  const showManualAdd = ref(false)
  const manualFormRef = ref(null)
  const uploadRef = ref(null)
  const savingManual = ref(false)
  const parsingManualDocument = ref(false)
  const manualForm = ref(createManualTenderForm())
  const { obsUpload, tryUpload: tryObsUpload } = useTenderObsUpload('manual-tender', '标讯文件已上传至 OBS')

  // Guard: ensure pastedText never exceeds the maxlength regardless of browser/element-plus quirks
  watch(
    () => manualForm.value.pastedText,
    (value) => {
      if (value && value.length > PASTED_TEXT_MAX_LENGTH) {
        manualForm.value.pastedText = value.substring(0, PASTED_TEXT_MAX_LENGTH)
      }
    }
  )

  const resetManualForm = () => {
    manualForm.value = createManualTenderForm()
  }

  const handleFileRemove = (file, fileList) => { manualForm.value.attachments = fileList }

  const handleFileChange = async (file, fileList) => {
    manualForm.value.attachments = fileList
    const uploadFile = resolveUploadFile(file)
    const fileIndex = fileList.findIndex(f => (f.uid && f.uid === file?.uid) || f.name === file?.name)

    // 文件大小验证
    const sizeValidation = validateFileSize(uploadFile)
    if (!sizeValidation.valid) {
      ElMessage.warning(sizeValidation.message)
      // 移除超大的文件
      const filteredList = fileList.filter((f) => {
        const fFile = resolveUploadFile(f)
        return fFile && fFile.size <= MAX_FILE_SIZE_BYTES
      })
      manualForm.value.attachments = filteredList
      return
    }

    // 文件类型验证
    const typeValidation = validateFileType(uploadFile)
    if (!typeValidation.valid) {
      ElMessage.warning(typeValidation.message)
      // 移除不支持的文件
      const filteredList = fileList.filter((f) => {
        return isSupportedParseFile(resolveUploadFile(f))
      })
      manualForm.value.attachments = filteredList
      return
    }

    if (!uploadFile || !isSupportedParseFile(uploadFile)) return

    // OBS 直传：成功后跳过 store/parse（后端不支持 fileUrl，继续上传会 413）
    parsingManualDocument.value = true
    const obsUsed = await tryObsUpload(uploadFile, manualForm.value.attachments, fileIndex)
    if (obsUsed) { parsingManualDocument.value = false; return }
    // Step 1: 上传即保存（即使后续 AI 解析失败，文件也已保存）
    let storedDoc = null
    try {
      const storeResponse = await tendersApi.storeTenderDocument(uploadFile, { entityId: 'manual-tender' })
      if (storeResponse?.success && storeResponse.data) {
        storedDoc = storeResponse.data
        // 直接按索引回写 fileType 和 fileUrl，避免匹配失败
        if (fileIndex >= 0 && manualForm.value.attachments[fileIndex]) {
          const att = manualForm.value.attachments[fileIndex]
          if (uploadFile.type) att.fileType = uploadFile.type
          if (storedDoc.fileUrl) {
            att.url = storedDoc.fileUrl
            att.fileUrl = storedDoc.fileUrl
          }
        }
        applySourceDocumentMetadata(manualForm.value, uploadFile, storedDoc)
        ElMessage.success('标讯文件已上传保存')
      } else {
        console.warn('文件存储返回异常:', storeResponse?.msg)
      }
    } catch (storeError) {
      console.warn('文件存储失败，继续尝试 AI 解析:', storeError?.message || storeError)
    }

    // Step 2: AI 解析（独立增强，失败不影响文件保存；优先 parseExisting 避免重复上传）
    try {
      const parseSource = storedDoc?.storagePath
        ? {
            file: uploadFile,
            parse: () => tendersApi.parseExistingTenderDocument({
              storagePath: storedDoc.storagePath,
              fileName: uploadFile.name,
              contentType: uploadFile.type,
              entityId: 'manual-tender',
            }),
          }
        : {
            file: uploadFile,
            parse: () => tendersApi.parseTenderIntakeDocument(uploadFile, { entityId: 'manual-tender' }),
          }
      await parseAndBackfill({
        form: manualForm.value,
        source: parseSource,
        warningMessage: '文档自动识别失败',
      })
      ElMessage.success('DeepSeek/AI 已识别标讯文件内容，可继续编辑后保存')
    } catch (error) {
      const timedOut = error?.code === 'ECONNABORTED'
      ElMessage.warning(timedOut ? 'AI 解析超时，可继续手动填写' : '自动识别失败，可继续手动填写')
    } finally {
      parsingManualDocument.value = false
    }
  }

  const handlePastedTextParse = async () => {
    const text = manualForm.value.pastedText?.trim()
    if (!text) {
      ElMessage.warning('请先粘贴标讯正文')
      return false
    }

    parsingManualDocument.value = true
    try {
      await parseAndBackfill({
        form: manualForm.value,
        source: {
          file: { name: '粘贴标讯文本.txt', type: 'text/plain' },
          parse: () => tendersApi.parseTenderIntakeText(text, { entityId: 'manual-tender' }),
        },
        warningMessage: '粘贴文本识别失败',
      })
      ElMessage.success('DeepSeek/AI 已识别粘贴文本，可继续编辑后保存')
      return true
    } catch (error) {
      const timedOut = error?.code === 'ECONNABORTED'
      ElMessage.warning(timedOut ? 'AI 解析超时，可继续手动填写' : '粘贴文本识别失败，可继续手动填写')
      return false
    } finally {
      parsingManualDocument.value = false
    }
  }

  const saveManualTender = async () => {
    if (!canCreateTender.value) {
      ElMessage.error('当前账号无权人工录入标讯')
      return false
    }

    try {
      await manualFormRef.value?.validate()
      savingManual.value = true
      const payload = buildManualTenderPayload(manualForm.value)
      const response = await tendersApi.create(payload)
      if (!response?.success) {
        throw new Error(response?.msg || '标讯入库失败')
      }

      // 如有评估表单数据，创建标讯后同步保存评估表草稿
      const evaluation = payload.evaluation
      if (evaluation && response.data?.id) {
        const evalHasData = evaluation.projectBackground || evaluation.competitorAnalysis ||
          evaluation.contractPeriodStart || evaluation.contractPeriodEnd ||
          evaluation.shortlistedCount != null || evaluation.platformServiceFee != null
        if (evalHasData) {
          try {
            await tendersApi.saveEvaluationDraft(response.data.id, evaluation)
          } catch (evalErr) {
            // 评估保存失败不影响标讯创建成功，但需提示用户
            console.warn('评估表草稿保存失败:', evalErr?.message || evalErr)
            ElMessage.warning('标讯已入库，但评估表草稿保存失败，可稍后在标讯详情页补充评估信息')
          }
        }
      }

      ElMessage.success('标讯已成功入库')
      showManualAdd.value = false
      resetManualForm()
      await refreshTenderList()
      return true
    } catch (error) {
      if (error && !hasGlobalHttpErrorMessage(error)) {
        ElMessage.error(error.message || '标讯入库失败')
      }
      return false
    } finally {
      savingManual.value = false
    }
  }

  return {
    showManualAdd,
    manualFormRef,
    uploadRef,
    manualForm,
    savingManual,
    parsingManualDocument,
    obsUpload,
    resetManualForm,
    handleFileChange,
    handleFileRemove,
    handlePastedTextParse,
    saveManualTender,
  }
}
