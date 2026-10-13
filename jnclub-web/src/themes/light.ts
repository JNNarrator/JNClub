import type { GlobalThemeOverrides } from 'naive-ui'
import { lightTokens as t } from './tokens'

/**
 * naive-ui 主题覆盖（亮色）—— M2-1
 *
 * 设计原则：naive-ui 的绝大多数组件变量都从 common 派生
 * （popoverColor / boxShadow2 / textColor2 / hoverColor / borderRadius / dividerColor …），
 * 因此**优先补 common**，组件段只覆盖 common 里没有对应项的少数属性。
 * 这样一次改动就能让 Modal / Dialog / Drawer / Popover / Dropdown / Message /
 * Notification / Tooltip / Card 全部落到 token 上，而不是逐个组件打补丁。
 *
 * 之前只覆盖了 common 的 8 个字段，导致弹层沿用了 naive 默认的
 * Material 灰阶（textColor2 rgb(51,54,57)）与 Material 缓动
 * （cubic-bezier(.4,0,.2,1)），与站点的 Apple 弹性曲线割裂。
 */
const lightThemeOverrides: GlobalThemeOverrides = {
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
    // —— 文字（naive 默认 rgb(31,34,37)/rgb(51,54,57)/rgb(118,124,130) 与 token 不一致）——
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
    // —— 关闭按钮（Dialog / Drawer / Message / Notification 共用）——
    closeIconColor: t.text3,
    closeIconColorHover: t.text2,
    closeIconColorPressed: t.text1,
    closeColorHover: t.hoverBg,
    closeColorPressed: t.bgPage,
    // —— 排版 ——
    fontFamily: t.fontSans,
    fontFamilyMono: t.fontMono,
    fontWeightStrong: '600',
    // —— 动效：把 naive 的 Material 曲线换成站点的 Apple 弹性缓动 ——
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
  // 弹层：颜色与阴影已由 common 覆盖，这里只补 common 没有的几何
  Dialog: {
    borderRadius: t.radiusLg,
    iconColor: t.brand,
  },
  // 抽屉贴边，圆角必须归零（naive 默认会跟随 common.borderRadius 变成 10px）
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

export default lightThemeOverrides
