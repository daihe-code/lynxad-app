<template>
  <section aria-label="企业资质人工审核">
    <p>请通过人工渠道核对营业执照与企业名称、统一社会信用代码；填写核对依据后作出审核决定。格式校验通过不代表资质已核实。</p>
    <div class="review-toolbar"><el-select v-model="status" aria-label="资质审核状态" @change="page = 1; load()"><el-option label="待人工审核" value="PENDING" /><el-option label="已通过" value="APPROVED" /><el-option label="已驳回" value="REJECTED" /></el-select><el-button :loading="loading" @click="load">刷新审核队列</el-button></div>
    <el-alert v-if="error" :title="error" type="error" :closable="false" />
    <el-table :data="items" v-loading="loading" empty-text="暂无该状态的企业资料">
      <el-table-column prop="companyName" label="企业名称" min-width="180" />
      <el-table-column prop="creditCode" label="统一社会信用代码" min-width="200" />
      <el-table-column prop="tenantId" label="账户ID" min-width="230" />
      <el-table-column label="提交时间" min-width="180"><template #default="{row}">{{ time(row.submittedAt) }}</template></el-table-column>
      <el-table-column prop="reviewReason" label="审核依据 / 驳回原因" min-width="200" />
      <el-table-column label="操作" width="120"><template #default="{row}"><el-button v-if="row.verificationStatus === 'PENDING'" link type="primary" @click="selected = row; reason = ''; reviewError = ''">人工审核</el-button><span v-else>{{ row.verificationStatus === 'APPROVED' ? '已通过' : '已驳回' }}</span></template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="page" :total="total" :page-size="20" layout="prev,pager,next,total" @current-change="load" />
    <el-dialog :model-value="!!selected" title="企业资质人工审核" width="min(600px,94vw)" :close-on-click-modal="false" :show-close="!saving" @close="!saving && (selected = undefined)">
      <p>{{ selected?.companyName }}</p><p>{{ selected?.creditCode }}</p>
      <label>核对依据或驳回原因（必填）<el-input v-model="reason" type="textarea" maxlength="512" show-word-limit :rows="4" aria-label="审核依据或驳回原因" /></label>
      <el-alert v-if="reviewError" :title="reviewError" type="error" :closable="false" />
      <template #footer><el-button :disabled="saving" @click="selected = undefined">取消</el-button><el-button :disabled="saving || !reason.trim()" type="danger" @click="review('REJECTED')">驳回</el-button><el-button :disabled="saving || !reason.trim()" type="primary" @click="review('APPROVED')">核验通过</el-button></template>
    </el-dialog>
  </section>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { getQualifications, reviewQualification, type Qualification } from '@/features/admin/adminApi';
const status = ref('PENDING'), page = ref(1), total = ref(0), items = ref<Qualification[]>([]);
const loading = ref(false), saving = ref(false), error = ref(''), reviewError = ref(''), reason = ref(''), selected = ref<Qualification>();
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false });
let sequence = 0;
async function load() {
  const current = ++sequence; loading.value = true; error.value = '';
  try { const data = await getQualifications(status.value, page.value); if (current === sequence) { items.value = data.records; total.value = data.total; } }
  catch (e) { if (current === sequence) error.value = e instanceof Error ? e.message : '审核队列加载失败'; }
  finally { if (current === sequence) loading.value = false; }
}
async function review(result: 'APPROVED' | 'REJECTED') {
  if (!selected.value || saving.value || !reason.value.trim()) return;
  saving.value = true; reviewError.value = '';
  try { await reviewQualification(selected.value, result, reason.value.trim()); selected.value = undefined; await load(); }
  catch (e) { reviewError.value = e instanceof Error ? e.message : '审核失败，请刷新资料后重试'; }
  finally { saving.value = false; }
}
onMounted(load);
</script>
<style scoped>
.review-toolbar{display:flex;gap:16px;margin:18px 0}.review-toolbar .el-select{width:180px}p{line-height:1.8}.el-pagination{margin-top:20px}
</style>
