import { createApp } from "vue";
import { createPinia } from "pinia";
import { createRouter, createWebHistory } from "vue-router";
import Root from "./Root.vue";
import App from "./App.vue";
import DocsPage from "./DocsPage.vue";
import IndexPage from "./IndexPage.vue";
import VideoWorkbench from "./VideoWorkbench.vue";
import "./styles.css";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", component: App },
    { path: "/index", component: IndexPage },
    { path: "/docs", component: DocsPage },
    { path: "/video", component: VideoWorkbench }
  ]
});

createApp(Root).use(createPinia()).use(router).mount("#app");
