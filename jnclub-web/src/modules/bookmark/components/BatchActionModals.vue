<script setup lang="ts">
/**
 * BatchActionModals.vue — 批量移动 / 批量打标签弹窗
 * 收藏/便签共用；由 Home 通过 ref 调用 openMove() / openTags()。
 * 成功操作后 emit('done')，由父级统一退出多选并刷新数据。
 */
import { ref } from 'vue'
import { NModal, NButton, NForm, NFormItem, NSelect, useMessage } from 'naive-ui'
import axios from 'axios'
import { fetchTags } from '../composables/tags'

const props = defineProps<{
  activeModule: 'bookmarks' | 'notes'
  selectedIds: number[]
}>()

const emit = defineEmits<{
  done: []
}>()

const message = useMessage()

const showMove = ref(false)
const moveOptions = ref<{ label: string; value: number }[]>([])
const moveForm = ref({ directoryId: null as number | null })

const showTags = ref(false)
const tagOptions = ref<{ label: string; value: number }[]>([])
const tagIds = ref<number[]>([])

const loadDirs = async () => {
  const type = props.activeModule === 'bookmarks' ? 1 : 2
  try {
    const res = await axios.get('/api/directories', { params: { type } })
    const flat: { label: string; value: number }[] = []
    const walk = (dirs: any[], prefix: string) => {
      for (const d of dirs) {
        flat.push({ label: prefix + d.name, value: d.id })
        if (d.children?.length) walk(d.children, prefix + d.name + ' / ')
      }
    }
    walk(res.data.data || [], '')
    moveOptions.value = flat
  } catch { /* 静默 */ }
}

const openMove = async () => {
  await loadDirs()
  moveForm.value = { directoryId: null }
  showMove.value = true
}

const submitMove = async () => {
  if (!moveForm.value.directoryId) {
    message.warning('请选择目标目录')
    return
  }
  const url = props.activeModule === 'notes' ? '/api/notes/batch-move' : '/api/bookmarks/batch-move'
  try {
    await axios.put(url, { ids: props.selectedIds, directoryId: moveForm.value.directoryId })
    message.success(`已移动 ${props.selectedIds.length} 项`)
    showMove.value = false
    emit('done')
  } catch (e: any) {
    message.error(e.response?.data?.message || '移动失败')
  }
}

const openTags = async () => {
  const tags = await fetchTags('bookmark')
  tagOptions.value = tags.map(t => ({ label: t.name, value: t.id }))
  tagIds.value = []
  showTags.value = true
}

const submitTags = async () => {
  const names = tagOptions.value
    .filter(o => tagIds.value.includes(o.value))
    .map(o => o.label)
  try {
    await axios.put('/api/bookmarks/batch-tags', { ids: props.selectedIds, tagNames: names })
    message.success(`已更新 ${props.selectedIds.length} 项标签`)
    showTags.value = false
    emit('done')
  } catch (e: any) {
    message.error(e.response?.data?.message || '打标签失败')
  }
}

defineExpose({ openMove, openTags })
</script>

<template>
  <!-- 批量移动到弹窗 -->
  <NModal v-model:show="showMove" preset="dialog" :title="`移动到（${selectedIds.length} 项）`">
    <NForm style="margin-top: 12px;">
      <NFormItem label="目标目录">
        <NSelect v-model:value="moveForm.directoryId" :options="moveOptions" placeholder="选择目录" filterable clearable />
      </NFormItem>
    </NForm>
    <template #action>
      <NButton @click="showMove = false">取消</NButton>
      <NButton type="primary" @click="submitMove">确定</NButton>
    </template>
  </NModal>

  <!-- 批量打标签弹窗（收藏） -->
  <NModal v-model:show="showTags" preset="dialog" :title="`打标签（${selectedIds.length} 项）`">
    <p class="jn-hint" style="margin: 0 0 10px;">将覆盖所选收藏的现有标签（全量设置）。</p>
    <NSelect v-model:value="tagIds" :options="tagOptions" multiple filterable placeholder="选择或输入标签" />
    <template #action>
      <NButton @click="showTags = false">取消</NButton>
      <NButton type="primary" @click="submitTags">确定</NButton>
    </template>
  </NModal>
</template>
