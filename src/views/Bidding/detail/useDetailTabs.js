import { computed, ref, watch } from 'vue'

// Tab 配置
const TABS = [
  { name: 'basic', label: '基本信息' },
  { name: 'evaluation', label: '项目评估表' },
  { name: 'logs', label: '操作日志' },
]

export function useDetailTabs(tenderRef) {
  const activeTab = ref('basic')

  // 可见的 Tab 列表（操作日志 Tab 条件隐藏）
  const visibleTabs = computed(() => {
    // tender 为 null/undefined 时显示所有 Tab（加载状态）
    if (!tenderRef.value) return TABS

    // 蓝图要求：人工录入创建标讯时（sourceType === 'MANUAL_SINGLE'）
    // 不展示操作日志 Tab
    if (tenderRef.value.sourceType === 'MANUAL_SINGLE') {
      return TABS.filter(t => t.name !== 'logs')
    }

    // 其他情况（EXTERNAL_PLATFORM, CRM_OPPORTUNITY, BULK_IMPORT）显示所有 Tab
    return TABS
  })

  // activeTab 随着 visibleTabs 变化自动修正
  // 如果当前 tab 被隐藏了，切到第一个可见 tab
  watch(visibleTabs, (tabs) => {
    const isActiveTabVisible = tabs.some(t => t.name === activeTab.value)
    if (!isActiveTabVisible && tabs.length > 0) {
      activeTab.value = tabs[0].name
    }
  }, { immediate: true })

  // Tab 切换方法
  function switchTab(name) {
    activeTab.value = name
  }

  return { activeTab, visibleTabs, switchTab }
}
