<script setup lang="ts">
/**
 * Welcome.vue — 欢迎/引导页（未登录入口）
 * 风格与主应用一致（品牌粉色/毛玻璃/圆角，暗黑/白天双主题）
 * 已登录 → 「进入 JNClub」直达首页；未登录 → 引导去 SSO 登录，并展示「注册」按钮
 */
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NIcon } from 'naive-ui'
import { Sun, Moon, Link, PenLine, Cloud, KeyRound, Music, Trash2, ArrowRight, UserPlus } from 'lucide-vue-next'
import { useUserStore } from '../stores/user'
import BrandLogo from '../components/BrandLogo.vue'
import { JShinyText, JMagnet, JGlareHover } from '../components/animation'

defineProps<{
  isDark: boolean
}>()

const emit = defineEmits<{
  'toggle-theme': []
}>()

const router = useRouter()
const userStore = useUserStore()

/** 登录态：与路由守卫一致走 Pinia store（原用 localStorage jn-token 判定，双源不一致） */
const isLoggedIn = computed(() => userStore.isLoggedIn)

const features = [
  {
    icon: Link,
    title: '网页收藏夹',
    desc: '集中管理常用网址，目录分类、图标预览、标签筛选。',
  },
  {
    icon: PenLine,
    title: 'Markdown 便签',
    desc: '即时渲染写作，自动保存、大纲导航、图片上传。',
  },
  {
    icon: Cloud,
    title: '云盘',
    desc: '分片上传、断点续传，文件随取随用。',
  },
  {
    icon: KeyRound,
    title: '密码库',
    desc: 'AES 加密存储，主密钥解锁，安全守护账号密码。',
  },
  {
    icon: Music,
    title: '音乐',
    desc: '夜猫电台内嵌，播放队列、歌词、收藏一应俱全。',
  },
  {
    icon: Trash2,
    title: '回收站',
    desc: '软删除统一管理，误删可恢复、可彻底清除。',
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
        <BrandLogo :size="72" :show-text="false" class="brand-mark" />
        <JShinyText text="JNClub" className="brand-name" :speed="3" :spread="140" color="var(--brand)" />
        <p class="brand-slogan">个人工作台 · 收藏夹 / 便签 / 云盘 / 密码库，一站式打理你的数字生活</p>
      </header>

      <!-- 功能简介（模块墙，GlareHover 光标光泽跟随） -->
      <section class="features">
        <JGlareHover
          v-for="(f, i) in features"
          :key="f.title"
          :glare-color="'#ffffff'"
          :glare-opacity="0.35"
          :glare-size="320"
          :border-radius="'var(--radius-lg)'"
          :border-color="'transparent'"
          class="feature-card-wrap"
          :style="{ animationDelay: `${i * 60}ms` }"
        >
          <div class="feature-card">
            <div class="feature-icon">
              <NIcon :component="f.icon" size="22" />
            </div>
            <h3 class="feature-title">{{ f.title }}</h3>
            <p class="feature-desc">{{ f.desc }}</p>
          </div>
        </JGlareHover>
      </section>

      <!-- 操作区 -->
      <footer class="actions">
        <JMagnet :magnet-strength="3" :padding="120">
          <NButton size="large" type="primary" class="btn-enter" @click="goApp">
            <template #icon><NIcon :component="ArrowRight" size="18" /></template>
            进入 JNClub
          </NButton>
        </JMagnet>
        <JMagnet v-if="!isLoggedIn" :magnet-strength="3" :padding="100">
          <NButton size="large" quaternary class="btn-register" @click="goRegister">
            <template #icon><NIcon :component="UserPlus" size="18" /></template>
            注册账号
          </NButton>
        </JMagnet>
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
.brand { margin-bottom: 40px; }
.brand-mark {
  display: inline-flex;
  margin-bottom: 20px;
  filter: drop-shadow(0 12px 24px var(--brand-soft));
}
.brand-name {
  display: inline-block;
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

/* 功能卡片（模块墙，3 列自适应；GlareHover 外层 wrapper，内部卡片撑满） */
.features {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-bottom: 40px;
}
.feature-card-wrap {
  opacity: 0;
  animation: welcome-fade-up .5s var(--ease) forwards;
  /* 覆盖 vendor GlareHover 外层的 overflow-hidden：
     否则卡片 hover 上飘 translateY(-4px) 时，顶部 4px 被外层裁剪，上边框看起来被切掉/挡住 */
  overflow: visible;
}
.feature-card {
  padding: 24px 20px;
  height: 100%;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  text-align: left;
  transition: transform .22s ease, box-shadow .22s ease, border-color .22s ease;
}
.feature-card-wrap:hover .feature-card {
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
  background: var(--gradient-btn);
  border: none;
  box-shadow: var(--shadow-fab);
}
.btn-enter:hover {
  box-shadow: var(--shadow-fab-hover);
}
.btn-register {
  min-width: 150px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--glass-border);
  color: var(--text-2);
}

@keyframes welcome-fade-up {
  from { opacity: 0; transform: translateY(12px); }
  to { opacity: 1; transform: translateY(0); }
}

@media (max-width: 720px) {
  .features { grid-template-columns: 1fr 1fr; }
  .brand-name { font-size: 32px; }
  .welcome-inner { padding: 32px 20px 32px; }
}
@media (max-width: 480px) {
  .features { grid-template-columns: 1fr; }
}
</style>
