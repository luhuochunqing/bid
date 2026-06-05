import { mount } from '@vue/test-utils'
import { describe, expect, it, vi } from 'vitest'

const stubs = vi.hoisted(() => ({
  ElButton: { template: '<button><slot /></button>' },
  ElCalendar: {
    props: ['modelValue'],
    template: '<div class="el-calendar"><slot name="date-cell" :data="{ date: modelValue || new Date(), day: \'2026-04-22\', viewType: \'month\' }" /></div>',
  },
  ElDatePicker: { template: '<input @input="$emit(\'update:modelValue\', $event.target.value)">' },
  ElDialog: { props: ['modelValue'], template: '<section><slot /><slot name="footer" /></section>' },
  ElForm: { template: '<form><slot /></form>' },
  ElFormItem: { template: '<div><slot /></div>' },
  ElIcon: { template: '<span><slot /></span>' },
  ElInput: { template: '<textarea @input="$emit(\'update:modelValue\', $event.target.value)"></textarea>' },
  ElLink: { template: '<button><slot /></button>' },
  ElOption: { template: '<option><slot /></option>' },
  ElSelect: { template: '<select @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>' },
  ElTag: { template: '<span><slot /></span>' },
  ElCheckbox: { template: '<input type="checkbox" @change="$emit(\'change\', $event.target.checked)">' },
  'el-button': { template: '<button><slot /></button>' },
  'el-calendar': {
    props: ['modelValue'],
    template: '<div class="el-calendar"><slot name="date-cell" :data="{ date: modelValue || new Date(), day: \'2026-04-22\', viewType: \'month\' }" /></div>',
  },
  'el-link': { template: '<button><slot /></button>' },
  'el-tag': { template: '<span><slot /></span>' },
  'el-icon': { template: '<span><slot /></span>' },
  'el-checkbox': { template: '<input type="checkbox" @change="$emit(\'change\', $event.target.checked)">' },
  'el-dialog': { props: ['modelValue'], template: '<section><slot /><slot name="footer" /></section>' },
  'el-form': { template: '<form><slot /></form>' },
  'el-form-item': { template: '<div><slot /></div>' },
  'el-select': { template: '<select @change="$emit(\'update:modelValue\', $event.target.value)"><slot /></select>' },
  'el-option': { template: '<option><slot /></option>' },
  'el-date-picker': { template: '<input @input="$emit(\'update:modelValue\', $event.target.value)">' },
  'el-input': { template: '<textarea @input="$emit(\'update:modelValue\', $event.target.value)"></textarea>' },
}))

vi.mock('vue', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    resolveComponent: (name) => stubs[name] || actual.resolveComponent(name),
    resolveDirective: (name) => (name === 'loading' ? {} : actual.resolveDirective(name)),
  }
})

import { h, nextTick } from 'vue'
import WelcomeBanner from './WelcomeBanner.vue'
import MetricCards from './MetricCards.vue'
import ProjectList from './ProjectList.vue'
import PriorityTodos from './PriorityTodos.vue'
import SupportRequestDialog from './SupportRequestDialog.vue'
import WorkCalendar from './WorkCalendar.vue'

const DummyIcon = { render: () => h('span', 'icon') }

const mountWithStubs = async (component, options = {}) => {
  const global = options.global || {}
  const wrapper = mount(component, {
    ...options,
    global: {
      ...global,
      components: {
        ...stubs,
        ...(global.components || {}),
      },
      stubs: {
        ...stubs,
        ...(global.stubs || {}),
      },
      directives: {
        loading: {},
        ...(global.directives || {}),
      },
    },
  })
  wrapper.vm.$forceUpdate()
  await nextTick()
  return wrapper
}

