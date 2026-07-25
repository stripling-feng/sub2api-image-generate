<script setup lang="ts">
import { onMounted, onUnmounted, ref } from "vue";
import { ArrowRight, BadgeDollarSign, BookOpenText, PlugZap, ShieldCheck } from "lucide-vue-next";
import * as THREE from "three";

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

function initThreeOrb() {
  const mount = orbMount.value;
  if (!mount) return;

  const mobile = window.matchMedia("(max-width: 767px)").matches;
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  const nodeCount = mobile ? 14 : 22;
  const scene = new THREE.Scene();
  const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 50);
  camera.position.z = 6;

  const renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true });
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, mobile ? 1.2 : 1.6));
  renderer.setClearColor(0x000000, 0);
  renderer.domElement.className = "creative-constellation-canvas";
  mount.appendChild(renderer.domElement);

  const root = new THREE.Group();
  root.position.set(0.45, 0.05, 0);
  root.rotation.z = -0.12;
  scene.add(root);

  const positions = new Float32Array(nodeCount * 3);
  const colors = new Float32Array(nodeCount * 3);
  const palette = [new THREE.Color("#d9e8ff"), new THREE.Color("#6fcaff"), new THREE.Color("#aa8cff")];

  for (let index = 0; index < nodeCount; index += 1) {
    const angle = index * 0.74;
    const radius = 0.68 + index * 0.075;
    positions[index * 3] = Math.cos(angle) * radius * 1.18;
    positions[index * 3 + 1] = Math.sin(angle) * radius * 0.72;
    positions[index * 3 + 2] = Math.sin(index * 1.7) * 0.34;
    const color = palette[index % palette.length];
    colors[index * 3] = color.r;
    colors[index * 3 + 1] = color.g;
    colors[index * 3 + 2] = color.b;
  }

  const nodeGeometry = new THREE.BufferGeometry();
  nodeGeometry.setAttribute("position", new THREE.BufferAttribute(positions, 3));
  nodeGeometry.setAttribute("color", new THREE.BufferAttribute(colors, 3));
  const nodeMaterial = new THREE.PointsMaterial({
    size: mobile ? 0.075 : 0.085,
    vertexColors: true,
    transparent: true,
    opacity: 0.82,
    blending: THREE.AdditiveBlending,
    depthWrite: false
  });
  root.add(new THREE.Points(nodeGeometry, nodeMaterial));

  const linePositions: number[] = [];
  const connect = (from: number, to: number) => {
    for (const index of [from, to]) {
      linePositions.push(positions[index * 3], positions[index * 3 + 1], positions[index * 3 + 2]);
    }
  };
  for (let index = 0; index < nodeCount - 1; index += 1) {
    connect(index, index + 1);
    if (index % 4 === 0 && index + 3 < nodeCount) connect(index, index + 3);
  }

  const lineGeometry = new THREE.BufferGeometry();
  lineGeometry.setAttribute("position", new THREE.Float32BufferAttribute(linePositions, 3));
  const lineMaterial = new THREE.LineBasicMaterial({
    color: 0x79bfff,
    transparent: true,
    opacity: 0.2,
    blending: THREE.AdditiveBlending,
    depthWrite: false
  });
  root.add(new THREE.LineSegments(lineGeometry, lineMaterial));

  const dustCount = mobile ? 28 : 54;
  const dustPositions = new Float32Array(dustCount * 3);
  for (let index = 0; index < dustCount; index += 1) {
    dustPositions[index * 3] = Math.sin(index * 12.9898) * 2.9;
    dustPositions[index * 3 + 1] = Math.sin(index * 7.233 + 1.4) * 2.05;
    dustPositions[index * 3 + 2] = Math.cos(index * 4.771) * 1.2;
  }
  const dustGeometry = new THREE.BufferGeometry();
  dustGeometry.setAttribute("position", new THREE.BufferAttribute(dustPositions, 3));
  const dustMaterial = new THREE.PointsMaterial({
    color: 0x9fcfff,
    size: 0.025,
    transparent: true,
    opacity: 0.32,
    depthWrite: false
  });
  const dust = new THREE.Points(dustGeometry, dustMaterial);
  root.add(dust);

  const resize = () => {
    const rect = mount.getBoundingClientRect();
    const width = Math.max(300, Math.floor(rect.width || 620));
    const height = Math.max(240, Math.floor(rect.height || 520));
    renderer.setSize(width, height, false);
    camera.aspect = width / height;
    camera.updateProjectionMatrix();
  };
  resize();
  window.addEventListener("resize", resize);

  let frame = 0;
  let lastTime = performance.now();
  let elapsed = 0;
  const motionScale = reducedMotion ? 0.2 : 1;
  const animate = () => {
    const now = performance.now();
    const delta = Math.min((now - lastTime) / 1000, 0.033);
    lastTime = now;
    elapsed += delta;
    frame = window.requestAnimationFrame(animate);
    root.rotation.y += delta * 0.055 * motionScale;
    root.rotation.x = Math.sin(elapsed * 0.3) * 0.04;
    dust.rotation.z -= delta * 0.018 * motionScale;
    nodeMaterial.opacity = 0.72 + Math.sin(elapsed * 1.1) * 0.1;
    lineMaterial.opacity = 0.16 + Math.sin(elapsed * 0.75) * 0.05;
    renderer.render(scene, camera);
  };
  animate();

  cleanupOrb = () => {
    window.cancelAnimationFrame(frame);
    window.removeEventListener("resize", resize);
    nodeGeometry.dispose();
    nodeMaterial.dispose();
    lineGeometry.dispose();
    lineMaterial.dispose();
    dustGeometry.dispose();
    dustMaterial.dispose();
    renderer.dispose();
    renderer.domElement.remove();
  };
}

