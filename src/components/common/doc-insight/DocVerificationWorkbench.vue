<!--
  Input: title, subtitle (strings); schema ({ groups: [{ id, title, fields: [{ key, label }] }] });
         data (Object – key/value map pre-filled by AI); requirements (Array<{ title, sourceExcerpt }>);
         markdown (String – raw document text rendered alongside extraction)
  Output: emits cancel(); emits confirm(localData) when user approves
  Pos: src/components/common/doc-insight/ - generic AI-extraction review/verify workbench
  一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
-->
<template>
  <div class="doc-verification-workbench">
    <div class="workbench-header">
      <div class="header-left">
        <h3>{{ title }}</h3>
        <p class="subtitle">{{ subtitle }}</p>
      </div>
      <div class="header-right">
        <el-button @click="$emit('cancel')">取消</el-button>
        <el-button type="primary" @click="$emit('confirm', localData)">确认并继续</el-button>
      </div>
    </div>

    <el-row :gutter="24" class="workbench-body">
      <el-col :span="10" class="scroll-panel">
        <el-form label-position="top">
          <div v-for="group in schema.groups" :key="group.id" class="info-group">
            <el-card shadow="never">
              <template #header>{{ group.title }}</template>
              <el-form-item v-for="field in group.fields" :key="field.key" :label="field.label">
                <el-input v-model="localData[field.key]" />
              </el-form-item>
            </el-card>
          </div>
          <el-card shadow="never" v-if="requirements?.length">
            <div
              v-for="(item, i) in requirements"
              :key="i"
              class="req-item"
              @click="highlightInMarkdown(item)"
            >
              {{ item.title }}
            </div>
          </el-card>
        </el-form>
      </el-col>
      <el-col :span="14" class="scroll-panel">
        <div class="markdown-container" ref="mdContainer" v-html="safeMarkdownHtml" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { renderSafeMarkdown } from './markdownSanitizer.js'

const props = defineProps({
  title: { type: String, default: '文档深度核对' },
  subtitle: { type: String, default: '请核对 AI 提取的关键信息及其在原文中的证据' },
  schema: { type: Object, default: () => ({ groups: [] }) },
  data: { type: Object, required: true },
  requirements: { type: Array, default: () => [] },
  markdown: { type: String, default: '' }
})

defineEmits(['cancel', 'confirm'])

const localData = ref({ ...props.data })
const mdContainer = ref(null)

const safeMarkdownHtml = computed(() => renderSafeMarkdown(props.markdown))

watch(
  () => props.data,
  (newVal) => {
    localData.value = { ...newVal }
  },
  { deep: true }
)

/**
 * Walk the rendered markdown container to find the deepest element whose
 * textContent includes the requirement's sourceExcerpt, then scroll it into
 * view and flash the is-highlighted class for 2 seconds.
 */
const highlightInMarkdown = (item) => {
  if (!mdContainer.value || !item?.sourceExcerpt) return

  const excerpt = item.sourceExcerpt
  const container = mdContainer.value

  const findDeepestMatch = (el) => {
    for (const child of Array.from(el.children)) {
      if (child.textContent?.includes(excerpt)) {
        return findDeepestMatch(child) || child
      }
    }
    return null
  }

  const target = findDeepestMatch(container) || container
  target.scrollIntoView({ behavior: 'smooth', block: 'center' })
  target.classList.add('is-highlighted')
  setTimeout(() => target.classList.remove('is-highlighted'), 2000)
}
</script>

<style scoped>
.doc-verification-workbench { height: 100%; display: flex; flex-direction: column; }
.workbench-header { padding: 16px; display: flex; justify-content: space-between; }
.workbench-body { flex: 1; overflow: hidden; padding: 16px; }
.scroll-panel { height: 100%; overflow-y: auto; }
.req-item { padding: 6px 8px; cursor: pointer; border-radius: 4px; }
.req-item:hover { background: var(--el-fill-color-light, #f5f5f5); }
.markdown-container :deep(.is-highlighted) {
  background: #fffbe6;
  outline: 2px solid #faad14;
  border-radius: 2px;
  transition: background 0.3s, outline 0.3s;
}
</style>
