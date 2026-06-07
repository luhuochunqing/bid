import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'
import ScoreDraftDialog from './ScoreDraftDialog.vue'

vi.mock('@/api', () => ({
  projectsApi: {
    getScoreDrafts: vi.fn(),
    parseScoreDrafts: vi.fn(),
    updateScoreDraft: vi.fn(),
    generateScoreDraftTasks: vi.fn(),
    clearScoreDrafts: vi.fn(),
  },
}))

describe('ScoreDraftDialog', () => {
  it('allows Word Excel and text PDF score draft files', () => {
    const wrapper = mount(ScoreDraftDialog, {
      props: {
        visible: false,
        projectId: 1001,
      },
      global: {
        directives: {
          loading: vi.fn(),
        },
        stubs: {
          ElDialog: {
            props: ['modelValue'],
            template: '<section><slot /><slot name="footer" /></section>',
          },
          ElUpload: {
            props: ['accept'],
            template: '<div data-test="score-draft-upload" :data-accept="accept"><slot /></div>',
          },
          ElButton: {
            template: '<button type="button"><slot /></button>',
          },
          ElTag: true,
          ElTable: true,
          ElTableColumn: true,
          ElInput: true,
          ElDatePicker: true,
          ElSelect: true,
          ElOption: true,
        },
      },
    })

    expect(wrapper.find('[data-test="score-draft-upload"]').attributes('data-accept'))
      .toBe('.doc,.docx,.xls,.xlsx,.pdf')
  })
})
