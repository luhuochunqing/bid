import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CustomerInfoMatrix from './CustomerInfoMatrix.vue'

const globalStubs = {
  CustomerInfoMatrixTable: {
    name: 'CustomerInfoMatrixTable',
    props: ['localData', 'editableColumns', 'disabled'],
    emits: ['data-change'],
    template: '<button class="matrix-table-stub" @click="$emit(\'data-change\')"></button>',
  },
}

describe('CustomerInfoMatrix', () => {
  it('shows external role rows converted from tender 285 EAV response', () => {
    const wrapper = mount(CustomerInfoMatrix, {
      props: {
        modelValue: [
          {
            roleKey: 'EXTERNAL_ROLE_1',
            CAN_GET_KEY_INFO: 'true',
            CAN_REMOVE_ADVERSE: 'true',
            CAN_SYNC_EVAL: 'true',
            CONTACT_INFO: '18888888888',
            INFO_TENDENCY_BASIS: '客户明确偏向西域',
          },
        ],
      },
      global: { stubs: globalStubs },
    })

    const table = wrapper.findComponent({ name: 'CustomerInfoMatrixTable' })
    const localData = table.props('localData')

    expect(localData).toHaveLength(1)
    expect(localData).toContainEqual(expect.objectContaining({
      roleKey: 'EXTERNAL_ROLE_1',
      roleLabel: '外部对接人1',
      CONTACT_INFO: '18888888888',
      INFO_TENDENCY_BASIS: '客户明确偏向西域',
    }))
  })

  it('does not render preset rows when no customer info is provided', () => {
    const wrapper = mount(CustomerInfoMatrix, {
      props: { modelValue: [] },
      global: { stubs: globalStubs },
    })

    const table = wrapper.findComponent({ name: 'CustomerInfoMatrixTable' })

    expect(table.props('localData')).toEqual([])
  })

  it('keeps preset role rows visible when they contain customer info', () => {
    const wrapper = mount(CustomerInfoMatrix, {
      props: {
        modelValue: [
          {
            roleKey: 'PROJECT_HIGHEST_DECISION_MAKER',
            NAME: '李四',
            POSITION: '1',
          },
        ],
      },
      global: { stubs: globalStubs },
    })

    const table = wrapper.findComponent({ name: 'CustomerInfoMatrixTable' })
    const localData = table.props('localData')

    expect(localData).toHaveLength(1)
    expect(localData[0]).toMatchObject({
      roleKey: 'PROJECT_HIGHEST_DECISION_MAKER',
      roleLabel: '项目最高决策人',
      NAME: '李四',
      POSITION: '1',
    })
  })

  it('keeps preset role rows visible when clear winner bid info is false', () => {
    const wrapper = mount(CustomerInfoMatrix, {
      props: {
        modelValue: [
          {
            roleKey: 'PROJECT_HIGHEST_DECISION_MAKER',
            INFO_CLEAR_WINNER_BID: false,
          },
        ],
      },
      global: { stubs: globalStubs },
    })

    const table = wrapper.findComponent({ name: 'CustomerInfoMatrixTable' })
    const localData = table.props('localData')

    expect(localData).toHaveLength(1)
    expect(localData[0]).toMatchObject({
      roleKey: 'PROJECT_HIGHEST_DECISION_MAKER',
      roleLabel: '项目最高决策人',
      INFO_CLEAR_WINNER_BID: false,
    })
  })

  it('keeps external role rows visible and emits them back on change', async () => {
    const wrapper = mount(CustomerInfoMatrix, {
      props: {
        modelValue: [
          {
            roleKey: 'EXTERNAL_ROLE_1',
            NAME: '张三',
            CONTACT_INFO: '18888888888',
          },
        ],
      },
      global: { stubs: globalStubs },
    })

    const table = wrapper.findComponent({ name: 'CustomerInfoMatrixTable' })
    const localData = table.props('localData')
    const externalRow = localData.find(row => row.roleKey === 'EXTERNAL_ROLE_1')

    expect(localData).toHaveLength(1)
    expect(externalRow).toMatchObject({
      roleKey: 'EXTERNAL_ROLE_1',
      roleLabel: '外部对接人1',
      NAME: '张三',
      CONTACT_INFO: '18888888888',
    })

    await table.trigger('click')

    const emitted = wrapper.emitted('update:modelValue')
    expect(emitted).toHaveLength(1)
    expect(emitted[0][0]).toEqual(expect.arrayContaining([
      expect.objectContaining({
        roleKey: 'EXTERNAL_ROLE_1',
        NAME: '张三',
        CONTACT_INFO: '18888888888',
      }),
    ]))
  })
})
