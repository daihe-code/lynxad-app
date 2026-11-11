export const accountNavigation = [
  {key:'workspace',label:'工作空间',description:'',to:'/accounts?tab=workspace',icon:'grid'},
  {key:'info',label:'账户信息',description:'管理企业信息、安全设置与发票信息',to:'/accounts',icon:'user'},
  {key:'team',label:'子账户管理',description:'',to:'/accounts?tab=team',icon:'user'},
  {key:'billing',label:'订阅与账单',description:'查看当前套餐、对比方案与账单记录',to:'/subscription',icon:'card'},
  {key:'oauth',label:'广告投放授权',description:'管理已授权的广告账户与投放权限',to:'/accounts?tab=oauth',icon:'shield'},
  {key:'logs',label:'操作日志',description:'查看账户操作记录，保障投放安全与可追溯',to:'/accounts?tab=logs',icon:'clock'},
];

export function accountNavigationForRole(role?: string, roles?: string[]) {
  return accountNavigation.filter(item => {
    if (item.key === 'team') return role === 'OWNER';
    if (item.key === 'billing') return !role || role === 'OWNER' || role === 'FINANCE' || roles?.includes('FINANCE');
    if (role === 'FINANCE') return ['workspace','billing','info'].includes(item.key);
    if (role === 'VIEWER') return ['workspace','info'].includes(item.key);
    return true;
  });
}
