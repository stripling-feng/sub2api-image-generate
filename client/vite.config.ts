import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const apiProxy = process.env.VITE_API_PROXY ?? "http://localhost:5001";

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 6655,
    proxy: {
      "/api": {
        target: apiProxy,
        changeOrigin: true
      },
      "/uploads": {
        target: apiProxy,
        changeOrigin: true
      }
    }
  }
});
