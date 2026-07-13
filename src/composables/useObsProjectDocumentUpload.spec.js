// Input: useObsProjectDocumentUpload composable
// Output: 测试套件
// Pos: src/composables/ - useObsProjectDocumentUpload 单元测试
// 防复发：验证 customUpload 不手动调用 onSuccess/onError（根因：双重调用导致 [ElUpload] file to be removed not found）
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { ref } from 'vue'

// Mock uploadDocument API（vi.mock 会被提升到顶部，不需要配对 import）
vi.mock('@/api/modules/projectDocuments.js', () => ({
  uploadDocument: vi.fn(),
}))

// Mock useObsUpload
vi.mock('@/composables/useObsUpload.js', () => ({
  useObsUpload: vi.fn(() => ({
    upload: vi.fn(),
    cancel: vi.fn(),
    reset: vi.fn(),
    uploading: { value: false },
    progress: { value: 0 },
    progressPercent: { value: 0 },
  })),
}))

describe('useObsProjectDocumentUpload', () => {
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

  /**
   * 辅助：动态加载模块以控制 VITE_OBS_ENABLED 求值时机
   */
  async function loadModule(obsEnabled) {
    vi.stubEnv('VITE_OBS_ENABLED', obsEnabled ? 'true' : 'false')
    vi.resetModules()
    // 重新 mock（vi.resetModules 后需要重新设置）
    vi.doMock('@/api/modules/projectDocuments.js', () => ({
      uploadDocument: vi.fn(),
    }))
    vi.doMock('@/composables/useObsUpload.js', () => ({
      useObsUpload: vi.fn(() => ({
        upload: vi.fn(),
        cancel: vi.fn(),
        reset: vi.fn(),
        uploading: { value: false },
        progress: { value: 0 },
        progressPercent: { value: 0 },
      })),
    }))
    const mod = await import('./useObsProjectDocumentUpload.js')
    const { uploadDocument: mockedUpload } = await import('@/api/modules/projectDocuments.js')
    const { useObsUpload: mockedUseObsUpload } = await import('@/composables/useObsUpload.js')
    return { ...mod, mockedUpload, mockedUseObsUpload }
  }

  function makeFile(name = 'test.pdf', size = 1024 * 1024) {
    const file = new File(['x'.repeat(size)], name, { type: 'application/pdf' })
    return file
  }

  function makeOptions(file) {
    return {
      file,
      data: { documentCategory: 'BID' },
      onSuccess: vi.fn(),
      onError: vi.fn(),
      onProgress: vi.fn(),
    }
  }

  describe('防复发：customUpload 不手动调用 onSuccess/onError', () => {
    it('成功时 return response，不调用 options.onSuccess', async () => {
      const { useObsProjectDocumentUpload, mockedUpload } = await loadModule(false)
      const { customUpload } = useObsProjectDocumentUpload(() => 100, {
        uploaderId: () => 1,
        uploaderName: () => 'test',
      })
      const file = makeFile()
      const options = makeOptions(file)
      const fakeResponse = { success: true, data: { id: 1 } }
      mockedUpload.mockResolvedValue(fakeResponse)

      // customUpload 应返回 Promise（response），而非手动调用 onSuccess
      const result = await customUpload(options)

      expect(result).toEqual(fakeResponse)
      // ⚠️ 防复发核心断言：onSuccess 不应被 customUpload 手动调用
      // el-upload 的 doUpload 会通过 request.then(options.onSuccess) 自动调用
      expect(options.onSuccess).not.toHaveBeenCalled()
      expect(options.onError).not.toHaveBeenCalled()
    })

    it('失败时 throw err，不调用 options.onError', async () => {
      const { useObsProjectDocumentUpload, mockedUpload } = await loadModule(false)
      const { customUpload } = useObsProjectDocumentUpload(() => 100, {
        uploaderId: () => 1,
        uploaderName: () => 'test',
      })
      const file = makeFile()
      const options = makeOptions(file)
      const fakeError = new Error('网络错误')
      mockedUpload.mockRejectedValue(fakeError)

      // customUpload 应 throw，而非手动调用 onError
      await expect(customUpload(options)).rejects.toThrow('网络错误')

      // ⚠️ 防复发核心断言：onError 不应被 customUpload 手动调用
      expect(options.onSuccess).not.toHaveBeenCalled()
      expect(options.onError).not.toHaveBeenCalled()
    })
  })

  describe('OBS 未启用：走 multipart', () => {
    it('formData 包含 file 字段，不包含 fileUrl', async () => {
      const { useObsProjectDocumentUpload, mockedUpload } = await loadModule(false)
      const { customUpload } = useObsProjectDocumentUpload(() => 100, {
        uploaderId: () => 1,
        uploaderName: () => 'test',
      })
      const file = makeFile('doc.pdf', 2 * 1024 * 1024)
      const options = makeOptions(file)
      mockedUpload.mockResolvedValue({ success: true })

      await customUpload(options)

      expect(mockedUpload).toHaveBeenCalledTimes(1)
      const [projectId, formData] = mockedUpload.mock.calls[0]
      expect(projectId).toBe(100)
      expect(formData.get('file')).toBeInstanceOf(File)
      expect(formData.get('fileUrl')).toBeNull()
      expect(formData.get('name')).toBe('doc.pdf')
      expect(formData.get('documentCategory')).toBe('BID')
      expect(formData.get('uploaderId')).toBe('1')
      expect(formData.get('uploaderName')).toBe('test')
    })
  })

  describe('OBS 启用 + OBS 成功：走 fileUrl 模式', () => {
    it('formData 包含 fileUrl，不包含 file', async () => {
      const { useObsProjectDocumentUpload, mockedUseObsUpload, mockedUpload } = await loadModule(true)
      // 让 OBS 直传成功
      const obsUploadInstance = {
        upload: vi.fn().mockResolvedValue({ uploadId: 'obs-123' }),
        cancel: vi.fn(),
        reset: vi.fn(),
        // customUpload 会 watch(obsUpload.progressPercent) 同步给 el-upload 进度条，mock 需提供真正的 ref
        progress: ref(0),
        progressPercent: ref(0),
      }
      mockedUseObsUpload.mockReturnValue(obsUploadInstance)

      const { customUpload } = useObsProjectDocumentUpload(() => 100, {
        uploaderId: () => 1,
        uploaderName: () => 'test',
      })
      const file = makeFile('big.pdf', 100 * 1024 * 1024)
      const options = makeOptions(file)
      mockedUpload.mockResolvedValue({ success: true, data: { id: 1 } })

      await customUpload(options)

      expect(obsUploadInstance.upload).toHaveBeenCalledWith(file)
      expect(mockedUpload).toHaveBeenCalledTimes(1)
      const [, formData] = mockedUpload.mock.calls[0]
      expect(formData.get('fileUrl')).toBe('obs-direct:obs-123')
      expect(formData.get('file')).toBeNull()
    })
  })

  describe('OBS 启用 + OBS 失败（如 CORS 403）：回退 multipart', () => {
    it('OBS 失败后回退到 multipart 模式', async () => {
      const { useObsProjectDocumentUpload, mockedUseObsUpload, mockedUpload } = await loadModule(true)
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
      // OBS 直传失败（模拟 CORS 403 场景）
      const obsUploadInstance = {
        upload: vi.fn().mockRejectedValue(new Error('OBS CORS 403 Forbidden')),
        cancel: vi.fn(),
        reset: vi.fn(),
        // customUpload 会 watch(obsUpload.progressPercent) 同步给 el-upload 进度条，mock 需提供真正的 ref
        progress: ref(0),
        progressPercent: ref(0),
      }
      mockedUseObsUpload.mockReturnValue(obsUploadInstance)

      const { customUpload } = useObsProjectDocumentUpload(() => 100, {
        uploaderId: () => 1,
        uploaderName: () => 'test',
      })
      const file = makeFile('big.pdf', 100 * 1024 * 1024)
      const options = makeOptions(file)
      mockedUpload.mockResolvedValue({ success: true, data: { id: 1 } })

      const result = await customUpload(options)

      // OBS 直传被调用但失败
      expect(obsUploadInstance.upload).toHaveBeenCalledWith(file)
      expect(warnSpy).toHaveBeenCalled()
      // 回退到 multipart
      expect(mockedUpload).toHaveBeenCalledTimes(1)
      const [, formData] = mockedUpload.mock.calls[0]
      expect(formData.get('file')).toBeInstanceOf(File)
      expect(formData.get('fileUrl')).toBeNull()
      // 返回 multipart 的结果
      expect(result).toEqual({ success: true, data: { id: 1 } })
      warnSpy.mockRestore()
    })

    it('OBS 失败 + multipart 也失败 → throw err（不调用 onError）', async () => {
      const { useObsProjectDocumentUpload, mockedUseObsUpload, mockedUpload } = await loadModule(true)
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
      const obsUploadInstance = {
        upload: vi.fn().mockRejectedValue(new Error('OBS CORS 403')),
        cancel: vi.fn(),
        reset: vi.fn(),
        // customUpload 会 watch(obsUpload.progressPercent) 同步给 el-upload 进度条，mock 需提供真正的 ref
        progress: ref(0),
        progressPercent: ref(0),
      }
      mockedUseObsUpload.mockReturnValue(obsUploadInstance)

      const { customUpload } = useObsProjectDocumentUpload(() => 100, {
        uploaderId: () => 1,
        uploaderName: () => 'test',
      })
      const file = makeFile()
      const options = makeOptions(file)
      const multipartError = new Error('413 Payload Too Large')
      mockedUpload.mockRejectedValue(multipartError)

      await expect(customUpload(options)).rejects.toThrow('413 Payload Too Large')

      // 防复发：不手动调用 onError
      expect(options.onError).not.toHaveBeenCalled()
      expect(options.onSuccess).not.toHaveBeenCalled()
      warnSpy.mockRestore()
    })
  })

  describe('进度同步：OBS 直传时 progressPercent 同步给 el-upload', () => {
    it('OBS 直传时 progressPercent 变化同步给 el-upload onProgress', async () => {
      const { useObsProjectDocumentUpload, mockedUseObsUpload, mockedUpload } = await loadModule(true)
      const progress = ref(0)
      const progressPercent = ref(0)
      const obsUploadInstance = {
        upload: vi.fn().mockImplementation(async () => {
          // 模拟 OBS 上传过程中进度变化
          progressPercent.value = 50
          await new Promise(resolve => setTimeout(resolve, 0))
          progressPercent.value = 100
          return { uploadId: 'obs-123' }
        }),
        cancel: vi.fn(),
        reset: vi.fn(),
        progress,
        progressPercent,
      }
      mockedUseObsUpload.mockReturnValue(obsUploadInstance)

      const { customUpload } = useObsProjectDocumentUpload(() => 100)
      const file = makeFile('big.pdf', 100 * 1024 * 1024)
      const options = makeOptions(file)
      mockedUpload.mockResolvedValue({ success: true })

      await customUpload(options)

      // onProgress 应被调用，且 percent 值与 progressPercent 同步
      expect(options.onProgress).toHaveBeenCalled()
      const calls = options.onProgress.mock.calls.map(call => call[0].percent)
      expect(calls).toContain(50)
      expect(calls).toContain(100)
    })

    it('OBS 未启用时不设置 progress watch（不调 onProgress）', async () => {
      const { useObsProjectDocumentUpload, mockedUpload } = await loadModule(false)
      const { customUpload } = useObsProjectDocumentUpload(() => 100)
      const file = makeFile()
      const options = makeOptions(file)
      mockedUpload.mockResolvedValue({ success: true })

      await customUpload(options)

      expect(options.onProgress).not.toHaveBeenCalled()
    })
  })

  describe('OBS 回退进度重置', () => {
    it('OBS 失败回退 multipart 时重置 progress 为 0（避免进度条停在中间值）', async () => {
      const { useObsProjectDocumentUpload, mockedUseObsUpload, mockedUpload } = await loadModule(true)
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(() => {})
      const progress = ref(0)
      const progressPercent = ref(0)
      const obsUploadInstance = {
        upload: vi.fn().mockImplementation(async () => {
          // 模拟 OBS 上传到 50% 后失败
          progress.value = 0.5
          progressPercent.value = 50
          throw new Error('OBS CORS 403')
        }),
        cancel: vi.fn(),
        reset: vi.fn(),
        progress,
        progressPercent,
      }
      mockedUseObsUpload.mockReturnValue(obsUploadInstance)

      const { customUpload } = useObsProjectDocumentUpload(() => 100)
      const file = makeFile()
      const options = makeOptions(file)
      mockedUpload.mockResolvedValue({ success: true, data: { id: 1 } })

      await customUpload(options)

      // OBS 失败后 progress 应被重置为 0
      expect(progress.value).toBe(0)
      warnSpy.mockRestore()
    })
  })

  describe('customUpload 返回 Promise（el-upload 兼容性）', () => {
    it('customUpload 返回值是 Promise', async () => {
      const { useObsProjectDocumentUpload, mockedUpload } = await loadModule(false)
      const { customUpload } = useObsProjectDocumentUpload(() => 100)
      const file = makeFile()
      const options = makeOptions(file)
      mockedUpload.mockResolvedValue({ success: true })

      const result = customUpload(options)

      // el-upload 的 doUpload 会检查 request instanceof Promise
      expect(result).toBeInstanceOf(Promise)
      await result
    })
  })

  describe('projectIdRef 支持多种形式', () => {
    it('支持 getter 函数', async () => {
      const { useObsProjectDocumentUpload, mockedUpload } = await loadModule(false)
      const { customUpload } = useObsProjectDocumentUpload(() => 200)
      const file = makeFile()
      const options = makeOptions(file)
      mockedUpload.mockResolvedValue({ success: true })

      await customUpload(options)

      expect(mockedUpload.mock.calls[0][0]).toBe(200)
    })

    it('支持 ref 对象', async () => {
      const { useObsProjectDocumentUpload, mockedUpload } = await loadModule(false)
      const { customUpload } = useObsProjectDocumentUpload({ value: 300 })
      const file = makeFile()
      const options = makeOptions(file)
      mockedUpload.mockResolvedValue({ success: true })

      await customUpload(options)

      expect(mockedUpload.mock.calls[0][0]).toBe(300)
    })
  })
})
