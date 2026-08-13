<template>
  <div class="filter-select" ref="wrapperRef">
    <el-select
      ref="selectRef"
      v-model="selectedValues"
      :placeholder="placeholder"
      multiple
      filterable
      remote
      :remote-method="handleRemoteSearch"
      :loading="loading"
      :popper-class="'filter-select-popper'"
      :reserve-keyword="false"
      clearable
      @visible-change="handleVisibleChange"
      @change="handleChange"
      @clear="handleClear"
    >
      <template #header>
        <div class="select-header">
          <el-input
            ref="searchInputRef"
            v-model="searchText"
            size="small"
            placeholder="搜索..."
            :prefix-icon="Search"
            clearable
            @input="handleSearchInput"
            @compositionstart="isComposing = true"
            @compositionend="handleCompositionEnd"
          />
        </div>
      </template>

      <el-option
        v-for="option in visibleOptions"
        :key="option.value"
        :label="option.label"
        :value="option.value"
      >
        <span>{{ option.label }}</span>
      </el-option>

      <template #empty>
        <div class="select-empty">
          <el-empty v-if="searchText && !loading" description="无匹配结果" :image-size="60" />
          <el-empty v-else-if="!loading" description="请搜索" :image-size="60" />
        </div>
      </template>

      <template #footer>
        <div class="select-footer">
          <el-button size="small" @click="handleSelectAllVisible">全选可见</el-button>
          <el-button size="small" @click="handleClearAll">清空</el-button>
        </div>
      </template>
    </el-select>
  </div>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { Search } from '@element-plus/icons-vue'

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => []
  },
  options: {
    type: Array,
    default: () => []
  },
  loading: {
    type: Boolean,
    default: false
  },
  placeholder: {
    type: String,
    default: '请选择'
  }
})

const emit = defineEmits(['update:modelValue', 'search', 'change'])

const selectRef = ref(null)
const searchInputRef = ref(null)
const wrapperRef = ref(null)
const searchText = ref('')
const isComposing = ref(false)
const selectedValues = ref([...props.modelValue])

const visibleOptions = computed(() => {
  if (!searchText.value || isComposing.value) return props.options
  const keyword = searchText.value.toLowerCase()
  return props.options.filter((opt) =>
    opt.label.toLowerCase().includes(keyword)
  )
})

watch(() => props.modelValue, (val) => {
  selectedValues.value = [...(val || [])]
}, { deep: true })

watch(selectedValues, (val) => {
  emit('update:modelValue', [...val])
}, { deep: true })

const handleRemoteSearch = (query) => {
  // 使用自定义搜索逻辑，remote-method 仅用于触发搜索
  // 实际的过滤由 visibleOptions 计算属性完成
}

const handleSearchInput = (value) => {
  if (isComposing.value) return
  searchText.value = value
  emit('search', value)
}

const handleCompositionEnd = (e) => {
  isComposing.value = false
  searchText.value = e.target?.value || ''
  emit('search', searchText.value)
}

const handleVisibleChange = (visible) => {
  if (visible) {
    searchText.value = ''
    nextTick(() => {
      // 聚焦搜索框
      const input = wrapperRef.value?.querySelector('.select-header .el-input__inner')
      if (input) {
        input.focus()
      }
    })
  }
}

const handleSelectAllVisible = () => {
  const visibleValues = visibleOptions.value.map((opt) => opt.value)
  const currentValues = [...selectedValues.value]

  const newValues = [...new Set([...currentValues, ...visibleValues])]
  selectedValues.value = newValues
}

const handleClearAll = () => {
  selectedValues.value = []
}

const handleClear = () => {
  selectedValues.value = []
  emit('change', [])
}

const handleChange = (val) => {
  emit('change', val)
}
</script>

<style>
/* 全局样式 - 自定义下拉面板 */
.filter-select-popper {
  padding: 0 !important;
}

.filter-select-popper .el-select-dropdown__list {
  padding: 0 !important;
}

.filter-select-popper .el-select-dropdown__header {
  padding: 8px 10px !important;
  border-bottom: 1px solid #E2E8F0;
}

.filter-select-popper .el-select-dropdown__footer {
  padding: 8px 10px !important;
  border-top: 1px solid #E2E8F0;
}

.filter-select-popper .el-select-dropdown__wrap {
  max-height: 220px !important;
}

.filter-select-popper .el-select-dropdown__empty {
  padding: 20px !important;
}
</style>

<style scoped>
.filter-select {
  width: 100%;
}

.select-header {
  padding: 0;
}

.select-footer {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}

.select-empty {
  display: flex;
  justify-content: center;
  align-items: center;
  padding: 10px 0;
}
</style>