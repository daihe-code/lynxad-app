package com.lynx.ad.modules.identity.application;

import com.lynx.ad.modules.identity.domain.model.OauthAccount;
import com.lynx.ad.modules.identity.domain.port.OauthAccountRepository;
import com.lynx.ad.modules.identity.domain.service.TokenRefreshPolicy;
import com.lynx.ad.modules.tenant.application.InboxCommandService;
import com.lynx.ad.modules.tenant.domain.model.BizRef;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class TokenRefreshJob {

    private static final Logger log = LoggerFactory.getLogger(TokenRefreshJob.class);

    private final OauthAccountRepository repository;
    private final InboxCommandService inboxCommandService;
    private final OauthCommandService commandService;
    private final TokenRefreshPolicy policy = new TokenRefreshPolicy();

    public TokenRefreshJob(
            OauthAccountRepository repository,
            InboxCommandService inboxCommandService,
            OauthCommandService commandService) {
        this.repository = repository;
        this.inboxCommandService = inboxCommandService;
        this.commandService = commandService;
    }

    @Scheduled(fixedDelayString = "${lynx.ad.schedule.token-refresh-ms:600000}")
    public void refreshDue() {
        Instant now = Instant.now();
        Instant deadline = now.plus(OauthAccount.REFRESH_LEAD);
        for (OauthAccount account : repository.findDueRefresh(deadline)) {
            if (!policy.due(account, now)) {
                continue;
            }
            try {
                commandService.applyPortRefresh(account);
                repository.save(account);
            } catch (Exception ex) {
                log.warn("token refresh failed account={}", account.id().value());
                account.markRefreshFailed();
                repository.save(account);
                inboxCommandService.post(
                        account.tenantId(), "授权已失效", "请重新授权巨量千川", new BizRef("TOKEN_EXPIRED", account.id().value()));
            }
        }
    }
}
