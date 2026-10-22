<template>
  <span class="aweme-account-summary">
    <AuthorizationAvatar class="aweme-account-summary__avatar" :src="account.avatar" :name="account.awemeName" />
    <span class="aweme-account-summary__details">
      <span class="aweme-account-summary__name" :title="account.awemeName">{{ account.awemeName }}</span>
      <span class="aweme-account-summary__id" :title="`抖音号ID：${account.awemeId}`">抖音号ID：{{ account.awemeId }}</span>
      <span v-if="chips.length" class="aweme-account-summary__chips">
        <span v-for="(chip, i) in chips" :key="chip" class="aweme-account-summary__chip" :class="{ 'aweme-account-summary__chip--warn': i > 0 }">{{ chip }}</span>
      </span>
    </span>
  </span>
</template>

<script setup lang="ts">
import AuthorizationAvatar from '@/pages/accounts/AuthorizationAvatar.vue';
withDefaults(defineProps<{
  account: { awemeName: string; awemeId: string; avatar?: string | null };
  chips?: string[];
}>(), { chips: () => [] });
</script>

<style scoped>
.aweme-account-summary{display:flex;align-items:center;gap:12px;min-width:0;text-align:left}
.aweme-account-summary__avatar{box-sizing:border-box;width:44px;height:44px;border-radius:50%;border:1px solid #ffffff4d}
.aweme-account-summary__details{flex:1;min-width:0}
.aweme-account-summary__name,.aweme-account-summary__id{display:block;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.aweme-account-summary__name{font-size:16px;font-weight:600;line-height:20px;color:#fff}
.aweme-account-summary__id{margin-top:4px;font-size:12px;line-height:16px;color:#ffffffb3}
.aweme-account-summary__chips{display:flex;flex-wrap:wrap;gap:6px;margin-top:7px}
.aweme-account-summary__chip{display:inline-flex;align-items:center;min-height:22px;padding:0 10px;border-radius:9999px;background:#fff;color:#575cf6;font-size:12px;font-weight:500;line-height:18px}
.aweme-account-summary__chip--warn{background:#ee9b2e;color:#fff}
</style>
