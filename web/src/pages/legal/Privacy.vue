<template>
  <div class="legal">
    <router-link to="/">← 返回</router-link>
    <h1>隐私政策</h1>
    <p>灵投仅处理投放所需的账户、商品、计划与报表数据。</p>
    <ul>
      <li>手机号用于登录与成员邀请，验证码在服务端校验后失效。</li>
      <li>千川访问令牌仅加密保存在服务端，浏览器不会持有 access_token。</li>
      <li>不采集消费者个人信息，不出售数据。</li>
    </ul>
    <p>如需注销演示账号，请联系实施同学清理租户数据。</p>
  </div>
</template>

<style scoped>
.legal {
  max-width: 760px;
  margin: 40px auto;
  background: #fff;
  padding: 32px;
  border-radius: 16px;
}
</style>