describe('dashboard presentation components', () => {
  it('WelcomeBanner renders actions and emits selected action', async () => {
    const action = { key: 'report', label: '业绩报表', type: 'primary', icon: DummyIcon }
    const wrapper = await mountWithStubs(WelcomeBanner, {
      props: { role: 'admin', title: '上午好', subtitle: '今天有待办', actions: [action] },
    })

    await wrapper.find('button').trigger('click')

    expect(wrapper.text()).toContain('上午好')
    expect(wrapper.find('.banner-action-label').text()).toBe('业绩报表')
    expect(wrapper.emitted('action-click')[0]).toEqual([action])
  })

  it('MetricCards emits the clicked metric without owning navigation', async () => {
    const metric = { key: 'activeProjects', label: '进行中项目', value: '3个', icon: DummyIcon, variant: 'blue' }
    const wrapper = await mountWithStubs(MetricCards, { props: { metrics: [metric] } })

    await wrapper.find('.metric-card').trigger('click')
    await wrapper.find('.metric-card').trigger('keydown.enter')

    expect(wrapper.text()).toContain('进行中项目')
    expect(wrapper.emitted('metric-click')[0]).toEqual([metric])
    expect(wrapper.emitted('metric-click')[1]).toEqual([metric])
  })

  it('MetricCards shows actionable empty and error states', async () => {
    const wrapper = await mountWithStubs(MetricCards, {
      props: { metrics: [], loading: false },
      global: { stubs: { ...stubs, EmptyState: false } },
    })
    expect(wrapper.text()).toContain('暂无指标数据')

    await wrapper.find('button').trigger('click')
    expect(wrapper.emitted('retry')).toHaveLength(1)

    await wrapper.setProps({ error: '指标数据暂时不可用' })
    expect(wrapper.text()).toContain('指标加载失败')
  })

  it('ProjectList emits view-all and project-click events', async () => {
    const project = { id: 'P001', name: '智慧办公平台', status: '编制中', progress: 45, deadline: '03-05', manager: '张经理' }
    const wrapper = await mountWithStubs(ProjectList, { props: { projects: [project] } })

    await wrapper.find('.project-card').trigger('click')
    await wrapper.find('.project-card').trigger('keydown.space')
    await wrapper.find('.section-header button').trigger('click')

    expect(wrapper.emitted('project-click')[0]).toEqual([project])
    expect(wrapper.emitted('project-click')[1]).toEqual([project])
    expect(wrapper.emitted('view-all')).toHaveLength(1)
  })

  it('PriorityTodos keeps completion as an emitted parent concern', async () => {
    const todo = { id: 1, title: '补充业绩材料', priority: 'high', deadline: '今天', done: false, type: 'task' }
    const wrapper = await mountWithStubs(PriorityTodos, { props: { todos: [todo] } })

    await wrapper.find('.todo-checkbox').trigger('click')

    expect(wrapper.emitted('todo-toggle')[0]).toEqual([todo])
  })

  it('PriorityTodos shows a warm empty state and retryable error state', async () => {
    const wrapper = await mountWithStubs(PriorityTodos, {
      props: { todos: [] },
      global: { stubs: { ...stubs, EmptyState: false } },
    })
    expect(wrapper.text()).toContain('今天没有高优先级待办')

    await wrapper.setProps({ error: '待办加载失败，请稍后重试' })
    await wrapper.find('button').trigger('click')

    expect(wrapper.text()).toContain('待办加载失败')
    expect(wrapper.emitted('retry')).toHaveLength(1)
  })

  it('SupportRequestDialog emits immutable form updates and submit', async () => {
    const form = { projectId: 1, type: 'bid_support', dueDate: '', description: '' }
    const wrapper = await mountWithStubs(SupportRequestDialog, {
      props: { modelValue: true, form, projects: [{ id: 1, name: '智慧办公平台' }] },
    })

    await wrapper.find('textarea').setValue('需要技术方案支持')
    await wrapper.findAll('button').at(1).trigger('click')

    expect(wrapper.emitted('update:form')[0][0]).toEqual({ ...form, description: '需要技术方案支持' })
    expect(wrapper.emitted('submit')).toHaveLength(1)
  })

  it('WorkCalendar renders through real Element Plus calendar wiring', async () => {
    const event = {
      id: 1,
      type: 'deadline',
      title: '数字政府项目截标',
      project: '数字政府项目截标',
      urgent: true,
      priorityLevel: 'priority-critical',
      countdownLabel: 'D-1',
      riskTagType: 'danger',
      riskLabel: '高风险',
      actionLabel: '去补材料',
      dayLabel: '04-23',
      weekdayLabel: '四',
      fieldSummary: { owner: '负责人 商务专员', stage: '阶段 资料收口', blocker: '阻塞 待确认最终材料' },
    }
    const wrapper = await mountWithStubs(WorkCalendar, {
      props: {
        modelValue: new Date('2026-04-22T00:00:00'),
        activeFilter: 'all',
        filters: [{ label: '全部', value: 'all' }],
        visibleEvents: [event],
        selectedDateEvents: [event],
        selectedDateLabel: '4月23日星期四',
        monthSummary: { total: 1, urgent: 1, nextDeadlineLabel: 'D-1' },
        upcomingEvents: [event],
        getEventsForDate: () => [event],
        calendarCellClass: () => 'calendar-day-urgent',
        getEventTypeTag: () => ({ type: 'danger', label: '截止' }),
      },
    })

    expect(wrapper.text()).toContain('数字政府项目截标')
    expect(wrapper.text()).toContain('1 个节点')
    expect(wrapper.find('.el-calendar').exists()).toBe(true)
  })

  it('WorkCalendar exposes keyboard access for calendar event cards', async () => {
    const event = {
      id: 1,
      type: 'deadline',
      title: '数字政府项目截标',
      project: '数字政府项目截标',
      priorityLevel: 'priority-critical',
      countdownLabel: 'D-1',
      actionLabel: '去补材料',
      dayLabel: '04-23',
      weekdayLabel: '四',
      fieldSummary: { owner: '负责人 商务专员', stage: '阶段 资料收口', blocker: '阻塞 待确认最终材料' },
    }
    const wrapper = await mountWithStubs(WorkCalendar, {
      props: {
        modelValue: new Date('2026-04-22T00:00:00'),
        activeFilter: 'all',
        filters: [{ label: '全部', value: 'all' }],
        visibleEvents: [event],
        selectedDateEvents: [event],
        selectedDateLabel: '4月23日星期四',
        monthSummary: { total: 1, urgent: 1, nextDeadlineLabel: 'D-1' },
        upcomingEvents: [event],
        getEventsForDate: () => [event],
        getEventTypeTag: () => ({ type: 'danger', label: '截止' }),
      },
    })

    await wrapper.find('.upcoming-event-item').trigger('keydown.enter')

    expect(wrapper.emitted('event-date-select')[0]).toEqual([event])
    expect(wrapper.find('.upcoming-event-item').attributes('role')).toBe('button')
  })
})
