# 巨量千川四步创建投放计划

## 入口与职责

入口为 `/campaigns/create`，草稿恢复为 `/campaigns/create?draftId=<灵投计划ID>`。按当前登录灵投账户查询数据库中的千川授权、advertiser_id 和抖音号；令牌刷新继续由授权模块负责。浏览器不接收 app secret、Access-Token 或永久存储凭证；仅返回预览所需的短期素材访问地址。

所有创建请求由 `CreationWorkflowService` 处理，复用现有授权、素材记录、同步、计划查询和状态同步模块。本地上传走预检及审核，引擎生成结果走已有 OSS 文件引用。旧的模拟素材包发布路径在真实千川模式下被阻止。无需修改 LynxAd-ai-engine 项目或另建授权/素材上传服务。

## 四步流程

1. **选择商品**：调用真实商品接口，支持抖音号切换、搜索、分页和平台支持的分类。最多选择 10 件商品；不支持的新品分类禁用。不存在的价格和销量显示为“—”。点击“我选好了”后，由后台调用引擎选品分析；只有该组选品分析成功，下一步才可用。改变选品会使旧分析和素材包失效。
2. **设置投放目标**：加载千川预算和净成交 ROI 推荐。控成本使用 `SMART_BID_CUSTOM`，提交 `roi2_goal`；放量使用 `SMART_BID_CONSERVATIVE`，不提交该字段。放量预估只采用与当前预算精确匹配的千川推荐行，不构造收益或订单数。支持长期和自定日期；金额、ROI、名称及日期在前后端同时校验。
3. **定素材并投放**：可按商品调用引擎异步生成，或选择现有本地视频、图片、图文。引擎生成任务 ID 持久化，轮询真实进度。生成产物已由引擎保存 OSS，应用仅登记任务/素材/组件关联，不下载后重新上传 OSS，也不创建审核任务。预览与千川同步前从引擎查询最新签名地址；千川的文件上传接口所需临时下载不构成第二次 OSS 存储。引擎图文全部组件无需上传审核；本地上传图文仍要求文本、图片、音频全部通过审核，随后在千川组装为 carousel。全部商品具备可用素材后才能确认素材包；未确认当前版本不能发布。删除、重新生成和取消投放使用已有弹窗，反馈保存在草稿内。重新生成成功且同步完成后才替换旧素材。标题修改会重新质检并使确认失效。
4. **查看投放结果**：发布完成保存实际 `ad_id`，关联到本地计划并复用已有结果页和平台状态同步。提交成功表示已创建，审核是否通过以千川返回结果为准。请求超时进入“结果待确认”，禁止自动重试创建；可输入已核对的千川计划 ID，由后台读取并核对抖音号、名称和商品集合后关联，关联不会创建广告。

三个编辑步骤的“取消投放”均支持保存草稿后返回列表，或丢弃未发布草稿。素材记录及引擎引用保留在素材中心，丢弃草稿仅清除计划关联，不删除引擎 OSS 文件。

### 生成终态对账与文字重排

App 收到引擎 `partial_failed` / `failed` 后不会直接永久停止检查。未发布、非新增素材的原任务保留最多 10 次后台结果对账，失败汇总按约 30 秒间隔读取同一个任务，不重新提交生成。引擎恢复执行时清除旧终态；最终成功且当前素材就绪、商品覆盖和已知数量均完整时，汇总更新为 `SUCCEEDED`。真实失败仍保留具体问题与可用素材，重试达到上限后停止。

读取历史失败草稿时也可在现有任务租约保护下触发对账，兼容 `work_pending=0` 的旧记录；后台预算耗尽后重新打开草稿仍允许一次有间隔限制的结果读取，不重启整轮重试。引擎暂不可用时保留已有预览。`generationReconciliationAttempts`、`generationReconciledAt` 保存在草稿 JSON，无需数据库迁移；新生成、重生成和文字重排开始时重置。响应必须匹配当前任务 ID，旧任务的迟到结果不能覆盖新任务。

已完成的 AI 商品卡和视频封面可点击“重排文字”，复用无字底图及原文案修正字体字形，不调用生图或视频生成。新图导入并同步就绪后才替换当前选择，原版本继续留在引擎历史中。重排无法保留原有完整文案时保留旧素材，不以无字或更少文字的图片替换原成品。

## 应用 API

统一前缀 `/api/v1/delivery/creation`，由现有登录认证与租户隔离保护。

| 方法和路径 | 用途 |
| --- | --- |
| GET `/products` | 当前授权账号、抖音号下真实商品 |
| POST `/` | 新建草稿或更新选品，携带旧 revision |
| GET `/{id}` | 恢复草稿、读取分析/生成/发布状态及可用预览地址 |
| POST `/{id}/analysis` | 排队选品分析 |
| GET `/{id}/recommendations` | 获取并保存千川预算、ROI 推荐 |
| PUT `/{id}/goal` | 保存投放目标与日期 |
| POST `/{id}/generation` | 为选定商品提交生成任务 |
| POST `/{id}/materials` | 加入素材中心的本地素材 |
| DELETE `/{id}/materials/{material}` | 删除计划素材关联并记录反馈 |
| POST `/{id}/materials/{material}/regenerate` | 提交引擎重新生成并记录反馈 |
| PUT `/{id}/materials/{material}/titles` | 编辑标题并重新质检 |
| POST `/{id}/materials/{material}/recheck` | 素材中心重试审核/同步后重新检查 |
| POST `/{id}/confirm` | 确认当前素材包版本 |
| POST `/{id}/publish` | 持久化发布请求、排队提交千川 |
| POST `/{id}/reconcile` | 核验并关联待确认的平台计划 |
| DELETE `/{id}` | 丢弃未发布草稿 |

