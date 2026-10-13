import type { GlobalThemeOverrides } from 'naive-ui'
import { darkTokens as t } from './tokens'

/**
 * naive-ui 主题覆盖（暗色）—— M2-1
 *
 * 结构与 light.ts 对称，逐字段注释见该文件。
 * 暗色下 naive 内置的 darkTheme 会把 common 派生值算成半透明白，
 * 这里用不透明 token 覆盖，避免弹层在纯黑页面上「灰得发脏」。
 */
const darkThemeOverrides: GlobalThemeOverrides = {
  common: {
    // —— 品牌与状态 ——
    primaryColor: t.brand,
    primaryColorHover: t.brandHover,
    primaryColorPressed: t.brandPress,
    primaryColorSuppl: t.brandSuppl,
    infoColor: t.brand,
    successColor: t.stateSuccess,
    warningColor: t.stateWarning,
    errorColor: t.stateError,
    // —— 底色 ——
    bodyColor: t.bgPage,
    cardColor: t.bgCard,
    modalColor: t.bgCard,
    popoverColor: t.bgCard,
    // —— 文字 ——
    textColorBase: t.text1,
    textColor1: t.text1,
    textColor2: t.text2,
    textColor3: t.text3,
    textColorDisabled: t.text4,
    placeholderColor: t.glassTextPlaceholder,
    placeholderColorDisabled: t.text4,
    hoverColor: t.hoverBg,
    // —— 边框与圆角 ——
    borderColor: t.border,
    dividerColor: t.border,
    borderRadius: t.radiusSm,
    borderRadiusSmall: t.radiusXs,
    // —— 阴影分级：1=卡片 2=气泡/下拉/Message 3=模态/抽屉 ——
    boxShadow1: t.shadow1,
    boxShadow2: t.shadowPopover,
    boxShadow3: t.glassShadow,
    // —— 关闭按钮 ——
    closeIconColor: t.text3,
    closeIconColorHover: t.text2,
    closeIconColorPressed: t.text1,
    closeColorHover: t.hoverBg,
    closeColorPressed: t.bgCard,
    // —— 排版 ——
    fontFamily: t.fontSans,
    fontFamilyMono: t.fontMono,
    fontWeightStrong: '600',
    // —— 动效 ——
    cubicBezierEaseInOut: t.ease,
    cubicBezierEaseOut: t.ease,
    cubicBezierEaseIn: t.ease,
  },
  Layout: {
    color: t.bgPage,
    siderColor: t.bgCard,
    headerColor: t.bgCard,
  },
  Breadcrumb: {
    itemTextColor: t.text3,
    itemTextColorHover: t.text1,
    itemTextColorPressed: t.text1,
    fontWeightActive: '600',
  },
  Dialog: {
    borderRadius: t.radiusLg,
    iconColor: t.brand,
  },
  Drawer: {
    borderRadius: '0',
  },
  Card: {
    borderRadius: t.radiusMd,
  },
  Tooltip: {
    borderRadius: t.radiusSm,
  },
}

export default darkThemeOverrides
