<script setup lang="ts">
/**
 * TagPicker.vue — 标签选择器（NSelect multiple + tag 模式，支持创建）
 * 用于收藏/便签编辑场景：传入 refType + refId，自动加载已选标签
 */
import { ref, watch, onMounted } from 'vue'
import { NSelect, useMessage } from 'naive-ui'
import { fetchTags, fetchRefTags, setRefTags, type TagItem } from '../composables/tags'

const props = defineProps<{
  refType: 'bookmark' | 'note'
  refId: number | null
  /** 手动触发保存（如父组件提交时），依赖 watch on-demand */
  saveTrigger?: number
}>()

const emit = defineEmits<{
  saved: [names: string[]]
}>()

const message = useMessage()
const options = ref<{ label: string; value: number }[]>([])
const selectedIds = ref<number[]>([])
const allTags = ref<TagItem[]>([])

/** 选项变化同步 selectedIds 中不存在的 name（新创建标签时 NSelect 会自动带出） */
const selectedNames = () =>
  selectedIds.value
    .map(id => options.value.find(o => o.value === id)?.label)
    .filter((n): n is string => !!n)

const loadAll = async () => {
  allTags.value = await fetchTags(props.refType)
  options.value = allTags.value.map(t => ({ label: t.name, value: t.id }))
}

const loadSelected = async () => {
  if (!props.refId) { selectedIds.value = []; return }
  const tags = await fetchRefTags(props.refType, props.refId)
  selectedIds.value = tags.map(t => t.id)
}

const save = async () => {
  if (!props.refId) return
  try {
    await setRefTags(props.refType, props.refId, selectedNames())
    emit('saved', selectedNames())
  } catch (e: any) {
    message.error(e.message || '保存标签失败')
  }
}

watch(() => props.saveTrigger, (v) => {
  if (v) save()
})

onMounted(async () => {
  await Promise.all([loadAll(), loadSelected()])
})

defineExpose({ save, loadSelected })
</script>

<template>
  <NSelect
    v-model:value="selectedIds"
    multiple
    filterable
    tag
    :options="options"
    placeholder="选择或输入标签，回车创建"
    :clearable="true"
    class="tag-picker"
    size="small"
  />
</template>
