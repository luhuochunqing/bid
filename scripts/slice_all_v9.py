# -*- coding: utf-8 -*-
# v9: 全量跑 70 个项目 + sleep+重试机制解决 obsfs 不稳定问题
import zipfile, re, io, os, json, sys, subprocess, time

WHITELIST = [
    u"方案", u"服务", u"措施", u"保障", u"承诺", u"体系", u"架构",
    u"备份", u"防护", u"应急", u"管理", u"计划", u"流程", u"制度",
    u"规范", u"策略", u"机制", u"培训", u"运维", u"运营", u"实施",
    u"交付", u"质量", u"安全", u"技术", u"商务", u"售后", u"响应",
    u"介绍", u"分析", u"评估", u"建议", u"总结", u"概述", u"理解",
    u"设计", u"建设", u"规划", u"部署", u"集成", u"对接", u"开发",
    u"验收", u"质保", u"保修", u"维护", u"支持"
]
BLACKLIST = [
    u"发票", u"执照", u"证明", u"社保", u"审计", u"开户", u"许可",
    u"复印件", u"扫描件", u"资质", u"证书", u"营业执照", u"许可证", u"授权书",
    u"承诺书", u"声明", u"函",
    u"业绩证明", u"关联关系", u"中标通知书", u"合作协议", u"框架协议",
    u"合作期限", u"订单发票", u"大额订单",
    u"索引表", u"偏差表", u"响应文件", u"报价文件", u"目录", u"附件",
    u"汇总", u"明细",
    u"截图", u"图片",
    u"页", u"张", u"份"
]
COMPANY_SUFFIXES = [u"集团", u"有限", u"股份", u"实业", u"控股", u"科技",
                    u"技术", u"工程", u"建设", u"投资", u"发展", u"工业",
                    u"商业", u"贸易", u"物流", u"地产", u"能源", u"电力",
                    u"通信", u"医药", u"化工", u"材料", u"食品", u"服装",
                    u"电子", u"机械", u"汽车", u"航空", u"航天", u"船舶",
                    u"兵器", u"核工业", u"石油", u"石化", u"海油", u"煤业",
                    u"矿业", u"钢铁", u"铝业", u"铜业", u"钨业", u"稀土"]

MIN_PARA_TEXT_LEN = 50

def is_answer_section(title, full_text=""):
    if not title or len(title.strip()) < 4:
        return False, u"标题过短"
    for b in BLACKLIST:
        if b in title:
            return False, u"黑名单:" + b
    if title.endswith(u"公司") and len(title) < 50:
        for suf in COMPANY_SUFFIXES:
            if suf in title:
                return False, u"公司名"
    if title.endswith(u"公司") and len(title) < 30:
        return False, u"短公司名"
    text_len = len(full_text.strip()) if full_text else 0
    if text_len < MIN_PARA_TEXT_LEN:
        return False, u"正文过短(%d字)" % text_len
    for w in WHITELIST:
        if w in title:
            return True, u"白名单:" + w
    return False, u"未命中白名单"

def parse_docx_headings(docx_path):
    with zipfile.ZipFile(docx_path) as z:
        with z.open("word/document.xml") as f:
            content = f.read().decode("utf-8", errors="ignore")
    paras = re.findall(r"<w:p[^>]*>.*?</w:p>", content, re.DOTALL)
    sections = []
    current = None
    para_count = 0
    text_buf = []
    for p in paras:
        pstyle = re.search(r"<w:pStyle w:val=\"([^\"]*)\"", p)
        text = "".join(re.findall(r"<w:t[^>]*>([^<]*)</w:t>", p))
        is_heading = False
        level = 0
        if pstyle:
            sname = pstyle.group(1)
            if "Heading" in sname:
                m = re.search(r"\d+", sname)
                level = int(m.group()) if m else 1
                is_heading = True
            elif sname.startswith("AA") and "title" in sname.lower():
                m = re.search(r"(\d+)$", sname)
                level = int(m.group(1)) + 1 if m else 1
                is_heading = True
            elif sname == "AA":
                level = 1
                is_heading = True
        if is_heading:
            if current is not None:
                current["para_count"] = para_count
                current["full_text"] = u"".join(text_buf)
                current["text_preview"] = current["full_text"][:300]
                sections.append(current)
            current = {"level": level, "text": text.strip()}
            para_count = 0
            text_buf = []
        else:
            para_count += 1
            if len(text_buf) < 100 and text.strip():
                text_buf.append(text)
    if current is not None:
        current["para_count"] = para_count
        current["full_text"] = u"".join(text_buf)
        current["text_preview"] = current["full_text"][:300]
        sections.append(current)
    return sections

