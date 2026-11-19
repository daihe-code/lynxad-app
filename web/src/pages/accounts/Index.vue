<template><AccountShell :active="tab"><WorkspaceSettings v-if="tab === 'workspace'" /><WorkspaceTeam v-else-if="tab === 'team'" /><WorkspaceRecharge v-else-if="tab === 'recharge'" :key="user.me?.tenantId" /><AccountAuthorization v-else-if="tab === 'oauth'" /><AccountLogs v-else-if="tab === 'logs'" /><AccountInfo v-else /></AccountShell></template>
<script setup lang="ts">
import { computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useUserStore } from '@/stores/user';
import AccountShell from './AccountShell.vue';
import AccountInfo from './AccountInfo.vue';
import WorkspaceSettings from './WorkspaceSettings.vue';
import WorkspaceTeam from './WorkspaceTeam.vue';
import WorkspaceRecharge from './WorkspaceRecharge.vue';
import AccountAuthorization from './AccountAuthorization.vue';
import AccountLogs from './AccountLogs.vue';
const route = useRoute(), router = useRouter();
const user = useUserStore();
const isOwner = computed(() => user.me?.memberRole === 'OWNER');
const tab = computed(() => {
  const requested = String(route.query.tab);
  if (isOwner.value && ['team', 'recharge'].includes(requested)) return requested;
  return ['workspace', 'oauth', 'logs'].includes(requested) ? requested : 'info';
});
watch([() => route.query.tab, isOwner], ([value, owner]) => {
  if (value === 'billing') void router.replace({ path: '/subscription', query: { ...route.query, tab: undefined } });
  if ((value === 'team' || value === 'recharge') && !owner) void router.replace({ query: { ...route.query, tab: undefined } });
}, { immediate: true });
</script>
