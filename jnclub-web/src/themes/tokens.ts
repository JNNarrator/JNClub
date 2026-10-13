/**
 * 设计 Token — Apple Pink 克制风格
 * 苹果灰底 + 粉色点缀 + 极轻阴影 + DM Sans 字体
 * 亮/暗双模式
 */

export interface DesignTokens {
  brand: string
  brandSoft: string
  brandHover: string
  brandPress: string
  brandSuppl: string
  link: string
  text1: string
  text2: string
  text3: string
  text4: string
  bgPage: string
  bgCard: string
  border: string
  split: string
  hoverBg: string
  // 粉色阶
  pinkCherry: string
  pinkPeach: string
  pinkRose: string
  pinkAdzuki: string
  pinkWhite: string
  // 渐变
  gradientTopBar: string
  gradientBtn: string
  gradientNavActive: string
  // 阴影
  shadow1: string
  shadow2: string
  /** 弹层阴影（气泡/下拉/Message/Notification）：比卡片重、比模态轻 */
  shadowPopover: string
  shadowCardHover: string
  shadowFab: string
  shadowFabHover: string
  glowIcon: string
  // 状态色
  stateSuccess: string
  stateWarning: string
  stateError: string
  stateWarningSoft: string
  stateErrorSoft: string
  gradientSuccess: string
  shadowSuccess: string
  // 语义化状态文字/背景（UI 重构后统一替代业务组件硬编码色）
  danger: string
  dangerSoft: string
  dangerText: string
  warningText: string
  successText: string
  // 模块色（概览趋势/统计/徽标）
  moduleBookmark: string
  moduleNote: string
  moduleFile: string
  moduleVault: string
  // 文件类型图标色
  fileTypeImage: string
  fileTypeDoc: string
  fileTypeArchive: string
  fileTypeAudio: string
  // 玻璃拟态体系（从密码库弹框提炼）
  glassBgSolid: string
  glassBgTrans: string
  glassBorder: string
  glassGlowTop: string
  glassGlowBottom: string
  glassShadow: string
  glassBlur: string
  glassInputBg: string
  glassInputBorder: string
  glassBtnPrimary: string
  glassBtnGhost: string
  glassBtnGhostHover: string
  glassChipBg: string
  glassChipBorder: string
  glassChipText: string
  glassTextSecondary: string
  glassTextPlaceholder: string
  focusRing: string
  /** 焦点环外晕（半透明，与实色 focusRing 叠成双层环） */
  focusRingSoft: string
  /** 品牌色作为正文文字时使用（亮色需压深到 4.5:1，故与 brand 分离） */
  brandText: string
  /** 品牌渐变/实色底上的文字色（亮粉底配深字，两端对比度均 ≥5:1） */
  textOnBrand: string
  // 主题级交互/反馈色（M1：滚动条、选区、遮罩、骨架、卡片态、面板）
  scrollbarThumb: string
  scrollbarThumbHover: string
  selectionBg: string
  selectionText: string
  overlayBg: string
  overlayBlur: string
  selectedBg: string
  /** 批量勾选框浮层的深色垫底：两主题同值，保证白勾选框在浅色卡片上仍可见 */
  checkScrim: string
  faviconScrim: string
  iconOnBrand: string
  // 光标特效配色（预设内容本身，非主题语义色；亮暗同值）
  cursorRainbow: string
  cursorPastel: string
  cursorStar1: string
  cursorStar2: string
  cursorStar3: string
  previewStage: string
  // 阅读模式「专注纸」——刻意脱离主色系，成对定义以便整体调暖
  readerPaperText: string
  readerPaperBg: string
  panelBg: string
  panelBorder: string
  panelShadow: string
  // 粒子调色板（亮色需在浅底上清晰 → 深紫红；暗色 → 柔和亮粉）
  particle1: string
  particle2: string
  particle3: string
  particle4: string
  fontSans: string
  fontMono: string
  fsXs: string
  fsSm: string
  fsMd: string
  fsBase: string
  radiusXs: string
  radiusSm: string
  radiusMd: string
  radiusLg: string
  radiusPill: string
  // 布局体系
  layoutContentMax: string
  layoutPageGutter: string
  layoutPagePadding: string
  layoutPanelPadding: string
  headerHeight: string
  tabbarHeight: string
  /** 移动端最小点击区（PLAN-2026-16 规范：最小 32px） */
  tapMin: string
  /** 空态占位纵向内边距（弹窗/抽屉内的大空态） */
  emptyPad: string
  /** 空态占位纵向内边距（下拉/菜单等紧凑容器） */
  emptyPadSm: string
  // 骨架屏（M2-4）
  /** 骨架底色 */
  skeletonBase: string
  /** 骨架扫光高光色 */
  skeletonHighlight: string
  // 动效时长（M2-3）：快 / 标准 / 慢 / 更慢
  durFast: string
  dur: string
  durSlow: string
  durSlower: string
  /** 循环类动画（骨架脉冲、警告点闪烁） */
  durLoop: string
  /** 图标环呼吸（刻意放慢） */
  durBreathe: string
  /** 加载旋转（sync-spin / dead-spin） */
  durSpin: string
  /** 列表逐项入场间隔 */
  durStagger: string
  /** 一次性倒计时环（语义时长，对应业务等待） */
  durCountdown: string
  /** 背景装饰性飘浮循环（花瓣等） */
  durFloat: string
  ease: string
  easeBouncy: string
  /** 匀速（旋转、扫光等机械运动） */
  easeLinear: string
  /** 往返循环（呼吸、飘浮等有机运动） */
  easeSmooth: string
}

