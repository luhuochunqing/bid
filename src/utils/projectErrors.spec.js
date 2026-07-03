// Input: projectErrors.js
// Output: unit tests for ProjectLoadError / PROJECT_LOAD_ERROR_TYPE
// Pos: src/utils/ - Utility test
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
import { describe, it, expect } from 'vitest'
import { ProjectLoadError, PROJECT_LOAD_ERROR_TYPE } from './projectErrors.js'

describe('PROJECT_LOAD_ERROR_TYPE', () => {
  it('导出 3 种错误类型枚举', () => {
    expect(PROJECT_LOAD_ERROR_TYPE.NO_PERMISSION).toBe('no-permission')
    expect(PROJECT_LOAD_ERROR_TYPE.NOT_FOUND).toBe('not-found')
    expect(PROJECT_LOAD_ERROR_TYPE.NETWORK_ERROR).toBe('network-error')
  })
})

describe('ProjectLoadError', () => {
  it('构造函数正确设置 name / errorType / message / cause', () => {
    const cause = new Error('original')
    const error = new ProjectLoadError(
      PROJECT_LOAD_ERROR_TYPE.NO_PERMISSION,
      '无权限访问该项目',
      cause
    )
    expect(error).toBeInstanceOf(Error)
    expect(error.name).toBe('ProjectLoadError')
    expect(error.errorType).toBe('no-permission')
    expect(error.message).toBe('无权限访问该项目')
    expect(error.cause).toBe(cause)
  })

  it('cause 参数可选', () => {
    const error = new ProjectLoadError(PROJECT_LOAD_ERROR_TYPE.NOT_FOUND, '项目不存在')
    expect(error.cause).toBeUndefined()
  })

  it('instanceof Error 为 true，可被 try-catch 捕获', () => {
    let caught = null
    try {
      throw new ProjectLoadError(PROJECT_LOAD_ERROR_TYPE.NETWORK_ERROR, '加载失败')
    } catch (e) {
      caught = e
    }
    expect(caught).toBeInstanceOf(Error)
    expect(caught).toBeInstanceOf(ProjectLoadError)
    expect(caught.errorType).toBe('network-error')
  })
})
