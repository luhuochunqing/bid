// Input: useBrandAuthExport composable
// Output: 覆盖 filterSummary、exportFilename、handleExport、doExport、handleExportZipConfirm
// Pos: src/composables/ — Composable 单元测试（同目录约定）

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref, reactive } from 'vue'

// Mock element-plus
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElLoading: { service: vi.fn(() => ({ close: vi.fn() })) }
}))

// Mock http client
vi.mock('@/api/client', () => ({
  default: { get: vi.fn().mockResolvedValue({ data: new Blob() }) }
}))

// Mock brandAuthApi
vi.mock('@/api/modules/brandAuth.js', () => ({
  default: { exportZip: vi.fn().mockResolvedValue({ data: new Blob() }) }
}))

import { useBrandAuthExport } from './useBrandAuthExport.js'
import http from '@/api/client'
import brandAuthApi from '@/api/modules/brandAuth.js'
import { ElMessage, ElLoading } from 'element-plus'

describe('useBrandAuthExport', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    global.URL.createObjectURL = vi.fn(() => 'blob:mock')
    global.URL.revokeObjectURL = vi.fn()
    const fakeAnchor = { click: vi.fn(), remove: vi.fn(), href: '', download: '' }
    global.document = {
      ...global.document,
      createElement: vi.fn(() => fakeAnchor),
      body: { appendChild: vi.fn() }
    }
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  describe('filterSummary', () => {
    it('全部筛选为空时返回"全部"', () => {
      const filters = reactive({})
      const { filterSummary } = useBrandAuthExport(filters, ref('manufacturer'), ref(0))
      expect(filterSummary.value).toBe('全部')
    })

    it('拼接产线/品牌/状态等筛选条件', () => {
      const filters = reactive({
        productLines: ['工具', '刀具'],
        brandName: '品牌A',
        statuses: ['ACTIVE', 'EXPIRED']
      })
      const { filterSummary } = useBrandAuthExport(filters, ref('manufacturer'), ref(0))
      expect(filterSummary.value).toContain('产线:工具,刀具')
      expect(filterSummary.value).toContain('品牌:品牌A')
      expect(filterSummary.value).toContain('状态:ACTIVE,EXPIRED')
    })
  })

  describe('exportFilename', () => {
    it('manufacturer tab 生成原厂授权清单文件名', () => {
      const filters = reactive({})
      const { exportFilename } = useBrandAuthExport(filters, ref('manufacturer'), ref(0))
      expect(exportFilename.value).toMatch(/^原厂授权清单_\d{4}-\d{2}-\d{2}_\d{4}\.xlsx$/)
    })

    it('agent tab 生成代理商授权清单文件名', () => {
      const filters = reactive({})
      const { exportFilename } = useBrandAuthExport(filters, ref('agent'), ref(0))
      expect(exportFilename.value).toMatch(/^代理商授权清单_\d{4}-\d{2}-\d{2}_\d{4}\.xlsx$/)
    })
  })

  describe('handleExport', () => {
    it('command="zip" 时打开 ZIP 对话框', () => {
      const filters = reactive({})
      const { handleExport, exportZipDialogVisible, exportVisible } = useBrandAuthExport(
        filters, ref('manufacturer'), ref(0)
      )
      handleExport('zip')
      expect(exportZipDialogVisible.value).toBe(true)
      expect(exportVisible.value).toBe(false)
    })

    it('command 为其他值时打开 Excel 导出对话框', () => {
      const filters = reactive({})
      const { handleExport, exportVisible, exportZipDialogVisible } = useBrandAuthExport(
        filters, ref('manufacturer'), ref(0)
      )
      handleExport('excel')
      expect(exportVisible.value).toBe(true)
      expect(exportZipDialogVisible.value).toBe(false)
    })
  })

  describe('doExport', () => {
    it('total>500 时不发起请求并提示警告', async () => {
      const filters = reactive({})
      const { doExport } = useBrandAuthExport(filters, ref('manufacturer'), ref(501))
      await doExport()
      expect(http.get).not.toHaveBeenCalled()
      expect(ElMessage.warning).toHaveBeenCalled()
    })

    it('total<=500 时发起导出请求并下载文件', async () => {
      const filters = reactive({ brandName: '品牌A' })
      const { doExport } = useBrandAuthExport(filters, ref('agent'), ref(10))
      await doExport()
      expect(http.get).toHaveBeenCalledTimes(1)
      const url = http.get.mock.calls[0][0]
      expect(url).toContain('authorizationType=AGENT')
      expect(url).toContain('brandName=')
    })

    it('请求异常时提示失败', async () => {
      http.get.mockRejectedValueOnce(new Error('network'))
      const filters = reactive({})
      const { doExport } = useBrandAuthExport(filters, ref('manufacturer'), ref(10))
      await doExport()
      expect(ElMessage.error).toHaveBeenCalledWith('导出失败')
    })
  })

  describe('handleExportZipConfirm', () => {
    it('调用 brandAuthApi.exportZip 并传正确的参数', async () => {
      const filters = reactive({ brandName: '品牌A' })
      const { handleExportZipConfirm } = useBrandAuthExport(filters, ref('agent'), ref(10))
      await handleExportZipConfirm(['AUTH_DOC', 'SUPPLEMENTARY'])
      expect(brandAuthApi.exportZip).toHaveBeenCalledTimes(1)
      const params = brandAuthApi.exportZip.mock.calls[0][0]
      expect(params.authorizationType).toBe('AGENT')
      expect(params.attachmentTypes).toEqual(['AUTH_DOC', 'SUPPLEMENTARY'])
    })

    it('异常时提示失败信息', async () => {
      brandAuthApi.exportZip.mockRejectedValueOnce(new Error('zip failed'))
      const filters = reactive({})
      const { handleExportZipConfirm } = useBrandAuthExport(filters, ref('manufacturer'), ref(10))
      await handleExportZipConfirm(['AUTH_DOC'])
      expect(ElMessage.error).toHaveBeenCalledWith('导出失败: zip failed')
    })

    it('完成后关闭 loading', async () => {
      const filters = reactive({})
      const { handleExportZipConfirm } = useBrandAuthExport(filters, ref('manufacturer'), ref(10))
      await handleExportZipConfirm([])
      expect(ElLoading.service).toHaveBeenCalled()
    })
  })
})
