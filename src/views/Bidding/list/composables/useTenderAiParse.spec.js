import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useTenderAiParse } from './useTenderAiParse.js'

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
}))

vi.mock('@/api/modules/tenders.js', () => ({
  tendersApi: {
    storeTenderDocument: vi.fn(),
    parseExistingTenderDocument: vi.fn(),
    parseTenderIntakeDocument: vi.fn(),
    parseTenderIntakeText: vi.fn(),
  },
}))

import { tendersApi } from '@/api/modules/tenders.js'

// 模块级 helper：构造上传文件对象
function makeUploadFile(name, type = 'application/pdf', uid) {
  const raw = new File(['content'], name, { type })
  return { name, raw, uid, type }
}

describe('useTenderAiParse', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  function mockParseResponse(documentId, overrides = {}) {
    return {
      success: true,
      data: {
        documentId,
        extractedData: {},
        requirements: [],
        rawMarkdown: '',
        ...overrides,
      },
    }
  }

  function mockStoreResponse(fileUrl = 'doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf', overrides = {}) {
    return {
      success: true,
      data: {
        fileUrl,
        storagePath: 'TENDER_INTAKE/create-tender/hash-tender.pdf',
        contentType: 'application/pdf',
        ...overrides,
      },
    }
  }

  it('stores the attachment first and backfills url/fileUrl before AI parse enhancement', async () => {
    const form = ref({ attachments: [], pastedText: '' })
    const { handleFileChange } = useTenderAiParse(form)
    const file = makeUploadFile('tender.pdf', 'application/pdf', 1)

    tendersApi.storeTenderDocument.mockResolvedValue(
      mockStoreResponse('doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf')
    )
    tendersApi.parseExistingTenderDocument.mockResolvedValue(
      mockParseResponse('doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf')
    )

    await handleFileChange(file, [file])

    expect(tendersApi.storeTenderDocument).toHaveBeenCalledWith(file.raw, { entityId: 'create-tender' })
    expect(tendersApi.parseExistingTenderDocument).toHaveBeenCalledWith({
      storagePath: 'TENDER_INTAKE/create-tender/hash-tender.pdf',
      fileName: 'tender.pdf',
      contentType: 'application/pdf',
      entityId: 'create-tender',
    })
    expect(tendersApi.parseTenderIntakeDocument).not.toHaveBeenCalled()
    expect(form.value.attachments[0]).toMatchObject({
      name: 'tender.pdf',
      type: 'application/pdf',
      fileName: 'tender.pdf',
      fileType: 'application/pdf',
      url: 'doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf',
      fileUrl: 'doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf',
    })
  })

  it('keeps stored url/fileUrl when parse-existing fails', async () => {
    const form = ref({ attachments: [], pastedText: '' })
    const { handleFileChange } = useTenderAiParse(form)
    const file = makeUploadFile('tender.pdf', 'application/pdf', 1)

    tendersApi.storeTenderDocument.mockResolvedValue(
      mockStoreResponse('doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf')
    )
    tendersApi.parseExistingTenderDocument.mockRejectedValue(new Error('AI unavailable'))

    await handleFileChange(file, [file])

    expect(form.value.attachments[0]).toMatchObject({
      url: 'doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf',
      fileUrl: 'doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf',
    })
  })

  it('backfills attachment url/fileUrl from parsed documentId', async () => {
    const form = ref({ attachments: [], pastedText: '' })
    const { handleFileChange } = useTenderAiParse(form)
    const file = makeUploadFile('tender.pdf', 'application/pdf', 1)

    tendersApi.parseTenderIntakeDocument.mockResolvedValue(
      mockParseResponse('doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf')
    )

    await handleFileChange(file, [file])

    expect(form.value.attachments).toHaveLength(1)
    expect(form.value.attachments[0]).toMatchObject({
      name: 'tender.pdf',
      type: 'application/pdf',
      fileName: 'tender.pdf',
      fileType: 'application/pdf',
      url: 'doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf',
      fileUrl: 'doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf',
    })
  })

  it('backfills attachment url/fileUrl even when document is scanned', async () => {
    const form = ref({ attachments: [], pastedText: '' })
    const { handleFileChange } = useTenderAiParse(form)
    const file = makeUploadFile('scanned.pdf', 'application/pdf', 2)

    tendersApi.parseTenderIntakeDocument.mockResolvedValue(
      mockParseResponse('doc-insight://TENDER_INTAKE/create-tender/hash-scanned.pdf', {
        warnings: ['SCANNED_DOCUMENT: 该文件可能是扫描件'],
      })
    )

    await handleFileChange(file, [file])

    expect(form.value.attachments[0]).toMatchObject({
      url: 'doc-insight://TENDER_INTAKE/create-tender/hash-scanned.pdf',
      fileUrl: 'doc-insight://TENDER_INTAKE/create-tender/hash-scanned.pdf',
    })
  })

  it('updates the correct attachment by index when multiple files share the same name', async () => {
    const form = ref({ attachments: [], pastedText: '' })
    const { handleFileChange } = useTenderAiParse(form)

    const first = makeUploadFile('same-name.pdf', 'application/pdf', 10)
    const second = makeUploadFile('same-name.pdf', 'application/pdf', 20)
    const fileList = [first, second]

    tendersApi.parseTenderIntakeDocument.mockResolvedValue(
      mockParseResponse('doc-insight://TENDER_INTAKE/create-tender/hash-second.pdf')
    )

    await handleFileChange(second, fileList)

    expect(form.value.attachments).toHaveLength(2)
    expect(form.value.attachments[0].url).toBeUndefined()
    expect(form.value.attachments[0].fileUrl).toBeUndefined()
    expect(form.value.attachments[1].url).toBe('doc-insight://TENDER_INTAKE/create-tender/hash-second.pdf')
    expect(form.value.attachments[1].fileUrl).toBe('doc-insight://TENDER_INTAKE/create-tender/hash-second.pdf')
  })

  it('removes attachment from form before save via handleFileRemove', () => {
    const form = ref({ attachments: [], pastedText: '' })
    const { handleFileRemove } = useTenderAiParse(form)
    const file = makeUploadFile('tender.pdf', 'application/pdf', 1)

    handleFileRemove(file, [])

    expect(form.value.attachments).toEqual([])
  })
})

