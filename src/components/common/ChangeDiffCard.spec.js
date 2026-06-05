// Input: ChangeDiffCard.vue component
// Output: unit tests for ChangeDiffCard
// Pos: src/components/common/ - Component test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ChangeDiffCard from './ChangeDiffCard.vue'

const mountCard = (props = {}) =>
  mount(ChangeDiffCard, {
    props,
    global: {
      stubs: {
        'el-empty': { template: '<div class="el-empty-stub"><slot /></div>' },
        'el-table': {
          props: ['data'],
          template: '<div class="el-table-stub"><slot /></div>'
        },
        'el-table-column': {
          props: ['label', 'prop', 'width'],
          template: '<div class="el-table-column-stub"><slot :row="{}" /></div>'
        }
      }
    }
  })

describe('ChangeDiffCard', () => {
  it('renders empty state when no changes', () => {
    const wrapper = mountCard({ changes: [] })
    expect(wrapper.find('.el-empty-stub').exists()).toBe(true)
  })

  it('renders empty state when prop missing', () => {
    const wrapper = mountCard({})
    expect(wrapper.find('.el-empty-stub').exists()).toBe(true)
  })

  it('renders table when changes present', () => {
    const wrapper = mountCard({
      changes: [
        { field: 'title', before: 'old', after: 'new' },
        { field: 'status', before: 'DRAFT', after: 'PUBLISHED' }
      ]
    })
    expect(wrapper.find('.el-table-stub').exists()).toBe(true)
    expect(wrapper.find('.el-empty-stub').exists()).toBe(false)
  })
})
