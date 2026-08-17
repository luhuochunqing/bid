// Input: 无（值对象定义）
// Output: 二态值对象 — 投标文件加载结果（内存快路径 / 临时文件流式路径）
// Pos: scoreparse/application — spec 045 FR-001/FR-011
package com.xiyu.bid.scoreparse.application;

import java.nio.file.Path;

/**
 * 投标文件加载结果二态值对象（data-model.md §LoadedBidFile）。
 * <p>sealed + 穷尽 switch：非法中间态不可表示；构造后只读。
 * OnDisk.tempFile 的删除责任在 TempBidFileStore（单一所有权）。
 */
public sealed interface LoadedBidFile permits LoadedBidFile.InMemory, LoadedBidFile.OnDisk {

    String fileName();

    String contentType();

    /** ≤ ram-buffer-bytes（默认50MB）：全程内存，快路径（FR-011），行为与现网一致。 */
    record InMemory(byte[] content, String fileName, String contentType) implements LoadedBidFile {}

    /** spill 后：内容在临时文件，零整包堆持有（FR-001）。 */
    record OnDisk(Path tempFile, String fileName, String contentType) implements LoadedBidFile {}
}
