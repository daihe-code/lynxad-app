<template>
  <div class="blacklist-actions">
    <el-input v-model="searchPhone" placeholder="搜索手机号" aria-label="搜索黑名单手机号" maxlength="11"
      clearable @keyup.enter="search" @clear="search" />
    <el-button @click="search">搜索</el-button>
    <el-button type="primary" class="add-phone" @click="openAdd">添加黑名单</el-button>
  </div>
  <el-alert v-if="loadError" :title="loadError" type="error" :closable="false" />
  <el-table :data="entries" v-loading="loading" empty-text="暂无黑名单记录">
    <el-table-column prop="phone" label="手机号" min-width="150" />
    <el-table-column prop="reason" label="拉黑原因" min-width="240" />
    <el-table-column label="拉黑时间" min-width="180">
      <template #default="{ row }">{{ time(row.updatedAt) }}</template>
    </el-table-column>
    <el-table-column label="操作" min-width="110">
      <template #default="{ row }">
        <el-button link type="primary" :disabled="!!unblocking" @click="remove(row.phone)">解除拉黑</el-button>
      </template>
    </el-table-column>
  </el-table>
  <el-pagination v-model:current-page="page" class="blacklist-pages" background layout="total, prev, pager, next"
    :total="total" :page-size="20" @current-change="load" />
  <el-dialog v-model="adding" title="添加黑名单" width="min(440px, 95vw)" :close-on-click-modal="false"
    :show-close="!saving" :close-on-press-escape="!saving">
    <el-form label-position="top" @submit.prevent="add">
      <el-form-item label="手机号" :error="phoneError">
        <el-input v-model="phone" maxlength="11" inputmode="numeric" :disabled="saving" @input="phoneError = ''" />
      </el-form-item>
      <el-form-item label="拉黑原因" :error="reasonError">
        <el-input v-model="reason" type="textarea" :rows="2" maxlength="300" :disabled="saving" @input="reasonError = ''" />
      </el-form-item>
    </el-form>
    <el-alert v-if="saveError" :title="saveError" type="error" :closable="false" />
    <template #footer>
      <el-button :disabled="saving" @click="adding = false">取消</el-button>
      <el-button type="danger" :loading="saving" @click="add">确认拉黑</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { blockPhone, getPhoneBlacklist, unblockPhone, type PhoneBlacklistEntry } from '@/features/admin/adminApi';

const entries = ref<PhoneBlacklistEntry[]>([]);
const page = ref(1), total = ref(0), searchPhone = ref(''), loading = ref(false), loadError = ref('');
const adding = ref(false), phone = ref(''), reason = ref(''), saving = ref(false);
const phoneError = ref(''), reasonError = ref(''), saveError = ref(''), unblocking = ref('');
const time = (value: string) => new Date(value).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false });
const message = (error: unknown) => error instanceof Error ? error.message : '操作失败，请重试';

let loadId = 0;
async function load() {
  const id = ++loadId;
  loading.value = true;
  loadError.value = '';
  try {
    const result = await getPhoneBlacklist(searchPhone.value.trim(), page.value);
    if (id !== loadId) return;
    entries.value = result.records;
    total.value = result.total;
    const lastPage = Math.max(1, Math.ceil(result.total / result.pageSize));
    if (page.value > lastPage) page.value = lastPage;
  } catch (error) {
    if (id === loadId) loadError.value = message(error);
  } finally {
    if (id === loadId) loading.value = false;
  }
}

function search() {
  if (page.value === 1) void load();
  else page.value = 1;
}

function openAdd() {
  phone.value = reason.value = phoneError.value = reasonError.value = saveError.value = '';
  adding.value = true;
}

async function add() {
  if (saving.value) return;
  phoneError.value = /^1[3-9]\d{9}$/.test(phone.value.trim()) ? '' : '请输入正确的手机号';
  reasonError.value = reason.value.trim().length < 2 ? '请填写至少2个字的拉黑原因' : '';
  if (phoneError.value || reasonError.value) return;
  saving.value = true;
  saveError.value = '';
  try {
    await blockPhone(phone.value.trim(), reason.value.trim());
    adding.value = false;
    ElMessage.success('已加入黑名单');
    searchPhone.value = '';
    search();
  } catch (error) {
    saveError.value = message(error);
  } finally {
    saving.value = false;
  }
}

async function remove(number: string) {
  if (unblocking.value) return;
  unblocking.value = number;
  try {
    await ElMessageBox.confirm(`解除对 ${number} 的手机号限制？`, '解除拉黑', { confirmButtonText: '解除拉黑', cancelButtonText: '取消' });
    await unblockPhone(number);
    ElMessage.success('已解除拉黑');
    await load();
  } catch (error) {
    if (error instanceof Error) ElMessage.error(message(error));
  } finally {
    unblocking.value = '';
  }
}

onMounted(load);
</script>

<style scoped>
.blacklist-actions { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; margin: 6px 0 20px; }
.blacklist-actions .el-input { width: min(300px, 100%); }
.add-phone { margin-left: auto; }
.blacklist-pages { justify-content: flex-end; margin-top: 24px; overflow: auto; }
</style>
