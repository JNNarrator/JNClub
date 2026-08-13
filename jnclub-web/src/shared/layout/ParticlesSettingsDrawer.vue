<script setup lang="ts">
/**
 * ParticlesSettingsDrawer.vue — 背景特效设置抽屉（侧栏底部「背景特效」唤起）
 * 4 项：关闭 / 漂浮光点 / 雪落 / 花瓣飘落，选择即持久化到用户偏好（particles.style，后端+localStorage）
 */
import { ref, watch } from 'vue'
import { NDrawer, NIcon } from 'naive-ui'
import { Sparkles, Ban } from 'lucide-vue-next'
import { useUserPreferences } from '../composables/useUserPreferences'
import { useParticlesSettings, type ParticlesStyle } from '../composables/useParticlesSettings'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  close: []
}>()

const prefs = useUserPreferences()
const settings = useParticlesSettings()

const style = ref<ParticlesStyle>('none')

/** 打开抽屉时重读偏好（后端水合后以后端为准） */
watch(() => props.show, (v) => {
  if (v) {
    style.value = prefs.get<ParticlesStyle>('particles.style', 'none')
    settings.style.value = style.value
  }
})

/** 选择即持久化并全局生效 */
const selectStyle = (s: ParticlesStyle) => {
  style.value = s
  settings.setStyle(s)
}

/** 移动端抽屉全宽，桌面 420 */
const isMobileWidth = () => (typeof window !== 'undefined' && window.innerWidth < 768 ? '100%' : 420)

const options: { key: ParticlesStyle; label: string; desc: string; preview: 'off' | 'dots' | 'snow' | 'petals' }[] = [
  { key: 'none', label: '关闭', desc: '不显示背景特效（默认）', preview: 'off' },
  { key: 'float-dots', label: '漂浮光点', desc: '粉色光点缓慢上浮，最克制', preview: 'dots' },
  { key: 'snow', label: '雪落', desc: '细碎粉雪缓缓飘落', preview: 'snow' },
  { key: 'petals', label: '花瓣飘落', desc: '稍大粉色圆点轻盈下落', preview: 'petals' },
]
</script>

<template>
  <NDrawer
    v-model:show="props.show"
    :width="isMobileWidth()"
    placement="right"
    class="particles-settings-drawer"
    @update:show="(v: boolean) => !v && emit('close')"
  >
    <div class="particles-settings-panel">
      <div class="settings-header">
        <div class="settings-title">
          <NIcon :component="Sparkles" size="16" />
          背景特效
        </div>
        <span class="settings-hint">选择喜欢的粒子背景，实时生效</span>
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
            <span class="style-preview" :class="'preview-' + opt.preview">
              <NIcon v-if="opt.preview === 'off'" :component="Ban" size="18" />
              <template v-else>
                <span class="pdot" /><span class="pdot" /><span class="pdot" /><span class="pdot" />
              </template>
            </span>
            <span class="style-meta">
              <span class="style-label">{{ opt.label }}</span>
              <span class="style-desc">{{ opt.desc }}</span>
            </span>
            <span class="style-check"><NIcon :component="Sparkles" size="16" /></span>
          </button>
        </div>
      </div>

      <div class="settings-foot">
        <span class="foot-hint">便签编辑/预览与音乐页不显示；触屏设备自动关闭</span>
      </div>
    </div>
  </NDrawer>
</template>

<style scoped>
.particles-settings-drawer :deep(.n-drawer-body-content-wrapper) {
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
}

.particles-settings-panel {
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

/* 预览区：固定小方框，内放几个装饰点 */
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
  color: var(--text-3);
  overflow: hidden;
}
.pdot {
  position: absolute;
  border-radius: 50%;
  background: var(--brand);
  opacity: 0.7;
}
.preview-dots .pdot:nth-child(1) { width: 6px; height: 6px; top: 12px; left: 10px; }
.preview-dots .pdot:nth-child(2) { width: 4px; height: 4px; top: 26px; left: 24px; }
.preview-dots .pdot:nth-child(3) { width: 5px; height: 5px; top: 10px; left: 30px; }
.preview-dots .pdot:nth-child(4) { width: 4px; height: 4px; top: 32px; left: 14px; }
.preview-snow .pdot:nth-child(1) { width: 4px; height: 4px; top: 8px; left: 12px; background: #fff; }
.preview-snow .pdot:nth-child(2) { width: 5px; height: 5px; top: 18px; left: 28px; background: var(--pink-peach); }
.preview-snow .pdot:nth-child(3) { width: 4px; height: 4px; top: 30px; left: 16px; background: #fff; }
.preview-snow .pdot:nth-child(4) { width: 5px; height: 5px; top: 34px; left: 32px; background: var(--pink-peach); }
.preview-petals .pdot:nth-child(1) { width: 8px; height: 8px; top: 10px; left: 10px; }
.preview-petals .pdot:nth-child(2) { width: 7px; height: 7px; top: 24px; left: 26px; opacity: 0.5; }
.preview-petals .pdot:nth-child(3) { width: 6px; height: 6px; top: 30px; left: 8px; opacity: 0.6; }
.preview-petals .pdot:nth-child(4) { width: 7px; height: 7px; top: 8px; left: 30px; opacity: 0.5; }

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
