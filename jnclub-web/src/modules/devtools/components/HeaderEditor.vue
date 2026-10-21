<script setup lang="ts">
import type { HttpHeader } from '../stores/http'

const props = defineProps<{ headers: HttpHeader[] }>()
const emit = defineEmits<{ (e: 'update:headers', v: HttpHeader[]): void }>()

function update(i: number, f: 'key' | 'value', val: string) {
  const h = [...props.headers]
  h[i] = { ...h[i], [f]: val }
  if (i === h.length - 1 && (h[i].key || h[i].value)) {
    h.push({ key: '', value: '', enabled: true })
  }
  emit('update:headers', h)
}

function toggle(i: number) {
  const h = [...props.headers]
  h[i] = { ...h[i], enabled: !h[i].enabled }
  emit('update:headers', h)
}

function remove(i: number) {
  if (props.headers.length <= 1) {
    emit('update:headers', [{ key: '', value: '', enabled: true }])
    return
  }
  emit('update:headers', props.headers.filter((_, j) => j !== i))
}
</script>

<template>
  <div class="heditor">
    <div
      v-for="(h, i) in headers"
      :key="i"
      class="hrow"
      :class="{ disabled: !h.enabled }"
    >
      <input
        class="hin key"
        :value="h.key"
        @input="update(i, 'key', ($event.target as HTMLInputElement).value)"
        placeholder="键"
        aria-label="Header 键"
      />
      <input
        class="hin val"
        :value="h.value"
        @input="update(i, 'value', ($event.target as HTMLInputElement).value)"
        placeholder="值"
        aria-label="Header 值"
      />
      <button class="hbtn toggle" :class="{ off: !h.enabled }" @click="toggle(i)" :title="h.enabled ? '禁用' : '启用'" aria-label="切换启用">
        <svg viewBox="0 0 14 14" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.5">
          <path v-if="h.enabled" d="M2 7l3 4 7-8"/>
          <path v-else d="M3 3l8 8M11 3l-8 8"/>
        </svg>
      </button>
      <button class="hbtn del" @click="remove(i)" title="删除" aria-label="删除 header">
        <svg viewBox="0 0 14 14" width="12" height="12" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 4h8M5.5 4V3a.5.5 0 01.5-.5h2a.5.5 0 01.5.5v1M4 4v7a1 1 0 001 1h4a1 1 0 001-1V4"/></svg>
      </button>
    </div>
  </div>
</template>

<style scoped>
.heditor { display: flex; flex-direction: column; }
.hrow { display: flex; align-items: center; gap: 6px; padding: 4px 0; }
.hrow.disabled { opacity: 0.45; }
.hin {
  flex: 1; height: 32px; padding: 0 10px; font-size: 13px;
  border: 1px solid var(--border); border-radius: 6px;
  background: var(--bg-card);
  color: var(--text-1); outline: none;
  font-family: var(--font-mono);
  transition: border-color 0.15s;
}
.hin:focus { border-color: var(--brand); }
.hin.key { flex: 0 0 160px; }
.hin.val { flex: 1; }
.hin::placeholder { color: var(--text-3); }

.hbtn {
  width: 28px; height: 28px; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  border: 1px solid var(--border); border-radius: 6px;
  background: transparent; cursor: pointer;
  color: var(--text-2); transition: all 0.15s;
}
.hbtn:hover { border-color: var(--brand); color: var(--brand); }
.hbtn.toggle.off { color: var(--danger); }
.hbtn.del:hover { color: var(--danger); border-color: var(--danger); }
</style>
