<template>
  <section class="acct-card" :aria-busy="loading">
    <div class="lgbar">
      <select class="account-log-control" aria-label="按操作类型筛选" :value="kind" @change="change({ kind: value($event), page: undefined })"><option value="">全部类型</option><option v-for="item in kinds" :key="item.key" :value="item.key">{{ item.label }}</option></select>
      <input type="date" class="account-log-control" :value="from" :max="to || undefined" aria-label="起始日期" @change="change({ from: value($event), page: undefined })" /><span class="lgbar__sep">至</span><input type="date" class="account-log-control" :value="to" :min="from || undefined" aria-label="结束日期" @change="change({ to: value($event), page: undefined })" />
      <div class="lsearch account-log-search"><AccountIcon class="lsearch__ic" name="search" /><input v-model="keyword" class="lsearch__input" type="search" placeholder="搜索操作内容..." aria-label="搜索操作内容" maxlength="200" @input="search" @keydown.enter.prevent="flushSearch" /></div>
      <div class="lgbar__r"><button class="ltool__btn" :disabled="exporting || loading || !!error || !total" @click="exportLogs"><AccountIcon name="download" />{{ exporting ? '导出中…' : '导出日志' }}</button></div>
    </div>
    <div class="mt-[18px] overflow-x-auto"><table class="lgtable"><thead><tr><th scope="col">操作时间</th><th scope="col">操作类型</th><th scope="col">操作内容</th><th scope="col">操作人</th><th scope="col">IP 地址</th></tr></thead><tbody><tr v-for="row in rows" :key="row.id"><td class="lgtable__time account-nowrap">{{ time(row.createdAt) }}</td><td><span class="lg" :class="`lg--${itemFor(row.actionType).key}`"><AccountIcon :name="itemFor(row.actionType).icon" />{{ itemFor(row.actionType).label }}</span></td><td>{{ row.objectLabel }}</td><td>{{ row.actorName || '—' }}</td><td class="lgtable__ip account-nowrap">{{ row.ipMasked || '—' }}</td></tr></tbody></table></div>
    <div v-if="loading" class="nodata" role="status">正在加载操作日志…</div>
    <div v-else-if="error" class="nodata"><p class="account-error" role="alert">{{ error }}</p><button class="obtn mt-[18px]" @click="load">重新加载</button></div>
    <div v-else-if="!rows.length" class="nodata"><p class="nodata__t">没有符合条件的操作记录</p><p class="nodata__d">调整类型、日期范围或搜索关键词后重试</p><button class="obtn mt-[18px]" @click="reset">重置筛选</button></div>
    <div class="acct-pager"><p class="acct-pager__info">共 <b>{{ total }}</b> 条记录，每页 20 条</p><nav class="acct-pager__l" aria-label="日志分页"><button class="acct-pager__b" aria-label="上一页" :disabled="page === 1 || loading" @click="go(page - 1)"><AccountIcon name="chevron-right" class="account-chevron-left" /></button><template v-for="(p, index) in pages" :key="`${p}-${index}`"><span v-if="p === '…'">…</span><button v-else class="acct-pager__b" :aria-current="page === p ? 'page' : undefined" :disabled="loading" @click="go(Number(p))">{{ p }}</button></template><button class="acct-pager__b" aria-label="下一页" :disabled="page >= pageCount || loading" @click="go(page + 1)"><AccountIcon name="chevron-right" /></button></nav></div>
  </section>
