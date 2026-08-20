package com.xiyu.bid.casework.domain.model;

/**
 * 归档文件的领域读模型。
 *
 * <p>Application 层（ArchiveFileResponseFactory）只依赖此接口，
 * 不直接依赖基础设施层的 JPA Entity。实现类由基础设施层提供。
 */
public interface ArchiveFileReadModel {

    String getFilePath();

    String getFileName();
}
