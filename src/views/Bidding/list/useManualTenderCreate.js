// Input: tenders API (store/parse-existing/parse), manual form validation ref, and refresh callback
// Output: manual tender dialog state, document backfill (store-then-parse flow), and create action
// Pos: src/views/Bidding/list/ - Manual tender creation composable
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createManualTenderForm } from './constants.js'
import { buildManualTenderPayload, normalizeManualTenderParseResult } from './helpers.js'

const PASTED_TEXT_MAX_LENGTH = 500000

const SUPPORTED_PARSE_EXTENSIONS = new Set(['.doc', '.docx', '.pdf'])
const MAX_FILE_SIZE = 50 * 1024 * 1024 // 50MB

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
  if (uploadFile.size > MAX_FILE_SIZE) {
    return { valid: false, message: `文件 "${uploadFile.name}" 超过 50MB 限制` }
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

/**
 * 将文件元数据（sourceDocumentName/FileType/FileUrl）写入表单。
 * @param {Object} form - 响应式表单对象
 * @param {File} file - 浏览器 File 对象（用于 .name / .type）
 * @param {Object} result - 后端返回结果，可能是 StoredDocument（{fileUrl, storagePath}）
 *                          或 DocumentAnalysisResult（{documentId, document: {fileUrl}}）
 */
function applySourceDocumentMetadata(form, file, result = {}) {
  const fileUrl = result?.fileUrl || result?.documentId || result?.document?.fileUrl || ''
  if (!fileUrl) return
  form.sourceDocumentName = file?.name || result?.documentName || '招标文件'
  form.sourceDocumentFileType = file?.type || result?.contentType || ''
  form.sourceDocumentFileUrl = fileUrl
  // 将 URL 回写到 attachments 数组中对应文件
  if (Array.isArray(form.attachments)) {
    const target = form.attachments.find(f => (f.uid && f.uid === file?.uid) || f.name === file?.name)
    if (target) {
      target.url = fileUrl
      target.fileUrl = fileUrl
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
  // "上传即保存"流程中，Step 1 (/store) 已通过 applySourceDocumentMetadata 设置了
  // sourceDocumentFileUrl；此处仅在旧流程（/parse 一站式）中回填元数据
  if (!form.sourceDocumentFileUrl) {
    applySourceDocumentMetadata(form, source.file, response.data)
  }
}

export function useManualTenderCreate({ tendersApi, refreshTenderList, canCreateTender }) {
  const showManualAdd = ref(false)
  const manualFormRef = ref(null)
  const uploadRef = ref(null)
  const savingManual = ref(false)
  const parsingManualDocument = ref(false)
  const manualForm = ref(createManualTenderForm())

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

  const handleFileChange = async (file, fileList) => {
    manualForm.value.attachments = fileList
    const uploadFile = resolveUploadFile(file)

    // 文件大小验证
    const sizeValidation = validateFileSize(uploadFile)
    if (!sizeValidation.valid) {
      ElMessage.warning(sizeValidation.message)
      // 移除超大的文件
      const filteredList = fileList.filter((f) => {
        const fFile = resolveUploadFile(f)
        return fFile && fFile.size <= MAX_FILE_SIZE
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

    // ── Step 1: 上传即保存 ──────────────────────────────────────────────────────
    // 文件选择后立即存储到后端，获取 fileUrl / storagePath。
    // 即使后续 AI 解析失败，文件也已保存，用户保存标讯时文件元数据不会丢失。
    let storedDoc = null
    try {
      const storeResponse = await tendersApi.storeTenderDocument(uploadFile, { entityId: 'manual-tender' })
      if (storeResponse?.success && storeResponse.data) {
        storedDoc = storeResponse.data
        applySourceDocumentMetadata(manualForm.value, uploadFile, storedDoc)
        ElMessage.success('标讯文件已上传保存')
      } else {
        console.warn('文件存储返回异常:', storeResponse?.msg)
      }
    } catch (storeError) {
      console.warn('文件存储失败，继续尝试 AI 解析:', storeError?.message || storeError)
    }

    // ── Step 2: AI 解析（独立增强步骤，失败不影响文件保存）─────────────────────────
    // 优先使用 parseExisting（基于 storagePath，避免重复上传），
    // 仅在 Step 1 存储失败时回退到 /parse 一站式端点。
    parsingManualDocument.value = true
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
    resetManualForm,
    handleFileChange,
    handlePastedTextParse,
    saveManualTender,
  }
}
