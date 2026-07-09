// Input: useObsUploadFallback composable
// Output: 测试套件
// Pos: src/composables/ - useObsUploadFallback 单元测试
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

describe('useObsUploadFallback', () => {
  let originalObsEnabled

  beforeEach(() => {
    originalObsEnabled = import.meta.env.VITE_OBS_ENABLED
    vi.clearAllMocks()
  })

  afterEach(() => {
    if (originalObsEnabled !== undefined) {
      vi.stubEnv('VITE_OBS_ENABLED', originalObsEnabled)
    } else {
      vi.unstubAllEnvs()
    }
    vi.resetModules()
  })

  // 动态加载模块，便于控制 VITE_OBS_ENABLED 的求值时机
  async function loadFallback(obsEnabled) {
    vi.stubEnv('VITE_OBS_ENABLED', obsEnabled ? 'true' : 'false')
    vi.resetModules()
    return await import('./useObsUploadFallback.js')
  }

  describe('isObsEnabled', () => {
    it('returns true when VITE_OBS_ENABLED=true', async () => {
      const { isObsEnabled } = await loadFallback(true)
      expect(isObsEnabled).toBe(true)
    })

    it('returns false when VITE_OBS_ENABLED is not "true"', async () => {
      const { isObsEnabled } = await loadFallback(false)
      expect(isObsEnabled).toBe(false)
    })
  })

  describe('tryObsDirectUpload', () => {
    it('returns null without calling upload when OBS disabled', async () => {
      const { tryObsDirectUpload } = await loadFallback(false)
      const obsUpload = { upload: vi.fn() }
      const file = new File(['x'], 't.txt', { type: 'text/plain' })

      const result = await tryObsDirectUpload(obsUpload, file)

      expect(result).toBeNull()
      expect(obsUpload.upload).not.toHaveBeenCalled()
    })

    it('returns obs-direct:{uploadId} when upload succeeds', async () => {
      const { tryObsDirectUpload } = await loadFallback(true)
      const obsUpload = { upload: vi.fn().mockResolvedValue({ uploadId: 'up-123' }) }
      const file = new File(['x'], 't.txt', { type: 'text/plain' })

      const result = await tryObsDirectUpload(obsUpload, file)

      expect(result).toBe('obs-direct:up-123')
      expect(obsUpload.upload).toHaveBeenCalledWith(file)
    })

    it('returns null and warns when upload throws', async () => {
      const { tryObsDirectUpload } = await loadFallback(true)
      const obsUpload = { upload: vi.fn().mockRejectedValue(new Error('boom')) }
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
      const file = new File(['x'], 't.txt', { type: 'text/plain' })

      const result = await tryObsDirectUpload(obsUpload, file)

      expect(result).toBeNull()
      expect(warnSpy).toHaveBeenCalled()
      const warnArg = warnSpy.mock.calls[0][1]
      expect(String(warnArg)).toContain('boom')
    })

    it('returns null when upload resolves without uploadId', async () => {
      const { tryObsDirectUpload } = await loadFallback(true)
      const obsUpload = { upload: vi.fn().mockResolvedValue({}) }
      const file = new File(['x'], 't.txt', { type: 'text/plain' })

      const result = await tryObsDirectUpload(obsUpload, file)

      expect(result).toBe('obs-direct:undefined')
    })
  })

  describe('buildUploadFormData', () => {
    it('sets fileUrl/fileName/fileType when obsFileUrl provided', async () => {
      const { buildUploadFormData } = await loadFallback(false)
      const file = new File(['x'], 'bid.pdf', { type: 'application/pdf' })

      const fd = buildUploadFormData(file, 'obs-direct:abc', '招标文件')

      expect(fd.get('fileUrl')).toBe('obs-direct:abc')
      expect(fd.get('fileName')).toBe('bid.pdf')
      expect(fd.get('fileType')).toBe('application/pdf')
      expect(fd.get('file')).toBeNull()
    })

    it('sets file field with filename when obsFileUrl is null', async () => {
      const { buildUploadFormData } = await loadFallback(false)
      const file = new File(['x'], 'bid.pdf', { type: 'application/pdf' })

      const fd = buildUploadFormData(file, null, '招标文件')

      const storedFile = fd.get('file')
      expect(storedFile).toBeInstanceOf(File)
      expect(storedFile.name).toBe('bid.pdf')
      expect(fd.get('fileUrl')).toBeNull()
      // multipart 模式下 fileName 作为 File 的 filename 写入，不作为独立字段
      expect(fd.get('fileName')).toBeNull()
    })

    it('uses fallback name as filename when file has empty name (multipart)', async () => {
      const { buildUploadFormData } = await loadFallback(false)
      const file = new File(['x'], '')

      const fd = buildUploadFormData(file, null, '招标文件')

      const storedFile = fd.get('file')
      expect(storedFile).toBeInstanceOf(File)
      expect(storedFile.name).toBe('招标文件')
    })

    it('uses fallback name when file has empty name', async () => {
      const { buildUploadFormData } = await loadFallback(false)
      const file = new File(['x'], '')

      const fd = buildUploadFormData(file, 'obs-direct:abc', '招标文件')

      expect(fd.get('fileName')).toBe('招标文件')
    })

    it('uses octet-stream when file has no type', async () => {
      const { buildUploadFormData } = await loadFallback(false)
      const file = { name: 'bid.pdf' }

      const fd = buildUploadFormData(file, 'obs-direct:abc', '招标文件')

      expect(fd.get('fileType')).toBe('application/octet-stream')
    })
  })

  describe('callApiWithObsFallback', () => {
    it('calls apiCall with formData and returns its result', async () => {
      const { callApiWithObsFallback } = await loadFallback(false)
      const apiCall = vi.fn().mockResolvedValue({ success: true })
      const file = new File(['x'], 'b.pdf', { type: 'application/pdf' })

      const result = await callApiWithObsFallback(file, null, apiCall, '招标文件')

      expect(apiCall).toHaveBeenCalledTimes(1)
      const fd = apiCall.mock.calls[0][0]
      expect(fd.get('file')).toBeInstanceOf(File)
      expect(result).toEqual({ success: true })
    })

    it('retries with multipart when apiCall throws 415 and obsFileUrl is set', async () => {
      const { callApiWithObsFallback } = await loadFallback(false)
      const error415 = Object.assign(new Error('Unsupported Media Type'), {
        response: { status: 415 },
      })
      const apiCall = vi.fn()
        .mockRejectedValueOnce(error415)
        .mockResolvedValueOnce({ success: true, retried: true })
      const file = new File(['x'], 'b.pdf', { type: 'application/pdf' })

      const result = await callApiWithObsFallback(file, 'obs-direct:abc', apiCall, '招标文件')

      expect(apiCall).toHaveBeenCalledTimes(2)
      const fd1 = apiCall.mock.calls[0][0]
      expect(fd1.get('fileUrl')).toBe('obs-direct:abc')
      const fd2 = apiCall.mock.calls[1][0]
      expect(fd2.get('file')).toBeInstanceOf(File)
      expect(fd2.get('fileUrl')).toBeNull()
      expect(result).toEqual({ success: true, retried: true })
    })

    it('does not retry when apiCall throws non-415 error', async () => {
      const { callApiWithObsFallback } = await loadFallback(false)
      const error500 = Object.assign(new Error('Server Error'), {
        response: { status: 500 },
      })
      const apiCall = vi.fn().mockRejectedValue(error500)
      const file = new File(['x'], 'b.pdf', { type: 'application/pdf' })

      await expect(callApiWithObsFallback(file, 'obs-direct:abc', apiCall, '招标文件'))
        .rejects.toThrow('Server Error')

      expect(apiCall).toHaveBeenCalledTimes(1)
    })

    it('does not retry when obsFileUrl is null even on 415', async () => {
      const { callApiWithObsFallback } = await loadFallback(false)
      const error415 = Object.assign(new Error('Unsupported Media Type'), {
        response: { status: 415 },
      })
      const apiCall = vi.fn().mockRejectedValue(error415)
      const file = new File(['x'], 'b.pdf', { type: 'application/pdf' })

      await expect(callApiWithObsFallback(file, null, apiCall, '招标文件'))
        .rejects.toThrow('Unsupported Media Type')

      expect(apiCall).toHaveBeenCalledTimes(1)
    })

    it('rethrows when retry also fails', async () => {
      const { callApiWithObsFallback } = await loadFallback(false)
      const error415 = Object.assign(new Error('Unsupported Media Type'), {
        response: { status: 415 },
      })
      const error500 = Object.assign(new Error('Server Error'), {
        response: { status: 500 },
      })
      const apiCall = vi.fn()
        .mockRejectedValueOnce(error415)
        .mockRejectedValueOnce(error500)
      const file = new File(['x'], 'b.pdf', { type: 'application/pdf' })

      await expect(callApiWithObsFallback(file, 'obs-direct:abc', apiCall, '招标文件'))
        .rejects.toThrow('Server Error')

      expect(apiCall).toHaveBeenCalledTimes(2)
    })
  })
})
