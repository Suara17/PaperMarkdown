# 素笺 · Paper Markdown 阅读器 (Android 原生)

「素笺淡墨，纸短情长。」  
专为 Android 打造的素雅纸质风格 Markdown 阅读与轻量编辑工具。

---

## 🎨 视觉设计与风格

- **素雅纸质质感**：采用暖米黄羊皮纸底色（`#FAF7F0`）与沉稳古墨字色（`#2D2A28`），阅读如展卷品书，柔和护眼。
- **典雅层级排版**：精美下划线标题、羊皮纸卡片式代码块、古典印泥朱砂红（`#8C5338`）重点标注与层级胶囊徽章。
- **轻巧零负担**：纯原生实现，离线渲染秒开，无需笨重依赖。

---

## 🚀 核心功能

1. **文件夹与文稿管理**：
   - 支持通过侧滑文稿目录随时切换、扫描任意本地文件夹（包含 `/sdcard/Download`、系统文档目录及 SAF 存储授权目录）。
   - 自动列出全部 `.md` / `.markdown` / `.txt` 文档，并显示文件大小与修改时间。
   - 支持直接在当前文件夹内一键新建笔记（如 `新笔记.md`）。
2. **章节大纲视图 (TOC)**：
   - 智能快速解析文档中的 H1 ~ H6 各级标题，按层级缩进排列。
   - 轻点右上角大纲图标唤出目录抽屉，点击任一章节标题平滑滚动直达正文对应位置。
3. **沉浸阅读与快捷编辑**：
   - **阅读模式**：基于 marked.js 离线引擎渲染，高保真还原粗体、斜体、列表、引用块、代码高亮、表格与任务清单。
   - **编辑模式**：素雅信笺风格输入框，底部配备 Markdown 常用语法快捷键（`#`、`##`、`粗体`、`斜体`、`引用`、`清单`、`代码块`、`链接`、`分割线` 等）。
   - **自动与手动保存**：切换回阅读模式时自动静默保存，同时右上角常驻保存按钮。

---

## 📁 项目目录结构

```text
/storage/emulated/0/Download/PaperMarkdown/
├── AndroidManifest.xml          # 应用清单（声明存储读写、SAF、Launcher 等）
├── PaperMarkdown.apk            # 已编译签名的完整 APK 安装包
├── build.sh                     # 极简纯本地命令行 7 步编译流水线
├── assets/
│   ├── marked.min.js            # 离线 Markdown 解析引擎
│   └── template.html            # 纸质风格 HTML/CSS 渲染模版与平滑滚动逻辑
├── res/                         # 原生 Android 资源
│   ├── drawable/                # 纸质卡片背景与矢量图标
│   ├── layout/                  # 主界面、列表项与大纲项布局
│   └── values/                  # 纸质色板 colors.xml 与样式
└── src/com/papermarkdown/reader/
    ├── MainActivity.java        # 核心控制器（目录扫描、大纲抽屉、双模式切换、保存）
    ├── MarkdownParser.java      # 正则与大纲结构解析器
    ├── OutlineItem.java         # 大纲数据模型
    └── FileItem.java            # 文件条目数据模型
```

---

## 🛠 本地重新构建命令

在终端中执行：
```bash
bash /storage/emulated/0/Download/PaperMarkdown/build.sh
```
即会自动执行 `aapt` -> `javac` -> `d8` -> `zipalign` -> `apksigner` 全流程并输出可直接安装的 `PaperMarkdown.apk`。
