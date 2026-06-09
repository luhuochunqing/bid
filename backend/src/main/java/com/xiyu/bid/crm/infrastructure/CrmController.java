package com.xiyu.bid.crm.infrastructure;

import com.xiyu.bid.crm.application.CrmAuthService;
import com.xiyu.bid.crm.application.CrmCustomerService;
import com.xiyu.bid.crm.application.CrmEmployeeService;
import com.xiyu.bid.crm.application.CrmMenuService;
import com.xiyu.bid.crm.application.CrmMessageService;
import com.xiyu.bid.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/xiyu/crm")
public class CrmController {

    private final CrmCustomerService customerService;
    private final CrmAuthService authService;
    private final CrmMenuService menuService;
    private final CrmEmployeeService employeeService;
    private final CrmMessageService messageService;

    public CrmController(CrmCustomerService customerService, CrmAuthService authService,
                         CrmMenuService menuService, CrmEmployeeService employeeService,
                         CrmMessageService messageService) {
        this.customerService = customerService;
        this.authService = authService;
        this.menuService = menuService;
        this.employeeService = employeeService;
        this.messageService = messageService;
    }

    @GetMapping("/customers")
    public ResponseEntity<ApiResponse<Object>> searchCustomers(@RequestParam String keyword,
                                             @RequestParam(defaultValue = "20") int pageSize) {
        var response = customerService.searchCustomers(keyword, pageSize);
        if (response.success()) {
            return ResponseEntity.ok(ApiResponse.success("Customers retrieved", response.data()));
        }
        return ResponseEntity.ok(ApiResponse.error(response.msg()));
    }

    @GetMapping("/customers/{customerId}/contacts")
    public ResponseEntity<ApiResponse<Object>> getContacts(@PathVariable String customerId) {
        var response = customerService.getCustomerContacts(List.of(customerId));
        if (response.success()) {
            return ResponseEntity.ok(ApiResponse.success("Contacts retrieved", response.data()));
        }
        return ResponseEntity.ok(ApiResponse.error(response.msg()));
    }

    @GetMapping("/menus")
    public ResponseEntity<ApiResponse<Object>> getMenuTree(@RequestParam String systemType) {
        var response = menuService.getMenuTree(systemType);
        if (response.success()) {
            return ResponseEntity.ok(ApiResponse.success("Menu tree retrieved", response.data()));
        }
        return ResponseEntity.ok(ApiResponse.error(response.msg()));
    }

    @GetMapping("/employees/{token}")
    public ResponseEntity<ApiResponse<Object>> getEmployee(@PathVariable String token) {
        var response = employeeService.getEmployeeByToken(token);
        if (response.success()) {
            return ResponseEntity.ok(ApiResponse.success("Employee retrieved", response.data()));
        }
        return ResponseEntity.ok(ApiResponse.error(response.msg()));
    }

    /**
     * 发送消息（企微+站内信）。
     * 代理客户 POST /common/sendMessage。
     *
     * 请求体：
     * {
     *   "recipientNos": ["工号"],
     *   "title": "标题",
     *   "content": "内容",
     *   "flag": 1
     * }
     */
    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<Void>> sendMessage(@RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        var recipientNos = (List<String>) body.get("recipientNos");
        var title = (String) body.getOrDefault("title", "");
        var content = (String) body.getOrDefault("content", "");
        var flag = body.get("flag") instanceof Number n ? n.intValue() : 1;

        if (recipientNos == null || recipientNos.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error("recipientNos is required"));
        }

        var response = messageService.sendMessage(recipientNos, title, content, flag);
        if (response.success()) {
            return ResponseEntity.ok(ApiResponse.success("消息发送成功", null));
        }
        return ResponseEntity.ok(ApiResponse.error("消息发送失败: " + response.msg()));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<ApiResponse<Map<String, String>>> logout() {
        authService.logout();
        return ResponseEntity.ok(ApiResponse.success("Logged out", Map.of("result", "ok")));
    }
}
