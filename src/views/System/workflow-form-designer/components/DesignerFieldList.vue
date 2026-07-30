<template>
  <div class="field-list">
    <div class="section-title">
      <h2>字段配置器</h2>
      <div class="section-actions">
        <el-button @click="$emit('add-field')" :disabled="disableAddField">添加字段</el-button>
        <el-button @click="$emit('new-template', 'GENERAL')" type="info" plain :disabled="disableAddField">新建通用</el-button>
        <el-button @click="$emit('new-template', 'TENDER')" type="info" plain :disabled="disableAddField">新建标讯</el-button>
        <el-button @click="$emit('new-template', 'PROJECT')" type="info" plain :disabled="disableAddField">新建项目</el-button>
      </div>
    </div>

    <div
      v-for="(field, index) in fields"
      :key="field.key"
      class="field-row"
      :draggable="!isFixedGroup(field)"
      @dragstart="onDragStart(index)"
      @dragover.prevent="onDragOver(index)"
      @drop="onDrop(index)"
      @dragend="dragIndex = null"
      :class="{ 'drag-over': dragOverIndex === index, 'field-locked': isLocked(field), 'drag-disabled': isFixedGroup(field) }"
    >
      <el-tooltip v-if="isFixedGroup(field)" content="固定分组字段，不可拖拽排序，key 不可修改" placement="top">
        <span class="lock-icon">🔒</span>
      </el-tooltip>
      <el-tooltip v-else-if="isLocked(field)" content="核心字段，key 和类型不可修改" placement="top">
        <span class="lock-icon">🔒</span>
      </el-tooltip>
      <span v-else class="drag-handle" :title="'拖拽排序'">⠿</span>
      <el-input v-model="field.key" placeholder="字段 key" class="field-key-input" :disabled="isKeyLocked(field)" />
      <el-input v-model="field.label" placeholder="字段名称" class="field-label-input" />
      <el-select v-model="field.type" :disabled="isLocked(field)" @change="(v) => { $emit('normalize-field', field); if (['tender_source','project_status','qualification_type'].includes(v) && !field.optionsText) field.optionsText = $emit('get-enum-options', v) }" class="field-type-select">
        <el-option v-for="type in fieldTypes" :key="type.value" :label="type.label" :value="type.value" />
      </el-select>
      <el-checkbox v-model="field.required" :disabled="['info','section','divider'].includes(field.type)">必填</el-checkbox>
      <el-checkbox v-model="field.enabled">启用</el-checkbox>
      <el-tooltip :content="getFieldHelp(field.type)" placement="top" :show-after="300">
        <span class="field-help-badge">?</span>
      </el-tooltip>
      <el-button type="info" size="small" text @click="$emit('copy-field', index)" title="复制字段">复制</el-button>
      <el-button v-if="!isKeyLocked(field)" type="danger" size="small" text @click="$emit('delete-field', field.key)">删</el-button>

      <!-- select 类型选项 -->
      <el-input v-if="field.type === 'select'" v-model="field.optionsText" class="field-wide" placeholder="选项，格式：显示名=值，每行一个" type="textarea" :rows="2" />
      <!-- 枚举预填 -->
      <el-input v-if="['tender_source','project_status','qualification_type'].includes(field.type)" v-model="field.optionsText" class="field-wide" placeholder="枚举选项，格式：显示名=值，每行一个" type="textarea" :rows="2" />
      <!-- info 说明 -->
      <el-input v-if="field.type === 'info'" v-model="field.content" class="field-wide" placeholder="说明文本内容" type="textarea" :rows="2" />
      <!-- textarea 行数 -->
      <div v-if="field.type === 'textarea'" class="field-wide field-props-row">
        <el-input-number v-model="field.rows" :min="2" :max="20" placeholder="行数" />
        <span class="field-prop-label">行</span>
        <el-input v-model="field.placeholder" placeholder="占位提示文字" class="field-placeholder" />
      </div>
      <!-- number/currency 范围 -->
      <div v-if="['number','currency'].includes(field.type)" class="field-wide field-props-row">
        <el-input-number v-model="field.min" placeholder="最小值" />
        <span class="field-prop-label">~</span>
        <el-input-number v-model="field.max" placeholder="最大值" />
        <span class="field-prop-label">范围</span>
      </div>
      <!-- attachment 配置 -->
      <div v-if="field.type === 'attachment'" class="field-wide field-props-row">
        <el-input v-model="field.accept" placeholder="接受文件类型，如 .pdf,.doc" style="width: 240px" />
        <el-input-number v-model="field.limit" :min="1" :max="20" placeholder="数量上限" />
        <span class="field-prop-label">个文件</span>
      </div>
      <!-- table 列定义 -->
      <div v-if="field.type === 'table'" class="field-wide table-columns-editor">
        <div class="table-columns-label">表格列定义</div>
        <div v-for="(col, ci) in (field.columns || [])" :key="ci" class="table-col-row">
          <el-input v-model="col.key" placeholder="列key" />
          <el-input v-model="col.label" placeholder="列名" />
          <el-select v-model="col.type" placeholder="类型" style="width: 100px">
            <el-option label="文本" value="text" />
            <el-option label="数字" value="number" />
            <el-option label="下拉" value="select" />
          </el-select>
          <el-checkbox v-model="col.required">必填</el-checkbox>
          <el-button type="danger" size="small" text @click="field.columns.splice(ci, 1)">删</el-button>
        </div>
        <el-button size="small" @click="(field.columns = field.columns || []).push({ key: '', label: '', type: 'text', required: false })">+ 添加列</el-button>
        <el-input-number v-model="field.minRows" :min="1" :max="field.maxRows || 50" placeholder="最小行数" size="small" />
        <el-input-number v-model="field.maxRows" :min="field.minRows || 1" :max="100" placeholder="最大行数" size="small" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { FIELD_TYPE_HELP_TEXT, LOCKED_FIELD_KEYS, FIXED_GROUP_KEYS, KEY_LOCKED_FIELD_KEYS } from '../workflowFormDesignerCore.js'

