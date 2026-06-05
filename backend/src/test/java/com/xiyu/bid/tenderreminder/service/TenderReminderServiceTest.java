package com.xiyu.bid.tenderreminder.service;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.tenderreminder.domain.TenderReminderPolicy;
import com.xiyu.bid.tenderreminder.dto.CreateReminderRequest;
import com.xiyu.bid.tenderreminder.dto.ReminderSettingDTO;
import com.xiyu.bid.tenderreminder.dto.TenderReminderMapper;
import com.xiyu.bid.tenderreminder.dto.UpdateReminderRequest;
import com.xiyu.bid.tenderreminder.entity.ReminderType;
import com.xiyu.bid.tenderreminder.entity.TenderReminderSetting;
import com.xiyu.bid.tenderreminder.repository.TenderReminderSettingRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 标讯提醒服务测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TenderReminderService 测试")
class TenderReminderServiceTest {

    @Mock
    private TenderReminderSettingRepository reminderRepository;

    @Mock
    private TenderRepository tenderRepository;

    private TenderReminderMapper mapper;
    private TenderReminderService service;

    @BeforeEach
    void setUp() {
        mapper = new TenderReminderMapper(new ObjectMapper());
        service = new TenderReminderService(reminderRepository, tenderRepository, mapper);
    }

    @Nested
    @DisplayName("createReminder")
    class CreateReminderTests {

        @Test
        @DisplayName("应成功创建提醒设置")
        void shouldCreateReminderSuccessfully() {
            Long tenderId = 1L;
            Long userId = 100L;
            CreateReminderRequest request = CreateReminderRequest.builder()
                    .reminderType(ReminderType.REGISTRATION_DEADLINE)
                    .remindBeforeHours(24)
                    .reminderTargets(List.of(
                            CreateReminderRequest.ReminderTargetDTO.builder()
                                    .userId(1L)
                                    .userName("张三")
                                    .build()
                    ))
                    .enabled(true)
                    .build();

            Tender savedEntity = Tender.builder().id(tenderId).title("测试标讯").build();

            when(tenderRepository.existsById(tenderId)).thenReturn(true);
            when(tenderRepository.findById(tenderId)).thenReturn(Optional.of(savedEntity));
            when(reminderRepository.findByTenderIdAndReminderType(tenderId, ReminderType.REGISTRATION_DEADLINE))
                    .thenReturn(Optional.empty());
            when(reminderRepository.save(any(TenderReminderSetting.class)))
                    .thenAnswer(invocation -> {
                        TenderReminderSetting setting = invocation.getArgument(0);
                        setting.setId(1L);
                        setting.setCreatedAt(LocalDateTime.now());
                        return setting;
                    });

            ReminderSettingDTO result = service.createReminder(tenderId, request, userId);

            assertNotNull(result);
            assertEquals(tenderId, result.getTenderId());
            assertEquals(ReminderType.REGISTRATION_DEADLINE, result.getReminderType());
            assertEquals(24, result.getRemindBeforeHours());
            assertEquals("测试标讯", result.getTenderTitle());

            verify(reminderRepository).save(any(TenderReminderSetting.class));
        }

        @Test
        @DisplayName("标讯不存在应抛出异常")
        void shouldThrowExceptionWhenTenderNotExists() {
            Long tenderId = 999L;
            CreateReminderRequest request = CreateReminderRequest.builder()
                    .reminderType(ReminderType.REGISTRATION_DEADLINE)
                    .remindBeforeHours(24)
                    .reminderTargets(List.of())
                    .build();

            when(tenderRepository.existsById(tenderId)).thenReturn(false);

            assertThrows(IllegalArgumentException.class,
                    () -> service.createReminder(tenderId, request, 1L));

            verify(reminderRepository, never()).save(any());
        }

