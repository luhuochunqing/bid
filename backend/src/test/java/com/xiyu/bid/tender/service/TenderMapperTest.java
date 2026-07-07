package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.tender.dto.ContactDTO;
import com.xiyu.bid.tender.dto.TenderDTO;
import com.xiyu.bid.tender.dto.TenderRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-402: TenderMapper.buildContacts 联系人数组构建逻辑测试。
 * 原仅 name 非空才加入列表，导致联系人2姓名为空但有电话时丢失。
 * 修复后：name/phone/tel/mail 任一字段有值即加入列表。
 */
class TenderMapperTest {

    private final TenderMapper tenderMapper = new TenderMapper();

    private Tender buildTender(String n1, String p1, String t1, String m1,
                               String n2, String p2, String t2, String m2) {
        return Tender.builder()
                .contactName(n1).contactPhone(p1).contactTel(t1).contactMail(m1)
                .contactName2(n2).contactPhone2(p2).contactTel2(t2).contactMail2(m2)
                .build();
    }

    // 测试要点 1: contactName2 为空、contactTel2 有值 → contactInfo 应包含第二个联系人
    @Test
    @DisplayName("CO-402: 联系人2姓名为空但电话有值时，应包含在 contactInfo 中")
    void buildContacts_contact2EmptyNameButHasTel_shouldIncludeSecondContact() {
        Tender tender = buildTender("张三", "13800000001", null, null,
                null, null, "010-12345678", null);

        List<ContactDTO> contacts = tenderMapper.buildContacts(tender);

        assertThat(contacts).hasSize(2);
        assertThat(contacts.get(0).getName()).isEqualTo("张三");
        assertThat(contacts.get(0).getPhone()).isEqualTo("13800000001");
        assertThat(contacts.get(1).getName()).isNull();
        assertThat(contacts.get(1).getTel()).isEqualTo("010-12345678");
    }

    // 测试要点 2: contactName2 有值、其他为空 → contactInfo 应包含第二个联系人
    @Test
    @DisplayName("CO-402: 联系人2姓名有值其他为空时，应包含在 contactInfo 中")
    void buildContacts_contact2HasNameOnly_shouldIncludeSecondContact() {
        Tender tender = buildTender("张三", "13800000001", null, null,
                "李四", null, null, null);

        List<ContactDTO> contacts = tenderMapper.buildContacts(tender);

        assertThat(contacts).hasSize(2);
        assertThat(contacts.get(1).getName()).isEqualTo("李四");
        assertThat(contacts.get(1).getPhone()).isNull();
        assertThat(contacts.get(1).getTel()).isNull();
        assertThat(contacts.get(1).getMail()).isNull();
    }

    // 测试要点 3: contactName2 和其他字段都为空 → contactInfo 不包含第二个联系人
    @Test
    @DisplayName("CO-402: 联系人2所有字段都为空时，不应包含在 contactInfo 中")
    void buildContacts_contact2AllFieldsEmpty_shouldNotIncludeSecondContact() {
        Tender tender = buildTender("张三", "13800000001", null, null,
                null, null, null, null);

        List<ContactDTO> contacts = tenderMapper.buildContacts(tender);

        assertThat(contacts).hasSize(1);
        assertThat(contacts.get(0).getName()).isEqualTo("张三");
    }

    // 补充: 联系人1姓名为空但电话有值 → 也应包含（修复同步应用于两个联系人）
    @Test
    @DisplayName("CO-402: 联系人1姓名为空但电话有值时，应包含在 contactInfo 中")
    void buildContacts_contact1EmptyNameButHasPhone_shouldIncludeFirstContact() {
        Tender tender = buildTender(null, "13800000001", null, null,
                null, null, null, null);

        List<ContactDTO> contacts = tenderMapper.buildContacts(tender);

        assertThat(contacts).hasSize(1);
        assertThat(contacts.get(0).getName()).isNull();
        assertThat(contacts.get(0).getPhone()).isEqualTo("13800000001");
    }

