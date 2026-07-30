import type { GlobalThemeOverrides } from 'naive-ui'

const lightThemeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#FF8FAB',
    primaryColorHover: '#FF7A9A',
    primaryColorPressed: '#FF6B8A',
    primaryColorSuppl: '#FFB3C6',
    bodyColor: '#FFF0F3',
    cardColor: '#FFFFFF',
    textColor1: '#4A4A4A',
    textColor2: '#8A8A8A',
    textColor3: '#B0B0B0',
    borderColor: '#FFD6E0',
    borderRadius: '12px',
    borderRadiusSmall: '8px',
    hoverColor: '#FFF5F7',
  },
  Button: {
    borderRadiusMedium: '20px',
    borderRadiusSmall: '16px',
  },
  Card: {
    borderRadius: '16px',
  },
  Input: {
    borderRadius: '12px',
  },
  Tag: {
    borderRadius: '12px',
  },
}

export default lightThemeOverrides
