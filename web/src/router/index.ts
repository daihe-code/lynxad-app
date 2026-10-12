import { createRouter, createWebHistory } from "vue-router";
import { useUserStore } from "@/stores/user";
import { hasUsableAweme, listAccounts } from "@/features/identity/oauthApi";

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) return savedPosition;
    if (to.path === '/campaigns' && from.path === '/campaigns') return false;
    if (to.hash) {
      return { el: to.hash, behavior: "smooth" };
    }
    return { top: 0 };
  },
  routes: [
    { path: "/", component: () => import("@/pages/landing/Index.vue"), meta: { public: true } },
    { path: "/login", component: () => import("@/pages/login/Login.vue"), meta: { public: true } },
    { path: "/onboarding", component: () => import("@/pages/onboarding/Onboarding.vue"), meta: { auth: true } },
    { path: "/legal/terms", component: () => import("@/pages/legal/Terms.vue"), meta: { public: true } },
    { path: "/legal/privacy", component: () => import("@/pages/legal/Privacy.vue"), meta: { public: true } },
    {
      path: "/campaigns",
      component: () => import("@/layouts/AppLayout.vue"),
      meta: { auth: true },
      children: [
        { path: "", component: () => import("@/pages/campaigns/List.vue") },
        { path: "create", component: () => import("@/pages/campaigns/Create.vue"), meta: { requireAweme: true } },
        { path: "manage/create", component: () => import("@/pages/campaigns/reference/PlanForm.vue"), meta: { requireAweme: true } },
        { path: ":id/analysis", component: () => import("@/pages/campaigns/reference/PlanAnalysis.vue") },
        { path: ":id/edit", component: () => import("@/pages/campaigns/PlanWorkspace.vue"), props: { mode: "edit" } },
        { path: ":id/transfer", component: () => import("@/pages/campaigns/PlanWorkspace.vue"), props: { mode: "transfer" } },
        { path: ":id/result", component: () => import("@/pages/campaigns/reference/PlanResult.vue") },
        { path: ":id/overview", component: () => import("@/pages/campaigns/reference/PlanOverview.vue") },
        { path: ":id", component: () => import("@/pages/campaigns/PlanWorkspace.vue"), props: { mode: "view" } },
      ],
    },
    {
      path: "/authorization",
      component: () => import("@/layouts/AppLayout.vue"),
      meta: { auth: true },
      children: [
        { path: "", component: () => import("@/pages/authorization/Authorization.vue") },
        { path: "authorizing", component: () => import("@/pages/authorization/Authorization.vue") },
      ],
    },
    {
      path: "/oauth",
      component: () => import("@/layouts/AppLayout.vue"),
      meta: { auth: true },
      children: [{ path: "callback", component: () => import("@/pages/authorization/Authorization.vue") }],
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
        return { path: "/authorization", query: { redirect: to.fullPath } };
      }
    }
  }
  return true;
});

export default router;
