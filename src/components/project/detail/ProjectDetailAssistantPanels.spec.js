import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

const context = {
  assistantPanelVisible: false,
  dialogProjectId: '12',
  isDemoMode: false,
  handleOpenCompetitionIntel: vi.fn(),
  handleOpenRoiAnalysis: vi.fn(),
  handleOpenScoreCoverage: vi.fn(),
  handleOpenComplianceCheck: vi.fn(),
  handleOpenVersionControl: vi.fn(),
  handleOpenCollaboration: vi.fn(),
  handleOpenAutoTasks: vi.fn(),
  handleOpenMobileCard: vi.fn(),
}

vi.mock('@/composables/projectDetail/context.js', () => ({
  useProjectDetailContext: () => context,
}))

vi.mock('@/components/ai/SmartAssistantPanel.vue', () => ({
  default: {
    name: 'SmartAssistantPanel',
    template: '<div data-test="smart-assistant-panel" />',
  },
}))

vi.mock('./ProjectDetailFeatureModals.vue', () => ({
  default: {
    name: 'ProjectDetailFeatureModals',
    template: '<div data-test="project-detail-feature-modals" />',
  },
}))

import ProjectDetailAssistantPanels from './ProjectDetailAssistantPanels.vue'

describe('ProjectDetailAssistantPanels', () => {
  it('mounts the feature modals alongside the smart assistant panel', () => {
    const wrapper = mount(ProjectDetailAssistantPanels)

    expect(wrapper.find('[data-test="smart-assistant-panel"]').exists()).toBe(true)
    expect(wrapper.find('[data-test="project-detail-feature-modals"]').exists()).toBe(true)
  })
})