const props = defineProps({
  fields: { type: Array, required: true },
  fieldTypes: { type: Array, required: true },
  disableAddField: { type: Boolean, default: false },
})

defineEmits(['add-field', 'delete-field', 'copy-field', 'new-template', 'normalize-field', 'get-enum-options'])

const dragIndex = ref(null)
const dragOverIndex = ref(null)

function getFieldHelp(type) { return FIELD_TYPE_HELP_TEXT[type] || '' }
function isLocked(field) { return LOCKED_FIELD_KEYS.includes(field.key) }
function isFixedGroup(field) { return FIXED_GROUP_KEYS.includes(field.key) }
function isKeyLocked(field) { return KEY_LOCKED_FIELD_KEYS.includes(field.key) }

function onDragStart(index) { dragIndex.value = index }
function onDragOver(index) { dragOverIndex.value = index }
function onDrop(index) {
  if (dragIndex.value === null || dragIndex.value === index) return
  const item = props.fields.splice(dragIndex.value, 1)[0]
  props.fields.splice(index, 0, item)
  dragIndex.value = null
  dragOverIndex.value = null
}
</script>

<style scoped>
.section-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12px; }
.section-title h2 { margin: 0; font-size: 15px; font-weight: 600; }
.section-actions { display: flex; gap: 8px; }
.field-row { display: flex; align-items: center; gap: 6px; padding: 8px 12px; margin-bottom: 6px; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; background: var(--el-fill-color-blank); flex-wrap: wrap; transition: border-color 0.2s; }
.field-row.drag-over { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.drag-handle { cursor: grab; color: var(--el-text-color-placeholder); font-size: 16px; user-select: none; padding: 0 2px; }
.drag-handle:active { cursor: grabbing; }
.field-row.field-locked { background: var(--el-fill-color-light); }
.field-row.drag-disabled { opacity: 0.85; }
.lock-icon { cursor: help; color: var(--el-text-color-secondary); font-size: 14px; padding: 0 2px; }
.field-key-input { width: 100px; }
.field-label-input { width: 140px; }
.field-type-select { width: 140px; }
.field-help-badge { display: inline-flex; align-items: center; justify-content: center; width: 18px; height: 18px; border-radius: 50%; background: var(--el-fill-color-dark); color: var(--el-text-color-secondary); font-size: 11px; cursor: help; }
.field-wide { width: 100%; margin-top: 4px; }
.field-props-row { display: flex; align-items: center; gap: 6px; margin-top: 4px; }
.field-prop-label { font-size: 12px; color: var(--el-text-color-secondary); white-space: nowrap; }
.field-placeholder { flex: 1; }
.table-columns-editor { width: 100%; margin-top: 4px; padding: 8px; background: var(--el-fill-color-light); border-radius: 4px; }
.table-columns-label { font-size: 12px; font-weight: 600; margin-bottom: 6px; color: var(--el-text-color-secondary); }
.table-col-row { display: flex; align-items: center; gap: 6px; margin-bottom: 4px; }
</style>