</template>
<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { listAuditRecords, type AuditRecord, type AuditQuery } from '@/features/audit/auditApi';
import AccountIcon from './AccountIcon.vue';
import { csvDownload, time } from './accountFormat';
const route = useRoute(), router = useRouter();
const kinds = [{ key: 'plan', type: 'PLAN', label: '投放计划', icon: 'tune' }, { key: 'login', type: 'LOGIN', label: '登录', icon: 'arrow-right' }, { key: 'asset', type: 'CREATIVE', label: '素材管理', icon: 'image' }, { key: 'auth', type: 'OAUTH', label: '授权管理', icon: 'shield' }, { key: 'acct', type: 'ACCOUNT', label: '账户设置', icon: 'user' }, { key: 'sys', type: 'SYSTEM_NOTICE', label: '系统通知', icon: 'info-line' }];
const kind = computed(() => kinds.some(k => k.key === route.query.kind) ? String(route.query.kind) : '');
function validDate(value: unknown) { return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(new Date(value + 'T00:00:00').getTime()) ? value : ''; }
const from = computed(() => validDate(route.query.from)), to = computed(() => validDate(route.query.to));
const page = computed(() => Math.min(100000, Math.max(1, Number.parseInt(String(route.query.page || 1), 10) || 1)));
const rows = ref<AuditRecord[]>([]), total = ref(0), loading = ref(false), error = ref(''), exporting = ref(false), keyword = ref(String(route.query.kw || ''));
const pageCount = computed(() => Math.max(1, Math.ceil(total.value / 20)));
const pages = computed<(number | string)[]>(() => { const entries = Array.from(new Set([1, pageCount.value, page.value - 1, page.value, page.value + 1])).filter(n => n >= 1 && n <= pageCount.value).sort((a, b) => a - b); const result: (number | string)[] = []; entries.forEach((n, i) => { if (i && n - entries[i - 1]! > 1)
    result.push('…'); result.push(n); }); return result; });
let sequence = 0, searchTimer: ReturnType<typeof setTimeout> | undefined;
function value(event: Event) { return (event.target as HTMLInputElement).value; }
function itemFor(type: string) { return kinds.find(k => k.type === type) || { key: 'sys', label: type, icon: 'info-line' }; }
function query(): AuditQuery { return { actionType: kinds.find(k => k.key === kind.value)?.type, from: from.value ? new Date(from.value + 'T00:00:00').toISOString() : undefined, to: to.value ? new Date(to.value + 'T23:59:59.999').toISOString() : undefined, q: typeof route.query.kw === 'string' ? route.query.kw.trim() : undefined }; }
function change(patch: Record<string, string | undefined>, replace = false) { void router[replace ? 'replace' : 'push']({ query: { ...route.query, ...patch, tab: 'logs' } }); }
function go(p: number) { change({ page: p === 1 ? undefined : String(p) }); }
function reset() { void router.push({ path: '/accounts', query: { tab: 'logs' } }); }
function search() { if (searchTimer)
    clearTimeout(searchTimer); searchTimer = setTimeout(flushSearch, 300); }
function flushSearch() { if (searchTimer)
    clearTimeout(searchTimer); change({ kw: keyword.value.trim() || undefined, page: undefined }, true); }
async function load() {
    const id = ++sequence;
    loading.value = true;
    error.value = '';
    rows.value = [];
    if (from.value && to.value && from.value > to.value) {
        error.value = '结束日期不能早于起始日期';
        total.value = 0;
        loading.value = false;
        return;
    }
    try {
        const data = await listAuditRecords({ ...query(), pageNum: page.value, pageSize: 20 });
        if (id !== sequence)
            return;
        rows.value = data.list;
        total.value = data.total;
        if (page.value > pageCount.value)
            go(pageCount.value);
    }
    catch {
        if (id === sequence) {
            error.value = '操作日志加载失败，请重试';
            total.value = 0;
        }
    }
    finally {
        if (id === sequence)
            loading.value = false;
    }
}
async function exportLogs() {
    if (exporting.value || !total.value)
        return;
    exporting.value = true;
    const filter = query();
    try {
        const first = await listAuditRecords({ ...filter, pageNum: 1, pageSize: 100 });
        const records = [...first.list];
        for (let p = 2; p <= Math.ceil(first.total / 100); p++) {
            const data = await listAuditRecords({ ...filter, pageNum: p, pageSize: 100 });
            records.push(...data.list);
        }
        const unique = Array.from(new Map(records.map(row => [row.id, row])).values());
        if (!unique.length) {
            ElMessage.info('暂无可导出记录');
            return;
        }
        csvDownload('操作日志.csv', [['操作时间', '操作类型', '操作内容', '操作人', 'IP地址'], ...unique.map(r => [time(r.createdAt), itemFor(r.actionType).label, r.objectLabel, r.actorName, r.ipMasked])]);
        ElMessage.success(`已导出 ${unique.length} 条记录`);
    }
    catch {
        ElMessage.error('日志导出失败，请重试');
    }
    finally {
        exporting.value = false;
    }
}
watch(() => [route.query.kind, route.query.from, route.query.to, route.query.kw, route.query.page], () => { keyword.value = String(route.query.kw || ''); void load(); }, { immediate: true });
onUnmounted(() => { ++sequence; if (searchTimer)
    clearTimeout(searchTimer); });
</script>
