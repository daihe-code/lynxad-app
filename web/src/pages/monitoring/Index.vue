<template>
  <section class="monitoring-center" v-loading="busy">
    <header class="center-heading"><div><h1>全店盯盘中心</h1><p>集中查看所有广告账户的计划、利润建议与自动调控记录。</p></div><div class="actions"><el-button @click="limitsOpen = true">账户预算上限</el-button><el-button type="warning" plain :disabled="!center?.automatic" @click="holdAll">暂停全店自动调控</el-button><el-button @click="load">刷新</el-button></div></header>
    <el-alert v-if="error" :title="error" type="error" show-icon :closable="false" />
    <div class="overview"><div><span>持续盯盘</span><strong>{{ center?.watched ?? '—' }}</strong></div><div><span>已授权自动执行</span><strong>{{ center?.automatic ?? '—' }}</strong></div><div><span>已暂停自动执行</span><strong>{{ center?.held ?? '—' }}</strong></div><div><span>更新频率</span><strong>5 分钟</strong></div></div>
    <el-tabs v-model="tab" @tab-change="changed"><el-tab-pane label="全店计划" name="plans" /><el-tab-pane label="建议与执行历史" name="history" /></el-tabs>
    <div class="filters">
      <template v-if="tab === 'plans'"><el-select v-model="state" clearable placeholder="全部盯盘状态" @change="changed"><el-option v-for="item in states" :key="item.value" :label="item.label" :value="item.value" /></el-select><el-input v-model="query" clearable placeholder="搜索计划名称 / 千川 ID" @keyup.enter="changed" @clear="changed" /><el-button @click="changed">搜索</el-button></template>
      <template v-else><el-select v-model="status" clearable placeholder="全部处理结果" @change="changed"><el-option v-for="(label, value) in statuses" :key="value" :label="label" :value="value" /></el-select><el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" @change="changed" /><el-button v-if="planId" @click="clearPlan">取消单计划筛选</el-button></template>
    </div>
    <p v-if="planId && tab === 'history'" class="caption">当前查看计划 {{ planId }} 的历史；删除计划后，历史仍保留。</p>
    <el-table v-if="tab === 'plans'" :data="center?.records || []" empty-text="当前筛选范围没有已发布计划">
      <el-table-column label="计划 / 广告账户" min-width="265"><template #default="{ row }"><router-link :to="planLink(row.planId)">{{ row.planName }}</router-link><small>{{ row.accountName }} · ID：{{ row.qcId }}</small></template></el-table-column>
      <el-table-column label="盯盘状态" min-width="180"><template #default="{ row }"><el-tag :type="row.holdReason || row.error ? 'warning' : row.enabled ? 'success' : 'info'">{{ row.holdReason ? '自动执行已暂停' : !row.enabled ? '未开启' : row.mode === 'AUTO_LIMITED' ? '自动调控' : '只看建议' }}</el-tag><small v-if="row.error || row.holdReason">{{ row.error || row.holdReason }}</small><small v-else-if="row.enabled && row.stale">等待最新数据</small></template></el-table-column>
      <el-table-column label="预估利润 / 今天广告费" min-width="185"><template #default="{ row }">{{ money(row.result?.estimated_contribution_profit) }} / {{ money(row.result?.daily_spend) }}<small>{{ row.result?.state === 'READY' ? '依据较完整的订单数据判断' : '等待订单数据更新或补齐成本' }}</small></template></el-table-column>
      <el-table-column label="最新建议" min-width="230"><template #default="{ row }">{{ row.result?.recommendations.map((r: any) => r.title).join('；') || '暂无建议' }}</template></el-table-column>
      <el-table-column label="最近成功采集" min-width="175"><template #default="{ row }">{{ time(row.lastSuccess) }}</template></el-table-column>
      <el-table-column label="操作" min-width="175"><template #default="{ row }"><el-button link type="primary" @click="router.push(planLink(row.planId))">配置盯盘</el-button><el-button link type="primary" @click="openHistory(row.planId)">历史</el-button><el-button v-if="row.enabled && row.mode === 'AUTO_LIMITED' && row.holdReason" link type="warning" :disabled="actionBusy" @click="resume(row)">恢复授权</el-button></template></el-table-column>
    </el-table>
    <el-table v-else :data="history?.records || []" empty-text="当前范围没有建议或执行记录">
      <el-table-column label="计划 / 时间" min-width="235"><template #default="{ row }">{{ row.planName }}<small>{{ time(row.createdAt) }}</small></template></el-table-column>
      <el-table-column label="建议与依据" min-width="270"><template #default="{ row }"><b>{{ row.title }}</b><small>{{ row.evidence }}</small></template></el-table-column>
      <el-table-column label="拟执行调整" min-width="170"><template #default="{ row }">{{ proposalText(row) }}</template></el-table-column>
      <el-table-column label="处理结果" min-width="200"><template #default="{ row }"><el-tag :type="row.status === 'SUCCEEDED' ? 'success' : ['UNKNOWN', 'VERIFY_PENDING'].includes(row.status) ? 'warning' : 'info'">{{ statuses[row.status] || row.status }}</el-tag><small>{{ row.error }}</small></template></el-table-column>
      <el-table-column label="操作" min-width="220"><template #default="{ row }"><el-button link type="primary" @click="detail = row">查看记录</el-button><router-link v-if="row.kind === 'MATERIAL_GENERATE'" :to="planLink(row.planId)">查看素材与试投</router-link><template v-else-if="['UNKNOWN','VERIFY_PENDING','EXECUTING'].includes(row.status)"><el-button link type="primary" :disabled="actionBusy" @click="recheck(row)">回查平台</el-button><el-button link type="warning" :disabled="actionBusy" @click="acknowledge(row)">人工核查完成</el-button></template></template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="20" :total="tab === 'plans' ? center?.total || 0 : history?.total || 0" layout="total, prev, pager, next" @current-change="load" />
    <p class="caption">自动执行需逐计划授权；平台数据缺失、过期或提交结果不明确时停止执行。搜索抢首屏未纳入自动调控。</p>
    <el-dialog v-model="limitsOpen" title="账户总日预算上限" width="min(620px, 94vw)" @open="initLimits"><p class="caption">约束账户内所有正在投放计划的日预算合计，扩量前从千川重新读取。未设置上限时，不自动增加预算、下调 ROI 或恢复投放。该上限不等于账户实际扣费限额。</p><el-form label-position="top"><el-form-item v-for="a in center?.accounts || []" :key="a.accountId" :label="a.name"><el-input-number v-model="limits[a.accountId]" :min="50" :max="999999999" :precision="2" /><el-button :loading="savingLimit === a.accountId" @click="saveLimit(a.accountId)">保存此账户</el-button></el-form-item></el-form></el-dialog>
    <el-drawer :model-value="!!detail" title="建议与执行记录" size="min(640px, 95vw)" @close="detail = undefined"><template v-if="detail"><h3>{{ detail.title }}</h3><p>{{ detail.planName }}</p><el-descriptions :column="1" border><el-descriptions-item label="状态">{{ statuses[detail.status] }}</el-descriptions-item><el-descriptions-item label="授权版本">{{ detail.version }}</el-descriptions-item><el-descriptions-item label="规则版本">{{ detail.report.rule_version }}</el-descriptions-item><el-descriptions-item label="拟执行">{{ proposalText(detail) }}</el-descriptions-item><el-descriptions-item label="依据">{{ detail.evidence }}</el-descriptions-item><el-descriptions-item label="预估利润（扣成本和广告费）">{{ money(detail.report.estimated_contribution_profit) }}</el-descriptions-item><el-descriptions-item label="本次判断的数据时间范围">{{ time(detail.report.window_start) }} ～ {{ time(detail.report.window_end) }}</el-descriptions-item><el-descriptions-item label="提交时间">{{ time(detail.dispatchedAt) }}</el-descriptions-item><el-descriptions-item label="核查结束时间">{{ time(detail.finishedAt) }}</el-descriptions-item><el-descriptions-item label="说明">{{ detail.error || '—' }}</el-descriptions-item></el-descriptions><h4>平台回查值</h4><el-descriptions v-if="detail.actual" :column="1" border><el-descriptions-item v-for="key in ['status','budget','roi','qcId']" :key="key" :label="actualLabels[key]">{{ detail.actual[key] ?? '—' }}</el-descriptions-item></el-descriptions><div v-if="detail.actual?.operation" class="caption"><p>平台确认：{{ detail.actual.actionConfirmed ? '已确认' : '待核查' }}</p><p v-if="(detail.actual.operation as any).taskName">任务名称：{{ (detail.actual.operation as any).taskName }}</p><p v-if="(detail.actual.operation as any).objectKey">素材标识：{{ (detail.actual.operation as any).objectKey }}</p><p v-if="(detail.actual.operation as any).members?.length">同步计划：{{ (detail.actual.operation as any).members.join('、') }}</p></div><el-empty v-if="!detail.actual" description="尚无平台回查值" :image-size="50" /><p class="caption">{{ detail.report.profit_note }}</p></template></el-drawer>
  </section>
