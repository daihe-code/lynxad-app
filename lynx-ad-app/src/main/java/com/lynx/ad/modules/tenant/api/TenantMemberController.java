package com.lynx.ad.modules.tenant.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.domain.PhoneNumber;
import com.lynx.ad.common.security.MemberRole;
import com.lynx.ad.modules.tenant.application.WorkspaceTeamService;
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
    private final WorkspaceTeamService team;

    public TenantMemberController(MemberCommandService memberCommandService, WorkspaceTeamService team) {
        this.memberCommandService = memberCommandService;
        this.team = team;
    }

    @GetMapping
    public ApiResponse<List<MemberVo>> list() {
        return ApiResponse.ok(memberCommandService.list(TenantContext.requireTenantId()));
    }

    @PostMapping
    public ApiResponse<?> invite(@RequestBody InviteRequest req) {
        return ApiResponse.ok(team.invite(new PhoneNumber(req.phone()), MemberRole.OPERATOR));
    }

    @PostMapping("/invite")
    public ApiResponse<?> invitePath(@RequestBody InviteRequest req) {
        return invite(req);
    }
}
