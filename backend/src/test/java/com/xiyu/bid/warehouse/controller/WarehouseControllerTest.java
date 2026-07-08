package com.xiyu.bid.warehouse.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.warehouse.application.WarehouseNameValidator;
import com.xiyu.bid.warehouse.domain.WarehouseType;
import com.xiyu.bid.warehouse.dto.WarehouseDTO;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseRepository;
import com.xiyu.bid.warehouse.service.WarehouseLogService;
import com.xiyu.bid.warehouse.service.WarehouseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WarehouseController 新增仓库接口契约测试。
 *
 * <p>覆盖契约：
 * <ul>
 *   <li>POST /api/knowledge/warehouses 名称不重复 → 201 Created</li>
 *   <li>POST /api/knowledge/warehouses 名称已存在 → 400 Bad Request，msg 为"该仓库已存在"</li>
 * </ul>
 */
class WarehouseControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private WarehouseRepository repo;
    private WarehouseMapper warehouseMapper;
    private WarehouseLogService warehouseLogService;
    private WarehouseNameValidator warehouseNameValidator;
    private UserResolver userResolver;

    @BeforeEach
    void setUp() {
        repo = mock(WarehouseRepository.class);
        warehouseMapper = mock(WarehouseMapper.class);
        warehouseLogService = mock(WarehouseLogService.class);
        warehouseNameValidator = mock(WarehouseNameValidator.class);
        userResolver = mock(UserResolver.class);

        WarehouseController controller = new WarehouseController(
                repo, null, null, null, warehouseMapper, warehouseLogService,
                warehouseNameValidator, userResolver);

        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    @DisplayName("POST /api/knowledge/warehouses: 名称不重复 → 201 Created")
    void create_uniqueName_returns201() throws Exception {
        WarehouseDTO dto = validDto("新仓库");
        WarehouseEntity entity = toEntity(dto);
        entity.setId(100L);
        User operator = User.builder().id(1L).username("admin").fullName("管理员").build();

        when(warehouseNameValidator.isNameTaken("新仓库")).thenReturn(false);
        when(warehouseMapper.toEntity(any(WarehouseDTO.class))).thenReturn(entity);
        when(userResolver.resolveCurrentUser()).thenReturn(operator);
        when(repo.save(any(WarehouseEntity.class))).thenReturn(entity);

        mockMvc.perform(post("/api/knowledge/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("新仓库"));
    }

    @Test
    @DisplayName("POST /api/knowledge/warehouses: 名称已存在 → 400 并提示该仓库已存在")
    void create_duplicateName_returns400() throws Exception {
        WarehouseDTO dto = validDto("已有仓库");

        when(warehouseNameValidator.isNameTaken("已有仓库")).thenReturn(true);

        mockMvc.perform(post("/api/knowledge/warehouses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("该仓库已存在"));

        verify(warehouseNameValidator).isNameTaken("已有仓库");
        verify(warehouseMapper, never()).toEntity(any());
        verify(repo, never()).save(any());
        verify(warehouseLogService, never()).saveLog(any(), any(), any(), any(), any(), any(), any(), any());
    }

    private WarehouseDTO validDto(String name) {
        return WarehouseDTO.builder()
                .name(name)
                .type(WarehouseType.SELF_OPERATED)
                .region("华东")
                .province("上海市")
                .address("测试地址")
                .area(BigDecimal.valueOf(100))
                .contactPerson("张三")
                .startDate(LocalDate.of(2026, 1, 1))
                .endDate(LocalDate.of(2026, 12, 31))
                .lessor("出租方")
                .lessee("承租方")
                .hasPropertyCert(false)
                .hasInvoice(false)
                .hasPhotos(false)
                .hasLeaseContract(false)
                .build();
    }

    private WarehouseEntity toEntity(WarehouseDTO dto) {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setName(dto.getName());
        entity.setType(dto.getType());
        entity.setRegion(dto.getRegion());
        entity.setProvince(dto.getProvince());
        entity.setAddress(dto.getAddress());
        entity.setArea(dto.getArea());
        entity.setContactPerson(dto.getContactPerson());
        entity.setStartDate(dto.getStartDate());
        entity.setEndDate(dto.getEndDate());
        entity.setLessor(dto.getLessor());
        entity.setLessee(dto.getLessee());
        entity.setHasPropertyCert(dto.getHasPropertyCert());
        entity.setHasInvoice(dto.getHasInvoice());
        entity.setHasPhotos(dto.getHasPhotos());
        entity.setHasLeaseContract(dto.getHasLeaseContract());
        return entity;
    }
}
