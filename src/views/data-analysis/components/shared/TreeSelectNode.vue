<template>
  <div class="tsn-group">
    <template v-for="node in nodes" :key="node.value">
      <div
        class="ms-option"
        :class="{ checked: selectedSet.has(node.value) }"
        :style="{ paddingLeft: 14 + depth * 16 + 'px' }"
        @click="tree.toggle(node.value)"
      >
        <span
          v-if="hasChildren(node)"
          class="tsn-arrow"
          :class="{ open: tree.isExpanded(node.value) }"
          @click.stop="tree.toggleExpand(node.value)"
        >&#9654;</span>
        <span v-else class="tsn-arrow-spacer"></span>
        <span class="ms-check"></span>
        <span class="ms-label">{{ node.label }}</span>
      </div>
      <TreeSelectNode
        v-if="hasChildren(node) && tree.isExpanded(node.value)"
        :nodes="node.children"
        :depth="depth + 1"
      />
    </template>
  </div>
</template>

<script setup>
import { inject } from 'vue'

defineProps({
  nodes: { type: Array, default: () => [] },
  depth: { type: Number, default: 0 }
})

const tree = inject('treeSelectContext')
if (!tree) throw new Error('TreeSelectNode 必须在 TreeSelectDropdown 内使用')

function hasChildren(node) {
  return Array.isArray(node.children) && node.children.length > 0
}
</script>

<style scoped>
.tsn-group { width: 100%; }

.ms-option {
  display: flex;
  align-items: center;
  gap: 8px;
  padding-top: 6px;
  padding-bottom: 6px;
  padding-right: 14px;
  font-size: 12px;
  color: var(--status-neutral-color);
  cursor: pointer;
  transition: background 0.15s;
  white-space: nowrap;
  box-sizing: border-box;
}
.ms-option:hover { background: var(--brand-xiyu-logo-light); color: var(--brand-xiyu-logo); }
.ms-check {
  width: 14px;
  height: 14px;
  border: 1.5px solid var(--status-neutral-bg);
  border-radius: 3px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s;
}
.ms-option.checked .ms-check { background: var(--brand-xiyu-logo); border-color: var(--brand-xiyu-logo); }
.ms-option.checked .ms-check::after {
  content: '';
  width: 4px;
  height: 7px;
  border: solid var(--bg-white);
  border-width: 0 1.5px 1.5px 0;
  transform: rotate(45deg) translate(-1px, -1px);
}
.ms-label { flex: 1; }

.tsn-arrow {
  width: 14px;
  flex-shrink: 0;
  font-size: 9px;
  color: var(--gray-350);
  transition: transform 0.15s;
  text-align: center;
  line-height: 1;
}
.tsn-arrow:hover { color: var(--brand-xiyu-logo); }
.tsn-arrow.open { transform: rotate(90deg); }
.tsn-arrow-spacer { width: 14px; flex-shrink: 0; }
</style>
