<template>
  <div class="remake-file">
    <input ref="input" hidden type="file" :accept="accept" :disabled="disabled || approvalRequired" @change="choose">
    <div v-if="!file" class="remake-file-drop" @dragover.prevent @drop.prevent="drop">
      <span class="remake-file-icon"><AssetIcon :name="kind === 'video' ? 'video' : 'image'" /></span>
      <div><strong>{{ label }}</strong><small>{{ formats }}</small></div>
      <el-button type="primary" plain :disabled="disabled" @click="openPicker">选择{{ kind === 'video' ? '视频' : '图片' }}</el-button>
    </div>
    <div v-else class="remake-file-picked">
      <video v-if="kind === 'video' && url && !previewError" :src="url" controls playsinline preload="metadata"
        :aria-label="label" @loadedmetadata="readVideo" @error="failVideoPreview" />
      <img v-if="kind === 'image' && url && !previewError" :src="url" :alt="label" @error="previewError = true">
      <AssetIcon v-if="previewError" class="remake-file-icon" :name="kind" />
      <div class="remake-file-meta">
        <strong :title="file.name">{{ file.name }}</strong>
        <small>{{ fileSize(file.size) }}<template v-if="videoDuration != null"> · {{ duration(videoDuration) }}</template></small>
        <small v-if="previewError && kind === 'image'">当前浏览器无法预览，提交时会校验文件。</small>
        <div class="remake-file-actions">
          <el-button link type="primary" :disabled="disabled" @click="openPicker">更换</el-button>
          <el-button link :disabled="disabled" @click="remove">移除</el-button>
        </div>
      </div>
    </div>
    <p v-if="error" class="remake-error" role="alert">{{ error }}</p>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import AssetIcon from './AssetIcon.vue';
import { duration, fileSize } from './assetFormat';

const props = defineProps<{ kind: 'video' | 'image'; label: string; file?: File; disabled?: boolean; approvalRequired?: boolean }>();
const emit = defineEmits<{
  change: [file?: File];
  blocked: [];
  metadata: [value: { duration: number; width: number; height: number } | null];
}>();
const input = ref<HTMLInputElement>();
const error = ref(''), url = ref(''), previewError = ref(false), videoDuration = ref<number>();
const accept = computed(() => props.kind === 'video' ? '.mp4,.mov,.webm' : '.jpg,.jpeg,.png,.webp');
const formats = computed(() => props.kind === 'video' ? 'MP4 / MOV / WebM · 4–120 秒 · 最大 200 MB' : 'JPG / PNG / WebP · 最大 10 MB');

watch(() => props.file, (file, _, onCleanup) => {
  previewError.value = false;
  videoDuration.value = undefined;
  url.value = file ? URL.createObjectURL(file) : '';
  const objectUrl = url.value;
  if (objectUrl) onCleanup(() => URL.revokeObjectURL(objectUrl));
}, { immediate: true });

function canChoose() {
  if (props.disabled) return false;
  if (!props.approvalRequired) return true;
  error.value = '';
  emit('blocked');
  return false;
}
function openPicker() {
  if (canChoose()) input.value?.click();
}
function setFile(file?: File) {
  if (!file) return;
  const extension = file.name.slice(file.name.lastIndexOf('.')).toLowerCase();
  const limit = props.kind === 'video' ? 200 : 10;
  if (!accept.value.split(',').includes(extension)) {
    error.value = '请选择支持的文件格式。';
  } else if (!file.size || file.size > limit * 1024 * 1024) {
    error.value = file.size ? '文件大小不得超过 ' + limit + ' MB。' : '文件为空，请重新选择。';
  } else {
    error.value = '';
    emit('change', file);
  }
}
function choose(event: Event) {
  const picker = event.target as HTMLInputElement;
  if (picker.files?.length && canChoose()) setFile(picker.files[0]);
  picker.value = '';
}
function drop(event: DragEvent) {
  if (!canChoose()) return;
  const files = event.dataTransfer?.files;
  if (files && files.length > 1) {
    error.value = '请一次选择一个文件。';
    return;
  }
  setFile(files?.[0]);
}
function remove() {
  error.value = '';
  emit('change', undefined);
}
function readVideo(event: Event) {
  const { duration, videoWidth: width, videoHeight: height } = event.target as HTMLVideoElement;
  if (!Number.isFinite(duration) || !width || !height) { failVideoPreview(); return; }
  videoDuration.value = duration;
  emit('metadata', { duration, width, height });
}
function failVideoPreview() {
  previewError.value = true;
  if (videoDuration.value === undefined) emit('metadata', null);
}
</script>
