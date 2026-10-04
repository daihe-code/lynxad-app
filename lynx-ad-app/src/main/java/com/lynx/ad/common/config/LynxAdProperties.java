package com.lynx.ad.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "lynx.ad")
public class LynxAdProperties {

    private Mock mock = new Mock();
    private Sms sms = new Sms();
    private Jwt jwt = new Jwt();
    private Crypto crypto = new Crypto();
    private Oauth oauth = new Oauth();
    private Storage storage = new Storage();
    private Payment payment = new Payment();
    private Schedule schedule = new Schedule();
    private Money money = new Money();
    private Contact contact = new Contact();
    private QianchuanLibrary qianchuan = new QianchuanLibrary();
    private Ai ai = new Ai();
    private boolean demoMode = true;

    public Mock getMock() {
        return mock;
    }

    public void setMock(Mock mock) {
        this.mock = mock;
    }

    public Sms getSms() {
        return sms;
    }

    public void setSms(Sms sms) {
        this.sms = sms;
    }

    public Jwt getJwt() {
        return jwt;
    }

    public void setJwt(Jwt jwt) {
        this.jwt = jwt;
    }

    public Crypto getCrypto() {
        return crypto;
    }

    public void setCrypto(Crypto crypto) {
        this.crypto = crypto;
    }

    public Oauth getOauth() {
        return oauth;
    }

    public void setOauth(Oauth oauth) {
        this.oauth = oauth;
    }

    public Storage getStorage() {
        return storage;
    }