        @Test
        @DisplayName("已存在同类型提醒应抛出异常")
        void shouldThrowExceptionWhenDuplicateType() {
            Long tenderId = 1L;
            CreateReminderRequest request = CreateReminderRequest.builder()
                    .reminderType(ReminderType.REGISTRATION_DEADLINE)
                    .remindBeforeHours(24)
                    .reminderTargets(List.of(
                            CreateReminderRequest.ReminderTargetDTO.builder()
                                    .userId(1L)
                                    .userName("张三")
                                    .build()
                    ))
                    .build();

            TenderReminderSetting existing = TenderReminderSetting.builder()
                    .id(1L)
                    .tenderId(tenderId)
                    .reminderType(ReminderType.REGISTRATION_DEADLINE)
                    .build();

            when(tenderRepository.existsById(tenderId)).thenReturn(true);
            when(reminderRepository.findByTenderIdAndReminderType(tenderId, ReminderType.REGISTRATION_DEADLINE))
                    .thenReturn(Optional.of(existing));

            assertThrows(IllegalStateException.class,
                    () -> service.createReminder(tenderId, request, 1L));

            verify(reminderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("updateReminder")
    class UpdateReminderTests {

        @Test
        @DisplayName("应成功更新提醒设置")
        void shouldUpdateReminderSuccessfully() {
            Long reminderId = 1L;
            TenderReminderSetting existing = TenderReminderSetting.builder()
                    .id(reminderId)
                    .tenderId(1L)
                    .reminderType(ReminderType.REGISTRATION_DEADLINE)
                    .remindBeforeHours(24)
                    .reminderTargets("[{\"userId\":1,\"userName\":\"张三\"}]")
                    .enabled(true)
                    .build();

            UpdateReminderRequest request = UpdateReminderRequest.builder()
                    .remindBeforeHours(48)
                    .enabled(false)
                    .build();

            when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(existing));
            when(reminderRepository.save(any(TenderReminderSetting.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(tenderRepository.findById(1L)).thenReturn(Optional.of(
                    Tender.builder().id(1L).title("测试标讯").build()));

            Optional<ReminderSettingDTO> result = service.updateReminder(reminderId, request);

            assertTrue(result.isPresent());
            assertEquals(48, result.get().getRemindBeforeHours());
            assertFalse(result.get().getEnabled());

            ArgumentCaptor<TenderReminderSetting> captor = ArgumentCaptor.forClass(TenderReminderSetting.class);
            verify(reminderRepository).save(captor.capture());
            assertEquals(48, captor.getValue().getRemindBeforeHours());
        }

        @Test
        @DisplayName("提醒不存在应返回空Optional")
        void shouldReturnEmptyWhenReminderNotFound() {
            when(reminderRepository.findById(999L)).thenReturn(Optional.empty());

            Optional<ReminderSettingDTO> result = service.updateReminder(999L,
                    UpdateReminderRequest.builder().enabled(false).build());

            assertTrue(result.isEmpty());
            verify(reminderRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("deleteReminder")
    class DeleteReminderTests {

        @Test
        @DisplayName("应成功删除提醒设置")
        void shouldDeleteReminderSuccessfully() {
            Long reminderId = 1L;
            when(reminderRepository.existsById(reminderId)).thenReturn(true);

            service.deleteReminder(reminderId);

            verify(reminderRepository).deleteById(reminderId);
        }

        @Test
        @DisplayName("提醒不存在应抛出异常")
        void shouldThrowExceptionWhenReminderNotFound() {
            when(reminderRepository.existsById(999L)).thenReturn(false);

            assertThrows(IllegalArgumentException.class,
                    () -> service.deleteReminder(999L));

            verify(reminderRepository, never()).deleteById(any());
        }
    }

    @Nested
    @DisplayName("toggleReminder")
    class ToggleReminderTests {

        @Test
        @DisplayName("应切换提醒启用状态")
        void shouldToggleReminderStatus() {
            Long reminderId = 1L;
            TenderReminderSetting existing = TenderReminderSetting.builder()
                    .id(reminderId)
                    .tenderId(1L)
                    .reminderType(ReminderType.REGISTRATION_DEADLINE)
                    .enabled(true)
                    .build();

            when(reminderRepository.findById(reminderId)).thenReturn(Optional.of(existing));
            when(reminderRepository.save(any(TenderReminderSetting.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(tenderRepository.findById(1L)).thenReturn(Optional.of(
                    Tender.builder().id(1L).title("测试标讯").build()));

            Optional<ReminderSettingDTO> result = service.toggleReminder(reminderId);

            assertTrue(result.isPresent());
            assertFalse(result.get().getEnabled()); // true -> false

            ArgumentCaptor<TenderReminderSetting> captor = ArgumentCaptor.forClass(TenderReminderSetting.class);
            verify(reminderRepository).save(captor.capture());
            assertFalse(captor.getValue().getEnabled());
        }
    }

    @Nested
    @DisplayName("getRemindersByTenderId")
    class GetRemindersByTenderIdTests {

        @Test
        @DisplayName("应返回标讯的所有提醒设置")
        void shouldReturnAllRemindersForTender() {
            Long tenderId = 1L;
            Tender tender = Tender.builder().id(tenderId).title("测试标讯").build();

            List<TenderReminderSetting> settings = List.of(
                    TenderReminderSetting.builder()
                            .id(1L)
                            .tenderId(tenderId)
                            .reminderType(ReminderType.REGISTRATION_DEADLINE)
                            .remindBeforeHours(24)
                            .enabled(true)
                            .build(),
                    TenderReminderSetting.builder()
                            .id(2L)
                            .tenderId(tenderId)
                            .reminderType(ReminderType.BID_OPENING)
                            .remindBeforeHours(48)
                            .enabled(true)
                            .build()
            );

            when(tenderRepository.findById(tenderId)).thenReturn(Optional.of(tender));
            when(reminderRepository.findByTenderId(tenderId)).thenReturn(settings);

            List<ReminderSettingDTO> result = service.getRemindersByTenderId(tenderId);

            assertEquals(2, result.size());
            assertEquals("测试标讯", result.get(0).getTenderTitle());
            assertEquals(ReminderType.REGISTRATION_DEADLINE, result.get(0).getReminderType());
            assertEquals(ReminderType.BID_OPENING, result.get(1).getReminderType());
        }

        @Test
        @DisplayName("tenderId为null应返回空列表")
        void shouldReturnEmptyListWhenTenderIdIsNull() {
            List<ReminderSettingDTO> result = service.getRemindersByTenderId(null);
            assertTrue(result.isEmpty());
        }
    }
}
