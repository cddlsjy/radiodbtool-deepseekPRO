#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
RadioDBTool 最终版 - 电台数据库下载与导出工具
- 支持全量同步或按国家/语言/关键词条件同步
- 数据存储为 SQLite 数据库 (.db)，与 Android RadioDroid 兼容
- 断点续传、实时进度 (GUI + 控制台)
- 导出格式：M3U (带 UUID 和图标), CSV, JSON, 独立 .db 文件
- 新增：打开其他数据库文件并导出
"""

import json
import sqlite3
import threading
import time
import tkinter as tk
from tkinter import ttk, messagebox, filedialog
import hashlib
import os
import requests

# ------------------------------------------------------------
# 数据库管理类 (线程安全，支持断点续传)
# ------------------------------------------------------------
class RadioDatabase:
    def __init__(self, db_path="radio_stations.db"):
        self.db_path = db_path
        self.conn = sqlite3.connect(db_path, check_same_thread=False)
        self.cursor = self.conn.cursor()
        self.write_lock = threading.Lock()
        self._create_tables()

    def _create_tables(self):
        with self.write_lock:
            # 主表
            self.cursor.execute('''
                CREATE TABLE IF NOT EXISTS stations (
                    stationuuid TEXT PRIMARY KEY,
                    name TEXT,
                    url TEXT,
                    homepage TEXT,
                    favicon TEXT,
                    country TEXT,
                    countrycode TEXT,
                    state TEXT,
                    language TEXT,
                    tags TEXT,
                    codec TEXT,
                    bitrate INTEGER,
                    lastcheckok INTEGER,
                    lastchangetime TEXT,
                    clickcount INTEGER,
                    clicktrend INTEGER,
                    votes INTEGER
                )
            ''')
            # 进度表
            self.cursor.execute('''
                CREATE TABLE IF NOT EXISTS sync_progress (
                    filter_key TEXT PRIMARY KEY,
                    offset INTEGER
                )
            ''')
            # 检查并修复旧表结构（兼容列名错误）
            self.cursor.execute("PRAGMA table_info(sync_progress)")
            columns = [col[1] for col in self.cursor.fetchall()]
            if 'filter_key' not in columns:
                if 'key' in columns:
                    self.cursor.execute("ALTER TABLE sync_progress RENAME COLUMN key TO filter_key")
                else:
                    self.cursor.execute("DROP TABLE sync_progress")
                    self.cursor.execute('''
                        CREATE TABLE sync_progress (
                            filter_key TEXT PRIMARY KEY,
                            offset INTEGER
                        )
                    ''')
            self.conn.commit()
        print(f"[DB] 数据库表初始化/修复完成 (文件: {self.db_path})")

    def close(self):
        with self.write_lock:
            if self.conn:
                self.conn.close()

    def get_last_offset(self, filter_key):
        self.cursor.execute("SELECT offset FROM sync_progress WHERE filter_key=?", (filter_key,))
        row = self.cursor.fetchone()
        return row[0] if row else 0

    def save_offset(self, filter_key, offset):
        with self.write_lock:
            self.cursor.execute("REPLACE INTO sync_progress (filter_key, offset) VALUES (?, ?)", (filter_key, offset))
            self.conn.commit()
        print(f"[DB] 保存断点 filter={filter_key}, offset={offset}")

    def clear_offset(self, filter_key):
        with self.write_lock:
            self.cursor.execute("DELETE FROM sync_progress WHERE filter_key=?", (filter_key,))
            self.conn.commit()
        print(f"[DB] 清除断点 filter={filter_key}")

    def get_station_count(self):
        self.cursor.execute("SELECT COUNT(*) FROM stations")
        return self.cursor.fetchone()[0]

    def insert_stations(self, stations_list):
        if not stations_list:
            return
        with self.write_lock:
            for s in stations_list:
                self.cursor.execute('''
                    INSERT OR IGNORE INTO stations (
                        stationuuid, name, url, homepage, favicon, country, countrycode,
                        state, language, tags, codec, bitrate, lastcheckok, lastchangetime,
                        clickcount, clicktrend, votes
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ''', (
                    s.get('stationuuid', ''),
                    s.get('name', ''),
                    s.get('url', ''),
                    s.get('homepage', ''),
                    s.get('favicon', ''),
                    s.get('country', ''),
                    s.get('countrycode', ''),
                    s.get('state', ''),
                    s.get('language', ''),
                    s.get('tags', ''),
                    s.get('codec', ''),
                    s.get('bitrate', 0),
                    1 if s.get('lastcheckok') == 1 else 0,
                    s.get('lastchangetime', ''),
                    s.get('clickcount', 0),
                    s.get('clicktrend', 0),
                    s.get('votes', 0)
                ))
            self.conn.commit()

    def get_all_countries(self):
        self.cursor.execute("SELECT DISTINCT country FROM stations WHERE country != '' ORDER BY country")
        return [row[0] for row in self.cursor.fetchall()]

    def get_all_languages(self):
        self.cursor.execute("SELECT DISTINCT language FROM stations WHERE language != '' ORDER BY language")
        return [row[0] for row in self.cursor.fetchall()]

    def filter_stations(self, country="", language="", keyword=""):
        query = "SELECT * FROM stations WHERE 1=1"
        params = []
        if country:
            query += " AND country = ?"
            params.append(country)
        if language:
            query += " AND language = ?"
            params.append(language)
        if keyword:
            query += " AND (name LIKE ? OR tags LIKE ?)"
            like = f"%{keyword}%"
            params.extend([like, like])
        query += " ORDER BY clickcount DESC"
        self.cursor.execute(query, params)
        col_names = [desc[0] for desc in self.cursor.description]
        rows = self.cursor.fetchall()
        return [dict(zip(col_names, row)) for row in rows]


# ------------------------------------------------------------
# 网络同步器 (支持全量或条件同步，动态获取国别/语言列表)
# ------------------------------------------------------------
class RadioSyncer:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip('/')
        self.session = requests.Session()
        self.session.headers.update({'User-Agent': 'RadioDBTool/2.0'})

    def _get_total_stations(self):
        url = f"{self.base_url}/json/stats"
        print(f"[NET] 请求统计: {url}")
        resp = self.session.get(url, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        return data.get('stations', 0)

    def get_available_countries(self):
        url = f"{self.base_url}/json/countries"
        print(f"[NET] 获取国家列表: {url}")
        resp = self.session.get(url, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        countries = sorted(set(item['name'] for item in data if item.get('name')))
        return countries

    def get_available_languages(self):
        url = f"{self.base_url}/json/languages"
        print(f"[NET] 获取语言列表: {url}")
        resp = self.session.get(url, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        languages = sorted(set(item['name'] for item in data if item.get('name')))
        return languages

    def build_filter_url(self, country="", language="", keyword="", limit=500, offset=0):
        url = f"{self.base_url}/json/stations"
        params = {"limit": limit, "offset": offset}
        if country:
            params["country"] = country
        if language:
            params["language"] = language
        if keyword:
            params["name"] = keyword
        query = "&".join([f"{k}={v}" for k, v in params.items()])
        return f"{url}?{query}"

    def fetch_page(self, url):
        resp = self.session.get(url, timeout=30)
        resp.raise_for_status()
        data = resp.json()
        print(f"[NET] 下载 {len(data)} 条 (URL: {url[:120]}...)")
        return data

    def sync_full(self, db, progress_callback, cancel_check=None):
        limit = 500
        total = self._get_total_stations()
        if total == 0:
            progress_callback(0, 0, "无法获取电台总数")
            return False, "无法获取电台总数"
        filter_key = "full"
        start_offset = db.get_last_offset(filter_key)
        if start_offset >= total:
            progress_callback(total, total, "数据已是最新")
            return True, "数据已是最新"
        offset = start_offset
        while offset < total:
            if cancel_check and cancel_check():
                return False, "同步已取消"
            url = f"{self.base_url}/json/stations?limit={limit}&offset={offset}"
            stations = self.fetch_page(url)
            if not stations:
                break
            db.insert_stations(stations)
            offset += len(stations)
            db.save_offset(filter_key, offset)
            progress_callback(offset, total, f"全量下载中 ({offset}/{total})")
            print(f"[PROG] 全量进度: {offset}/{total} ({(offset/total)*100:.2f}%)")
            time.sleep(0.05)
        db.clear_offset(filter_key)
        progress_callback(total, total, "全量同步完成")
        return True, f"全量同步完成，共 {total} 个电台"

    def sync_filtered(self, db, country, language, keyword, progress_callback, cancel_check=None):
        limit = 500
        offset = 0
        filter_str = f"{country}|{language}|{keyword}"
        filter_key = hashlib.md5(filter_str.encode()).hexdigest()
        db.clear_offset(filter_key)
        print(f"[INFO] 开始条件同步: country='{country}', language='{language}', keyword='{keyword}'")

        downloaded = 0
        while True:
            if cancel_check and cancel_check():
                return False, "同步已取消"
            url = self.build_filter_url(country, language, keyword, limit, offset)
            print(f"[DEBUG] 请求URL: {url[:150]}...")
            stations = self.fetch_page(url)
            if not stations:
                break
            db.insert_stations(stations)
            downloaded += len(stations)
            offset += len(stations)
            db.save_offset(filter_key, offset)
            progress_callback(downloaded, 0, f"条件下载中 (已下载 {downloaded} 条)")
            print(f"[PROG] 条件同步已下载 {downloaded} 条, offset={offset}")
            if len(stations) < limit:
                break
            time.sleep(0.05)
        db.clear_offset(filter_key)
        progress_callback(downloaded, downloaded, f"条件同步完成，共 {downloaded} 个电台")
        return True, f"条件同步完成，共 {downloaded} 个电台"


# ------------------------------------------------------------
# 导出辅助类 (支持 M3U, CSV, JSON, 单独 .db)
# ------------------------------------------------------------
class ExportHelper:
    @staticmethod
    def to_m3u(stations, filepath):
        with open(filepath, 'w', encoding='utf-8') as f:
            f.write("#EXTM3U\n\n")
            for s in stations:
                uuid = s.get('stationuuid', '')
                name = s.get('name', 'Unknown')
                icon = s.get('favicon', '')
                url = s.get('url', '')
                if not url:
                    continue
                if uuid:
                    f.write(f"#RADIOBROWSERUUID:{uuid}\n")
                f.write(f"#EXTINF:-1,{name}\n")
                if icon and icon.strip():
                    f.write(f"#EXTIMG:{icon}\n")
                f.write(f"{url}\n\n")

    @staticmethod
    def to_csv(stations, filepath):
        import csv
        if not stations:
            return
        fieldnames = ['stationuuid', 'name', 'url', 'country', 'language', 'tags', 'clickcount', 'votes']
        with open(filepath, 'w', newline='', encoding='utf-8') as f:
            writer = csv.DictWriter(f, fieldnames=fieldnames)
            writer.writeheader()
            for s in stations:
                row = {k: s.get(k, '') for k in fieldnames}
                writer.writerow(row)

    @staticmethod
    def to_json(stations, filepath):
        with open(filepath, 'w', encoding='utf-8') as f:
            json.dump(stations, f, indent=2, ensure_ascii=False)

    @staticmethod
    def to_db(stations, filepath):
        conn = sqlite3.connect(filepath)
        c = conn.cursor()
        c.execute('''
            CREATE TABLE IF NOT EXISTS stations (
                stationuuid TEXT PRIMARY KEY,
                name TEXT, url TEXT, homepage TEXT, favicon TEXT,
                country TEXT, countrycode TEXT, state TEXT, language TEXT,
                tags TEXT, codec TEXT, bitrate INTEGER, lastcheckok INTEGER,
                lastchangetime TEXT, clickcount INTEGER, clicktrend INTEGER, votes INTEGER
            )
        ''')
        for s in stations:
            c.execute('''
                INSERT OR IGNORE INTO stations VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ''', (
                s.get('stationuuid'), s.get('name'), s.get('url'), s.get('homepage'),
                s.get('favicon'), s.get('country'), s.get('countrycode'), s.get('state'),
                s.get('language'), s.get('tags'), s.get('codec'), s.get('bitrate'),
                s.get('lastcheckok'), s.get('lastchangetime'), s.get('clickcount'),
                s.get('clicktrend'), s.get('votes')
            ))
        conn.commit()
        conn.close()
        return len(stations)


# ------------------------------------------------------------
# 主 GUI 应用程序
# ------------------------------------------------------------
class RadioDBToolApp:
    def __init__(self, root):
        self.root = root
        self.root.title("RadioDBTool 最终版 - 电台数据库下载与导出")
        self.root.geometry("900x800")
        self.db = RadioDatabase()
        self.syncer = None
        self.sync_thread = None
        self.cancel_requested = False

        self.create_widgets()
        self.load_server_lists()

    def create_widgets(self):
        main_frame = ttk.Frame(self.root, padding="10")
        main_frame.pack(fill=tk.BOTH, expand=True)

        # ---------------- 工具栏 ----------------
        toolbar_frame = ttk.Frame(main_frame)
        toolbar_frame.pack(fill=tk.X, pady=5)
        self.open_db_btn = ttk.Button(toolbar_frame, text="📂 打开其他数据库文件", command=self.open_external_database)
        self.open_db_btn.pack(side=tk.LEFT, padx=5)
        self.reset_db_btn = ttk.Button(toolbar_frame, text="🔄 重置默认数据库", command=self.reset_default_database)
        self.reset_db_btn.pack(side=tk.LEFT, padx=5)
        self.current_db_label = ttk.Label(toolbar_frame, text=f"当前数据库: {self.db.db_path}")
        self.current_db_label.pack(side=tk.LEFT, padx=10)

        # ---------------- 服务器设置 ----------------
        server_frame = ttk.LabelFrame(main_frame, text="服务器设置", padding="5")
        server_frame.pack(fill=tk.X, pady=5)
        ttk.Label(server_frame, text="API地址:").grid(row=0, column=0, padx=5, sticky=tk.W)
        self.server_entry = ttk.Entry(server_frame, width=50)
        self.server_entry.grid(row=0, column=1, padx=5, sticky=tk.W)
        self.server_entry.insert(0, "https://de1.api.radio-browser.info")

        # ---------------- 同步模式 ----------------
        mode_frame = ttk.LabelFrame(main_frame, text="同步模式", padding="5")
        mode_frame.pack(fill=tk.X, pady=5)
        self.sync_mode = tk.StringVar(value="filtered")
        ttk.Radiobutton(mode_frame, text="全量同步（下载全部电台，耗时较长）", variable=self.sync_mode, value="full").pack(anchor=tk.W)
        ttk.Radiobutton(mode_frame, text="条件同步（只下载符合下方筛选条件的电台）", variable=self.sync_mode, value="filtered").pack(anchor=tk.W)

        # ---------------- 筛选条件（用于条件同步） ----------------
        filter_frame = ttk.LabelFrame(main_frame, text="条件同步的筛选条件", padding="5")
        filter_frame.pack(fill=tk.X, pady=5)

        ttk.Label(filter_frame, text="国家:").grid(row=0, column=0, padx=5, pady=2, sticky=tk.W)
        self.country_combo = ttk.Combobox(filter_frame, width=30)
        self.country_combo.grid(row=0, column=1, padx=5, pady=2, sticky=tk.W)

        ttk.Label(filter_frame, text="语言:").grid(row=1, column=0, padx=5, pady=2, sticky=tk.W)
        self.language_combo = ttk.Combobox(filter_frame, width=30)
        self.language_combo.grid(row=1, column=1, padx=5, pady=2, sticky=tk.W)

        ttk.Label(filter_frame, text="关键词:").grid(row=2, column=0, padx=5, pady=2, sticky=tk.W)
        self.keyword_entry = ttk.Entry(filter_frame, width=40)
        self.keyword_entry.grid(row=2, column=1, padx=5, pady=2, sticky=tk.W)

        self.status_country_lang = ttk.Label(filter_frame, text="正在从服务器加载国家/语言列表...", foreground="gray")
        self.status_country_lang.grid(row=3, column=0, columnspan=2, pady=5, sticky=tk.W)

        # ---------------- 同步控制 ----------------
        control_frame = ttk.Frame(main_frame)
        control_frame.pack(fill=tk.X, pady=5)
        self.sync_btn = ttk.Button(control_frame, text="开始同步", command=self.start_sync)
        self.sync_btn.pack(side=tk.LEFT, padx=5)
        self.cancel_btn = ttk.Button(control_frame, text="取消同步", command=self.cancel_sync, state=tk.DISABLED)
        self.cancel_btn.pack(side=tk.LEFT, padx=5)

        self.progress_bar = ttk.Progressbar(control_frame, orient=tk.HORIZONTAL, length=400, mode='determinate')
        self.progress_bar.pack(side=tk.LEFT, padx=10, fill=tk.X, expand=True)
        self.status_label = ttk.Label(control_frame, text="就绪")
        self.status_label.pack(side=tk.LEFT, padx=5)

        # ---------------- 导出区域 ----------------
        export_frame = ttk.LabelFrame(main_frame, text="导出筛选结果（基于当前数据库）", padding="5")
        export_frame.pack(fill=tk.X, pady=5)

        ttk.Label(export_frame, text="国家:").grid(row=0, column=0, padx=5, pady=2, sticky=tk.W)
        self.export_country = ttk.Combobox(export_frame, width=30)
        self.export_country.grid(row=0, column=1, padx=5, pady=2, sticky=tk.W)

        ttk.Label(export_frame, text="语言:").grid(row=1, column=0, padx=5, pady=2, sticky=tk.W)
        self.export_language = ttk.Combobox(export_frame, width=30)
        self.export_language.grid(row=1, column=1, padx=5, pady=2, sticky=tk.W)

        ttk.Label(export_frame, text="关键词:").grid(row=2, column=0, padx=5, pady=2, sticky=tk.W)
        self.export_keyword = ttk.Entry(export_frame, width=40)
        self.export_keyword.grid(row=2, column=1, padx=5, pady=2, sticky=tk.W)

        ttk.Label(export_frame, text="导出格式:").grid(row=3, column=0, padx=5, pady=2, sticky=tk.W)
        self.format_var = tk.StringVar(value="M3U")
        format_combo = ttk.Combobox(export_frame, textvariable=self.format_var, values=["M3U", "CSV", "JSON", "SQLite DB"], width=10)
        format_combo.grid(row=3, column=1, padx=5, pady=2, sticky=tk.W)

        self.export_btn = ttk.Button(export_frame, text="导出筛选结果", command=self.export_stations)
        self.export_btn.grid(row=3, column=2, padx=10)

        # ---------------- 预览表格 ----------------
        preview_frame = ttk.LabelFrame(main_frame, text="已下载电台预览（前20条）", padding="5")
        preview_frame.pack(fill=tk.BOTH, expand=True, pady=5)

        columns = ("name", "country", "language", "clickcount", "bitrate")
        self.tree = ttk.Treeview(preview_frame, columns=columns, show="headings")
        self.tree.heading("name", text="电台名称")
        self.tree.heading("country", text="国家")
        self.tree.heading("language", text="语言")
        self.tree.heading("clickcount", text="点击数")
        self.tree.heading("bitrate", text="码率")
        self.tree.column("name", width=300)
        self.tree.column("country", width=100)
        self.tree.column("language", width=100)
        self.tree.column("clickcount", width=80)
        self.tree.column("bitrate", width=60)

        scrollbar = ttk.Scrollbar(preview_frame, orient=tk.VERTICAL, command=self.tree.yview)
        self.tree.configure(yscrollcommand=scrollbar.set)
        self.tree.pack(side=tk.LEFT, fill=tk.BOTH, expand=True)
        scrollbar.pack(side=tk.RIGHT, fill=tk.Y)

        # 绑定导出筛选变化刷新预览
        self.export_country.bind("<<ComboboxSelected>>", lambda e: self.refresh_preview())
        self.export_language.bind("<<ComboboxSelected>>", lambda e: self.refresh_preview())
        self.export_keyword.bind("<KeyRelease>", lambda e: self.refresh_preview())

    def load_server_lists(self):
        """从服务器加载可用的国家和语言列表，并填充下拉框"""
        def load():
            try:
                server = self.server_entry.get().strip()
                syncer = RadioSyncer(server)
                countries = syncer.get_available_countries()
                languages = syncer.get_available_languages()
                self.root.after(0, lambda: self._populate_lists(countries, languages))
            except Exception as e:
                self.root.after(0, lambda: self._populate_lists([], [], error=str(e)))
        threading.Thread(target=load, daemon=True).start()

    def _populate_lists(self, countries, languages, error=None):
        if error:
            self.status_country_lang.config(text=f"加载失败: {error}，请手动输入或检查网络", foreground="red")
        else:
            self.country_combo['values'] = countries
            self.language_combo['values'] = languages
            if countries:
                self.country_combo.set(countries[0] if "China" not in countries else "China")
            if languages:
                self.language_combo.set(languages[0] if "Chinese" not in languages else "Chinese")
            self.status_country_lang.config(text=f"已加载 {len(countries)} 个国家，{len(languages)} 种语言", foreground="green")
        self.refresh_filter_options()

    def refresh_filter_options(self):
        """从当前数据库加载国家/语言（用于导出下拉）"""
        def load():
            countries = self.db.get_all_countries()
            languages = self.db.get_all_languages()
            self.root.after(0, lambda: self._set_export_options(countries, languages))
        threading.Thread(target=load, daemon=True).start()

    def _set_export_options(self, countries, languages):
        self.export_country['values'] = [''] + countries
        self.export_language['values'] = [''] + languages

    def refresh_preview(self):
        """刷新预览表格（基于当前数据库）"""
        def do_filter():
            country = self.export_country.get().strip()
            language = self.export_language.get().strip()
            keyword = self.export_keyword.get().strip()
            stations = self.db.filter_stations(country, language, keyword)
            self.root.after(0, lambda: self._update_preview(stations[:20]))
        threading.Thread(target=do_filter, daemon=True).start()

    def _update_preview(self, stations):
        for item in self.tree.get_children():
            self.tree.delete(item)
        for s in stations:
            self.tree.insert("", tk.END, values=(
                s.get('name', ''),
                s.get('country', ''),
                s.get('language', ''),
                s.get('clickcount', 0),
                s.get('bitrate', 0)
            ))

    # ------------------ 同步控制 ------------------
    def start_sync(self):
        if self.sync_thread and self.sync_thread.is_alive():
            messagebox.showwarning("提示", "同步任务已在运行中")
            return
        server = self.server_entry.get().strip()
        if not server:
            messagebox.showerror("错误", "请输入服务器地址")
            return

        mode = self.sync_mode.get()
        country = self.country_combo.get().strip() if mode == "filtered" else ""
        language = self.language_combo.get().strip() if mode == "filtered" else ""
        keyword = self.keyword_entry.get().strip() if mode == "filtered" else ""

        if mode == "filtered" and not (country or language or keyword):
            if not messagebox.askyesno("确认", "未设置任何筛选条件，将下载全部电台（等同于全量同步），是否继续？"):
                return

        self.cancel_requested = False
        self.sync_btn.config(state=tk.DISABLED)
        self.cancel_btn.config(state=tk.NORMAL)
        self.progress_bar['value'] = 0
        self.status_label.config(text=f"正在连接服务器... ({mode})")
        print(f"\n[GUI] 开始同步任务，模式={mode}")

        self.syncer = RadioSyncer(server)
        self.sync_thread = threading.Thread(target=self._sync_worker, args=(mode, country, language, keyword), daemon=True)
        self.sync_thread.start()

    def _sync_worker(self, mode, country, language, keyword):
        def progress_callback(current, total, message):
            self.root.after(0, lambda: self._update_progress(current, total, message))

        try:
            if mode == "full":
                success, msg = self.syncer.sync_full(self.db, progress_callback, lambda: self.cancel_requested)
            else:
                success, msg = self.syncer.sync_filtered(self.db, country, language, keyword, progress_callback, lambda: self.cancel_requested)
        except Exception as e:
            success, msg = False, str(e)

        self.root.after(0, lambda: self._sync_finished(success, msg))

    def _update_progress(self, current, total, message):
        if total > 0:
            percent = int(current / total * 100)
            self.progress_bar['value'] = percent
            self.progress_bar['maximum'] = total
        else:
            self.progress_bar['value'] = current % 100 if current > 0 else 0
            self.progress_bar['maximum'] = 100
        self.status_label.config(text=message)

    def _sync_finished(self, success, msg):
        self.sync_btn.config(state=tk.NORMAL)
        self.cancel_btn.config(state=tk.DISABLED)
        if success:
            messagebox.showinfo("完成", msg)
            self.status_label.config(text=msg)
            self.refresh_filter_options()
            self.refresh_preview()
        else:
            messagebox.showerror("错误", msg)
            self.status_label.config(text=f"同步失败: {msg}")
        print(f"[GUI] 同步结束: {msg}")

    def cancel_sync(self):
        self.cancel_requested = True
        self.cancel_btn.config(state=tk.DISABLED)
        self.status_label.config(text="正在取消...")
        print("[GUI] 用户请求取消同步")

    # ------------------ 导出 ------------------
    def export_stations(self):
        country = self.export_country.get().strip()
        language = self.export_language.get().strip()
        keyword = self.export_keyword.get().strip()
        fmt = self.format_var.get().lower()

        if self.db.get_station_count() == 0:
            messagebox.showwarning("无数据", "当前数据库中没有任何电台，请先同步数据或打开其他数据库")
            return

        def do_export():
            stations = self.db.filter_stations(country, language, keyword)
            if not stations:
                self.root.after(0, lambda: messagebox.showinfo("提示", "没有找到符合条件的电台"))
                return
            filetypes = []
            ext = ""
            if fmt == "m3u":
                ext = ".m3u"
                filetypes = [("M3U 播放列表", "*.m3u")]
            elif fmt == "csv":
                ext = ".csv"
                filetypes = [("CSV 表格", "*.csv")]
            elif fmt == "json":
                ext = ".json"
                filetypes = [("JSON 文件", "*.json")]
            else:  # sqlite db
                ext = ".db"
                filetypes = [("SQLite 数据库", "*.db")]

            filepath = filedialog.asksaveasfilename(defaultextension=ext, filetypes=filetypes)
            if not filepath:
                return
            try:
                if fmt == "m3u":
                    ExportHelper.to_m3u(stations, filepath)
                elif fmt == "csv":
                    ExportHelper.to_csv(stations, filepath)
                elif fmt == "json":
                    ExportHelper.to_json(stations, filepath)
                else:
                    count = ExportHelper.to_db(stations, filepath)
                    self.root.after(0, lambda: messagebox.showinfo("成功", f"已导出 {count} 个电台到数据库\n{filepath}"))
                    return
                self.root.after(0, lambda: messagebox.showinfo("成功", f"已导出 {len(stations)} 个电台到\n{filepath}"))
            except Exception as e:
                self.root.after(0, lambda: messagebox.showerror("错误", f"导出失败: {e}"))

        threading.Thread(target=do_export, daemon=True).start()

    # ------------------ 数据库切换功能 ------------------
    def open_external_database(self):
        """打开其他数据库文件并切换当前数据库"""
        if self.sync_thread and self.sync_thread.is_alive():
            messagebox.showwarning("提示", "请先取消当前同步任务再切换数据库")
            return

        filepath = filedialog.askopenfilename(
            title="选择数据库文件",
            filetypes=[("SQLite数据库", "*.db"), ("所有文件", "*.*")]
        )
        if not filepath:
            return
        try:
            # 尝试连接并验证表结构
            new_db = RadioDatabase(filepath)
            # 测试是否包含 stations 表
            new_db.cursor.execute("SELECT COUNT(*) FROM stations")
            self.db.close()
            self.db = new_db
            self.current_db_label.config(text=f"当前数据库: {filepath}")
            self.refresh_filter_options()
            self.refresh_preview()
            messagebox.showinfo("成功", f"已切换到数据库: {os.path.basename(filepath)}")
        except Exception as e:
            messagebox.showerror("错误", f"无法打开数据库文件:\n{e}")

    def reset_default_database(self):
        """重置为默认数据库 (radio_stations.db)"""
        if self.sync_thread and self.sync_thread.is_alive():
            messagebox.showwarning("提示", "请先取消当前同步任务再重置数据库")
            return

        default_path = "radio_stations.db"
        if self.db.db_path == default_path:
            messagebox.showinfo("提示", "已经是默认数据库")
            return
        try:
            new_db = RadioDatabase(default_path)
            self.db.close()
            self.db = new_db
            self.current_db_label.config(text=f"当前数据库: {default_path}")
            self.refresh_filter_options()
            self.refresh_preview()
            messagebox.showinfo("成功", "已切换回默认数据库")
        except Exception as e:
            messagebox.showerror("错误", f"无法打开默认数据库:\n{e}")

    def on_closing(self):
        self.db.close()
        self.root.destroy()


if __name__ == "__main__":
    try:
        import requests
    except ImportError:
        print("请先安装 requests 库：pip install requests")
        exit(1)

    root = tk.Tk()
    app = RadioDBToolApp(root)
    root.protocol("WM_DELETE_WINDOW", app.on_closing)
    root.mainloop()
