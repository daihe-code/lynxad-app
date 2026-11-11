package com.lynx.ad.modules.tenant.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.common.security.*;
import com.lynx.ad.modules.tenant.application.*;
import com.lynx.ad.modules.billing.application.PointWalletService;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenant/workspaces")
public class WorkspaceController {
    private final WorkspaceQueryService service;private final WorkspaceAccess access;private final PointWalletService points;private final ClientSpaceService clients;
    private final WorkspaceTeamService team;
    private final AuthCommandService auth;
    public WorkspaceController(WorkspaceQueryService service,WorkspaceAccess access,PointWalletService points,ClientSpaceService clients,WorkspaceTeamService team,AuthCommandService auth) {this.service=service;this.access=access;this.points=points;this.clients=clients;this.team=team;this.auth=auth;}
    @GetMapping public ApiResponse<?> list() {return ApiResponse.ok(service.list(TenantContext.userId().value()));}
    @PostMapping("/{id}/switch") public ApiResponse<?> switchTo(@PathVariable String id) {return ApiResponse.ok(service.switchTo(id,TenantContext.userId().value()));}
    @GetMapping("/current") public ApiResponse<?> current() {return ApiResponse.ok(service.overview());}
    @GetMapping("/current/accounts") public ApiResponse<?> accounts() {return ApiResponse.ok(service.bindings());}
    @GetMapping("/current/points") public ApiResponse<?> ledger(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) {
        var member=access.require(MemberRole.OWNER,MemberRole.FINANCE);return ApiResponse.ok(points.ledger(member.tenantId(),page,pageSize));
    }
    public record ClientRequest(String name) {}
    public record AssignRequest(String clientSpaceId) {}
    @PostMapping("/current/clients") public ApiResponse<?> createClient(@RequestBody ClientRequest body) {return ApiResponse.ok(Map.of("id",clients.save(null,body.name())));}
    @PutMapping("/current/clients/{id}") public ApiResponse<?> updateClient(@PathVariable String id,@RequestBody ClientRequest body) {return ApiResponse.ok(Map.of("id",clients.save(id,body.name())));}
    @DeleteMapping("/current/clients/{id}") public ApiResponse<?> deleteClient(@PathVariable String id) {clients.delete(id);return ApiResponse.ok(null);}
    @PutMapping("/current/accounts/{id}/client") public ApiResponse<?> assignClient(@PathVariable String id,@RequestBody AssignRequest body) {clients.assign(id,body.clientSpaceId());return ApiResponse.ok(null);}
    public record Invite(String phone, MemberRole role, List<MemberRole> roles) {}
    public record ChangeInvite(MemberRole role, List<MemberRole> roles) {}
    public record ChangeMember(MemberRole role, Boolean active, List<MemberRole> roles) {}
    public record ActivateChild(String code, String password) {}
    @GetMapping("/current/team") public ApiResponse<?> team() { return ApiResponse.ok(team.list()); }
    @PostMapping("/current/team/invitations") public ApiResponse<?> invite(@RequestBody Invite body) {
        var phone = new com.lynx.ad.common.domain.PhoneNumber(body.phone());
        if (body.roles() == null) return ApiResponse.ok(team.invite(phone,body.role()));
        var invitation = team.inviteWithRoles(phone,body.roles());
        return ApiResponse.ok(Map.of("id", invitation.id(), "expiresAt", invitation.expiresAt()));
    }
    @PutMapping("/current/team/invitations/{id}") public ApiResponse<?> inviteRole(@PathVariable String id,@RequestBody ChangeInvite body) {
        if (body.roles() == null) team.updateInviteRole(id,body.role());
        else team.updateInviteRoles(id,body.roles());
        return ApiResponse.ok(null);
    }
    @PostMapping("/current/team/invitations/{id}/sms") public ApiResponse<?> sendChildSms(@PathVariable String id) {
        return ApiResponse.ok(auth.sendChildActivationSms(id));
    }
    @PostMapping("/current/team/invitations/{id}/activate") public ApiResponse<?> activateChild(@PathVariable String id,@RequestBody ActivateChild body) {
        return ApiResponse.ok(Map.of("initialPasswordSet", auth.activateChild(id,body.code(),body.password())));
    }
    @DeleteMapping("/current/team/invitations/{id}") public ApiResponse<?> revoke(@PathVariable String id) { team.revoke(id); return ApiResponse.ok(null); }
    @PutMapping("/current/team/members/{id}") public ApiResponse<?> member(@PathVariable String id,@RequestBody ChangeMember body) {
        if (body.roles() == null) team.update(id,body.role(),body.active());
        else team.updateRoles(id,body.roles(),body.active());
        return ApiResponse.ok(null);
    }
}
