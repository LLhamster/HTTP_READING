#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import os
import re
from pathlib import Path
from typing import List, Tuple, Optional

import pdfplumber  # 新增
from ebooklib import epub
from bs4 import BeautifulSoup
import fitz  # PyMuPDF

# ========= 全局配置 =========
BOOK_ROOT = Path("/home/hamster/file/books")
CHAPTER_DIR_TEMPLATE = "/home/hamster/file/books/{book_id}/chapters"
SQL_PATH_TEMPLATE = "/home/hamster/file/books/{book_id}/import.sql"
BOOK_ID_START = 1000

CHAPTER_TITLE_PATTERN = re.compile(
    r"^(第?\s*\d+\s*[章篇节]|Chapter\s+\d+|CHAPTER\s+\d+)[：:\.\s]*(.+)?$",
    re.IGNORECASE
)

# ================= 工具函数 =================

# --------- PDF 提取（使用 pdfplumber） ---------
def extract_text_with_pdfplumber(pdf_path: Path) -> str:
    """
    使用 pdfplumber 提取 PDF 文本，尽量保留段落结构
    """
    all_text = []
    with pdfplumber.open(str(pdf_path)) as pdf:
        for page in pdf.pages:
            text = page.extract_text(x_tolerance=1, y_tolerance=1) or ""
            all_text.append(text)
    return "\n".join(all_text)

# --------- PDF TOC 分章 ---------
def split_pdf_by_toc(pdf_path: Path):
    """
    使用 PDF 内置目录（TOC）分章。
    返回: [(chapter_index, title, content), ...]
    """
    doc = fitz.open(str(pdf_path))
    toc = doc.get_toc()
    if not toc:
        print("[WARN] PDF 没有 TOC，无法按目录切分")
        return None

    chapters_meta = []
    for level, title, page in toc:
        if level == 1:
            chapters_meta.append({"title": title.strip(), "start": page - 1, "end": None})

    for i in range(len(chapters_meta) - 1):
        chapters_meta[i]["end"] = chapters_meta[i + 1]["start"] - 1
    chapters_meta[-1]["end"] = doc.page_count - 1

    result = []
    for idx, ch in enumerate(chapters_meta, start=1):
        parts = []
        for p in range(ch["start"], ch["end"] + 1):
            with pdfplumber.open(str(pdf_path)) as pdf:
                page_text = pdf.pages[p].extract_text(x_tolerance=1, y_tolerance=1) or ""
                parts.append(page_text)
        full_text = "\n".join(parts).strip()
        result.append((idx, ch["title"], full_text))
        print(f"[INFO] PDF 章节识别: {idx} - {ch['title']} (pages {ch['start']} ~ {ch['end']})")
    return result

# --------- EPUB 提取 ---------
# def extract_text_from_epub(path: Path) -> str:
#     """
#     使用 HTML 标签提取 EPUB 文本，每段落空一行
#     """
#     book = epub.read_epub(str(path))
#     texts = []
#     for item in book.get_items():
#         if item.get_type() == epub.EpubHtml:
#             html = item.get_body_content().decode("utf-8", errors="ignore")
#             soup = BeautifulSoup(html, "html.parser")
#             for tag in soup(["script", "style"]):
#                 tag.decompose()
#             paragraphs = []
#             for p in soup.find_all(['p', 'h1', 'h2', 'h3', 'li']):
#                 text = p.get_text(strip=True)
#                 if text:
#                     paragraphs.append(text)
#             txt = "\n\n".join(paragraphs)
#             texts.append(txt)
#     return "\n".join(texts)

