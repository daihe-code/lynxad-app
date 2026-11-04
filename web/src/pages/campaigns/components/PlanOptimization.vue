<template>
  <PlanOptimizationRules v-if="editing" :plan-id="planId" :plan="plan" :account-name="account?.accountName" @close="cancelEdit" @saved="savedRules" />
  <PlanOptimizationDetails v-else :key="revision" :plan-id="planId" :plan="plan" :account="account" @edit="editing = true" @close="emit('close')" @saved="emit('saved')" @data="emit('data', $event)" />
</template>
<script setup lang="ts">
import { ref } from 'vue';
import type { PlanListVo } from '@/features/delivery/deliveryApi';
import type { AccountVo } from '@/features/identity/oauthApi';
import PlanOptimizationRules from './PlanOptimizationRules.vue';
import PlanOptimizationDetails from './PlanOptimizationDetails.vue';
const props = defineProps<{ planId: string; plan?: PlanListVo; account?: AccountVo; configured?: boolean }>();
const emit = defineEmits<{ saved: []; close: []; data: [tab: string] }>();
const editing = ref(!props.configured), revision = ref(0);
function cancelEdit() { if (props.configured || revision.value) editing.value = false; else emit('close'); }
function savedRules() { emit('saved'); revision.value++; editing.value = false; }
</script>
