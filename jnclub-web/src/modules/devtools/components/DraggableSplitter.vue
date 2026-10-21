<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{ (e: 'update:ratio', r: number): void }>()

const dragging = ref(false)
const hovered = ref(false)

function startDrag(e: MouseEvent) {
  dragging.value = true
  const startX = e.clientX
  const parent = (e.target as HTMLElement).parentElement!
  const startW = parent.getBoundingClientRect().width
  let lastRatio = 0.5

  function onMove(ev: MouseEvent) {
    const dx = ev.clientX - startX
    lastRatio = Math.max(0.2, Math.min(0.8, 0.5 + dx / startW))
    emit('update:ratio', lastRatio)
  }

  function onUp() {
    dragging.value = false
    document.removeEventListener('mousemove', onMove)
    document.removeEventListener('mouseup', onUp)
    document.body.style.cursor = ''
    document.body.style.userSelect = ''
  }

  document.addEventListener('mousemove', onMove)
  document.addEventListener('mouseup', onUp)
  document.body.style.cursor = 'col-resize'
  document.body.style.userSelect = 'none'
}
</script>

<template>
  <div
    class="splitter"
    :class="{ dragging, hovered }"
    @mousedown="startDrag"
    @mouseenter="hovered = true"
    @mouseleave="hovered = false"
  />
</template>

<style scoped>
.splitter {
  position: relative;
  width: 5px;
  flex-shrink: 0;
  cursor: col-resize;
  background: transparent;
  transition: background 0.15s;
  z-index: 5;
  margin: 0 2px;
}
.splitter::after {
  content: '';
  position: absolute;
  top: 0; bottom: 0; left: 2px; right: 2px;
  border-radius: 3px;
  background: var(--json-border, rgba(232,76,111,0.1));
  transition: background 0.15s, left 0.15s, right 0.15s;
}
.splitter.hovered::after,
.splitter.dragging::after {
  left: 0; right: 0;
  background: var(--json-primary, #E84C6F);
}
</style>
