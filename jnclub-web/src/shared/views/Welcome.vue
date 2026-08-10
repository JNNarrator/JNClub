<script setup lang="ts">
/**
 * Welcome.vue — 欢迎/引导页（未登录入口）
 * 风格与主应用一致（品牌粉色/毛玻璃/圆角，暗黑/白天双主题）
 * 已登录 → 「进入 JNClub」直达首页；未登录 → 引导去 SSO 登录，并展示「注册」按钮
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NIcon } from 'naive-ui'
import { Sun, Moon, Link, PenLine, ShieldCheck, ArrowRight, UserPlus, Sparkles } from 'lucide-vue-next'

defineProps<{
  isDark: boolean
}>()

const emit = defineEmits<{
  'toggle-theme': []
}>()

const router = useRouter()

/** 未登录判定：localStorage 无 token 即视为访客 */
const isLoggedIn = computed(() => !!localStorage.getItem('jn-token'))

const features = [
  {
    icon: Link,
    title: '网页收藏夹',
    desc: '集中管理常用网址，目录分类、图标预览，随手收藏随手找。',
  },
  {
    icon: PenLine,
    title: 'Markdown 便签',
    desc: '即时渲染写作，自动保存、大纲导航、代码高亮，灵感不丢失。',
  },
  {
    icon: ShieldCheck,
    title: 'SSO 统一认证',
    desc: '一套账号走遍所有服务，登录一次、处处通行。',
  },
]

/** 进入应用：已登录直达首页；未登录引导至 SSO 登录 */
const goApp = () => {
  if (isLoggedIn.value) {
    router.push('/')
  } else {
    window.location.href = import.meta.env.BASE_URL + 'sso/login'
  }
}

/** 注册（仅未登录展示） */
const goRegister = () => {
  window.location.href = import.meta.env.BASE_URL + 'sso/register'
}
</script>

<template>
  <div class="welcome-page">
    <!-- 点阵背景 -->
    <div class="welcome-texture"></div>

    <!-- 主题切换 -->
    <button type="button" class="theme-toggle" :title="isDark ? '切换到白天模式' : '切换到暗黑模式'" @click="emit('toggle-theme')">
      <NIcon :component="isDark ? Sun : Moon" size="18" />
    </button>

    <div class="welcome-inner">
      <!-- 品牌区 -->
      <header class="brand">
        <div class="brand-logo">
          <NIcon :component="Sparkles" size="30" />
        </div>
        <h1 class="brand-name">JNClub</h1>
        <p class="brand-slogan">个人工作台 · 收藏与便签，一站式打理你的数字生活</p>
      </header>

      <!-- 功能简介 -->
      <section class="features">
        <div v-for="f in features" :key="f.title" class="feature-card">
          <div class="feature-icon">
            <NIcon :component="f.icon" size="22" />
          </div>
          <h3 class="feature-title">{{ f.title }}</h3>
          <p class="feature-desc">{{ f.desc }}</p>
        </div>
      </section>

      <!-- 操作区 -->
      <footer class="actions">
        <NButton size="large" type="primary" class="btn-enter" @click="goApp">
          <template #icon><NIcon :component="ArrowRight" size="18" /></template>
          进入 JNClub
        </NButton>
        <NButton v-if="!isLoggedIn" size="large" quaternary class="btn-register" @click="goRegister">
          <template #icon><NIcon :component="UserPlus" size="18" /></template>
          注册账号
        </NButton>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.welcome-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background:
    radial-gradient(1200px 500px at 10% -10%, var(--glass-glow-top), transparent 60%),
    radial-gradient(900px 400px at 110% 120%, var(--glass-glow-bottom), transparent 60%),
    var(--bg-page);
  overflow: hidden;
}

/* 点阵纹理（与主应用一致） */
.welcome-texture {
  position: absolute;
  inset: 0;
  background-image: radial-gradient(var(--border) 1px, transparent 1px);
  background-size: 22px 22px;
  opacity: .35;
  pointer-events: none;
}

.theme-toggle {
  position: absolute;
  top: 22px;
  right: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-pill);
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  color: var(--text-2);
  cursor: pointer;
  transition: all .2s ease;
  z-index: 2;
}
.theme-toggle:hover {
  color: var(--brand);
  border-color: var(--brand);
  box-shadow: 0 4px 14px var(--brand-soft);
}

.welcome-inner {
  position: relative;
  z-index: 1;
  width: min(960px, 92vw);
  text-align: center;
  padding: 48px 48px 44px;
  background:
    radial-gradient(1200px 500px at 10% -10%, var(--glass-glow-top), transparent 60%),
    radial-gradient(900px 400px at 110% 120%, var(--glass-glow-bottom), transparent 60%),
    var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: 28px;
  box-shadow: var(--glass-shadow);
}

/* 品牌区 */
.brand { margin-bottom: 44px; }
.brand-logo {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 72px;
  height: 72px;
  border-radius: 22px;
  background: linear-gradient(135deg, var(--brand), var(--brand-hover));
  color: #fff;
  box-shadow: 0 12px 32px var(--brand-soft), inset 0 1px 0 rgba(255, 255, 255, 0.35);
  margin-bottom: 20px;
}
.brand-name {
  margin: 0 0 10px;
  font-size: 40px;
  font-weight: 800;
  letter-spacing: 1px;
  background: linear-gradient(120deg, var(--brand), var(--brand-suppl, var(--brand)));
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  color: var(--brand);
}
.brand-slogan {
  margin: 0;
  font-size: 15px;
  color: var(--text-2);
}

/* 功能卡片 */
.features {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-bottom: 44px;
}
.feature-card {
  padding: 28px 22px;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  text-align: left;
  transition: transform .22s ease, box-shadow .22s ease, border-color .22s ease;
}
.feature-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--glass-shadow);
  border-color: var(--brand);
}
.feature-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 46px;
  height: 46px;
  border-radius: 14px;
  background: var(--glass-chip-bg);
  border: 1px solid var(--glass-chip-border);
  color: var(--brand);
  margin-bottom: 14px;
}
.feature-title {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-1);
}
.feature-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--text-2);
}

/* 操作区 */
.actions {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  flex-wrap: wrap;
}
.btn-enter {
  min-width: 200px;
  border-radius: var(--radius-pill);
  font-weight: 600;
}
.btn-register {
  min-width: 150px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--glass-border);
  color: var(--text-2);
}

@media (max-width: 720px) {
  .features { grid-template-columns: 1fr; }
  .brand-name { font-size: 32px; }
  .welcome-inner { padding: 32px 20px 32px; }
}
</style>
