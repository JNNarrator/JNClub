/**
 * 设计 Token — JNClub 氛围升级版
 * 四阶粉色系 + 渐变 + 阴影 + 暖底
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
  fontSans: string
  fontMono: string
  radiusSm: string
  radiusMd: string
  radiusLg: string
  radiusPill: string
  spaceXs: string
  spaceSm: string
  spaceMd: string
  spaceLg: string
  spaceXl: string
  dur: string
  ease: string
  easeBouncy: string
}

export const lightTokens: DesignTokens = {
  brand: '#EC5B8E',
  brandSoft: 'rgba(236, 91, 142, 0.08)',
  brandHover: '#E84D7A',
  brandPress: '#D43F6A',
  brandSuppl: 'rgba(236, 91, 142, 0.15)',
  link: '#C9396A',
  text1: '#4A3A42',
  text2: '#6B5B64',
  text3: '#97898F',
  text4: '#BFB5B9',
  bgPage: '#FFF7F9',
  bgCard: '#FFFFFF',
  border: '#F2E4E9',
  hoverBg: '#FFF0F4',
  // 粉色阶
  pinkCherry: '#FFE4EF',
  pinkPeach: '#FFB3C6',
  pinkRose: '#FF8FAB',
  pinkAdzuki: '#F472B6',
  pinkWhite: '#FFF5F8',
  // 渐变
  gradientCherry: 'linear-gradient(135deg, #FFE4EF 0%, #FFF5F8 100%)',
  gradientCard: 'linear-gradient(165deg, #FFFFFF 0%, #FFF5F8 100%)',
  gradientTopBar: 'linear-gradient(90deg, #EC5B8E, #FFB3C6)',
  gradientBtn: 'linear-gradient(135deg, #EC5B8E, #FF8FAB)',
  gradientNavActive: 'linear-gradient(90deg, #FFE4EF 0%, transparent 100%)',
  gradientDivider: 'linear-gradient(90deg, transparent, rgba(236,91,142,0.3), transparent)',
  gradientFabGlow: 'linear-gradient(to top, rgba(236,91,142,0.2), transparent)',
  // 阴影
  shadow1: '0 1px 2px rgba(74,58,66,0.04), 0 1px 1px rgba(74,58,66,0.02)',
  shadow2: '0 8px 24px -8px rgba(74,58,66,0.14)',
  shadow3: '0 24px 60px -20px rgba(74,58,66,0.24)',
  shadowCardHover: '0 12px 40px rgba(236,91,142,0.18)',
  shadowFab: '0 4px 20px rgba(236,91,142,0.4), 0 0 0 6px rgba(236,91,142,0.1)',
  shadowFabHover: '0 6px 30px rgba(236,91,142,0.5), 0 0 0 10px rgba(236,91,142,0.12)',
  glowIcon: '0 0 0 4px rgba(236,91,142,0.1)',
  // 状态色
  stateSuccess: '#7AC686',
  stateWarning: '#F3C470',
  stateError: '#E87878',
  stateInfo: '#7EB8E8',
  fontSans: '"Inter", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, -apple-system, sans-serif',
  fontMono: '"SF Mono", "Fira Code", "Noto Sans SC", monospace',
  radiusSm: '8px',
  radiusMd: '12px',
  radiusLg: '16px',
  radiusPill: '999px',
  spaceXs: '4px',
  spaceSm: '8px',
  spaceMd: '16px',
  spaceLg: '24px',
  spaceXl: '32px',
  dur: '0.2s',
  ease: 'cubic-bezier(0.4, 0, 0.2, 1)',
  easeBouncy: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
}

export const darkTokens: DesignTokens = {
  brand: '#FF7A9A',
  brandSoft: 'rgba(255, 143, 171, 0.12)',
  brandHover: '#FF8FAB',
  brandPress: '#FF6B8A',
  brandSuppl: 'rgba(255, 143, 171, 0.2)',
  link: '#FF8FAB',
  text1: '#D1D5DB',
  text2: '#B0AAB0',
  text3: '#8A8088',
  text4: '#5A5260',
  bgPage: '#1A1417',
  bgCard: '#2D1F26',
  border: 'rgba(255, 143, 171, 0.15)',
  hoverBg: '#241C20',
  // 粉色阶（暗色）
  pinkCherry: '#3D2A30',
  pinkPeach: '#5A3A45',
  pinkRose: '#FF8FAB',
  pinkAdzuki: '#F472B6',
  pinkWhite: '#2D1F26',
  // 渐变（暗色）
  gradientCherry: 'linear-gradient(135deg, #3D2A30 0%, #2D1F26 100%)',
  gradientCard: 'linear-gradient(165deg, #2D1F26 0%, #241C20 100%)',
  gradientTopBar: 'linear-gradient(90deg, #FF8FAB, #F472B6)',
  gradientBtn: 'linear-gradient(135deg, #FF8FAB, #F472B6)',
  gradientNavActive: 'linear-gradient(90deg, rgba(255,143,171,0.15) 0%, transparent 100%)',
  gradientDivider: 'linear-gradient(90deg, transparent, rgba(255,143,171,0.3), transparent)',
  gradientFabGlow: 'linear-gradient(to top, rgba(255,143,171,0.2), transparent)',
  // 阴影（暗色）
  shadow1: '0 1px 2px rgba(0,0,0,0.2), 0 1px 1px rgba(0,0,0,0.14)',
  shadow2: '0 8px 24px -8px rgba(0,0,0,0.4)',
  shadow3: '0 24px 60px -20px rgba(0,0,0,0.55)',
  shadowCardHover: '0 12px 40px rgba(255,143,171,0.15)',
  shadowFab: '0 4px 20px rgba(255,143,171,0.3), 0 0 0 6px rgba(255,143,171,0.08)',
  shadowFabHover: '0 6px 30px rgba(255,143,171,0.4), 0 0 0 10px rgba(255,143,171,0.1)',
  glowIcon: '0 0 0 4px rgba(255,143,171,0.08)',
  // 状态色
  stateSuccess: '#7AC686',
  stateWarning: '#F3C470',
  stateError: '#E87878',
  stateInfo: '#7EB8E8',
  fontSans: '"Inter", "Noto Sans SC", "PingFang SC", "Microsoft YaHei", system-ui, -apple-system, sans-serif',
  fontMono: '"SF Mono", "Fira Code", "Noto Sans SC", monospace',
  radiusSm: '8px',
  radiusMd: '12px',
  radiusLg: '16px',
  radiusPill: '999px',
  spaceXs: '4px',
  spaceSm: '8px',
  spaceMd: '16px',
  spaceLg: '24px',
  spaceXl: '32px',
  dur: '0.2s',
  ease: 'cubic-bezier(0.4, 0, 0.2, 1)',
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
    '--font-sans': tokens.fontSans,
    '--font-mono': tokens.fontMono,
    '--radius-sm': tokens.radiusSm,
    '--radius-md': tokens.radiusMd,
    '--radius-lg': tokens.radiusLg,
    '--radius-pill': tokens.radiusPill,
    '--space-xs': tokens.spaceXs,
    '--space-sm': tokens.spaceSm,
    '--space-md': tokens.spaceMd,
    '--space-lg': tokens.spaceLg,
    '--space-xl': tokens.spaceXl,
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
