<template>
  <AccountShell active="billing">
    <SubscriptionBilling />
  </AccountShell>
</template>

<script setup lang="ts">
import AccountShell from "@/pages/accounts/AccountShell.vue";
import SubscriptionBilling from "./SubscriptionBilling.vue";
</script>
