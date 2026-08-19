<template>
  <div class="filter-select filter-tree-select" ref="wrapperRef">
    <!-- trigger：与 FilterSelect 一致，显示"全部/已选 N 项" + badge -->
    <div class="ms-trigger" :class="{ open: panelVisible }" @click="togglePanel">
      <span class="ms-trigger-text">{{ triggerText }}</span>
      <span v-if="showBadge" class="ms-badge">{{ selectedCount }}</span>
    </div>

    <!-- panel：搜索框 + 树形复选列表 + 全选/清空 -->
    <div v-show="panelVisible" class="ms-panel" ref="panelRef">
      <input
        ref="searchInputRef"
        v-model="searchText"
        type="text"
        class="ms-search"
        placeholder="搜索..."
        @compositionstart="isComposing = true"
        @compositionend="isComposing = false"
      />
      <div class="ms-list">
        <TreeSelectNode :nodes="visibleTree" :depth="0" />
        <div v-if="visibleTree.length === 0" class="ms-empty">无匹配结果</div>
      </div>
      <div class="ms-actions">
        <a @click.stop="selectAllVisible">全选</a>
        <a @click.stop="clearAll">清空</a>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, provide, onMounted, onBeforeUnmount } from 'vue'
import TreeSelectNode from './TreeSelectNode.vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  options: { type: Array, default: () => [] },
  placeholder: { type: String, default: '请选择' }
})

const emit = defineEmits(['update:modelValue', 'change'])

const wrapperRef = ref(null)
const panelRef = ref(null)
const searchInputRef = ref(null)
const searchText = ref('')
const isComposing = ref(false)
const panelVisible = ref(false)
const expandedValues = ref(new Set())

const selectedSet = computed(() => new Set(props.modelValue))
const selectedCount = computed(() => props.modelValue.length)

// 递归收集树全部节点 value
function collectValues(nodes, acc) {
  for (const node of nodes || []) {
    acc.push(node.value)
    if (Array.isArray(node.children)) collectValues(node.children, acc)
  }
  return acc
}
const allValues = computed(() => collectValues(props.options, []))
const totalCount = computed(() => allValues.value.length)

const triggerText = computed(() => {
  const count = selectedCount.value
  if (count === 0) return '请选择'
  if (totalCount.value > 0 && count === totalCount.value) return '全部'
  return `已选 ${count} 项`
})
const showBadge = computed(() =>
  selectedCount.value > 0 && selectedCount.value !== totalCount.value
)

// 搜索过滤：节点匹配保留整棵子树；仅后代匹配则保留到匹配链
function filterTree(nodes, kw) {
  const result = []
  for (const node of nodes || []) {
    const selfMatch = (node.label || '').toLowerCase().includes(kw)
    const children = selfMatch
      ? node.children || []
      : filterTree(node.children || [], kw)
    if (selfMatch || children.length > 0) {
      result.push({ ...node, children })
    }
  }
  return result
}

const visibleTree = computed(() => {
  if (!searchText.value || isComposing.value) return props.options
  return filterTree(props.options, searchText.value.toLowerCase())
})

// 选项变化时默认展开全部层级；搜索时强制全部展开
watch(
  () => props.options,
  (opts) => {
    expandedValues.value = new Set(collectValues(opts, []))
  },
  { immediate: true }
)
watch([searchText, panelVisible], () => {
  if (searchText.value && panelVisible.value) {
    expandedValues.value = new Set(collectValues(props.options, []))
  }
})

provide('treeSelectContext', {
  selectedSet,
  isExpanded: (value) => expandedValues.value.has(value),
  toggleExpand: (value) => {
    const next = new Set(expandedValues.value)
    if (next.has(value)) next.delete(value)
    else next.add(value)
    expandedValues.value = next
  },
  toggle: (value) => {
    const next = selectedSet.value.has(value)
      ? props.modelValue.filter((v) => v !== value)
      : [...props.modelValue, value]
    emit('update:modelValue', next)
    emit('change', next)
  }
})

