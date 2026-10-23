import { defineStore } from "pinia";
import { computed, ref } from "vue";
import { initializeAccount, fetchMe, passwordLogin, smsLogin, welcomeStatus, acknowledgeWelcome, type CurrentUser } from "@/features/tenant/authApi";
import { loginLandingPath } from "@/features/identity/oauthApi";

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

  function persist(next: string) {
    token.value = next;
    localStorage.setItem("lynx.token", next);
  }

  async function afterLogin(res: { token: string }) {
    welcomeAcknowledged.value = null;
    persist(res.token);
    me.value = null;
    return loadMe();
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
    let current = await fetchMe();
    if (current.needOnboarding || !current.tenantId) {
      const initialized = await initializeAccount();
      persist(initialized.token);
      welcomeAcknowledged.value = null;
      current = await fetchMe();
      if (current.needOnboarding || !current.tenantId) throw new Error("账户初始化未完成，请重新登录");
    }
    me.value = current;
    return me.value;
  }

  async function entryPath() {
    if (!me.value) await loadMe();
    if (welcomeAcknowledged.value === null) await loadWelcome();
    if (!welcomeAcknowledged.value) return "/welcome";
    return loginLandingPath();
  }

  async function loadWelcome() {
    welcomeAcknowledged.value = (await welcomeStatus()).acknowledged;
  }

  function logout() {
    welcomeAcknowledged.value = null;
    token.value = "";
    me.value = null;
    localStorage.removeItem("lynx.token");
  }

  return {
    welcomeAcknowledged,
    loadWelcome,
    async finishWelcome() { welcomeAcknowledged.value = (await acknowledgeWelcome()).acknowledged; },
    entryPath,
    token,
    me,
    isLoggedIn,
    demoMode,
    trialActive,
    loginBySms,
    loginByPassword,
    loadMe,
    logout,
  };
});
