import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

vi.mock('./ai-analysis/AiAnalysisPage.vue', () => ({
  default: { name: 'AiAnalysisPage', template: '<div class="ai-analysis-page-stub" />' },
}))

describe('AIAnalysis.vue', () => {
  it('renders ai analysis page shell', async () => {
    const AIAnalysis = (await import('./AIAnalysis.vue')).default
    const wrapper = mount(AIAnalysis)
    expect(wrapper.find('.ai-analysis-page-stub').exists()).toBe(true)
  })
})
