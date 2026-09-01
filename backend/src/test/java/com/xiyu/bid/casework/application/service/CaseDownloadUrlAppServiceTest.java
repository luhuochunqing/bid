package com.xiyu.bid.casework.application.service;

import com.xiyu.bid.casework.domain.model.CaseObsBidFile;
import com.xiyu.bid.casework.domain.gateway.CaseObsBidFileReader;
import com.xiyu.bid.casework.domain.gateway.HistoricalCaseObsConfig;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.file.domain.gateway.ObsDownloadUrlGateway;
import com.xiyu.bid.file.domain.model.SignedDownloadUrl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CaseDownloadUrlAppServiceTest {

    private static final String OBJECT_KEY = "2026年（投标文件）/2026.01.05-中广核办公/项目复盘-中广核.docx";

    @Mock private CaseObsBidFileReader caseObsBidFileReader;
    @Mock private ObsDownloadUrlGateway obsDownloadUrlGateway;
    @Mock private HistoricalCaseObsConfig obsConfig;

    @InjectMocks private CaseDownloadUrlAppService service;

    private CaseObsBidFile historicalCase(String obsKey) {
        return new CaseObsBidFile(true, obsKey);
    }

    @Test
    void signDownloadUrl_returnsSignedUrlFilenameAndExpireSeconds() {
        when(caseObsBidFileReader.findByCaseId(1L)).thenReturn(historicalCase(OBJECT_KEY));
        when(obsConfig.bucket()).thenReturn("ehsy-widbid");
        when(obsConfig.downloadUrlExpireSeconds()).thenReturn(300);
        when(obsDownloadUrlGateway.signDownloadUrl("ehsy-widbid", OBJECT_KEY, 300))
                .thenReturn(new SignedDownloadUrl("https://ehsy-widbid.obs.cn-east-3.myhuaweicloud.com/x?Signature=s",
                        Instant.now().plusSeconds(300)));

        CaseDownloadUrlAppService.DownloadUrlResult result = service.signDownloadUrl(1L);

        assertEquals("https://ehsy-widbid.obs.cn-east-3.myhuaweicloud.com/x?Signature=s", result.url());
        assertEquals("项目复盘-中广核.docx", result.filename());
        assertEquals(300, result.expireSeconds());
    }

    @Test
    void signDownloadUrl_caseNotFound_throws404() {
        when(caseObsBidFileReader.findByCaseId(404L)).thenReturn(new CaseObsBidFile(false, null));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.signDownloadUrl(404L));
        assertEquals(HttpStatus.NOT_FOUND, ex.getHttpStatus());
    }

    @Test
    void signDownloadUrl_withoutObsKey_throws409() {
        when(caseObsBidFileReader.findByCaseId(2L)).thenReturn(historicalCase(null));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.signDownloadUrl(2L));
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }

    @Test
    void signDownloadUrl_blankObsKey_throws409() {
        when(caseObsBidFileReader.findByCaseId(3L)).thenReturn(historicalCase("  "));
        BusinessException ex = assertThrows(BusinessException.class, () -> service.signDownloadUrl(3L));
        assertEquals(HttpStatus.CONFLICT, ex.getHttpStatus());
    }

    @Test
    void signDownloadUrl_gatewayFailure_throws502() {
        when(caseObsBidFileReader.findByCaseId(4L)).thenReturn(historicalCase(OBJECT_KEY));
        when(obsConfig.bucket()).thenReturn("ehsy-widbid");
        when(obsConfig.downloadUrlExpireSeconds()).thenReturn(300);
        when(obsDownloadUrlGateway.signDownloadUrl(anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("obs down"));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.signDownloadUrl(4L));
        assertEquals(HttpStatus.BAD_GATEWAY, ex.getHttpStatus());
    }
}
