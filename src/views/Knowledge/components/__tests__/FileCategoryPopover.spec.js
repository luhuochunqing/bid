import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import FileCategoryPopover from '../FileCategoryPopover.vue'

describe('FileCategoryPopover', () => {
  it('renders correctly with category details', () => {
    const wrapper = mount(FileCategoryPopover, {
      props: {
        categoryDetails: {
          TENDER: 2,
          BID: 3,
          OPEN_LIST: 0,
          WIN_NOTICE: 0,
          DEPOSIT_RECEIPT: 0,
          OTHER: 1
        },
        fileCount: 6
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
          OPEN_LIST: 1,
          WIN_NOTICE: 0,
          DEPOSIT_RECEIPT: 0,
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

    // CO-592: Verify categoryItems computed property (6 standard categories:
    // TENDER/BID/OPEN_LIST/WIN_NOTICE/DEPOSIT_RECEIPT/OTHER)
    expect(wrapper.vm.categoryItems).toHaveLength(6)
    expect(wrapper.vm.categoryItems[0]).toMatchObject({ key: 'tender', label: '招标文件', count: 3 })
    expect(wrapper.vm.categoryItems[1]).toMatchObject({ key: 'bid', label: '标书文件', count: 2 })
    expect(wrapper.vm.categoryItems[2]).toMatchObject({ key: 'open', label: '开标一览表', count: 1 })
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
          OPEN_LIST: 2,
          WIN_NOTICE: 3,
          DEPOSIT_RECEIPT: 8,
          OTHER: 1
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
    // CO-592: All 6 standard category items should be rendered (with 0 counts)
    expect(wrapper.vm.categoryItems).toHaveLength(6)
  })
})
