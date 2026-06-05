import { describe, expect, it, vi, beforeEach } from 'vitest'
import { ref } from 'vue'
import { useProjectDetailAI } from './useProjectDetailAI.js'
import { complianceApi, scoreAnalysisApi } from '@/api/modules/ai.js'

vi.mock('@/api/modules/ai.js', () => ({
  complianceApi: {
    getCheckResult: vi.fn(),
  },
  scoreAnalysisApi: {
    getAnalysis: vi.fn(),
    generatePreview: vi.fn(),
  },
}))

function createContext() {
  return {
    route: { params: { id: '108' } },
    isDemoMode: false,
    isApiProject: ref(true),
    project: ref({
      id: 108,
      tenderId: 140,
      name: '电子商城电商供应商引入项目',
      industry: '电子商务',
      budget: null,
      tagsJson: '["框架协议","MRO工业品"]',
    }),
    message: {
      success: vi.fn(),
      warning: vi.fn(),
      error: vi.fn(),
      info: vi.fn(),
    },
    state: {
      assistantPanelVisible: ref(false),
      showCompetitionIntel: ref(false),
      showROIAnalysis: ref(false),
      showComplianceCheck: ref(false),
      showVersionControl: ref(false),
      showCollaboration: ref(false),
      showAutoTasks: ref(false),
      showMobileCard: ref(false),
    },
  }
}

describe('useProjectDetailAI', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('falls back to score preview when a new project has no saved score analysis', async () => {
    complianceApi.getCheckResult.mockResolvedValue({ success: true, data: [] })
    scoreAnalysisApi.getAnalysis.mockResolvedValue({ success: false, msg: '未找到项目的评分分析' })
    scoreAnalysisApi.generatePreview.mockResolvedValue({
      success: true,
      data: {
        aiSummary: {
          winScore: 62,
          suggestions: ['补充响应材料'],
        },
        scoreAnalysis: {
          scoreCategories: [
            { name: '技术', percentage: 70 },
            { name: '商务', percentage: 60 },
          ],
        },
      },
    })

    const context = createContext()
    const ai = useProjectDetailAI(context)

    await ai.runAICheck()

    expect(scoreAnalysisApi.generatePreview).toHaveBeenCalledWith({
      projectId: '108',
      tenderId: 140,
      projectName: '电子商城电商供应商引入项目',
      industry: '电子商务',
      budget: 0,
      tags: ['框架协议', 'MRO工业品'],
    })
    expect(ai.aiResult.value.score.total).toBe(62)
    expect(context.message.success).toHaveBeenCalledWith('AI检查完成')
    expect(context.message.error).not.toHaveBeenCalled()
  })
})
