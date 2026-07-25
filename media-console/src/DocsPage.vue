<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from "vue";
import { ArrowLeft } from "lucide-vue-next";
import { marked } from "marked";
import DOMPurify from "dompurify";
import { api } from "./api";

type DocKey = "image" | "video";

type PublicDoc = {
  key: DocKey;
  title: string;
  content: string;
  updatedAt?: string | null;
};

type Heading = {
  id: string;
  text: string;
  level: 2 | 3;
};

const docItems: Array<{ key: DocKey; label: string }> = [
  { key: "image", label: "图片文档" },
  { key: "video", label: "视频文档" }
];

const docEndpoints: Record<DocKey, string> = {
  image: "/api/docs/image",
  video: "/api/docs/video"
};

const activeDocKey = ref<DocKey>("image");
const doc = ref<PublicDoc | null>(null);
const headings = ref<Heading[]>([]);
const loading = ref(false);
const error = ref("");

const renderedContent = computed(() => {
  if (!doc.value?.content) return "";
  return DOMPurify.sanitize(marked.parse(doc.value.content, { async: false }) as string);
});

function extractHeadings(markdown: string): Heading[] {
  const usedIds = new Map<string, number>();
  return markdown
    .split(/\r?\n/)
    .map((line) => /^(#{2,3})\s+(.+?)\s*$/.exec(line))
    .filter((match): match is RegExpExecArray => Boolean(match))
    .map((match) => {
      const text = stripMarkdownInline(match[2]);
      const baseId = slugify(text);
      const count = usedIds.get(baseId) ?? 0;
      usedIds.set(baseId, count + 1);
      return {
        id: count ? `${baseId}-${count + 1}` : baseId,
        text,
        level: match[1].length as 2 | 3
      };
    });
}

function stripMarkdownInline(value: string) {
  return value
    .replace(/`([^`]+)`/g, "$1")
    .replace(/\*\*([^*]+)\*\*/g, "$1")
    .replace(/\*([^*]+)\*/g, "$1")
    .replace(/\[([^\]]+)]\([^)]+\)/g, "$1")
    .trim();
}

function slugify(value: string) {
  const slug = value
    .toLowerCase()
    .trim()
    .replace(/[^\p{L}\p{N}]+/gu, "-")
    .replace(/^-+|-+$/g, "");
  return slug || "heading";
}

function assignHeadingIds() {
  const content = document.querySelector(".doc-markdown");
  if (!content) return;
  const renderedHeadings = Array.from(content.querySelectorAll("h2, h3"));
  renderedHeadings.forEach((element, index) => {
    const heading = headings.value[index];
    if (heading) element.id = heading.id;
  });
}

async function loadDoc(key: DocKey) {
  activeDocKey.value = key;
  loading.value = true;
  error.value = "";
  doc.value = null;
  headings.value = [];

  try {
    const result = await api.get<PublicDoc>(docEndpoints[key]);
    doc.value = result;
    headings.value = extractHeadings(result.content ?? "");
    loading.value = false;
    await nextTick();
    assignHeadingIds();
  } catch (err) {
    error.value = err instanceof Error ? err.message : "文档加载失败";
    loading.value = false;
  }
}

function scrollToHeading(id: string) {
  document.getElementById(id)?.scrollIntoView({ behavior: "smooth", block: "start" });
}

watch(renderedContent, async () => {
  await nextTick();
  assignHeadingIds();
});

onMounted(() => {
  document.documentElement.classList.add("docs-scroll-page");
  document.body.classList.add("docs-scroll-page");
  document.getElementById("app")?.classList.add("docs-scroll-page");
  document.title = "文档 - 天才少年";
  void loadDoc("image");
});

onBeforeUnmount(() => {
  document.documentElement.classList.remove("docs-scroll-page");
  document.body.classList.remove("docs-scroll-page");
  document.getElementById("app")?.classList.remove("docs-scroll-page");
});
</script>

<template>
  <main class="docs-page">
    <header class="docs-page-header">
      <div class="docs-topbar">
        <a class="docs-back-link" href="/index" aria-label="返回首页">
          <ArrowLeft :size="16" />
          <span>返回首页</span>
        </a>

        <nav class="docs-type-tabs" aria-label="文档分类">
          <button
            v-for="item in docItems"
            :key="item.key"
            type="button"
            :class="{ active: activeDocKey === item.key }"
            @click="loadDoc(item.key)"
          >
            {{ item.label }}
          </button>
        </nav>
      </div>
    </header>

    <section class="docs-page-shell">
      <aside class="docs-page-sidebar" aria-label="标题导航">
        <div class="docs-toc-head">
          <span>目录</span>
        </div>
        <div v-if="headings.length" class="doc-nav">
          <button
            v-for="heading in headings"
            :key="heading.id"
            type="button"
            class="doc-heading-link"
            :class="`level-${heading.level}`"
            @click="scrollToHeading(heading.id)"
          >
            {{ heading.text }}
          </button>
        </div>
        <div v-else class="docs-toc-empty">暂无标题</div>
      </aside>

      <div class="docs-page-content">
        <div v-if="loading" class="doc-loading">文档加载中...</div>
        <div v-else-if="error" class="doc-error">
          <strong>文档暂不可用</strong>
          <span>{{ error }}</span>
        </div>
        <section v-else-if="doc?.content" class="doc-section doc-markdown" v-html="renderedContent"></section>
        <div v-else class="doc-error">
          <strong>暂无文档</strong>
          <span>后台还没有绑定 Markdown 文档。</span>
        </div>
      </div>
    </section>
  </main>
</template>
