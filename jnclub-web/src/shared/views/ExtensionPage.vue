<script setup lang="ts">
/**
 * ExtensionPage.vue — 浏览器插件「JNClub 收藏助手」下载与安装引导
 * 公开页（无需登录）：下载 zip + 图文安装步骤
 */
import { NButton, NIcon, NSteps, NStep, NAlert } from 'naive-ui'
import { Download, MousePointerClick, ListChecks, MousePointer, ShieldCheck, Puzzle, ChevronRight, FileText } from 'lucide-vue-next'

const EXT_VERSION = '1.1.0'
// zip 为 public/extension.zip 单文件：不用 extension/ 目录，避免与 SPA 路由 /extension 冲突（nginx 403）
const zipUrl = `${import.meta.env.BASE_URL}extension.zip`

const features = [
  { icon: MousePointerClick, title: '当前页一键收藏', desc: '点工具栏图标，自动带出当前页标题/网址，选目录即收藏' },
  { icon: ListChecks, title: '批量收藏标签页', desc: '列出全部打开的标签页，勾选批量收藏，已收藏自动去重' },
  { icon: MousePointer, title: '右键菜单收藏', desc: '任意网页右键 →「收藏到 JNClub」，收藏后桌面通知' },
  { icon: FileText, title: '网页转 Markdown 便签', desc: '右键或弹窗一键把文章正文转为 Markdown 便签，自动去导航/广告' },
]

const steps = [
  { title: '下载插件包', desc: '点击下方按钮下载 zip 压缩包' },
  { title: '解压到固定目录', desc: '解压到本地目录（建议 ~/Extensions/jnclub-extension），不要删除' },
  { title: '打开扩展管理', desc: '浏览器地址栏输入 chrome://extensions 并回车' },
  { title: '开启开发者模式', desc: '页面右上角打开「开发者模式」开关' },
  { title: '加载已解压的扩展', desc: '点「加载已解压的扩展程序」，选择刚才解压的目录' },
]

const download = () => {
  const a = document.createElement('a')
  a.href = zipUrl
  a.download = `jnclub-extension-v${EXT_VERSION}.zip`
  a.click()
}
</script>

<template>
  <div class="ext-page">
    <div class="ext-texture"></div>

    <div class="ext-inner">
      <!-- 品牌 -->
      <header class="brand">
        <div class="brand-logo">
          <NIcon :component="Puzzle" size="28" />
        </div>
        <h1 class="brand-name">JNClub 收藏助手</h1>
        <p class="brand-slogan">浏览器扩展 · 把网页一键收进 JNClub 工作台</p>
      </header>

      <!-- 功能 -->
      <section class="features">
        <div v-for="f in features" :key="f.title" class="feature-card">
          <div class="feature-icon"><NIcon :component="f.icon" size="22" /></div>
          <div>
            <div class="feature-title">{{ f.title }}</div>
            <div class="feature-desc">{{ f.desc }}</div>
          </div>
        </div>
      </section>

      <!-- 下载 -->
      <section class="download-box">
        <NButton type="primary" size="large" class="download-btn jnclub-bouncy" @click="download">
          <template #icon><NIcon :component="Download" /></template>
          下载插件包 v{{ EXT_VERSION }}
        </NButton>
        <p class="jn-hint">zip 约 60KB · 免费 · 支持 Chrome / Edge（基于 Chromium）</p>
        <NAlert type="warning" :bordered="false" class="install-alert">
          <template #icon><NIcon :component="ShieldCheck" /></template>
          安装后在「开发者模式」下加载，无需发布到商店；登录使用 JNClub 既有 SSO 账号。
        </NAlert>
      </section>

      <!-- 安装步骤 -->
      <section class="steps">
        <h2 class="steps-title">安装步骤</h2>
        <NSteps vertical size="small">
          <NStep
            v-for="(s, i) in steps"
            :key="i"
            :title="s.title"
            :description="s.desc"
            :status="'process'"
          >
            <template #icon>
              <span class="step-num">{{ i + 1 }}</span>
            </template>
          </NStep>
        </NSteps>
      </section>

      <footer class="foot">
        <NIcon :component="ChevronRight" size="14" style="color: var(--text-3)" />
        <router-link to="/" class="foot-link">返回 JNClub 工作台</router-link>
      </footer>
    </div>
  </div>
</template>

<style scoped>
.ext-page {
  position: relative;
  min-height: 100vh;
  padding: 40px 20px 60px;
  display: flex;
  justify-content: center;
  background:
    radial-gradient(1200px 500px at 10% -10%, var(--glass-glow-top), transparent 60%),
    radial-gradient(900px 400px at 110% 120%, var(--glass-glow-bottom), transparent 60%),
    var(--bg-page);
  overflow-x: hidden;
}
.ext-texture {
  position: fixed;
  inset: 0;
  pointer-events: none;
  background-image: radial-gradient(rgba(236, 91, 142, 0.08) 1px, transparent 1px);
  background-size: 24px 24px;
  opacity: 0.5;
}
.ext-inner {
  position: relative;
  z-index: 1;
  width: 100%;
  max-width: 640px;
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.brand {
  text-align: center;
}
.brand-logo {
  width: 64px;
  height: 64px;
  margin: 0 auto 14px;
  border-radius: 18px;
  background: var(--gradient-btn);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: var(--shadow-2);
}
.brand-name {
  font-size: 26px;
  font-weight: 800;
  color: var(--text-1);
  letter-spacing: 0.5px;
  margin-bottom: 8px;
}
.brand-slogan {
  font-size: 14px;
  color: var(--text-2);
}

.features {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.feature-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 16px;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  box-shadow: var(--glass-shadow);
}
.feature-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: var(--brand-soft);
  color: var(--brand);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.feature-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-1);
  margin-bottom: 3px;
}
.feature-desc {
  font-size: 13px;
  color: var(--text-2);
}

.download-box {
  padding: 22px;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--glass-shadow);
  text-align: center;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}
.download-btn {
  min-width: 220px;
  border-radius: var(--radius-pill);
  background: var(--gradient-btn);
  border: none;
}
.jn-hint {
  font-size: 12px;
  color: var(--text-3);
}
.install-alert {
  width: 100%;
  text-align: left;
  --n-color: var(--brand-soft) !important;
  --n-title-text-color: var(--text-1) !important;
  --n-close-color: var(--text-3) !important;
}

.steps-title {
  font-size: 18px;
  font-weight: 700;
  color: var(--text-1);
  margin-bottom: 16px;
}
.step-num {
  /* 绝对定位于 slot（slot 本身 position:relative），避免行内基线对齐造成的偏移 */
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: var(--brand-soft);
  color: var(--brand);
  font-size: 12px;
  font-weight: 700;
  line-height: 1;
}
:deep(.n-step) {
  --n-title-text-color: var(--text-1) !important;
  --n-description-text-color: var(--text-2) !important;
  --n-indicator-color: var(--brand-soft) !important;
  --n-indicator-text-color: var(--brand) !important;
  --n-line-color: var(--glass-border) !important;
  /* 指示器圆与内部数字槽同尺寸，确保数字严格居中 */
  --n-indicator-size: 28px !important;
  --n-indicator-icon-size: 28px !important;
  --n-indicator-index-font-size: 12px !important;
}

.foot {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  padding-top: 8px;
}
.foot-link {
  font-size: 13px;
  color: var(--link);
  text-decoration: none;
}
.foot-link:hover {
  text-decoration: underline;
}
</style>
