# 应用发布来源

应用前端 `web/` 和后端 `lynx-ad-app/` 统一使用 [GitHub 应用仓库](https://github.com/daihe-code/lynxad-app)，发布分支为 `main`。Gitee 已停用，不作为拉取、推送或发布来源。

本机 `origin` 的拉取与推送地址均为 `https://github.com/daihe-code/lynxad-app.git`。

发布前先完成必要验证并推送代码，再执行 `git fetch origin main`，确认工作区干净且待发布提交与 GitHub 的 `origin/main` 一致。前后端构建使用该提交的 Git 归档，并在发布记录中保留提交哈希。

只有用户单独明确要求上线时才执行生产部署；推送代码不会自动发布。

## 支付宝电脑网站支付

商家绑定页用于把「电脑网站支付」签约商户绑定到已创建的网页应用。联调使用沙箱应用参数和沙箱网关，生产使用已上线且开通对应产品的应用参数。

| 服务端配置 | 含义 |
| --- | --- |
| `LYNX_PAYMENT_MODE=alipay` | 使用支付宝收银台 |
| `ALIPAY_APP_ID` | 已绑定的支付宝应用 ID |
| `ALIPAY_SELLER_ID` | 与收款商户匹配的支付宝商户 PID |
| `ALIPAY_PRIVATE_KEY` | PKCS#8 应用私钥，由生产密钥配置注入 |
| `ALIPAY_PUBLIC_KEY` | 普通公钥模式使用的支付宝公钥 |
| `ALIPAY_APP_CERT_PATH`、`ALIPAY_PUBLIC_CERT_PATH`、`ALIPAY_ROOT_CERT_PATH` | 证书模式使用的三份证书路径，部署时挂载到 API 容器 |
| `ALIPAY_GATEWAY` | 生产缺省为 `https://openapi.alipay.com/gateway.do`，沙箱使用其控制台显示的网关 |
| `ALIPAY_NOTIFY_URL` | `https://lynxad.cn/lynx-ad/api/v1/billing/payments/alipay/notify` |
| `ALIPAY_RETURN_ORIGIN` | `https://lynxad.cn` |

从沙箱验证一次套餐购买和一次积分充值，核对异步通知、查单和重复通知的账本流水。切换正式环境后先用内部账号小额验证同样流程。需要取消待付订单时，坤境先核对支付宝交易；已付款的订单按结算结果处理。