def split_epub_by_spine_and_toc(path: Path):
    book = epub.read_epub(str(path))

    # ===== 1. 读取 TOC（目录）=====
    toc_links = []

    def flatten(toc):
        for item in toc:
            if isinstance(item, epub.Link):
                toc_links.append(item)
            elif isinstance(item, (list, tuple)):
                flatten(item)

    flatten(book.toc)

    # href -> 章节标题
    toc_map = {}
    for link in toc_links:
        href = link.href.split("#")[0]
        toc_map[href] = link.title.strip()

    # ===== 2. 按 spine 顺序拿 HTML =====
    spine_ids = [i[0] for i in book.spine if i[0] != "nav"]
    spine_items = [book.get_item_with_id(sid) for sid in spine_ids]

    # ===== 3. HTML → 纯文本 =====
    def html_to_text(item):
        soup = BeautifulSoup(
            item.get_content().decode("utf-8", errors="ignore"),
            "html.parser",
        )
        for tag in soup(["script", "style", "nav"]):
            tag.decompose()

        for br in soup.find_all("br"):
            br.replace_with("\n")

        body = soup.body
        if not body:
            return ""

        lines = []
        for line in body.get_text("\n", strip=True).splitlines():
            line = re.sub(r"\s+", " ", line).strip()
            if line:
                lines.append("　　" + line)

        return "\n\n".join(lines)

    # ===== 4. 正式分章节 =====
    chapters = []
    current_title = "正文前内容"
    current_buf = []
    chapter_index = 1

    for item in spine_items:
        name = item.file_name
        text = html_to_text(item)
        if not text:
            continue

        # 如果这个 HTML 在 TOC 里 → 新章节开始
        if name in toc_map:
            if current_buf:
                chapters.append(
                    (chapter_index, current_title, "\n\n".join(current_buf))
                )
                chapter_index += 1
                current_buf = []

            current_title = toc_map[name]

        current_buf.append(text)

    # 收尾
    if current_buf:
        chapters.append(
            (chapter_index, current_title, "\n\n".join(current_buf))
        )

    return chapters


def extract_text_from_epub(path: Path) -> str:
    """
    EPUB → 小说友好 TXT
    - 兼容 div / p / span / br
    - 合并 EPUB 排版换行
    - 段首两个全角空格
    """
    book = epub.read_epub(str(path))
    texts = []

    for item in book.get_items():
        # ⚠️ 不再只限制 EpubHtml
        if item.media_type not in ("application/xhtml+xml", "text/html"):
            continue

        html = item.get_content().decode("utf-8", errors="ignore")
        soup = BeautifulSoup(html, "html.parser")

        for tag in soup(["script", "style", "nav"]):
            tag.decompose()

        body = soup.body
        if not body:
            continue

        # 把 <br> 统一当作换行
        for br in body.find_all("br"):
            br.replace_with("\n")

        # 拿 body 下的“所有可见文本”
        raw_text = body.get_text("\n", strip=True)

        for line in raw_text.splitlines():
            line = re.sub(r"\s+", " ", line).strip()
            if not line:
                continue

            texts.append("　　" + line)

    return "\n\n".join(texts)

