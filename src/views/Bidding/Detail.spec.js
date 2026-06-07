import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

vi.mock('./detail/DetailPage.vue', () => ({
  default: { name: 'DetailPage', template: '<div class="detail-page-stub" />' },
}))

describe('Detail.vue', () => {
  it('renders detail page shell', async () => {
    const Detail = (await import('./Detail.vue')).default
    const wrapper = mount(Detail)
    expect(wrapper.find('.detail-page-stub').exists()).toBe(true)
  })
})
