<template>
  <div class="plan-guarantee">
    <h3>保障历史</h3>
    <el-alert v-if="guarantee?.warning" :title="guarantee.warning" type="warning" :closable="false" show-icon />
    <p class="guarantee-explanation">{{ guarantee?.reason || '平台暂未返回保障说明。' }} <a v-if="safeUrl(guarantee?.url)" :href="guarantee?.url" target="_blank" rel="noopener noreferrer">查看平台保障规则 ↗</a></p>
    <el-table :data="guarantee ? [guarantee] : []" empty-text="暂无保障信息">
      <el-table-column label="统计周期" min-width="195"><template #default>{{ range[0] }} ～ {{ range[1] }}</template></el-table-column>
      <el-table-column label="当前保障状态" min-width="155"><template #default="{ row }"><el-tag :type="row.status === 'IN_EFFECT' ? 'success' : 'info'">{{ guaranteeLabel(row.status) }}</el-tag></template></el-table-column>
      <el-table-column label="日消耗" width="150"><template #default><span v-if="spending" class="spending-total">共 {{ yuan(spending.totalCost) }}</span><el-button link type="primary" @click="openCalendar">查看详情</el-button></template></el-table-column>
      <el-table-column label="说明" min-width="260"><template #default="{ row }">{{ row.reason || '平台未提供' }}</template></el-table-column>
    </el-table>
    <p class="guarantee-note">当前接口未返回保障周期和每日有效投放判定，日消耗按所选统计周期展示；有效投放结果以千川后台为准。</p>
    <el-collapse class="guarantee-observations"><el-collapse-item title="保障状态同步记录" name="observations">
      <el-table :data="guarantee?.history.records || []" empty-text="所选区间暂无保障状态同步记录"><el-table-column label="同步时间" width="180"><template #default="{ row }">{{ timeText(row.time) }}</template></el-table-column><el-table-column label="保障状态" min-width="160"><template #default="{ row }">{{ guaranteeLabel(row.status) }}</template></el-table-column><el-table-column prop="reason" label="平台说明" min-width="200" /></el-table>
      <el-pagination v-if="guarantee && guarantee.history.total > 20" :current-page="page" :total="guarantee.history.total" :page-size="20" layout="total, prev, pager, next" @current-change="emit('page', $event)" />
      <p class="guarantee-note">记录坤境实际同步到的保障状态变化，从功能启用后积累。</p>
    </el-collapse-item></el-collapse>
    <el-dialog v-model="calendarOpen" title="日消耗日历" width="min(800px, calc(100vw - 24px))" class="guarantee-calendar-dialog" append-to-body @closed="closeCalendar">
      <div class="spending-calendar" v-loading="loading">
        <div class="calendar-summary"><span>{{ range[0] }} ～ {{ range[1] }}</span><strong>区间消耗 {{ spending ? yuan(spending.totalCost) : '—' }}</strong></div>
        <div v-if="error" class="calendar-error" role="alert">{{ error }} <el-button @click="loadSpending">重新加载</el-button></div>
        <el-alert v-if="spending?.warning" :title="spending.warning" type="warning" :closable="false" show-icon />
        <div class="calendar-controls"><el-button :disabled="!canPrevious" :icon="ArrowLeft" aria-label="上一个月" @click="shiftMonth(-1)" /><h4>{{ monthLabel }}</h4><el-button :disabled="!canNext" :icon="ArrowRight" aria-label="下一个月" @click="shiftMonth(1)" /><el-button class="back-today" @click="resetMonth">{{ todayInRange ? '回到今天' : '回到本期' }}</el-button></div>
        <div class="calendar-week"><span v-for="day in weekdays" :key="day">{{ day }}</span></div>
        <div class="calendar-days"><button v-for="day in cells" :key="day.date" type="button" :disabled="!day.inRange || !day.inMonth || loading || !!error" :class="{ 'in-period': day.inRange && day.inMonth, selected: day.date === selectedDate, 'out-month': !day.inMonth }" :aria-pressed="day.date === selectedDate" :aria-label="`${day.date} 日消耗 ${day.cost == null ? '未提供' : yuan(day.cost)}`" @click="selectedDate = day.date"><span class="day-number">{{ day.day }}</span><span v-if="day.inRange && day.inMonth" class="day-cost">消耗 {{ yuan(day.cost) }}</span></button></div>
        <p class="selected-spending">{{ selectedDate }} · 日消耗 <strong>{{ yuan(selectedCost) }}</strong><span>有效投放判定：平台未提供</span></p>
        <p class="guarantee-note">「—」表示平台未返回该日消耗。暂停计划保留已产生的消耗；有消耗不等于完成有效投放。</p>
      </div>
    </el-dialog>
  </div>
