<script setup lang="ts">
/**
 * MoveItemModal.vue — 共享「移动到目录」弹窗
 * 收藏夹/便签/云盘/密码库 item 通用：按模块 type 拉取目录树（层级缩进），
 * 选中目标目录后调对应模块的 move 接口，成功后 emit('refresh') 走统一刷新链路。
 * 目录字段四模块统一为 directoryId，接口形态也统一为 PUT /api/<module>/{id}/move。
 */
import { ref, watch } from 'vue'
import { NModal, NForm, NFormItem, NSelect, NButton, useMessage } from 'naive-ui'
import axios from 'axios'

const props = withDefaults(defineProps<{
  show: boolean
  /** 目录 type：1=收藏夹 2=便签 3=云盘 5=密码库 */
  itemType: number
  targets: { id: number; name: string }[]
  /** item 当前所在目录 id（选中该目录时提示已在该目录） */
  currentDirectoryId?: number | null
}>(), {
  currentDirectoryId: null,
})

const emit = defineEmits<{
  (e: 'update:show', v: boolean): void
  (e: 'refresh'): void
}>()

const message = useMessage()

const dirOptions = ref<{ label: string; value: number }[]>([])
const directoryId = ref<number | null>(null)
const submitting = ref(false)

/** 模块 → move 接口前缀 */
const API_PREFIX: Record<number, string> = {
  1: '/api/bookmarks',
  2: '/api/notes',
  3: '/api/clouddisk/files',
  5: '/api/vault',
}

const loadDirs = async () => {
  try {
    const res = await axios.get('/api/directories', { params: { type: props.itemType } })
    if (res.data.code === 200) {
      const flat: { label: string; value: number }[] = []
      const walk = (dirs: any[], prefix: string) => {
        for (const d of dirs) {
          flat.push({ label: prefix + d.name, value: d.id })
          if (d.children?.length) walk(d.children, prefix + d.name + ' / ')
        }
      }
      walk(res.data.data || [], '')
      dirOptions.value = flat
    }
  } catch { /* 静默：列表空时由后端兜底提示 */ }
}

watch(() => props.show, (v) => {
  if (v) {
    directoryId.value = null
    loadDirs()
  }
})

const submit = async () => {
  if (!directoryId.value) {
    message.warning('请选择目标目录')
    return
  }
  if (props.currentDirectoryId === directoryId.value) {
    message.info('已在该目录中')
    return
  }
  submitting.value = true
  try {
    const prefix = API_PREFIX[props.itemType]
    for (const t of props.targets) {
      await axios.put(`${prefix}/${t.id}/move`, { directoryId: directoryId.value })
    }
    message.success(`已移动 ${props.targets.length} 项`)
    emit('update:show', false)
    emit('refresh')
  } catch (e: any) {
    message.error(e.response?.data?.message || '移动失败')
  } finally {
    submitting.value = false
  }
}

const targetLabel = props.targets.length > 1 ? `（${props.targets.length} 项）` : ''
</script>

<template>
  <NModal
    :show="show"
    preset="dialog"
    :title="`移动到${targetLabel}`"
    @update:show="(v: boolean) => emit('update:show', v)"
  >
    <NForm style="margin-top: 12px;">
      <NFormItem label="目标目录">
        <NSelect
          v-model:value="directoryId"
          :options="dirOptions"
          placeholder="选择目标目录"
          filterable
          clearable
        />
      </NFormItem>
    </NForm>
    <template #action>
      <NButton @click="emit('update:show', false)">取消</NButton>
      <NButton type="primary" :loading="submitting" @click="submit">确定</NButton>
    </template>
  </NModal>
</template>
