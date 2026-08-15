<template>
  <div class="filter-select" :class="{ disabled: disabled }" ref="wrapperRef">
    <!-- 自定义 trigger：显示"全部"或"已选 N 项" + badge -->
    <div
      class="ms-trigger"
      :class="{ open: panelVisible, disabled: disabled }"
      @click="togglePanel"
    >
      <span class="ms-trigger-text">{{ triggerText }}</span>
      <span v-if="showBadge" class="ms-badge">{{ selectedCount }}</span>
    </div>

    <!-- 自定义 panel -->
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
        <div
          v-for="opt in visibleOptions"
          :key="opt.value"
          class="ms-option"
          :class="{ checked: selectedSet.has(opt.value) }"
          @click="toggleOption(opt.value)"
        >
          <span class="ms-check"></span>
          <span class="ms-label">{{ opt.label }}</span>
        </div>
        <div v-if="visibleOptions.length === 0" class="ms-empty">无匹配结果</div>
      </div>
      <div class="ms-actions">
        <a v-if="!hideSelectAll" @click.stop="selectAllVisible">全选</a>
        <a @click.stop="clearAll">清空</a>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  options: { type: Array, default: () => [] },
  loading: { type: Boolean, default: false },
  disabled: { type: Boolean, default: false },
  hideSelectAll: { type: Boolean, default: false },
  placeholder: { type: String, default: '请选择' }
})

const emit = defineEmits(['update:modelValue', 'search', 'change'])

const wrapperRef = ref(null)
const panelRef = ref(null)
const searchInputRef = ref(null)
const searchText = ref('')
const isComposing = ref(false)
const panelVisible = ref(false)
const selectedValues = ref([...props.modelValue])

const selectedSet = computed(() => new Set(selectedValues.value))
const selectedCount = computed(() => selectedValues.value.length)
const totalCount = computed(() => props.options.length)

// trigger 显示逻辑：未选时显示"请选择"，有选中时显示"已选 N 项"
const triggerText = computed(() => {
  const count = selectedCount.value
  if (count === 0) return '请选择'
  if (count === totalCount.value) return '全部'
  return `已选 ${count} 项`
})
const showBadge = computed(() => {
  return selectedCount.value > 0 && selectedCount.value !== totalCount.value
})

const visibleOptions = computed(() => {
  if (!searchText.value || isComposing.value) return props.options
  const kw = searchText.value.toLowerCase()
  return props.options.filter((o) => o.label.toLowerCase().includes(kw))
})

watch(() => props.modelValue, (val) => {
  if (val !== selectedValues.value) {
    selectedValues.value = [...(val || [])]
  }
})

const togglePanel = () => {
  if (props.disabled) return
  panelVisible.value = !panelVisible.value
  if (panelVisible.value) {
    searchText.value = ''
    setTimeout(() => searchInputRef.value?.focus(), 0)
  }
}

const toggleOption = (value) => {
  if (selectedSet.value.has(value)) {
    selectedValues.value = selectedValues.value.filter((v) => v !== value)
  } else {
    selectedValues.value = [...selectedValues.value, value]
  }
  emitChange()
}

const selectAllVisible = () => {
  const visibleVals = visibleOptions.value.map((o) => o.value)
  const merged = [...new Set([...selectedValues.value, ...visibleVals])]
  selectedValues.value = merged
  emitChange()
}

const clearAll = () => {
  selectedValues.value = []
  emitChange()
}

const emitChange = () => {
  emit('update:modelValue', [...selectedValues.value])
  emit('change', [...selectedValues.value])
}

// 点击外部关闭
const handleClickOutside = (e) => {
  if (wrapperRef.value && !wrapperRef.value.contains(e.target)) {
    panelVisible.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})
onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside)
})
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
  border: 1px solid #E2E8F0;
  border-radius: 4px;
  font-size: 12px;
  color: #475569;
  background: #fff;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s;
  position: relative;
  appearance: none;
  background-image: url("data:image/svg+xml;charset=utf-8,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6' viewBox='0 0 10 6'%3E%3Cpath fill='%2394A3B8' d='M0 0l5 6 5-6z'/%3E%3C/svg%3E");
  background-repeat: no-repeat;
  background-position: right 8px center;
  user-select: none;
}
.ms-trigger:hover { border-color: #2E7659; }
.ms-trigger.disabled {
  background: #F1F5F9; color: #CBD5E1; cursor: not-allowed; border-color: #E2E8F0;
}
.ms-trigger.disabled:hover { border-color: #E2E8F0; }
.ms-trigger.open {
  border-color: #2E7659;
  box-shadow: 0 0 0 3px rgba(46,118,89,0.12);
}
.ms-trigger-text {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ms-badge {
  background: #2E7659;
  color: #fff;
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
  min-width: 200px;
  max-height: 280px;
  background: #fff;
  border: 1px solid #E2E8F0;
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
  border: 1px solid #E2E8F0;
  border-radius: 4px;
  font-size: 12px;
  outline: none;
  box-sizing: border-box;
  transition: border-color 0.15s;
  flex-shrink: 0;
}
.ms-search:focus { border-color: #2E7659; }
.ms-search::placeholder { color: #CBD5E1; }

.ms-list {
  flex: 1;
  overflow-y: auto;
  padding: 6px 0;
  min-height: 0;
}
.ms-list::-webkit-scrollbar { width: 4px; }
.ms-list::-webkit-scrollbar-track { background: #F8FAFC; }
.ms-list::-webkit-scrollbar-thumb { background: #E2E8F0; border-radius: 2px; }

.ms-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
  transition: background 0.15s;
  white-space: nowrap;
}
.ms-option:hover { background: #F0F9F6; color: #2E7659; }
.ms-check {
  width: 14px;
  height: 14px;
  border: 1.5px solid #E2E8F0;
  border-radius: 3px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}
.ms-option.checked .ms-check {
  background: #2E7659;
  border-color: #2E7659;
}
.ms-option.checked .ms-check::after {
  content: '';
  width: 4px;
  height: 7px;
  border: solid #fff;
  border-width: 0 1.5px 1.5px 0;
  transform: rotate(45deg) translate(-1px, -1px);
}
.ms-label { flex: 1; }

.ms-empty {
  padding: 12px;
  text-align: center;
  color: #94A3B8;
  font-size: 12px;
}

.ms-actions {
  display: flex;
  gap: 8px;
  padding: 8px 14px;
  border-top: 1px solid #F1F5F9;
  background: #fff;
  flex-shrink: 0;
  z-index: 1;
  justify-content: flex-end;
}
.ms-actions a {
  font-size: 11px;
  color: #2E7659;
  cursor: pointer;
  text-decoration: none;
  font-weight: 500;
}
.ms-actions a:hover { text-decoration: underline; }
</style>
