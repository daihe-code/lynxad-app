<template><AccountShell :active="tab"><AccountAuthorization v-if="tab === 'oauth'" /><AccountLogs v-else-if="tab === 'logs'" /><AccountInfo v-else /></AccountShell></template>
<script setup lang="ts">
import { computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AccountShell from './AccountShell.vue';
import AccountInfo from './AccountInfo.vue';
import AccountAuthorization from './AccountAuthorization.vue';
import AccountLogs from './AccountLogs.vue';
const route = useRoute(), router = useRouter();
const tab = computed(() => ['oauth', 'logs'].includes(String(route.query.tab)) ? String(route.query.tab) : 'info');
watch(() => route.query.tab, value => { if (value === 'billing')
    void router.replace({ path: '/subscription', query: { ...route.query, tab: undefined } }); }, { immediate: true });
</script>
