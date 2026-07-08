<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { ArrowRight, BadgeDollarSign, BookOpenText, PlugZap, ShieldCheck } from "lucide-vue-next";
import * as THREE from "three";
import DocsDialog from "./DocsDialog.vue";

const featureCards = [
  {
    title: "一键接入",
    text: "获取一个 API 密钥，即可调用所有已接入的 AI 模型，无需分别申请。",
    icon: PlugZap,
    tone: "primary"
  },
  {
    title: "稳定可靠",
    text: "智能调度多个上游账号，自动切换和负载均衡，告别频繁报错。",
    icon: ShieldCheck,
    tone: "cyan"
  },
  {
    title: "用多少付多少",
    text: "按实际使用量计费，支持设置配额上限，团队用量一目了然。",
    icon: BadgeDollarSign,
    tone: "violet"
  }
];

const particlesMount = ref<HTMLDivElement | null>(null);
const orbMount = ref<HTMLDivElement | null>(null);
const orbTransform = ref("rotateX(0deg) rotateY(0deg)");
const isOrbActive = ref(false);
const docDialogOpen = ref(false);
let cleanupParticles: (() => void) | undefined;
let cleanupOrb: (() => void) | undefined;

function initThreeParticles() {
  const mount = particlesMount.value;
  if (!mount) return;

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(60, window.innerWidth / window.innerHeight, 0.1, 1000);
  camera.position.z = 7;

  const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  renderer.setSize(window.innerWidth, window.innerHeight);
  mount.appendChild(renderer.domElement);

  const particleCount = 1300;
  const positions = new Float32Array(particleCount * 3);
  const colors = new Float32Array(particleCount * 3);
  const colorA = new THREE.Color("#4F7DFF");
  const colorB = new THREE.Color("#8B5CF6");
  const colorC = new THREE.Color("#00E5FF");

  for (let i = 0; i < particleCount; i += 1) {
    const i3 = i * 3;
    positions[i3] = (Math.random() - 0.5) * 18;
    positions[i3 + 1] = (Math.random() - 0.5) * 10;
    positions[i3 + 2] = (Math.random() - 0.5) * 10;
    const mixed = colorA.clone().lerp(i % 3 === 0 ? colorC : colorB, Math.random());
    colors[i3] = mixed.r;
    colors[i3 + 1] = mixed.g;
    colors[i3 + 2] = mixed.b;
  }

  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
  geometry.setAttribute("color", new THREE.BufferAttribute(colors, 3));

  const material = new THREE.PointsMaterial({
    size: 0.018,
    vertexColors: true,
    transparent: true,
    opacity: 0.72,
    blending: THREE.AdditiveBlending,
    depthWrite: false
  });

  const points = new THREE.Points(geometry, material);
  scene.add(points);

  const mouse = new THREE.Vector2();
  const onMouseMove = (event: MouseEvent) => {
    mouse.x = (event.clientX / window.innerWidth - 0.5) * 2;
    mouse.y = (event.clientY / window.innerHeight - 0.5) * 2;
  };

  const onResize = () => {
    camera.aspect = window.innerWidth / window.innerHeight;
    camera.updateProjectionMatrix();
    renderer.setSize(window.innerWidth, window.innerHeight);
  };

  window.addEventListener("mousemove", onMouseMove);
  window.addEventListener("resize", onResize);

  let frame = 0;
  const animate = () => {
    frame = window.requestAnimationFrame(animate);
    points.rotation.y += 0.0008;
    points.rotation.x += 0.00025;
    camera.position.x += (mouse.x * 0.35 - camera.position.x) * 0.025;
    camera.position.y += (-mouse.y * 0.22 - camera.position.y) * 0.025;
    camera.lookAt(0, 0, 0);
    renderer.render(scene, camera);
  };
  animate();

  cleanupParticles = () => {
    window.cancelAnimationFrame(frame);
    window.removeEventListener("mousemove", onMouseMove);
    window.removeEventListener("resize", onResize);
    geometry.dispose();
    material.dispose();
    renderer.dispose();
    renderer.domElement.remove();
  };
}