    public void setStorage(Storage storage) {
        this.storage = storage;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public Schedule getSchedule() {
        return schedule;
    }

    public void setSchedule(Schedule schedule) {
        this.schedule = schedule;
    }

    public Money getMoney() {
        return money;
    }

    public void setMoney(Money money) {
        this.money = money;
    }

    public Contact getContact() {
        return contact;
    }

    public void setContact(Contact contact) {
        this.contact = contact;
    }

    public QianchuanLibrary getQianchuan() {
        return qianchuan;
    }

    public void setQianchuan(QianchuanLibrary qianchuan) {
        this.qianchuan = qianchuan;
    }

    public Ai getAi() {
        return ai;
    }

    public void setAi(Ai ai) {
        this.ai = ai;
    }

    /** mock.qianchuan=false 时换票所需：app-id / secret / redirect / authorize-url */
    public boolean isQianchuanLiveConfigured() {
        Qianchuan qc = oauth.getQianchuan();
        return notBlank(qc.getAppId())
                && notBlank(qc.getAppSecret())
                && notBlank(qc.getRedirectUri())
                && notBlank(qc.getAuthorizeUrl());
    }

    /** mock.ai=false 时出片所需：LLM key + 可灵密钥 */
    public boolean isAiLiveConfigured() {
        return notBlank(ai.getLlm().getApiKey())
                && notBlank(ai.getKeling().getAccessKey())
                && notBlank(ai.getKeling().getSecret());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public boolean isDemoMode() {
        return demoMode;
    }

    public void setDemoMode(boolean demoMode) {
        this.demoMode = demoMode;
    }

    public static class Mock {
        private boolean qianchuan = true;
        private boolean ai = true;
        private String autoAudit = "PASS";

        public boolean isQianchuan() {
            return qianchuan;
        }

        public void setQianchuan(boolean qianchuan) {
            this.qianchuan = qianchuan;
        }

        public boolean isAi() {
            return ai;
        }

        public void setAi(boolean ai) {
            this.ai = ai;
        }

        public String getAutoAudit() {
            return autoAudit;
        }

        public void setAutoAudit(String autoAudit) {
            this.autoAudit = autoAudit;
        }
    }

    public static class Sms {
        private String mode = "mock";
        private String devCode = "123456";
        private int ttlSec = 300;
        private int cooldownSec = 90;
        private Live live = new Live();

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getDevCode() {
            return devCode;
        }

        public void setDevCode(String devCode) {
            this.devCode = devCode;
        }

        public int getTtlSec() {
            return ttlSec;
        }

        public void setTtlSec(int ttlSec) {
            this.ttlSec = ttlSec;
        }

        public int getCooldownSec() {
            return cooldownSec;
        }

        public void setCooldownSec(int cooldownSec) {
            this.cooldownSec = cooldownSec;
        }

        public Live getLive() {
            return live;
        }

        public void setLive(Live live) {
            this.live = live;
        }

        public boolean isOff() {
            return "off".equalsIgnoreCase(mode);
        }

        public boolean isLive() {
            return "live".equalsIgnoreCase(mode);
        }

        public boolean isLiveConfigured() {
            Live l = live;
            return notBlank(l.getEndpoint())
                    && notBlank(l.getAccessKey())
                    && notBlank(l.getAccessSecret())
                    && notBlank(l.getSignName())
                    && notBlank(l.getTemplateCode());
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    public static class Live {
        private String vendor = "";
        private String endpoint = "";
        private String accessKey = "";
        private String accessSecret = "";
        private String signName = "";
        private String templateCode = "";

        public String getVendor() {
            return vendor;
        }

        public void setVendor(String vendor) {
            this.vendor = vendor;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getAccessSecret() {
            return accessSecret;
        }

        public void setAccessSecret(String accessSecret) {
            this.accessSecret = accessSecret;
        }

        public String getSignName() {
            return signName;
        }

        public void setSignName(String signName) {
            this.signName = signName;
        }

        public String getTemplateCode() {
            return templateCode;
        }

        public void setTemplateCode(String templateCode) {
            this.templateCode = templateCode;
        }
    }

    public static class Contact {
        private String phone = "";
        private String email = "";
        private String opsPhones = "";

        public String getPhone() {
            return phone;
        }

        public void setPhone(String phone) {
            this.phone = phone;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getOpsPhones() {
            return opsPhones;
        }

        public void setOpsPhones(String opsPhones) {
            this.opsPhones = opsPhones;
        }
    }

    public static class Jwt {
        private String secret = "dev-only-change-me-32bytes-min-key!!";
        private long ttlSec = 86400;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getTtlSec() {
            return ttlSec;
        }

        public void setTtlSec(long ttlSec) {
            this.ttlSec = ttlSec;
        }
    }

    public static class Crypto {
        private String aesKey = "dev-only-aes256-key-32bytes!!!!";

        public String getAesKey() {
            return aesKey;
        }

        public void setAesKey(String aesKey) {
            this.aesKey = aesKey;
        }
    }

    public static class Oauth {
        private String webCallbackUri = "";
        private Qianchuan qianchuan = new Qianchuan();

        public String getWebCallbackUri() {
            return webCallbackUri;
        }

        public void setWebCallbackUri(String webCallbackUri) {
            this.webCallbackUri = webCallbackUri;
        }

        public Qianchuan getQianchuan() {
            return qianchuan;
        }

        public void setQianchuan(Qianchuan qianchuan) {
            this.qianchuan = qianchuan;
        }
    }

    public static class Qianchuan {
        private String appId = "";
        private String appSecret = "";
        private String redirectUri = "http://localhost:5173/oauth/callback";
        /** Demo 对照：千川开放平台授权页 */
        private String authorizeUrl = "https://qianchuan.jinritemai.com/openapi/qc/audit/oauth.html";

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public String getAuthorizeUrl() {
            return authorizeUrl;
        }

        public void setAuthorizeUrl(String authorizeUrl) {
            this.authorizeUrl = authorizeUrl;
        }
    }

    public static class Storage {
        private String mode = "local";
        private String localDir = "./data/storage";
        private int urlTtlSec = 3600;
        private Oss oss = new Oss();

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getLocalDir() {
            return localDir;
        }

        public void setLocalDir(String localDir) {
            this.localDir = localDir;
        }

        public int getUrlTtlSec() {
            return urlTtlSec;
        }

        public void setUrlTtlSec(int urlTtlSec) {
            this.urlTtlSec = urlTtlSec;
        }

        public Oss getOss() {
            return oss;
        }

        public void setOss(Oss oss) {
            this.oss = oss;
        }

        public boolean isOss() {
            return "oss".equalsIgnoreCase(mode);
        }

        public boolean isOssConfigured() {
            return notBlank(oss.getEndpoint())
                    && notBlank(oss.getBucket())
                    && notBlank(oss.getAccessKeyId())
                    && notBlank(oss.getAccessKeySecret());
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    public static class Oss {
        private String endpoint = "";
        private String bucket = "";
        private String region = "";
        private String prefix = "lynx-ad/";
        private String accessKeyId = "";
        private String accessKeySecret = "";

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getRegion() {
            return region;
        }

        public void setRegion(String region) {
            this.region = region;
        }

        public String getPrefix() {
            return prefix;
        }

        public void setPrefix(String prefix) {
            this.prefix = prefix;
        }

        public String getAccessKeyId() {
            return accessKeyId;
        }

        public void setAccessKeyId(String accessKeyId) {
            this.accessKeyId = accessKeyId;
        }

        public String getAccessKeySecret() {
            return accessKeySecret;
        }

        public void setAccessKeySecret(String accessKeySecret) {
            this.accessKeySecret = accessKeySecret;
        }
    }

    public static class Payment {
        private String mode = "mock";
        private String timezone = "Asia/Shanghai";
        private Wechat wechat = new Wechat();

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getTimezone() {
            return timezone;
        }

        public void setTimezone(String timezone) {
            this.timezone = timezone;
        }

        public Wechat getWechat() {
            return wechat;
        }

        public void setWechat(Wechat wechat) {
            this.wechat = wechat;
        }

        public boolean isMock() {
            return !"live".equalsIgnoreCase(mode);
        }

        public boolean isLive() {
            return "live".equalsIgnoreCase(mode);
        }

        public boolean isLiveConfigured() {
            return notBlank(wechat.appId)
                    && notBlank(wechat.mchId)
                    && notBlank(wechat.apiV3Key)
                    && notBlank(wechat.privateKeyPem);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }
    }

    public static class Wechat {
        private String appId = "";
        private String mchId = "";
        private String apiV3Key = "";
        private String serialNo = "";
        private String privateKeyPem = "";
        private String notifyUrl = "";

        public String getAppId() {
            return appId;
        }

        public void setAppId(String appId) {
            this.appId = appId;
        }

        public String getMchId() {
            return mchId;
        }

        public void setMchId(String mchId) {
            this.mchId = mchId;
        }

        public String getApiV3Key() {
            return apiV3Key;
        }

        public void setApiV3Key(String apiV3Key) {
            this.apiV3Key = apiV3Key;
        }

        public String getSerialNo() {
            return serialNo;
        }

        public void setSerialNo(String serialNo) {
            this.serialNo = serialNo;
        }

        public String getPrivateKeyPem() {
            return privateKeyPem;
        }

        public void setPrivateKeyPem(String privateKeyPem) {
            this.privateKeyPem = privateKeyPem;
        }

        public String getNotifyUrl() {
            return notifyUrl;
        }

        public void setNotifyUrl(String notifyUrl) {
            this.notifyUrl = notifyUrl;
        }
    }

    public static class QianchuanLibrary {
        private String videoGetUrl = "https://ad.oceanengine.com/open_api/v1.0/qianchuan/video/get/";
        private String imageGetUrl = "https://ad.oceanengine.com/open_api/v1.0/qianchuan/image/get/";
        private String carouselGetUrl = "https://api.oceanengine.com/open_api/v1.0/qianchuan/carousel/get/";
        private OauthApi oauth = new OauthApi();
        private CatalogApi catalog = new CatalogApi();
        private DeliveryApi delivery = new DeliveryApi();
        private ReportApi report = new ReportApi();
        private MaterialApi material = new MaterialApi();

        public String getVideoGetUrl() {
            return videoGetUrl;
        }

        public void setVideoGetUrl(String videoGetUrl) {
            this.videoGetUrl = videoGetUrl;
        }

        public String getImageGetUrl() {
            return imageGetUrl;
        }

        public void setImageGetUrl(String imageGetUrl) {
            this.imageGetUrl = imageGetUrl;
        }

        public String getCarouselGetUrl() {
            return carouselGetUrl;
        }

        public void setCarouselGetUrl(String carouselGetUrl) {
            this.carouselGetUrl = carouselGetUrl;
        }

        public OauthApi getOauth() {
            return oauth;
        }

        public void setOauth(OauthApi oauth) {
            this.oauth = oauth;
        }

        public CatalogApi getCatalog() {
            return catalog;
        }

        public void setCatalog(CatalogApi catalog) {
            this.catalog = catalog;
        }

        public DeliveryApi getDelivery() {
            return delivery;
        }

        public void setDelivery(DeliveryApi delivery) {
            this.delivery = delivery;
        }

        public ReportApi getReport() {
            return report;
        }

        public void setReport(ReportApi report) {
            this.report = report;
        }

        public MaterialApi getMaterial() {
            return material;
        }

        public void setMaterial(MaterialApi material) {
            this.material = material;
        }
    }

    /** 巨量开放平台 OAuth path，默认对照 Demo qianchuan-callback / refresh-token */
    public static class OauthApi {
        private String accessTokenUrl = "https://api.oceanengine.com/open_api/oauth2/access_token/";
        private String refreshTokenUrl = "https://ad.oceanengine.com/open_api/oauth2/refresh_token/";
        private String advertiserGetUrl = "https://api.oceanengine.com/open_api/oauth2/advertiser/get/";
        private String awemeAuthorizedUrl = "https://api.oceanengine.com/open_api/v1.0/qianchuan/uni_aweme/authorized/get/";

        public String getAccessTokenUrl() { return accessTokenUrl; }
        public void setAccessTokenUrl(String accessTokenUrl) { this.accessTokenUrl = accessTokenUrl; }
        public String getRefreshTokenUrl() { return refreshTokenUrl; }
        public void setRefreshTokenUrl(String refreshTokenUrl) { this.refreshTokenUrl = refreshTokenUrl; }
        public String getAdvertiserGetUrl() { return advertiserGetUrl; }
        public void setAdvertiserGetUrl(String advertiserGetUrl) { this.advertiserGetUrl = advertiserGetUrl; }
        public String getAwemeAuthorizedUrl() { return awemeAuthorizedUrl; }
        public void setAwemeAuthorizedUrl(String awemeAuthorizedUrl) { this.awemeAuthorizedUrl = awemeAuthorizedUrl; }
    }

    public static class CatalogApi {
        private String productGetUrl = "https://api.oceanengine.com/open_api/v1.0/qianchuan/uni_promotion/product/get/";

        public String getProductGetUrl() { return productGetUrl; }
        public void setProductGetUrl(String productGetUrl) { this.productGetUrl = productGetUrl; }
    }

    public static class DeliveryApi {
        private String suggestBudgetUrl = "https://api.oceanengine.com/open_api/v1.0/qianchuan/uni_aweme/suggest/budget/";
        private String suggestRoiUrl = "https://api.oceanengine.com/open_api/v1.0/qianchuan/uni_aweme/suggest/roi/";
        private String adCreateUrl = "https://api.oceanengine.com/open_api/v1.0/qianchuan/uni_aweme/ad/create/";
        private String adUpdateUrl = "https://api.oceanengine.com/open_api/v1.0/qianchuan/uni_aweme/ad/update/";
        private String adListUrl = "https://api.oceanengine.com/open_api/v1.0/qianchuan/uni_promotion/list/";
        private String adStatusUrl = "https://api.oceanengine.com/open_api/v1.0/qianchuan/uni_promotion/ad/status/update/";

        public String getSuggestBudgetUrl() { return suggestBudgetUrl; }
        public void setSuggestBudgetUrl(String suggestBudgetUrl) { this.suggestBudgetUrl = suggestBudgetUrl; }
        public String getSuggestRoiUrl() { return suggestRoiUrl; }
        public void setSuggestRoiUrl(String suggestRoiUrl) { this.suggestRoiUrl = suggestRoiUrl; }
        public String getAdCreateUrl() { return adCreateUrl; }
        public void setAdCreateUrl(String adCreateUrl) { this.adCreateUrl = adCreateUrl; }
        public String getAdUpdateUrl() { return adUpdateUrl; }
        public void setAdUpdateUrl(String adUpdateUrl) { this.adUpdateUrl = adUpdateUrl; }
        public String getAdListUrl() { return adListUrl; }
        public void setAdListUrl(String adListUrl) { this.adListUrl = adListUrl; }
        public String getAdStatusUrl() { return adStatusUrl; }
        public void setAdStatusUrl(String adStatusUrl) { this.adStatusUrl = adStatusUrl; }
    }

    public static class ReportApi {
        private String customUrl = "https://api.oceanengine.com/open_api/v1.0/qianchuan/report/uni_promotion/data/get/";

        public String getCustomUrl() { return customUrl; }
        public void setCustomUrl(String customUrl) { this.customUrl = customUrl; }
    }

    public static class MaterialApi {
        private String videoUploadUrl = "https://ad.oceanengine.com/open_api/2/file/video/ad/";

        public String getVideoUploadUrl() { return videoUploadUrl; }
        public void setVideoUploadUrl(String videoUploadUrl) { this.videoUploadUrl = videoUploadUrl; }
    }

    public static class Ai {
        private Llm llm = new Llm();
        private Tts tts = new Tts();
        private Keling keling = new Keling();

        public Llm getLlm() { return llm; }
        public void setLlm(Llm llm) { this.llm = llm; }
        public Tts getTts() { return tts; }
        public void setTts(Tts tts) { this.tts = tts; }
        public Keling getKeling() { return keling; }
        public void setKeling(Keling keling) { this.keling = keling; }
    }

    public static class Llm {
        private String baseUrl = "";
        private String apiKey = "";
        private String model = "";
        private long timeoutMs = 60000;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    }

    public static class Tts {
        private String baseUrl = "";
        private String apiKey = "";
        private long timeoutMs = 60000;

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public long getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
    }

    public static class Keling {
        private String endpoint = "";
        private String accessKey = "";
        private String secret = "";
        private long pollMs = 2000;
        private int maxPoll = 150;

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getPollMs() { return pollMs; }
        public void setPollMs(long pollMs) { this.pollMs = pollMs; }
        public int getMaxPoll() { return maxPoll; }
        public void setMaxPoll(int maxPoll) { this.maxPoll = maxPoll; }
    }

    public static class Schedule {
        private long tokenRefreshMs = 600000;
        private long auditPollMs = 30000;
        private long metricSnapshotMs = 300000;

        public long getTokenRefreshMs() {
            return tokenRefreshMs;
        }

        public void setTokenRefreshMs(long tokenRefreshMs) {
            this.tokenRefreshMs = tokenRefreshMs;
        }

        public long getAuditPollMs() {
            return auditPollMs;
        }

        public void setAuditPollMs(long auditPollMs) {
            this.auditPollMs = auditPollMs;
        }

        public long getMetricSnapshotMs() {
            return metricSnapshotMs;
        }

        public void setMetricSnapshotMs(long metricSnapshotMs) {
            this.metricSnapshotMs = metricSnapshotMs;
        }
    }

    public static class Money {
        private int statCostDivisor = 100000;

        public int getStatCostDivisor() {
            return statCostDivisor;
        }

        public void setStatCostDivisor(int statCostDivisor) {
            this.statCostDivisor = statCostDivisor;
        }
    }
}
