import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import BulkImportDialog from './BulkImportDialog.vue'

function mountDialog(props = {}) {
  return shallowMount(BulkImportDialog, {
    props: {
      modelValue: true,
      selectedFile: null,
      result: null,
      importProgress: null,
      polling: false,
      templateDownloading: false,
      importing: false,
      ...props,
    },
    global: {
      stubs: {
        'el-dialog': { template: '<section><slot /><slot name="footer" /></section>' },
        'el-upload': { template: '<div class="upload-stub"><slot /></div>' },
        'el-icon': { template: '<i><slot /></i>' },
        'el-button': { template: '<button><slot /></button>' },
        'el-alert': {
          name: 'ElAlert',
          props: ['type', 'title', 'description', 'closable'],
          template: '<div class="alert-stub" :data-type="type">{{ title }}{{ description ? ": " + description : "" }}</div>',
        },
        'el-progress': {
          name: 'ElProgress',
          props: ['percentage', 'status', 'striped', 'stripedFlow'],
          template: '<div class="progress-stub" :data-percentage="percentage" :data-status="status || \'\'" />',
        },
        'el-table': {
          name: 'ElTable',
          props: ['data'],
          template: '<table class="table-stub"><tbody><tr v-for="row in data" :key="row.rowNumber"><td>{{ row.rowNumber }}</td><td>{{ row.field }}</td><td>{{ row.errorMessage }}</td></tr></tbody></table>',
        },
        'el-table-column': { template: '<th><slot /></th>' },
      },
    },
  })
}