</template>
<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue';
import { storeToRefs } from 'pinia';
import { useAdvertisingAccountsStore } from '@/stores/advertisingAccounts';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { getMonitoringCenter, getOptimizationHistory, holdAllOptimization, setAccountBudgetLimit, resumeOptimization, recheckOptimization, acknowledgeOptimization, type CenterView, type CenterRow, type HistoryPage, type OptimizationEvent } from '@/features/delivery/optimizationApi';
const route = useRoute(), router = useRouter();
const center = ref<CenterView>(), history = ref<HistoryPage>(), tab = ref(route.query.tab === 'history' ? 'history' : 'plans'), planId = ref(typeof route.query.planId === 'string' ? route.query.planId : '');
const { accountId } = storeToRefs(useAdvertisingAccountsStore());
const state = ref(''), query = ref(''), status = ref(''), range = ref<[string, string]>(), page = ref(1), error = ref(''), busy = ref(false), actionBusy = ref(false), detail = ref<OptimizationEvent>();
const limitsOpen = ref(false), limits = ref<Record<string, number | undefined>>({}), savingLimit = ref('');
const states = [{ value: 'AUTO', label: '自动调控' }, { value: 'OBSERVE', label: '只看建议' }, { value: 'HELD', label: '自动执行已暂停' }, { value: 'ERROR', label: '采集异常' }, { value: 'OFF', label: '未开启' }];
const statuses: Record<string, string> = { RECOMMENDED: '待人工参考', READY: '等待执行检查', EXECUTING: '提交中', SUCCEEDED: '已回查生效', SKIPPED: '未执行', EXPIRED: '已过期', VERIFY_PENDING: '等待平台回查', UNKNOWN: '待核查', ACKNOWLEDGED: '已人工核查', GENERATING: '生成中', REVIEW: '素材待确认发布', SYNCING: '素材同步 / 等待发布条件', PUBLISHING: '生成素材发布核查中', PLATFORM_REVIEW: '等待平台审核', TRIAL: '素材试投中', QUALIFIED: '素材试投达标', FAILED: '未完成', CANCELLED: '已停止', REMOVING: '亏损素材移除中', REMOVED: '亏损素材已移除', LOSS_RETAINED: '达到亏损线需人工处理', UNTESTED: '验证期结束未达标' };
const actualLabels: Record<string, string> = { status: '平台启停', budget: '日预算', roi: '目标回报（ROI）', qcId: '千川计划 ID' };
const time = (v?: string | null) => v ? new Date(v).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }) : '—';
const money = (v?: string | number | null) => v == null ? '—' : Number(v).toFixed(2);
const planLink = (id: string) => ({ path: '/campaigns', query: { plan: id, tab: 'optimization' } });
const proposalText = (e: OptimizationEvent) => { const a = e.proposal; if (!a) return '仅建议'; const name = ({ BUDGET: '日预算', ROI: '目标回报（ROI）', PAUSE: '暂停', ENABLE: '恢复投放', BOOST: '一键起量', MATERIAL_FOLLOW: '素材追投', MATERIAL_GENERATE: '诊断后生成素材', MATERIAL_ADD: '发布生成素材', MATERIAL_REMOVE: '移除素材', PRODUCT_ADD: '新增商品（历史操作）', PRODUCT_REMOVE: '移除商品' })[a.kind]; if (a.kind === 'MATERIAL_GENERATE') return `${name}：${({ video: '视频', image_text: '图文', product_card: '商品卡图片' } as Record<string,string>)[a.strategy?.target || ''] || '待定'} · 商品 ${a.product_id}`; if (a.generation_id) return `${name} · 商品 ${a.product_id}`; return a.budget ? `${name}：¥${a.budget} / ${a.duration}小时${a.entity_id ? ` · 素材 ${a.entity_id}` : ''}` : a.entity_id ? `${name}：${a.entity_id}` : `${name}：${a.expected} → ${a.target}`; };
let disposed = false, revision = 0, timer: ReturnType<typeof setTimeout> | undefined;
async function load() {
  const turn = ++revision; busy.value = true;
  try {
    if (range.value && (Date.parse(range.value[1]) - Date.parse(range.value[0]) > 179 * 86400000 || range.value[1] < range.value[0])) throw new Error('时间区间最长180天');
    const common = { accountId: accountId.value || undefined, pageNum: page.value, pageSize: 20 };
    const next = await getMonitoringCenter({ ...common, state: state.value || undefined, query: query.value || undefined });
    const events = tab.value === 'history' ? await getOptimizationHistory({ ...common, planId: planId.value || undefined, status: status.value || undefined, startDate: range.value?.[0], endDate: range.value?.[1] }) : undefined;
    if (!disposed && turn === revision) { center.value = next; if (events) history.value = events; error.value = ''; }
  } catch (e: any) { if (!disposed && turn === revision) error.value = e.message || '盯盘中心暂不可用，请重试'; }
  finally { if (!disposed && turn === revision) busy.value = false; }
}
function changed() { page.value = 1; void load(); }
watch(accountId, changed);
function openHistory(id: string) { void router.push({ path: '/monitoring', query: { planId: id, tab: 'history' } }); }
function clearPlan() { void router.replace({ path: '/monitoring', query: { tab: 'history' } }); }
watch(() => route.query, value => { planId.value = typeof value.planId === 'string' ? value.planId : ''; tab.value = value.tab === 'history' ? 'history' : 'plans'; changed(); });
async function perform(fn: () => Promise<unknown>) { if (actionBusy.value) return; actionBusy.value = true; try { await fn(); ElMessage.success('操作已完成'); await load(); } catch { /* HTTP layer reports mutation failures. */ } finally { actionBusy.value = false; } }
async function holdAll() { try { await ElMessageBox.confirm('停止全店后续自动执行，仍持续采集与生成建议。已提交的操作会继续回查结果。各计划可单独恢复授权。', '暂停自动调控', { confirmButtonText: '暂停自动执行', cancelButtonText: '取消' }); } catch { return; } await perform(holdAllOptimization); }
async function resume(row: CenterRow) { try { await ElMessageBox.confirm('核查当前平台状态后，恢复该计划已保存范围内的自动调控。', '恢复自动执行授权', { confirmButtonText: '重新授权', cancelButtonText: '取消' }); } catch { return; } await perform(() => resumeOptimization(row.planId, row.version)); }
async function recheck(e: OptimizationEvent) { await perform(() => recheckOptimization(e.id)); }
async function acknowledge(e: OptimizationEvent) { try { await ElMessageBox.confirm('确认已在千川后台核查，并接受平台当前状态。该确认不代表原操作成功；后续需单独恢复自动授权。', '完成人工核查', { confirmButtonText: '确认已核查', cancelButtonText: '取消' }); } catch { return; } await perform(() => acknowledgeOptimization(e.id)); }
function initLimits() { limits.value = Object.fromEntries((center.value?.accounts || []).map(a => [a.accountId, a.budgetLimit])); }
async function saveLimit(id: string) { const value = limits.value[id]; if (value == null) { ElMessage.warning('请填写预算上限'); return; } savingLimit.value = id; try { await setAccountBudgetLimit(id, value); ElMessage.success('账户上限已保存'); await load(); } catch {} finally { savingLimit.value = ''; } }
async function poll() { if (disposed) return; if (!document.hidden && !busy.value && !actionBusy.value) await load(); if (!disposed) timer = setTimeout(poll, 30000); }
onMounted(async () => { await load(); if (!disposed) timer = setTimeout(poll, 30000); });
onBeforeUnmount(() => { disposed = true; revision++; if (timer) clearTimeout(timer); });
</script>
<style scoped>
.monitoring-center{max-width:1560px;margin:auto;color:var(--el-text-color-primary)}.center-heading,.actions,.filters{display:flex;align-items:center;gap:12px;flex-wrap:wrap}.center-heading{justify-content:space-between;margin-bottom:24px}.center-heading h1{font-size:26px;margin:0 0 10px}.center-heading p,.caption,small{font-size:12px;line-height:1.8;color:var(--el-text-color-secondary)}.overview{display:grid;grid-template-columns:repeat(4,1fr);gap:16px;margin:22px 0}.overview>div{padding:22px;background:var(--el-fill-color-light);border:1px solid var(--el-border-color-lighter);border-radius:12px}.overview span{display:block;font-size:13px;color:var(--el-text-color-secondary)}.overview strong{display:block;margin-top:12px;font-size:28px}.filters{margin:4px 0 20px}.filters .el-select{width:200px}.filters .el-input{width:280px}small{display:block;overflow-wrap:anywhere;margin-top:7px}a{color:var(--el-color-primary);text-decoration:none}.el-pagination{margin-top:20px;justify-content:flex-end}.el-form-item .el-button{margin-left:12px}.caption{margin-top:20px}@media(max-width:800px){.overview{grid-template-columns:repeat(2,1fr)}.center-heading h1{font-size:22px}.actions{gap:6px}.actions .el-button{margin-left:0}}@media(max-width:500px){.filters>*{width:100%!important}.overview>div{padding:16px}.overview strong{font-size:22px}.el-pagination{justify-content:flex-start}}
</style>
