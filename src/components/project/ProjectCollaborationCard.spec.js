import { mount, flushPromises } from '@vue/test-utils'
import { describe, expect, it, vi, beforeEach } from 'vitest'

const getThreadsMock = vi.fn()
const getThreadMock = vi.fn()
const addCommentMock = vi.fn()
const createThreadMock = vi.fn()
const mentionsCreateMock = vi.fn()
const messageSuccess = vi.fn()
const messageWarning = vi.fn()
const messageError = vi.fn()

vi.mock('@/api/modules/collaboration.js', () => ({
  collaborationApi: {
    getThreads: (...a) => getThreadsMock(...a),
    getThread: (...a) => getThreadMock(...a),
    addComment: (...a) => addCommentMock(...a),
    createThread: (...a) => createThreadMock(...a),
  },
}))

vi.mock('@/api/modules/mentions.js', () => ({
  mentionsApi: { create: (...a) => mentionsCreateMock(...a) },
}))

vi.mock('@/components/common/MentionInput.vue', () => ({
  default: {
    name: 'MentionInput',
    props: ['modelValue'],
    emits: ['update:modelValue'],
    template: '<textarea class="mention-input-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
  },
}))

vi.mock('element-plus', async () => {
  const actual = await vi.importActual('element-plus')
  return {
    ...actual,
    ElMessage: {
      success: (...a) => messageSuccess(...a),
      warning: (...a) => messageWarning(...a),
      error: (...a) => messageError(...a),
    },
  }
})

const mountCard = async (projectId = 9) => {
  const ProjectCollaborationCard = (await import('./ProjectCollaborationCard.vue')).default
  const wrapper = mount(ProjectCollaborationCard, {
    props: { projectId },
    global: {
      stubs: {
        'el-card': { template: '<div><slot name="header" /><slot /></div>' },
        'el-empty': { template: '<div class="empty-stub"><slot /></div>' },
        'el-tag': { template: '<span><slot /></span>' },
        'el-icon': { template: '<i><slot /></i>' },
        'el-button': {
          template: '<button class="btn-stub" :disabled="disabled" @click="$emit(\'click\')"><slot /></button>',
          props: ['disabled', 'loading', 'type', 'link', 'icon'],
          emits: ['click'],
        },
        'el-dialog': {
          template: '<div v-if="modelValue" class="dialog-stub"><slot /><slot name="footer" /></div>',
          props: ['modelValue'],
          emits: ['update:modelValue', 'close'],
        },
        'el-form': { template: '<form><slot /></form>' },
        'el-form-item': { template: '<div><slot /></div>' },
        'el-input': {
          template: '<input class="el-input-stub" :value="modelValue" @input="$emit(\'update:modelValue\', $event.target.value)" />',
          props: ['modelValue'],
          emits: ['update:modelValue'],
        },
      },
    },
  })
  await flushPromises()
  return wrapper
}

const selectFirstThread = async (wrapper) => {
  const item = wrapper.find('.thread-item')
  await item.trigger('click')
  await flushPromises()
}

const typeComment = async (wrapper, text) => {
  const ta = wrapper.find('textarea.mention-input-stub')
  await ta.setValue(text)
}

const clickSendComment = async (wrapper) => {
  const buttons = wrapper.findAll('button.btn-stub')
  const send = buttons.find((b) => b.text() === '发送评论')
  await send.trigger('click')
  await flushPromises()
}

