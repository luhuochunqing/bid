import { shallowMount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import { nextTick, ref } from 'vue'
import TenderBasicInfoTab from './TenderBasicInfoTab.vue'

/**
 * AdaptiveFormPage stub factory.
 * Exposes getFields() + hasSchema ref so the component can read schema-driven
 * field configuration. Without this stub, shallowMount hides the slot content.
 */
function createAdaptiveFormPageStub(fields = [], hasSchemaFlag = false) {
  return {
    name: 'AdaptiveFormPage',
    props: ['scope', 'modelValue', 'disabled'],
    setup(props, { expose }) {
      const hasSchemaRef = ref(hasSchemaFlag)
      expose({
        getFields: () => fields,
        hasSchema: hasSchemaRef,
      })
      return {}
    },
    template: '<div class="adaptive-form-page-stub"><slot name="fallback-form" /></div>',
  }
}

const elStubs = {
  'el-card': { template: '<div><slot /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': {
    props: ['label', 'required', 'prop', 'labelWidth'],
    template: '<div class="form-item-stub" :data-prop="prop" :data-required="required === true ? \'true\' : \'false\'">{{ label }}<slot /></div>',
  },
  'el-row': { template: '<div class="row-stub"><slot /></div>' },
  'el-col': {
    props: ['span'],
    template: '<div class="col-stub" :data-span="span"><slot /></div>',
  },
  'el-input': { template: '<input />' },
  'el-cascader': { template: '<div class="cascader-stub" />' },
  'el-select': { template: '<select><slot /></select>' },
  'el-option': { template: '<option />' },
  'el-date-picker': { template: '<input type="datetime" />' },
  'el-upload': { template: '<div class="upload-stub"><slot /></div>' },
  'el-button': { template: '<button><slot /></button>' },
  'el-icon': { template: '<span><slot /></span>' },
  DocumentCopy: { template: '<i />' },
  Upload: { template: '<i />' },
}

function createForm(overrides = {}) {
  return {
    title: '',
    region: '',
    purchaser: '',
    deadline: null,
    bidOpeningTime: null,
    customerType: '',
    priority: '',
    projectType: '',
    contact: '',
    phone: '',
    landline: '',
    mail: '',
    contact2: '',
    phone2: '',
    landline2: '',
    mail2: '',
    description: '',
    tenderInfo: '',
    attachments: [],
    pastedText: '',
    ...overrides,
  }
}

function mountTab(props = {}, adaptiveFields = [], hasSchemaFlag = false) {
  return shallowMount(TenderBasicInfoTab, {
    props: {
      activeTab: 'basic',
      form: createForm(),
      rules: {},
      regions: [],
      customerTypes: ['政府机关/事业单位/高校'],
      projectTypes: ['工业品'],
      priorities: [{ value: 'S', label: 'S 级', desc: '战略级', standard: '标准' }],
      saving: false,
      isReadOnly: false,
      parsingDocument: false,
      acceptFileTypes: '.pdf',
      ...props,
    },
    global: {
      stubs: {
        ...elStubs,
        AdaptiveFormPage: createAdaptiveFormPageStub(adaptiveFields, hasSchemaFlag),
      },
    },
  })
}

describe('TenderBasicInfoTab', () => {
  it('renders all default fields when no schema is available', async () => {
    const wrapper = mountTab({}, [], false)
    await nextTick()

    expect(wrapper.text()).toContain('项目名称')
    expect(wrapper.text()).toContain('总部所在地')
    expect(wrapper.text()).toContain('招标主体')
    expect(wrapper.text()).toContain('报名截止时间')
    expect(wrapper.text()).toContain('开标时间')
    expect(wrapper.text()).toContain('客户类型')
    expect(wrapper.text()).toContain('优先级')
    expect(wrapper.text()).toContain('项目类型')
    expect(wrapper.text()).toContain('标讯描述')
    expect(wrapper.text()).toContain('标讯信息')
    expect(wrapper.text()).toContain('粘贴识别')
    expect(wrapper.text()).toContain('标讯文件')
    expect(wrapper.text()).toContain('联系人1')
    expect(wrapper.text()).toContain('联系人2')
  })

  it('hides field when schema sets enabled=false', async () => {
    const fields = [
      { key: 'title', label: '项目名称', enabled: true, required: true, type: 'text' },
      { key: 'purchaser', label: '招标主体', enabled: false, required: true, type: 'text' },
    ]
    const wrapper = mountTab({}, fields, true)
    await nextTick()

    const cols = wrapper.findAll('.col-stub')
    const titleCol = cols.find((col) => col.find('[data-prop="title"]').exists())
    expect(titleCol).toBeTruthy()
    expect(titleCol.element.style.display).not.toBe('none')

    const purchaserCol = cols.find((col) => col.find('[data-prop="purchaser"]').exists())
    expect(purchaserCol).toBeTruthy()
    expect(purchaserCol.element.style.display).toBe('none')
  })

  it('sets required attribute on el-form-item when schema field has required=true', async () => {
    const fields = [
      { key: 'title', label: '项目名称', enabled: true, required: true, type: 'text' },
      { key: 'purchaser', label: '招标主体', enabled: true, required: false, type: 'text' },
    ]
    const wrapper = mountTab({}, fields, true)
    await nextTick()

    const titleItem = wrapper.find('[data-prop="title"]')
    expect(titleItem.exists()).toBe(true)
    expect(titleItem.attributes('data-required')).toBe('true')

    const purchaserItem = wrapper.find('[data-prop="purchaser"]')
    expect(purchaserItem.exists()).toBe(true)
    expect(purchaserItem.attributes('data-required')).toBe('false')
  })

  it('keeps contact 1 group in horizontal 4+6+7+7 colSpan layout', async () => {
    const wrapper = mountTab()
    await nextTick()

    const contactItem = wrapper.find('[data-prop="contact"]')
    expect(contactItem.exists()).toBe(true)
    expect(contactItem.element.parentElement.getAttribute('data-span')).toBe('4')

    const phoneItem = wrapper.find('[data-prop="phone"]')
    expect(phoneItem.exists()).toBe(true)
    expect(phoneItem.element.parentElement.getAttribute('data-span')).toBe('6')

    const landlineItem = wrapper.find('[data-prop="landline"]')
    expect(landlineItem.exists()).toBe(true)
    expect(landlineItem.element.parentElement.getAttribute('data-span')).toBe('7')

    const mailItem = wrapper.find('[data-prop="mail"]')
    expect(mailItem.exists()).toBe(true)
    expect(mailItem.element.parentElement.getAttribute('data-span')).toBe('7')
  })

  it('exposes validate method for parent to call', () => {
    const wrapper = mountTab()
    expect(typeof wrapper.vm.validate).toBe('function')
  })

  it('renders simple fields in schema order (drag-and-drop reordering)', async () => {
    // Schema 顺序：purchaser 在 title 之前（模拟拖拽排序后）
    const fields = [
      { key: 'purchaser', label: '招标主体', enabled: true, required: true, type: 'text' },
      { key: 'title', label: '项目名称', enabled: true, required: true, type: 'text' },
    ]
    const wrapper = mountTab({}, fields, true)
    await nextTick()
    await nextTick()

    // 直接检查 HTML 中 purchaser 的 data-prop 出现在 title 的 data-prop 之前
    const html = wrapper.html()
    const purchaserIdx = html.indexOf('data-prop="purchaser"')
    const titleIdx = html.indexOf('data-prop="title"')

    expect(purchaserIdx).toBeGreaterThan(-1)
    expect(titleIdx).toBeGreaterThan(-1)
    expect(purchaserIdx).toBeLessThan(titleIdx)
  })

  it('reads field label from schema (config page label change reflects in business page)', async () => {
    const fields = [
      { key: 'title', label: '自定义项目名称', enabled: true, required: true, type: 'text' },
    ]
    const wrapper = mountTab({}, fields, true)
    await nextTick()

    // 业务页应显示 schema 中的 label，而非硬编码的"项目名称"
    expect(wrapper.text()).toContain('自定义项目名称')
  })
})
