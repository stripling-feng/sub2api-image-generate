import { defineConfig } from "vite";
import vue from "@vitejs/plugin-vue";

const apiProxy = process.env.VITE_API_PROXY ?? "http://localhost:5001";
const imageProxy = process.env.VITE_IMAGE_PROXY ?? "https://image.tcboys.de";
const base = process.env.VITE_BASE_PATH ?? "/";

export default defineConfig({
  base,
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
      },
      "/img": {
        target: imageProxy,
        changeOrigin: true
      }
    }
  }
});
