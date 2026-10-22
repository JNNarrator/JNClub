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
    // cardColor 归位到 L1（--surface-1），与 light.ts 对称；值同为 #26262A，零视觉变化
    cardColor: t.surface1,
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
    // —— 字号阶梯（P2-5，与 light.ts 同值：字号不随明暗分档）——
    // 见 light.ts 的说明：绑定到 --fs-* 阶梯以防 naive 升级漂移，取值与默认等值、零视觉变化。
    fontSize: t.fsBase, // 14px
    fontSizeMini: t.fsSm, // 12px
    fontSizeTiny: t.fsSm, // 12px
    fontSizeSmall: t.fsBase, // 14px
    fontSizeMedium: t.fsBase, // 14px
    fontSizeLarge: t.fsLg, // 15px
    fontSizeHuge: t.fsXl, // 16px
    // —— 动效 ——
    cubicBezierEaseInOut: t.ease,
    cubicBezierEaseOut: t.ease,
    cubicBezierEaseIn: t.ease,
  },
  Layout: {
    color: t.bgPage,
    // 侧栏改 L1 实色，与 SideNav.vue 的 --surface-1 同源（见 light.ts）
    siderColor: t.surface1,
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
    // 显式化，与 light.ts 对称
    color: t.surface1,
    borderColor: t.surfaceBorder,
  },
  Tooltip: {
    borderRadius: t.radiusSm,
  },
}

export default darkThemeOverrides
