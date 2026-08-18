import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import AICaseReuseSectionModal from './AICaseReuseSectionModal.vue'
import { caseRecommendationApi } from '@/api/modules/caseRecommendation'

vi.mock('@/api/modules/caseRecommendation', () => ({
  caseRecommendationApi: {
    reuseCase: vi.fn()
  }
}))

describe('AICaseReuseSectionModal.vue', () => {
  const mockCase = {
    id: 101,
    scoringPointTitle: '高可用架构方案',
    responseText: '应答正文内容...'
  }

  beforeEach(() => {
    vi.clearAllMocks()
    caseRecommendationApi.reuseCase.mockResolvedValue({
      caseId: 101,
      newReuseCount: 4
    })
  })

  it('renders modal and submits reuse request with default chapter and append mode', async () => {
    const wrapper = mount(AICaseReuseSectionModal, {
      props: {
        modelValue: true,
        caseData: mockCase,
        projectId: 123,
        scoreItemId: 1,
        defaultChapter: '第3章 技术方案'
      },
      global: {
        stubs: {
          'el-dialog': {
            template: '<div class="el-dialog-stub"><slot/><slot name="footer"/></div>'
          },
          'el-form': true,
          'el-form-item': true,
          'el-cascader': true,
          'el-input': true,
          'el-radio-group': true,
          'el-radio-button': true,
          'el-button': true
        }
      }
    })

    await wrapper.vm.handleConfirmReuse()
    await flushPromises()

    expect(caseRecommendationApi.reuseCase).toHaveBeenCalledWith(101, {
      projectId: 123,
      scoreItemId: 1,
      targetChapter: '第3章 技术方案',
      reuseMode: 'APPEND',
      referenceTarget: '第3章 技术方案',
      referenceContext: '从案例库复用【高可用架构方案】到【第3章 技术方案】'
    })

    expect(wrapper.emitted('reuse-success')).toBeTruthy()
  })
})
