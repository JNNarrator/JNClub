<script setup lang="ts">
/**
 * CursorSettingsDrawer.vue — 光标样式设置抽屉（侧栏底部「光标样式」唤起）
 * 五种光标风格 + 三种点击特效 + 三种轨迹特效，选择即持久化到用户偏好
 * 打开时重读偏好（后端水合后以后端为准）
 */
import { ref, watch } from 'vue'
import { NDrawer, NIcon } from 'naive-ui'
import { MousePointer2, Sparkles, Zap, Route } from 'lucide-vue-next'
import { useUserPreferences } from '../composables/useUserPreferences'
import {
  useCustomCursor,
  type CursorStyle,
  type ClickEffectType,
  type TrailEffectType,
} from '../composables/useCustomCursor'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const prefs = useUserPreferences()
const cursor = useCustomCursor()

const style = ref<CursorStyle>('dot-halo')
const clickEffect = ref<ClickEffectType>('none')
const trailEffect = ref<TrailEffectType>('none')

/** 打开抽屉时重读偏好（后端水合后以后端为准） */
watch(() => props.show, (v) => {
  if (v) {
    style.value = prefs.get<CursorStyle>('cursor.style', 'dot-halo')
    cursor.style.value = style.value
    clickEffect.value = prefs.get<ClickEffectType>('cursor.clickEffect', 'none')
    cursor.clickEffect.value = clickEffect.value
    trailEffect.value = prefs.get<TrailEffectType>('cursor.trailEffect', 'none')
    cursor.trailEffect.value = trailEffect.value
  }
})

/** 选择即持久化并全局生效 */
const selectStyle = (s: CursorStyle) => {
  style.value = s
  cursor.setStyle(s)
}
const selectClickEffect = (e: ClickEffectType) => {
  clickEffect.value = e
  cursor.setClickEffect(e)
}
const selectTrailEffect = (t: TrailEffectType) => {
  trailEffect.value = t
  cursor.setTrailEffect(t)
}

/** 移动端抽屉全宽，桌面 420（复用 SearchDrawer/NavEditorDrawer 形态） */
const isMobileWidth = () => (typeof window !== 'undefined' && window.innerWidth < 768 ? '100%' : 420)

const options: { key: CursorStyle; label: string; desc: string; icon: 'dot' | 'emoji' | 'crosshair' | 'ring' | 'star' }[] = [
  { key: 'dot-halo', label: '圆点 + 光环', desc: '主圆点即时跟随，光环弹性拖尾；悬停放大变色', icon: 'dot' },
  { key: 'emoji', label: 'Emoji 光标', desc: '🐾 跟随鼠标，悬停变 💗，点击变 ✨', icon: 'emoji' },
  { key: 'crosshair', label: '十字准心', desc: '十字线弹性跟随，中心点即时定位', icon: 'crosshair' },
  { key: 'ring', label: '双层圆环', desc: '外环 lerp 缓动，内环即时跟随；悬停旋转', icon: 'ring' },
  { key: 'star', label: '星星光标', desc: '⭐ 跟随鼠标，悬停旋转放大', icon: 'star' },
]

const clickOptions: { key: ClickEffectType; label: string; desc: string; emoji: string }[] = [
  { key: 'none', label: '无', desc: '不显示点击特效', emoji: '—' },
  { key: 'star', label: '星星', desc: '点击时散开金色星星', emoji: '⭐' },
  { key: 'heart', label: '爱心', desc: '点击时散开粉色爱心', emoji: '💗' },
  { key: 'flower', label: '花朵', desc: '点击时散开柔和花瓣', emoji: '🌸' },
]

const trailOptions: { key: TrailEffectType; label: string; desc: string; color: string }[] = [
  { key: 'none', label: '无', desc: '不显示鼠标轨迹', color: 'transparent' },
  { key: 'rainbow', label: '彩虹', desc: '鼠标拖出彩色渐变轨迹', color: 'linear-gradient(90deg, #ff6b6b, #ffd93d, #6bcb77, #4d96ff, #9b59b6)' },
  { key: 'brand', label: '品牌粉', desc: '品牌粉色柔和拖尾', color: 'var(--brand)' },
  { key: 'pastel', label: '粉彩', desc: '柔和粉彩色系轮换', color: 'linear-gradient(90deg, #FFB7D5, #FFDAB9, #E8D5F5, #B5EAD7)' },
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
              <span v-else-if="opt.icon === 'emoji'" class="preview-emoji">🐾</span>
              <template v-else-if="opt.icon === 'crosshair'">
                <span class="preview-crosshair-h" /><span class="preview-crosshair-v" /><span class="preview-dot" />
              </template>
              <template v-else-if="opt.icon === 'ring'">
                <span class="preview-ring-outer" /><span class="preview-dot" />
              </template>
              <span v-else class="preview-emoji">⭐</span>
            </span>
            <span class="style-meta">
              <span class="style-label">{{ opt.label }}</span>
              <span class="style-desc">{{ opt.desc }}</span>
            </span>
            <NIcon :component="Sparkles" size="16" class="style-check" />
          </button>
        </div>
      </div>

      <!-- 点击特效 -->
      <div class="settings-section">
        <div class="section-label">
          <NIcon :component="Zap" size="14" />
          点击特效
        </div>
        <div class="style-list">
          <button
            v-for="opt in clickOptions"
            :key="opt.key"
            type="button"
            :class="['style-option', 'jnclub-bouncy', { active: clickEffect === opt.key }]"
            @click="selectClickEffect(opt.key)"
          >
            <span class="style-preview preview-click">
              <span class="preview-click-emoji">{{ opt.emoji }}</span>
            </span>
            <span class="style-meta">
              <span class="style-label">{{ opt.label }}</span>
              <span class="style-desc">{{ opt.desc }}</span>
            </span>
            <NIcon :component="Sparkles" size="16" class="style-check" />
          </button>
        </div>
      </div>

      <!-- 轨迹特效 -->
      <div class="settings-section">
        <div class="section-label">
          <NIcon :component="Route" size="14" />
          轨迹特效
        </div>
        <div class="style-list">
          <button
            v-for="opt in trailOptions"
            :key="opt.key"
            type="button"
            :class="['style-option', 'jnclub-bouncy', { active: trailEffect === opt.key }]"
            @click="selectTrailEffect(opt.key)"
          >
            <span class="style-preview preview-trail">
              <span
                class="preview-trail-line"
                :style="{ background: opt.color }"
              />
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

/* 新增光标预览：十字准心 */
.preview-crosshair-h {
  position: absolute;
  top: 50%;
  left: 8px;
  right: 8px;
  height: 1.5px;
  background: var(--brand);
  opacity: 0.7;
}
.preview-crosshair-v {
  position: absolute;
  left: 50%;
  top: 8px;
  bottom: 8px;
  width: 1.5px;
  background: var(--brand);
  opacity: 0.7;
}
/* 新增光标预览：圆环 */
.preview-ring-outer {
  position: absolute;
  width: 30px;
  height: 30px;
  border-radius: 50%;
  border: 1.5px solid color-mix(in srgb, var(--brand) 50%, transparent);
}

/* 点击特效预览 */
.preview-click {
  background: var(--glass-chip-bg);
}
.preview-click-emoji {
  font-size: 22px;
  line-height: 1;
}

/* 轨迹特效预览 */
.preview-trail {
  overflow: hidden;
}
.preview-trail-line {
  width: 100%;
  height: 3px;
  border-radius: 2px;
  opacity: 0.8;
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
