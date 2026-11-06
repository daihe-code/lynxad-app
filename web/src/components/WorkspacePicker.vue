<template>
  <el-select v-if="spaces.length > 1" :model-value="user.me?.tenantId" :loading="switching" :disabled="switching" aria-label="切换工作空间" style="width:180px" @change="change">
    <el-option v-for="space in spaces" :key="space.tenantId" :value="space.tenantId" :label="space.name" />
  </el-select>
</template>
<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { listWorkspaces, switchWorkspace, type Workspace } from '@/features/tenant/workspaceApi';
import { useUserStore } from '@/stores/user';
const user = useUserStore(), spaces = ref<Workspace[]>([]), switching = ref(false);
onMounted(async () => { try { spaces.value = await listWorkspaces(); } catch { /* API layer reports the error. */ } });
async function change(id: string) {
  if (id === user.me?.tenantId || switching.value) return;
  switching.value = true;
  try {
    const session = await switchWorkspace(id);
    localStorage.setItem('lynx.token', session.token);
    // Discard page/query/store state together; running jobs retain their original scope.
    window.location.assign('/campaigns');
  } catch { switching.value = false; }
}
</script>