export const lightTokens: DesignTokens = {
  brand: '#EC5B8E',
  brandSoft: 'rgba(236, 91, 142, 0.08)',
  brandHover: '#E84D7A',
  brandPress: '#D43F6A',
  brandSuppl: 'rgba(236, 91, 142, 0.12)',
  // 链接文字需 ≥4.5:1，品牌粉 #EC5B8E 在浅底上仅 2.99:1，故压深
  link: '#C9256A',
  text1: '#1D1D1F',
  text2: '#515154',
  text3: '#6E6E73',
  text4: '#C7C7CC',
  bgPage: '#F5F5F7',
  bgCard: '#FFFFFF',
  border: '#D1D1D6',
  split: '#B8B8BD',
  hoverBg: '#F2F2F7',
  // 粉色阶
  pinkCherry: '#FFE4EF',
  pinkPeach: '#FFB3C6',
  pinkRose: '#FF8FAB',
  pinkAdzuki: '#F472B6',
  pinkWhite: '#FFF5F8',
  // 渐变
  gradientTopBar: 'linear-gradient(90deg, #EC5B8E, #FFB3C6)',
  gradientBtn: 'linear-gradient(135deg, #EC5B8E, #FF8FAB)',
  gradientNavActive: 'linear-gradient(90deg, #FFE4EF 0%, transparent 100%)',
  // 阴影 — Apple 极轻
  shadow1: '0 1px 2px 0 rgba(0, 0, 0, 0.04)',
  shadow2: '0 2px 4px -1px rgba(0, 0, 0, 0.06), 0 1px 2px -1px rgba(0, 0, 0, 0.05)',
  shadowPopover: '0 8px 24px -6px rgba(0, 0, 0, 0.12), 0 2px 6px -1px rgba(0, 0, 0, 0.06)',
  shadowCardHover: '0 4px 8px -2px rgba(0, 0, 0, 0.06), 0 2px 4px -2px rgba(0, 0, 0, 0.05)',
  shadowFab: '0 4px 12px -2px rgba(236, 91, 142, 0.15)',
  shadowFabHover: '0 8px 20px -4px rgba(236, 91, 142, 0.2)',
  glowIcon: '0 0 0 4px rgba(236,91,142,0.06)',
  // 状态色
  stateSuccess: '#7AC686',
  stateWarning: '#F3C470',
  stateError: '#E87878',
  stateWarningSoft: 'rgba(243, 196, 112, 0.14)',
  stateErrorSoft: 'rgba(232, 120, 120, 0.12)',
  gradientSuccess: 'linear-gradient(135deg, #7AC686, #059669)',
  shadowSuccess: '0 4px 14px -4px rgba(16, 185, 129, 0.5)',
  danger: '#EF5B6B',
  dangerSoft: 'rgba(245, 72, 92, 0.1)',
  dangerText: '#FF8A97',
  warningText: '#F0A13A',
  successText: '#37C978',
  moduleBookmark: '#7C5CFF',
  moduleNote: '#2F9DF7',
  moduleFile: '#0FBF8C',
  moduleVault: '#F0A13A',
  fileTypeImage: '#7EB8E8',
  fileTypeDoc: '#F472B6',
  fileTypeArchive: '#F3C470',
  fileTypeAudio: '#7AC686',
  // 玻璃拟态（亮色：浅粉玻璃）
  glassBgSolid: '#FFFFFF',
  glassBgTrans: 'rgba(255, 255, 255, 0.72)',
  glassBorder: 'rgba(255, 255, 255, 0.6)',
  glassGlowTop: 'rgba(236, 91, 142, 0.16)',
  glassGlowBottom: 'rgba(255, 143, 171, 0.14)',
  glassShadow: '0 24px 64px -12px rgba(236, 91, 142, 0.18), inset 0 1px 0 rgba(255, 255, 255, 0.9)',
  glassBlur: '12px',
  glassInputBg: 'rgba(255, 255, 255, 0.6)',
  glassInputBorder: 'rgba(236, 91, 142, 0.22)',
  glassBtnPrimary: 'linear-gradient(135deg, #EC5B8E, #FF8FAB)',
  glassBtnGhost: 'rgba(255, 255, 255, 0.5)',
  glassBtnGhostHover: 'rgba(255, 255, 255, 0.72)',
  glassChipBg: 'rgba(236, 91, 142, 0.12)',
  glassChipBorder: 'rgba(236, 91, 142, 0.28)',
  glassChipText: '#B82A63',
  glassTextSecondary: 'rgba(29, 29, 31, 0.65)',
  glassTextPlaceholder: 'rgba(29, 29, 31, 0.62)',
  focusRing: '#C9256A',
  focusRingSoft: 'rgba(201, 37, 106, 0.22)',
  brandText: '#C9256A',
  textOnBrand: '#1D1D1F',
  scrollbarThumb: 'rgba(0, 0, 0, 0.14)',
  scrollbarThumbHover: 'rgba(0, 0, 0, 0.24)',
  selectionBg: 'rgba(236, 91, 142, 0.18)',
  selectionText: '#1D1D1F',
  overlayBg: 'rgba(245, 245, 247, 0.55)',
  overlayBlur: '6px',
  selectedBg: 'rgba(236, 91, 142, 0.10)',
  checkScrim: 'rgba(0, 0, 0, 0.35)',
  faviconScrim: '#FFFFFF',
  iconOnBrand: '#FFFFFF',
  cursorRainbow: 'linear-gradient(90deg, #ff6b6b, #ffd93d, #6bcb77, #4d96ff, #9b59b6)',
  cursorPastel: 'linear-gradient(90deg, #FFB7D5, #FFDAB9, #E8D5F5, #B5EAD7)',
  cursorStar1: '#FFD700',
  cursorStar2: '#FFA500',
  cursorStar3: '#FF6347',
  previewStage: '#111111',
  readerPaperText: '#3A3229',
  readerPaperBg: 'linear-gradient(180deg, #FBF5EA 0%, #F6EFDF 100%)',
  panelBg: 'rgba(255, 255, 255, 0.72)',
  panelBorder: 'rgba(255, 255, 255, 0.6)',
  panelShadow: '0 24px 64px -12px rgba(236, 91, 142, 0.18), inset 0 1px 0 rgba(255, 255, 255, 0.9)',
  particle1: '#7A1247',
  particle2: '#8C1750',
  particle3: '#9E1D5A',
  particle4: '#A41F5D',
  fontSans: '"DM Sans", "Noto Sans SC", "PingFang SC", system-ui, -apple-system, sans-serif',
  fontMono: '"JetBrains Mono", "SF Mono", monospace',
  fsXs: '11px',
  fsSm: '12px',
  fsMd: '13px',
  fsBase: '14px',
  radiusXs: '6px',
  radiusSm: '10px',
  radiusMd: '16px',
  radiusLg: '20px',
  radiusPill: '999px',
  layoutContentMax: '1560px',
  layoutPageGutter: '24px',
  layoutPagePadding: '12px 24px 0',
  layoutPanelPadding: '20px 24px',
  headerHeight: '60px',
  tabbarHeight: '56px',
  tapMin: '32px',
  emptyPad: '40px',
  emptyPadSm: '24px',
  skeletonBase: 'rgba(0, 0, 0, 0.06)',
  skeletonHighlight: 'rgba(255, 255, 255, 0.72)',
  durFast: '0.15s',
  dur: '0.2s',
  durSlow: '0.3s',
  durSlower: '0.5s',
  durLoop: '1.4s',
  durBreathe: '2.8s',
  durSpin: '0.9s',
  durStagger: '0.045s',
  durCountdown: '3s',
  durFloat: '6s',
  ease: 'cubic-bezier(0.32, 0.72, 0, 1)',
  easeBouncy: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
  easeLinear: 'linear',
  easeSmooth: 'ease-in-out',
}

