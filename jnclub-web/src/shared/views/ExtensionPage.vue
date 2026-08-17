<script setup lang="ts">
/**
 * DownloadCenter (ExtensionPage.vue) — JNClub 下载中心（公开页，无需登录）
 * 同时承载两类下载：
 *   1) 浏览器插件「JNClub 收藏助手」（zip + 图文安装步骤）
 *   2) 桌面工具箱「JNX」（macOS dmg / Windows exe，来自 GitHub Release）
 */
import { NButton, NIcon, NSteps, NStep, NAlert } from 'naive-ui'
import {
  Download, MousePointerClick, ListChecks, MousePointer, ShieldCheck,
  ChevronRight, FileText, AppWindow, Boxes, TerminalSquare, ClipboardList, Keyboard, Database,
} from 'lucide-vue-next'
import BrandLogo from '../components/BrandLogo.vue'

/* ---------- 浏览器插件 ---------- */
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

const downloadZip = () => {
  const a = document.createElement('a')
  a.href = zipUrl
  a.download = `jnclub-extension-v${EXT_VERSION}.zip`
  a.click()
}

/* ---------- JNX 桌面工具箱 ---------- */
const JNX_VERSION = '0.1.0'
const JNX_TAG = 'master'
// 资产 URL 在 GitHub Release 就绪后确认；tag 保留为 master（流水线产物）
const jnxReleaseUrl = `https://github.com/JNNarrator/jnx/releases/tag/${JNX_TAG}`
const jnxAsset = {
  mac: 'https://github.com/JNNarrator/jnx/releases/download/master/jnx_0.1.0_aarch64.dmg',
  macIntel: 'https://github.com/JNNarrator/jnx/releases/download/master/jnx_0.1.0_x64.dmg',
  win: 'https://github.com/JNNarrator/jnx/releases/download/master/jnx_0.1.0_x64-setup.exe',
  msi: 'https://github.com/JNNarrator/jnx/releases/download/master/jnx_0.1.0_x64_en-US.msi',
}

const jnxFeatures = [
  { icon: Boxes, title: 'JSON 工具', desc: '格式化/压缩/校验 + 树形浏览、搜索、路径导航' },
  { icon: TerminalSquare, title: 'HTTP 客户端', desc: 'GET/POST/PUT/DELETE、cURL 导入、响应预览' },
  { icon: ClipboardList, title: '格式互转', desc: 'JSON / YAML / TOML / XML / CSV / Properties 双向转换' },
  { icon: Keyboard, title: '高频工具', desc: 'Cron 表达式可视化、JSON⇄JavaBean、剪贴板历史、全局快捷键' },
  { icon: AppWindow, title: '桌面体验', desc: 'Tauri 原生壳 · 语法高亮编辑器 · 浅色/深色主题' },
  { icon: Database, title: '本地数据', desc: '设置与历史记录存于本地 SQLite，不上传云' },
]
</script>

<template>
  <div class="ext-page">
    <div class="ext-texture"></div>

    <div class="ext-inner">
      <!-- 品牌 -->
      <header class="brand">
        <BrandLogo :size="64" :show-text="false" class="brand-mark" />
        <h1 class="brand-name">JNClub 下载中心</h1>
        <p class="brand-slogan">浏览器扩展 · 桌面工具箱 · 一站式下载</p>
      </header>

      <!-- ============ 浏览器插件 JNClub 收藏助手 ============ -->
      <section class="dl-section">
        <div class="section-head">
          <div class="section-icon"><NIcon :component="MousePointerClick" size="20" /></div>
          <div>
            <h2 class="section-title">JNClub 收藏助手</h2>
            <p class="section-sub">浏览器扩展 · 把网页一键收进 JNClub 工作台</p>
          </div>
        </div>

        <div class="features">
          <div v-for="f in features" :key="f.title" class="feature-card">
            <div class="feature-icon"><NIcon :component="f.icon" size="22" /></div>
            <div>
              <div class="feature-title">{{ f.title }}</div>
              <div class="feature-desc">{{ f.desc }}</div>
            </div>
          </div>
        </div>

        <div class="download-box">
          <NButton type="primary" size="large" class="download-btn jnclub-bouncy" @click="downloadZip">
            <template #icon><NIcon :component="Download" /></template>
            下载插件包 v{{ EXT_VERSION }}
          </NButton>
          <p class="jn-hint">zip 约 60KB · 免费 · 支持 Chrome / Edge（基于 Chromium）</p>
          <NAlert type="warning" :bordered="false" class="install-alert">
            <template #icon><NIcon :component="ShieldCheck" /></template>
            安装后在「开发者模式」下加载，无需发布到商店；登录使用 JNClub 既有 SSO 账号。
          </NAlert>
        </div>

        <div class="steps">
          <h3 class="steps-title">安装步骤</h3>
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
        </div>
      </section>

      <!-- ============ JNX 开发者桌面工具箱 ============ -->
      <section class="dl-section">
        <div class="section-head">
          <div class="section-icon jnx"><NIcon :component="AppWindow" size="20" /></div>
          <div>
            <h2 class="section-title">JNX 开发者桌面工具箱</h2>
            <p class="section-sub">本地桌面工具集 · macOS / Windows · v{{ JNX_VERSION }}</p>
          </div>
        </div>

        <div class="features">
          <div v-for="f in jnxFeatures" :key="f.title" class="feature-card">
            <div class="feature-icon jnx"><NIcon :component="f.icon" size="22" /></div>
            <div>
              <div class="feature-title">{{ f.title }}</div>
              <div class="feature-desc">{{ f.desc }}</div>
            </div>
          </div>
        </div>

        <div class="download-box">
          <div class="jnx-btns">
            <NButton tag="a" :href="jnxAsset.mac" target="_blank" rel="noopener" size="large" class="download-btn jnclub-bouncy">
              <template #icon><NIcon :component="Download" /></template>
              macOS · Apple 芯片
            </NButton>
            <NButton tag="a" :href="jnxAsset.macIntel" target="_blank" rel="noopener" size="large" class="download-btn jnclub-bouncy">
              <template #icon><NIcon :component="Download" /></template>
              macOS · Intel
            </NButton>
            <NButton tag="a" :href="jnxAsset.win" target="_blank" rel="noopener" size="large" class="download-btn jnclub-bouncy">
              <template #icon><NIcon :component="Download" /></template>
              Windows
            </NButton>
          </div>
          <p class="jn-hint">免费桌面端 · 数据存本地 SQLite · Windows 另提供 <a :href="jnxAsset.msi" target="_blank" rel="noopener" class="inline-link">MSI 安装包</a></p>
          <NAlert type="info" :bordered="false" class="install-alert">
            <template #icon><NIcon :component="ShieldCheck" /></template>
            Windows 首次运行如遇 SmartScreen 提示，选择「更多信息 → 仍要运行」；下载前可
            <a :href="jnxReleaseUrl" target="_blank" rel="noopener" class="inline-link">查看 GitHub Release</a> 校验版本与变更。
          </NAlert>
        </div>
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
  max-width: 680px;
  display: flex;
  flex-direction: column;
  gap: 28px;
}

