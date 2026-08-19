<script setup lang="ts">
/**
 * ShareView.vue — 公开只读分享页（访客免登录）
 * 便签 / 收藏 / 云盘文件的只读视图；支持访问密码 + 过期控制
 */
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { NButton, NIcon, NInput, NSpin, NTag } from 'naive-ui'
import { Download, Lock, StickyNote, Bookmark, FileText, ExternalLink } from 'lucide-vue-next'
import { formatDate } from '../../modules/bookmark/composables/formatDate'
import axios from 'axios'

const route = useRoute()
const token = computed(() => String(route.params.token || ''))
const pwd = ref('')
const loading = ref(true)
const locked = ref(false)
const error = ref('')
const data = ref<any>(null)

const load = async (withPwd: string) => {
  loading.value = true
  error.value = ''
  try {
    const res = await axios.get(`/api/share/${token.value}`, { params: withPwd ? { pwd: withPwd } : {} })
    if (res.data.code === 200) {
      const d = res.data.data
      if (d && d.locked) {
        locked.value = true
        data.value = null
      } else {
        locked.value = false
        data.value = d
      }
    } else {
      error.value = res.data.message || '加载失败'
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || '加载失败'
  } finally { loading.value = false }
}

const submitPwd = () => load(pwd.value)

const fileDownloadUrl = () => `/api/share/${token.value}/file${pwd.value ? `?pwd=${encodeURIComponent(pwd.value)}` : ''}`

onMounted(() => load(''))

const typeMeta: Record<string, { label: string; icon: any }> = {
  note: { label: '便签', icon: StickyNote },
  bookmark: { label: '收藏', icon: Bookmark },
  file: { label: '云盘文件', icon: FileText },
}
const meta = computed(() => typeMeta[data.value?.type] || { label: '', icon: FileText })
const bookmarkDomain = computed(() => {
  try { return data.value?.url ? new URL(data.value.url).hostname : '' } catch { return '' }
})
</script>

<template>
  <div class="share-page">
    <div class="share-card">
      <template v-if="loading">
        <div class="share-state"><NSpin /> <span>加载中…</span></div>
      </template>

      <template v-else-if="error">
        <div class="share-state share-error">{{ error }}</div>
      </template>

      <template v-else-if="locked">
        <div class="share-lock">
          <div class="lock-ic"><NIcon :component="Lock" size="30" /></div>
          <h2 class="share-title">请输入访问密码</h2>
          <p class="share-sub">此分享设置了密码保护</p>
          <NInput v-model:value="pwd" type="password" placeholder="访问密码" size="large" class="pwd-input" @keyup.enter="submitPwd" />
          <NButton type="primary" size="large" @click="submitPwd">查看内容</NButton>
        </div>
      </template>

      <template v-else-if="data">
        <header class="share-head">
          <NTag size="small" round :bordered="false" class="type-tag">
            <NIcon :component="meta.icon" size="13" /> {{ meta.label }}
          </NTag>
          <h1 class="share-title">{{ data.type === 'file' ? data.name : (data.type === 'bookmark' ? data.title : (data.title || '无标题')) }}</h1>
          <p v-if="data.createTime" class="share-sub">{{ formatDate(data.createTime) }} · 公开只读</p>
        </header>

        <div v-if="data.type === 'note'" class="share-content">
          <pre class="note-body">{{ data.content || '（空便签）' }}</pre>
        </div>

        <div v-else-if="data.type === 'bookmark'" class="share-content">
          <div class="bookmark-box">
            <span class="bookmark-domain">{{ bookmarkDomain }}</span>
          </div>
          <NButton type="primary" size="large" tag="a" :href="data.url" target="_blank" rel="noopener">
            <template #icon><NIcon :component="ExternalLink" size="16" /></template>
            打开链接
          </NButton>
        </div>

        <div v-else-if="data.type === 'file'" class="share-content">
          <div class="file-box">
            <NIcon :component="FileText" size="36" class="file-ic" />
            <div class="file-info">
              <div class="file-name">{{ data.name }}</div>
              <div class="file-meta">{{ data.size ? (data.size / 1024 / 1024).toFixed(1) + ' MB' : '' }}</div>
            </div>
          </div>
          <NButton type="primary" size="large" tag="a" :href="fileDownloadUrl()">
            <template #icon><NIcon :component="Download" size="16" /></template>
            下载文件
          </NButton>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.share-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(1200px 500px at 10% -10%, var(--glass-glow-top), transparent 60%),
    radial-gradient(900px 400px at 110% 120%, var(--glass-glow-bottom), transparent 60%),
    var(--bg-page);
}
.share-card {
  width: 100%;
  max-width: 560px;
  padding: 32px;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--glass-shadow);
}
.share-state { display: flex; align-items: center; gap: 10px; justify-content: center; color: var(--text-2); padding: 30px 0; }
.share-error { color: var(--danger); }
.share-lock { display: flex; flex-direction: column; align-items: center; gap: 12px; text-align: center; }
.lock-ic { color: var(--brand); }
.share-head { margin-bottom: 18px; }
.type-tag { margin-bottom: 10px; }
.share-title { font-size: 22px; font-weight: 800; color: var(--text-1); margin: 8px 0 4px; }
.share-sub { font-size: var(--fs-sm); color: var(--text-3); margin: 0; }
.share-content { display: flex; flex-direction: column; gap: 16px; align-items: center; }
.note-body {
  width: 100%;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: var(--font-sans);
  font-size: 14px;
  line-height: 1.8;
  color: var(--text-1);
  margin: 0;
}
.bookmark-box { width: 100%; padding: 16px; text-align: center; background: var(--glass-chip-bg); border-radius: var(--radius-md); }
.bookmark-domain { font-family: var(--font-mono); color: var(--brand); }
.file-box { display: flex; align-items: center; gap: 14px; width: 100%; padding: 16px; background: var(--glass-chip-bg); border-radius: var(--radius-md); }
.file-ic { color: var(--brand); flex-shrink: 0; }
.file-info { min-width: 0; }
.file-name { font-weight: 600; color: var(--text-1); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-meta { font-size: var(--fs-sm); color: var(--text-3); }
.pwd-input { width: 100%; max-width: 320px; }
</style>
