<template>
  <div class="plan-products" v-loading="loading">
    <div class="product-toolbar">
      <el-input v-model="searchDraft" placeholder="输入商品名称 / ID，回车搜索" clearable aria-label="搜索投放商品" @keyup.enter="search" @clear="search"><template #suffix><el-button text :icon="Search" aria-label="搜索商品" @click="search" /></template></el-input>
      <div class="product-tools"><el-button :icon="Refresh" :disabled="loading" @click="load(true)">刷新价格</el-button><span v-if="data?.shared">转投计划共用商品和素材</span><el-tooltip content="导出当前筛选的商品数据"><el-button :icon="Download" aria-label="导出商品数据" :disabled="!filtered.length" @click="download" /></el-tooltip><el-popover placement="bottom-end" trigger="click" :width="230"><template #reference><el-button :icon="Setting" aria-label="设置商品数据列" /></template><strong>展示指标</strong><el-checkbox-group v-model="visibleColumns"><el-checkbox v-for="m in metrics" :key="m.key" :value="m.key">{{ m.label }}</el-checkbox></el-checkbox-group></el-popover></div>
    </div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon><el-button text @click="load()">重新加载</el-button></el-alert>
    <el-alert v-if="data?.warning" :title="data.warning" type="warning" :closable="false" show-icon />
    <div v-if="pending" class="pending-change" role="alert"><div><strong>商品删除结果待核查</strong><p>{{ pending.message }}</p></div><el-button type="primary" :loading="deleting" @click="continueDelete">继续核查</el-button></div>
    <el-table ref="table" :data="paged" row-key="id" class="product-table" scrollbar-always-on :default-sort="{ prop: 'metrics.shows', order: 'descending' }" :empty-text="keyword ? '没有符合条件的商品' : '当前计划暂无投放商品'" @selection-change="selected = $event" @sort-change="sortChange">
      <el-table-column type="selection" :reserve-selection="true" width="44" />
      <el-table-column label="商品" min-width="255"><template #default="{ row }"><div class="product-info"><el-image v-if="row.imageUrl" :src="row.imageUrl" :preview-src-list="[row.imageUrl]" preview-teleported fit="cover" /><div class="product-name"><el-tooltip :content="row.name" placement="top"><strong>{{ row.name }}</strong></el-tooltip><small>ID：{{ row.id }}</small><ProductPrice :price-info="row.priceInfo" /></div></div></template></el-table-column>
      <el-table-column label="操作" width="108"><template #default="{ row }"><div class="row-actions"><router-link :to="`/campaigns/${planId}/edit`">编辑</router-link><el-tooltip :content="deleteHint(row)" :disabled="row.deletable && !pending"><span><el-button link type="danger" :disabled="deleting || !!pending || !row.deletable" @click="confirmDelete(row)">删除</el-button></span></el-tooltip></div></template></el-table-column>
      <el-table-column label="审核状态" width="100"><template #default="{ row }"><span class="audit-state" :class="row.auditStatus"><i v-if="row.auditStatus" />{{ auditText(row.auditStatus) }}</span></template></el-table-column>
      <el-table-column v-for="m in columns" :key="m.key" :prop="`metrics.${m.key}`" :label="m.label" :min-width="m.width" sortable="custom" align="right"><template #default="{ row }">{{ value(row.metrics[m.key], m.kind) }}</template></el-table-column>
    </el-table>
    <div v-if="filtered.length" class="product-summary"><span>共 {{ filtered.length }} 个商品<span v-if="selected.length"> · 已选择 {{ selected.length }} 个</span></span><div v-for="m in columns" :key="m.key"><small>{{ m.label }}</small><strong>{{ value(summary[m.key], m.kind) }}</strong></div></div>
    <footer class="product-footer"><span>区间：{{ range[0] }} ～ {{ range[1] }}<br />有数据的商品 {{ filteredDataCount }}/{{ filtered.length }}，汇总仅计算平台返回的数据；未返回的指标显示「—」。</span><el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="filtered.length" :page-sizes="[10, 20, 50, 100]" layout="total, prev, pager, next, sizes" @size-change="page = 1" /></footer>
    <el-dialog v-model="deleteVisible" title="删除投放商品" width="min(540px, calc(100vw - 32px))" append-to-body :close-on-click-modal="false" :close-on-press-escape="!deleting" :show-close="!deleting" @closed="clearTarget">
      <div class="delete-product"><img v-if="target?.imageUrl" :src="target.imageUrl" alt="待删除商品" /><div><strong>{{ target?.name }}</strong><small>商品 ID：{{ target?.id }}</small></div></div>
      <p>确认从{{ data?.shared ? '关联的放量、控成本两个计划' : '当前投放计划' }}中删除此商品？该商品在计划中的视频、图片、图文和标题关联会一起移除。</p>
      <el-alert title="删除可能影响投放效果及保障资格。素材中心里的素材文件仍会保留。" type="warning" :closable="false" show-icon />
      <p v-if="deleteError" class="delete-error" role="alert">{{ deleteError }}</p>
      <template #footer><el-button :disabled="deleting" @click="deleteVisible = false">取消</el-button><el-button type="danger" :loading="deleting" @click="submitDelete">确认删除</el-button></template>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElMessage, type TableInstance } from 'element-plus';