def sudo_find_with_retry(path, maxdepth=3, name_pattern=None, types=None, retries=3):
    """带重试机制的 sudo find，应对 obsfs 不稳定"""
    cmd = [b"sudo", b"-n", b"find", path.encode("utf-8"),
           b"-maxdepth", str(maxdepth).encode(), b"-mindepth", b"1"]
    if types:
        cmd.extend([b"-type", types.encode()])
    if name_pattern:
        cmd.extend([b"-name", name_pattern.encode("utf-8")])

    for attempt in range(retries):
        time.sleep(0.3)  # 每次 find 前 sleep 0.3s，给 obsfs 喘息时间
        result = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        if result.returncode != 0:
            time.sleep(1.0)  # 失败后等更久
            continue
        files = []
        for line in result.stdout.split(b"\n"):
            if not line:
                continue
            try:
                full_path = line.decode("utf-8")
            except UnicodeDecodeError:
                continue
            # 过滤 ~$ 开头的 Word 临时文件
            basename = full_path.rsplit(u"/", 1)[-1]
            if basename.startswith(u"~$"):
                continue
            prefix = path + u"/"
            if full_path.startswith(prefix):
                rel = full_path[len(prefix):]
            else:
                rel = full_path
            files.append(rel)
        if files:
            return files
        # 如果第一次返回空，可能是 obsfs 还没 ready，等待后重试
        time.sleep(1.0)
    return []

def sudo_find_dirs(path, maxdepth=1, retries=3):
    return sudo_find_with_retry(path, maxdepth=maxdepth, types="d", retries=retries)

def sudo_find_docx(path, retries=3):
    return sudo_find_with_retry(path, maxdepth=4, name_pattern="*.docx", types="f", retries=retries)

def process_project(idx, project_dir, base_path, log):
    project_path = base_path + u"/" + project_dir
    docx_files = sudo_find_docx(project_path)
    if not docx_files:
        log.write(u"  no docx files (after retries)\n")
        return project_dir, 0, 0, 0

    md_path = "/tmp/winbid_slices/project_%d.md" % idx
    jsonl_path = "/tmp/winbid_slices/project_%d.jsonl" % idx

    out = io.open(md_path, "w", encoding="utf-8")
    jsonl = io.open(jsonl_path, "w", encoding="utf-8")

    out.write(u"# %s - 章节切片\n\n" % project_dir)
    out.write(u"项目目录: `%s`\n" % project_path)
    out.write(u"docx 文件数: %d\n\n" % len(docx_files))

    total_kept = 0
    total_skipped = 0
    section_idx = 0
    for fname in docx_files:
        fpath = project_path + u"/" + fname
        tmp_path = "/tmp/_parse_tmp.docx"
        try:
            subprocess.run([b"sudo", b"-n", b"cp", fpath.encode("utf-8"), tmp_path.encode("utf-8")], check=True)
            subprocess.run([b"sudo", b"-n", b"chown", b"jetty:jetty", tmp_path.encode("utf-8")], check=True)
        except subprocess.CalledProcessError:
            out.write(u"\n## 文件复制失败: %s\n" % fname[:80])
            continue
        label = u"商务" if u"商务" in fname else (u"技术" if u"技术" in fname else (u"报价" if u"报价" in fname else u"其他"))
        out.write(u"---\n\n## 文件: %s (%s)\n\n" % (fname[:80], label))
        try:
            sections = parse_docx_headings(tmp_path)
        except Exception:
            out.write(u"\n解析失败\n\n")
            continue
        kept = []
        for s in sections:
            section_idx += 1
            keep, reason = is_answer_section(s["text"], s.get("full_text", ""))
            if keep:
                kept.append(s)
                rec = {
                    "project": project_dir,
                    "project_idx": idx,
                    "docx_file": fname,
                    "docx_label": label,
                    "section_idx": section_idx,
                    "level": s["level"],
                    "title": s["text"],
                    "text_length": len(s.get("full_text", "")),
                    "text_preview": s.get("text_preview", "")[:300],
                    "para_count": s.get("para_count", 0)
                }
                jsonl.write(json.dumps(rec, ensure_ascii=False) + u"\n")
        total_kept += len(kept)
        total_skipped += len(sections) - len(kept)
        out.write(u"### 保留的应答章节（共 %d 条，过滤 %d 条）\n\n" % (len(kept), len(sections) - len(kept)))
        for i, s in enumerate(kept, 1):
            out.write(u"#### [%d] (L%d) %s\n\n" % (i, s["level"], s["text"]))
            preview = s["text_preview"][:300].replace(u"\n", u" ")
            out.write(u"> 正文预览 (%d字): %s\n\n" % (len(s.get("full_text", "")), preview))

    out.write(u"\n---\n\n## 统计\n\n")
    out.write(u"- docx 文件数: %d\n" % len(docx_files))
    out.write(u"- 总章节: %d\n" % (total_kept + total_skipped))
    out.write(u"- 保留: %d\n" % total_kept)
    out.write(u"- 过滤: %d\n" % total_skipped)
    out.write(u"- 保留率: %.1f%%\n" % (100.0 * total_kept / max(1, total_kept + total_skipped)))
    out.close()
    jsonl.close()
    log.write(u"  docx=%d sections=%d kept=%d\n" % (len(docx_files), total_kept + total_skipped, total_kept))
    return project_dir, len(docx_files), total_kept + total_skipped, total_kept

