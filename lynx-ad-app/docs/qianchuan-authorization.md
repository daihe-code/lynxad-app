# 巨量千川授权联调

入口：`/accounts?tab=oauth` → 添加新授权 → 前往授权。首次登录后的 `/authorization` 也使用同一后端授权流程。

## 配置

应用 Secret 只保存在仓库外的受限文件或部署环境变量中。后端启动时将应用配置加密同步到 `oauth_application`；浏览器不会收到 Secret 或 Token。

```properties
lynx.ad.oauth.qianchuan.mode=live
lynx.ad.mock.qianchuan=false
lynx.ad.oauth.qianchuan.app-id=<千川应用 ID>
lynx.ad.oauth.qianchuan.app-secret=<千川应用 Secret>
lynx.ad.oauth.qianchuan.redirect-uri=<平台注册的 HTTPS 回调地址>
lynx.ad.oauth.web-callback-uri=http://localhost:5174/oauth/callback
```

通过 `--spring.config.additional-location=optional:file:<仓库外配置文件>` 加载。生产环境使用独立的 `LYNX_AES_KEY`、`LYNX_JWT_SECRET`，妥善保管加密密钥。已有加密数据不可直接更换 AES Key，需先迁移。

`dev` 默认 legacy/mock；`prod` 默认 live，可用 `QIANCHUAN_OAUTH_MODE` 覆盖。应用环境变量沿用 `QIANCHUAN_APPID`、`QIANCHUAN_SECRET`、`QIANCHUAN_REDIRECT_URI`、`LYNX_OAUTH_WEB_CALLBACK_URI`。

后端回调路径为 `/lynx-ad/api/v1/identity/oauth/callback`。本地内网穿透只转发这个路径，回调验证 state 后重定向本地前端。临时隧道必须持续运行；地址更换后需同时更新平台注册地址和后端配置。

生产千川应用的**授权回调地址**与 `QIANCHUAN_REDIRECT_URI` 均为
`https://lynxad.cn/lynx-ad/api/v1/identity/oauth/callback`。应用首页可以填写
`https://lynxad.cn/`，但不能将首页地址填为授权回调。后端验证授权码和 state 后，
通过 `LYNX_OAUTH_WEB_CALLBACK_URI=https://lynxad.cn/oauth/callback` 将结果送回前端。

## 数据与流程

- `oauth_application` 区分平台、应用 ID 和加密 Secret。当前只开放 QIANCHUAN；OCEANENGINE（巨量营销）以后使用独立应用、凭证和适配器。
- `oauth_authorization_session` 绑定发起者和灵投账户，state 有效 15 分钟。回调使用状态 CAS 和授权码哈希唯一约束，避免重复换票。
- 换票成功后先将加密 Token 保存为 `oauth_grant`，再查询授权账户。广告主直接绑定；店铺、工作台组织、EBP 组织展开为千川广告账户并分页去重。
- 单账户在套餐允许时自动绑定；多个账户在回调页选择绑定。查询失败保留凭证，可重新同步；绑定会再次校验账户范围、套餐额度和会话所有权。
- `oauth_account` 按灵投账户、平台、advertiser_id 更新，重新授权保留原内部账户 ID。查询实际授权抖音号；缺少权限或同步失败会显示提示，不生成虚构抖音号。
- 同次授权的多个账户共享一个 grant。手动刷新、定时刷新和接口令牌过期后的刷新均在授权模块执行；数据库租约与版本控制避免重复消费刷新 Token。
- 临时网络失败保留授权状态；明确的凭证过期、撤销或刷新凭证无效将关联账户标为失效。平台未返回刷新有效期时不推算虚假到期时间。
- 业务模块使用 `QianchuanCredentialFacade.getUsableCredential(tenantId, accountId)` 从数据库读取并校验凭证，不能跨灵投账户取凭证，演示账户不能调用真实接口。

取消为解除灵投平台绑定；删除为隐藏已失效/取消的本地记录，历史计划与日志仍保留。平台端撤销授权需要在千川官方页面操作。

## 验证

`QianchuanOAuthContractTest` 验证平台报文、账户展开、抖音号解析和刷新错误分类；`LiveQianchuanAuthorizationIT` 验证真实数据库上的加密、幂等、租户隔离、账户额度、重新授权及共享令牌刷新。集成测试使用隔离测试容器和虚构凭证，不写入联调数据库的真实授权。

上线联调最后一步需要用户在千川官方页面登录并确认账户授权，才能获得真实 Access-Token；未完成平台确认不能通过本地 mock 冒充成功。
