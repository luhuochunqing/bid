import { ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'
import { useProjectDetailProjectOps } from './useProjectDetailProjectOps.js'
import { ApiCode } from '@/constants/apiCode'

describe('useProjectDetailProjectOps', () => {
  it('handleUploadSuccess 在 code=SUCCESS 时写入 noticeFile 并提示成功', () => {
    const success = vi.fn()
    const error = vi.fn()
    const context = {
      resultForm: ref({ noticeFile: '' }),
      message: { success, error },
    }
    const { handleUploadSuccess } = useProjectDetailProjectOps(context)
    handleUploadSuccess({ code: ApiCode.SUCCESS, data: { url: '/files/a.pdf' } })
    expect(context.resultForm.value.noticeFile).toBe('/files/a.pdf')
    expect(success).toHaveBeenCalledWith('上传成功')
    expect(error).not.toHaveBeenCalled()
  })

  it('handleUploadSuccess 在 code!=SUCCESS 时提示错误', () => {
    const success = vi.fn()
    const error = vi.fn()
    const context = {
      resultForm: ref({ noticeFile: '' }),
      message: { success, error },
    }
    const { handleUploadSuccess } = useProjectDetailProjectOps(context)
    handleUploadSuccess({ code: 500, msg: '失败' })
    expect(error).toHaveBeenCalledWith('失败')
    expect(context.resultForm.value.noticeFile).toBe('')
    expect(success).not.toHaveBeenCalled()
  })
})
