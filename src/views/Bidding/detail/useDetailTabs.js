import { computed, ref, watch } from 'vue'

// Tab 配置
const TABS = [
  { name: 'basic', label: '基本信息' },
  { name: 'evaluation', label: '项目评估表' },
  { name: 'logs', label: '操作日志' },
]

export function useDetailTabs(tenderRef) {
  const activeTab = ref('basic')

  // 可见的 Tab 列表
  const visibleTabs = computed(() => {
    // tender 为 null/undefined 时显示所有 Tab（加载状态）
    // 所有来源类型（第三方平台、CRM 商机、批量导入、人工录入）均显示全部 Tab
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
