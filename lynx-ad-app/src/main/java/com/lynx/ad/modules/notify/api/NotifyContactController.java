package com.lynx.ad.modules.notify.api;

import com.lynx.ad.common.api.ApiResponse;
import com.lynx.ad.modules.notify.api.dto.ContactInfoVo;
import com.lynx.ad.modules.notify.api.dto.ContactRequest;
import com.lynx.ad.modules.notify.api.dto.ContactTicketVo;
import com.lynx.ad.modules.notify.application.NotifyContactService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notify")
public class NotifyContactController {

    private final NotifyContactService service;

    public NotifyContactController(NotifyContactService service) {
        this.service = service;
    }

    @GetMapping("/contact/info")
    public ApiResponse<ContactInfoVo> info() {
        return ApiResponse.ok(service.info());
    }

    @PostMapping("/contact")
    public ApiResponse<ContactTicketVo> contact(@RequestBody ContactRequest req) {
        return ApiResponse.ok(service.submit(req == null ? null : req.phone()));
    }
}
