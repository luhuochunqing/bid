package com.xiyu.bid.integration.organization.infrastructure.mapper;

import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PositionToRoleMapper - regex-based position to role mapping")
class PositionToRoleMapperTest {

    private PositionToRoleMapper createMapper(List<OrganizationIntegrationProperties.PositionToRoleMapping> mappings) {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.setPositionToRoleMappings(mappings);
        return new PositionToRoleMapper(properties);
    }

    private OrganizationIntegrationProperties.PositionToRoleMapping mapping(String pattern, String roleCode) {
        OrganizationIntegrationProperties.PositionToRoleMapping m = new OrganizationIntegrationProperties.PositionToRoleMapping();
        m.setPositionPattern(pattern);
        m.setRoleCode(roleCode);
        return m;
    }

    @Test
    @DisplayName("returns null when position text is null")
    void map_nullPosition_returnsNull() {
        PositionToRoleMapper mapper = createMapper(List.of(
                mapping("投标.*管理.*", "bid_admin")
        ));

        assertThat(mapper.map(null)).isNull();
    }

    @Test
    @DisplayName("returns null when position text is blank")
    void map_blankPosition_returnsNull() {
        PositionToRoleMapper mapper = createMapper(List.of(
                mapping("投标.*管理.*", "bid_admin")
        ));

        assertThat(mapper.map("   ")).isNull();
    }

    @Test
    @DisplayName("matches position name with regex pattern")
    void map_matchingPattern_returnsRoleCode() {
        PositionToRoleMapper mapper = createMapper(List.of(
                mapping("投标.*管理.*", "bid_admin"),
                mapping("投标.*组长.*", "bid_lead"),
                mapping("投标.*专员.*", "bid_specialist")
        ));

        assertThat(mapper.map("投标管理员")).isEqualTo("bid_admin");
        assertThat(mapper.map("投标组长")).isEqualTo("bid_lead");
        assertThat(mapper.map("投标专员")).isEqualTo("bid_specialist");
    }

    @Test
    @DisplayName("returns first match when multiple patterns match")
    void map_multipleMatches_returnsFirst() {
        PositionToRoleMapper mapper = createMapper(List.of(
                mapping("投标.*", "bid_specialist"),
                mapping("投标.*管理.*", "bid_admin")
        ));

        assertThat(mapper.map("投标管理员")).isEqualTo("bid_specialist");
    }

    @Test
    @DisplayName("returns null when no pattern matches")
    void map_noMatch_returnsNull() {
        PositionToRoleMapper mapper = createMapper(List.of(
                mapping("投标.*管理.*", "bid_admin")
        ));

        assertThat(mapper.map("销售人员")).isNull();
    }

    @Test
    @DisplayName("returns null when mappings list is empty")
    void map_emptyMappings_returnsNull() {
        PositionToRoleMapper mapper = createMapper(List.of());

        assertThat(mapper.map("投标管理员")).isNull();
    }

    @Test
    @DisplayName("skips mapping entries with blank pattern or role code")
    void map_blankMappingEntries_skips() {
        PositionToRoleMapper mapper = createMapper(List.of(
                mapping("", "bid_admin"),
                mapping("投标.*管理.*", ""),
                mapping(null, "bid_lead"),
                mapping("投标.*专员.*", "bid_specialist")
        ));

        assertThat(mapper.map("投标管理员")).isNull();
        assertThat(mapper.map("投标专员")).isEqualTo("bid_specialist");
    }

    @Test
    @DisplayName("normalizes returned role code to lowercase")
    void map_returnsLowercaseRoleCode() {
        PositionToRoleMapper mapper = createMapper(List.of(
                mapping("投标.*管理.*", "BID_ADMIN")
        ));

        assertThat(mapper.map("投标管理员")).isEqualTo("bid_admin");
    }

    @Test
    @DisplayName("matches partial text within position name")
    void map_partialMatch_returnsRoleCode() {
        PositionToRoleMapper mapper = createMapper(List.of(
                mapping("行政.*", "admin_staff"),
                mapping("项目.*负责.*", "sales")
        ));

        assertThat(mapper.map("高级行政助理")).isEqualTo("admin_staff");
        assertThat(mapper.map("项目总负责人")).isEqualTo("sales");
    }
}