const togglePanel = () => {
  panelVisible.value = !panelVisible.value
  if (panelVisible.value) {
    searchText.value = ''
    setTimeout(() => searchInputRef.value?.focus(), 0)
  }
}

const selectAllVisible = () => {
  const visibleVals = collectValues(visibleTree.value, [])
  const merged = [...new Set([...props.modelValue, ...visibleVals])]
  emit('update:modelValue', merged)
  emit('change', merged)
}

const clearAll = () => {
  emit('update:modelValue', [])
  emit('change', [])
}

const handleClickOutside = (e) => {
  if (wrapperRef.value && !wrapperRef.value.contains(e.target)) {
    panelVisible.value = false
  }
}

onMounted(() => document.addEventListener('click', handleClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', handleClickOutside))
</script>

<style scoped>
.filter-select {
  position: relative;
  width: 140px;
  flex-shrink: 0;
}

.ms-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  min-width: 90px;
  padding: 5px 26px 5px 10px;
  border: 1px solid var(--status-neutral-bg);
  border-radius: 4px;
  font-size: 12px;
  color: var(--status-neutral-color);
  background: var(--bg-white);
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
  position: relative;
  user-select: none;
  box-sizing: border-box;
}
.ms-trigger::after {
  content: '';
  position: absolute;
  right: 9px;
  top: 50%;
  width: 0;
  height: 0;
  border-left: 4px solid transparent;
  border-right: 4px solid transparent;
  border-top: 5px solid var(--gray-350);
  transform: translateY(-2px);
  pointer-events: none;
}
.ms-trigger:hover { border-color: var(--brand-xiyu-logo); }
.ms-trigger.open {
  border-color: var(--brand-xiyu-logo);
  box-shadow: 0 0 0 3px var(--brand-xiyu-logo-soft);
}
.ms-trigger-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ms-badge {
  background: var(--brand-xiyu-logo);
  color: var(--bg-white);
  font-size: 10px;
  font-weight: 700;
  padding: 0 5px;
  border-radius: 8px;
  min-width: 16px;
  text-align: center;
  line-height: 16px;
  flex-shrink: 0;
}

.ms-panel {
  position: absolute;
  top: calc(100% + 4px);
  left: 0;
  z-index: 200;
  min-width: 220px;
  max-height: 280px;
  background: var(--bg-white);
  border: 1px solid var(--status-neutral-bg);
  border-radius: 4px;
  box-shadow: 0 10px 25px rgba(0,0,0,0.1);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.ms-search {
  width: calc(100% - 20px);
  margin: 4px 10px 6px;
  padding: 5px 10px;
  border: 1px solid var(--status-neutral-bg);
  border-radius: 4px;
  font-size: 12px;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.15s;
  flex-shrink: 0;
}
.ms-search:focus { border-color: var(--brand-xiyu-logo); }
.ms-search::placeholder { color: var(--gray-300); }

.ms-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px 0;
  min-height: 0;
}
.ms-list::-webkit-scrollbar { width: 4px; }
.ms-list::-webkit-scrollbar-track { background: var(--gray-50); }
.ms-list::-webkit-scrollbar-thumb { background: var(--status-neutral-bg); border-radius: 2px; }

.ms-empty { padding: 12px; text-align: center; color: var(--gray-300); font-size: 12px; }

.ms-actions {
  display: flex;
  gap: 8px;
  padding: 8px 14px;
  border-top: 1px solid var(--gray-50);
  background: var(--bg-white);
  flex-shrink: 0;
  z-index: 1;
  justify-content: flex-end;
}
.ms-actions a { font-size: 11px; color: var(--brand-xiyu-logo); cursor: pointer; text-decoration: none; font-weight: 500; }
.ms-actions a:hover { text-decoration: underline; }
</style>
