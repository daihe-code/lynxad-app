package com.lynx.ad.modules.identity.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.domain.Ids.AccountId;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.identity.api.dto.AccountVo;
import com.lynx.ad.modules.identity.api.dto.OauthUrlResponse;
import com.lynx.ad.common.config.LynxAdProperties;
import com.lynx.ad.modules.identity.application.OauthCallbackResult;
import com.lynx.ad.modules.identity.application.OauthCommandService;
import com.lynx.ad.modules.identity.application.OauthQueryService;
import com.lynx.ad.modules.identity.domain.model.Platform;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
public class IdentityOauthController {

    private final OauthCommandService commandService;
    private final OauthQueryService queryService;
    private final LynxAdProperties properties;

    public IdentityOauthController(
            OauthCommandService commandService, OauthQueryService queryService, LynxAdProperties properties) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.properties = properties;
    }

    @GetMapping("/api/v1/identity/oauth/url")
    public ApiResponse<OauthUrlResponse> authorizeUrl(@RequestParam(defaultValue = "QIANCHUAN") String platform) {
        return ApiResponse.ok(commandService.buildAuthorizeUrl(TenantContext.requireTenantId(), Platform.valueOf(platform)));
    }

    @GetMapping("/api/v1/identity/oauth/callback")
    public RedirectView callback(
            @RequestParam(name = "auth_code", required = false) String authCode,
            @RequestParam(required = false) String state) {
        OauthCallbackResult result = commandService.handleCallback(authCode, state);
        String base = properties.getOauth().getWebCallbackUri();
        RedirectView view;
        if (result.ok()) {
            view = new RedirectView(base + "?ok=1");
        } else {
            String message = URLEncoder.encode(result.message(), StandardCharsets.UTF_8);
            view = new RedirectView(base + "?ok=0&status=fail&message=" + message);
        }
        view.setExposeModelAttributes(false);
        return view;
    }

    @PostMapping("/api/v1/identity/oauth/accounts/mock-bind")
    public ApiResponse<AccountVo> mockBind() {
        return ApiResponse.ok(commandService.mockBind(TenantContext.requireTenantId()));
    }

    @GetMapping({"/api/v1/identity/oauth/accounts", "/oauth/accounts"})
    public ApiResponse<List<AccountVo>> accounts() {
        return ApiResponse.ok(queryService.list(TenantContext.requireTenantId()));
    }

    @PostMapping("/api/v1/identity/accounts/{id}/refresh")
    public ApiResponse<AccountVo> refresh(@PathVariable String id) {
        commandService.refresh(TenantContext.requireTenantId(), new AccountId(id));
        return accounts().data().stream()
                .filter(a -> a.accountId().equals(id))
                .findFirst()
                .map(ApiResponse::ok)
                .orElseGet(() -> ApiResponse.ok(null));
    }

    @DeleteMapping("/api/v1/identity/accounts/{id}")
    public ApiResponse<Void> unbind(@PathVariable String id) {
        commandService.unbind(TenantContext.requireTenantId(), new AccountId(id));
        return ApiResponse.ok(null);
    }
}
