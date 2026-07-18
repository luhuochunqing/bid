import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import WarehouseExportPackageDetail from './WarehouseExportPackageDetail.vue'

describe('WarehouseExportPackageDetail', () => {
  it('渲染仓库信息台账基本信息', () => {
    const wrapper = mount(WarehouseExportPackageDetail, {
      props: {
        totalCount: 42,
        summary: { filterSummary: '当前筛选', attachmentScope: 'ALL', elapsedMs: 5000, zipBytes: 10240 },
        attachmentForms: ['ATTACHMENTS_FOLDER', 'WORD_COMBINED']
      }
    })
    expect(wrapper.text()).toContain('仓库信息台账.xlsx')
    expect(wrapper.text()).toContain('42 条')
    expect(wrapper.text()).toContain('当前筛选')
    expect(wrapper.text()).toContain('仓库附件合订本.docx')
  })

  it('attachmentForms 不含 ATTACHMENTS_FOLDER 时不渲染 attachments/ 节点', () => {
    const wrapper = mount(WarehouseExportPackageDetail, {
      props: {
        totalCount: 1,
        summary: {},
        attachmentForms: ['WORD_COMBINED']
      }
    })
    expect(wrapper.text()).not.toContain('attachments/')
    expect(wrapper.text()).toContain('仓库附件合订本.docx')
  })

  it('attachmentForms 不含 WORD_COMBINED 时不渲染合订本', () => {
    const wrapper = mount(WarehouseExportPackageDetail, {
      props: {
        totalCount: 1,
        summary: { propertyCertCount: 1 },
        attachmentForms: ['ATTACHMENTS_FOLDER']
      }
    })
    expect(wrapper.text()).toContain('attachments/')
    expect(wrapper.text()).not.toContain('仓库附件合订本.docx')
  })

  it('附件计数显示正确', () => {
    const wrapper = mount(WarehouseExportPackageDetail, {
      props: {
        totalCount: 5,
        summary: {
          propertyCertCount: 3,
          invoiceCount: 2,
          photosCount: 10,
          leaseContractCount: 1
        },
        attachmentForms: ['ATTACHMENTS_FOLDER']
      }
    })
    expect(wrapper.text()).toContain('产权证 3 份')
    expect(wrapper.text()).toContain('发票 2 份')
    expect(wrapper.text()).toContain('照片 10 张')
    expect(wrapper.text()).toContain('租赁合同 1 份')
  })

  it('formatElapsed 正确格式化耗时', () => {
    const wrapper1 = mount(WarehouseExportPackageDetail, {
      props: { totalCount: 1, summary: { elapsedMs: 500 }, attachmentForms: [] }
    })
    expect(wrapper1.text()).toContain('500 毫秒')

    const wrapper2 = mount(WarehouseExportPackageDetail, {
      props: { totalCount: 1, summary: { elapsedMs: 3000 }, attachmentForms: [] }
    })
    expect(wrapper2.text()).toContain('3 秒')

    const wrapper3 = mount(WarehouseExportPackageDetail, {
      props: { totalCount: 1, summary: { elapsedMs: 65000 }, attachmentForms: [] }
    })
    expect(wrapper3.text()).toContain('1 分 5 秒')

    const wrapper4 = mount(WarehouseExportPackageDetail, {
      props: { totalCount: 1, summary: { elapsedMs: 0 }, attachmentForms: [] }
    })
    expect(wrapper4.text()).toContain('—')
  })

  it('formatBytes 正确格式化包大小', () => {
    const wrapper1 = mount(WarehouseExportPackageDetail, {
      props: { totalCount: 1, summary: { zipBytes: 500 }, attachmentForms: [] }
    })
    expect(wrapper1.text()).toContain('500 B')

    const wrapper2 = mount(WarehouseExportPackageDetail, {
      props: { totalCount: 1, summary: { zipBytes: 2048 }, attachmentForms: [] }
    })
    expect(wrapper2.text()).toContain('2.00 KB')

    const wrapper3 = mount(WarehouseExportPackageDetail, {
      props: { totalCount: 1, summary: { zipBytes: 1048576 }, attachmentForms: [] }
    })
    expect(wrapper3.text()).toContain('1.00 MB')

    const wrapper4 = mount(WarehouseExportPackageDetail, {
      props: { totalCount: 1, summary: { zipBytes: 0 }, attachmentForms: [] }
    })
    expect(wrapper4.text()).toContain('—')
  })

  it('链接有效期固定显示 7 天', () => {
    const wrapper = mount(WarehouseExportPackageDetail, {
      props: { totalCount: 1, summary: {}, attachmentForms: [] }
    })
    expect(wrapper.text()).toContain('7 天')
  })
})
