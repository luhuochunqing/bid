import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import FileCategoryPopover from '../FileCategoryPopover.vue'

describe('FileCategoryPopover', () => {
  it('renders correctly with category details', () => {
    const wrapper = mount(FileCategoryPopover, {
      props: {
        categoryDetails: {
          TENDER: 2,
          BID: 3,
          CONTRACT: 1,
          PROCESS: 5,
          RETROSPECTIVE: 0,
          OTHER: 1
        },
        fileCount: 12
      },
      global: {
        stubs: {
          ElPopover: {
            props: ['width', 'placement', 'trigger', 'openDelay'],
            template: '<div class="popover-stub"><slot name="reference" /><slot /></div>'
          },
          ElButton: {
            template: '<button><slot /></button>'
          },
          ElIcon: true,
          Files: true
        }
      }
    })

    expect(wrapper.find('.popover-stub').exists()).toBe(true)
  })

  it('computes category items correctly from categoryDetails prop', () => {
    const wrapper = mount(FileCategoryPopover, {
      props: {
        categoryDetails: {
          TENDER: 3,
          BID: 2,
          CONTRACT: 1,
          OTHER: 0
        },
        fileCount: 10
      },
      global: {
        stubs: {
          ElPopover: {
            template: '<div class="popover-stub"><slot name="reference" /><slot /></div>'
          },
          ElButton: true,
          ElIcon: true,
          Files: true
        }
      }
    })

    // Verify categoryItems computed property (9 categories: TENDER/BID/OPEN_LIST/WIN_NOTICE/DEPOSIT_RECEIPT/CONTRACT/PROCESS/RETROSPECTIVE/OTHER)
    expect(wrapper.vm.categoryItems).toHaveLength(9)
    expect(wrapper.vm.categoryItems[0]).toMatchObject({ key: 'tender', label: '招标文件', count: 3 })
    expect(wrapper.vm.categoryItems[1]).toMatchObject({ key: 'bid', label: '标书文件', count: 2 })
    expect(wrapper.vm.categoryItems[2]).toMatchObject({ key: 'open', label: '开标一览表', count: 0 })
    expect(wrapper.vm.categoryItems[3]).toMatchObject({ key: 'award', label: '中标通知书', count: 0 })

    // Verify totalCount sums up categoryDetails values
    expect(wrapper.vm.totalCount).toBe(6)
  })

  it('falls back to fileCount when categoryDetails is null', () => {
    const wrapper = mount(FileCategoryPopover, {
      props: {
        categoryDetails: null,
        fileCount: 8
      },
      global: {
        stubs: {
          ElPopover: {
            template: '<div class="popover-stub"><slot name="reference" /><slot /></div>'
          },
          ElButton: true,
          ElIcon: true,
          Files: true
        }
      }
    })

    expect(wrapper.vm.totalCount).toBe(8)
    expect(wrapper.vm.categoryItems.every(item => item.count === 0)).toBe(true)
  })

  it('displays correct category counts', () => {
    const wrapper = mount(FileCategoryPopover, {
      props: {
        categoryDetails: {
          TENDER: 5,
          BID: 10,
          AWARD_NOTICE: 2,
          CONTRACT: 3,
          PROCESS: 8,
          RETROSPECTIVE: 1,
          OTHER: 0
        },
        fileCount: 29
      },
      global: {
        stubs: {
          ElPopover: {
            template: '<div class="popover-stub"><slot name="reference" /><slot /></div>'
          },
          ElButton: true,
          ElIcon: true,
          Files: true
        }
      }
    })

    // Verify total count matches the sum of category details
    expect(wrapper.vm.totalCount).toBe(29)
  })

  it('handles empty category details', () => {
    const wrapper = mount(FileCategoryPopover, {
      props: {
        categoryDetails: null,
        fileCount: 0
      },
      global: {
        stubs: {
          ElPopover: {
            template: '<div class="popover-stub"><slot name="reference" /><slot /></div>'
          },
          ElButton: true,
          ElIcon: true,
          Files: true
        }
      }
    })

    // Verify empty data handling
    expect(wrapper.vm.totalCount).toBe(0)
    // All 9 category items should be rendered (with 0 counts)
    expect(wrapper.vm.categoryItems).toHaveLength(9)
  })
})
