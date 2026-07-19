import { defineStore } from 'pinia'
import { applyTheme, defaultTheme, loadTheme, saveTheme } from '../utils/theme'

export const useThemeStore = defineStore('theme', {
  state: () => ({
    ...defaultTheme,
  }),
  actions: {
    initTheme() {
      Object.assign(this, loadTheme())
      applyTheme(this.$state)
    },
    updateTheme(patch) {
      Object.assign(this, patch)
      applyTheme(this.$state)
      saveTheme(this.$state)
    },
    resetTheme() {
      Object.assign(this, defaultTheme)
      applyTheme(this.$state)
      saveTheme(this.$state)
    },
  },
})
