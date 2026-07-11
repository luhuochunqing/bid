import { describe, it, expect, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { ref, h, defineComponent } from 'vue'
import ProjectDetailDocumentsCard from './ProjectDetailDocumentsCard.vue'
import { projectDetailKey } from '@/composables/projectDetail/context.js'

const docs = [
  { id: 1, name: '商务标.docx', uploader: '张三', time: '2026-07-01 10:00', size: '1.2MB' }
]

/** 展开 default 作用域插槽，使操作列按钮可断言 */
const TableStub = defineComponent({
  name: 'ElTableStub',
  props: { data: { type: Array, default: () => [] } },
  setup(props, { slots }) {
    return () =>
      h(
        'div',
        { class: 'el-table-stub' },
        (props.data || []).flatMap((row) =>
          slots.default ? slots.default({ row }) : []
        )
      )
  }
})

function mountCard() {
  const detail = {
    project: ref({ documents: docs }),
    canManageProjectDocuments: ref(true),
    handleArchiveDocuments: vi.fn(),
    handleUpload: vi.fn(() => false),
    handleDownload: vi.fn(),
    handleDeleteDoc: vi.fn()
  }

  // 自定义 column stub：渲染 default slot 并注入第一行文档
  const ColumnWithRow = defineComponent({
    name: 'ElTableColumn',
    setup(_, { slots, attrs }) {
      return () =>
        h(
          'div',
          { class: 'col-stub', 'data-label': attrs.label },
          slots.default ? slots.default({ row: docs[0] }) : []
        )
    }
  })

  return {
    wrapper: mount(ProjectDetailDocumentsCard, {
      global: {
        provide: { [projectDetailKey]: detail },
        stubs: {
          'el-card': { template: '<div class="el-card-stub"><slot name="header" /><slot /></div>' },
          'el-table': TableStub,
          'el-table-column': ColumnWithRow,
          'el-button': {
            template: '<button class="el-button-stub" type="button" @click="$emit(\'click\')"><slot /></button>'
          },
          'el-upload': { template: '<div class="el-upload-stub"><slot /></div>' },
          'el-empty': true,
          'el-icon': true,
          Folder: true,
          Document: true,
          DocumentChecked: true,
          Upload: true
        }
      }
    }),
    detail
  }
}

describe('ProjectDetailDocumentsCard', () => {
  it('渲染项目文档标题与文档名', () => {
    const { wrapper } = mountCard()
    expect(wrapper.text()).toContain('项目文档')
    expect(wrapper.text()).toContain('商务标.docx')
  })

  it('操作列用 ops-actions 包裹下载/删除，保证单行布局', async () => {
    const { wrapper, detail } = mountCard()
    const ops = wrapper.find('.ops-actions')
    expect(ops.exists()).toBe(true)
    expect(ops.text()).toContain('下载')
    expect(ops.text()).toContain('删除')

    const buttons = ops.findAll('button')
    expect(buttons.length).toBeGreaterThanOrEqual(2)
    await buttons[0].trigger('click')
    expect(detail.handleDownload).toHaveBeenCalled()
    await buttons[1].trigger('click')
    expect(detail.handleDeleteDoc).toHaveBeenCalled()
  })
})