describe('ProjectCollaborationCard', () => {
  vi.setConfig({ testTimeout: 10000 })
  beforeEach(() => {
    getThreadsMock.mockReset()
    getThreadMock.mockReset()
    addCommentMock.mockReset()
    createThreadMock.mockReset()
    mentionsCreateMock.mockReset()
    messageSuccess.mockReset()
    messageWarning.mockReset()
    messageError.mockReset()
    getThreadsMock.mockResolvedValue({ data: [{ id: 42, title: '投标评估', status: 'OPEN' }] })
    getThreadMock.mockResolvedValue({ data: { comments: [] } })
  })

  it('无 @ 时仅调用 addComment，不调 mentionsApi', async () => {
    addCommentMock.mockResolvedValue({ success: true })
    const wrapper = await mountCard()
    await selectFirstThread(wrapper)
    await typeComment(wrapper, '这个先看下')
    await clickSendComment(wrapper)

    expect(addCommentMock).toHaveBeenCalledWith(42, { content: '这个先看下' })
    expect(mentionsCreateMock).not.toHaveBeenCalled()
    expect(messageSuccess).toHaveBeenCalledWith('评论已发送')
  })

  it('含 @ 时先写评论（plainText），再调 mentionsApi（原始 raw + COMMENT + threadId）', async () => {
    addCommentMock.mockResolvedValue({ success: true })
    mentionsCreateMock.mockResolvedValue({ success: true })
    const wrapper = await mountCard()
    await selectFirstThread(wrapper)
    await typeComment(wrapper, '麻烦 @[张三](7) 看一下')
    await clickSendComment(wrapper)

    expect(addCommentMock).toHaveBeenCalledWith(42, { content: '麻烦 @张三 看一下' })
    expect(mentionsCreateMock).toHaveBeenCalledWith({
      content: '麻烦 @[张三](7) 看一下',
      sourceEntityType: 'COMMENT',
      sourceEntityId: 42,
      title: '投标评估',
    })
    expect(messageSuccess).toHaveBeenCalledWith('评论已发送')
  })

  it('addComment 失败时不触发 mentionsApi，提示发送失败', async () => {
    addCommentMock.mockRejectedValue(new Error('boom'))
    const wrapper = await mountCard()
    await selectFirstThread(wrapper)
    await typeComment(wrapper, '麻烦 @[张三](7) 看一下')
    await clickSendComment(wrapper)

    expect(addCommentMock).toHaveBeenCalledTimes(1)
    expect(mentionsCreateMock).not.toHaveBeenCalled()
    expect(messageError).toHaveBeenCalledWith('发送失败')
    expect(messageSuccess).not.toHaveBeenCalled()
  })

  it('addComment 成功但 mentionsApi 失败时评论保留并 warning 提示', async () => {
    addCommentMock.mockResolvedValue({ success: true })
    mentionsCreateMock.mockRejectedValue(new Error('mention down'))
    const wrapper = await mountCard()
    await selectFirstThread(wrapper)
    await typeComment(wrapper, '麻烦 @[张三](7) 看一下')
    await clickSendComment(wrapper)

    expect(addCommentMock).toHaveBeenCalledTimes(1)
    expect(mentionsCreateMock).toHaveBeenCalledTimes(1)
    expect(messageWarning).toHaveBeenCalledWith('评论已发送，但 @ 通知发送失败')
    expect(messageSuccess).not.toHaveBeenCalled()
  })

  it('新建讨论：提交后刷新线程、选中新线程', async () => {
    // 第一次 loadThreads：空；第二次：返回新建的那个
    getThreadsMock
      .mockResolvedValueOnce({ data: [] })
      .mockResolvedValueOnce({ data: [{ id: 99, title: '新讨论', status: 'OPEN' }] })
    createThreadMock.mockResolvedValue({ data: { id: 99, title: '新讨论' } })

    const wrapper = await mountCard()

    // 此时应有空态，点「新建讨论」按钮
    const buttons = wrapper.findAll('button.btn-stub')
    const createBtn = buttons.find((b) => b.text() === '新建讨论')
    await createBtn.trigger('click')
    await flushPromises()

    // dialog 里填标题
    const titleInput = wrapper.find('input.el-input-stub')
    await titleInput.setValue('新讨论')

    // 点确定
    const allButtons = wrapper.findAll('button.btn-stub')
    const okBtn = allButtons.find((b) => b.text() === '确定')
    await okBtn.trigger('click')
    await flushPromises()

    expect(createThreadMock).toHaveBeenCalledWith({ projectId: 9, title: '新讨论' })
    expect(getThreadsMock).toHaveBeenCalledTimes(2) // 初始 + 创建后刷新
    expect(getThreadMock).toHaveBeenCalledWith(99) // 自动选中新线程
    expect(messageSuccess).toHaveBeenCalledWith('讨论已创建')
  })
})
