<template>
  <ReferenceModal class="add-title-dialog" :title="`新增${label}`" :width="560" :busy="locked" @close="close">
    <div class="title-source" role="group" aria-label="标题添加方式"><button v-for="(name,key) in sources" :key="key" type="button" :aria-pressed="source===key" :disabled="locked" @click="source=key;error=''">{{ name }}</button></div>
    <div v-if="source==='MANUAL'" class="title-entry"><label for="new-product-title">{{ label }}</label><div class="title-input"><textarea id="new-product-title" v-model="text" :aria-label="`新增${label}内容`" :maxlength="limit" :disabled="locked" :placeholder="`请输入${label}，最多${limit}字`" @input="error=''" /><span>{{ text.length }}/{{ limit }}</span></div><p v-if="validation" class="title-error" role="alert">{{ validation }}</p></div>
    <div v-else class="title-ai"><ReferenceIcon name="sparkle" /><span>根据当前商品信息，生成并新增 1 条{{ label }}。</span></div>
    <p v-if="error" class="title-error" role="alert">{{ error }}</p>
    <p v-if="waiting" class="title-status" role="status"><span class="title-spinner" />{{ generation?.message || '正在生成新增标题…' }}</p>
    <template #footer><button type="button" class="secondary" :disabled="locked" @click="close">取消</button><button type="button" class="primary" :disabled="locked || source==='MANUAL' && (!text.trim() || !!validation)" @click="submit">{{ submitting || waiting ? source==='AI'?'生成中…':'添加中…' : source==='AI'?'生成并添加':`添加${label}` }}</button></template>
  </ReferenceModal>
</template>
<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import ReferenceModal from '../reference/ReferenceModal.vue';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
import { titleLimit, type TitleKind } from '@/features/delivery/productTitles';
import type { TitleGeneration } from '@/features/delivery/creationApi';
import type { TitleSource, TitleAdditionReceipt } from '@/features/delivery/titleAddition';
const props=defineProps<{kind:TitleKind;busy:boolean;generation?:TitleGeneration;validate:(text?:string)=>string}>();
const emit=defineEmits<{close:[];add:[source:TitleSource,text:string,receipt:TitleAdditionReceipt]}>();
const sources:Record<TitleSource,string>={MANUAL:'手动输入',AI:'智能生成'};
const source=ref<TitleSource>('MANUAL'),text=ref(''),error=ref(''),submitting=ref(false),pending=ref<TitleGeneration>();
const requestId=ref('');
let alive=true;
const label=computed(()=>props.kind==='CARD'?'商品卡标题':'标题'),limit=computed(()=>titleLimit(props.kind));
const validation=computed(()=>source.value==='MANUAL'&&text.value.trim()?props.validate(text.value):'');
const waiting=computed(()=>!!pending.value&&['SENDING','PROCESSING'].includes(pending.value.status));
const locked=computed(()=>props.busy||submitting.value||waiting.value);
function accept(generation?:TitleGeneration) {
  if(!generation){emit('close');return;}
  pending.value=generation;
  if(generation.status==='SUCCEEDED')emit('close');
  else if(generation.status==='FAILED')error.value=generation.message||'标题生成失败，请重试';
}
function close(){if(!locked.value)emit('close');}
function submit(){
  if(locked.value)return;
  error.value=props.validate(source.value==='MANUAL'?text.value:undefined);if(error.value)return;
  submitting.value=true;
  if(source.value==='AI'&&(!requestId.value||pending.value?.status==='FAILED'))requestId.value=crypto.randomUUID();
  emit('add',source.value,text.value.trim(),{requestId:source.value==='AI'?requestId.value:undefined,done(message,generation){if(!alive)return;submitting.value=false;if(message){error.value=message;return;}accept(generation);}});
}
watch(()=>props.generation,generation=>{if(generation?.requestId&&generation.requestId===requestId.value)accept(generation);});
onUnmounted(()=>{alive=false;});
</script>
<style scoped>
.add-title-dialog :deep(.reference-modal__body){margin:24px 0}.title-source{display:flex;gap:4px;padding:4px;border-radius:9px;background:#f1efff;margin-bottom:22px}.title-source button{flex:1;padding:10px;border:1px solid transparent;border-radius:7px;background:transparent;color:#78749c;font-size:14px}.title-source button[aria-pressed=true]{border-color:#6455ff;background:#fff;color:#5543ef}.title-entry>label{font-size:14px;color:#303149}.title-input{position:relative;margin-top:9px}.title-input textarea{box-sizing:border-box;width:100%;min-height:105px;padding:12px 14px 32px;border:1px solid #dcd8f5;border-radius:8px;background:#fff;color:#34364d;font:inherit;font-size:14px;resize:vertical}.title-input span{position:absolute;right:13px;bottom:12px;color:#9292aa;font-size:12px;pointer-events:none}.title-ai{display:flex;align-items:center;gap:14px;min-height:90px;padding:18px;background:#f6f4ff;border-radius:10px;color:#5b508a;font-size:14px;line-height:1.7}.title-ai svg{color:#6255ff;width:28px;height:28px;flex-shrink:0}.title-error{color:#d54650;font-size:12px;line-height:1.6;margin-top:10px;overflow-wrap:anywhere}.title-status{display:flex;align-items:center;gap:9px;font-size:13px;color:#7c75a2;margin-top:12px}.title-spinner{width:16px;height:16px;border:2px solid #ded8ff;border-top-color:#6255ff;border-radius:50%;animation:title-spin 1s linear infinite}.primary,.secondary{padding:10px 22px;border-radius:8px;border:1px solid #ded9f6;font-size:14px}.primary{background:#5b48ff;color:#fff;border-color:#5b48ff}.secondary{background:white;color:#65617f}button:disabled{opacity:.45;cursor:not-allowed}textarea:focus-visible,button:focus-visible{outline:2px solid #8584ff;outline-offset:2px}@keyframes title-spin{to{transform:rotate(360deg)}}@media(prefers-reduced-motion:reduce){.title-spinner{animation:none}}
</style>