function handleOrbMove(event: MouseEvent) {
  isOrbActive.value = true;
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
  const mouseX = (event.clientX - rect.left) / rect.width - 0.5;
  const mouseY = (event.clientY - rect.top) / rect.height - 0.5;
  orbTransform.value = `rotateX(${(-mouseY * 8).toFixed(2)}deg) rotateY(${(mouseX * 12).toFixed(2)}deg)`;
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

function navigateParent(path: "/dashboard" | "/index" | "/docs") {
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
          <button class="index-doc-button" type="button" @click="navigateParent('/docs')">
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
  position: relative;
  display: flex;
  justify-content: flex-end;
  min-width: 0;
  animation: scaleIn 1s 100ms cubic-bezier(0.22, 1, 0.36, 1) both;
}

.hero-orb {
  position: relative;
  width: min(44vw, 700px);
  min-width: 560px;
  aspect-ratio: 1.15;
  margin-right: clamp(-132px, -6vw, -60px);
  transform-style: preserve-3d;
  transition: transform 240ms ease-out;
}

.orbital-system {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  overflow: visible;
}

.orbital-system :deep(canvas) {
  position: absolute;
  inset: 0;
  width: 100% !important;
  height: 100% !important;
  display: block;
  opacity: 0.7;
  filter: drop-shadow(0 0 24px rgba(86, 171, 255, 0.2));
  transition: opacity 240ms ease, filter 240ms ease;
}

.hero-orb.is-active .orbital-system :deep(canvas) {
  opacity: 0.86;
  filter: drop-shadow(0 0 30px rgba(119, 190, 255, 0.3));
}

@keyframes spin {
  to {
    transform: rotate(360deg);
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


@keyframes particleDrift {
  to {
    transform: translate3d(18px, -24px, 0);
    opacity: 0.35;
  }
}

@media (max-height: 800px) and (min-width: 1181px) {
  .headline-wrap {
    transform: translateY(-72px);
  }

  .headline-wrap h2 {
    margin-top: 30px;
  }

  .feature-grid {
    margin-top: 34px;
  }
}

@media (max-width: 1180px) {
  .index-page {
    height: 100dvh;
    overflow-x: hidden;
    overflow-y: auto;
  }

  .hero {
    grid-template-columns: 1fr;
    align-content: center;
    gap: 20px;
  }

  .headline-wrap {
    transform: translateY(-60px);
  }

  .orb-stage {
    min-height: 300px;
    margin-top: -42px;
    justify-content: flex-end;
  }

  .hero-orb {
    width: min(70vw, 620px);
    min-width: 490px;
    margin-right: -96px;
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

  .hero-copy {
    display: contents;
  }

  .headline-wrap {
    order: 1;
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
    order: 3;
    margin-top: 0;
    grid-template-columns: 1fr;
  }

  .orb-stage {
    order: 2;
    min-height: 260px;
    margin-top: -10px;
  }

  .hero-orb {
    width: 390px;
    min-width: 390px;
    margin-right: -102px;
  }
}
</style>
