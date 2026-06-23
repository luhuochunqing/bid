import { mount } from '@vue/test-utils'
import { describe, it, expect } from 'vitest'
import ProjectGroupSettingsPanel from './ProjectGroupSettingsPanel.vue'

const globalStubs = {
  'el-table': true,
  'el-table-column': true,
  'el-input': true,
  'el-select': true,
  'el-option': true,
  'el-button': true
}

describe('ProjectGroupSettingsPanel', () => {
  const defaultProps = {
    projectGroups: [],
    userOptions: [],
    projectScopeOptions: []
  }

  it('renders panel container with expected class', () => {
    const wrapper = mount(ProjectGroupSettingsPanel, {
      props: defaultProps,
      global: { stubs: globalStubs }
    })
    expect(wrapper.find('.project-group-settings-panel').exists()).toBe(true)
  })

  it('defines expected emits', () => {
    const wrapper = mount(ProjectGroupSettingsPanel, {
      props: defaultProps,
      global: { stubs: globalStubs }
    })
    const emittedEvents = Object.keys(wrapper.emitted())
    // Component declares emits: add-group, save-group, delete-group
    expect(wrapper.vm.$options.emits).toEqual(['add-group', 'save-group', 'delete-group'])
  })
})
