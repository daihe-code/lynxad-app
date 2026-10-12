<template>
  <div class="modal" role="dialog" aria-modal="true" :aria-labelledby="titleId" @keydown="onKey">
    <div class="modal__mask" @click="close" />
    <div ref="box" class="modal__box" :class="small ? 'modal__box--sm' : expanded ? 'modal__box--detail' : 'modal__box--md'" tabindex="-1">
      <div class="modal__head"><h2 :id="titleId" class="modal__title">{{ title }}</h2><button class="modal__close" type="button" aria-label="关闭" :disabled="busy" @click="close"><AccountIcon name="close" /></button></div>
      <p v-if="description" class="modal__desc">{{ description }}</p>
      <div v-if="$slots.default" class="mt-[24px]" :class="{ 'modal__body--scroll': expanded }"><slot /></div>
      <div class="modal__foot"><slot name="footer"><button class="btn-modal-ghost" :disabled="busy" @click="close">取消</button></slot></div>
    </div>
  </div>
</template>
<script setup lang="ts">
import { onMounted, onUnmounted, ref, useId } from 'vue';
import AccountIcon from './AccountIcon.vue';
const props = defineProps<{
    title: string;
    description?: string;
    small?: boolean;
    expanded?: boolean;
    busy?: boolean;
}>();
const emit = defineEmits<{
    close: [
    ];
}>();
const titleId = useId(), box = ref<HTMLElement>();
let overflow = '', previous: HTMLElement | null = null;
function close() { if (!props.busy)
    emit('close'); }
function onKey(e: KeyboardEvent) {
    if (e.key === 'Escape') {
        e.preventDefault();
        close();
    }
    if (e.key !== 'Tab')
        return;
    const items = Array.from(box.value?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), a[href], [tabindex="0"]') || []).filter(el => el.getClientRects().length);
    const first = items[0], last = items.at(-1);
    if (!first || !last) {
        e.preventDefault();
        return;
    }
    if (e.shiftKey && (document.activeElement === first || document.activeElement === box.value)) {
        e.preventDefault();
        last.focus();
    }
    else if (!e.shiftKey && (document.activeElement === last || document.activeElement === box.value)) {
        e.preventDefault();
        first.focus();
    }
}
onMounted(() => { previous = document.activeElement as HTMLElement; overflow = document.body.style.overflow; document.body.style.overflow = 'hidden'; box.value?.focus(); });
onUnmounted(() => { document.body.style.overflow = overflow; if (previous?.isConnected)
    previous.focus(); });
</script>
<style scoped>
.modal__box.modal__box--detail{max-width:760px;max-height:calc(100dvh - 48px);display:flex;flex-direction:column;padding:24px 28px}.modal__box--detail .modal__head,.modal__box--detail .modal__foot{flex-shrink:0}.modal__body--scroll{min-height:0;overflow-y:auto;overscroll-behavior:contain;padding-right:8px}@media(max-width:639px){.modal__box.modal__box--detail{padding:20px;max-height:calc(100dvh - 48px)}}
</style>
