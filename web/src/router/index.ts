import { createRouter, createWebHistory } from "vue-router";
import { useUserStore } from "@/stores/user";
import { hasUsableAweme, listAccounts } from "@/features/identity/oauthApi";

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to) {
    if (to.hash) {
      return { el: to.hash, behavior: "smooth" };
    }
    return { top: 0 };
  },
  routes: [
    { path: "/", component: () => import("@/pages/landing/Index.vue"), meta: { public: true } },
    { path: "/login", component: () => import("@/pages/login/Login.vue"), meta: { public: true } },
    { path: "/onboarding", component: () => import("@/pages/onboarding/Onboarding.vue"), meta: { auth: true } },
    { path: "/oauth/callback", component: () => import("@/pages/oauth/Callback.vue"), meta: { public: true } },
    { path: "/legal/terms", component: () => import("@/pages/legal/Terms.vue"), meta: { public: true } },
    { path: "/legal/privacy", component: () => import("@/pages/legal/Privacy.vue"), meta: { public: true } },
    {
      path: "/campaigns",
      component: () => import("@/layouts/AppLayout.vue"),
      meta: { auth: true },
      children: [
        { path: "", component: () => import("@/pages/campaigns/List.vue") },
        { path: "create", component: () => import("@/pages/campaigns/Create.vue"), meta: { requireAweme: true } },
        { path: ":id", component: () => import("@/pages/campaigns/Detail.vue") },
      ],
    },
    {
      path: "/creatives",
      component: () => import("@/layouts/AppLayout.vue"),
      meta: { auth: true },
      children: [{ path: "", component: () => import("@/pages/creatives/Index.vue") }],
    },
    {
      path: "/accounts",
      component: () => import("@/layouts/AppLayout.vue"),
      meta: { auth: true },
      children: [{ path: "", component: () => import("@/pages/accounts/Index.vue") }],
    },
    {
      path: "/subscription",
      component: () => import("@/layouts/AppLayout.vue"),
      meta: { auth: true },
      children: [{ path: "", component: () => import("@/pages/subscription/Index.vue") }],
    },
    {
      path: "/analytics",
      component: () => import("@/layouts/AppLayout.vue"),
      meta: { auth: true },
      children: [{ path: "", component: () => import("@/pages/analytics/Index.vue") }],
    },
    {
      path: "/finance",
      component: () => import("@/layouts/AppLayout.vue"),
      meta: { auth: true },
      children: [{ path: "", component: () => import("@/pages/shell/ComingSoon.vue"), props: { title: "财务中心" } }],
    },
    {
      path: "/settings",
      component: () => import("@/layouts/AppLayout.vue"),
      meta: { auth: true },
      children: [{ path: "", component: () => import("@/pages/shell/ComingSoon.vue"), props: { title: "系统设置" } }],
    },
    {
      path: "/admin",
      component: () => import("@/layouts/AppLayout.vue"),
      meta: { auth: true },
      children: [{ path: "", component: () => import("@/pages/shell/ComingSoon.vue"), props: { title: "运营后台" } }],
    },
  ],
});

router.beforeEach(async (to) => {
  const user = useUserStore();
  if (to.meta.public) {
    return true;
  }
  if (to.meta.auth || to.matched.some((r) => r.meta.auth)) {
    if (!user.isLoggedIn) {
      return { path: "/login", query: { redirect: to.fullPath } };
    }
    if (!user.me) {
      try {
        await user.loadMe();
      } catch {
        user.logout();
        return "/login";
      }
    }
    if (user.needOnboarding && to.path !== "/onboarding") {
      return "/onboarding";
    }
    if (to.meta.requireAweme) {
      const accounts = await listAccounts();
      if (!hasUsableAweme(accounts)) {
        return "/accounts";
      }
    }
  }
  return true;
});

export default router;
