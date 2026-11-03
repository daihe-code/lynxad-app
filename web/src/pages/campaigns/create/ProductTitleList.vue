<template>
  <div class="product-titles">
    <section v-for="group in groups" :key="group.kind" class="copy-group">
      <div class="copy-heading"><h3>{{ group.label }}</h3><span>已添加 {{ values(group.kind).length }} 条</span></div>
      <p class="copy-description">{{ group.description }}</p>
      <div class="copy-rows">
        <div v-for="(text,index) in values(group.kind)" :key="`${group.kind}:${index}`" class="copy-row">
          <div class="copy-field">
            <input :value="isEditing(group.kind,index) ? draft : text" :maxlength="titleLimit(group.kind)" :readonly="readOnly" :disabled="busy"
              :aria-label="`${group.label} ${index+1}`" @focus="edit(group.kind,index,text)" @input="draft=($event.target as HTMLInputElement).value"
              @blur="save" @keydown.enter="save" @keydown.esc="editing=undefined" />
            <span v-if="isGeneratedTitle(materials,pool,group.kind,text)" class="copy-ai"><ReferenceIcon name="sparkle" />智能生成</span>
            <span class="copy-count">{{ (isEditing(group.kind,index) ? draft : text).length }}/{{ titleLimit(group.kind) }}</span>
          </div>
          <div v-if="!readOnly" class="copy-actions">
            <button type="button" :disabled="busy" :aria-label="`重新生成${group.label} ${index+1}`" title="重新生成" @mousedown.prevent @click="regenerate(group.kind,index,text)"><ReferenceIcon name="refresh" /></button>
            <button type="button" :disabled="busy || values(group.kind).length <= 1" :aria-label="`删除${group.label} ${index+1}`" :title="values(group.kind).length <= 1 ? '当前类型至少保留一条投放标题' : '删除'" @mousedown.prevent @click="remove(group.kind,index)"><ReferenceIcon name="trash" /></button>
          </div>
        </div>
      </div>
      <p v-if="!values(group.kind).length" class="copy-description">当前商品暂无{{ group.label }}</p>
      <button v-if="!readOnly" type="button" class="copy-add" :disabled="busy || !!additionError(group.kind)" :title="additionError(group.kind) || undefined" @click="openAddition(group.kind)"><ReferenceIcon name="plus" />新增{{ group.label }}</button>
    </section>
    <p v-if="error || validationError" class="copy-error" role="alert">{{ error || validationError }}</p>
    <p v-if="!adding && generation?.message" class="copy-status" :class="{'copy-error':generation.status==='FAILED'}" role="status">{{ generation.message }}</p>
    <AddTitleDialog v-if="adding" :kind="adding" :busy="busy" :generation="generation" :validate="text => additionError(adding!,text)" @close="adding=undefined" @add="(source,text,receipt) => emit('add',adding!,source,text,receipt)" />
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import type { CreationMaterial, ProductTitlesPool, TitleGeneration } from '@/features/delivery/creationApi';
import { productTitleValues, isGeneratedTitle, productTitleError, titleLimit, titleAdditionError, type TitleKind } from '@/features/delivery/productTitles';
import type { TitleSource, TitleAdditionReceipt } from '@/features/delivery/titleAddition';
import AddTitleDialog from './AddTitleDialog.vue';
import ReferenceIcon from '../reference/ReferenceIcon.vue';