</template>
<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { ArrowLeft, ArrowRight } from '@element-plus/icons-vue';
import { insightDailySpending, type DailySpending, type InsightGuarantee } from '@/features/delivery/planInsightsApi';
import { shanghaiToday, type StatisticsRange } from '@/features/delivery/statisticsRange';
import { GUARANTEE_STATUS, yuan } from '@/utils/labels';
const props = defineProps<{ planId: string; range: StatisticsRange; guarantee?: InsightGuarantee; page: number }>();
const emit = defineEmits<{ page: [value: number] }>();
const guaranteeLabel = (value?: string) => value ? GUARANTEE_STATUS[value] || value : '平台未返回保障状态';
const safeUrl = (value?: string) => !!value && /^https?:\/\//i.test(value);
function timeText(value: string) { return new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false }); }
const calendarOpen = ref(false), loading = ref(false), error = ref(''), spending = ref<DailySpending>();
const selectedDate = ref(props.range[1]), month = ref(`${props.range[1].slice(0, 7)}-01`);
const weekdays = ['一', '二', '三', '四', '五', '六', '日'];
const iso = (date: Date) => date.toISOString().slice(0, 10);
const todayInRange = computed(() => props.range[0] <= shanghaiToday() && props.range[1] >= shanghaiToday());
const monthLabel = computed(() => `${Number(month.value.slice(0, 4))}年 ${Number(month.value.slice(5, 7))}月`);
const canPrevious = computed(() => month.value.slice(0, 7) > props.range[0].slice(0, 7));
const canNext = computed(() => month.value.slice(0, 7) < props.range[1].slice(0, 7));
const costs = computed(() => new Map(spending.value?.days.map(day => [day.date, day.cost]) || []));
const selectedCost = computed(() => costs.value.get(selectedDate.value));
const cells = computed(() => {
  const first = new Date(`${month.value}T00:00:00Z`), start = new Date(first);
  start.setUTCDate(1 - ((first.getUTCDay() + 6) % 7));
  const last = new Date(first); last.setUTCMonth(last.getUTCMonth() + 1, 0);
  const count = Math.ceil((((first.getUTCDay() + 6) % 7) + last.getUTCDate()) / 7) * 7;
  return Array.from({ length: count }, (_, i) => { const day = new Date(start); day.setUTCDate(start.getUTCDate() + i); const date = iso(day); return { date, day: day.getUTCDate(), inMonth: date.slice(0, 7) === month.value.slice(0, 7), inRange: date >= props.range[0] && date <= props.range[1], cost: costs.value.get(date) }; });
});
function shiftMonth(amount: number) { const date = new Date(`${month.value}T00:00:00Z`); date.setUTCMonth(date.getUTCMonth() + amount); month.value = iso(date); }
function resetMonth() { selectedDate.value = todayInRange.value ? shanghaiToday() : props.range[1]; month.value = `${selectedDate.value.slice(0, 7)}-01`; }
let request = 0, disposed = false;
async function loadSpending() {
  const turn = ++request; loading.value = true; error.value = ''; spending.value = undefined;
  try { const value = await insightDailySpending(props.planId, [...props.range]); if (!disposed && turn === request) spending.value = value; }
  catch (e: any) { if (!disposed && turn === request) error.value = e?.response?.data?.message || e?.message || '日消耗查询失败，请重试'; }
  finally { if (!disposed && turn === request) loading.value = false; }
}
function openCalendar() { calendarOpen.value = true; resetMonth(); void loadSpending(); }
function closeCalendar() { request++; loading.value = false; }
watch(() => [props.planId, ...props.range, props.guarantee?.fetchedAt], () => { request++; spending.value = undefined; error.value = ''; loading.value = false; calendarOpen.value = false; resetMonth(); });
onBeforeUnmount(() => { disposed = true; request++; });
</script>
<style scoped>
.plan-guarantee h3{font-size:16px;margin:0 0 18px}.guarantee-explanation{font-size:12px;line-height:1.9;color:#606780;margin:16px 0}.guarantee-explanation a{color:#5b57ff;text-decoration:none}.guarantee-note{font-size:12px;color:#8b90a5;line-height:1.8}.spending-total{display:block;font-size:12px}.guarantee-observations{margin-top:24px;border:0}.calendar-summary{display:flex;justify-content:space-between;gap:12px;padding:14px 18px;background:#edf5ff;border-radius:8px;font-size:13px;flex-wrap:wrap}.calendar-controls{display:flex;align-items:center;justify-content:center;gap:20px;margin:22px 0 16px;position:relative}.calendar-controls h4{margin:0;font-size:15px}.calendar-controls .el-button{margin:0}.back-today{position:absolute;right:0}.calendar-week,.calendar-days{display:grid;grid-template-columns:repeat(7,minmax(0,1fr));gap:6px}.calendar-week{padding-bottom:12px;text-align:center}.calendar-days button{min-width:0;min-height:78px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:12px;border:1px solid transparent;border-radius:5px;background:#f7f8fa;color:#40455c;font:inherit;font-size:13px}.calendar-days button.in-period{background:#edf5ff}.calendar-days button:not(:disabled){cursor:pointer}.calendar-days button.selected{border-color:#5b57ff;color:#5b57ff}.calendar-days button.out-month{color:#b2b5c2;background:#fff}.day-cost{font-size:11px;white-space:nowrap}.selected-spending{margin:20px 0 12px;font-size:13px}.selected-spending>span{display:block;color:#8b90a5;margin-top:8px;font-size:12px}.calendar-error{padding:16px;color:#cc5151;background:#fff5f5;display:flex;align-items:center;justify-content:space-between;gap:16px;margin-top:16px}.calendar-days button:focus-visible{outline:2px solid #5b57ff;outline-offset:2px}@media(max-width:600px){.calendar-summary{padding:12px;font-size:12px}.calendar-controls{gap:10px;margin:16px 0}.back-today{position:static;font-size:12px;padding:8px}.calendar-days,.calendar-week{gap:3px}.calendar-days button{min-height:62px;gap:8px;font-size:12px}.day-cost{font-size:9px}.selected-spending{font-size:12px}.calendar-error{flex-direction:column;align-items:flex-start}}
</style>
