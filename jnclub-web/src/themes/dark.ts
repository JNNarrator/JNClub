import type { GlobalThemeOverrides } from 'naive-ui'

const darkThemeOverrides: GlobalThemeOverrides = {
  common: {
    primaryColor: '#FF8FAB',
    primaryColorHover: '#FFA0B8',
    primaryColorPressed: '#FFB3C6',
    primaryColorSuppl: '#FF7A9A',
    bodyColor: '#1a1a2e',
    cardColor: '#2a2a3c',
    textColor1: '#E8E8E8',
    textColor2: '#A0A0A0',
    textColor3: '#707070',
    borderColor: '#3d3d5c',
    borderRadius: '12px',
    borderRadiusSmall: '8px',
    hoverColor: '#33334d',
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

export default darkThemeOverrides