function createPlanetTexture() {
  const canvas = document.createElement("canvas");
  canvas.width = 1024;
  canvas.height = 512;
  const context = canvas.getContext("2d");
  if (!context) return null;

  const base = context.createLinearGradient(0, 0, canvas.width, canvas.height);
  base.addColorStop(0, "#72f4ff");
  base.addColorStop(0.18, "#236dff");
  base.addColorStop(0.46, "#132178");
  base.addColorStop(0.72, "#1b145d");
  base.addColorStop(1, "#5c3dff");
  context.fillStyle = base;
  context.fillRect(0, 0, canvas.width, canvas.height);

  context.globalCompositeOperation = "screen";
  context.strokeStyle = "rgba(126, 239, 255, 0.24)";
  context.lineWidth = 1;
  for (let x = 24; x < canvas.width; x += 58) {
    context.beginPath();
    context.moveTo(x, 0);
    context.bezierCurveTo(x + 34, 140, x - 38, 320, x + 18, canvas.height);
    context.stroke();
  }
  for (let y = 36; y < canvas.height; y += 48) {
    context.beginPath();
    context.moveTo(0, y);
    context.bezierCurveTo(260, y - 18, 560, y + 22, canvas.width, y - 8);
    context.stroke();
  }

  context.strokeStyle = "rgba(255,255,255,0.3)";
  context.lineWidth = 3;
  context.lineCap = "round";
  for (let i = 0; i < 24; i += 1) {
    const y = 38 + Math.random() * 420;
    const x = Math.random() * 900;
    context.beginPath();
    context.moveTo(x - 120, y);
    context.bezierCurveTo(x - 40, y - 34, x + 90, y + 28, x + 240, y - 18);
    context.stroke();
  }

  for (let i = 0; i < 48; i += 1) {
    const x = Math.random() * canvas.width;
    const y = Math.random() * canvas.height;
    const radius = 16 + Math.random() * 58;
    const glow = context.createRadialGradient(x, y, 0, x, y, radius);
    glow.addColorStop(0, "rgba(126,239,255,0.45)");
    glow.addColorStop(0.32, "rgba(79,125,255,0.18)");
    glow.addColorStop(1, "rgba(0,0,0,0)");
    context.fillStyle = glow;
    context.beginPath();
    context.arc(x, y, radius, 0, Math.PI * 2);
    context.fill();
  }

  context.fillStyle = "rgba(255,255,255,0.72)";
  for (let i = 0; i < 90; i += 1) {
    context.beginPath();
    context.arc(Math.random() * canvas.width, Math.random() * canvas.height, 0.8 + Math.random() * 1.6, 0, Math.PI * 2);
    context.fill();
  }

  context.globalCompositeOperation = "source-over";
  const highlight = context.createRadialGradient(244, 118, 0, 244, 118, 360);
  highlight.addColorStop(0, "rgba(255,255,255,0.34)");
  highlight.addColorStop(0.52, "rgba(126,239,255,0.08)");
  highlight.addColorStop(1, "rgba(255,255,255,0)");
  context.fillStyle = highlight;
  context.fillRect(0, 0, canvas.width, canvas.height);

  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.wrapS = THREE.RepeatWrapping;
  texture.wrapT = THREE.ClampToEdgeWrapping;
  return texture;
}

function createCloudTexture() {
  const canvas = document.createElement("canvas");
  canvas.width = 1024;
  canvas.height = 512;
  const context = canvas.getContext("2d");
  if (!context) return null;

  context.clearRect(0, 0, canvas.width, canvas.height);
  context.strokeStyle = "rgba(190,248,255,0.42)";
  context.lineWidth = 7;
  context.lineCap = "round";

  for (let i = 0; i < 32; i += 1) {
    const y = 46 + Math.random() * 420;
    const x = Math.random() * 920;
    context.beginPath();
    context.moveTo(x - 120, y);
    context.bezierCurveTo(x - 42, y - 18, x + 82, y + 16, x + 220, y - 10);
    context.stroke();
  }

  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.wrapS = THREE.RepeatWrapping;
  texture.wrapT = THREE.ClampToEdgeWrapping;
  return texture;
}

