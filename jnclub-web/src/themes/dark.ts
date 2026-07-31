import type { GlobalThemeOverrides } from 'naive-ui'
import { darkTokens } from './tokens'

const darkThemeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: darkTokens.brand,
    primaryColorHover: darkTokens.brandHover,
    primaryColorPressed: darkTokens.brandPress,
    primaryColorSuppl: darkTokens.brandSuppl,
    bodyColor: darkTokens.bgPage,
    cardColor: darkTokens.bgCard,
    modalColor: darkTokens.bgCard,
    popoverColor: darkTokens.bgCard,
    borderColor: darkTokens.border,
    dividerColor: darkTokens.border,
    borderRadius: darkTokens.radiusSm,
  },
  Layout: {
    siderColor: darkTokens.bgCard,
    headerColor: darkTokens.bgCard,
  },
  Breadcrumb: {
    itemTextColor: darkTokens.text3,
    itemTextColorHover: darkTokens.text1,
    itemTextColorPressed: darkTokens.text1,
    fontWeightActive: '600',
  },
}

export default darkThemeOverrides
