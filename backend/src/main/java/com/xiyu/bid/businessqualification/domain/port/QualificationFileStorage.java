package com.xiyu.bid.businessqualification.domain.port;

/**
 * 资质证书文件存储端口。
 * 职责：将附件字节存储到持久化位置，并返回存储的文件名（用于后续附件表记录和磁盘路径解析）。
 */
public interface QualificationFileStorage {

    String storeAttachment(Long qualificationId, byte[] content, String originalFilename, String contentType);

    String storeAttachmentWithNaming(
            Long qualificationId,
            byte[] content,
            String certificateNo,
            int sequence,
            String qualificationName,
            String originalFilename,
            String contentType
    );

    String generateStandardFileName(String certificateNo, int sequence, String qualificationName, String originalFilename);
}
