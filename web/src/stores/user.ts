import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { completeOnboarding, fetchMe, passwordLogin, smsLogin, welcomeStatus, acknowledgeWelcome, type CurrentUser, type OnboardingPayload } from "@/features/tenant/authApi";

export const useUserStore = defineStore("user", () => {
  const token = ref(localStorage.getItem("lynx.token") || "");
  const me = ref<CurrentUser | null>(null);
  const welcomeAcknowledged = ref<boolean | null>(null);

  const isLoggedIn = computed(() => Boolean(token.value));
  const demoMode = computed(() => Boolean(me.value?.demoMode));
  const trialActive = computed(() => {
    if (me.value?.trialActive) return true;
    const st = me.value?.subscriptionStatus;
    return st === "ACTIVE" || st === "PENDING_DOWNGRADE";
  });
  const needOnboarding = computed(() => Boolean(me.value?.needOnboarding));

  function persist(next: string) {
    token.value = next;
    localStorage.setItem("lynx.token", next);
  }

  async function afterLogin(res: { token: string }) {
    welcomeAcknowledged.value = null;
    persist(res.token);
    me.value = await fetchMe();
    return me.value;
  }

  async function loginBySms(phone: string, code: string) {
    return afterLogin(await smsLogin(phone, code));
  }

  async function loginByPassword(phone: string, password: string) {
    return afterLogin(await passwordLogin(phone, password));
  }

  async function loadMe() {
    if (!token.value) {
      return null;
    }
    me.value = await fetchMe();
    return me.value;
  }

  async function onboard(payload: OnboardingPayload) {
    me.value = await completeOnboarding(payload);
    return me.value;
  }

  function logout() {
    welcomeAcknowledged.value = null;
    token.value = "";
    me.value = null;
    localStorage.removeItem("lynx.token");
  }

  return {
    welcomeAcknowledged,
    async loadWelcome() { welcomeAcknowledged.value = (await welcomeStatus()).acknowledged; },
    async finishWelcome() { welcomeAcknowledged.value = (await acknowledgeWelcome()).acknowledged; },
    token,
    me,
    isLoggedIn,
    demoMode,
    trialActive,
    needOnboarding,
    loginBySms,
    loginByPassword,
    loadMe,
    onboard,
    logout,
  };
});
