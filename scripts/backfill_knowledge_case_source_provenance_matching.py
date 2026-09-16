#!/usr/bin/env python3
"""Markdown/JSONL parsing and evidence matching for provenance backfill."""
import json
import os
import re

HEADING_RE = re.compile(r'^(#{1,6})\s+(.+)$')
CHAPTER_RE = re.compile(r'^第[一二三四五六七八九十百千万0-9]+[章篇卷部分](\s+.*|$)')
SECTION_RE = re.compile(r'^第[一二三四五六七八九十百千万0-9]+[节条](\s+.*|$)')
CHINESE_NUM_RE = re.compile(r'^[一二三四五六七八九十百]+[、.．](\s+.*|$)')
CHINESE_PAREN_RE = re.compile(r'^[（(][一二三四五六七八九十百]+[）)]\s*(.+)')
DOT_NUM_RE = re.compile(r'^(\d+(?:\.\d+)+)[、.．\s]?\s*(.+)')
ARABIC_NUM_RE = re.compile(r'^(\d+)[、.．]\s*(.+)')
ARABIC_PAREN_RE = re.compile(r'^[（(](\d+)[）)]\s*(.+)')


def normalize_ws(text):
    return re.sub(r'\s+', ' ', str(text)).strip() if text else ''


def clean_title(raw):
    cleaned = (raw or '').strip()
    cleaned = re.sub(r'\s*#+\s*$', '', cleaned)
    if cleaned.startswith('**') and cleaned.endswith('**') and len(cleaned) > 4:
        cleaned = cleaned[2:-2].strip()
    return cleaned


def _stack_has_level(stack, level):
    return any(item[0] == level for item in stack)


def _parse_heading(stripped, stack):
    match = HEADING_RE.match(stripped)
    if match:
        title = clean_title(match.group(2))
        return len(match.group(1)), title
    if len(stripped) > 120 or len(stripped) < 2:
        return None
    if stripped.startswith(('|', '- ', '* ', '+ ', '>')):
        return None
    if stripped[-1] in '。；，?？!！' or '详见' in stripped or '参见' in stripped:
        return None
    if CHAPTER_RE.match(stripped):
        return 1, clean_title(stripped)
    if CHINESE_NUM_RE.match(stripped):
        return (2 if _stack_has_level(stack, 1) else 1), clean_title(stripped)
    if SECTION_RE.match(stripped):
        return 2, clean_title(stripped)
    match = DOT_NUM_RE.match(stripped)
    if match:
        base = 2 if _stack_has_level(stack, 2) else 1
        return min(6, base + match.group(1).count('.')), clean_title(stripped)
    if CHINESE_PAREN_RE.match(stripped):
        base = stack[-1][0] if stack else 1
        level = min(6, base + 1 if base >= 3 else (3 if _stack_has_level(stack, 1) else 2))
        return level, clean_title(stripped)
    match = ARABIC_NUM_RE.match(stripped)
    if match:
        base = stack[-1][0] if stack else 1
        return min(6, base + 1 if base >= 2 else 2), clean_title(stripped)
    if ARABIC_PAREN_RE.match(stripped):
        base = stack[-1][0] if stack else 1
        return min(6, base + 1), clean_title(stripped)
    return None


def parse_markdown_headings(content):
    headings, stack, index, char_offset = [], [], 0, 0
    for line in (content or '').split('\n'):
        parsed = _parse_heading(line.strip(), stack)
        if parsed:
            level, title = parsed
            while stack and stack[-1][0] >= level:
                stack.pop()
            path = ' > '.join([item[1] for item in stack] + [title])
            headings.append({'index': index, 'char_start': char_offset, 'level': level,
                             'title': title, 'path': path})
            stack.append((level, title))
            index += 1
        char_offset += len(line) + 1
    return headings


def find_nearest_heading(headings, match_char_offset):
    best = None
    for heading in headings:
        if heading['char_start'] <= match_char_offset:
            best = heading
        else:
            break
    return best


def build_norm_pos_map(content):
    norm_chars, norm_to_orig, in_ws = [], [], True
    for index, char in enumerate(content or ''):
        if char.isspace():
            if not in_ws and norm_chars:
                norm_chars.append(' ')
                norm_to_orig.append(index)
                in_ws = True
        else:
            norm_chars.append(char)
            norm_to_orig.append(index)
            in_ws = False
    while norm_chars and norm_chars[-1] == ' ':
        norm_chars.pop()
        norm_to_orig.pop()
    return ''.join(norm_chars), norm_to_orig


def find_loose_matches(content, response_text):
    norm_content, norm_to_orig = build_norm_pos_map(content)
    norm_response = normalize_ws(response_text)
    if not norm_response:
        return []
    positions, pos = [], norm_content.find(norm_response)
    while pos >= 0:
        if pos < len(norm_to_orig):
            positions.append((norm_to_orig[pos], pos, norm_content, norm_to_orig))
        pos = norm_content.find(norm_response, pos + 1)
    return positions


def _has_short_prefix_match(content, response_text):
    norm_content, norm_to_orig = build_norm_pos_map(content)
    prefix = normalize_ws(response_text)[:200]
    return bool(prefix and norm_content.find(prefix) >= 0 and norm_to_orig)


