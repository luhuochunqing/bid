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

import { ElMessage } from 'element-plus'
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

  // 生产案例 2026-08-28：store 成功后 el-upload 仍显示"未上传"（status 恒为 ready）
  it('marks attachment status as success after store so el-upload shows the green check', async () => {
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

    expect(form.value.attachments[0].status).toBe('success')
  })

  // 生产案例 2026-08-28：公开谈判采购无开标时间 → AI 留空 → 用户不知道还缺什么
  it('warns with missing required fields after AI parse when form still incomplete', async () => {
    const form = ref({ attachments: [], pastedText: '' })
    const { handleFileChange } = useTenderAiParse(form)
    const file = makeUploadFile('tender.pdf', 'application/pdf', 1)

    tendersApi.parseTenderIntakeDocument.mockResolvedValue(
      mockParseResponse('doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf', {
        extractedData: { tenderTitle: '国家电网办公类物资框架协议采购' },
      })
    )

    await handleFileChange(file, [file])

    expect(form.value.title).toBe('国家电网办公类物资框架协议采购')
    expect(ElMessage.warning).toHaveBeenCalledWith(expect.stringContaining('请补齐后再保存'))
    expect(ElMessage.warning).toHaveBeenCalledWith(expect.stringContaining('开标时间'))
    expect(ElMessage.success).not.toHaveBeenCalled()
  })

  it('warns with missing required fields after pasted-text parse', async () => {
    const form = ref({ attachments: [], pastedText: '国家电网公开谈判采购正文' })
    const { handlePastedTextParse } = useTenderAiParse(form)

    tendersApi.parseTenderIntakeText.mockResolvedValue(
      mockParseResponse('doc-insight://TENDER_INTAKE/create-tender/hash-text', {
        extractedData: { tenderTitle: '国家电网办公类物资框架协议采购' },
      })
    )

    await handlePastedTextParse()

    expect(form.value.title).toBe('国家电网办公类物资框架协议采购')
    expect(ElMessage.warning).toHaveBeenCalledWith(expect.stringContaining('请补齐后再保存'))
    expect(ElMessage.success).not.toHaveBeenCalled()
  })

  it('keeps the success message when AI parse backfills all required fields', async () => {
    const form = ref({ attachments: [], pastedText: '' })
    const { handleFileChange } = useTenderAiParse(form)
    const file = makeUploadFile('tender.pdf', 'application/pdf', 1)

    tendersApi.parseTenderIntakeDocument.mockResolvedValue(
      mockParseResponse('doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf', {
        extractedData: {
          tenderTitle: '国家电网办公类物资框架协议采购',
          purchaserName: '国家电网有限公司',
          region: '北京市',
          deadline: '2026-09-01T17:00:00',
          bidOpeningTime: '2026-09-03T10:00:00',
          customerType: '央企集团',
          priority: 'A',
        },
      })
    )

    await handleFileChange(file, [file])

    expect(ElMessage.success).toHaveBeenCalledWith('DeepSeek/AI 已识别附件内容，可继续编辑后保存')
    expect(ElMessage.warning).not.toHaveBeenCalled()
  })

  it('removes attachment from form before save via handleFileRemove', () => {
    const form = ref({ attachments: [], pastedText: '' })
    const { handleFileRemove } = useTenderAiParse(form)
    const file = makeUploadFile('tender.pdf', 'application/pdf', 1)

    handleFileRemove(file, [])

    expect(form.value.attachments).toEqual([])
  })

  // purchaserName 映射回归测试（曾因 useTenderAiParse.applyParsedFields mapping 缺少
  // purchaserName: 'purchaser'，导致后端正则兜底修复 purchaserName 后前端表单仍显示空）。
  it('maps purchaserName to form.purchaser when tenderAgency is absent', async () => {
    const form = ref({ attachments: [], pastedText: '', purchaser: '' })
    const { handleFileChange } = useTenderAiParse(form)
    const file = makeUploadFile('tender.pdf', 'application/pdf', 1)

    tendersApi.parseTenderIntakeDocument.mockResolvedValue(
      mockParseResponse('doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf', {
        extractedData: {
          tenderTitle: '张家口银行办公用品采购项目',
          purchaserName: '张家口银行股份有限公司',
          region: '河北省张家口市',
        },
      })
    )

    await handleFileChange(file, [file])

    expect(form.value.purchaser).toBe('张家口银行股份有限公司')
    expect(form.value.title).toBe('张家口银行办公用品采购项目')
    expect(form.value.region).toBe('河北省张家口市')
  })

  it('purchaserName overrides tenderAgency when both are present (招标主体优先于代理机构)', async () => {
    const form = ref({ attachments: [], pastedText: '', purchaser: '' })
    const { handleFileChange } = useTenderAiParse(form)
    const file = makeUploadFile('tender.pdf', 'application/pdf', 1)

    tendersApi.parseTenderIntakeDocument.mockResolvedValue(
      mockParseResponse('doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf', {
        extractedData: {
          purchaserName: '张家口银行股份有限公司',
          tenderAgency: '祥安招标代理有限公司',
        },
      })
    )

    await handleFileChange(file, [file])

    expect(form.value.purchaser).toBe('张家口银行股份有限公司')
  })

  it('falls back to tenderAgency when purchaserName is absent (兼容历史 AI 输出)', async () => {
    const form = ref({ attachments: [], pastedText: '', purchaser: '' })
    const { handleFileChange } = useTenderAiParse(form)
    const file = makeUploadFile('tender.pdf', 'application/pdf', 1)

    tendersApi.parseTenderIntakeDocument.mockResolvedValue(
      mockParseResponse('doc-insight://TENDER_INTAKE/create-tender/hash-tender.pdf', {
        extractedData: {
          tenderAgency: '上海招标代理有限公司',
        },
      })
    )

    await handleFileChange(file, [file])

    expect(form.value.purchaser).toBe('上海招标代理有限公司')
  })
})
