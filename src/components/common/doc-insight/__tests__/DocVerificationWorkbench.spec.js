// Input: DocVerificationWorkbench props (schema, data, requirements, markdown)
// Output: assertions on rendering, emits, two-way bind, highlight behavior, XSS safety
// Pos: src/components/common/doc-insight/__tests__/ - component tests
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import { nextTick } from 'vue'
import DocVerificationWorkbench from '../DocVerificationWorkbench.vue'

// Pass-through stubs so wrapper.html() reflects actual slot content and v-model wires up
const elStubs = {
  'el-row': { template: '<div class="el-row"><slot /></div>' },
  'el-col': { template: '<div class="el-col"><slot /></div>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-card': {
    template: '<div class="el-card"><slot name="header" /><slot /></div>'
  },
  'el-button': {
    props: ['type'],
    emits: ['click'],
    template: '<button :data-type="type" @click="$emit(\'click\')"><slot /></button>'
  },
  'el-form-item': {
    props: ['label'],
    template: '<div class="el-form-item"><label>{{ label }}</label><slot /></div>'
  },
  'el-input': {
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template:
      '<input :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />'
  },
  'el-tag': true,
  'el-icon': true,
  Location: true
}

const baseProps = {
  data: { projectName: '测试项目' },
  schema: { groups: [] }
}

describe('DocVerificationWorkbench', () => {
  // --- baseline mount ---

  it('mounts with minimal props', () => {
    const wrapper = mount(DocVerificationWorkbench, {
      props: baseProps,
      global: { stubs: elStubs }
    })
    expect(wrapper.exists()).toBe(true)
  })

  // --- emits ---

  it('emits cancel when 取消 button is clicked', async () => {
    const wrapper = mount(DocVerificationWorkbench, {
      props: baseProps,
      global: { stubs: elStubs }
    })
    const cancelBtn = wrapper
      .findAll('button')
      .find((b) => b.text().includes('取消'))
    expect(cancelBtn).toBeDefined()
    await cancelBtn.trigger('click')
    expect(wrapper.emitted('cancel')).toBeTruthy()
  })

  it('emits confirm with localData when 确认并继续 button is clicked', async () => {
    const wrapper = mount(DocVerificationWorkbench, {
      props: { ...baseProps, data: { projectName: 'ABC' } },
      global: { stubs: elStubs }
    })
    const confirmBtn = wrapper
      .findAll('button')
      .find((b) => b.text().includes('确认并继续'))
    expect(confirmBtn).toBeDefined()
    await confirmBtn.trigger('click')
    const emitted = wrapper.emitted('confirm')
    expect(emitted).toBeTruthy()
    expect(emitted[0][0]).toMatchObject({ projectName: 'ABC' })
  })

  // --- schema-driven field rendering ---

  it('renders schema groups and field labels', () => {
    const schema = {
      groups: [
        {
          id: 'g1',
          title: '基本信息',
          fields: [
            { key: 'projectName', label: '项目名称' },
            { key: 'budget', label: '预算金额' }
          ]
        }
      ]
    }
    const wrapper = mount(DocVerificationWorkbench, {
      props: { data: { projectName: '项目A', budget: '100万' }, schema },
      global: { stubs: elStubs }
    })
    expect(wrapper.text()).toContain('项目名称')
    expect(wrapper.text()).toContain('预算金额')
    expect(wrapper.findAll('input').length).toBe(2)
  })

  // --- two-way bind ---

  it('updates localData when user types into a field', async () => {
    const schema = {
      groups: [
        {
          id: 'g1',
          title: '基本信息',
          fields: [{ key: 'projectName', label: '项目名称' }]
        }
      ]
    }
    const wrapper = mount(DocVerificationWorkbench, {
      props: { data: { projectName: '初始值' }, schema },
      global: { stubs: elStubs }
    })
    await wrapper.find('input').setValue('新值')

    // Trigger confirm to read localData
    const confirmBtn = wrapper
      .findAll('button')
      .find((b) => b.text().includes('确认并继续'))
    await confirmBtn.trigger('click')
    const emitted = wrapper.emitted('confirm')
    expect(emitted[0][0].projectName).toBe('新值')
  })

  // --- data prop watch ---

  it('updates localData when data prop changes', async () => {
    const wrapper = mount(DocVerificationWorkbench, {
      props: { data: { projectName: 'Old' }, schema: { groups: [] } },
      global: { stubs: elStubs }
    })
    await wrapper.setProps({ data: { projectName: 'New' } })

    const confirmBtn = wrapper
      .findAll('button')
      .find((b) => b.text().includes('确认并继续'))
    await confirmBtn.trigger('click')
    const emitted = wrapper.emitted('confirm')
    expect(emitted[0][0].projectName).toBe('New')
  })

  // --- requirements rendering ---

  it('renders no .req-item when requirements is empty', () => {
    const wrapper = mount(DocVerificationWorkbench, {
      props: { ...baseProps, requirements: [] },
      global: { stubs: elStubs }
    })
    expect(wrapper.findAll('.req-item').length).toBe(0)
  })

  it('renders one .req-item per requirement', () => {
    const requirements = [
      { title: '要求一', sourceExcerpt: '条款一' },
      { title: '要求二', sourceExcerpt: '条款二' },
      { title: '要求三', sourceExcerpt: '条款三' }
    ]
    const wrapper = mount(DocVerificationWorkbench, {
      props: { ...baseProps, requirements },
      global: { stubs: elStubs }
    })
    expect(wrapper.findAll('.req-item').length).toBe(3)
    expect(wrapper.text()).toContain('要求一')
    expect(wrapper.text()).toContain('要求三')
  })

  // --- highlightInMarkdown ---

  it('calls scrollIntoView when a .req-item is clicked', async () => {
    const mockScrollIntoView = vi.fn()
    window.HTMLElement.prototype.scrollIntoView = mockScrollIntoView

    const wrapper = mount(DocVerificationWorkbench, {
      props: {
        data: {},
        schema: { groups: [] },
        requirements: [{ title: '关键要求', sourceExcerpt: '关键句' }],
        markdown: '# 标题\n\n关键句出现在这里'
      },
      global: { stubs: elStubs }
    })

    await wrapper.find('.req-item').trigger('click')
    await nextTick()

    expect(mockScrollIntoView).toHaveBeenCalled()
  })

  // --- XSS safety (empty markdown) ---

  it('renders no onerror or script when markdown is empty', () => {
    const wrapper = mount(DocVerificationWorkbench, {
      props: { ...baseProps, markdown: '' },
      global: { stubs: elStubs }
    })
    const html = wrapper.html()
    expect(html).not.toContain('onerror')
    expect(html.toLowerCase()).not.toContain('<script')
  })

  // --- XSS safety (malicious markdown) ---

  it('strips XSS payload from malicious markdown prop', () => {
    const malicious =
      '# Title\n\n<img src=x onerror=alert(1)>\n\n<script>evil()</script>'
    const wrapper = mount(DocVerificationWorkbench, {
      props: { ...baseProps, markdown: malicious },
      global: { stubs: elStubs }
    })
    const html = wrapper.html()
    expect(html).not.toContain('onerror')
    expect(html.toLowerCase()).not.toContain('<script')
  })

  // --- valid markdown renders as HTML ---

  it('renders valid markdown as HTML headings and bold', () => {
    const wrapper = mount(DocVerificationWorkbench, {
      props: {
        ...baseProps,
        markdown: '# 主标题\n\n**加粗文字**\n\n- 列表项一\n- 列表项二'
      },
      global: { stubs: elStubs }
    })
    const mdHtml = wrapper.find('.markdown-container').html()
    expect(mdHtml).toContain('<h1')
    expect(mdHtml).toContain('<strong>')
    expect(mdHtml).toContain('<ul>')
    expect(mdHtml).toContain('<li>')
  })
})
