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
  gradientCherry: string
  gradientCard: string
  gradientTopBar: string
  gradientBtn: string
  gradientNavActive: string
  gradientDivider: string
  gradientFabGlow: string
  // 阴影
  shadow1: string
  shadow2: string
  shadow3: string
  shadowCardHover: string
  shadowFab: string
  shadowFabHover: string
  glowIcon: string
  // 状态色
  stateSuccess: string
  stateWarning: string
  stateError: string
  stateInfo: string
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
  glassHighlight: string
  glassGlowTop: string
  glassGlowBottom: string
  glassShadow: string
  glassBlur: string
  glassInputBg: string
  glassInputBorder: string
  glassInputFocus: string
  glassBtnPrimary: string
  glassBtnGhost: string
  glassBtnGhostHover: string
  glassChipBg: string
  glassChipBorder: string
  glassChipText: string
  glassTextSecondary: string
  glassTextPlaceholder: string
  focusRing: string
  // 主题级交互/反馈色（M1：滚动条、选区、遮罩、骨架、卡片态、面板）
  scrollbarThumb: string
  scrollbarThumbHover: string
  selectionBg: string
  selectionText: string
  overlayBg: string
  overlayBlur: string
  skeletonBase: string
  skeletonHighlight: string
  cardHoverBg: string
  selectedBg: string
  panelBg: string
  panelBorder: string
  panelShadow: string
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
  spaceXs: string
  spaceSm: string
  spaceMd: string
  spaceLg: string
  spaceXl: string
  // 布局体系
  layoutContentMax: string
  layoutPageGutter: string
  layoutPagePadding: string
  layoutPanelPadding: string
  headerHeight: string
  tabbarHeight: string
  dur: string
  ease: string
  easeBouncy: string
}