def read_text_file(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")

# --------- 元数据猜测 ---------
def guess_metadata_from_text(raw_text: str, filename: str):
    lines = [l.strip() for l in raw_text.splitlines() if l.strip()]
    title = None
    author = None
    intro = None
    status = None

    base = Path(filename).stem
    base = re.sub(r"[_\-]+", " ", base).strip()
    if base:
        title = base

    head_lines = lines[:20]
    if head_lines:
        longest = max(head_lines, key=len)
        if len(longest) > 4:
            title = title or longest

    for l in lines[:50]:
        if "作者" in l:
            m = re.search(r"作者[:：]\s*(.+)", l)
            if m:
                author = m.group(1).strip()
                break
            else:
                author = l
                break
        if re.search(r"Author[:：]", l, re.IGNORECASE):
            m = re.search(r"Author[:：]\s*(.+)", l, re.IGNORECASE)
            if m:
                author = m.group(1).strip()
                break
            else:
                author = l
                break

    intro = "\n".join(lines[:10]) if lines else None
    return title, author, intro, status

# --------- 分章（正则匹配） ---------
def split_into_chapters(raw_text: str) -> List[Tuple[int, str, str]]:
    lines = raw_text.splitlines()
    chapters = []
    current_title: Optional[str] = None
    current_index = 0
    current_buf: List[str] = []

    def flush():
        nonlocal current_index, current_title, current_buf
        if current_title is None:
            return
        content = "\n".join(current_buf).strip()
        if not content:
            return
        chapters.append((current_index, current_title, content))
        current_buf = []

    for line in lines:
        stripped = line.strip()
        if not stripped:
            if current_title is not None:
                current_buf.append("")
            continue

        m = CHAPTER_TITLE_PATTERN.match(stripped)
        if m:
            flush()
            current_index += 1
            current_title = stripped
            print(f"[INFO] 发现章节 {current_index}: {current_title}")
        else:
            if current_title is not None:
                current_buf.append(stripped)

    flush()
    return chapters

def escape_sql(s: str) -> str:
    if s is None:
        return "NULL"
    return "'" + s.replace("\\", "\\\\").replace("'", "''") + "'"

# ========== 主流程 ==========
def process_one_book(book_id: int, file_path: Path):
    print(f"[INFO] 处理书本 {book_id}: {file_path}")
    suffix = file_path.suffix.lower()

    if suffix == ".pdf":
        chapters = split_pdf_by_toc(file_path)
        if chapters:
            print("[INFO] 使用 PDF 目录成功分章")
            raw_text = None
        else:
            print("[WARN] PDF 无目录，回退到 pdfplumber 提取全文")
            raw_text = extract_text_with_pdfplumber(file_path)
    elif suffix == ".epub":
        chapters = split_epub_by_spine_and_toc(file_path)
        raw_text = None
    elif suffix in (".txt", ".md"):
        raw_text = read_text_file(file_path)
    else:
        print(f"[WARN] 不支持的文件类型: {file_path}")
        return

    if raw_text is not None:
        title, author, intro, status = guess_metadata_from_text(raw_text, file_path.name)
    else:
        stem = file_path.stem
        title = stem
        author = None
        intro = None
        status = None
        print("[INFO] PDF 使用目录分章，跳过 metadata 提取（全文未加载）")

    print(f"[META] title={title!r}, author={author!r}, status={status!r}")

    if raw_text is not None:
        chapters = split_into_chapters(raw_text)

    if not chapters:
        chapters = [(1, title or "第 1 章", raw_text)]

    chapter_dir = Path(CHAPTER_DIR_TEMPLATE.format(book_id=book_id))
    chapter_dir.mkdir(parents=True, exist_ok=True)
    sql_path = Path(SQL_PATH_TEMPLATE.format(book_id=book_id))
    sql_path.parent.mkdir(parents=True, exist_ok=True)

    sql_lines: List[str] = []

    book_sql = (
        "INSERT INTO book (id, title, author, intro, status, created_at, updated_at) VALUES ("
        f"{book_id}, "
        f"{escape_sql(title)}, "
        f"{escape_sql(author)}, "
        f"{escape_sql(intro)}, "
        f"{escape_sql(status)}, "
        "NOW(), NOW()"
        ");"
    )
    sql_lines.append(book_sql)

    for chapter_index, ch_title, content in chapters:
        # ---------------- 修改点：直接使用 pdfplumber/EPUB 提取的文本 ----------------
        normalized = content
        # -------------------------------------------------------------------------
        filename = f"{chapter_index}.txt"
        txt_path = chapter_dir / filename
        txt_path.write_text(normalized, encoding="utf-8")

        abs_path = str(txt_path.resolve())
        print(f"[INFO] 写入章节 {chapter_index}: {ch_title} -> {abs_path}")

        safe_title = escape_sql(ch_title)
        safe_path = escape_sql(abs_path)

        chapter_sql = (
            "INSERT INTO chapter (book_id, chapter_index, title, content, content_file_path, created_at, updated_at) "
            f"VALUES ({book_id}, {chapter_index}, {safe_title}, NULL, {safe_path}, NOW(), NOW());"
        )
        sql_lines.append(chapter_sql)

    sql_path.write_text(
        "-- 由 import_books.py 自动生成\n" + "\n".join(sql_lines),
        encoding="utf-8",
    )
    print(f"[INFO] 书本 {book_id} 的 SQL 已生成: {sql_path}")

def main():
    if not BOOK_ROOT.exists():
        print(f"[ERROR] 根目录不存在: {BOOK_ROOT}")
        return

    book_files: List[Path] = []

    for entry in sorted(BOOK_ROOT.iterdir()):
        if entry.is_file() and entry.suffix.lower() in (".pdf", ".epub", ".txt", ".md"):
            book_files.append(entry)
        elif entry.is_dir():
            for f in entry.iterdir():
                if f.is_file() and f.suffix.lower() in (".pdf", ".epub", ".txt", ".md"):
                    book_files.append(f)
                    break

    if not book_files:
        print(f"[WARN] 未在 {BOOK_ROOT} 下找到任何 pdf/epub/txt/md 文件")
        return

    print(f"[INFO] 共发现 {len(book_files)} 本书")

    current_book_id = BOOK_ID_START
    for f in book_files:
        process_one_book(current_book_id, f)
        current_book_id += 1

if __name__ == "__main__":
    main()
