package com.lynx.ad.modules.tenant.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.security.TenantContext;
import com.lynx.ad.modules.tenant.api.dto.InviteRequest;
import com.lynx.ad.modules.tenant.api.dto.MemberVo;
import com.lynx.ad.modules.tenant.application.MemberCommandService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/members")
public class TenantMemberController {

    private final MemberCommandService memberCommandService;

    public TenantMemberController(MemberCommandService memberCommandService) {
        this.memberCommandService = memberCommandService;
    }

    @GetMapping
    public ApiResponse<List<MemberVo>> list() {
        return ApiResponse.ok(memberCommandService.list(TenantContext.requireTenantId()));
    }

    @PostMapping
    public ApiResponse<Void> invite(@RequestBody InviteRequest req) {
        memberCommandService.invite(TenantContext.userId(), new PhoneNumber(req.phone()));
        return ApiResponse.ok(null);
    }

    @PostMapping("/invite")
    public ApiResponse<Void> invitePath(@RequestBody InviteRequest req) {
        return invite(req);
    }
}