def find_loose_match(content, response_text):
    matches = find_loose_matches(content, response_text)
    if matches:
        return matches[0]
    norm_content, norm_to_orig = build_norm_pos_map(content)
    return None, None, norm_content, norm_to_orig


def load_jsonl_index(jsonl_dir):
    text_index, count = {}, 0
    if not jsonl_dir or not os.path.isdir(jsonl_dir):
        return text_index
    for fname in sorted(os.listdir(jsonl_dir)):
        if not re.match(r'^project_\d+\.jsonl$', fname):
            continue
        with open(os.path.join(jsonl_dir, fname), encoding='utf-8') as handle:
            for line in handle:
                try:
                    record = json.loads(line.strip())
                except (ValueError, TypeError):
                    continue
                text = (record.get('text') or record.get('text_preview') or '').strip()
                if not text:
                    continue
                text_index.setdefault(normalize_ws(text), []).append({
                    'project': (record.get('project') or '').strip(),
                    'obs_key': (record.get('obs_bid_file_key') or '').strip(),
                    'title': (record.get('title') or '').strip(),
                    'docx_label': (record.get('docx_label') or '').strip(),
                })
                count += 1
    print(f'JSONL 索引: {count} 条切片，{len(text_index)} 个唯一文本')
    return text_index


def find_markdown_file(markdown_dir, obs_key):
    if not markdown_dir or not obs_key:
        return None
    for extension in ('.md', '.markdown', '.txt'):
        candidate = os.path.join(markdown_dir, os.path.splitext(obs_key)[0] + extension)
        if os.path.isfile(candidate):
            return candidate
    return None


def search_in_markdown(md_path, response_text, scoring_point_title=None):
    if not md_path or not os.path.isfile(md_path):
        return None
    with open(md_path, encoding='utf-8') as handle:
        content = handle.read()
    if scoring_point_title and normalize_ws(response_text).casefold() == normalize_ws(scoring_point_title).casefold():
        return {'headings': parse_markdown_headings(content), 'matches': [],
                'status': 'insufficient_evidence', 'reason': '仅命中评分项标题'}
    headings = parse_markdown_headings(content)
    matches = [{'orig_pos': orig, 'norm_pos': norm,
                'nearest': find_nearest_heading(headings, orig)}
               for orig, norm, _, _ in find_loose_matches(content, response_text)]
    if not matches and _has_short_prefix_match(content, response_text):
        return {'headings': headings, 'matches': [], 'status': 'insufficient_evidence',
                'reason': '仅命中 response_text 前缀，缺少完整证据'}
    return {'headings': headings, 'matches': matches, 'status': None}


def process_cases(cases, text_index, markdown_dir):
    results = []
    for case in cases:
        response = (case.get('response_text') or '').strip()
        if not response:
            results.append({**case, '_status': 'no_response_text'})
            continue
        matches = text_index.get(normalize_ws(response), [])
        if not matches:
            results.append({**case, '_status': 'no_match', '_matches': []})
            continue
        projects = {item['project'] for item in matches}
        obs_keys = {item['obs_key'] for item in matches}
        if len(matches) > 1 and (len(projects) > 1 or len(obs_keys) > 1):
            results.append({**case, '_status': 'cross_section_conflict', '_matches': matches,
                            '_conflict_reason': '多项目或多文件命中'})
            continue
        matched = matches[0]
        obs_key = case.get('obs_bid_file_key') or matched.get('obs_key') or ''
        md_path = find_markdown_file(markdown_dir, obs_key)
        if not md_path:
            results.append({**case, '_status': 'file_missing', '_matched': matched, '_obs_key': obs_key})
            continue
        md_result = search_in_markdown(md_path, response, case.get('scoring_point_title'))
        if md_result is None:
            results.append({**case, '_status': 'file_missing', '_matched': matched})
            continue
        if md_result.get('status') == 'insufficient_evidence':
            results.append({**case, '_status': 'insufficient_evidence', '_reason': md_result['reason']})
            continue
        markdown_matches = md_result['matches']
        sections = {item['nearest']['index'] for item in markdown_matches if item['nearest'] is not None}
        if len(sections) > 1:
            results.append({**case, '_status': 'cross_section_conflict', '_matches': matches,
                            '_markdown_matches': markdown_matches,
                            '_conflict_reason': '同一 Markdown 文档跨章节多命中'})
            continue
        nearest = markdown_matches[0]['nearest'] if markdown_matches else None
        if nearest is None and md_result['headings']:
            results.append({**case, '_status': 'no_nearest_heading', '_matched': matched,
                            '_headings_count': len(md_result['headings'])})
            continue
        results.append({**case, '_status': 'updatable', '_matched': matched,
                        '_source_section_title': nearest['title'] if nearest else None,
                        '_source_section_path': nearest['path'] if nearest else None,
                        '_source_section_index': nearest['index'] if nearest else None,
                        '_source_document_name': os.path.basename(obs_key) if obs_key else None,
                        '_source_page_number': None})
    return results