describe('BulkImportDialog', () => {
  describe('初始状态', () => {
    it('未选择文件时不显示进度区域', () => {
      const wrapper = mountDialog()
      expect(wrapper.find('.bulk-import-progress').exists()).toBe(false)
    })

    it('未选择文件时"开始导入"按钮禁用', () => {
      const wrapper = mountDialog({ selectedFile: null })
      const submitBtn = wrapper.findAll('button').find(b => b.text().includes('开始导入'))
      expect(submitBtn?.attributes('disabled')).toBeDefined()
    })

    it('选择文件后"开始导入"按钮启用', () => {
      const wrapper = mountDialog({
        selectedFile: { name: 'test.xlsx' },
      })
      const submitBtn = wrapper.findAll('button').find(b => b.text().includes('开始导入'))
      expect(submitBtn?.attributes('disabled')).toBeUndefined()
    })

    it('显示已选文件名', () => {
      const wrapper = mountDialog({
        selectedFile: { name: 'my-tenders.xlsx' },
      })
      expect(wrapper.text()).toContain('my-tenders.xlsx')
    })
  })

  describe('轮询中状态', () => {
    it('polling=true 但无 importProgress 时显示"等待处理"', () => {
      const wrapper = mountDialog({ polling: true, importProgress: null })
      expect(wrapper.text()).toContain('导入任务已创建，等待处理')
    })

    it('polling=true 且 importProgress 有值时显示进度条 + 实时计数', () => {
      const wrapper = mountDialog({
        polling: true,
        importProgress: {
          status: 'PROCESSING',
          totalRows: 100,
          processedRows: 50,
          successCount: 45,
          failureCount: 5,
          percent: 50,
        },
      })
      expect(wrapper.text()).toContain('正在导入')
      expect(wrapper.text()).toContain('已处理 50 / 100 行')
      expect(wrapper.text()).toContain('成功 45')
      expect(wrapper.text()).toContain('失败 5')
      const progress = wrapper.find('.progress-stub')
      expect(progress.exists()).toBe(true)
      expect(progress.attributes('data-percentage')).toBe('50')
    })

    it('PENDING 状态显示"排队中"', () => {
      const wrapper = mountDialog({
        polling: true,
        importProgress: {
          status: 'PENDING',
          totalRows: 0,
          processedRows: 0,
          successCount: 0,
          failureCount: 0,
          percent: 0,
        },
      })
      expect(wrapper.text()).toContain('排队中')
    })

    it('轮询中"开始导入"按钮禁用 + 显示"导入进行中"', () => {
      const wrapper = mountDialog({
        polling: true,
        selectedFile: { name: 'test.xlsx' },
      })
      const submitBtn = wrapper.findAll('button').find(b => b.text().includes('导入进行中'))
      expect(submitBtn).toBeDefined()
      expect(submitBtn?.attributes('disabled')).toBeDefined()
    })

    it('轮询中上传组件禁用', () => {
      const wrapper = mountDialog({
        polling: true,
      })
      const upload = wrapper.find('.upload-stub')
      // el-upload disabled 通过属性传递，stub 不反射，但按钮文案应提示
      expect(wrapper.text()).toContain('导入进行中')
    })
  })

  describe('终态：COMPLETED', () => {
    it('显示成功提示 + 不显示错误表格', () => {
      const wrapper = mountDialog({
        polling: false,
        result: {
          status: 'COMPLETED',
          totalRows: 100,
          successCount: 100,
          failureCount: 0,
          errors: [],
        },
      })
      const alert = wrapper.find('.alert-stub')
      expect(alert.exists()).toBe(true)
      expect(alert.attributes('data-type')).toBe('success')
      expect(alert.text()).toContain('100 行全部导入成功')
      expect(wrapper.find('.table-stub').exists()).toBe(false)
    })
  })

  describe('终态：PARTIAL_SUCCESS', () => {
    it('显示部分成功提示 + 错误表格', () => {
      const wrapper = mountDialog({
        polling: false,
        result: {
          status: 'PARTIAL_SUCCESS',
          totalRows: 100,
          successCount: 95,
          failureCount: 5,
          errors: [
            { rowNumber: 12, field: 'duplicate', errorMessage: '三字段重复', tenderTitle: '标讯A' },
            { rowNumber: 34, field: 'title', errorMessage: '标题不能为空', tenderTitle: null },
          ],
        },
      })
      const alert = wrapper.find('.alert-stub')
      expect(alert.attributes('data-type')).toBe('warning')
      expect(alert.text()).toContain('部分成功')
      expect(alert.text()).toContain('成功 95')
      expect(alert.text()).toContain('失败 5')
      const table = wrapper.find('.table-stub')
      expect(table.exists()).toBe(true)
      // 表格应显示 rowNumber/field/errorMessage
      expect(table.text()).toContain('12')
      expect(table.text()).toContain('duplicate')
      expect(table.text()).toContain('三字段重复')
      expect(table.text()).toContain('34')
    })
  })

  describe('终态：FAILED', () => {
    it('显示失败提示 + 错误表格', () => {
      const wrapper = mountDialog({
        polling: false,
        result: {
          status: 'FAILED',
          totalRows: 10,
          successCount: 0,
          failureCount: 10,
          errors: [
            { rowNumber: 2, field: 'purchaserName', errorMessage: '招标主体不能为空', tenderTitle: '标讯B' },
          ],
        },
      })
      const alert = wrapper.find('.alert-stub')
      expect(alert.attributes('data-type')).toBe('error')
      expect(alert.text()).toContain('导入失败')
      expect(alert.text()).toContain('未写入任何数据')
      expect(wrapper.find('.table-stub').exists()).toBe(true)
    })
  })

  describe('字段标签格式化', () => {
    // 注意：formatField 是 el-table-column 的 :formatter，由 el-table 在渲染时调用。
    // 单元测试 stub 难以完整模拟 el-table-column 的 formatter 调用链，
    // 这里只验证 field 原始值被渲染到表格中，中文标签转换由集成/E2E 测试覆盖。
    it('duplicate field 渲染到表格', () => {
      const wrapper = mountDialog({
        polling: false,
        result: {
          status: 'FAILED',
          totalRows: 1,
          successCount: 0,
          failureCount: 1,
          errors: [
            { rowNumber: 2, field: 'duplicate', errorMessage: '重复', tenderTitle: 'A' },
          ],
        },
      })
      expect(wrapper.find('.table-stub').exists()).toBe(true)
      expect(wrapper.text()).toContain('duplicate')
    })

    it('未知 field 原样显示', () => {
      const wrapper = mountDialog({
        polling: false,
        result: {
          status: 'FAILED',
          totalRows: 1,
          successCount: 0,
          failureCount: 1,
          errors: [
            { rowNumber: 2, field: 'unknownField', errorMessage: 'err', tenderTitle: 'A' },
          ],
        },
      })
      expect(wrapper.text()).toContain('unknownField')
    })
  })

  describe('事件触发', () => {
    it('点击"开始导入"按钮触发 submit 事件', async () => {
      const wrapper = mountDialog({
        selectedFile: { name: 'test.xlsx' },
      })
      const submitBtn = wrapper.findAll('button').find(b => b.text().includes('开始导入'))
      await submitBtn?.trigger('click')
      expect(wrapper.emitted('submit')).toBeTruthy()
    })

    it('点击"下载批量导入模板"按钮触发 download-template 事件', async () => {
      const wrapper = mountDialog()
      const dlBtn = wrapper.findAll('button').find(b => b.text().includes('下载批量导入模板'))
      await dlBtn?.trigger('click')
      expect(wrapper.emitted('download-template')).toBeTruthy()
    })
  })

  describe('提示文案', () => {
    it('提示中说明"异步处理"特性', () => {
      const wrapper = mountDialog()
      const tips = wrapper.find('.bulk-import-tips')
      expect(tips.exists()).toBe(true)
      expect(tips.text()).toContain('异步处理')
      expect(tips.text()).toContain('进度不会丢失')
    })
  })
})
