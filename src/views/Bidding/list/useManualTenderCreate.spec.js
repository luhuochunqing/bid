import { nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useManualTenderCreate } from './useManualTenderCreate.js'

vi.mock('element-plus', () => ({
  ElMessage: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
}))

import { ElMessage } from 'element-plus'

function createWorkflow(overrides = {}) {
  const tendersApi = {
    create: vi.fn(),
    parseTenderIntakeDocument: vi.fn(),
    parseTenderIntakeText: vi.fn(),
  }
  const workflow = useManualTenderCreate({
    tendersApi,
    refreshTenderList: vi.fn(),
    canCreateTender: ref(true),
    ...overrides,
  })
  return { workflow, tendersApi }
}

describe('useManualTenderCreate', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('backfills editable manual form fields after a supported attachment is parsed', async () => {
    const { workflow, tendersApi } = createWorkflow()
    const file = new File(['tender'], '招标文件.pdf', { type: 'application/pdf' })
    tendersApi.parseTenderIntakeDocument.mockResolvedValue({
      success: true,
      data: {
        documentId: 'doc-insight://TENDER_INTAKE/manual-tender/hash-招标文件.pdf',
        extractedData: {
          tenderTitle: '西域智能仓储采购项目',
          region: '上海',
          tenderAgency: '上海招标代理有限公司',
          deadline: '2026-06-01T17:00:00',
          bidOpeningTime: '2026-06-03T10:00:00',
          contactName: '李经理',
          contactPhone: '13800138000',
          contactEmail: 'li@example.com',
          customerType: '央企集团',
          priority: 'S',
          tenderScope: '仓储自动化设备采购',
          tags: ['公开招标', '智能仓储'],
        },
      },
    })

    await workflow.handleFileChange({ name: file.name, raw: file }, [{ name: file.name, raw: file }])

    expect(tendersApi.parseTenderIntakeDocument).toHaveBeenCalledWith(file, { entityId: 'manual-tender' })
    expect(workflow.manualForm.value).toMatchObject({
      title: '西域智能仓储采购项目',
      region: '上海',
      purchaser: '上海招标代理有限公司',
      contact: '李经理',
      phone: '13800138000',
      mail: 'li@example.com',
      customerType: '央企集团',
      priority: 'S',
      tenderInfo: '仓储自动化设备采购',
      tags: ['公开招标', '智能仓储'],
    })
    expect(workflow.manualForm.value.attachments[0]).toMatchObject({
      name: '招标文件.pdf',
      fileType: 'application/pdf',
      url: 'doc-insight://TENDER_INTAKE/manual-tender/hash-招标文件.pdf',
      fileUrl: 'doc-insight://TENDER_INTAKE/manual-tender/hash-招标文件.pdf',
    })
    expect(workflow.manualForm.value.deadline).toEqual(new Date('2026-06-01T17:00:00'))
    expect(workflow.manualForm.value.bidOpeningTime).toEqual(new Date('2026-06-03T10:00:00'))
    expect(ElMessage.success).toHaveBeenCalledWith('DeepSeek/AI 已识别标讯文件内容，可继续编辑后保存')
  })

  it('backfills fields from pasted tender text recognition', async () => {
    const { workflow, tendersApi } = createWorkflow()
    workflow.manualForm.value.pastedText = '项目名称：西域MRO项目\n开标时间：2026-06-03 10:00'
    tendersApi.parseTenderIntakeText.mockResolvedValue({
      success: true,
      data: {
        documentId: 'doc-insight://TENDER_INTAKE/manual-tender/hash-粘贴标讯文本.txt',
        extractedData: {
          tenderTitle: '西域MRO项目',
          bidOpeningTime: '2026-06-03T10:00:00',
          contactName: '王经理',
          contactPhone: '13900139000',
          contactEmail: 'wang@example.com',
          customerType: 'KA 客户',
          priority: 'A',
        },
      },
    })

    await expect(workflow.handlePastedTextParse()).resolves.toBe(true)

    expect(tendersApi.parseTenderIntakeText).toHaveBeenCalledWith(
      '项目名称：西域MRO项目\n开标时间：2026-06-03 10:00',
      { entityId: 'manual-tender' },
    )
    expect(workflow.manualForm.value).toMatchObject({
      title: '西域MRO项目',
      contact: '王经理',
      phone: '13900139000',
      mail: 'wang@example.com',
      customerType: 'KA 客户',
      priority: 'A',
    })
    expect(workflow.manualForm.value.bidOpeningTime).toEqual(new Date('2026-06-03T10:00:00'))
    expect(ElMessage.success).toHaveBeenCalledWith('DeepSeek/AI 已识别粘贴文本，可继续编辑后保存')
  })

  it('keeps existing manual values when document parsing fails', async () => {
    const { workflow, tendersApi } = createWorkflow()
    workflow.manualForm.value.title = '手动输入标题'
    workflow.manualForm.value.region = '北京'
    const file = new File(['bad'], '坏文件.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })
    tendersApi.parseTenderIntakeDocument.mockRejectedValue(new Error('parse failed'))

    await workflow.handleFileChange({ name: file.name, raw: file }, [{ name: file.name, raw: file }])

    expect(workflow.manualForm.value.title).toBe('手动输入标题')
    expect(workflow.manualForm.value.region).toBe('北京')
    expect(ElMessage.warning).toHaveBeenCalledWith('自动识别失败，可继续手动填写')
  })

  it('clears parsing state and shows timeout hint when AI parsing times out', async () => {
    const { workflow, tendersApi } = createWorkflow()
    const file = new File(['slow'], '慢文件.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })
    tendersApi.parseTenderIntakeDocument.mockRejectedValue({ code: 'ECONNABORTED' })

    await workflow.handleFileChange({ name: file.name, raw: file }, [{ name: file.name, raw: file }])

    expect(workflow.parsingManualDocument.value).toBe(false)
    expect(ElMessage.warning).toHaveBeenCalledWith('AI 解析超时，可继续手动填写')
  })

  it('submits reviewed manual form values when framework agreement has no budget', async () => {
    const refreshTenderList = vi.fn()
    const { workflow, tendersApi } = createWorkflow({ refreshTenderList })
    tendersApi.create.mockResolvedValue({ success: true })
    workflow.manualFormRef.value = { validate: vi.fn().mockResolvedValue(true) }
    workflow.manualForm.value = {
      ...workflow.manualForm.value,
      title: '中国兵器装备集团有限公司电子商城电商供应商引入项目',
      region: '北京',
      deadline: '2026-10-14 00:00',
      bidOpeningTime: '2026-10-16 09:30',
      purchaser: '南方工业科技贸易有限公司',
      contact: '赵经理',
      phone: '010-88888888',
      mail: 'zhao@example.com',
      customerType: '央企集团',
      priority: 'A',
      description: '框架协议供应商引入，无明确采购预算。',
      attachments: [
        {
          name: '框架协议招标文件.docx',
          type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
          url: 'doc-insight://TENDER_INTAKE/manual-tender/hash-框架协议招标文件.docx',
          fileName: '框架协议招标文件.docx',
          fileType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
          fileUrl: 'doc-insight://TENDER_INTAKE/manual-tender/hash-框架协议招标文件.docx',
        },
      ],
      publishDate: expect.any(String),
    }

    await expect(workflow.saveManualTender()).resolves.toBe(true)

    expect(tendersApi.create).toHaveBeenCalledWith(
      expect.objectContaining({
        title: '中国兵器装备集团有限公司电子商城电商供应商引入项目',
        region: '北京',
        purchaserName: '南方工业科技贸易有限公司',
        deadline: '2026-10-14T23:59:59',
        bidOpeningTime: '2026-10-16T09:30:00',
        contactName: '赵经理',
        contactPhone: '010-88888888',
        contactTel: null,
        contactMail: 'zhao@example.com',
        contactName2: null,
        contactPhone2: null,
        contactTel2: null,
        contactMail2: null,
        customerType: '央企集团',
        priority: 'A',
        projectType: null,
        description: '框架协议供应商引入，无明确采购预算。',
        source: '人工录入',
        tenderInfo: null,
        publishDate: expect.any(String),
        status: 'PENDING_ASSIGNMENT',
      }),
    )
    const callArgs = tendersApi.create.mock.calls[0][0]
    expect(callArgs).not.toHaveProperty('sourceDocumentName')
    expect(callArgs).not.toHaveProperty('sourceDocumentFileType')
    expect(callArgs).not.toHaveProperty('sourceDocumentFileUrl')
    expect(callArgs.attachments).toHaveLength(1)
    expect(callArgs.attachments[0]).toMatchObject({
      fileName: '框架协议招标文件.docx',
      fileType: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
      fileUrl: 'doc-insight://TENDER_INTAKE/manual-tender/hash-框架协议招标文件.docx',
    })
    expect(refreshTenderList).toHaveBeenCalled()
  })

  it('does not duplicate backend validation messages already shown by the HTTP client', async () => {
    const { workflow, tendersApi } = createWorkflow()
    const httpValidationError = {
      isAxiosError: true,
      response: {
        status: 400,
        data: { msg: '参数校验失败: 截止日期必须是未来的时间' },
      },
      message: 'Request failed with status code 400',
    }
    tendersApi.create.mockRejectedValue(httpValidationError)
    workflow.manualFormRef.value = { validate: vi.fn().mockResolvedValue(true) }

    await expect(workflow.saveManualTender()).resolves.toBe(false)

    expect(ElMessage.error).not.toHaveBeenCalledWith('Request failed with status code 400')
  })

  it('keeps pasted text intact up to 500,000 characters and trims only when exceeding the cap', async () => {
    const { workflow } = createWorkflow()

    workflow.manualForm.value.pastedText = '中'.repeat(60_000)
    await nextTick()
    expect(workflow.manualForm.value.pastedText.length).toBe(60_000)

    workflow.manualForm.value.pastedText = '中'.repeat(500_000)
    await nextTick()
    expect(workflow.manualForm.value.pastedText.length).toBe(500_000)

    workflow.manualForm.value.pastedText = '中'.repeat(500_010)
    await nextTick()
    expect(workflow.manualForm.value.pastedText.length).toBe(500_000)
  })

  it('maps landline form fields to contactTel/contactTel2 in payload', async () => {
    const refreshTenderList = vi.fn()
    const { workflow, tendersApi } = createWorkflow({ refreshTenderList })
    tendersApi.create.mockResolvedValue({ success: true })
    workflow.manualFormRef.value = { validate: vi.fn().mockResolvedValue(true) }
    workflow.manualForm.value = {
      ...workflow.manualForm.value,
      title: '座机字段映射测试项目',
      region: '上海',
      deadline: '2026-11-01 00:00',
      bidOpeningTime: '2026-11-03 09:30',
      purchaser: '测试采购方',
      contact: '张经理',
      phone: '13800138000',
      landline: '010-12345678',
      mail: 'zhang@example.com',
      contact2: '李经理',
      phone2: '13900139000',
      landline2: '021-87654321',
      mail2: 'li@example.com',
      customerType: '央企集团',
      priority: 'A',
    }

    await expect(workflow.saveManualTender()).resolves.toBe(true)

    expect(tendersApi.create).toHaveBeenCalledWith(
      expect.objectContaining({
        contactTel: '010-12345678',
        contactTel2: '021-87654321',
      }),
    )
  })

  it('rejects files exceeding 50MB size limit', async () => {
    const { workflow, tendersApi } = createWorkflow()
    const largeContent = new Array(51 * 1024 * 1024).join('x') // 51MB
    const largeFile = new File([largeContent], '大文件.pdf', { type: 'application/pdf' })

    await workflow.handleFileChange({ name: largeFile.name, raw: largeFile }, [{ name: largeFile.name, raw: largeFile }])

    expect(ElMessage.warning).toHaveBeenCalledWith(expect.stringContaining('超过 50MB'))
    expect(tendersApi.parseTenderIntakeDocument).not.toHaveBeenCalled()
  })

  it('rejects unsupported file types with warning message', async () => {
    const { workflow, tendersApi } = createWorkflow()
    const excelFile = new File(['test'], '数据.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })

    await workflow.handleFileChange({ name: excelFile.name, raw: excelFile }, [
      { name: excelFile.name, raw: excelFile },
    ])

    expect(ElMessage.warning).toHaveBeenCalledWith(expect.stringContaining('格式不支持'))
    expect(tendersApi.parseTenderIntakeDocument).not.toHaveBeenCalled()
  })

  it('filters out oversized files from file list', async () => {
    const { workflow } = createWorkflow()
    const normalFile = new File(['test content'], '正常文件.pdf', { type: 'application/pdf' })
    const largeContent = new Array(51 * 1024 * 1024).join('x')
    const largeFile = new File([largeContent], '大文件.pdf', { type: 'application/pdf' })

    await workflow.handleFileChange({ name: largeFile.name, raw: largeFile }, [
      { name: normalFile.name, raw: normalFile },
      { name: largeFile.name, raw: largeFile },
    ])

    expect(workflow.manualForm.value.attachments).toHaveLength(1)
    expect(workflow.manualForm.value.attachments[0].name).toBe('正常文件.pdf')
  })

  it('filters out unsupported file types from file list', async () => {
    const { workflow } = createWorkflow()
    const pdfFile = new File(['pdf content'], '招标书.pdf', { type: 'application/pdf' })
    const excelFile = new File(['excel'], '数据.xlsx', {
      type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    })

    await workflow.handleFileChange({ name: excelFile.name, raw: excelFile }, [
      { name: pdfFile.name, raw: pdfFile },
      { name: excelFile.name, raw: excelFile },
    ])

    expect(workflow.manualForm.value.attachments).toHaveLength(1)
    expect(workflow.manualForm.value.attachments[0].name).toBe('招标书.pdf')
  })

  it('successfully parses .doc files', async () => {
    const { workflow, tendersApi } = createWorkflow()
    const docFile = new File(['word content'], '招标公告.doc', { type: 'application/msword' })
    tendersApi.parseTenderIntakeDocument.mockResolvedValue({
      success: true,
      data: {
        documentId: 'doc-insight://TENDER_INTAKE/manual-tender/hash-招标公告.doc',
        extractedData: {
          tenderTitle: 'Word文档项目',
        },
      },
    })

    await workflow.handleFileChange({ name: docFile.name, raw: docFile }, [{ name: docFile.name, raw: docFile }])

    expect(tendersApi.parseTenderIntakeDocument).toHaveBeenCalled()
    expect(workflow.manualForm.value.title).toBe('Word文档项目')
  })

  it('successfully parses .docx files', async () => {
    const { workflow, tendersApi } = createWorkflow()
    const docxFile = new File(['word content'], '招标公告.docx', {
      type: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    })
    tendersApi.parseTenderIntakeDocument.mockResolvedValue({
      success: true,
      data: {
        documentId: 'doc-insight://TENDER_INTAKE/manual-tender/hash-招标公告.docx',
        extractedData: {
          tenderTitle: 'Word文档项目',
        },
      },
    })

    await workflow.handleFileChange(
      { name: docxFile.name, raw: docxFile },
      [{ name: docxFile.name, raw: docxFile }],
    )

    expect(tendersApi.parseTenderIntakeDocument).toHaveBeenCalled()
    expect(workflow.manualForm.value.title).toBe('Word文档项目')
  })
})
