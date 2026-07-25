<script setup lang="ts">
import { ref } from "vue";
import { BookOpenText, X } from "lucide-vue-next";

defineEmits<{
  close: [];
}>();

const activeDocCategory = ref<"images">("images");
const activeDocTab = ref<"gpt-image-2" | "gpt-image-2-4k">("gpt-image-2");
</script>

<template>
  <div class="doc-modal" role="dialog" aria-modal="true" @click.self="$emit('close')">
    <div class="doc-card">
      <div class="doc-header">
        <div class="section-title-label">
          <BookOpenText :size="18" />
          <div>
            <h2>文档</h2>
          </div>
        </div>
        <button class="icon-btn small" type="button" title="关闭文档" @click="$emit('close')">
          <X :size="16" />
        </button>
      </div>

      <div class="doc-layout">
        <aside class="doc-sidebar" role="tablist" aria-label="文档分类">
          <button
            type="button"
            :class="{ active: activeDocCategory === 'images' }"
            role="tab"
            :aria-selected="activeDocCategory === 'images'"
            @click="activeDocCategory = 'images'"
          >
            生图
          </button>
        </aside>

        <div class="doc-content">
          <div class="doc-tabs" role="tablist" aria-label="模型文档">
            <button
              type="button"
              :class="{ active: activeDocTab === 'gpt-image-2' }"
              role="tab"
              :aria-selected="activeDocTab === 'gpt-image-2'"
              @click="activeDocTab = 'gpt-image-2'"
            >
              gpt-image-2
            </button>
            <button
              type="button"
              :class="{ active: activeDocTab === 'gpt-image-2-4k' }"
              role="tab"
              :aria-selected="activeDocTab === 'gpt-image-2-4k'"
              @click="activeDocTab = 'gpt-image-2-4k'"
            >
              gpt-image-2-4k
            </button>
          </div>

          <div class="doc-body">
            <section class="doc-section">
              <div class="doc-callout">
                <strong>快速接入</strong>
                <ul>
                  <li><strong>Base URL：</strong><code>https://api.tcboys.de/v1</code></li>
                  <li><strong>鉴权：</strong>使用中转站控制台创建的 API Key，所有请求携带 <code>Authorization: Bearer &lt;API_KEY&gt;</code>。</li>
                  <li><strong>异步流程：</strong>POST 创建任务，读取返回的 <code>id</code>，再用对应 GET 接口轮询。</li>
                  <li><strong>状态：</strong><code>queued</code> / <code>processing</code> 表示继续轮询，<code>completed</code> 表示成功，<code>failed</code> 表示失败。</li>
                </ul>
              </div>

              <h3>查询任务</h3>
              <pre><code>curl "https://api.tcboys.de/v1/images/tasks/{task_id}" \
  -H "Authorization: Bearer &lt;API_KEY&gt;"</code></pre>
              <p>文生图和图片编辑任务统一使用该接口轮询。任务完成后读取 <code>data[].url</code>。</p>

              <h3>错误返回</h3>
              <pre><code>{
  "error": {
    "message": "Invalid API key"
  }
}</code></pre>
            </section>

            <section v-if="activeDocTab === 'gpt-image-2'" class="doc-section">
              <div class="doc-callout">
                <strong>GPT-Image-2</strong>
                <ul>
                  <li><strong>size：</strong>请传画幅比例，例如 1:1、3:2、2:3。</li>
                  <li><strong>文生图：</strong>JSON POST /images/generations。</li>
                  <li><strong>参考图 / 多图叠图：</strong>multipart POST /images/edits，字段使用 image 或 image[]。</li>
                  <li><strong>计费：</strong>按所选模型后台配置的单张价格扣除；提交前冻结额度，成功后结算，失败自动释放。</li>
                </ul>
              </div>

              <h3>请求字段</h3>
              <div class="doc-table-wrap">
                <table class="doc-table">
                  <thead>
                    <tr>
                      <th>字段</th>
                      <th>是否必填</th>
                      <th>说明</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td>model</td>
                      <td>是</td>
                      <td>固定传中转站模型名 gpt-image-2。</td>
                    </tr>
                    <tr>
                      <td>prompt</td>
                      <td>是</td>
                      <td>图像描述提示词；edits 时可在 prompt 中用 @图片1 等引用参考图。</td>
                    </tr>
                    <tr>
                      <td>size</td>
                      <td>是</td>
                      <td>画幅比例（推荐），如 1:1、3:2、2:3；兼容传像素但不保证输出像素一致。</td>
                    </tr>
                    <tr>
                      <td>n</td>
                      <td>否</td>
                      <td>生成张数，1-10，默认 1。</td>
                    </tr>
                    <tr>
                      <td>async / stream</td>
                      <td>是</td>
                      <td>固定传 async=true、stream=false。</td>
                    </tr>
                    <tr>
                      <td>image / image[]</td>
                      <td>编辑时必填</td>
                      <td>edits 端点 multipart 参考图字段，最多 10 张。</td>
                    </tr>
                    <tr>
                      <td>mask</td>
                      <td>否</td>
                      <td>PNG 蒙版，透明区域为编辑区，尺寸须与第一张参考图一致。</td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <h3>文生图 curl</h3>
              <pre><code>curl -X POST "https://api.tcboys.de/v1/images/generations" \
  -H "Authorization: Bearer &lt;API_KEY&gt;" \
  -H "Content-Type: application/json" \
  -d '{
    "async": true,
    "model": "gpt-image-2",
    "n": 1,
    "prompt": "一只橘猫坐在窗台上，午后阳光",
    "size": "1:1",
    "stream": false
  }'</code></pre>

              <h3>图片编辑 curl</h3>
              <pre><code>curl -X POST "https://api.tcboys.de/v1/images/edits" \
  -H "Authorization: Bearer &lt;API_KEY&gt;" \
  -F "model=gpt-image-2" \
  -F "prompt=把背景改成雪山" \
  -F "size=1:1" \
  -F "n=1" \
  -F "async=true" \
  -F "stream=false" \
  -F "image[]=@./reference-1.png" \
  -F "image[]=@./reference-2.png"