import { Download, Refresh, Search, Setting } from '@element-plus/icons-vue';
import { insightProducts, deletePlanProduct, type PlanProducts, type PlanProduct, type ProductChange, type ProductMetrics } from '@/features/delivery/planInsightsApi';
import ProductPrice from '@/components/ProductPrice.vue';
import { productPriceText } from '@/features/catalog/productPrice';
import type { StatisticsRange } from '@/features/delivery/statisticsRange';
const props = defineProps<{ planId: string; qcPlanId: string; range: StatisticsRange; refreshToken: number }>();
const emit = defineEmits<{ changed: [] }>();
const data = ref<PlanProducts>(), loading = ref(false), error = ref(''), searchDraft = ref(''), keyword = ref(''), selected = ref<PlanProduct[]>([]), table = ref<TableInstance>();
const page = ref(1), pageSize = ref(10), sort = ref<{ key?: keyof ProductMetrics; order?: string }>({ key: 'shows', order: 'descending' });
const metrics: { key: keyof ProductMetrics; label: string; kind: string; width: number }[] = [
  { key: 'shows', label: '整体展示次数', kind: 'count', width: 115 }, { key: 'clicks', label: '整体点击次数', kind: 'count', width: 115 },
  { key: 'ctr', label: '整体点击率', kind: 'rate', width: 110 }, { key: 'cvr', label: '整体转化率', kind: 'rate', width: 110 },
  { key: 'cost', label: '整体消耗（元）', kind: 'money', width: 130 }, { key: 'orders', label: '整体成交订单数', kind: 'count', width: 150 },
  { key: 'gmv', label: '整体成交金额（元）', kind: 'money', width: 165 }, { key: 'roi', label: '整体支付 ROI', kind: 'number', width: 135 },
];
const visibleColumns = ref(metrics.map(m => m.key)), columns = computed(() => metrics.filter(m => visibleColumns.value.includes(m.key)));
const filtered = computed(() => {
  const text = keyword.value.toLocaleLowerCase(); const rows = (data.value?.records || []).filter(p => !text || p.name.toLocaleLowerCase().includes(text) || p.id.includes(text));
  if (!sort.value.key || !sort.value.order) return rows;
  const key = sort.value.key, order = sort.value.order === 'ascending' ? 1 : -1;
  return [...rows].sort((a, b) => { const x = a.metrics[key], y = b.metrics[key]; if (x == null) return y == null ? 0 : 1; if (y == null) return -1; return (Number(x) - Number(y)) * order; });
});
const paged = computed(() => filtered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value));
const filteredDataCount = computed(() => filtered.value.filter(p => Object.values(p.metrics).some(v => v != null)).length);
const summary = computed<ProductMetrics>(() => {
  const result: ProductMetrics = {}; for (const key of ['shows', 'clicks', 'cost', 'orders', 'gmv'] as const) { const known = filtered.value.map(p => p.metrics[key]).filter((v): v is number => v != null); result[key] = known.length ? known.reduce((sum, v) => sum + Number(v), 0) : null; }
  const ratio = (a: number | null | undefined, b: number | null | undefined, factor: number) => a == null || b == null || !b ? null : a / b * factor;
  result.ctr = ratio(result.clicks, result.shows, 100); result.cvr = ratio(result.orders, result.clicks, 100); result.roi = ratio(result.gmv, result.cost, 1); return result;
});
const pending = ref<ProductChange>(), deleting = ref(false), deleteVisible = ref(false), target = ref<PlanProduct>(), requestKey = ref(''), deleteError = ref('');
const auditText = (status?: string) => ({ PASS: '审核通过', REJECT: '审核拒绝', IN_PROGRESS: '审核中' } as Record<string, string>)[status || ''] || '—';
const deleteHint = (row: PlanProduct) => pending.value ? '关联计划有删除操作待核查' : !row.deletable ? '计划至少需要保留一件商品，或有关联操作待核查' : `删除${row.name}`;
const errorText = (e: any) => e?.response?.data?.message || e?.message || '查询失败，请稍后重试';
function value(v: number | null | undefined, kind: string) { if (v == null) return '—'; const n = Number(v); return kind === 'count' ? n.toLocaleString('zh-CN') : `${n.toFixed(2)}${kind === 'rate' ? '%' : ''}`; }
function search() { keyword.value = searchDraft.value.trim(); page.value = 1; table.value?.clearSelection(); }
function sortChange(v: { prop?: string; order?: string }) { sort.value = { key: v.prop?.replace('metrics.', '') as keyof ProductMetrics, order: v.order }; page.value = 1; }
let serial = 0, disposed = false;
async function load(refreshPrices = false) {
  const turn = ++serial; loading.value = true; error.value = ''; data.value = undefined; selected.value = []; table.value?.clearSelection();
  try { const result = await insightProducts(props.planId, [...props.range], refreshPrices); if (!disposed && turn === serial) { data.value = result; pending.value = result.pending; page.value = Math.min(page.value, Math.max(1, Math.ceil(filtered.value.length / pageSize.value))); } }
  catch (e) { if (!disposed && turn === serial) error.value = errorText(e); }
  finally { if (!disposed && turn === serial) loading.value = false; }
}
function confirmDelete(row: PlanProduct) { target.value = row; requestKey.value = crypto.randomUUID(); deleteError.value = ''; deleteVisible.value = true; }
function clearTarget() { if (!deleting.value) target.value = undefined; }
async function perform(product: string, key: string) {
  if (deleting.value) return; deleting.value = true; deleteError.value = '';
  try {
    const result = await deletePlanProduct(props.planId, product, key, props.qcPlanId);
    if (disposed) return;
    if (result.status === 'DONE') { pending.value = undefined; deleteVisible.value = false; ElMessage.success(result.message); emit('changed'); }
    else { pending.value = result; deleteVisible.value = false; ElMessage.warning('商品删除结果待核查，请点击“继续核查”'); }
    await load();
  } catch (e) {
    // A disconnected client may have left a durable operation. Preserve its key for safe read-before-retry.
    deleteError.value = errorText(e);
    if (!(((e as any)?.code || (e as any)?.response?.data?.code) > 0)) { pending.value = { requestKey: key, productId: product, status: 'PENDING', message: '请求中断，删除结果待核查，请继续核查。' }; deleteVisible.value = false; }
    else if (!deleteVisible.value) ElMessage.error(deleteError.value);
  } finally { deleting.value = false; }
}
async function submitDelete() { if (target.value) await perform(target.value.id, requestKey.value); }
async function continueDelete() { if (pending.value) await perform(pending.value.productId, pending.value.requestKey); }
function download() {
  const rows = selected.value.length ? filtered.value.filter(p => selected.value.some(v => v.id === p.id)) : filtered.value;
  const csv = (v: string) => `"${(/^[=+\-@\t\r]/.test(v) || /^\d{16,}$/.test(v) ? "'" : '') + v.replaceAll('"', '""')}"`;
  const lines = [['商品', '商品ID', '商品参考价格', '审核状态', ...columns.value.map(m => m.label)], ...rows.map(p => [p.name, p.id, productPriceText(p.priceInfo), auditText(p.auditStatus), ...columns.value.map(m => value(p.metrics[m.key], m.kind))])];
  const url = URL.createObjectURL(new Blob(['\uFEFF' + lines.map(row => row.map(csv).join(',')).join('\r\n')], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a'); link.href = url; link.download = `计划商品_${props.range[0]}_${props.range[1]}.csv`; link.click(); URL.revokeObjectURL(url);
}
watch(() => [props.planId, props.range[0], props.range[1], props.refreshToken], () => { page.value = 1; void load(); });
onMounted(() => load()); onBeforeUnmount(() => { disposed = true; serial++; });
</script>
<style scoped>
.product-toolbar{display:flex;justify-content:space-between;gap:20px;margin-bottom:18px;align-items:center}.product-toolbar>.el-input{max-width:310px}.product-tools{display:flex;gap:8px;align-items:center}.product-tools>span{font-size:12px;color:#878ba1;margin-right:8px}.product-info{display:flex;align-items:center;gap:12px;padding:9px 0}.product-info .el-image{width:44px;height:44px;flex:none;border-radius:5px}.product-name{min-width:0}.product-name strong{display:block;font-size:12px;font-weight:500;overflow:hidden;white-space:nowrap;text-overflow:ellipsis;color:#292e53}.product-name small{display:block;color:#9196aa;font-size:11px;margin-top:5px}.row-actions{display:flex;align-items:center;gap:12px}.row-actions a{font-size:12px;text-decoration:none;color:#5b57ff}.row-actions .el-button{font-size:12px}.audit-state{display:flex;align-items:center;gap:5px;font-size:12px}.audit-state i{width:6px;height:6px;background:#e6a23c;border-radius:50%}.audit-state.PASS i{background:#00b36a}.audit-state.REJECT i{background:#f56c6c}.product-summary{display:flex;align-items:center;gap:20px;padding:16px;background:#f8f9fe;margin-top:8px;overflow-x:auto}.product-summary>span{white-space:nowrap;color:#727891;font-size:12px}.product-summary>div{min-width:105px;text-align:right}.product-summary small{display:block;color:#9297ab;font-size:11px;white-space:nowrap}.product-summary strong{display:block;margin-top:5px;font-size:13px}.product-footer{display:flex;gap:18px;justify-content:space-between;align-items:flex-end;margin-top:18px}.product-footer>span{font-size:11px;line-height:1.8;color:#9297ab}.product-footer :deep(.el-pagination){margin:0}.pending-change{display:flex;align-items:center;justify-content:space-between;gap:18px;padding:16px;background:#fff7e8;border:1px solid #f5deb1;border-radius:8px;margin:14px 0;font-size:13px}.pending-change p{line-height:1.7;margin-bottom:0}.delete-product{display:flex;align-items:center;gap:12px;margin-bottom:20px}.delete-product img{width:52px;height:52px;object-fit:cover;border-radius:6px}.delete-product small{display:block;color:#9297ab;margin-top:6px}.delete-error{color:#f56c6c}.plan-products :deep(.el-alert){margin-bottom:14px}.el-checkbox-group{display:flex;flex-direction:column;padding-top:10px}
@media(max-width:700px){.product-toolbar{flex-wrap:wrap;gap:12px}.product-toolbar>.el-input{max-width:none}.product-tools{width:100%;justify-content:flex-end}.product-tools>span{margin-right:auto}.product-footer{flex-direction:column;align-items:flex-start}.product-footer :deep(.el-pagination){max-width:100%;flex-wrap:wrap;row-gap:10px}.pending-change{align-items:flex-start;flex-direction:column}}
</style>