.brand { text-align: center; }
.brand-mark { margin: 0 auto 14px; filter: drop-shadow(0 8px 18px var(--brand-soft)); }
.brand-name {
  font-size: 26px; font-weight: 800; color: var(--text-1); letter-spacing: 0.5px; margin-bottom: 8px;
}
.brand-slogan { font-size: 14px; color: var(--text-2); }

.dl-section {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 22px;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--glass-shadow);
}
.section-head { display: flex; align-items: center; gap: 12px; }
.section-icon {
  width: 40px; height: 40px; border-radius: 12px;
  background: var(--brand-soft); color: var(--brand);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.section-icon.jnx { background: var(--brand-soft); color: var(--brand); }
.section-title { font-size: 18px; font-weight: 700; color: var(--text-1); }
.section-sub { font-size: 13px; color: var(--text-2); }

.features { display: flex; flex-direction: column; gap: 10px; }
.feature-card {
  display: flex; align-items: center; gap: 14px; padding: 14px 16px;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-md);
  box-shadow: var(--glass-shadow);
}
.feature-icon {
  width: 44px; height: 44px; border-radius: 12px;
  background: var(--brand-soft); color: var(--brand);
  display: flex; align-items: center; justify-content: center; flex-shrink: 0;
}
.feature-icon.jnx { background: var(--brand-soft); color: var(--brand); }
.feature-title { font-size: 15px; font-weight: 600; color: var(--text-1); margin-bottom: 3px; }
.feature-desc { font-size: 13px; color: var(--text-2); }

.download-box {
  padding: 20px;
  background: var(--glass-bg-trans);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--glass-shadow);
  text-align: center;
  display: flex; flex-direction: column; align-items: center; gap: 12px;
}
.jnx-btns { display: flex; flex-wrap: wrap; gap: 12px; justify-content: center; }
.download-btn { min-width: 200px; border-radius: var(--radius-pill); background: var(--gradient-btn); border: none; }
.jn-hint { font-size: 12px; color: var(--text-3); }
.install-alert {
  width: 100%; text-align: left;
  --n-color: var(--brand-soft) !important;
  --n-title-text-color: var(--text-1) !important;
  --n-close-color: var(--text-3) !important;
}
.inline-link { color: var(--link); text-decoration: none; }
.inline-link:hover { text-decoration: underline; }

.steps-title { font-size: 15px; font-weight: 700; color: var(--text-1); margin-bottom: 14px; }
.step-num {
  position: absolute; inset: 0;
  display: flex; align-items: center; justify-content: center;
  border-radius: 50%; background: var(--brand-soft); color: var(--brand);
  font-size: 12px; font-weight: 700; line-height: 1;
}
:deep(.n-step) {
  --n-title-text-color: var(--text-1) !important;
  --n-description-text-color: var(--text-2) !important;
  --n-indicator-color: var(--brand-soft) !important;
  --n-indicator-text-color: var(--brand) !important;
  --n-line-color: var(--glass-border) !important;
  --n-indicator-size: 28px !important;
  --n-indicator-icon-size: 28px !important;
  --n-indicator-index-font-size: 12px !important;
}

.foot { display: flex; align-items: center; justify-content: center; gap: 4px; padding-top: 8px; }
.foot-link { font-size: 13px; color: var(--link); text-decoration: none; }
.foot-link:hover { text-decoration: underline; }
</style>
