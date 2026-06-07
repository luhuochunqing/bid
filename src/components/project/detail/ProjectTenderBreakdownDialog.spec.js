import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

import { projectDetailKey } from '@/composables/projectDetail/context.js'
import ProjectTenderBreakdownDialog from './ProjectTenderBreakdownDialog.vue'

describe('ProjectTenderBreakdownDialog', () => {
  it('disables tender file upload while parsing is in progress', () => {
    const wrapper = mount(ProjectTenderBreakdownDialog, {
      global: {
        provide: {
          [projectDetailKey]: {
            tenderBreakdownDialogVisible: true,
            tenderBreakdownParsing: true,
            handleTenderBreakdownUpload: vi.fn(),
          },
        },
        stubs: {
          ElDialog: {
            props: ['modelValue'],
            template: '<section><slot /><slot name="footer" /></section>',
          },
          ElUpload: {
            props: ['disabled'],
            template: '<div data-test="tender-upload" :data-disabled="String(disabled)"><slot /><slot name="tip" /></div>',
          },
          ElAlert: {
            template: '<div data-test="parse-alert" />',
          },
          ElButton: {
            template: '<button type="button"><slot /></button>',
          },
          ElIcon: {
            template: '<span><slot /></span>',
          },
          UploadFilled: true,
        },
      },
    })

    expect(wrapper.find('[data-test="tender-upload"]').attributes('data-disabled')).toBe('true')
    expect(wrapper.find('[data-test="parse-alert"]').exists()).toBe(true)
  })
})
