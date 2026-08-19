<script setup lang="ts">
/**
 * ShareModal.vue — 公开只读分享弹窗
 * 对便签/收藏/云盘文件创建分享（可选密码 + 有效期），复制链接 / 撤销
 */
import { ref, watch } from 'vue'
import { NModal, NButton, NIcon, NInput, NSelect, useMessage } from 'naive-ui'
import { Link2, Copy, Trash2, Plus } from 'lucide-vue-next'
import { copyText } from '../../../shared/utils/clipboard'
import axios from 'axios'

const props = defineProps<{
  show: boolean
  refType: 'note' | 'bookmark' | 'file'
  refId: number | null
  name: string
}>()
const emit = defineEmits<{ 'update:show': [v: boolean] }>()

const message = useMessage()
const loading = ref(false)
const share = ref<{ token: string; url: string } | null>(null)
const hasPassword = ref(false)
const password = ref('')
const expiresInDays = ref<number | null>(null)

const EXPIRY_OPTIONS: any[] = [
  { label: '永不过期', value: 0 },
  { label: '1 天', value: 1 },
  { label: '7 天', value: 7 },
  { label: '30 天', value: 30 },
]

const load = async () => {
  if (!props.refId) return
  loading.value = true
  try {
    const res = await axios.get('/api/share', { params: { refType: props.refType, refId: props.refId } })
    const list = (res.data.code === 200 ? res.data.data : []) || []
    if (list.length) {
      const s = list[0]
      share.value = { token: s.token, url: `/jnclub/share/${s.token}` }
      hasPassword.value = !!s.passwordHash
    } else {
      share.value = null
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || '加载失败')
  } finally { loading.value = false }
}

watch(() => props.show, (v) => { if (v) { password.value = ''; expiresInDays.value = null; load() } })

const createShare = async () => {
  if (!props.refId) return
  try {
    const res = await axios.post('/api/share', {
      refType: props.refType,
      refId: props.refId,
      password: password.value || null,
      expiresInDays: expiresInDays.value,
    })
    if (res.data.code === 200) {
      const d = res.data.data
      share.value = { token: d.token, url: d.url }
      hasPassword.value = !!password.value
      message.success('分享已创建')
    }
  } catch (e: any) {
    message.error(e.response?.data?.message || '创建失败')
  }
}

const revokeShare = async () => {
  if (!share.value) return
  try {
    await axios.delete(`/api/share/${share.value.token}`)
    share.value = null
    message.success('分享已撤销')
  } catch (e: any) {
    message.error(e.response?.data?.message || '撤销失败')
  }
}

const fullUrl = () => (typeof window !== 'undefined' ? `${location.origin}${share.value?.url}` : share.value?.url || '')
const copyLink = async () => {
  if (!share.value) return
  if (await copyText(fullUrl())) message.success('链接已复制')
  else message.error('复制失败')
}
</script>

<template>
  <NModal
    :show="show"
    preset="card"
    :title="`分享 · ${name}`"
    style="width: 440px"
    :bordered="false"
    @update:show="(v: boolean) => emit('update:show', v)"
  >
    <div v-if="loading" class="share-loading">加载中…</div>

    <template v-else-if="share">
      <div class="share-url">
        <NIcon :component="Link2" size="15" />
        <span class="share-url-text" :title="fullUrl()">{{ fullUrl() }}</span>
      </div>
      <p class="share-tip">
        访客免登录查看<template v-if="hasPassword">，访问需密码</template>。
      </p>
      <div class="share-actions">
        <NButton size="small" type="primary" @click="copyLink">
          <template #icon><NIcon :component="Copy" size="14" /></template>
          复制链接
        </NButton>
        <NButton size="small" type="error" secondary @click="revokeShare">
          <template #icon><NIcon :component="Trash2" size="14" /></template>
          撤销分享
        </NButton>
      </div>
    </template>

    <template v-else>
      <div class="share-form">
        <label class="share-label">访问密码（可选）</label>
        <NInput v-model:value="password" placeholder="留空则免密查看" clearable />
        <label class="share-label">有效期</label>
        <NSelect v-model:value="expiresInDays" :options="EXPIRY_OPTIONS" />
      </div>
      <div class="share-actions">
        <NButton size="small" type="primary" :loading="loading" @click="createShare">
          <template #icon><NIcon :component="Plus" size="14" /></template>
          创建分享
        </NButton>
      </div>
    </template>
  </NModal>
</template>

<style scoped>
.share-loading { color: var(--text-3); font-size: var(--fs-sm); }
.share-url {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  color: var(--text-2);
}
.share-url-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: var(--fs-sm);
  font-family: var(--font-mono);
}
.share-tip { margin: 12px 0 14px; font-size: var(--fs-sm); color: var(--text-3); }
.share-actions { display: flex; gap: 10px; margin-top: 16px; justify-content: flex-end; }
.share-form { display: flex; flex-direction: column; gap: 8px; }
.share-label { font-size: var(--fs-sm); color: var(--text-2); margin-top: 6px; }
</style>