export const lightTokens: DesignTokens = {
  brand: '#EC5B8E',
  brandSoft: 'rgba(236, 91, 142, 0.08)',
  brandHover: '#E84D7A',
  brandPress: '#D43F6A',
  brandSuppl: 'rgba(236, 91, 142, 0.12)',
  link: '#EC5B8E',
  text1: '#1D1D1F',
  text2: '#6B6B6F',
  text3: '#8E8E93',
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
  gradientCherry: 'linear-gradient(135deg, #FFFFFF 0%, #FFF5F8 100%)',
  gradientCard: 'linear-gradient(165deg, #FFFFFF 0%, #F9F9FB 100%)',
  gradientTopBar: 'linear-gradient(90deg, #EC5B8E, #FFB3C6)',
  gradientBtn: 'linear-gradient(135deg, #EC5B8E, #FF8FAB)',
  gradientNavActive: 'linear-gradient(90deg, #FFE4EF 0%, transparent 100%)',
  gradientDivider: 'linear-gradient(90deg, transparent, rgba(236,91,142,0.25), transparent)',
  gradientFabGlow: 'linear-gradient(to top, rgba(236,91,142,0.12), transparent)',
  // 阴影 — Apple 极轻
  shadow1: '0 1px 2px 0 rgba(0, 0, 0, 0.04)',
  shadow2: '0 2px 4px -1px rgba(0, 0, 0, 0.06), 0 1px 2px -1px rgba(0, 0, 0, 0.05)',
  shadow3: '0 8px 24px -8px rgba(0, 0, 0, 0.08), 0 4px 8px -4px rgba(0, 0, 0, 0.05)',
  shadowCardHover: '0 4px 8px -2px rgba(0, 0, 0, 0.06), 0 2px 4px -2px rgba(0, 0, 0, 0.05)',
  shadowFab: '0 4px 12px -2px rgba(236, 91, 142, 0.15)',
  shadowFabHover: '0 8px 20px -4px rgba(236, 91, 142, 0.2)',
  glowIcon: '0 0 0 4px rgba(236,91,142,0.06)',
  // 状态色
  stateSuccess: '#7AC686',
  stateWarning: '#F3C470',
  stateError: '#E87878',
  stateInfo: '#7EB8E8',
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
  glassHighlight: 'rgba(255, 255, 255, 0.9)',
  glassGlowTop: 'rgba(236, 91, 142, 0.16)',
  glassGlowBottom: 'rgba(255, 143, 171, 0.14)',
  glassShadow: '0 24px 64px -12px rgba(236, 91, 142, 0.18), inset 0 1px 0 rgba(255, 255, 255, 0.9)',
  glassBlur: '12px',
  glassInputBg: 'rgba(255, 255, 255, 0.6)',
  glassInputBorder: 'rgba(236, 91, 142, 0.22)',
  glassInputFocus: 'rgba(236, 91, 142, 0.28)',
  glassBtnPrimary: 'linear-gradient(135deg, #EC5B8E, #FF8FAB)',
  glassBtnGhost: 'rgba(255, 255, 255, 0.5)',
  glassBtnGhostHover: 'rgba(255, 255, 255, 0.72)',
  glassChipBg: 'rgba(236, 91, 142, 0.12)',
  glassChipBorder: 'rgba(236, 91, 142, 0.28)',
  glassChipText: '#E84D7A',
  glassTextSecondary: 'rgba(29, 29, 31, 0.6)',
  glassTextPlaceholder: 'rgba(29, 29, 31, 0.35)',
  focusRing: 'rgba(236, 91, 142, 0.2)',
  scrollbarThumb: 'rgba(0, 0, 0, 0.14)',
  scrollbarThumbHover: 'rgba(0, 0, 0, 0.24)',
  selectionBg: 'rgba(236, 91, 142, 0.18)',
  selectionText: '#1D1D1F',
  overlayBg: 'rgba(245, 245, 247, 0.55)',
  overlayBlur: '6px',
  skeletonBase: 'rgba(0, 0, 0, 0.06)',
  skeletonHighlight: 'rgba(0, 0, 0, 0.03)',
  cardHoverBg: '#F2F2F7',
  selectedBg: 'rgba(236, 91, 142, 0.10)',
  panelBg: 'rgba(255, 255, 255, 0.72)',
  panelBorder: 'rgba(255, 255, 255, 0.6)',
  panelShadow: '0 24px 64px -12px rgba(236, 91, 142, 0.18), inset 0 1px 0 rgba(255, 255, 255, 0.9)',
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
  spaceXs: '4px',
  spaceSm: '8px',
  spaceMd: '16px',
  spaceLg: '24px',
  spaceXl: '32px',
  layoutContentMax: '1560px',
  layoutPageGutter: '24px',
  layoutPagePadding: '12px 24px 0',
  layoutPanelPadding: '20px 24px',
  headerHeight: '60px',
  tabbarHeight: '56px',
  dur: '0.2s',
  ease: 'cubic-bezier(0.32, 0.72, 0, 1)',
  easeBouncy: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
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
  bgCard: '#1C1C1E',
  border: '#38383A',
  split: '#4A4A4D',
  hoverBg: '#2C2C2E',
  // 粉色阶（暗色）
  pinkCherry: '#3D2A30',
  pinkPeach: '#5A3A45',
  pinkRose: '#FF8FAB',
  pinkAdzuki: '#F472B6',
  pinkWhite: '#2D1F26',
  // 渐变（暗色）
  gradientCherry: 'linear-gradient(135deg, #1C1C1E 0%, #000000 100%)',
  gradientCard: 'linear-gradient(165deg, #1C1C1E 0%, #2C2C2E 100%)',
  gradientTopBar: 'linear-gradient(90deg, #FF8FAB, #F472B6)',
  gradientBtn: 'linear-gradient(135deg, #FF8FAB, #F472B6)',
  gradientNavActive: 'linear-gradient(90deg, rgba(255,143,171,0.12) 0%, transparent 100%)',
  gradientDivider: 'linear-gradient(90deg, transparent, rgba(255,143,171,0.25), transparent)',
  gradientFabGlow: 'linear-gradient(to top, rgba(255,143,171,0.12), transparent)',
  // 阴影（暗色）
  shadow1: '0 1px 2px 0 rgba(0, 0, 0, 0.30)',
  shadow2: '0 2px 4px -1px rgba(0, 0, 0, 0.40)',
  shadow3: '0 8px 24px -8px rgba(0, 0, 0, 0.50)',
  shadowCardHover: '0 4px 8px -2px rgba(0, 0, 0, 0.44)',
  shadowFab: '0 4px 12px -2px rgba(255, 143, 171, 0.20)',
  shadowFabHover: '0 8px 20px -4px rgba(255, 143, 171, 0.25)',
  glowIcon: '0 0 0 4px rgba(255,143,171,0.06)',
  // 状态色
  stateSuccess: '#7AC686',
  stateWarning: '#F3C470',
  stateError: '#E87878',
  stateInfo: '#7EB8E8',
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
  glassBgSolid: '#1C1C1E',
  glassBgTrans: 'rgba(28, 28, 30, 0.72)',
  glassBorder: 'rgba(255, 255, 255, 0.12)',
  glassHighlight: 'rgba(255, 255, 255, 0.08)',
  glassGlowTop: 'rgba(255, 143, 171, 0.16)',
  glassGlowBottom: 'rgba(244, 114, 182, 0.12)',
  glassShadow: '0 24px 64px -12px rgba(0, 0, 0, 0.7), inset 0 1px 0 rgba(255, 255, 255, 0.08)',
  glassBlur: '12px',
  glassInputBg: 'rgba(255, 255, 255, 0.06)',
  glassInputBorder: 'rgba(255, 255, 255, 0.14)',
  glassInputFocus: 'rgba(255, 143, 171, 0.28)',
  glassBtnPrimary: 'linear-gradient(135deg, #FF8FAB, #F472B6)',
  glassBtnGhost: 'rgba(255, 255, 255, 0.08)',
  glassBtnGhostHover: 'rgba(255, 255, 255, 0.14)',
  glassChipBg: 'rgba(255, 143, 171, 0.16)',
  glassChipBorder: 'rgba(255, 143, 171, 0.32)',
  glassChipText: '#FFB3C6',
  glassTextSecondary: 'rgba(245, 245, 247, 0.6)',
  glassTextPlaceholder: 'rgba(245, 245, 247, 0.35)',
  focusRing: 'rgba(255, 143, 171, 0.28)',
  scrollbarThumb: 'rgba(255, 255, 255, 0.14)',
  scrollbarThumbHover: 'rgba(255, 255, 255, 0.24)',
  selectionBg: 'rgba(255, 143, 171, 0.26)',
  selectionText: '#F5F5F7',
  overlayBg: 'rgba(0, 0, 0, 0.55)',
  overlayBlur: '6px',
  skeletonBase: 'rgba(255, 255, 255, 0.08)',
  skeletonHighlight: 'rgba(255, 255, 255, 0.04)',
  cardHoverBg: '#2C2C2E',
  selectedBg: 'rgba(255, 143, 171, 0.16)',
  panelBg: 'rgba(28, 28, 30, 0.72)',
  panelBorder: 'rgba(255, 255, 255, 0.12)',
  panelShadow: '0 24px 64px -12px rgba(0, 0, 0, 0.7), inset 0 1px 0 rgba(255, 255, 255, 0.08)',
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
  spaceXs: '4px',
  spaceSm: '8px',
  spaceMd: '16px',
  spaceLg: '24px',
  spaceXl: '32px',
  layoutContentMax: '1560px',
  layoutPageGutter: '24px',
  layoutPagePadding: '12px 24px 0',
  layoutPanelPadding: '20px 24px',
  headerHeight: '60px',
  tabbarHeight: '56px',
  dur: '0.2s',
  ease: 'cubic-bezier(0.32, 0.72, 0, 1)',
  easeBouncy: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
}