def main():
    base = u"/data/obs_winbid/2026年（投标文件）"
    subprocess.run([b"sudo", b"-n", b"mkdir", b"-p", b"/tmp/winbid_slices"], check=True)
    subprocess.run([b"sudo", b"-n", b"chown", b"jetty:jetty", b"/tmp/winbid_slices"], check=True)
    subprocess.run([b"sudo", b"-n", b"rm", b"-rf", b"/tmp/winbid_slices/project_*"])

    # 列出所有项目目录（也加重试）
    project_dirs = sudo_find_dirs(base, maxdepth=1)
    selected = project_dirs

    summary = io.open("/tmp/winbid_slices/_summary.md", "w", encoding="utf-8")
    log = io.open("/tmp/winbid_slices/_run.log", "w", encoding="utf-8")
    summary.write(u"# 全量切片汇总 v9（%d 个项目）\n\n" % len(selected))
    summary.write(u"基础路径: `%s`\n\n" % base)
    summary.write(u"| 序号 | 项目目录 | docx 数 | 总章节 | 保留 | 保留率 |\n")
    summary.write(u"|---|---|---:|---:|---:|---:|\n")

    log.write(u"Processing %d projects...\n" % len(selected))
    total_kept_all = 0
    success_count = 0
    fail_count = 0
    no_docx_count = 0
    zero_section_count = 0
    for i, project_dir in enumerate(selected, 1):
        log.write(u"[%d/%d] %s\n" % (i, len(selected), project_dir))
        try:
            name, docx_count, total, kept = process_project(i, project_dir, base, log)
            rate = 100.0 * kept / max(1, total)
            summary.write(u"| %d | %s | %d | %d | %d | %.1f%% |\n" % (i, name[:50], docx_count, total, kept, rate))
            total_kept_all += kept
            if kept > 0:
                success_count += 1
            elif docx_count == 0:
                no_docx_count += 1
            else:
                zero_section_count += 1
        except Exception as e:
            log.write(u"  ERROR: %s\n" % str(e))
            summary.write(u"| %d | %s | ERROR | %s |\n" % (i, project_dir[:50], str(e)[:50]))
            fail_count += 1

    summary.write(u"\n## 总体统计\n\n")
    summary.write(u"- 总项目数: %d\n" % len(selected))
    summary.write(u"- 成功切片项目数: %d\n" % success_count)
    summary.write(u"- 无 docx 项目数: %d\n" % no_docx_count)
    summary.write(u"- docx 但 0 章节项目数: %d\n" % zero_section_count)
    summary.write(u"- 错误项目数: %d\n" % fail_count)
    summary.write(u"- 总切片数: %d\n" % total_kept_all)
    summary.write(u"- 项目覆盖率: %.1f%%\n" % (100.0 * success_count / max(1, len(selected))))
    summary.close()
    log.close()

main()