# 可选蒙版：-F "mask=@./mask.png"</code></pre>

              <h3>返回示例</h3>
              <pre><code>{
  "created_at": 1715923200,
  "id": "task_img_01HZX8A2...",
  "model": "gpt-image-2",
  "object": "image.generation",
  "progress": "10%",
  "status": "queued"
}</code></pre>
              <pre><code>{
  "data": [
    {
      "url": "https://example.com/image.png"
    }
  ],
  "id": "task_img_01HZX8A2...",
  "object": "image.generation",
  "progress": "100%",
  "status": "completed"
}</code></pre>
            </section>

            <section v-else class="doc-section">
              <div class="doc-callout">
                <strong>GPT-Image-2-4K</strong>
                <ul>
                  <li><strong>用途：</strong>用于高清出图，模型选择 gpt-image-2-4k。</li>
                  <li><strong>尺寸：</strong>可选择 Auto、2K、4K，并按画幅比例计算最终像素尺寸。</li>
                  <li><strong>返回格式：</strong>当前项目使用 url，再由后端下载到本地保存。</li>
                </ul>
              </div>

              <h3>请求字段</h3>
              <div class="doc-table-wrap">
                <table class="doc-table">
                  <thead>
                    <tr>
                      <th>字段</th>
                      <th>是否必填</th>
                      <th>说明</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td>model</td>
                      <td>是</td>
                      <td>固定传模型广场展示名 gpt-image-2-4k。</td>
                    </tr>
                    <tr>
                      <td>prompt</td>
                      <td>是</td>
                      <td>图片描述提示词。</td>
                    </tr>
                    <tr>
                      <td>size</td>
                      <td>是</td>
                      <td>Auto 或最终像素尺寸，例如 2048x2048、3840x2160。</td>
                    </tr>
                    <tr>
                      <td>n</td>
                      <td>否</td>
                      <td>生成张数，1-10。</td>
                    </tr>
                    <tr>
                      <td>response_format</td>
                      <td>否</td>
                      <td>返回格式，当前项目固定使用 url。</td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <h3>尺寸换算</h3>
              <div class="doc-table-wrap">
                <table class="doc-table">
                  <thead>
                    <tr>
                      <th>尺寸</th>
                      <th>比例</th>
                      <th>最终尺寸</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr>
                      <td>2K</td>
                      <td>1:1 / 16:9 / 9:16</td>
                      <td>2048x2048 / 2048x1152 / 1152x2048</td>
                    </tr>
                    <tr>
                      <td>4K</td>
                      <td>1:1 / 16:9 / 9:16</td>
                      <td>2880x2880 / 3840x2160 / 2160x3840</td>
                    </tr>
                    <tr>
                      <td>Auto</td>
                      <td>Auto</td>
                      <td>交给上游自动决定</td>
                    </tr>
                  </tbody>
                </table>
              </div>

              <h3>文生图 curl</h3>
              <pre><code>curl -X POST "https://api.tcboys.de/v1/images/generations" \
  -H "Authorization: Bearer &lt;API_KEY&gt;" \
  -H "Content-Type: application/json" \
  -d '{
    "async": true,
    "model": "gpt-image-2-4k",
    "prompt": "电影感城市夜景",
    "n": 1,
    "size": "3840x2160",
    "response_format": "url",
    "stream": false
  }'</code></pre>

              <p>带参考图时改用 <code>POST /v1/images/edits</code>，multipart 字段与 GPT-Image-2 示例一致。</p>
            </section>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
