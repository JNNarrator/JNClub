import type { GlobalThemeOverrides } from 'naive-ui'
import { lightTokens } from './tokens'

const lightThemeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: lightTokens.brand,
    primaryColorHover: lightTokens.brandHover,
    primaryColorPressed: lightTokens.brandPress,
    primaryColorSuppl: lightTokens.brandSuppl,
    bodyColor: lightTokens.bgPage,
    cardColor: lightTokens.bgCard,
    modalColor: lightTokens.bgCard,
    popoverColor: lightTokens.bgCard,
    borderColor: lightTokens.border,
    dividerColor: lightTokens.border,
    borderRadius: lightTokens.radiusSm,
  },
  Layout: {
    siderColor: lightTokens.bgCard,
    headerColor: 'rgba(255,255,255,0.8)',
  },
  Breadcrumb: {
    itemTextColor: lightTokens.text3,
    itemTextColorHover: lightTokens.text1,
    itemTextColorPressed: lightTokens.text1,
    fontWeightActive: '600',
  },
}

export default lightThemeOverrides