export function tokensToCSSVars(tokens: DesignTokens): Record<string, string> {
  return {
    '--brand': tokens.brand,
    '--brand-soft': tokens.brandSoft,
    '--brand-hover': tokens.brandHover,
    '--brand-press': tokens.brandPress,
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
    '--gradient-cherry': tokens.gradientCherry,
    '--gradient-card': tokens.gradientCard,
    '--gradient-top-bar': tokens.gradientTopBar,
    '--gradient-btn': tokens.gradientBtn,
    '--gradient-nav-active': tokens.gradientNavActive,
    '--gradient-divider': tokens.gradientDivider,
    '--gradient-fab-glow': tokens.gradientFabGlow,
    '--shadow-1': tokens.shadow1,
    '--shadow-2': tokens.shadow2,
    '--shadow-3': tokens.shadow3,
    '--shadow-card-hover': tokens.shadowCardHover,
    '--shadow-fab': tokens.shadowFab,
    '--shadow-fab-hover': tokens.shadowFabHover,
    '--glow-icon': tokens.glowIcon,
    '--state-success': tokens.stateSuccess,
    '--state-warning': tokens.stateWarning,
    '--state-error': tokens.stateError,
    '--state-info': tokens.stateInfo,
    '--success': tokens.stateSuccess,
    '--warning': tokens.stateWarning,
    '--info': tokens.stateInfo,
    '--error': tokens.stateError,
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
    '--glass-highlight': tokens.glassHighlight,
    '--glass-glow-top': tokens.glassGlowTop,
    '--glass-glow-bottom': tokens.glassGlowBottom,
    '--glass-shadow': tokens.glassShadow,
    '--glass-blur': tokens.glassBlur,
    '--glass-input-bg': tokens.glassInputBg,
    '--glass-input-border': tokens.glassInputBorder,
    '--glass-input-focus': tokens.glassInputFocus,
    '--glass-btn-primary': tokens.glassBtnPrimary,
    '--glass-btn-ghost': tokens.glassBtnGhost,
    '--glass-btn-ghost-hover': tokens.glassBtnGhostHover,
    '--glass-chip-bg': tokens.glassChipBg,
    '--glass-chip-border': tokens.glassChipBorder,
    '--glass-chip-text': tokens.glassChipText,
    '--glass-text-secondary': tokens.glassTextSecondary,
    '--glass-text-placeholder': tokens.glassTextPlaceholder,
    '--focus-ring': tokens.focusRing,
    '--scrollbar-thumb': tokens.scrollbarThumb,
    '--scrollbar-thumb-hover': tokens.scrollbarThumbHover,
    '--selection-bg': tokens.selectionBg,
    '--selection-text': tokens.selectionText,
    '--overlay-bg': tokens.overlayBg,
    '--overlay-blur': tokens.overlayBlur,
    '--skeleton-base': tokens.skeletonBase,
    '--skeleton-highlight': tokens.skeletonHighlight,
    '--card-hover-bg': tokens.cardHoverBg,
    '--selected-bg': tokens.selectedBg,
    '--panel-bg': tokens.panelBg,
    '--panel-border': tokens.panelBorder,
    '--panel-shadow': tokens.panelShadow,
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
    '--space-xs': tokens.spaceXs,
    '--space-sm': tokens.spaceSm,
    '--space-md': tokens.spaceMd,
    '--space-lg': tokens.spaceLg,
    '--space-xl': tokens.spaceXl,
    '--layout-content-max': tokens.layoutContentMax,
    '--layout-page-gutter': tokens.layoutPageGutter,
    '--layout-page-padding': tokens.layoutPagePadding,
    '--layout-panel-padding': tokens.layoutPanelPadding,
    '--header-height': tokens.headerHeight,
    '--tabbar-height': tokens.tabbarHeight,
    '--dur': tokens.dur,
    '--ease': tokens.ease,
    '--ease-bouncy': tokens.easeBouncy,
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