function initThreeOrb() {
  const mount = orbMount.value;
  if (!mount) return;

  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(45, 1, 0.1, 100);
  camera.position.set(0, 0.35, 6.6);

  const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
  mount.appendChild(renderer.domElement);

  const root = new THREE.Group();
  const planetGroup = new THREE.Group();
  root.add(planetGroup);
  scene.add(root);

  scene.add(new THREE.AmbientLight(0x88ccff, 0.85));
  const keyLight = new THREE.PointLight(0x9ff7ff, 3.2, 16);
  keyLight.position.set(-3.4, 3.1, 4.2);
  scene.add(keyLight);
  const rimLight = new THREE.PointLight(0x8b5cf6, 2.2, 14);
  rimLight.position.set(4, -1.8, 3);
  scene.add(rimLight);

  const planetTexture = createPlanetTexture();
  const cloudTexture = createCloudTexture();
  const planet = new THREE.Mesh(
    new THREE.SphereGeometry(1.86, 128, 96),
    new THREE.MeshStandardMaterial({
      map: planetTexture ?? undefined,
      color: 0xffffff,
      emissive: 0x1238ff,
      emissiveIntensity: 0.34,
      metalness: 0.18,
      roughness: 0.3
    })
  );
  planetGroup.add(planet);

  const wire = new THREE.Mesh(
    new THREE.SphereGeometry(1.878, 64, 48),
    new THREE.MeshBasicMaterial({
      color: 0xbdf8ff,
      wireframe: true,
      transparent: true,
      opacity: 0.11,
      blending: THREE.AdditiveBlending,
      depthWrite: false
    })
  );
  planetGroup.add(wire);

  const clouds = new THREE.Mesh(
    new THREE.SphereGeometry(1.895, 96, 64),
    new THREE.MeshBasicMaterial({
      map: cloudTexture ?? undefined,
      color: 0xffffff,
      transparent: true,
      opacity: 0.22,
      blending: THREE.AdditiveBlending,
      depthWrite: false
    })
  );
  planetGroup.add(clouds);

  const atmosphere = new THREE.Mesh(
    new THREE.SphereGeometry(2.08, 64, 48),
    new THREE.MeshBasicMaterial({
      color: 0x00e5ff,
      transparent: true,
      opacity: 0.18,
      side: THREE.BackSide,
      blending: THREE.AdditiveBlending
    })
  );
  planetGroup.add(atmosphere);

  const innerGlow = new THREE.Mesh(
    new THREE.SphereGeometry(1.76, 64, 48),
    new THREE.MeshBasicMaterial({
      color: 0x4f7dff,
      transparent: true,
      opacity: 0.16,
      blending: THREE.AdditiveBlending,
      depthWrite: false
    })
  );
  planetGroup.add(innerGlow);

  const auraGeometry = new THREE.BufferGeometry();
  const auraPositions = new Float32Array(240);
  for (let i = 0; i < auraPositions.length; i += 3) {
    const angle = Math.random() * Math.PI * 2;
    const height = (Math.random() - 0.5) * 1.8;
    const radius = 2.06 + Math.random() * 0.36;
    auraPositions[i] = Math.cos(angle) * radius;
    auraPositions[i + 1] = height;
    auraPositions[i + 2] = Math.sin(angle) * radius * 0.62;
  }
  auraGeometry.setAttribute("position", new THREE.BufferAttribute(auraPositions, 3));
  const auraDust = new THREE.Points(
    auraGeometry,
    new THREE.PointsMaterial({
      color: 0x7eefff,
      size: 0.022,
      transparent: true,
      opacity: 0.28,
      blending: THREE.AdditiveBlending,
      depthWrite: false
    })
  );
  planetGroup.add(auraDust);

  const starGeometry = new THREE.BufferGeometry();
  const starPositions = new Float32Array(360);
  for (let i = 0; i < starPositions.length; i += 3) {
    starPositions[i] = (Math.random() - 0.5) * 7;
    starPositions[i + 1] = (Math.random() - 0.5) * 5.2;
    starPositions[i + 2] = (Math.random() - 0.5) * 3.2;
  }
  starGeometry.setAttribute("position", new THREE.BufferAttribute(starPositions, 3));
  const stars = new THREE.Points(
    starGeometry,
    new THREE.PointsMaterial({
      color: 0x9ff7ff,
      size: 0.018,
      transparent: true,
      opacity: 0.65,
      blending: THREE.AdditiveBlending
    })
  );
  scene.add(stars);

  const resize = () => {
    const rect = mount.getBoundingClientRect();
    const size = Math.max(320, Math.floor(Math.min(rect.width || 620, rect.height || 620)));
    renderer.setSize(size, size, false);
    camera.aspect = 1;
    camera.updateProjectionMatrix();
  };
  resize();
  window.addEventListener("resize", resize);

  let frame = 0;
  let lastTime = performance.now();
  let elapsedTime = 0;
  const animate = () => {
    const now = performance.now();
    const delta = Math.min((now - lastTime) / 1000, 0.033);
    lastTime = now;
    elapsedTime += delta;
    const speed = isOrbActive.value ? 2.9 : 1;
    frame = window.requestAnimationFrame(animate);
    planet.rotation.y += delta * 0.28 * speed;
    planet.rotation.x = Math.sin(elapsedTime * 0.42) * 0.12;
    wire.rotation.y -= delta * 0.18 * speed;
    wire.rotation.x = -planet.rotation.x * 0.5;
    clouds.rotation.y += delta * 0.34 * speed;
    innerGlow.rotation.y += delta * 0.12;
    atmosphere.rotation.y += delta * 0.16;
    auraDust.rotation.y -= delta * 0.28 * speed;
    auraDust.rotation.x = Math.sin(elapsedTime * 0.36) * 0.08;
    stars.rotation.y -= delta * 0.035;
    root.rotation.y += ((isOrbActive.value ? 0.12 : 0.02) - root.rotation.y) * 0.025;
    root.rotation.x += ((isOrbActive.value ? -0.06 : 0.01) - root.rotation.x) * 0.025;
    renderer.render(scene, camera);
  };
  animate();

  cleanupOrb = () => {
    window.cancelAnimationFrame(frame);
    window.removeEventListener("resize", resize);
    scene.traverse((object) => {
      if (object instanceof THREE.Mesh || object instanceof THREE.Points) {
        object.geometry.dispose();
        const material = object.material;
        if (Array.isArray(material)) {
          material.forEach((item) => item.dispose());
        } else {
          material.dispose();
        }
      }
    });
    planetTexture?.dispose();
    cloudTexture?.dispose();
    renderer.dispose();
    renderer.domElement.remove();
  };
}

function handleOrbMove(event: MouseEvent) {
  isOrbActive.value = true;
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
  const mouseX = (event.clientX - rect.left) / rect.width - 0.5;
  const mouseY = (event.clientY - rect.top) / rect.height - 0.5;
  orbTransform.value = `rotateX(${(-mouseY * 16).toFixed(2)}deg) rotateY(${(mouseX * 24).toFixed(2)}deg)`;
}

function resetOrbMove() {
  isOrbActive.value = false;
  orbTransform.value = "rotateX(0deg) rotateY(0deg)";
}

function getParentOrigin() {
  if (document.referrer) {
    try {
      return new URL(document.referrer).origin;
    } catch {
      // Fall back to the current origin when the referrer is unavailable or malformed.
    }
  }

  return window.location.origin;
}

