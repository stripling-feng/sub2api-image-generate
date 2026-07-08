import { createApp } from "vue";
import { createPinia } from "pinia";
import { createRouter, createWebHistory } from "vue-router";
import Root from "./Root.vue";
import App from "./App.vue";
import IndexPage from "./IndexPage.vue";
import "./styles.css";

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: "/", component: App },
    { path: "/index", component: IndexPage }
  ]
});

createApp(Root).use(createPinia()).use(router).mount("#app");
