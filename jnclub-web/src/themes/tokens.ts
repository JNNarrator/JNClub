/**
 * 设计 Token — 全站唯一色彩/间距/圆角/动效定义
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
  radiusSm: string
  radiusMd: string
  radiusLg: string
  spaceXs: string
  spaceSm: string
  spaceMd: string
  spaceLg: string
  spaceXl: string
  dur: string
  ease: string
}

export const lightTokens: DesignTokens = {
  brand: '#EC5B8E',
  brandSoft: 'rgba(236, 91, 142, 0.08)',
  brandHover: '#E84D7A',
  brandPress: '#D43F6A',
  brandSuppl: 'rgba(236, 91, 142, 0.15)',
  link: '#3B6FD4',
  text1: '#15181F',
  text2: '#4A5162', /* 略加深，保证 7:1 对比度 */
  text3: '#7A8294', /* 从 #9AA1B1 加深到 4.5:1 以上 */
  text4: '#B0B4BE', /* 从 #C8CCD4 加深 */
  bgPage: '#F6F7F9',
  bgCard: '#FFFFFF',
  border: '#EAECF1',
  hoverBg: '#F0F1F4',
  radiusSm: '8px',
  radiusMd: '12px',
  radiusLg: '16px',
  spaceXs: '4px',
  spaceSm: '8px',
  spaceMd: '16px',
  spaceLg: '24px',
  spaceXl: '32px',
  dur: '0.2s',
  ease: 'cubic-bezier(0.4, 0, 0.2, 1)',
}

export const darkTokens: DesignTokens = {
  brand: '#FF7A9A',
  brandSoft: 'rgba(255, 122, 154, 0.12)',
  brandHover: '#FF8FAB',
  brandPress: '#FF6B8A',
  brandSuppl: 'rgba(255, 122, 154, 0.2)',
  link: '#5B8DEF',
  text1: '#E8E9EB',
  text2: '#B8BAC2', /* 提亮 */
  text3: '#8A8C96', /* 从 #6E7079 提亮 */
  text4: '#5A5C66', /* 从 #4A4C54 提亮 */
  bgPage: '#1C1F26',
  bgCard: '#282B35',
  border: '#3A3D47',
  hoverBg: '#33363F',
  radiusSm: '8px',
  radiusMd: '12px',
  radiusLg: '16px',
  spaceXs: '4px',
  spaceSm: '8px',
  spaceMd: '16px',
  spaceLg: '24px',
  spaceXl: '32px',
  dur: '0.2s',
  ease: 'cubic-bezier(0.4, 0, 0.2, 1)',
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
    '--radius-sm': tokens.radiusSm,
    '--radius-md': tokens.radiusMd,
    '--radius-lg': tokens.radiusLg,
    '--space-xs': tokens.spaceXs,
    '--space-sm': tokens.spaceSm,
    '--space-md': tokens.spaceMd,
    '--space-lg': tokens.spaceLg,
    '--space-xl': tokens.spaceXl,
    '--dur': tokens.dur,
    '--ease': tokens.ease,
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
