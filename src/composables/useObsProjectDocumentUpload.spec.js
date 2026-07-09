// Input: useObsProjectDocumentUpload composable
// Output: 测试套件
// Pos: src/composables/ - useObsProjectDocumentUpload 单元测试
import { describe, it, expect, vi, beforeEach } from 'vitest'

vi.mock('@/composables/useObsUpload.js', () => ({
  useObsUpload: vi.fn(() => ({ upload: vi.fn() })),
}))

vi.mock('@/composables/useObsUploadFallback.js', () => ({
  tryObsDirectUpload: vi.fn(),
  isObsEnabled: true,
}))

vi.mock('@/api/modules/projectDocuments.js', () => ({
  uploadDocument: vi.fn(),
}))

const { tryObsDirectUpload } = await import('@/composables/useObsUploadFallback.js')
const { uploadDocument } = await import('@/api/modules/projectDocuments.js')
const { useObsProjectDocumentUpload } = await import('./useObsProjectDocumentUpload.js')

function makeFile(name = 'bid.pdf', type = 'application/pdf') {
  return new File(['x'], name, { type })
}

function makeOptions(overrides = {}) {
  return {
    file: makeFile(),
    data: { documentCategory: 'BID' },
    onSuccess: vi.fn(),
    onError: vi.fn(),
    ...overrides,
  }
}

describe('useObsProjectDocumentUpload', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('OBS 直传成功时 formData 包含 fileUrl 不包含 file，并调用 onSuccess', async () => {
    tryObsDirectUpload.mockResolvedValue('obs-direct:up-123')
    uploadDocument.mockResolvedValue({ data: { id: 42 } })

    const { customUpload } = useObsProjectDocumentUpload(() => 188, {
      uploaderId: () => 7,
      uploaderName: () => '张三',
    })

    await customUpload(makeOptions())

    const fd = uploadDocument.mock.calls[0][1]
    expect(fd.get('fileUrl')).toBe('obs-direct:up-123')
    expect(fd.get('file')).toBeNull()
    expect(fd.get('name')).toBe('bid.pdf')
    expect(fd.get('fileType')).toBe('application/pdf')
    expect(fd.get('documentCategory')).toBe('BID')
    expect(fd.get('uploaderId')).toBe('7')
    expect(fd.get('uploaderName')).toBe('张三')
  })

  it('OBS 直传失败时回退 multipart，formData 包含 file 不包含 fileUrl', async () => {
    tryObsDirectUpload.mockResolvedValue(null)
    uploadDocument.mockResolvedValue({ data: { id: 43 } })

    const { customUpload } = useObsProjectDocumentUpload(() => 189)
    const onSuccess = vi.fn()

    await customUpload(makeOptions({ onSuccess }))

    const fd = uploadDocument.mock.calls[0][1]
    expect(fd.get('fileUrl')).toBeNull()
    expect(fd.get('file')).toBeInstanceOf(File)
    expect(onSuccess).toHaveBeenCalledWith({ data: { id: 43 } })
  })

  it('uploadDocument 抛出时调用 onError', async () => {
    tryObsDirectUpload.mockResolvedValue('obs-direct:up-err')
    const boom = new Error('network down')
    uploadDocument.mockRejectedValue(boom)

    const { customUpload } = useObsProjectDocumentUpload(() => 190)
    const onError = vi.fn()

    await customUpload(makeOptions({ onError }))

    expect(onError).toHaveBeenCalledWith(boom)
  })

  it('uploaderId/uploaderName 为空时不写入 formData', async () => {
    tryObsDirectUpload.mockResolvedValue(null)
    uploadDocument.mockResolvedValue({ data: { id: 1 } })

    const { customUpload } = useObsProjectDocumentUpload(() => 1)

    await customUpload(makeOptions())

    const fd = uploadDocument.mock.calls[0][1]
    expect(fd.get('uploaderId')).toBeNull()
    expect(fd.get('uploaderName')).toBeNull()
  })

  it('projectIdRef 支持 ref 对象', async () => {
    tryObsDirectUpload.mockResolvedValue(null)
    uploadDocument.mockResolvedValue({ data: { id: 1 } })

    const { ref } = await import('vue')
    const { customUpload } = useObsProjectDocumentUpload(ref(777))

    await customUpload(makeOptions())

    expect(uploadDocument.mock.calls[0][0]).toBe(777)
  })

  it('extraData 中 linkedEntityType/linkedEntityId 被传递', async () => {
    tryObsDirectUpload.mockResolvedValue('obs-direct:x')
    uploadDocument.mockResolvedValue({ data: { id: 1 } })

    const { customUpload } = useObsProjectDocumentUpload(() => 1)

    await customUpload(makeOptions({
      data: { documentCategory: 'OTHER', linkedEntityType: 'TASK', linkedEntityId: 'T-5' },
    }))

    const fd = uploadDocument.mock.calls[0][1]
    expect(fd.get('linkedEntityType')).toBe('TASK')
    expect(fd.get('linkedEntityId')).toBe('T-5')
  })
})
