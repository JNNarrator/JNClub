<script setup lang="ts">
/**
 * ReadingModal.vue — 收藏阅读模式
 * 调后端 /api/bookmarks/read 抓取正文（服务端提取+清洗），站内沉浸阅读；
 * 失败时回退"在新标签页打开原文"。
 */
import { ref, watch } from 'vue'
import { NModal, NButton, NIcon, NSpin, NEmpty } from 'naive-ui'
import { ExternalLink, X, BookOpen } from 'lucide-vue-next'
import axios from 'axios'

const props = defineProps<{
  show: boolean
  url: string
}>()
const emit = defineEmits<{ 'update:show': [v: boolean] }>()

const loading = ref(false)
const error = ref('')
const article = ref<{ title: string; content: string } | null>(null)

watch(() => props.show, (v) => {
  if (v && props.url) load()
})

const load = async () => {
  loading.value = true
  error.value = ''
  article.value = null
  try {
    const res = await axios.get('/api/bookmarks/read', { params: { url: props.url }, timeout: 20000 })
    if (res.data.code === 200 && res.data.data?.success) {
      article.value = { title: res.data.data.title, content: res.data.data.content }
    } else {
      error.value = res.data.data?.reason || '正文提取失败'
    }
  } catch (e: any) {
    error.value = e.response?.data?.message || e.message || '网络错误'
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <NModal
    :show="props.show"
    @update:show="(v: boolean) => emit('update:show', v)"
    :style="{ width: 'min(880px, 94vw)' }"
    class="reading-modal"
  >
    <div class="reading-shell">
      <div class="reading-head">
        <div class="reading-title-wrap">
          <NIcon :component="BookOpen" size="16" class="reading-title-icon" />
          <span class="reading-title">{{ article?.title || '阅读模式' }}</span>
        </div>
        <div class="reading-actions">
          <a v-if="props.url" :href="props.url" target="_blank" rel="noopener" class="reading-open">
            <NIcon :component="ExternalLink" size="14" /> 打开原文
          </a>
          <NButton quaternary circle size="small" @click="emit('update:show', false)">
            <template #icon><NIcon :component="X" size="16" /></template>
          </NButton>
        </div>
      </div>

      <div class="reading-body">
        <NSpin :show="loading">
          <div v-if="loading" class="reading-hint">正在抓取正文…</div>
          <div v-else-if="error" class="reading-error">
            <NEmpty :description="error" class="reading-empty" />
            <a :href="props.url" target="_blank" rel="noopener" class="reading-fallback">
              在新标签页打开原文 →
            </a>
          </div>
          <article v-else-if="article" class="reading-article" v-html="article.content" />
        </NSpin>
      </div>
    </div>
  </NModal>
</template>

<style scoped>
.reading-shell {
  display: flex;
  flex-direction: column;
  max-height: 86vh;
  border-radius: var(--radius-md);
  background: var(--glass-bg-solid);
  overflow: hidden;
}
.reading-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--glass-border);
  flex-shrink: 0;
}
.reading-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.reading-title-icon { color: var(--brand); flex-shrink: 0; }
.reading-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.reading-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.reading-open {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: var(--fs-sm);
  color: var(--brand);
  text-decoration: none;
  padding: 4px 10px;
  border-radius: var(--radius-pill);
  background: var(--brand-soft);
}
.reading-open:hover { filter: brightness(1.05); }

.reading-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 8px 0;
}
.reading-hint {
  padding: 60px 0;
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--text-3);
}
.reading-error {
  padding: 40px 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.reading-empty :deep(.n-empty__description) { max-width: 360px; }
.reading-fallback {
  font-size: var(--fs-sm);
  color: var(--brand);
  text-decoration: none;
}
.reading-fallback:hover { text-decoration: underline; }

/* 阅读排版 */
.reading-article {
  max-width: 720px;
  margin: 0 auto;
  padding: 20px 28px 48px;
  font-size: 16px;
  line-height: 1.85;
  color: var(--text-1);
  word-break: break-word;
}
.reading-article :deep(h1),
.reading-article :deep(h2),
.reading-article :deep(h3),
.reading-article :deep(h4) {
  margin: 1.4em 0 0.6em;
  line-height: 1.4;
  font-weight: 700;
  color: var(--text-1);
}
.reading-article :deep(h1) { font-size: 1.6em; }
.reading-article :deep(h2) { font-size: 1.35em; }
.reading-article :deep(h3) { font-size: 1.15em; }
.reading-article :deep(p) { margin: 0.9em 0; }
.reading-article :deep(img) {
  max-width: 100%;
  height: auto;
  border-radius: 8px;
  margin: 12px 0;
}
.reading-article :deep(a) { color: var(--brand); text-decoration: none; }
.reading-article :deep(a):hover { text-decoration: underline; }
.reading-article :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 16px;
  border-left: 3px solid var(--brand);
  color: var(--text-2);
  background: var(--glass-chip-bg);
  border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
}
.reading-article :deep(pre) {
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-chip-border);
  border-radius: var(--radius-sm);
  padding: 14px;
  overflow-x: auto;
  font-size: 13px;
  line-height: 1.6;
}
.reading-article :deep(code) {
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
  background: var(--glass-chip-bg);
  border-radius: 4px;
  padding: 1px 5px;
  font-size: 0.92em;
}
.reading-article :deep(pre code) { background: none; padding: 0; }
.reading-article :deep(ul),
.reading-article :deep(ol) { margin: 0.8em 0; padding-left: 1.6em; }
.reading-article :deep(li) { margin: 0.3em 0; }
.reading-article :deep(table) {
  border-collapse: collapse;
  margin: 14px 0;
  width: 100%;
  font-size: 14px;
}
.reading-article :deep(th),
.reading-article :deep(td) {
  border: 1px solid var(--glass-chip-border);
  padding: 8px 12px;
  text-align: left;
}
.reading-article :deep(th) { background: var(--glass-chip-bg); font-weight: 600; }
</style>