    // 补充: 两个联系人所有字段都为空 → contactInfo 为空数组
    @Test
    @DisplayName("CO-402: 两个联系人所有字段都为空时，contactInfo 应为空数组")
    void buildContacts_allFieldsEmpty_shouldReturnEmptyList() {
        Tender tender = buildTender(null, null, null, null, null, null, null, null);

        List<ContactDTO> contacts = tenderMapper.buildContacts(tender);

        assertThat(contacts).isEmpty();
    }

    // 补充: buildContactsFromDTO 同样应用新逻辑
    @Test
    @DisplayName("CO-402: buildContactsFromDTO 联系人2姓名为空但邮箱有值时，应包含在 contactInfo 中")
    void buildContactsFromDTO_contact2EmptyNameButHasMail_shouldIncludeSecondContact() {
        TenderDTO dto = TenderDTO.builder()
                .contactName("张三").contactPhone("13800000001")
                .contactMail2("li@example.com")
                .build();

        List<ContactDTO> contacts = tenderMapper.buildContactsFromDTO(dto);

        assertThat(contacts).hasSize(2);
        assertThat(contacts.get(1).getName()).isNull();
        assertThat(contacts.get(1).getMail()).isEqualTo("li@example.com");
    }

    // 补充: 空白字符串（whitespace）视为空值
    @Test
    @DisplayName("CO-402: 联系人2所有字段都是空白字符串时，不应包含在 contactInfo 中")
    void buildContacts_contact2AllFieldsBlank_shouldNotIncludeSecondContact() {
        Tender tender = buildTender("张三", "13800000001", null, null,
                "  ", "  ", "  ", "  ");

        List<ContactDTO> contacts = tenderMapper.buildContacts(tender);

        assertThat(contacts).hasSize(1);
    }

    // ===== CO-464/CO-500: purchaserId 映射测试 =====

    @Test
    @DisplayName("CO-464: toDTO(Tender) 应映射 purchaserId")
    void toDTO_fromTender_shouldMapPurchaserId() {
        Tender tender = Tender.builder()
                .id(1L)
                .title("测试标讯")
                .purchaserName("山东海化集团")
                .purchaserId(12345L)
                .build();

        TenderDTO dto = tenderMapper.toDTO(tender);

        assertThat(dto.getPurchaserId()).isEqualTo(12345L);
        assertThat(dto.getPurchaserName()).isEqualTo("山东海化集团");
    }

    @Test
    @DisplayName("CO-500: toDTO(TenderRequest) 应映射 purchaserId")
    void toDTO_fromRequest_shouldMapPurchaserId() {
        TenderRequest req = TenderRequest.builder()
                .title("测试标讯")
                .purchaserName("山东海化集团")
                .purchaserId(67890L)
                .build();

        TenderDTO dto = tenderMapper.toDTO(req);

        assertThat(dto.getPurchaserId()).isEqualTo(67890L);
    }

    @Test
    @DisplayName("CO-464: toEntity(TenderDTO) 应映射 purchaserId")
    void toEntity_shouldMapPurchaserId() {
        TenderDTO dto = TenderDTO.builder()
                .title("测试标讯")
                .purchaserName("山东海化集团")
                .purchaserId(11111L)
                .build();

        Tender entity = tenderMapper.toEntity(dto);

        assertThat(entity.getPurchaserId()).isEqualTo(11111L);
    }

    @Test
    @DisplayName("CO-500: updateEntity 应覆盖非 null 的 purchaserId；null 时保留原值")
    void updateEntity_purchaserId_shouldOverwriteOnlyWhenNonNull() {
        Tender target = Tender.builder()
                .id(1L)
                .title("原标讯")
                .purchaserId(100L)
                .build();

        // case 1: dto.purchaserId 非 null → 覆盖
        TenderDTO updateDto = TenderDTO.builder().purchaserId(200L).build();
        tenderMapper.updateEntity(target, updateDto);
        assertThat(target.getPurchaserId()).isEqualTo(200L);

        // case 2: dto.purchaserId 为 null → 保留原值
        TenderDTO nullDto = TenderDTO.builder().purchaserId(null).build();
        tenderMapper.updateEntity(target, nullDto);
        assertThat(target.getPurchaserId()).isEqualTo(200L);
    }
}
