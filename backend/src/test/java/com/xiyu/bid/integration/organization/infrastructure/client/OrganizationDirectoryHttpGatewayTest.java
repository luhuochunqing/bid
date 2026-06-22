package com.xiyu.bid.integration.organization.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.integration.organization.domain.OrganizationDirectoryLookupContext;
import com.xiyu.bid.integration.organization.domain.OrganizationDepartmentSnapshot;
import com.xiyu.bid.integration.organization.domain.OrganizationUserSnapshot;
import com.xiyu.bid.integration.organization.dto.OssMenuTreeNode;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withResourceNotFound;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@DisplayName("OrganizationDirectoryHttpGateway - customer org master data client")
class OrganizationDirectoryHttpGatewayTest {

    private OrganizationIntegrationProperties properties;

    @BeforeEach
    void setUp() {
        properties = defaultProperties();
    }

    @Test
    @DisplayName("fetches user master data by immutable userId via POST form")
    void fetchUserByUserId_mapsUserSnapshot() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://oss.example.test/subscription/msg/user"))
                .andExpect(content().formDataContains(Map.of("userId", "720518523")))
                .andRespond(withSuccess("""
                        {
                          "code": "200",
                          "data": {
                            "userId": 720518523,
                            "jobNumber": "wangwu",
                            "name": "王五",
                            "email": "wangwu@example.com",
                            "mobilePhone": "13900000000"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<OrganizationUserSnapshot> snapshot = gateway(restTemplate, properties)
                .fetchUserByUserId("720518523");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().externalUserId()).isEqualTo("720518523");
        assertThat(snapshot.get().username()).isEqualTo("wangwu");
        assertThat(snapshot.get().email()).isEqualTo("wangwu@example.com");
        assertThat(snapshot.get().enabled()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("sends trace source and auth headers to YAPI gateway via POST form")
    void fetchUserByUserId_sendsTraceSourceAndAuthHeaders() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://oss.example.test/subscription/msg/user"))
                .andExpect(header("EHSY-TraceID", "trace-1"))
                .andExpect(header("EHSY-SRCAPP", "BidSystem"))
                .andExpect(content().formDataContains(Map.of("userId", "720518523")))
                .andRespond(withSuccess("""
                        {
                          "code": "200",
                          "data": {
                            "userId": 720518523,
                            "jobNumber": "wangwu",
                            "name": "王五",
                            "email": "wangwu@example.com",
                            "mobilePhone": "13900000000"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        OrganizationIntegrationProperties props = defaultProperties();
        props.getDirectory().setSourceApp("BidSystem");

        Optional<OrganizationUserSnapshot> snapshot = gateway(restTemplate, props)
                .fetchUserByUserId("720518523", new OrganizationDirectoryLookupContext("trace-1", "oss"));

        assertThat(snapshot).isPresent();
        server.verify();
    }

    @Test
    @DisplayName("maps code 200 result envelope as successful payload via POST form")
    void fetchUserByUserId_code200ResultEnvelope_mapsUserSnapshot() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://oss.example.test/subscription/msg/user"))
                .andExpect(content().formDataContains(Map.of("userId", "720518523")))
                .andRespond(withSuccess("""
                        {
                          "code": "200",
                          "result": {
                            "userId": 720518523,
                            "jobNumber": "wangwu",
                            "name": "王五"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<OrganizationUserSnapshot> snapshot = gateway(restTemplate, properties)
                .fetchUserByUserId("720518523");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().externalUserId()).isEqualTo("720518523");
        server.verify();
    }

    @Test
    @DisplayName("maps 401 to non retryable gateway exception via POST form")
    void fetchUserByUserId_unauthorized_nonRetryable() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://oss.example.test/subscription/msg/user"))
                .andExpect(content().formDataContains(Map.of("userId", "720518523")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> gateway(restTemplate, properties)
                .fetchUserByUserId("720518523"))
                .isInstanceOf(OrganizationDirectoryHttpGatewayException.class)
                .satisfies(ex -> assertThat(((OrganizationDirectoryHttpGatewayException) ex).retryable()).isFalse());
        server.verify();
    }

    @Test
    @DisplayName("fetches department master data by immutable deptId via POST form")
    void fetchDepartmentByDeptId_mapsDepartmentSnapshot() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://oss.example.test/subscription/msg/dept"))
                .andExpect(content().formDataContains(Map.of("deptId", "3730158")))
                .andRespond(withSuccess("""
                        {
                          "data": {
                            "deptId": 3730158,
                            "name": "销售部",
                            "parentId": 1000
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<OrganizationDepartmentSnapshot> snapshot = gateway(restTemplate, properties)
                .fetchDepartmentByDeptId("3730158");

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().externalDeptId()).isEqualTo("3730158");
        assertThat(snapshot.get().departmentName()).isEqualTo("销售部");
        assertThat(snapshot.get().parentExternalDeptId()).isEqualTo("1000");
        server.verify();
    }

    @Test
    @DisplayName("returns empty for 404 on user detail via POST form")
    void fetchUserByUserId_notFound_returnsEmpty() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://oss.example.test/subscription/msg/user"))
                .andExpect(content().formDataContains(Map.of("userId", "unknown-user")))
                .andRespond(withResourceNotFound());

        Optional<OrganizationUserSnapshot> snapshot = gateway(restTemplate, properties)
                .fetchUserByUserId("unknown-user");

        assertThat(snapshot).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("maps user time window to list of snapshots via POST json")
    void listUsersByWindow_returnsUserSnapshots() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://oss.example.test/subscription/msg/getUserByTimeWindow"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {
                          "total": 3,
                          "code": 0,
                          "data": [
                            { "userId": 720518523, "jobNumber": "wangwu", "name": "王五" },
                            { "userId": 820518524, "jobNumber": "zhangsan", "name": "张三" },
                            { "userId": 920518525, "jobNumber": "lisi", "name": "李四" }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<OrganizationUserSnapshot> snapshots = gateway(restTemplate, properties)
                .listUsersByWindow(LocalDateTime.parse("2026-05-01T10:00"), LocalDateTime.parse("2026-05-02T10:30"));

        assertThat(snapshots).hasSize(3);
        assertThat(snapshots.get(0).externalUserId()).isEqualTo("720518523");
        assertThat(snapshots.get(1).externalUserId()).isEqualTo("820518524");
        assertThat(snapshots.get(2).externalUserId()).isEqualTo("920518525");
        server.verify();
    }

    @Test
    @DisplayName("maps department time window to list of snapshots via POST json")
    void listDepartmentsByWindow_returnsDepartmentSnapshots() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://oss.example.test/subscription/msg/getDeptByTimeWindow"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {
                          "total": 2,
                          "code": 0,
                          "data": [
                            { "deptId": 3730158, "name": "销售部", "parentId": 1000 },
                            { "deptId": 4730159, "name": "研发部", "parentId": 1000 }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<OrganizationDepartmentSnapshot> snapshots = gateway(restTemplate, properties)
                .listDepartmentsByWindow(LocalDateTime.parse("2026-05-01T10:00"), LocalDateTime.parse("2026-05-02T10:30"));

        assertThat(snapshots).hasSize(2);
        assertThat(snapshots.get(0).externalDeptId()).isEqualTo("3730158");
        assertThat(snapshots.get(0).parentExternalDeptId()).isEqualTo("1000");
        assertThat(snapshots.get(1).externalDeptId()).isEqualTo("4730159");
        assertThat(snapshots.get(1).parentExternalDeptId()).isEqualTo("1000");
        server.verify();
    }


    @Test
    @DisplayName("aggregates records across multiple paginated pages using offset fallback")
    void listUsersByWindow_multiPage_aggregatesAllPages() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        /* First page: total=5, batch of 2 */
        server.expect(requestTo("https://oss.example.test/subscription/msg/getUserByTimeWindow"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {"total":5,"code":0,"data":[
                          {"userId":"1","jobNumber":"u1","name":"User1","email":"u1@t.com","mobilePhone":"111"},
                          {"userId":"2","jobNumber":"u2","name":"User2","email":"u2@t.com","mobilePhone":"222"}
                        ]}
                        """, MediaType.APPLICATION_JSON));
        /* Second page: offset=2, next batch of 2 */
        server.expect(requestTo("https://oss.example.test/subscription/msg/getUserByTimeWindow"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {"total":5,"code":0,"data":[
                          {"userId":"3","jobNumber":"u3","name":"User3","email":"u3@t.com","mobilePhone":"333"},
                          {"userId":"4","jobNumber":"u4","name":"User4","email":"u4@t.com","mobilePhone":"444"}
                        ]}
                        """, MediaType.APPLICATION_JSON));
        /* Third page: offset=4, last single record */
        server.expect(requestTo("https://oss.example.test/subscription/msg/getUserByTimeWindow"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {"total":5,"code":0,"data":[
                          {"userId":"5","jobNumber":"u5","name":"User5","email":"u5@t.com","mobilePhone":"555"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<OrganizationUserSnapshot> snapshots = gateway(restTemplate, properties)
                .listUsersByWindow(LocalDateTime.parse("2026-05-01T10:00"), LocalDateTime.parse("2026-05-02T10:30"));

        assertThat(snapshots).hasSize(5);
        assertThat(snapshots.get(0).externalUserId()).isEqualTo("1");
        assertThat(snapshots.get(1).externalUserId()).isEqualTo("2");
        assertThat(snapshots.get(2).externalUserId()).isEqualTo("3");
        assertThat(snapshots.get(3).externalUserId()).isEqualTo("4");
        assertThat(snapshots.get(4).externalUserId()).isEqualTo("5");
        server.verify();
    }
    @Test
    @DisplayName("batch job/role lookup maps response by job number")
    void getUserJobAndRoleListByJobNumbers_mapsByJobNumber() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://oss.example.test/oss/admin-web/v1/output/data/getUserJobListByJobNumberList"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {
                          "code": 200,
                          "message": "success",
                          "data": [
                            { "jobNumber": "08402", "jobName": "项目经理", "sysRoleList": ["投标项目负责人"], "status": "启用", "username": "张三" },
                            { "jobNumber": "08640", "jobName": "项目总监", "sysRoleList": ["投标项目负责人", "管理员"], "status": "启用", "username": "李四" }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        java.util.Map<String, com.xiyu.bid.integration.organization.dto.OssUserJobAndRoleDto> result =
                gateway(restTemplate, defaultProperties()).getUserJobAndRoleListByJobNumbers(List.of("08402", "08640"));

        assertThat(result).hasSize(2);
        assertThat(result.get("08402").jobName()).isEqualTo("项目经理");
        assertThat(result.get("08402").sysRoleList()).containsExactly("投标项目负责人");
        assertThat(result.get("08640").jobName()).isEqualTo("项目总监");
        assertThat(result.get("08640").sysRoleList()).containsExactly("投标项目负责人", "管理员");
        server.verify();
    }

    @Test
    @DisplayName("batch job/role lookup maps object-style response keyed by job number")
    void getUserJobAndRoleListByJobNumbers_objectStyleResponse_mapsByJobNumber() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://oss.example.test/oss/admin-web/v1/output/data/getUserJobListByJobNumberList"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "msg": "操作成功",
                          "data": {
                            "08402": {
                              "jobName": "Java开发工程师",
                              "sysRoleList": [
                                { "id": 18, "roleName": "员工测试", "status": 0 },
                                { "id": 2, "roleName": "管理员", "status": 1, "isDefault": 1 }
                              ],
                              "employeeStatus": 3,
                              "jobNumber": "08402",
                              "username": "张锡臣",
                              "status": 1
                            },
                            "08640": {
                              "jobName": "运输管理专员",
                              "sysRoleList": [],
                              "employeeStatus": 8,
                              "jobNumber": "08640",
                              "username": "范子文",
                              "status": 0
                            }
                          },
                          "timestamp": 1747292370541
                        }
                        """, MediaType.APPLICATION_JSON));

        java.util.Map<String, com.xiyu.bid.integration.organization.dto.OssUserJobAndRoleDto> result =
                gateway(restTemplate, defaultProperties()).getUserJobAndRoleListByJobNumbers(List.of("08402", "08640"));

        assertThat(result).hasSize(2);
        assertThat(result.get("08402").jobName()).isEqualTo("Java开发工程师");
        assertThat(result.get("08402").sysRoleList()).containsExactly("员工测试", "管理员");
        assertThat(result.get("08402").username()).isEqualTo("张锡臣");
        assertThat(result.get("08640").jobName()).isEqualTo("运输管理专员");
        assertThat(result.get("08640").sysRoleList()).isEmpty();
        assertThat(result.get("08640").username()).isEqualTo("范子文");
        server.verify();
    }

    @Test
    @DisplayName("batch job/role lookup returns partial map on failure and does not throw")
    void getUserJobAndRoleListByJobNumbers_failure_returnsPartialMap() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://oss.example.test/oss/admin-web/v1/output/data/getUserJobListByJobNumberList"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        java.util.Map<String, com.xiyu.bid.integration.organization.dto.OssUserJobAndRoleDto> result =
                gateway(restTemplate, defaultProperties()).getUserJobAndRoleListByJobNumbers(List.of("08402"));

        assertThat(result).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("batch job/role lookup returns empty for blank path")
    void getUserJobAndRoleListByJobNumbers_blankPath_returnsEmpty() {
        RestTemplate restTemplate = new RestTemplate();
        OrganizationIntegrationProperties props = defaultProperties();
        props.getDirectory().setBatchJobRoleLookupPath("");

        java.util.Map<String, com.xiyu.bid.integration.organization.dto.OssUserJobAndRoleDto> result =
                gateway(restTemplate, props).getUserJobAndRoleListByJobNumbers(List.of("08402"));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("fetches user menu tree via GET with query params")
    void fetchUserMenuTree_mapsMenuTreeNodes() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://oss.example.test/sysMenuUrl/getUserMenuTree")))
                .andExpect(queryParam("jobNumber", "08402"))
                .andExpect(queryParam("systemName", "xiyu-bid-poc"))
                .andExpect(queryParam("menuRetrievalType", "2"))
                .andRespond(withSuccess("""
                        {
                          "code": 0,
                          "data": [
                            {
                              "id": 1,
                              "menuCode": "projectmanager",
                              "menuName": "项目管理",
                              "menuType": "M",
                              "children": [
                                {
                                  "id": 2,
                                  "menuCode": "bidding",
                                  "menuName": "投标",
                                  "menuType": "C",
                                  "children": []
                                }
                              ]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        Optional<List<OssMenuTreeNode>> result = gateway(restTemplate, defaultProperties())
                .fetchUserMenuTree("08402");

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
        assertThat(result.get().get(0).menuCode()).isEqualTo("projectmanager");
        assertThat(result.get().get(0).children()).hasSize(1);
        assertThat(result.get().get(0).children().get(0).menuCode()).isEqualTo("bidding");
        server.verify();
    }

    @Test
    @DisplayName("sends optional auth header when fetching user menu tree")
    void fetchUserMenuTree_sendsConfiguredAuthHeader() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        OrganizationIntegrationProperties props = defaultProperties();
        props.getDirectory().setAuthHeaderName("Authorization");
        props.getDirectory().setAuthToken("Bearer test-token");

        server.expect(requestTo(org.hamcrest.Matchers.startsWith("https://oss.example.test/sysMenuUrl/getUserMenuTree")))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(queryParam("jobNumber", "08402"))
                .andExpect(queryParam("systemName", "xiyu-bid-poc"))
                .andExpect(queryParam("menuRetrievalType", "2"))
                .andRespond(withSuccess("{\"code\":0,\"data\":[{\"menuCode\":\"1002\"}]}", MediaType.APPLICATION_JSON));

        Optional<List<OssMenuTreeNode>> result = gateway(restTemplate, props).fetchUserMenuTree("08402");

        assertThat(result).isPresent();
        assertThat(result.get()).hasSize(1);
        server.verify();
    }

    @Test
    @DisplayName("returns empty when user menu tree path is blank")
    void fetchUserMenuTree_blankPath_returnsEmpty() {
        RestTemplate restTemplate = new RestTemplate();
        OrganizationIntegrationProperties props = defaultProperties();
        props.getDirectory().setUserMenuTreePath("");

        Optional<List<OssMenuTreeNode>> result = gateway(restTemplate, props).fetchUserMenuTree("08402");

        assertThat(result).isEmpty();
    }

    private static OrganizationDirectoryHttpGateway gateway(
            RestTemplate restTemplate,
            OrganizationIntegrationProperties props) {
        return new OrganizationDirectoryHttpGateway(restTemplate, restTemplate, new ObjectMapper(), props);
    }

    private static OrganizationIntegrationProperties defaultProperties() {
        OrganizationIntegrationProperties props = new OrganizationIntegrationProperties();
        props.getDirectory().setBaseUrl("https://oss.example.test");
        props.getDirectory().setUserDetailPath("/subscription/msg/user");
        props.getDirectory().setDepartmentDetailPath("/subscription/msg/dept");
        props.getDirectory().setUserWindowPath("/subscription/msg/getUserByTimeWindow");
        props.getDirectory().setDepartmentWindowPath("/subscription/msg/getDeptByTimeWindow");
        props.getDirectory().setBatchJobRoleLookupPath("/oss/admin-web/v1/output/data/getUserJobListByJobNumberList");
        props.getDirectory().setBatchQuerySize(50);
        return props;
    }
}
