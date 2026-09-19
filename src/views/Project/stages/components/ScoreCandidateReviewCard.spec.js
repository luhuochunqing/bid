import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ScoreCandidateReviewCard from './ScoreCandidateReviewCard.vue'

describe('ScoreCandidateReviewCard.vue', () => {
  const candidateFacts = [
    { factId: 'f1', code: '1', dimension: '商务部分', weight: 6, sourceText: '业绩要求' },
    { factId: 'f2', code: '2', dimension: '财务状况', weight: 3, sourceText: '财务指标' },
  ]

  it('renders candidate table with facts and displays dimension and weight', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: {
        candidateFacts,
        candidateTotalWeight: 9,
        isAdoptingCandidates: false,
      },
      global: {
        stubs: {
          'el-alert': {
            template: '<div class="el-alert-mock"><slot name="title" />{{ title }}</div>',
            props: ['title'],
          },
          'el-button': {
            template: '<button class="el-button-mock"><slot /></button>',
          },
        },
      },
    })

    const rows = wrapper.findAll('.candidate-table tbody tr')
    expect(rows).toHaveLength(2)
    expect(rows[0].find('.col-dim').text()).toBe('商务部分')
    expect(rows[0].find('.col-weight').text()).toBe('6 分')
    expect(rows[1].find('.col-dim').text()).toBe('财务状况')
    expect(rows[1].find('.col-weight').text()).toBe('3 分')
  })

  it('renders warning alert when candidateTotalWeight is not 100', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: {
        candidateFacts,
        candidateTotalWeight: 9,
        isAdoptingCandidates: false,
      },
      global: {
        stubs: {
          'el-alert': {
            template: '<div class="el-alert-mock">{{ title }}</div>',
            props: ['title'],
          },
          'el-button': true,
        },
      },
    })

    const alert = wrapper.find('.el-alert-mock')
    expect(alert.exists()).toBe(true)
    expect(alert.text()).toContain('9 分（非 100 分满分制）')
  })

  it('does not render warning alert when candidateTotalWeight is 100', () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: {
        candidateFacts,
        candidateTotalWeight: 100,
        isAdoptingCandidates: false,
      },
      global: {
        stubs: {
          'el-alert': {
            template: '<div class="el-alert-mock">{{ title }}</div>',
            props: ['title'],
          },
          'el-button': true,
        },
      },
    })

    expect(wrapper.find('.el-alert-mock').exists()).toBe(false)
  })

  it('emits adopt when clicking adopt button', async () => {
    const wrapper = mount(ScoreCandidateReviewCard, {
      props: {
        candidateFacts,
        candidateTotalWeight: 100,
        isAdoptingCandidates: false,
      },
      global: {
        stubs: {
          'el-button': {
            template: '<button class="el-button-mock"><slot /></button>',
          },
        },
      },
    })

    const adoptBtn = wrapper.find('[data-test="btn-adopt-candidates"]')
    await adoptBtn.trigger('click')
    expect(wrapper.emitted('adopt')).toHaveLength(1)
  })
})
