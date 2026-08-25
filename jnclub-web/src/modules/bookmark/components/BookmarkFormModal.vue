<script setup lang="ts">
/**
 * BookmarkFormModal.vue — 收藏新建 / 编辑弹窗
 * 包含 URL 预览、标题/目录/标签表单；成功保存后 emit('saved')。
 */
import { ref, watch, computed, nextTick } from 'vue'
import {
  NModal, NForm, NFormItem, NInput, NSpace, NSelect, NAvatar, NSpin, NButton, NIcon, useMessage,
} from 'naive-ui'
import { Link, Globe } from 'lucide-vue-next'
import axios from 'axios'
import TagPicker from './TagPicker.vue'
import { setRefTags } from '../composables/tags'

const props = withDefaults(defineProps<{
  show: boolean
  editingBookmark?: { id: number; title: string; url: string; directoryId: number | null } | null
  defaultDirectoryId?: number | null
  directoryOptions: { label: string; value: number }[]
}>(), {
  editingBookmark: null,
  defaultDirectoryId: null,
  directoryOptions: () => [],
})

const emit = defineEmits<{
  'update:show': [v: boolean]
  saved: []
}>()

const message = useMessage()

const form = ref({ title: '', url: '', directoryId: null as number | null })
const creating = ref(false)
const previewIcon = ref('')
const previewTitle = ref('')
const previewLoading = ref(false)
let previewTimer: ReturnType<typeof setTimeout> | null = null

const editTagPickerRef = ref<InstanceType<typeof TagPicker> | null>(null)
const createTagPickerRef = ref<InstanceType<typeof TagPicker> | null>(null)
const tagSaveTrigger = ref(0)

const isEdit = computed(() => props.editingBookmark?.id != null)
const editingId = computed(() => props.editingBookmark?.id ?? null)

const init = () => {
  if (isEdit.value && props.editingBookmark) {
    form.value = {
      title: props.editingBookmark.title || '',
      url: props.editingBookmark.url || '',
      directoryId: props.editingBookmark.directoryId ?? props.defaultDirectoryId,
    }
  } else {
    form.value = { title: '', url: '', directoryId: props.defaultDirectoryId }
  }
  previewIcon.value = ''
  previewTitle.value = ''
  previewLoading.value = false
}

watch(() => props.show, (v) => {
  if (v) init()
})

const isValidUrl = (url: string) => {
  try { new URL(url); return true } catch { return false }
}

const onUrlInput = () => {
  if (previewTimer) clearTimeout(previewTimer)
  const url = form.value.url.trim()
  if (!isValidUrl(url)) { previewIcon.value = ''; previewTitle.value = ''; return }
  previewTimer = setTimeout(async () => {
    previewLoading.value = true
    try {
      const res = await axios.get('/api/bookmarks/preview', { params: { url } })
      if (res.data.code === 200 && res.data.data) {
        previewTitle.value = res.data.data.title || ''
        previewIcon.value = res.data.data.icon || ''
        if (!form.value.title.trim()) form.value.title = previewTitle.value
      }
    } catch { /* 静默 */ }
    finally { previewLoading.value = false }
  }, 600)
}

const save = async () => {
  if (!form.value.url.trim()) { message.warning('请输入网址'); return }
  if (!isValidUrl(form.value.url.trim())) { message.warning('请输入正确的网址'); return }
  if (!form.value.title.trim()) { message.warning('请输入标题'); return }
  if (!form.value.directoryId && props.defaultDirectoryId) {
    form.value.directoryId = props.defaultDirectoryId
  }
  if (!form.value.directoryId) { message.warning('请选择目录'); return }

  creating.value = true
  try {
    if (editingId.value !== null) {
      await axios.put(`/api/bookmarks/${editingId.value}`, {
        title: form.value.title.trim(),
        url: form.value.url.trim(),
        directoryId: form.value.directoryId,
      })
      message.success('保存成功')
    } else {
      const created = await axios.post('/api/bookmarks', {
        title: form.value.title.trim(),
        url: form.value.url.trim(),
        directoryId: form.value.directoryId,
      })
      message.success('收藏成功')
      // 创建态标签持久化（refId 为空时由 TagPicker 收集选中名，创建成功后按新 id 写入）
      if (created.data?.code === 200 && created.data?.data?.id) {
        const names = createTagPickerRef.value?.getSelectedNames() ?? []
        if (names.length) {
          await setRefTags('bookmark', created.data.data.id, names)
        }
      }
    }
    // 编辑态保存标签
    if (editingId.value !== null) {
      tagSaveTrigger.value++
      await nextTick()
      editTagPickerRef.value?.save()
    }
    emit('update:show', false)
    emit('saved')
  } catch (e: any) {
    message.error(e.response?.data?.message || '保存失败')
  } finally { creating.value = false }
}
</script>

<template>
  <NModal
    :show="show"
    preset="dialog"
    :title="isEdit ? '编辑收藏' : '添加收藏'"
    @update:show="(v: boolean) => emit('update:show', v)"
  >
    <NForm :model="form" style="margin-top: 12px;">
      <NFormItem label="网址" path="url">
        <NInput v-model:value="form.url" placeholder="https://example.com" clearable autofocus @input="onUrlInput">
          <template #prefix><NIcon :component="Link" /></template>
        </NInput>
      </NFormItem>
      <div v-if="previewTitle || previewLoading" class="preview-bar">
        <NSpin :show="previewLoading" size="small">
          <NAvatar v-if="previewIcon" :src="previewIcon" size="small" round class="preview-avatar" />
          <NIcon v-else :component="Globe" size="20" style="color: var(--text-3); flex-shrink: 0;" />
        </NSpin>
        <span class="preview-title">{{ previewTitle || '正在获取网页信息…' }}</span>
      </div>
      <NFormItem label="标题" path="title">
        <NInput v-model:value="form.title" placeholder="留空自动从网页获取" clearable />
      </NFormItem>
      <NFormItem label="所属目录" path="directoryId">
        <NSelect v-model:value="form.directoryId" :options="directoryOptions" placeholder="选择目录" clearable />
      </NFormItem>
      <NFormItem v-if="isEdit" label="标签" path="tags">
        <TagPicker ref="editTagPickerRef" ref-type="bookmark" :ref-id="editingId" :save-trigger="tagSaveTrigger" />
      </NFormItem>
      <NFormItem v-else label="标签" path="tags">
        <TagPicker ref="createTagPickerRef" ref-type="bookmark" :ref-id="null" />
      </NFormItem>
    </NForm>
    <template #action>
      <NSpace>
        <NButton @click="emit('update:show', false)">取消</NButton>
        <NButton type="primary" :loading="creating" @click="save">确定</NButton>
      </NSpace>
    </template>
  </NModal>
</template>

<style scoped>
.preview-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  margin-bottom: 16px;
  background: var(--glass-chip-bg);
  border-radius: var(--radius-sm);
  font-size: var(--fs-md);
  color: var(--text-2);
}
.preview-avatar { flex-shrink: 0; }
.preview-title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