function navigateParent(path: "/dashboard" | "/index") {
  const target = `${getParentOrigin()}${path}`;

  try {
    if (window.parent && window.parent !== window) {
      window.parent.location.href = target;
      return;
    }
  } catch {
    // Cross-origin iframe policies can reject parent navigation in some sandbox modes.
  }

  window.location.href = target;
}

onMounted(() => {
  initThreeParticles();
  initThreeOrb();
  document.title = "天才少年";
});
onUnmounted(() => {
  cleanupParticles?.();
  cleanupOrb?.();
});
</script>

<template>
  <main class="index-page">
    <div ref="particlesMount" class="three-particles" aria-hidden="true"></div>
    <div class="particle-field" aria-hidden="true">
      <span v-for="dot in 72" :key="dot" :style="{ '--i': dot }"></span>
    </div>
    <div class="aurora" aria-hidden="true"></div>
    <div class="outer-ring" aria-hidden="true"></div>
    <div class="grid-perspective" aria-hidden="true"></div>

    <header class="index-header">
      <nav class="index-nav">
        <a class="brand" href="/index" @click.prevent="navigateParent('/index')">
          <span class="brand-orb" aria-hidden="true">
            <img src="/assets/genius-boy.jpg" alt="" />
          </span>
          天才少年
        </a>
        <div class="index-actions">
          <button class="index-doc-button" type="button" @click="docDialogOpen = true">
            <BookOpenText :size="16" />
            <span>文档</span>
          </button>
          <a class="console-button" href="/dashboard" @click.prevent="navigateParent('/dashboard')">
            <span>控制台</span>
            <ArrowRight :size="16" />
          </a>
        </div>
      </nav>
    </header>

    <section class="hero">
      <div class="hero-copy">
        <div class="headline-wrap">
          <h1>让世界见证你的创造力</h1>
          <h2>让每一个普通人，都拥有改变世界的力量</h2>
        </div>

        <div class="feature-grid">
          <article
            v-for="(feature, index) in featureCards"
            :key="feature.title"
            class="feature-card"
            :style="{ animationDelay: `${0.55 + index * 0.08}s` }"
          >
            <div :class="['feature-icon', feature.tone]">
              <component :is="feature.icon" :size="20" />
            </div>
            <h3>{{ feature.title }}</h3>
            <p>{{ feature.text }}</p>
          </article>
        </div>
      </div>

      <div class="orb-stage" aria-hidden="true">
        <div
          :class="['hero-orb', { 'is-active': isOrbActive }]"
          :style="{ transform: orbTransform }"
          @mouseenter="isOrbActive = true"
          @mousemove="handleOrbMove"
          @mouseleave="resetOrbMove"
        >
          <div ref="orbMount" class="orbital-system"></div>
        </div>
      </div>
    </section>

    <DocsDialog v-if="docDialogOpen" @close="docDialogOpen = false" />
  </main>
</template>

<style scoped>
.index-page {
  position: relative;
  min-height: 100dvh;
  overflow: hidden;
  background:
    radial-gradient(circle at 20% 0%, rgba(79, 125, 255, 0.18), transparent 28rem),
    radial-gradient(circle at 82% 8%, rgba(139, 92, 246, 0.18), transparent 32rem),
    #050816;
  color: #fff;
  isolation: isolate;
}

.three-particles,
.particle-field,
.aurora,
.outer-ring,
.grid-perspective {
  pointer-events: none;
  position: fixed;
  z-index: -1;
}

.three-particles {
  inset: 0;
  opacity: 0.75;
}

.three-particles :deep(canvas) {
  display: block;
}

.particle-field {
  display: none;
}

.particle-field span {
  --x: calc((var(--i) * 37 % 100) * 1%);
  --y: calc((var(--i) * 61 % 100) * 1%);
  position: absolute;
  left: var(--x);
  top: var(--y);
  width: 2px;
  height: 2px;
  border-radius: 999px;
  background: #00e5ff;
  box-shadow: 0 0 18px rgba(0, 229, 255, 0.95);
  animation: particleDrift calc(8s + (var(--i) % 7) * 1s) ease-in-out infinite alternate;
}

.aurora {
  inset: 0;
  background:
    radial-gradient(ellipse at 18% 24%, rgba(79, 125, 255, 0.48), transparent 34%),
    radial-gradient(ellipse at 70% 18%, rgba(139, 92, 246, 0.42), transparent 34%),
    radial-gradient(ellipse at 58% 78%, rgba(0, 229, 255, 0.22), transparent 38%);
  filter: blur(44px) saturate(130%);
  opacity: 0.68;
  animation: breathe 5s ease-in-out infinite;
}

.outer-ring {
  left: 50%;
  top: -26rem;
  width: 64rem;
  height: 64rem;
  border: 1px solid rgba(79, 125, 255, 0.15);
  border-radius: 999px;
  box-shadow: 0 0 180px rgba(79, 125, 255, 0.18);
  transform: translateX(-50%);
  animation: spin 90s linear infinite;
}

.grid-perspective {
  left: -10%;
  right: -10%;
  bottom: -28%;
  height: 72vh;
  opacity: 0.5;
  background-image:
    linear-gradient(rgba(79, 125, 255, 0.16) 1px, transparent 1px),
    linear-gradient(90deg, rgba(79, 125, 255, 0.16) 1px, transparent 1px);
  background-size: 72px 72px;
  transform: perspective(700px) rotateX(62deg) translateY(-18%);
  transform-origin: top center;
  mask-image: linear-gradient(to bottom, transparent, black 14%, black 58%, transparent);
}

