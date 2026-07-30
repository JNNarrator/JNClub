import type { GlobalThemeOverrides } from 'naive-ui'
import { lightTokens, naiveBrandOverrides } from './tokens'

const t = lightTokens
const brand = naiveBrandOverrides(t)

/**
 * 亮色主题 — 极浅暖灰底 + 白卡 + 品牌粉点缀
 * 所有颜色值引用 tokens，不写魔法数
 */
const lightThemeOverrides: GlobalThemeOverrides = {
  common: {
    ...brand,
    bodyColor: t.bgPage,
    cardColor: t.bgCard,
    modalColor: t.bgCard,
    popoverColor: t.bgCard,
    tableColor: t.bgCard,
    actionColor: t.bgPage,
    borderColor: t.border,
    dividerColor: t.border,
    textColor1: t.text1,
    textColor2: t.text2,
    textColor3: t.text3,
    textColorDisabled: t.text4,
    borderRadius: t.radiusLg,
    borderRadiusSmall: t.radiusSm,
    fontWeightStrong: '700',
    hoverColor: t.hoverBg,
  },
  Card: {
    borderRadius: t.radiusMd,
    borderColor: t.border,
    boxShadow: '0 1px 2px rgba(0,0,0,0.06), 0 4px 20px rgba(0,0,0,0.05)',
    paddingMedium: '20px',
  },
  Menu: {
    borderRadius: t.radiusSm,
    itemColorActive: t.brandSoft,
    itemColorActiveHover: t.brandSuppl,
    itemTextColorActive: t.brand,
    itemIconColorActive: t.brand,
    itemColorHover: t.hoverBg,
    itemHeightMedium: '44px',
    itemBorderRadius: t.radiusSm,
    fontSize: '14px',
  },
  Button: {
    borderRadiusMedium: t.radiusSm,
    borderRadiusSmall: '8px',
    fontWeightMedium: '600',
  },
  Input: {
    borderRadius: t.radiusSm,
  },
  Tree: {
    borderRadius: t.radiusSm,
    nodeBorderRadius: t.radiusSm,
  },
  Tag: {
    borderRadius: '999px',
  },
  Switch: {
    railColor: t.border,
  },
}

export default lightThemeOverrides