写操作携带 `revision`；发布额外携带 `packageVersion` 与 `requestKey`。过期版本返回 1404，页面恢复最新状态。浏览器接口使用 `CreationView`，不会返回工作线程内部状态。

## 持久化和恢复

V18 同时提供 MySQL、PostgreSQL 迁移：

- `delivery_creation`：草稿、引擎任务 ID、选品快照、素材关系、反馈和版本；仅活跃任务进入工作队列，租约与心跳防止重复工作。
- `delivery_publish_attempt`：不可变发布载荷、版本、请求键、发送状态和平台回执。相同计划版本唯一，创建响应不明确时不重复 POST。平台成功回执先保存，再修复本地计划关联，避免本地数据库短暂故障抹掉成功结果。
- `creative_carousel_platform`：按租户/图文/千川账户保存组装结果。成功结果复用；不明确结果阻止盲目重建。

新流程商品快照用于投放列表的商品名/缩略图回退，直到平台同步返回商品信息；发布载荷中的真实素材 ID 用于素材中心“已引用”判断，不复制素材文件。

## 配置与边界

- 沿用 `lynx.ad.ai.review.base-url` 调用 LynxAd-ai-engine 的 plan-tasks、perception 与 generation API，沿用现有 OSS 和 FFmpeg 配置。
- 千川应用及授权从现有授权模块获取；巨量营销应用不会被当作千川授权使用。模拟千川模式不允许真实创建。
- 引擎引用只来自后台按租户查询的已成功任务结果，记录在 creative_engine_asset，storage_mode=engine_oss。AI 标记 is_aigc 本身不能免审核。审核界面使用 NOT_REQUIRED，不能伪装为审核通过。读取链接不跟随重定向，限制千川同步所需的临时下载时间及大小。
- API 不允许任意第三方下载 URL、跨租户素材或其他千川账户的素材 ID 混用。
- 日预算 0–100000（不含 0），控成本 ROI 0.1–50，最多两位小数，计划名称 1–30 字。千川还可能施加类目或账户限制，其拒绝结果由页面展示。
- 生成任务提交结果不明确时，读取引擎计划 context 恢复任务，不自动新建重复生成任务。图文组装结果不明确时仍需人工核对；不会假定成功。

## 验证

自动测试覆盖创建载荷、64 位 ID、投放方式差异、日期和版本校验、素材审核/账户隔离、数据库并发锁和发布幂等、引擎中断恢复、失败重生成保留旧素材、引擎 OSS 引用免审核、签名地址刷新及本地上传不可绕过审核，以及浏览器响应过滤；现有素材审核和千川管理回归继续通过。

浏览器使用仅拦截创建接口的测试响应检查选品分析门槛、两种投放方式、生成进度、素材确认、弹窗、草稿恢复和发布超时不重复请求。真实授权账户已验证商品查询、预算/ROI 推荐，以及临时草稿保存/删除。测试没有向千川创建真实广告，没有发起付费 AI 生成；完整真实生成与发布仍需使用实际可投素材联调。

字段参考：[巨量引擎官方 Go SDK 创建请求](https://github.com/oceanengine/ad_open_sdk_go/blob/master/models/model_qianchuan_uni_aweme_ad_create_v1_0_request.go)、[投放设置](https://github.com/oceanengine/ad_open_sdk_go/blob/master/models/model_qianchuan_uni_aweme_ad_create_v1_0_request_delivery_setting.go)。与压缩包中的调用结构对齐，并以当前官方字段及已有模块为准。


## OSS 存储与平台映射（V2）

图片/封面、音频、视频分别写入 lynxad-douyin-image、lynxad-douyin-voice、lynxad-douyin-video。文本仅保存在 creative_asset.text_content，object_key/bucket 为空。上传与生成共用 lynx-ad/v2/{tenant_id}/creative/{yyyyMMdd}/{asset_id}/source.{ext}，生成文件由引擎写入一次，应用仅登记元信息。

V20 补齐 bucket、storage_endpoint、path_version、origin、duration_ms、etag、preview_asset_id。实际 bucket/object_key 是永久定位依据；访问时生成签名地址。creative_engine_asset 记录任务来源，图文继续通过 creative_carousel_part 关联文字、图片顺序和音频。creative_asset_platform 按授权账户保留 advertiser_id/object_id/material_id/request_id，图文整体 ID 由 creative_carousel_platform 保存。接口未返回 material_id 时保持空值，不伪造平台标识。

应用调用由登录上下文传 store_id=tenant_id 和 operator.user_id；引擎独立调试缺省为 lynxad-ai-engine-test，默认测试素材不进入真实用户的素材库或自动使用真实授权。引擎 material_storage_objects 保存最终组件的桶、Key、校验信息，并让任务重试跨日期保持同一路径；正式 PostgreSQL 引擎需执行 Alembic 0011。

单个可用千川授权可以自动绑定未指定目标的独立媒体；多个账户需选择目标。无授权时保留文件，待绑定；文本和图文组件不单独自动同步。上传文件仍须审核通过，视频/音频异步轮询。引擎生成引用通过来源验证后免上传审核。平台失败仅重试同步，不重新写入 OSS。

旧数据回填通过显式启动参数 lynx.ad.storage.migrate-legacy=true 执行。正确桶内旧 Key 保持 LEGACY；放错桶的文件复制到目标桶，经大小/MD5 核验后切换数据库记录，旧文件保留。首次迁移成功后正常启动不带此参数。