.index-header {
  position: absolute;
  inset: 0 0 auto;
  z-index: 20;
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(5, 8, 22, 0.45);
  backdrop-filter: blur(24px);
}

.index-nav {
  width: min(1500px, 100%);
  height: 80px;
  margin: 0 auto;
  padding: 0 32px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.brand,
.index-doc-button,
.console-button {
  color: #fff;
  text-decoration: none;
}

.brand {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  font-size: 14px;
  font-weight: 700;
}

.brand-orb {
  position: relative;
  width: 44px;
  height: 44px;
  display: grid;
  place-items: center;
  overflow: hidden;
  border: 1px solid rgba(0, 229, 255, 0.35);
  border-radius: 999px;
  background: rgba(5, 8, 22, 0.82);
  box-shadow:
    0 0 0 3px rgba(5, 8, 22, 0.62),
    0 0 34px rgba(0, 229, 255, 0.32);
}

.brand-orb::after {
  position: absolute;
  inset: 0;
  content: "";
  border: 1px solid rgba(255, 255, 255, 0.34);
  border-radius: inherit;
  box-shadow: inset 0 0 16px rgba(0, 229, 255, 0.24);
}

.brand-orb img {
  width: 100%;
  height: 100%;
  display: block;
  object-fit: cover;
  transform: scale(1.12);
}

.index-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

.index-doc-button {
  min-height: 50px;
  padding: 0 22px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.08);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.12);
  color: #fff;
  font-size: 14px;
  font-weight: 750;
  backdrop-filter: blur(18px);
  transition: transform 180ms ease, border-color 180ms ease, background 180ms ease;
}

.index-doc-button:hover {
  transform: translateY(-2px);
  border-color: rgba(0, 229, 255, 0.52);
  background: rgba(0, 229, 255, 0.12);
}

