import { ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiMocks = vi.hoisted(() => ({
  getProjectQualityResult: vi.fn(),
  runProjectQualityCheck: vi.fn(),
  adoptQualitySuggestion: vi.fn(),
  ignoreQualitySuggestion: vi.fn(),
}))

vi.mock('@/api/modules/ai/quality.js', () => ({
  projectQualityApi: apiMocks,
}))

import { useProjectDetailQuality } from './useProjectDetailQuality.js'

async function flushPromises() {
  await Promise.resolve()
  await Promise.resolve()
  await new Promise((resolve) => setTimeout(resolve, 0))
}

describe('useProjectDetailQuality', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('loads the latest quality result when the composable is created', async () => {
    apiMocks.getProjectQualityResult.mockResolvedValue({
      data: {
        id: 18,
        projectId: 12,
        status: 'COMPLETED',
        empty: false,
        issues: [
          {
            id: 91,
            type: 'grammar',
            original: '原文',
            suggestion: '建议',
            location: '摘要',
            ignored: false,
            adopted: false,
          },
        ],
        errors: [
          {
            id: 91,
            type: 'grammar',
            original: '原文',
            suggestion: '建议',
            location: '摘要',
            ignored: false,
            adopted: false,
          },
        ],
        suggestions: [
          {
            id: 91,
            type: 'grammar',
            original: '原文',
            suggestion: '建议',
            location: '摘要',
            ignored: false,
            adopted: false,
          },
        ],
      },
    })

    const message = {
      success: vi.fn(),
      warning: vi.fn(),
      error: vi.fn(),
    }

    const quality = useProjectDetailQuality({
      route: { params: { id: '12' } },
      isDemoMode: ref(false),
      isApiProject: ref(true),
      project: ref({ aiCheck: {} }),
      message,
    })

    await flushPromises()

    expect(apiMocks.getProjectQualityResult).toHaveBeenCalledWith('12')
    expect(quality.qualityResult.value).toMatchObject({
      id: 18,
      status: 'COMPLETED',
      errors: [
        {
          id: 91,
          original: '原文',
          suggestion: '建议',
          location: '摘要',
        },
      ],
    })
  })

  it('keeps empty-state canonical and supports adopt/ignore updates', async () => {
    apiMocks.getProjectQualityResult.mockResolvedValue({ data: null })
    apiMocks.runProjectQualityCheck.mockResolvedValue({
      data: {
        id: 22,
        projectId: 12,
        status: 'EMPTY',
        empty: true,
        issues: [],
        errors: [],
        suggestions: [],
      },
    })
    apiMocks.adoptQualitySuggestion.mockResolvedValue({
      data: {
        id: 22,
        projectId: 12,
        status: 'COMPLETED',
        empty: false,
        issues: [
          {
            id: 101,
            type: 'grammar',
            original: '原文',
            suggestion: '建议',
            location: '摘要',
            adopted: true,
            ignored: false,
          },
        ],
        errors: [],
        suggestions: [],
      },
    })
    apiMocks.ignoreQualitySuggestion.mockResolvedValue({
      data: {
        id: 22,
        projectId: 12,
        status: 'COMPLETED',
        empty: false,
        issues: [
          {
            id: 102,
            type: 'format',
            original: '原文2',
            suggestion: '建议2',
            location: '正文',
            adopted: false,
            ignored: true,
          },
        ],
        errors: [],
        suggestions: [],
      },
    })

    const message = {
      success: vi.fn(),
      warning: vi.fn(),
      error: vi.fn(),
    }

    const quality = useProjectDetailQuality({
      route: { params: { id: '12' } },
      isDemoMode: ref(false),
      isApiProject: ref(true),
      project: ref({ aiCheck: {} }),
      message,
    })

    await flushPromises()

    await quality.runQualityCheck()
    expect(apiMocks.runProjectQualityCheck).toHaveBeenCalledWith('12')
    expect(quality.qualityResult.value).toMatchObject({
      status: 'EMPTY',
      empty: true,
      errors: [],
    })
    expect(message.success).toHaveBeenCalledWith('已完成检查，当前无可检查文档')

    quality.qualityResult.value = {
      id: 22,
      projectId: 12,
      status: 'COMPLETED',
      empty: false,
      errors: [
        {
          id: 101,
          type: 'grammar',
          original: '原文',
          suggestion: '建议',
          location: '摘要',
        },
      ],
      suggestions: [
        {
          id: 101,
          type: 'grammar',
          original: '原文',
          suggestion: '建议',
          location: '摘要',
        },
      ],
    }

    await quality.handleAdoptSuggestion(0)
    expect(apiMocks.adoptQualitySuggestion).toHaveBeenCalledWith('12', 22, 101)
    expect(message.success).toHaveBeenCalledWith('建议已采纳')

    quality.qualityResult.value = {
      id: 22,
      projectId: 12,
      status: 'COMPLETED',
      empty: false,
      errors: [
        {
          id: 102,
          type: 'format',
          original: '原文2',
          suggestion: '建议2',
          location: '正文',
        },
      ],
      suggestions: [
        {
          id: 102,
          type: 'format',
          original: '原文2',
          suggestion: '建议2',
          location: '正文',
        },
      ],
    }

    await quality.handleIgnoreSuggestion(0)
    expect(apiMocks.ignoreQualitySuggestion).toHaveBeenCalledWith('12', 22, 102)
    expect(message.success).toHaveBeenCalledWith('问题已忽略')
  })
})
