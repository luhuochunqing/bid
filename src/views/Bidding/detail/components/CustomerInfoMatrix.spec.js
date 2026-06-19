import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CustomerInfoMatrix from './CustomerInfoMatrix.vue'
import { CUSTOMER_INFO_ROWS } from './customerInfoMatrixConfig.js'

const globalStubs = {
  CustomerInfoMatrixTable: {
    name: 'CustomerInfoMatrixTable',
    props: ['localData', 'editableColumns', 'disabled'],
    emits: ['data-change'],
    template: '<button class="matrix-table-stub" @click="$emit(\'data-change\')"></button>',
  },
}

describe('CustomerInfoMatrix', () => {
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

    expect(localData).toHaveLength(CUSTOMER_INFO_ROWS.length + 1)
    expect(externalRow).toMatchObject({
      roleKey: 'EXTERNAL_ROLE_1',
      roleLabel: 'EXTERNAL_ROLE_1',
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