.console-button {
  position: relative;
  min-height: 56px;
  padding: 0 32px;
  overflow: hidden;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 0;
  border-radius: 999px;
  background: linear-gradient(90deg, #4f7dff, #8b5cf6, #00e5ff);
  box-shadow: 0 0 60px rgba(79, 125, 255, 0.28);
  font-size: 14px;
  font-weight: 750;
  transition: transform 180ms ease;
}

.console-button::after {
  position: absolute;
  inset: -80% -30%;
  content: "";
  background: linear-gradient(110deg, transparent 35%, rgba(255, 255, 255, 0.35) 50%, transparent 65%);
  transform: translateX(-80%) rotate(8deg);
  transition: transform 700ms cubic-bezier(0.2, 0.8, 0.2, 1);
}

.console-button:hover {
  transform: translateY(-2px) scale(1.015);
}

.console-button:hover::after {
  transform: translateX(80%) rotate(8deg);
}

.console-button span,
.console-button svg {
  position: relative;
  z-index: 1;
}

.hero {
  width: min(1500px, 100%);
  min-height: 100dvh;
  margin: 0 auto;
  padding: 80px 32px 0;
  display: grid;
  grid-template-columns: 1.04fr 0.96fr;
  align-items: center;
  gap: 64px;
}

.hero-copy {
  max-width: 1024px;
  animation: riseIn 900ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

.headline-wrap {
  transform: translateY(-150px);
}

.headline-wrap h1 {
  max-width: 1120px;
  margin: 0;
  white-space: nowrap;
  background: linear-gradient(92deg, #ffffff 0%, #dce7ff 38%, #8fb4ff 66%, #c7b4ff 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  font-size: clamp(4.35rem, 5.35vw, 6.45rem);
  font-weight: 700;
  line-height: 0.95;
}

.headline-wrap h2 {
  max-width: 980px;
  margin: 44px 0 0;
  white-space: nowrap;
  color: #fff;
  font-size: clamp(1.65rem, 2.18vw, 2.65rem);
  font-weight: 700;
  line-height: 1.18;
}

.feature-grid {
  max-width: 940px;
  margin-top: 48px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
}

.feature-card {
  position: relative;
  min-height: 150px;
  padding: 20px;
  overflow: hidden;
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 24px;
  background: linear-gradient(135deg, rgba(255, 255, 255, 0.12), rgba(255, 255, 255, 0.045));
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.16), 0 24px 80px rgba(0, 0, 0, 0.28);
  backdrop-filter: blur(22px);
  transition: transform 220ms ease;
  animation: cardIn 650ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

.feature-card::before {
  position: absolute;
  inset: -1px;
  z-index: -1;
  content: "";
  border-radius: inherit;
  background: linear-gradient(135deg, rgba(79, 125, 255, 0.8), rgba(139, 92, 246, 0.48), rgba(0, 229, 255, 0.7));
  opacity: 0;
  transition: opacity 220ms ease;
}

.feature-card::after {
  position: absolute;
  inset: auto 0 0;
  height: 1px;
  content: "";
  background: linear-gradient(90deg, transparent, rgba(0, 229, 255, 0.55), transparent);
  opacity: 0;
  transition: opacity 220ms ease;
}

.feature-card:hover {
  transform: translateY(-6px);
}

.feature-card:hover::before {
  opacity: 0.9;
}

.feature-card:hover::after {
  opacity: 1;
}

.feature-icon {
  width: 44px;
  height: 44px;
  margin-bottom: 20px;
  display: grid;
  place-items: center;
  border-radius: 16px;
  color: #fff;
  box-shadow: 0 0 50px rgba(0, 229, 255, 0.22);
}

.feature-icon.primary {
  background: linear-gradient(135deg, rgba(79, 125, 255, 0.9), rgba(0, 229, 255, 0.8));
}

.feature-icon.cyan {
  background: linear-gradient(135deg, rgba(0, 229, 255, 0.85), rgba(52, 211, 153, 0.7));
}

.feature-icon.violet {
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.9), rgba(232, 121, 249, 0.75));
}

.feature-card h3 {
  margin: 0;
  color: #fff;
  font-size: 18px;
  font-weight: 750;
}

.feature-card p {
  margin: 8px 0 0;
  color: rgba(255, 255, 255, 0.58);
  font-size: 14px;
  line-height: 1.7;
}

.orb-stage {
  display: flex;
  justify-content: flex-end;
  animation: scaleIn 1s 100ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

.hero-orb {
  position: relative;
  width: min(42vw, 720px);
  min-width: 520px;
  aspect-ratio: 1;
  border-radius: 999px;
  transform-style: preserve-3d;
  transition: transform 180ms ease-out;
}

.hero-orb::before {
  position: absolute;
  inset: 10%;
  content: "";
  border-radius: inherit;
  background:
    radial-gradient(circle at 50% 50%, rgba(0, 229, 255, 0.28), transparent 56%),
    radial-gradient(circle at 46% 42%, rgba(139, 92, 246, 0.24), transparent 62%);
  filter: blur(34px);
  opacity: 0.92;
  transform: translateZ(-1px);
}

.orbital-system {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  border-radius: inherit;
  overflow: visible;
}

.orbital-system :deep(canvas) {
  width: 100% !important;
  height: 100% !important;
  display: block;
  filter: drop-shadow(0 0 42px rgba(0, 229, 255, 0.32));
}

.hero-orb.is-active .orbital-system :deep(canvas) {
  filter:
    drop-shadow(0 0 52px rgba(0, 229, 255, 0.5))
    drop-shadow(0 0 86px rgba(139, 92, 246, 0.32));
}

.hero-orb.is-active .ring-a {
  animation-duration: 8s;
}

.hero-orb.is-active .ring-b {
  animation-duration: 11s;
}

.hero-orb.is-active .halo-a {
  animation-duration: 3.8s;
}

.hero-orb.is-active .halo-b {
  animation-duration: 5.2s;
}

.hero-orb.is-active .halo-c {
  animation-duration: 3.2s;
}

.orb-ring {
  position: absolute;
  border: 1px solid rgba(0, 229, 255, 0.32);
  border-radius: inherit;
  box-shadow: 0 0 46px rgba(79, 125, 255, 0.18);
}

.ring-a {
  inset: 3%;
  animation: spin 30s linear infinite;
}

.ring-b {
  inset: 12%;
  border-color: rgba(139, 92, 246, 0.35);
  animation: spinReverse 42s linear infinite;
}

.orb-core {
  position: absolute;
  inset: 17%;
  overflow: hidden;
  border-radius: inherit;
  background:
    radial-gradient(circle at 30% 24%, rgba(255, 255, 255, 0.98), rgba(0, 229, 255, 0.56) 12%, transparent 24%),
    radial-gradient(circle at 62% 68%, rgba(232, 121, 249, 0.35), transparent 24%),
    radial-gradient(circle at 50% 50%, rgba(0, 229, 255, 0.28), rgba(79, 125, 255, 0.18) 42%, rgba(139, 92, 246, 0.16) 61%, rgba(5, 8, 22, 0.2) 74%);
  box-shadow:
    inset 0 0 48px rgba(255, 255, 255, 0.08),
    inset 0 -34px 70px rgba(5, 8, 22, 0.34),
    0 0 150px rgba(79, 125, 255, 0.58),
    0 0 240px rgba(0, 229, 255, 0.24);
  backface-visibility: visible;
  transform-style: preserve-3d;
  will-change: transform, filter;
  animation: coreGlow 8s ease-in-out infinite;
}

.orb-core::before,
.orb-core::after {
  position: absolute;
  content: "";
  border-radius: inherit;
  pointer-events: none;
  transform-style: preserve-3d;
}

.orb-core::before {
  inset: -18%;
  opacity: 0.72;
  background:
    radial-gradient(ellipse at 28% 30%, rgba(255, 255, 255, 0.36), transparent 21%),
    conic-gradient(from 20deg, rgba(0, 229, 255, 0), rgba(0, 229, 255, 0.42), rgba(139, 92, 246, 0.24), rgba(79, 125, 255, 0), rgba(0, 229, 255, 0.42), rgba(0, 229, 255, 0)),
    repeating-linear-gradient(92deg, transparent 0 24px, rgba(255, 255, 255, 0.13) 25px, transparent 27px);
  filter: blur(0.2px);
  mix-blend-mode: screen;
  animation: surfaceFlow 16s linear infinite;
}

.orb-core::after {
  inset: 5%;
  opacity: 0.62;
  background:
    radial-gradient(ellipse at 30% 22%, rgba(255, 255, 255, 0.44), transparent 18%),
    repeating-radial-gradient(ellipse at center, transparent 0 26px, rgba(255, 255, 255, 0.12) 28px, transparent 31px),
    repeating-linear-gradient(90deg, transparent 0 35px, rgba(0, 229, 255, 0.16) 37px, transparent 40px);
  mask-image: radial-gradient(circle at center, black 0 64%, transparent 72%);
  mix-blend-mode: screen;
  animation: latitudeRoll 12s linear infinite reverse;
}

.hero-orb.is-active .orb-core {
  animation: coreGlow 2.4s ease-in-out infinite, orb3dTumble 5.8s linear infinite;
}

.hero-orb.is-active .orb-core::before {
  animation-duration: 5.4s;
}

.hero-orb.is-active .orb-core::after {
  animation-duration: 4.2s;
}

.orb-glass {
  position: absolute;
  inset: 19%;
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: inherit;
  background: rgba(255, 255, 255, 0.035);
  backdrop-filter: blur(12px);
}

.hero-orb.is-active .orb-glass {
  animation: glassSpin 12s linear infinite;
}

.orb-pulse {
  position: absolute;
  inset: 30%;
  border-radius: inherit;
  background: rgba(0, 229, 255, 0.2);
  filter: blur(28px);
  animation: pulseOrb 4.8s ease-in-out infinite;
}

.hero-orb.is-active .orb-pulse {
  animation-duration: 2.4s;
}

.orb-halo {
  position: absolute;
  border-radius: 999px;
  border: 1px solid transparent;
  transform-style: preserve-3d;
  mix-blend-mode: screen;
}

.halo-a {
  inset: 9%;
  border-top-color: rgba(255, 255, 255, 0.42);
  border-right-color: rgba(0, 229, 255, 0.78);
  border-bottom-color: rgba(79, 125, 255, 0.22);
  box-shadow: 0 0 38px rgba(0, 229, 255, 0.26);
  transform: rotateX(68deg) rotateZ(-18deg);
  animation: haloSpinA 9s linear infinite;
}

.halo-b {
  inset: 16%;
  border-left-color: rgba(139, 92, 246, 0.62);
  border-bottom-color: rgba(0, 229, 255, 0.62);
  box-shadow: 0 0 34px rgba(139, 92, 246, 0.24);
  transform: rotateY(64deg) rotateZ(28deg);
  animation: haloSpinB 13s linear infinite;
}

.halo-c {
  inset: 24%;
  border-top-color: rgba(255, 255, 255, 0.26);
  border-left-color: rgba(0, 229, 255, 0.56);
  transform: rotateX(74deg) rotateZ(82deg);
  animation: haloSpinC 7s linear infinite reverse;
}

.orb-scan {
  position: absolute;
  left: 18%;
  right: 18%;
  top: 14%;
  height: 72%;
  overflow: hidden;
  border-radius: 999px;
  mask-image: radial-gradient(ellipse at center, black 0 58%, transparent 70%);
  transform: rotate(-14deg);
}

.hero-orb.is-active .orb-scan {
  animation: scanDiscSpin 7s linear infinite;
}

.orb-scan::before {
  position: absolute;
  inset: -40% 0 auto;
  height: 38%;
  content: "";
  background: linear-gradient(180deg, transparent, rgba(255, 255, 255, 0.36), rgba(0, 229, 255, 0.44), transparent);
  filter: blur(2px);
  animation: scanSweep 3.7s ease-in-out infinite;
}

.hero-orb.is-active .orb-scan::before {
  animation-duration: 1.9s;
}

.orb-hotspot {
  position: absolute;
  inset: 42%;
  border-radius: 999px;
  background: #ffffff;
  box-shadow:
    0 0 22px rgba(255, 255, 255, 0.95),
    0 0 58px rgba(0, 229, 255, 0.9),
    0 0 110px rgba(139, 92, 246, 0.55);
  animation: hotspotPulse 2.4s ease-in-out infinite;
}

.hero-orb.is-active .orb-hotspot {
  animation-duration: 1.15s;
}

.satellite {
  position: absolute;
  width: 10px;
  height: 10px;
  border-radius: 999px;
  background: #fff;
  box-shadow: 0 0 20px rgba(0, 229, 255, 0.95), 0 0 48px rgba(79, 125, 255, 0.5);
}

.satellite-a {
  left: 17%;
  top: 29%;
  animation: satellitePulse 2.8s ease-in-out infinite;
}

.satellite-b {
  right: 20%;
  top: 19%;
  background: #00e5ff;
  animation: satellitePulse 3.2s 0.45s ease-in-out infinite;
}

.satellite-c {
  right: 12%;
  bottom: 32%;
  background: #c7b4ff;
  animation: satellitePulse 2.6s 0.9s ease-in-out infinite;
}

.satellite-d {
  left: 27%;
  bottom: 17%;
  width: 7px;
  height: 7px;
  animation: satellitePulse 3.6s 1.2s ease-in-out infinite;
}

.code-rain {
  position: absolute;
  left: 8%;
  top: 18%;
  width: 84%;
  height: 64%;
  border-radius: inherit;
  opacity: 0.5;
  filter: blur(0.5px);
  background: repeating-linear-gradient(to bottom, rgba(0, 229, 255, 0) 0, rgba(0, 229, 255, 0) 20px, rgba(0, 229, 255, 0.18) 21px, rgba(0, 229, 255, 0) 22px);
  transform: rotate(-12deg);
}

.hero-orb.is-active .code-rain {
  animation: codeRainSpin 10s linear infinite;
}

.orbit-dot {
  position: absolute;
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #00e5ff;
  box-shadow: 0 0 16px rgba(0, 229, 255, 0.9);
  animation: dotPulse 3s ease-in-out infinite;
}

.beam {
  position: absolute;
  height: 1px;
  background: linear-gradient(90deg, transparent, rgba(0, 229, 255, 0.8), transparent);
}

.beam-a {
  left: 12%;
  top: 24%;
  width: 76%;
  transform: rotate(12deg);
}

.beam-b {
  left: 17%;
  top: 66%;
  width: 66%;
  background: linear-gradient(90deg, transparent, rgba(139, 92, 246, 0.8), transparent);
  transform: rotate(-12deg);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

@keyframes spinReverse {
  to {
    transform: rotate(-360deg);
  }
}

@keyframes breathe {
  50% {
    opacity: 0.82;
  }
}

@keyframes riseIn {
  from {
    opacity: 0;
    transform: translateY(36px);
  }
}

@keyframes scaleIn {
  from {
    opacity: 0;
    transform: scale(0.92);
  }
}

@keyframes cardIn {
  from {
    opacity: 0;
    transform: translateY(18px);
  }
}

@keyframes pulseOrb {
  50% {
    opacity: 0.86;
    transform: scale(1.04);
  }
}

@keyframes coreGlow {
  50% {
    filter: saturate(1.28) brightness(1.13);
  }
}

@keyframes surfaceFlow {
  to {
    transform: translateX(-18%) rotate(360deg);
  }
}

@keyframes latitudeRoll {
  to {
    transform: rotateY(360deg) rotateZ(16deg);
  }
}

@keyframes orb3dTumble {
  0% {
    transform: rotateX(0deg) rotateY(0deg) rotateZ(0deg) scale(1);
  }

  24% {
    transform: rotateX(18deg) rotateY(92deg) rotateZ(8deg) scale(1.02);
  }

  50% {
    transform: rotateX(-12deg) rotateY(184deg) rotateZ(-7deg) scale(1.035);
  }

  76% {
    transform: rotateX(16deg) rotateY(278deg) rotateZ(10deg) scale(1.02);
  }

  100% {
    transform: rotateX(0deg) rotateY(360deg) rotateZ(0deg) scale(1);
  }
}

@keyframes glassSpin {
  to {
    transform: rotate(-360deg);
  }
}

@keyframes scanDiscSpin {
  to {
    transform: rotate(346deg);
  }
}

@keyframes codeRainSpin {
  to {
    transform: rotate(348deg);
  }
}

@keyframes haloSpinA {
  to {
    transform: rotateX(68deg) rotateZ(342deg);
  }
}

@keyframes haloSpinB {
  to {
    transform: rotateY(64deg) rotateZ(388deg);
  }
}

@keyframes haloSpinC {
  to {
    transform: rotateX(74deg) rotateZ(442deg);
  }
}

@keyframes scanSweep {
  0%,
  14% {
    transform: translateY(0);
    opacity: 0;
  }

  38%,
  72% {
    opacity: 0.88;
  }

  100% {
    transform: translateY(320%);
    opacity: 0;
  }
}

@keyframes hotspotPulse {
  50% {
    transform: scale(1.55);
    opacity: 0.58;
  }
}

@keyframes satellitePulse {
  50% {
    transform: scale(1.8);
    opacity: 0.45;
  }
}

@keyframes dotPulse {
  50% {
    opacity: 1;
    transform: scale(1.2);
  }

  0%,
  100% {
    opacity: 0.25;
    transform: scale(0.8);
  }
}

@keyframes particleDrift {
  to {
    transform: translate3d(18px, -24px, 0);
    opacity: 0.35;
  }
}

@media (max-width: 1180px) {
  .hero {
    grid-template-columns: 1fr;
    align-content: center;
    gap: 20px;
  }

  .headline-wrap {
    transform: translateY(-60px);
  }

  .orb-stage {
    display: none;
  }
}

@media (max-width: 760px) {
  .index-nav {
    padding: 0 18px;
  }

  .console-button {
    min-height: 46px;
    padding: 0 18px;
  }

  .index-actions {
    gap: 8px;
  }

  .index-doc-button {
    min-height: 42px;
    padding: 0 14px;
  }

  .hero {
    padding: 92px 18px 28px;
  }

  .headline-wrap {
    transform: none;
  }

  .headline-wrap h1,
  .headline-wrap h2 {
    white-space: normal;
  }

  .headline-wrap h1 {
    font-size: clamp(3rem, 13vw, 4.2rem);
  }

  .headline-wrap h2 {
    margin-top: 28px;
    font-size: clamp(1.35rem, 6vw, 2rem);
  }

  .feature-grid {
    grid-template-columns: 1fr;
  }
}
</style>
