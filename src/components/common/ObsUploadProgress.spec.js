// Input: ObsUploadProgress 组件
// Output: 测试套件
// Pos: src/components/common/ - ObsUploadProgress 单元测试
import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ObsUploadProgress from './ObsUploadProgress.vue'

describe('ObsUploadProgress', () => {
  it('renders when visible', () => {
    const wrapper = mount(ObsUploadProgress, {
      props: { visible: true, fileName: 'test.txt', fileSize: 1024 },
    })
    expect(wrapper.find('.obs-upload-progress').exists()).toBe(true)
    expect(wrapper.text()).toContain('test.txt')
  })

  it('does not render when not visible', () => {
    const wrapper = mount(ObsUploadProgress, {
      props: { visible: false, fileName: 'test.txt', fileSize: 1024 },
    })
    expect(wrapper.find('.obs-upload-progress').exists()).toBe(false)
  })

  it('shows uploading status', () => {
    const wrapper = mount(ObsUploadProgress, {
      props: {
        visible: true,
        fileName: 'test.txt',
        fileSize: 1024,
        uploading: true,
        progressPercent: 50,
      },
    })
    expect(wrapper.find('.is-uploading').exists()).toBe(true)
    expect(wrapper.text()).toContain('上传中')
  })

  it('shows success status at 100%', () => {
    const wrapper = mount(ObsUploadProgress, {
      props: {
        visible: true,
        fileName: 'test.txt',
        fileSize: 1024,
        uploading: false,
        progressPercent: 100,
      },
    })
    expect(wrapper.find('.is-success').exists()).toBe(true)
    expect(wrapper.text()).toContain('完成')
  })

  it('shows error status', () => {
    const wrapper = mount(ObsUploadProgress, {
      props: {
        visible: true,
        fileName: 'test.txt',
        fileSize: 1024,
        hasError: true,
      },
    })
    expect(wrapper.find('.is-error').exists()).toBe(true)
    expect(wrapper.text()).toContain('上传失败')
  })

  it('formats file size correctly', () => {
    const wrapper = mount(ObsUploadProgress, {
      props: { visible: true, fileName: 'test.txt', fileSize: 1048576 },
    })
    expect(wrapper.text()).toContain('1.0 MB')
  })

  it('emits cancel when cancel button clicked', async () => {
    const wrapper = mount(ObsUploadProgress, {
      props: {
        visible: true,
        fileName: 'test.txt',
        fileSize: 1024,
        uploading: true,
        progressPercent: 50,
      },
      global: { stubs: ['el-button', 'el-progress'] },
    })
    await wrapper.findComponent({ name: 'el-button' }).vm.$emit('click')
    expect(wrapper.emitted('cancel')).toBeTruthy()
  })
})