// ============================================================
// OBS 启用模式回归测试
// 重点验证：OBS 成功后 AI 解析仍执行；obs-direct: URL 不被 doc-insight:// 覆盖
// ============================================================
describe('useTenderAiParse (OBS 启用模式回归)', () => {
  beforeEach(() => {
    vi.resetAllMocks()
  })

  async function createParserWithObs({ obsSuccess = true } = {}) {
    vi.resetModules()
    vi.doMock('./useTenderObsUpload.js', () => {
      const tryUpload = async (uploadFile, attachments, fileIndex) => {
        if (!obsSuccess) return false
        if (fileIndex >= 0 && attachments[fileIndex]) {
          attachments[fileIndex].url = 'obs-direct:test-upload-id'
          attachments[fileIndex].fileUrl = 'obs-direct:test-upload-id'
          if (uploadFile.type) attachments[fileIndex].fileType = uploadFile.type
        }
        return true
      }
      return {
        useTenderObsUpload: () => ({ obsUpload: {}, tryUpload }),
        isObsEnabled: obsSuccess,
      }
    })
    vi.doMock('@/api/modules/tenders.js', () => ({
      tendersApi: {
        storeTenderDocument: vi.fn(),
        parseExistingTenderDocument: vi.fn(),
        parseTenderIntakeDocument: vi.fn(),
        parseTenderIntakeText: vi.fn(),
      },
    }))
    const { useTenderAiParse: fresh } = await import('./useTenderAiParse.js')
    const { tendersApi } = await import('@/api/modules/tenders.js')
    const form = ref({ attachments: [], pastedText: '' })
    const parser = fresh(form)
    return { parser, form, tendersApi }
  }

  it('OBS 成功后继续走 store→parse-existing，obs-direct: URL 不被覆盖', async () => {
    const { parser, form, tendersApi } = await createParserWithObs({ obsSuccess: true })
    const file = makeUploadFile('tender.pdf', 'application/pdf', 1)

    tendersApi.storeTenderDocument.mockResolvedValue({
      success: true,
      data: {
        fileUrl: 'doc-insight://TENDER_INTAKE/create-tender/hash.pdf',
        storagePath: 'TENDER_INTAKE/create-tender/hash.pdf',
      },
    })
    tendersApi.parseExistingTenderDocument.mockResolvedValue({
      success: true,
      data: {
        documentId: 'doc-insight://TENDER_INTAKE/create-tender/hash.pdf',
        extractedData: { tenderTitle: 'OBS 启用项目' },
      },
    })

    await parser.handleFileChange(file, [file])

    // AI 解析确实执行了
    expect(tendersApi.storeTenderDocument).toHaveBeenCalled()
    expect(tendersApi.parseExistingTenderDocument).toHaveBeenCalled()
    // 表单字段回填
    expect(form.value.title).toBe('OBS 启用项目')
    // 关键回归断言：obs-direct: URL 保留
    expect(form.value.attachments[0]).toMatchObject({
      url: 'obs-direct:test-upload-id',
      fileUrl: 'obs-direct:test-upload-id',
    })
  })

  it('OBS 成功 + store 失败 → 仍走 parse 一站式，obs-direct: URL 保留', async () => {
    const { parser, form, tendersApi } = await createParserWithObs({ obsSuccess: true })
    const file = makeUploadFile('tender.pdf', 'application/pdf', 1)

    tendersApi.storeTenderDocument.mockRejectedValue(new Error('store failed'))
    tendersApi.parseTenderIntakeDocument.mockResolvedValue({
      success: true,
      data: {
        documentId: 'doc-insight://TENDER_INTAKE/create-tender/hash.pdf',
        extractedData: { tenderTitle: 'Store 失败项目' },
      },
    })

    await parser.handleFileChange(file, [file])

    expect(tendersApi.parseTenderIntakeDocument).toHaveBeenCalled()
    expect(form.value.title).toBe('Store 失败项目')
    // obs-direct: URL 保留（applyAttachmentFileUrl 被 !obsUsed 守卫跳过）
    expect(form.value.attachments[0]).toMatchObject({
      url: 'obs-direct:test-upload-id',
      fileUrl: 'obs-direct:test-upload-id',
    })
  })

  it('OBS 成功 + AI 解析失败 → obs-direct: URL 保留，不崩溃', async () => {
    const { parser, form, tendersApi } = await createParserWithObs({ obsSuccess: true })
    const file = makeUploadFile('tender.pdf', 'application/pdf', 1)

    tendersApi.storeTenderDocument.mockResolvedValue({
      success: true,
      data: { fileUrl: 'doc-insight://store.pdf', storagePath: 'path/store.pdf' },
    })
    tendersApi.parseExistingTenderDocument.mockRejectedValue(new Error('AI 解析挂了'))

    await parser.handleFileChange(file, [file])

    // obs-direct: URL 保留
    expect(form.value.attachments[0]).toMatchObject({
      url: 'obs-direct:test-upload-id',
      fileUrl: 'obs-direct:test-upload-id',
    })
  })

  it('OBS 失败回退 multipart → store+parse 正常回填 doc-insight URL', async () => {
    const { parser, form, tendersApi } = await createParserWithObs({ obsSuccess: false })
    const file = makeUploadFile('tender.pdf', 'application/pdf', 1)

    tendersApi.storeTenderDocument.mockResolvedValue({
      success: true,
      data: {
        fileUrl: 'doc-insight://TENDER_INTAKE/create-tender/hash.pdf',
        storagePath: 'TENDER_INTAKE/create-tender/hash.pdf',
      },
    })
    tendersApi.parseExistingTenderDocument.mockResolvedValue({
      success: true,
      data: {
        documentId: 'doc-insight://TENDER_INTAKE/create-tender/hash.pdf',
        extractedData: { tenderTitle: '回退 multipart 项目' },
      },
    })

    await parser.handleFileChange(file, [file])

    expect(form.value.title).toBe('回退 multipart 项目')
    // OBS 失败时 store 的 URL 被回填
    expect(form.value.attachments[0]).toMatchObject({
      url: 'doc-insight://TENDER_INTAKE/create-tender/hash.pdf',
      fileUrl: 'doc-insight://TENDER_INTAKE/create-tender/hash.pdf',
    })
  })

  it('OBS 成功 + AI 返回扫描件警告 → obs-direct: URL 保留', async () => {
    const { parser, form, tendersApi } = await createParserWithObs({ obsSuccess: true })
    const file = makeUploadFile('scanned.pdf', 'application/pdf', 1)

    tendersApi.storeTenderDocument.mockResolvedValue({
      success: true,
      data: { fileUrl: 'doc-insight://store.pdf', storagePath: 'path/store.pdf' },
    })
    tendersApi.parseExistingTenderDocument.mockResolvedValue({
      success: true,
      data: {
        documentId: 'doc-insight://TENDER_INTAKE/create-tender/hash.pdf',
        warnings: ['SCANNED_DOCUMENT: 该文件可能是扫描件'],
      },
    })

    await parser.handleFileChange(file, [file])

    // 扫描件时不回填字段，但 URL 必须保留为 obs-direct:
    expect(form.value.attachments[0]).toMatchObject({
      url: 'obs-direct:test-upload-id',
      fileUrl: 'obs-direct:test-upload-id',
    })
  })
})
