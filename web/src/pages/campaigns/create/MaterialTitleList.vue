<template>
  <div>
    <section v-for="group in groups" :key="group.id" class="tgroup">
      <div class="tgroup__head"><span class="tgroup__name">{{ group.label }}<ReferenceIcon name="help-fill" /></span><span class="tgroup__added">已添加：{{ rows(group.id).length }}</span></div>
      <p class="tgroup__sub">{{ group.description }}</p>
      <div class="trows">
        <div v-for="row in rows(group.id)" :key="row.key" class="trow" :class="{ 'trow--active': selectedKey === row.key }" @click="select(row)" @keydown.enter="select(row)">
          <input v-if="editingKey === row.key" v-model="draft" class="trow__box" maxlength="55" :disabled="busy" :aria-label="`编辑${group.label}`" @click.stop @input="select(row, draft)" @keydown.enter.stop="save(row)" />
          <button v-else type="button" class="trow__text title-text" :title="row.text" @click.stop="select(row)">{{ row.text }}</button>
          <span v-if="row.material.engineMaterialId && editingKey !== row.key" class="srctag srctag--ai"><ReferenceIcon name="sparkle" />AI 智能生成</span>
          <span class="trow__count">{{ (editingKey === row.key ? draft : row.text).length }}/55</span>
          <span v-if="!readOnly" class="trow__ops">
            <button v-if="editingKey === row.key" type="button" class="iconbtn" :disabled="busy || !draft.trim() || draft.trim() === row.text" aria-label="保存标题" title="保存标题并重新质检" @click.stop="save(row)"><ReferenceIcon name="check" /></button>
            <button v-else type="button" class="iconbtn" :disabled="busy" aria-label="编辑标题" title="编辑标题" @click.stop="edit(row)"><ReferenceIcon name="edit" /></button>
            <button type="button" class="iconbtn" :disabled="busy || row.material.titles.length <= 1" aria-label="删除标题" :title="row.material.titles.length <= 1 ? '每条素材至少保留一个标题' : '删除标题并重新质检'" @click.stop="remove(row)"><ReferenceIcon name="trash" /></button>
          </span>
        </div>
      </div>
      <p v-if="!rows(group.id).length" class="title-empty">当前商品暂无{{ group.label }}</p>
      <div v-if="!readOnly && groupMaterials(group.id).length" class="title-add">
        <label class="title-add__target"><span class="title-add__label">新增标题 · 关联素材</span><select v-model="targets[group.id]" class="title-target" :disabled="busy" :aria-label="`新增${group.label}对应素材`"><option value="">选择对应素材</option><option v-for="m in groupMaterials(group.id)" :key="m.id" :value="m.id" :title="m.title">{{ materialLabel(m) }}</option></select></label>
        <div class="trow"><input v-model="newTitles[group.id]" class="trow__box" maxlength="55" :disabled="busy" :aria-label="`新增${group.label}`" placeholder="输入新标题，最多55字" /><span class="trow__count">{{ newTitles[group.id].length }}/55</span><button class="iconbtn" type="button" :disabled="busy || !canAdd(group.id)" aria-label="添加标题" title="添加标题并质检" @click="add(group.id)"><ReferenceIcon name="plus" /></button></div>
      </div>
    </section>
    <p v-if="!readOnly" class="title-help">标题与对应素材关联；修改后重新质检并确认素材包。每个商品最多29条自定义标题。</p>
  </div>