const props=defineProps<{materials:CreationMaterial[];pool?:ProductTitlesPool;busy:boolean;readOnly?:boolean;generation?:TitleGeneration}>();
const emit=defineEmits<{save:[kind:TitleKind,titles:string[]];regenerate:[kind:TitleKind,index:number,original:string];add:[kind:TitleKind,source:TitleSource,text:string,receipt:TitleAdditionReceipt]}>();
const groups: {kind:TitleKind;label:string;description:string}[]=[
  {kind:'CUSTOM',label:'标题',description:'配视频素材，在短视频信息流场景展示'},
  {kind:'CARD',label:'商品卡标题',description:'配图片、图文素材，在抖音商城、搜索等场景展示'},
];
const editing=ref<{kind:TitleKind;index:number}>(),draft=ref(''),error=ref(''),pending=ref(false);
const adding=ref<TitleKind>();
const additionError=(kind:TitleKind,text?:string)=>titleAdditionError(props.materials,props.pool,kind,text);
function openAddition(kind:TitleKind){if(props.readOnly||props.busy||additionError(kind))return;editing.value=undefined;error.value='';adding.value=kind;}
const validationError=computed(()=>editing.value?productTitleError(editing.value.kind,draft.value):'');
const values=(kind:TitleKind)=>productTitleValues(props.materials,props.pool,kind);
const isEditing=(kind:TitleKind,index:number)=>editing.value?.kind===kind&&editing.value.index===index;
function edit(kind:TitleKind,index:number,text:string){if(props.readOnly||props.busy)return;editing.value={kind,index};draft.value=text;error.value='';}
function save(){
  if(!editing.value||props.readOnly||props.busy||validationError.value)return;
  const {kind,index}=editing.value,titles=[...values(kind)],text=draft.value.trim();
  if(titles[index]===text){editing.value=undefined;return;}
  if(titles.some((t,i)=>i!==index&&t===text)){error.value='当前类型已存在相同标题';return;}
  titles[index]=text;pending.value=true;emit('save',kind,titles);error.value='';
}
function remove(kind:TitleKind,index:number){if(props.readOnly||props.busy||values(kind).length<=1)return;editing.value=undefined;error.value='';emit('save',kind,values(kind).filter((_,i)=>i!==index));}
function regenerate(kind:TitleKind,index:number,text:string){if(props.readOnly||props.busy)return;editing.value=undefined;error.value='';emit('regenerate',kind,index,text);}
watch(()=>props.materials[0]?.productId,()=>{editing.value=undefined;error.value='';});
watch(()=>props.busy,busy=>{if(!busy&&pending.value){pending.value=false;if(editing.value&&values(editing.value.kind)[editing.value.index]===draft.value.trim())editing.value=undefined;else error.value='标题暂未保存，输入内容已保留，可重试。';}});
</script>
<style scoped>
.copy-add{display:flex;align-items:center;justify-content:center;gap:8px;width:100%;padding:10px;margin-top:12px;border:1px dashed #c8c0fa;border-radius:8px;background:#fff;color:#6255ed;font-size:14px;cursor:pointer}.copy-add svg{width:17px;height:17px}.copy-add:disabled{opacity:.45;cursor:not-allowed}.copy-add:focus-visible{outline:2px solid #8584ff;outline-offset:2px}.copy-group+.copy-group{border-top:1px solid #ececf5;margin-top:24px;padding-top:24px}.copy-heading{display:flex;align-items:center;gap:20px}.copy-heading h3{font-size:17px;font-weight:650;margin:0;color:#202137}.copy-heading>span,.copy-description{font-size:13px;color:#8588a5}.copy-description{margin:7px 0 14px}.copy-rows{display:grid;gap:12px}.copy-row{display:flex;gap:14px;align-items:start}.copy-field{min-width:0;flex:1;display:flex;align-items:center;gap:12px;min-height:46px;padding:5px 14px;border:1px solid #d8d5fa;border-radius:9px;background:#fff}.copy-field:focus-within{border-color:#6258ff;box-shadow:0 0 0 1px #c2bdff}.copy-field input{min-width:0;flex:1;width:100%;border:0!important;outline:0!important;box-shadow:none!important;background:transparent!important;color:#34364d;padding:4px 0;font-size:14px}.copy-ai{display:inline-flex;align-items:center;justify-content:center;gap:7px;flex-shrink:0;border-radius:8px;background:#f0eeff;color:#6156f6;padding:6px 12px;font-size:12px;font-weight:600}.copy-ai svg{width:14px;height:14px}.copy-count{flex-shrink:0;min-width:42px;color:#9495ad;font-size:12px;text-align:right}.copy-actions{display:flex;gap:12px}.copy-actions button{display:flex;align-items:center;justify-content:center;width:52px;height:46px;border:1px solid #dedcf4;border-radius:9px;color:#6256e8;background:#fff;cursor:pointer}.copy-actions svg{width:18px;height:18px}.copy-actions button:hover:not(:disabled){color:#6258ff;border-color:#a5a0ff;background:#f8f7ff}.copy-actions button:disabled{opacity:.4;cursor:default}.copy-error{font-size:12px;color:#d54650;margin-top:12px}.copy-status{font-size:13px;margin-top:16px;color:#777b9b}.copy-status.copy-error{color:#d54650}@media(max-width:640px){.copy-row{gap:8px;flex-wrap:wrap}.copy-field{flex-basis:100%;flex-wrap:wrap;gap:6px}.copy-field input{flex-basis:100%}.copy-ai{margin-right:auto}.copy-actions{margin-left:auto}.copy-actions button{height:38px;width:44px}}
</style>
