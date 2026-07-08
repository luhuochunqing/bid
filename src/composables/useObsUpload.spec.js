// Input: useObsUpload composable
// Output: 测试套件
// Pos: src/composables/ - useObsUpload 单元测试
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useObsUpload } from './useObsUpload.js'

// Mock API
vi.mock('../api/files.js', () => ({
  requestUploadToken: vi.fn().mockResolvedValue({
    uploadId: 'test-upload-123',
    accessKey: 'ak',
    secretKey: 'sk',
    securityToken: 'st',
    bucket: 'test-bucket',
    objectKey: 'bids/test.txt',
    endpoint: 'https://obs.example.com',
  }),
  notifyUploadCompleted: vi.fn().mockResolvedValue({}),
}))

// Mock OBS SDK
vi.mock('esdk-obs-browserjs', () => ({
  default: class MockObsClient {
    async uploadFile({ ProgressCallback }) {
      if (ProgressCallback) {
        ProgressCallback(50, 100)
        ProgressCallback(100, 100)
      }
      return {
        CommonMsg: { Status: 200, Message: 'OK' },
        InterfaceResult: { ETag: 'test-etag' },
      }
    }
    close() {}
  },
}))

describe('useObsUpload', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('initial state is idle', () => {
    const { uploading, progress, progressPercent, currentFile, uploadId, error, completedFile } =
      useObsUpload()

    expect(uploading.value).toBe(false)
    expect(progress.value).toBe(0)
    expect(progressPercent.value).toBe(0)
    expect(currentFile.value).toBeNull()
    expect(uploadId.value).toBeNull()
    expect(error.value).toBeNull()
    expect(completedFile.value).toBeNull()
  })

  it('uploads a file successfully', async () => {
    const { upload, completedFile, progressPercent, uploading } = useObsUpload()

    const file = new File(['hello'], 'test.txt', { type: 'text/plain' })
    const result = await upload(file)

    expect(result.uploadId).toBe('test-upload-123')
    expect(result.objectKey).toBe('bids/test.txt')
    expect(result.bucket).toBe('test-bucket')
    expect(result.fileName).toBe('test.txt')
    expect(result.fileSize).toBe(file.size)
    expect(completedFile.value).toEqual(result)
    expect(progressPercent.value).toBe(100)
    expect(uploading.value).toBe(false)
  })

  it('rejects when already uploading', async () => {
    const { upload } = useObsUpload()
    const file = new File(['hello'], 'test.txt')

    // Start first upload (not awaited)
    upload(file)

    // Try second upload
    await expect(upload(file)).rejects.toThrow('已有上传任务进行中')
  })

  it('can be reset', () => {
    const { reset, uploading, progress, currentFile, uploadId, error, completedFile } =
      useObsUpload()

    reset()

    expect(uploading.value).toBe(false)
    expect(progress.value).toBe(0)
    expect(currentFile.value).toBeNull()
    expect(uploadId.value).toBeNull()
    expect(error.value).toBeNull()
    expect(completedFile.value).toBeNull()
  })
})
