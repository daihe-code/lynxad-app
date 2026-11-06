# 账户体系 V1.3：首期实现与联调说明

需求依据：[灵投智营 SaaS 租户与账户体系总方案 V1.3](https://acnp9lhn73wo.feishu.cn/docx/QkEMdRefRoNioCxqG2DcNivTnke)。

本次交付是分期方案的第一期基础实现。默认仍为单人工作空间，使用既有订阅权益与价格；没有开启团队协作售卖、多工作空间创建、跨空间账户移交或新的积分扣费。提交代码不代表已执行生产迁移或上线。

## 模型与边界

| 对象 | 首期行为 |
| --- | --- |
| 自然人 | 沿用稳定的 `sys_user.id`，不以手机号作为资源归属键 |
| 工作空间 | 沿用 `sys_tenant.id`，订阅、账户容量、积分账本以该 ID 隔离 |
| 成员关系 | 用户与工作空间 M:N；加入实例 `membership_id`、状态、权限版本独立于 JWT |
| 真实广告账户 | 平台 + ID 命名空间 + 平台广告账户 ID 唯一 |
| 业务绑定 | 保留原 `oauth_account.id` 作为 binding ID；责任人、客户分组、绑定状态与版本独立保存 |
| 授权路径 | 同一绑定可保存多条已验证 grant；授权失效不解除业务绑定、不释放容量 |
| 客户分组 | 工作空间内的业务分组；账户首期只能有一个主分组，不形成新的财务租户 |
| 积分 | 工作空间一个钱包；与千川广告消耗分离，保留幂等预占、结算、释放、退回和流水 |

重新授权保留责任人和 binding ID，同一账户不会重复占用名额。全局 claim 防止并发绑定到两个工作空间。即使旧绑定已释放，存在其他工作空间历史记录也不能通过 OAuth 自动接管，需后续正式移交流程核验。

`OWNER / ADMIN / FINANCE / OPERATOR / MEMBER / VIEWER` 是职责，不是广告账户执行权限。首期只有绑定责任人（且是有效的 OWNER、MEMBER 或 OPERATOR）可以执行该账户；工作空间所有者身份不绕过这一检查。财务可查看积分，不因此获得广告操作权。

团队范围授权尚未开放。现有业务列表仍有工作空间级查询，故 MEMBER/OPERATOR 发现空间内存在其他责任人的有效绑定时，拒绝访问这些旧业务 API，不以简单前端过滤冒充完整数据隔离。后续团队版本需要逐个迁移这些查询的资源范围、邀请接受、移除成员和责任移交功能。

## 应用入口

账户管理增加“工作空间”页：容量、客户分组、绑定与授权路径、积分余额和流水。客户分组变更写入操作审计。积分余额/流水仅 OWNER、FINANCE 可读，分组管理限 OWNER、ADMIN，账户分组调整仍要求绑定责任人。

已有多个有效成员关系时显示工作空间选择器。切换签发目标空间的 JWT 并整页加载，其他标签页同步清空旧页面上下文。切换不搬移已排队任务、素材、钱包或授权。

接口基路径：`/lynx-ad/api/v1/tenant/workspaces`。

| 方法与路径 | 用途 |
| --- | --- |
| `GET /` | 当前自然人的有效工作空间 |
| `POST /{id}/switch` | 校验成员关系后切换并签发会话 |
| `GET /current` | 名称、角色、容量、分组及有权限的钱包余额 |
| `GET /current/accounts` | 当前空间绑定；非治理角色仅返回本人负责的绑定 |
| `GET /current/points?page=1&pageSize=20` | 分页积分流水 |
| `POST /current/clients` | 新增分组，body 为 `{ "name": "客户名称" }` |
| `PUT /current/clients/{id}` | 重命名分组 |
| `DELETE /current/clients/{id}` | 删除空分组；默认分组不能删除 |
| `PUT /current/accounts/{id}/client` | body 为 `{ "clientSpaceId": "..." }` |

没有面向用户的积分入账、任意余额修改或自行指定价格接口。`PointWalletService` 是内部记账能力，本次没有将它接入生成任务的商业定价或支付发积分流程。新钱包初始为零，不按未知兑换比例迁移历史广告费，也不发放猜测的赠送积分。

## 权限变化与任务

每次请求重新读取成员状态与角色，旧 JWT 中的 OWNER 不会覆盖已降级的成员关系；退出会话记录 jti 撤销。平台管理员接口维持原有独立管理校验，不增加广告账户执行旁路。

本地草稿新增、修改和删除检查当前责任人；账户切换在修改前后检查。发布意图保存提交时的成员实例/权限版本和绑定版本，在真正创建平台计划前重新校验。权限变化、订阅失效或缺少旧任务授权快照会阻止新增发布；已受理请求的结果回读与本地回执应用可继续，避免丢失平台结果。

引擎执行票据包含固定的 tenant、binding、actor、membership ID、权限版本、绑定版本、task 和过期时间，HMAC 签名。新建引擎计划后保存原执行范围，追加/重试不能默默换成另一位当前用户的权限。实际授权凭证在签发/核验时复核，可用的同绑定备用 grant 可接替失效路径。

引擎将票据持久化到 job；Worker 开始和每个新的付费模型调用前核验。确定失权停止新调用，已生成文件和 checkpoint 保留。网络不可达按暂时无法核验处理，不能当成允许执行；已有供应商任务的查询不重复提交付费请求。票据当前有效期 24 小时，过期任务需要重新确认，不无限期续权。

## V52 迁移

PostgreSQL 与 MySQL 均提供 `V52__workspace_account_system.sql`。

新增 `client_space`、`real_ad_account`、`ad_account_binding`、`ad_account_claim`、`account_grant_path`、`point_wallet`、`point_reservation`、`point_ledger`、`workspace_session_revocation`、`workspace_engine_scope`、`workspace_publish_scope`，并扩充 `tenant_member`。

迁移保留用户、工作空间、OAuth 账户、计划和素材 ID。为每个工作空间建立默认分组和零余额钱包；责任人优先使用原授权人，没有授权人时仅采用唯一 OWNER。重复的有效真实账户、责任人缺失/失效或角色不适合执行的记录标为 `REVIEW_REQUIRED`，不建立全局 claim；待核验绑定仍计入已占容量。

发布前在数据库备份/恢复演练后，对生产副本执行全部 Flyway 迁移，核对以下内容：

```sql
SELECT status, COUNT(*) FROM ad_account_binding GROUP BY status;
SELECT platform, advertiser_id, COUNT(*) FROM oauth_account
 WHERE status NOT IN ('UNBOUND','DELETED')
 GROUP BY platform, advertiser_id HAVING COUNT(*) > 1;
SELECT tenant_id, COUNT(*) FROM ad_account_binding
 WHERE status IN ('ACTIVE','REVIEW_REQUIRED') GROUP BY tenant_id;
SELECT COUNT(*) FROM tenant_member WHERE membership_id IS NULL;
```

逐项核验 REVIEW_REQUIRED，不直接批量分配给空间 OWNER。解绑不是历史资源转移，不能通过删历史绑定记录绕过移交。旧 QUEUED 发布意图没有成员快照，升级前应处理完或取消后由用户重新确认；SENDING/UNCERTAIN 请求优先回读，不再次创建计划。

会话撤销记录只在过期前生效；后续运维清理可删除 `expires_at < CURRENT_TIMESTAMP` 的记录，历史账户绑定和积分流水不得同样清理。

## 应用与引擎分步启用

1. 先部署兼容版本引擎，配置固定的内部核验地址和服务密钥，暂不要求旧请求携带票据。
2. 应用先执行 V52；核验历史账户责任人、容量、待核验绑定，并处理旧发布队列。
3. 应用开启 `lynx.ad.workspace.execution-tickets-enabled=true`（环境变量可用 `LYNX_AD_WORKSPACE_EXECUTION_TICKETS_ENABLED`）。默认 false，防止引擎未升级就中断旧链路。
4. 验证初始生成、单条新增、追加、重新生成、标题和盯盘补充均携带原空间与 binding；验证切换空间、退出会话、成员失效和授权过期。
5. 处理完旧无范围生成队列后，再在引擎开启 `REQUIRE_WORKSPACE_EXECUTION=true`。该开关针对应用调用者的生成写入，独立引擎控制台保留现有授权机制。

引擎环境变量：

```text
APP_EXECUTION_VALIDATION_URL=http://<应用内网地址>:<端口>/lynx-ad/api/v1/internal/execution/validate
APP_EXECUTION_VALIDATION_KEY=<与应用 lynx.ad.monitoring.engine-api-key 一致的私密值>
REQUIRE_WORKSPACE_EXECUTION=false
```

固定回调只允许内部网络访问，URL 和服务密钥不能由请求、模型或前端提供，不能写入仓库。应用和引擎版本必须配套；生产开关与发布需要单独授权。

## 验证与后续范围

新增持久化/安全测试覆盖：双数据库兼容模式迁移、重复和缺失责任人的隔离、并发独占绑定、过期授权仍占容量、原责任人不可替换、钱包并发及幂等、跨空间分组隔离、JWT 旧角色失效、签名与任务篡改、备用授权路径范围、其他所有者不得改草稿、排队发布失权及回执保留。

本机 MySQL/PostgreSQL Testcontainers 因 Docker daemon 不可用而跳过；H2 兼容模式通过不能替代真实数据库验收。尚未执行生产迁移、真实 OAuth/平台写入或付费生成。

后续阶段仍包括完整团队与资源范围管理、正式账户移交与在途任务处置、积分定价及支付/生成记账接入、套餐生命周期迁移、多平台 namespace 扩展和平台支持工单。它们不能通过开放当前预留角色或修改数据库状态直接视为已完成。
