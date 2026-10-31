<template>
  <div
    class="reference-modal"
    role="dialog"
    aria-modal="true"
    :aria-labelledby="titleId"
  >
    <div class="reference-modal__mask" @click="close" />
    <section
      ref="box"
      class="reference-modal__box"
      :style="{ maxWidth: `${width || 680}px` }"
      tabindex="-1"
    >
      <header>
        <h2 :id="titleId">{{ title }}</h2>
        <slot name="header-extra" />
        <button v-if="showClose !== false" aria-label="关闭弹窗" :disabled="busy" @click="close">
          <ReferenceIcon name="close" />
        </button>
      </header>
      <div class="reference-modal__body"><slot /></div>
      <footer>
        <slot name="footer"
          ><button class="btn-prev" :disabled="busy" @click="close">
            取消
          </button></slot
        >
      </footer>
    </section>
  </div>
</template>
<script setup lang="ts">
import { ref, useId, onMounted, onBeforeUnmount } from "vue";
import { lockBodyScroll } from "@/utils/bodyScrollLock";
import ReferenceIcon from "./ReferenceIcon.vue";
const props = withDefaults(defineProps<{ title: string; width?: number; busy?: boolean; showClose?: boolean }>(), { showClose: true });
const emit = defineEmits<{ close: [] }>();
const titleId = useId(),
  box = ref<HTMLElement>();
let previous: HTMLElement | null = null;
let releaseScroll: (() => void) | undefined;
function close() {
  if (!props.busy) emit("close");
}
function onKey(e: KeyboardEvent) {
  if (e.key === "Escape") {
    e.preventDefault();
    close();
  }
  if (e.key !== "Tab") return;
  const els = Array.from(
    box.value?.querySelectorAll<HTMLElement>(
      'button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href],[tabindex="0"]',
    ) || [],
  ).filter((e) => e.getClientRects().length);
  const first = els[0],
    last = els.at(-1);
  if (!first || !last) {
    e.preventDefault();
    return;
  }
  if (
    e.shiftKey &&
    (document.activeElement === first || document.activeElement === box.value)
  ) {
    e.preventDefault();
    last.focus();
  } else if (
    !e.shiftKey &&
    (document.activeElement === last || document.activeElement === box.value)
  ) {
    e.preventDefault();
    first.focus();
  }
}
onMounted(() => {
  document.addEventListener("keydown", onKey);
  previous = document.activeElement as HTMLElement;
  releaseScroll = lockBodyScroll();
  box.value?.focus();
});
onBeforeUnmount(() => {
  document.removeEventListener("keydown", onKey);
  releaseScroll?.();
  if (previous?.isConnected) previous.focus();
});
</script>
<style scoped>
.reference-modal {
  position: fixed;
  inset: 0;
  z-index: 2100;
  display: grid;
  place-items: center;
  padding: 24px;
}
.reference-modal__mask {
  position: absolute;
  inset: 0;
  background: rgba(26, 27, 46, 0.38);
  backdrop-filter: blur(2px);
}
.reference-modal__box {
  position: relative;
  width: 100%;
  max-height: calc(100dvh - 48px);
  overflow: auto;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 24px 60px #1a1b2e33;
  padding: 24px;
  color: #1a1b2e;
  font-size: 14px;
}
.reference-modal__box header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.reference-modal__box h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
.reference-modal__box header button {
  border: 0;
  background: none;
  color: #7c7f99;
  padding: 4px;
  cursor: pointer;
}
.reference-modal__box header svg {
  width: 20px;
  height: 20px;
}
.reference-modal__body {
  margin: 24px 0;
}
.reference-modal__box footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}
.reference-modal__box :deep(input),
.reference-modal__box :deep(select),
.reference-modal__box :deep(textarea) {
  max-width: 100%;
}
</style>
