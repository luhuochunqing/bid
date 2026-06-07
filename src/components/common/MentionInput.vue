<template>
  <div class="mention-input">
    <el-input
      ref="inputRef"
      v-model="text"
      type="textarea"
      :rows="rows"
      :placeholder="placeholder"
      resize="vertical"
      @input="handleInput"
      @keydown="handleKeydown"
    />
    <div
      v-if="showPopover"
      class="mention-popover"
      role="listbox"
      aria-label="@ 用户候选"
    >
      <div v-if="loading" class="mention-loading">搜索中...</div>
      <div v-else-if="candidates.length === 0" class="mention-empty">无匹配用户</div>
      <div
        v-for="(user, idx) in candidates"
        :key="user.id"
        class="mention-candidate"
        :class="{ 'mention-candidate--active': idx === activeIdx }"
        role="option"
        :aria-selected="idx === activeIdx"
        @mousedown.prevent="pickUser(user)"
        @mouseenter="activeIdx = idx"
      >
        <span class="mention-candidate-name">{{ user.name }}</span>
        <span v-if="user.role" class="mention-candidate-role">{{ user.role }}</span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { usersApi } from '@/api/modules/users'
import { parseMentionContent } from '@/utils/notificationHelpers'

const props = defineProps({
  modelValue: { type: String, default: '' },
  rows: { type: Number, default: 4 },
  placeholder: { type: String, default: '输入内容，使用 @ 提及同事' },
  debounceMs: { type: Number, default: 200 }
})

const emit = defineEmits(['update:modelValue', 'parsed'])

const MENTION_TRIGGER = /@([^\s@[]{0,20})$/
const MAX_CANDIDATES = 8

const inputRef = ref(null)
const text = ref(props.modelValue)
const showPopover = ref(false)
const candidates = ref([])
const activeIdx = ref(0)
const loading = ref(false)
const triggerStart = ref(-1)
let searchTimer = null

watch(() => props.modelValue, (val) => {
  if (val !== text.value) text.value = val
})

watch(text, (val) => {
  emit('update:modelValue', val)
})

const closePopover = () => {
  showPopover.value = false
  candidates.value = []
  activeIdx.value = 0
  triggerStart.value = -1
}

const runSearch = async (query) => {
  loading.value = true
  try {
    const response = await usersApi.search(query, MAX_CANDIDATES)
    const list = Array.isArray(response?.data) ? response.data : []
    candidates.value = list.slice(0, MAX_CANDIDATES)
    activeIdx.value = 0
  } catch {
    candidates.value = []
  } finally {
    loading.value = false
  }
}

const handleInput = (val) => {
  const textarea = inputRef.value?.textarea
  const cursor = textarea?.selectionStart ?? val.length
  const prefix = val.slice(0, cursor)
  const match = prefix.match(MENTION_TRIGGER)
  if (!match) {
    closePopover()
    return
  }
  triggerStart.value = cursor - match[0].length
  showPopover.value = true
  if (searchTimer) clearTimeout(searchTimer)
  const query = match[1]
  searchTimer = setTimeout(() => runSearch(query), props.debounceMs)
}

const pickUser = (user) => {
  if (triggerStart.value < 0) return
  const textarea = inputRef.value?.textarea
  const cursor = textarea?.selectionStart ?? text.value.length
  const before = text.value.slice(0, triggerStart.value)
  const after = text.value.slice(cursor)
  const safeName = String(user.name ?? '').replace(/[[\]()]/g, '')
  const token = `@[${safeName}](${user.id}) `
  text.value = `${before}${token}${after}`
  closePopover()
  emit('parsed', parseMentionContent(text.value))
}

const handleKeydown = (event) => {
  if (!showPopover.value || candidates.value.length === 0) return
  if (event.key === 'ArrowDown') {
    event.preventDefault()
    activeIdx.value = (activeIdx.value + 1) % candidates.value.length
  } else if (event.key === 'ArrowUp') {
    event.preventDefault()
    activeIdx.value = (activeIdx.value - 1 + candidates.value.length) % candidates.value.length
  } else if (event.key === 'Enter') {
    event.preventDefault()
    pickUser(candidates.value[activeIdx.value])
  } else if (event.key === 'Escape') {
    closePopover()
  }
}

const submit = () => {
  emit('parsed', parseMentionContent(text.value))
}

onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
    searchTimer = null
  }
})

defineExpose({ submit })
</script>

<style scoped>
.mention-input {
  position: relative;
}

.mention-popover {
  position: absolute;
  top: 100%;
  left: 0;
  z-index: 2000;
  min-width: 220px;
  max-width: 320px;
  max-height: 240px;
  overflow-y: auto;
  background: var(--bg-card);
  border: 1px solid var(--border-color, #e5e7eb);
  border-radius: 8px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  padding: 4px 0;
  margin-top: 4px;
}

.mention-loading,
.mention-empty {
  padding: 10px 12px;
  font-size: 13px;
  color: var(--text-tertiary, #94a3b8);
  text-align: center;
}

.mention-candidate {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  cursor: pointer;
  transition: background 120ms ease;
}

.mention-candidate:hover,
.mention-candidate--active {
  background: var(--surface-hover, #f1f5f9);
}

.mention-candidate-name {
  font-size: 13px;
  color: var(--text-primary, #1e293b);
}

.mention-candidate-role {
  font-size: 11px;
  color: var(--text-tertiary, #94a3b8);
  background: var(--surface-hover, #f1f5f9);
  padding: 2px 8px;
  border-radius: 10px;
}
</style>