export const darkTokens: DesignTokens = {
  brand: '#FF8FAB',
  brandSoft: 'rgba(255, 143, 171, 0.12)',
  brandHover: '#FF7A99',
  brandPress: '#E86A8A',
  brandSuppl: 'rgba(255, 143, 171, 0.18)',
  link: '#FF8FAB',
  text1: '#F5F5F7',
  text2: '#C7C7CC',
  text3: '#8E8E93',
  text4: '#636366',
  bgPage: '#000000',
  bgCard: '#26262A',
  border: '#45454A',
  split: '#56565C',
  hoverBg: '#323238',
  // 粉色阶（暗色）
  pinkCherry: '#3D2A30',
  pinkPeach: '#5A3A45',
  pinkRose: '#FF8FAB',
  pinkAdzuki: '#F472B6',
  pinkWhite: '#2D1F26',
  // 渐变（暗色）
  gradientTopBar: 'linear-gradient(90deg, #FF8FAB, #F472B6)',
  gradientBtn: 'linear-gradient(135deg, #FF8FAB, #F472B6)',
  gradientNavActive: 'linear-gradient(90deg, rgba(255,143,171,0.12) 0%, transparent 100%)',
  // 阴影（暗色）
  shadow1: '0 1px 2px 0 rgba(0, 0, 0, 0.30)',
  shadow2: '0 2px 4px -1px rgba(0, 0, 0, 0.40)',
  shadowPopover: '0 8px 24px -6px rgba(0, 0, 0, 0.55), 0 2px 6px -1px rgba(0, 0, 0, 0.40)',
  shadowCardHover: '0 4px 8px -2px rgba(0, 0, 0, 0.44)',
  shadowFab: '0 4px 12px -2px rgba(255, 143, 171, 0.20)',
  shadowFabHover: '0 8px 20px -4px rgba(255, 143, 171, 0.25)',
  glowIcon: '0 0 0 4px rgba(255,143,171,0.06)',
  // 状态色
  stateSuccess: '#7AC686',
  stateWarning: '#F3C470',
  stateError: '#E87878',
  stateWarningSoft: 'rgba(243, 196, 112, 0.16)',
  stateErrorSoft: 'rgba(232, 120, 120, 0.14)',
  gradientSuccess: 'linear-gradient(135deg, #7AC686, #059669)',
  shadowSuccess: '0 4px 14px -4px rgba(16, 185, 129, 0.45)',
  danger: '#EF5B6B',
  dangerSoft: 'rgba(245, 72, 92, 0.14)',
  dangerText: '#FF8A97',
  warningText: '#F0A13A',
  successText: '#37C978',
  moduleBookmark: '#8B7CFF',
  moduleNote: '#5AA9F7',
  moduleFile: '#2FCFA3',
  moduleVault: '#F0A13A',
  fileTypeImage: '#7EB8E8',
  fileTypeDoc: '#F472B6',
  fileTypeArchive: '#F3C470',
  fileTypeAudio: '#7AC686',
  // 玻璃拟态（暗色：深粉玻璃）
  glassBgSolid: '#26262A',
  glassBgTrans: 'rgba(38, 38, 42, 0.72)',
  glassBorder: 'rgba(255, 255, 255, 0.14)',
  glassGlowTop: 'rgba(255, 143, 171, 0.16)',
  glassGlowBottom: 'rgba(244, 114, 182, 0.12)',
  glassShadow: '0 24px 64px -12px rgba(0, 0, 0, 0.7), inset 0 1px 0 rgba(255, 255, 255, 0.08)',
  glassBlur: '12px',
  glassInputBg: 'rgba(255, 255, 255, 0.06)',
  glassInputBorder: 'rgba(255, 255, 255, 0.14)',
  glassBtnPrimary: 'linear-gradient(135deg, #FF8FAB, #F472B6)',
  glassBtnGhost: 'rgba(255, 255, 255, 0.08)',
  glassBtnGhostHover: 'rgba(255, 255, 255, 0.14)',
  glassChipBg: 'rgba(255, 143, 171, 0.16)',
  glassChipBorder: 'rgba(255, 143, 171, 0.32)',
  glassChipText: '#FFB3C6',
  glassTextSecondary: 'rgba(245, 245, 247, 0.6)',
  glassTextPlaceholder: 'rgba(245, 245, 247, 0.5)',
  focusRing: '#FF8FAB',
  focusRingSoft: 'rgba(255, 143, 171, 0.32)',
  brandText: '#FF8FAB',
  textOnBrand: '#1D1D1F',
  scrollbarThumb: 'rgba(255, 255, 255, 0.14)',
  scrollbarThumbHover: 'rgba(255, 255, 255, 0.24)',
  selectionBg: 'rgba(255, 143, 171, 0.26)',
  selectionText: '#F5F5F7',
  overlayBg: 'rgba(0, 0, 0, 0.55)',
  overlayBlur: '6px',
  selectedBg: 'rgba(255, 143, 171, 0.16)',
  checkScrim: 'rgba(0, 0, 0, 0.35)',
  faviconScrim: '#FFFFFF',
  iconOnBrand: '#FFFFFF',
  cursorRainbow: 'linear-gradient(90deg, #ff6b6b, #ffd93d, #6bcb77, #4d96ff, #9b59b6)',
  cursorPastel: 'linear-gradient(90deg, #FFB7D5, #FFDAB9, #E8D5F5, #B5EAD7)',
  cursorStar1: '#FFD700',
  cursorStar2: '#FFA500',
  cursorStar3: '#FF6347',
  previewStage: '#111111',
  readerPaperText: '#EDE4D4',
  readerPaperBg: 'linear-gradient(180deg, #2C271E 0%, #241F17 100%)',
  panelBg: 'rgba(38, 38, 42, 0.72)',
  panelBorder: 'rgba(255, 255, 255, 0.12)',
  panelShadow: '0 24px 64px -12px rgba(0, 0, 0, 0.7), inset 0 1px 0 rgba(255, 255, 255, 0.08)',
  particle1: '#FF8FAB',
  particle2: '#F472B6',
  particle3: '#5A3A45',
  particle4: '#FFB3C6',
  fontSans: '"DM Sans", "Noto Sans SC", "PingFang SC", system-ui, -apple-system, sans-serif',
  fontMono: '"JetBrains Mono", "SF Mono", monospace',
  fsXs: '11px',
  fsSm: '12px',
  fsMd: '13px',
  fsBase: '14px',
  radiusXs: '6px',
  radiusSm: '10px',
  radiusMd: '16px',
  radiusLg: '20px',
  radiusPill: '999px',
  layoutContentMax: '1560px',
  layoutPageGutter: '24px',
  layoutPagePadding: '12px 24px 0',
  layoutPanelPadding: '20px 24px',
  headerHeight: '60px',
  tabbarHeight: '56px',
  tapMin: '32px',
  emptyPad: '40px',
  emptyPadSm: '24px',
  skeletonBase: 'rgba(255, 255, 255, 0.08)',
  skeletonHighlight: 'rgba(255, 255, 255, 0.20)',
  durFast: '0.15s',
  dur: '0.2s',
  durSlow: '0.3s',
  durSlower: '0.5s',
  durLoop: '1.4s',
  durBreathe: '2.8s',
  durSpin: '0.9s',
  durStagger: '0.045s',
  durCountdown: '3s',
  durFloat: '6s',
  ease: 'cubic-bezier(0.32, 0.72, 0, 1)',
  easeBouncy: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
  easeLinear: 'linear',
  easeSmooth: 'ease-in-out',
}

