package com.xiyu.bid.tender.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 联系人子对象（用于集成接口返回的 contacts 数组）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactDTO {

    /** 姓名 */
    private String name;

    /** 手机号 */
    private String phone;

    /** 座机号 */
    private String tel;

    /** 邮箱 */
    private String mail;

    /** 联系人性质（客户/代理/标前） */
    private String nature;
}
