<template>
  <div>
    <section v-for="group in groups" :key="group.kind" class="tgroup">
      <div class="tgroup__head"><span class="tgroup__name">{{ group.label }}</span><span class="tgroup__added">已添加：{{ values(group.kind).length }}</span></div>
      <p class="tgroup__sub">{{ group.description }}</p>
      <div class="trows"><div v-for="(text,index) in values(group.kind)" :key="`${group.kind}:${index}`" class="trow">
        <input v-if="editing?.kind === group.kind && editing.index === index" v-model="draft" class="trow__box" maxlength="55" :disabled="busy" :aria-label="`编辑${group.label}`" @keydown.enter="save()" @keydown.esc="editing = undefined" />
        <span v-else class="trow__text title-text">{{ text }}</span>
        <span class="trow__count">{{ (editing?.kind === group.kind && editing.index === index ? draft : text).length }}/55</span>
        <span v-if="!readOnly" class="trow__ops"><button v-if="editing?.kind === group.kind && editing.index === index" type="button" class="iconbtn" :disabled="busy || !!titlesInputError([draft])" aria-label="保存标题" title="保存标题" @click="save"><ReferenceIcon name="check" /></button><button v-else type="button" class="iconbtn" :disabled="busy" aria-label="编辑标题" title="编辑标题" @click="editing = {kind:group.kind,index}; draft=text; error='' "><ReferenceIcon name="edit" /></button><button type="button" class="iconbtn" :disabled="busy || values(group.kind).length <= 1" aria-label="删除标题" :title="values(group.kind).length <= 1 ? '当前类型至少保留一条投放标题' : '删除标题'" @click="remove(group.kind,index)"><ReferenceIcon name="trash" /></button></span>
      </div></div>
      <p v-if="!values(group.kind).length" class="title-help">当前商品暂无{{ group.label }}</p>
    </section>
    <p v-if="error || editing && titlesInputError([draft])" class="title-error" role="alert">{{ error || titlesInputError([draft]) }}</p>
    <p class="title-help">标题按商品管理，由平台按支持的类型组合使用。修改标题不会重新生成或上传图片、视频；发布前会校验文案。</p>
  </div>
</template>
<script setup lang="ts">
import { ref, watch } from 'vue';
import type { CreationMaterial } from '@/features/delivery/creationApi';
import { titlesInputError } from '@/features/delivery/inputValidation';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
type Kind='CUSTOM'|'CARD';
const props=defineProps<{materials:CreationMaterial[];pool?:{customTitles:string[];cardTitles:string[]};busy:boolean;readOnly?:boolean}>();
const emit=defineEmits<{save:[kind:Kind,titles:string[]]}>();
const groups: {kind:Kind;label:string;description:string}[]=[{kind:'CUSTOM',label:'标题',description:'用于当前商品的视频、图文投放，不固定绑定某一条素材。'},{kind:'CARD',label:'商品卡标题',description:'用于当前商品的商品卡投放，实际组合以平台支持为准。'}];
const editing=ref<{kind:Kind;index:number}>(), draft=ref(''),error=ref('');
const pending=ref(false);
function values(kind:Kind) {
  const saved=kind==='CARD'?props.pool?.cardTitles:props.pool?.customTitles;
  return saved?.length ? saved : [...new Set(props.materials.filter(m=>kind==='CARD'?m.type==='IMAGE':m.type!=='IMAGE').flatMap(m=>m.titles))];
}
function save() {
  if(!editing.value || props.readOnly || props.busy || titlesInputError([draft.value]))return;
  const {kind,index}=editing.value, titles=[...values(kind)],text=draft.value.trim();
  if(titles[index]===text){editing.value=undefined;return;}
  if(titles.some((t,i)=>i!==index && t===text)){error.value='当前类型已存在相同标题';return;}
  titles[index]=text;pending.value=true;emit('save',kind,titles);error.value='';
}
function remove(kind:Kind,index:number) {
  if(props.readOnly||props.busy||values(kind).length<=1)return;
  emit('save',kind,values(kind).filter((_,i)=>i!==index));editing.value=undefined;error.value='';
}
watch(()=>props.materials[0]?.productId,()=>{editing.value=undefined;error.value='';});
watch(()=>props.busy,busy=>{if(!busy && pending.value){pending.value=false;if(editing.value && values(editing.value.kind)[editing.value.index]===draft.value.trim())editing.value=undefined;else error.value='标题暂未保存，输入内容已保留，可继续修改或重试。';}});
</script>
<style scoped>
.title-text{white-space:normal;overflow-wrap:anywhere;text-align:left}.title-help{font-size:12px;line-height:1.8;color:#8588a5;margin-top:14px}.title-error{font-size:12px;color:#d54650;margin-top:12px}.trow__box{outline:none!important;border:0!important;box-shadow:none!important}.trow:focus-within{outline:2px solid #b7b4fa;outline-offset:2px}.iconbtn:disabled{opacity:.4;cursor:default}
</style>