export function tokensToCSSVars(tokens: DesignTokens): Record<string, string> {
  return {
    '--brand': tokens.brand,
    '--brand-soft': tokens.brandSoft,
    '--brand-hover': tokens.brandHover,
    '--brand-suppl': tokens.brandSuppl,
    '--link': tokens.link,
    '--text-1': tokens.text1,
    '--text-2': tokens.text2,
    '--text-3': tokens.text3,
    '--text-4': tokens.text4,
    '--bg-page': tokens.bgPage,
    '--bg-card': tokens.bgCard,
    '--border': tokens.border,
    '--split': tokens.split,
    '--hover-bg': tokens.hoverBg,
    '--pink-cherry': tokens.pinkCherry,
    '--pink-peach': tokens.pinkPeach,
    '--pink-rose': tokens.pinkRose,
    '--pink-adzuki': tokens.pinkAdzuki,
    '--pink-white': tokens.pinkWhite,
    '--gradient-top-bar': tokens.gradientTopBar,
    '--gradient-btn': tokens.gradientBtn,
    '--gradient-nav-active': tokens.gradientNavActive,
    '--shadow-1': tokens.shadow1,
    '--shadow-2': tokens.shadow2,
    '--shadow-popover': tokens.shadowPopover,
    '--shadow-card-hover': tokens.shadowCardHover,
    '--shadow-fab': tokens.shadowFab,
    '--shadow-fab-hover': tokens.shadowFabHover,
    '--glow-icon': tokens.glowIcon,
    '--state-success': tokens.stateSuccess,
    '--state-warning': tokens.stateWarning,
    '--state-error': tokens.stateError,
    '--state-warning-soft': tokens.stateWarningSoft,
    '--state-error-soft': tokens.stateErrorSoft,
    '--gradient-success': tokens.gradientSuccess,
    '--shadow-success': tokens.shadowSuccess,
    '--danger': tokens.danger,
    '--danger-soft': tokens.dangerSoft,
    '--danger-text': tokens.dangerText,
    '--warning-text': tokens.warningText,
    '--success-text': tokens.successText,
    '--module-bookmark': tokens.moduleBookmark,
    '--module-note': tokens.moduleNote,
    '--module-file': tokens.moduleFile,
    '--module-vault': tokens.moduleVault,
    '--file-type-image': tokens.fileTypeImage,
    '--file-type-doc': tokens.fileTypeDoc,
    '--file-type-archive': tokens.fileTypeArchive,
    '--file-type-audio': tokens.fileTypeAudio,
    '--glass-bg-solid': tokens.glassBgSolid,
    '--glass-bg-trans': tokens.glassBgTrans,
    '--glass-border': tokens.glassBorder,
    '--glass-glow-top': tokens.glassGlowTop,
    '--glass-glow-bottom': tokens.glassGlowBottom,
    '--glass-shadow': tokens.glassShadow,
    '--glass-blur': tokens.glassBlur,
    '--glass-input-bg': tokens.glassInputBg,
    '--glass-input-border': tokens.glassInputBorder,
    '--glass-btn-primary': tokens.glassBtnPrimary,
    '--glass-btn-ghost': tokens.glassBtnGhost,
    '--glass-btn-ghost-hover': tokens.glassBtnGhostHover,
    '--glass-chip-bg': tokens.glassChipBg,
    '--glass-chip-border': tokens.glassChipBorder,
    '--glass-chip-text': tokens.glassChipText,
    '--glass-text-secondary': tokens.glassTextSecondary,
    '--glass-text-placeholder': tokens.glassTextPlaceholder,
    '--focus-ring': tokens.focusRing,
    '--focus-ring-soft': tokens.focusRingSoft,
    '--brand-text': tokens.brandText,
    '--text-on-brand': tokens.textOnBrand,
    '--scrollbar-thumb': tokens.scrollbarThumb,
    '--scrollbar-thumb-hover': tokens.scrollbarThumbHover,
    '--selection-bg': tokens.selectionBg,
    '--selection-text': tokens.selectionText,
    '--overlay-bg': tokens.overlayBg,
    '--overlay-blur': tokens.overlayBlur,
    '--selected-bg': tokens.selectedBg,
    '--check-scrim': tokens.checkScrim,
    '--favicon-scrim': tokens.faviconScrim,
    '--icon-on-brand': tokens.iconOnBrand,
    '--cursor-rainbow': tokens.cursorRainbow,
    '--cursor-pastel': tokens.cursorPastel,
    '--cursor-star-1': tokens.cursorStar1,
    '--cursor-star-2': tokens.cursorStar2,
    '--cursor-star-3': tokens.cursorStar3,
    '--preview-stage': tokens.previewStage,
    '--reader-paper-text': tokens.readerPaperText,
    '--reader-paper-bg': tokens.readerPaperBg,
    '--panel-bg': tokens.panelBg,
    '--panel-border': tokens.panelBorder,
    '--panel-shadow': tokens.panelShadow,
    '--particle-1': tokens.particle1,
    '--particle-2': tokens.particle2,
    '--particle-3': tokens.particle3,
    '--particle-4': tokens.particle4,
    '--font-sans': tokens.fontSans,
    '--font-mono': tokens.fontMono,
    '--fs-xs': tokens.fsXs,
    '--fs-sm': tokens.fsSm,
    '--fs-md': tokens.fsMd,
    '--fs-base': tokens.fsBase,
    '--radius-xs': tokens.radiusXs,
    '--radius-sm': tokens.radiusSm,
    '--radius-md': tokens.radiusMd,
    '--radius-lg': tokens.radiusLg,
    '--radius-pill': tokens.radiusPill,
    '--layout-content-max': tokens.layoutContentMax,
    '--layout-page-gutter': tokens.layoutPageGutter,
    '--layout-page-padding': tokens.layoutPagePadding,
    '--layout-panel-padding': tokens.layoutPanelPadding,
    '--header-height': tokens.headerHeight,
    '--tabbar-height': tokens.tabbarHeight,
    '--tap-min': tokens.tapMin,
    '--skeleton-base': tokens.skeletonBase,
    '--skeleton-highlight': tokens.skeletonHighlight,
    '--empty-pad': tokens.emptyPad,
    '--empty-pad-sm': tokens.emptyPadSm,
    '--dur-fast': tokens.durFast,
    '--dur': tokens.dur,
    '--dur-slow': tokens.durSlow,
    '--dur-slower': tokens.durSlower,
    '--dur-loop': tokens.durLoop,
    '--dur-breathe': tokens.durBreathe,
    '--dur-spin': tokens.durSpin,
    '--dur-stagger': tokens.durStagger,
    '--dur-countdown': tokens.durCountdown,
    '--dur-float': tokens.durFloat,
    '--ease': tokens.ease,
    '--ease-bouncy': tokens.easeBouncy,
    '--ease-linear': tokens.easeLinear,
    '--ease-smooth': tokens.easeSmooth,
  }
}

export function naiveBrandOverrides(tokens: DesignTokens) {
  return {
    primaryColor: tokens.brand,
    primaryColorHover: tokens.brandHover,
    primaryColorPressed: tokens.brandPress,
    primaryColorSuppl: tokens.brandSuppl,
  }
}
