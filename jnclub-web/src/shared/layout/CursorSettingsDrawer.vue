<script setup lang="ts">
/**
 * CursorSettingsDrawer.vue — 光标样式设置抽屉（侧栏底部「光标样式」唤起）
 * 两种风格：圆点+光环 / Emoji 光标，选择即持久化到用户偏好（cursor.style，后端+localStorage）
 * 打开时重读偏好（后端水合后以后端为准）
 */
import { ref, watch } from 'vue'
import { NDrawer, NIcon } from 'naive-ui'
import { MousePointer2, Sparkles } from 'lucide-vue-next'
import { useUserPreferences } from '../composables/useUserPreferences'
import { useCustomCursor, type CursorStyle } from '../composables/useCustomCursor'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const prefs = useUserPreferences()
const cursor = useCustomCursor()

const style = ref<CursorStyle>('dot-halo')

/** 打开抽屉时重读偏好（后端水合后以后端为准） */
watch(() => props.show, (v) => {
  if (v) {
    style.value = prefs.get<CursorStyle>('cursor.style', 'dot-halo')
    cursor.style.value = style.value
  }
})

/** 选择即持久化并全局生效 */
const selectStyle = (s: CursorStyle) => {
  style.value = s
  cursor.setStyle(s)
}

/** 移动端抽屉全宽，桌面 420（复用 SearchDrawer/NavEditorDrawer 形态） */
const isMobileWidth = () => (typeof window !== 'undefined' && window.innerWidth < 768 ? '100%' : 420)

const options: { key: CursorStyle; label: string; desc: string; icon: 'dot' | 'emoji' }[] = [
  { key: 'dot-halo', label: '圆点 + 光环', desc: '主圆点即时跟随，光环弹性拖尾；悬停放大变色', icon: 'dot' },
  { key: 'emoji', label: 'Emoji 光标', desc: '🐾 跟随鼠标，悬停变 💗，点击变 ✨', icon: 'emoji' },
]
</script>

<template>
  <NDrawer
    v-model:show="props.show"
    :width="isMobileWidth()"
    placement="right"
    class="cursor-settings-drawer"
    @update:show="(v: boolean) => !v && emit('close')"
  >
    <div class="cursor-settings-panel">
      <div class="settings-header">
        <div class="settings-title">
          <NIcon :component="MousePointer2" size="16" />
          光标样式
        </div>
        <span class="settings-hint">选择喜欢的鼠标样式，实时生效</span>
      </div>

      <div class="settings-section">
        <div class="section-label">风格</div>
        <div class="style-list">
          <button
            v-for="opt in options"
            :key="opt.key"
            type="button"
            :class="['style-option', 'jnclub-bouncy', { active: style === opt.key }]"
            @click="selectStyle(opt.key)"
          >
            <span class="style-preview">
              <template v-if="opt.icon === 'dot'">
                <span class="preview-dot" /><span class="preview-halo" />
              </template>
              <span v-else class="preview-emoji">🐾</span>
            </span>
            <span class="style-meta">
              <span class="style-label">{{ opt.label }}</span>
              <span class="style-desc">{{ opt.desc }}</span>
            </span>
            <NIcon :component="Sparkles" size="16" class="style-check" />
          </button>
        </div>
      </div>

      <div class="settings-foot">
        <span class="foot-hint">触屏设备自动隐藏；输入框内显示系统光标</span>
      </div>
    </div>
  </NDrawer>
</template>

<style scoped>
.cursor-settings-drawer :deep(.n-drawer-body-content-wrapper) {
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
}

.cursor-settings-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  padding: 4px 2px;
}

.settings-header {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.settings-title {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-1);
}
.settings-hint {
  font-size: 12px;
  color: var(--text-3);
}

.settings-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.section-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-3);
  letter-spacing: 0.5px;
}

.style-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.style-option {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-sm);
  background: var(--glass-bg-trans);
  text-align: left;
  cursor: pointer;
  transition: border-color var(--dur) var(--ease), background-color var(--dur) var(--ease);
}
.style-option:hover {
  border-color: color-mix(in srgb, var(--brand) 50%, var(--glass-border));
}
.style-option.active {
  border-color: var(--brand);
  background: var(--brand-soft);
}

/* 预览：圆点+光环 / emoji */
.style-preview {
  position: relative;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-radius: var(--radius-sm);
  background: var(--glass-chip-bg);
}
.preview-dot {
  position: absolute;
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: var(--brand);
  z-index: 1;
}
.preview-halo {
  position: absolute;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 1.5px solid color-mix(in srgb, var(--brand) 60%, transparent);
  background: color-mix(in srgb, var(--brand) 8%, transparent);
}
.preview-emoji {
  font-size: 26px;
  line-height: 1;
}

.style-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.style-label {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
}
.style-desc {
  font-size: 12px;
  color: var(--text-3);
  line-height: 1.5;
}
.style-check {
  flex-shrink: 0;
  color: var(--text-4);
}
.style-option.active .style-check {
  color: var(--brand);
}

.settings-foot {
  border-top: 1px solid var(--glass-border);
  padding-top: 12px;
}
.foot-hint {
  font-size: 12px;
  color: var(--text-3);
}
</style>
