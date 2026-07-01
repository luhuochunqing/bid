package com.xiyu.bid.crm.infrastructure;

import com.xiyu.bid.crm.application.CrmChancePageResult;
import com.xiyu.bid.crm.application.CrmChanceService;
import com.xiyu.bid.crm.application.CrmContactPersonService;
import com.xiyu.bid.crm.infrastructure.dto.BidInfoSyncDTO;
import com.xiyu.bid.crm.infrastructure.dto.ContactPersonInfoVO;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChancePageRequest;
import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceSearchByTenderRequest;
import com.xiyu.bid.dto.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

/**
 * CRM 商机与对接人查询控制器。
 * <p>代理客户 CRM API，为前端提供统一的商机列表、标讯回传和对接人查询端点。
 * <p>副作用层：只做入参接收、Service 调用、响应封装。
 */
@RestController
@RequestMapping("/api/xiyu/crm/chances")
@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
public class CrmChanceController {

    private final CrmChanceService chanceService;
    private final CrmContactPersonService contactPersonService;

    public CrmChanceController(CrmChanceService chanceService,
                               CrmContactPersonService contactPersonService) {
        this.chanceService = chanceService;
        this.contactPersonService = contactPersonService;
    }

    /**
     * 商机列表（分页）。
     * 代理客户 POST /customer-chance/page-list。
     */
    @PostMapping("/page-list")
    public ResponseEntity<ApiResponse<CrmChancePageResult>> pageList(
            @RequestBody CustomerChancePageRequest request, Principal principal) {
        CrmChancePageResult result = chanceService.pageList(request, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("查询成功", result));
    }

    /**
     * 按标讯信息查询 CRM 商机（产品蓝图匹配规则）。
     * <p>使用招标主体、报名截止时间、开标时间组合匹配。
     */
    @PostMapping("/search-by-tender")
    public ResponseEntity<ApiResponse<CrmChancePageResult>> searchByTender(
            @RequestBody CustomerChanceSearchByTenderRequest request, Principal principal) {
        CrmChancePageResult result = chanceService.searchByTender(request, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("查询成功", result));
    }

    /**
     * 标讯回传。
     * 代理客户 POST /customer-chance/bidInfoSync。
     */
    @PostMapping("/bid-info-sync")
    public ResponseEntity<ApiResponse<Void>> bidInfoSync(@RequestBody BidInfoSyncDTO request,
                                                         Principal principal) {
        boolean success = chanceService.bidInfoSync(request, principal.getName());
        if (success) {
            return ResponseEntity.ok(ApiResponse.success("回传成功", null));
        }
        return ResponseEntity.ok(ApiResponse.error("回传失败"));
    }

    /**
     * 对接人列表。
     * 代理客户 POST /contact-person-info/page-list。
     */
    @PostMapping("/contact-persons")
    public ResponseEntity<ApiResponse<List<ContactPersonInfoVO>>> contactPersons(
            @RequestBody Long ccId, Principal principal) {
        List<ContactPersonInfoVO> list = contactPersonService.pageList(ccId, principal.getName());
        return ResponseEntity.ok(ApiResponse.success("查询成功", list));
    }
}