</template>
<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import ReferenceIcon from '../reference/ReferenceIcon.vue';
import type { CreationMaterial } from '@/features/delivery/creationApi';
const props = defineProps<{ materials: CreationMaterial[]; busy: boolean; readOnly?: boolean }>();
const emit = defineEmits<{ select: [id: string, text: string]; save: [id: string, titles: string[]] }>();
type Group = 'video' | 'card';
type Row = { key: string; material: CreationMaterial; index: number; text: string };
const groups: { id: Group; label: string; description: string }[] = [{ id: 'video', label: '标题', description: '配视频素材，在短视频信息流场景展示' }, { id: 'card', label: '商品卡标题', description: '配图片、图文素材，在抖音商城、搜索等场景展示' }];
const selectedKey = ref(''), editingKey = ref(''), draft = ref('');
const targets = ref<Record<Group,string>>({ video: '', card: '' }), newTitles = ref<Record<Group,string>>({ video: '', card: '' });
const groupMaterials = (id: Group) => props.materials.filter(m => id === 'video' ? m.type === 'VIDEO' : m.type !== 'VIDEO');
function materialLabel(material: CreationMaterial) {
  const labels = { VIDEO: '视频素材', IMAGE: '商品卡图片', CAROUSEL: '图文素材' };
  const index = props.materials.filter(m => m.type === material.type).findIndex(m => m.id === material.id) + 1;
  return `${labels[material.type]} ${index}`;
}
const rows = (id: Group): Row[] => groupMaterials(id).flatMap(m => m.titles.map((text,index) => ({ key: `${m.id}:${index}`, material: m, index, text })));
const allRows = computed(() => [...rows('video'), ...rows('card')]);
function select(row: Row, text = row.text) { selectedKey.value = row.key; emit('select', row.material.id, text); }
function edit(row: Row) { if (props.readOnly || props.busy) return; editingKey.value = row.key; draft.value = row.text; select(row); }
function save(row: Row) {
  const text = draft.value.trim();
  if (props.readOnly || props.busy || editingKey.value !== row.key || !text || text.length > 55 || text === row.text) return;
  const titles = [...row.material.titles]; titles[row.index] = text;
  emit('save', row.material.id, titles); editingKey.value = ''; select(row, text);
}
function remove(row: Row) {
  if (props.readOnly || props.busy || row.material.titles.length <= 1) return;
  emit('save', row.material.id, row.material.titles.filter((_,i) => i !== row.index)); editingKey.value = '';
}
function canAdd(group: Group) {
  const m = groupMaterials(group).find(x => x.id === targets.value[group]), text = newTitles.value[group].trim();
  const unique = new Set(props.materials.flatMap(x => x.titles));
  return !!m && !!text && text.length <= 55 && m.titles.length < 29 && !m.titles.includes(text) && (unique.has(text) || unique.size < 29);
}
function add(group: Group) {
  if (props.readOnly || props.busy || !canAdd(group)) return;
  const m = groupMaterials(group).find(x => x.id === targets.value[group])!;
  const text = newTitles.value[group].trim(); emit('save', m.id, [...m.titles, text]); emit('select', m.id, text); newTitles.value[group] = '';
}
watch(() => props.materials[0]?.productId, () => { selectedKey.value = ''; editingKey.value = ''; newTitles.value = { video: '', card: '' }; targets.value = { video: '', card: '' }; });
watch(allRows, current => {
  if (!current.some(row => row.key === selectedKey.value)) { const first = current[0]; selectedKey.value = ''; if (first) select(first); }
  else if (editingKey.value !== selectedKey.value) { const selected = current.find(row => row.key === selectedKey.value)!; emit('select', selected.material.id, selected.text); }
  if (editingKey.value && !current.some(row => row.key === editingKey.value)) editingKey.value = '';
  for (const group of groups) if (!groupMaterials(group.id).some(m => m.id === targets.value[group.id])) targets.value[group.id] = groupMaterials(group.id)[0]?.id || '';
}, { immediate: true });
</script>
<style scoped>
.title-text { text-align: left; }
.title-add__target { display: block; margin-top: 14px; }
.title-add__label { display: block; margin-bottom: 6px; font-size: 12px; color: #8588a5; }
.title-target { width: 100%; font-size: 12px; border: 1px solid #e0e2ef; border-radius: 8px; padding: 8px 10px; background: #fff; color: #555875; margin-bottom: 8px; }
.title-empty,.title-help { color: #8588a5; font-size: 12px; line-height: 1.7; margin-top: 14px; }
.iconbtn:disabled { opacity: .4; cursor: not-allowed; }
@media (max-width:639px) { .trow { flex-wrap: wrap; } .trow .srctag { display: none; } }
</style>
