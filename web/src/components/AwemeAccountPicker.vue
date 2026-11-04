<template>
  <div class="ad-account aweme-account-picker">
    <div class="ad-account__card" :class="{ 'aweme-account-picker--switchable': multiple, 'aweme-account-picker--disabled': disabled && multiple }">
      <AwemeAccountSummary :account="account" :chips="chips" />
      <svg v-if="multiple" class="aweme-account-picker__arrow" viewBox="0 0 24 24" aria-hidden="true"><path d="m6 9 6 6 6-6" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" /></svg>
      <select v-if="multiple" class="aweme-account-picker__select" :value="account.key" :disabled="disabled" aria-label="选择抖音号" @change="change(($event.target as HTMLSelectElement).value)">
        <option v-for="option in accounts" :key="option.key" :value="option.key">{{ option.awemeName }} · {{ option.awemeId }}</option>
      </select>
    </div>
    <p class="ad-account__hint">{{ multiple ? '下拉切换需要投放广告的抖音号' : '当前投放抖音号' }}</p>
  </div>
</template>
<script setup lang="ts">
import { computed } from 'vue';
import AwemeAccountSummary from './AwemeAccountSummary.vue';
const props = withDefaults(defineProps<{
  account: { key?: string; awemeName: string; awemeId: string; avatar?: string | null };
  accounts?: { key: string; awemeName: string; awemeId: string }[];
  chips?: string[];
  disabled?: boolean;
}>(), { accounts: () => [], chips: () => [] });
const emit = defineEmits<{ change: [key: string] }>();
const multiple = computed(() => props.accounts.length > 1);
function change(key: string) {
  if (!props.disabled && key !== props.account.key && props.accounts.some(option => option.key === key)) emit('change', key);
}
</script>
<style scoped>
.ad-account.aweme-account-picker{display:block!important;flex:0 0 360px;width:360px;max-width:100%}
.aweme-account-picker .ad-account__card{position:relative;box-sizing:border-box;padding:16px;border:1px solid #ffffff30;border-radius:18px;background:#ffffff24;color:#fff}
.aweme-account-picker--switchable{padding-right:48px!important}
.aweme-account-picker--switchable:focus-within{outline:2px solid #fff;outline-offset:3px}
.aweme-account-picker--disabled{opacity:.65}
.aweme-account-picker__arrow{position:absolute;right:16px;top:50%;width:20px;height:20px;transform:translateY(-50%);color:#fff;pointer-events:none}
.aweme-account-picker__select{position:absolute;inset:0;width:100%;height:100%;opacity:0;cursor:pointer;color:#1a1b2e;background:#fff}
.aweme-account-picker__select:disabled{cursor:not-allowed}
.aweme-account-picker .ad-account__hint{margin-top:10px;text-align:right;font-size:12px;line-height:18px;color:#ffffffb3}
@media(max-width:639px){.ad-account.aweme-account-picker{flex:0 1 auto;width:100%}}
</style>
