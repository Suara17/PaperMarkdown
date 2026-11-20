package com.papermarkdown.reader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Base64;
import org.json.JSONArray;
import org.json.JSONObject;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.TranslateAnimation;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final int REQUEST_CODE_SAF_FOLDER = 1001;
    private static final String PREFS_NAME = "paper_md_prefs";
    private static final String KEY_CURRENT_DIR = "current_dir";
    private static final String KEY_CURRENT_FILE = "current_file";
    private static final String KEY_SAF_TREE_URI = "saf_tree_uri";
    private static final String KEY_DARK_MODE = "key_dark_mode";
    private static final String KEY_STATS_ENABLED = "key_stats_enabled";
    private static final String KEY_WHEEL_VISIBLE = "wheel_visible_keys";
    private static final String[] WHEEL_KEYS = {"files", "search", "outline", "bookmark", "tts", "more"};
    private static final String[] WHEEL_LABELS = {"目录", "搜索", "大纲", "书签", "朗读", "更多"};
    private static final String KEY_BOOKMARKS = "bookmarks_v1";
    private static final int MAX_SEARCH_RESULTS = 80;

    // 控件：阅读与沉浸浮动菜单
    private View rootLayout;
    private WebView webView;
    private TextView tvReadingStats;
    private LinearLayout appBar;
    private TextView tvTitle;
    private TextView tvSubtitle;
    private TextView btnToggleStats;
    private FrameLayout wheelMenu;
    private TextView tvWheelCenter;
    private View btnFilesDrawer;
    private View btnOutline;
    private View btnTts;
    private View btnThemeToggle;
    private View btnToggleMode;
    private View btnSave;
    private View btnSearch;
    private View btnBookmark;
    private View btnAddBookmarkTop;
    private View btnMore;
    private View viewBookmarkDot;
    private ImageView ivBookmarkIcon;
    private ImageView ivThemeIcon;
    private TextView tvThemeLabel;
    private ImageView ivTtsIcon;
    private TextView tvTtsLabel;
    private TextView tvEditLabel;
    private ImageView ivEditIcon;
    // 控件：编辑模式
    private LinearLayout layoutEdit;
    private View editTopBar;
    private View editQuickScroll;
    private TextView tvEditTitle;
    private TextView btnEditDone;
    private EditText etContent;
    private LinearLayout layoutQuickSymbols;

    // 控件：侧拉面板（文稿目录与大纲）
    private View maskOverlay;
    private LinearLayout panelFileList;
    private View panelFileHeader;
    // 目录面板内的文档搜索
    private ImageView btnFileSearch;
    private LinearLayout layoutFileSearch;
    private LinearLayout layoutFileBreadcrumb;
    private EditText etFileSearch;
    private CheckBox cbFileRecursive;
    private CheckBox cbFileContent;
    private TextView tvFileSearchHint;
    private ImageView btnFileSearchClose;
    private boolean isFileSearchMode = false;
    private Runnable pendingFileSearch = null;
    /** 当前目录的真实列表（退出搜索时用于恢复） */
    private final List<FileItem> dirItemList = new ArrayList<>();
    /** 搜索模式下与 fileItemList 平行的文件夹路径（空串表示当前目录） */
    private final List<String> fileSearchPaths = new ArrayList<>();
    private TextView tvPanelFileTitle;
    private TextView tvCurrentDir;
    private ListView listFiles;
    private TextView tvEmptyFiles;
    private TextView btnChangeFolder;
    private ImageView btnNewFile;
    private View btnGoUp;
    private TextView tvGoUpLabel;

    private LinearLayout panelOutline;
    private View panelOutlineHeader;
    private TextView tvPanelOutlineTitle;
    private ListView listOutline;
    private TextView tvEmptyOutline;
    private ImageView btnCloseOutline;

    // 控件：下拉搜索卡片
    private View searchCard;
    private EditText etSearch;
    private TextView tvSearchCount;
    private View btnSearchUp, btnSearchDown, btnSearchClose;
    private boolean isSearchOpen = false;
    private String lastSearchKeyword = "";
    private int searchHitCurrent = 0;
    private int searchHitTotal = 0;
    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable pendingSearch = null;

    // 控件：书签面板
    private LinearLayout panelBookmark;
    private View panelBookmarkHeader;
    private TextView tvPanelBookmarkTitle;
    private ImageView btnAddBookmark;
    private ImageView btnCloseBookmark;
    private TextView tabBookmarks, tabFolderSearch;
    private LinearLayout layoutBookmarkList;
    private ListView listBookmarks;
    private TextView tvEmptyBookmarks;
    private LinearLayout layoutFolderSearch;
    private EditText etFolderSearch;
    private TextView btnDoFolderSearch;
    private CheckBox cbFolderRecursive;
    private TextView tvFolderSearchHint;
    private ListView listFolderResults;
    private TextView tvEmptyFolderResults;

    // 轮盘按钮显示配置
    private final java.util.Set<String> wheelVisibleKeys = new java.util.HashSet<>();

    // 书签数据
    /** 全量书签（持久化的唯一数据源，跨文档） */
    private final List<Bookmark> allBookmarks = new ArrayList<>();
    /** 当前文档的书签（面板展示用） */
    private final List<Bookmark> bookmarks = new ArrayList<>();
    private BookmarkAdapter bookmarkAdapter;
    private final List<SearchResult> searchResults = new ArrayList<>();
    private SearchResultAdapter searchResultAdapter;
    private final ExecutorService searchExecutor = Executors.newSingleThreadExecutor();

    // 分割线控件集合
    private View div1, div2, div3, div4, div5;
    private View divDrawer1, divDrawer2, divOutline1;

    // 状态数据
    private boolean isDarkMode = false;
    private boolean isEditMode = false;
    private boolean isWebViewReady = false;
    private boolean isControlsMenuVisible = false;
    private String currentMarkdownText = "";
    private File currentLocalFile = null;
    private Uri currentSafUri = null;
    private String currentFileName = "欢迎使用素笺.md";
    private File currentDirectory = null;
    private Uri currentTreeUri = null;
    private Uri safRootTreeUri = null;
    private String safCurrentDocId = null;
    private ArrayList<File> localNavStack = new ArrayList<>();
    private ArrayList<String> safPathStack = new ArrayList<>();
    private boolean navFromDirectoryList = false;

    // 语音朗读 (TTS) 相关状态
    private TextToSpeech textToSpeech;
    private boolean isTtsInitialized = false;
    private boolean isSpeaking = false;
    private String pendingSpeechText = null;

    private final List<FileItem> fileItemList = new ArrayList<>();
    private final List<OutlineItem> allOutlineList = new ArrayList<>();
    private final List<OutlineItem> outlineItemList = new ArrayList<>();
    private FileAdapter fileAdapter;
    private OutlineAdapter outlineAdapter;

    private Typeface paperTypeface = null;

    // 阅读统计：时长与次数
    private static final String PREF_STATS_PREFIX = "stats_";
    private boolean isStatsEnabled = true;
    private String currentDocKey = null;
    private long docSessionStartTime = 0;
    private android.os.Handler statsHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable statsTickerRunnable = new Runnable() {
        @Override
        public void run() {
            updateReadingStatsUi();
            statsHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        loadCustomFont();
        setupWebView();
        setupQuickToolbar();
        setupAdapters();
        setupListeners();
        setupSearchUi();
        setupBookmarkUi();
        setupFileSearchUi();
        loadWheelVisible();
        loadBookmarks();
        initTtsEngine();

        // 读取日间/夜间偏好并应用
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        isDarkMode = sp.getBoolean(KEY_DARK_MODE, false);
        isStatsEnabled = sp.getBoolean(KEY_STATS_ENABLED, true);
        applyTheme(isDarkMode);
        updateStatsButtonUi();

        // 默认先展示欢迎文档，确保界面初次打开绝非空白
        showWelcomeDoc();

        loadLastLocationOrDefaults();
    }

    private void initViews() {
        rootLayout = findViewById(R.id.root_layout);
        webView = findViewById(R.id.web_view);
        tvReadingStats = findViewById(R.id.tv_reading_stats);
        appBar = findViewById(R.id.app_bar);
        tvTitle = findViewById(R.id.tv_title);
        tvSubtitle = findViewById(R.id.tv_subtitle);
        btnToggleStats = findViewById(R.id.btn_toggle_stats);
        wheelMenu = findViewById(R.id.wheel_menu);
        tvWheelCenter = findViewById(R.id.tv_wheel_center);

        btnFilesDrawer = findViewById(R.id.btn_files_drawer);
        btnOutline = findViewById(R.id.btn_outline);
        btnTts = findViewById(R.id.btn_tts);
        ivTtsIcon = findViewById(R.id.iv_tts_icon);
        tvTtsLabel = findViewById(R.id.tv_tts_label);

        btnThemeToggle = findViewById(R.id.btn_theme_toggle);
        ivThemeIcon = findViewById(R.id.iv_theme_icon);
        tvThemeLabel = findViewById(R.id.tv_theme_label);

        btnToggleMode = findViewById(R.id.btn_toggle_mode);
        ivEditIcon = findViewById(R.id.iv_edit_icon);
        tvEditLabel = findViewById(R.id.tv_edit_label);
        btnSave = findViewById(R.id.btn_save);

        layoutEdit = findViewById(R.id.layout_edit);
        editTopBar = findViewById(R.id.edit_top_bar);
        editQuickScroll = findViewById(R.id.edit_quick_scroll);
        tvEditTitle = findViewById(R.id.tv_edit_title);
        btnEditDone = findViewById(R.id.btn_edit_done);
        etContent = findViewById(R.id.et_content);
        layoutQuickSymbols = findViewById(R.id.layout_quick_symbols);

        maskOverlay = findViewById(R.id.mask_overlay);
        panelFileList = findViewById(R.id.panel_file_list);
        panelFileHeader = findViewById(R.id.panel_file_header);
        btnFileSearch = findViewById(R.id.btn_file_search);
        layoutFileSearch = findViewById(R.id.layout_file_search);
        layoutFileBreadcrumb = findViewById(R.id.layout_file_breadcrumb);
        etFileSearch = findViewById(R.id.et_file_search);
        cbFileRecursive = findViewById(R.id.cb_file_recursive);
        cbFileContent = findViewById(R.id.cb_file_content);
        tvFileSearchHint = findViewById(R.id.tv_file_search_hint);
        btnFileSearchClose = findViewById(R.id.btn_file_search_close);
        tvPanelFileTitle = findViewById(R.id.tv_panel_file_title);
        tvCurrentDir = findViewById(R.id.tv_current_dir);
        listFiles = findViewById(R.id.list_files);
        tvEmptyFiles = findViewById(R.id.tv_empty_files);
        btnChangeFolder = null; // 「目录」按钮已移除
        btnNewFile = findViewById(R.id.btn_new_file);
        btnGoUp = findViewById(R.id.btn_go_up);
        tvGoUpLabel = findViewById(R.id.tv_go_up_label);

        panelOutline = findViewById(R.id.panel_outline);
        panelOutlineHeader = findViewById(R.id.panel_outline_header);
        tvPanelOutlineTitle = findViewById(R.id.tv_panel_outline_title);
        listOutline = findViewById(R.id.list_outline);
        tvEmptyOutline = findViewById(R.id.tv_empty_outline);
        btnCloseOutline = findViewById(R.id.btn_close_outline);

        btnAddBookmarkTop = findViewById(R.id.btn_add_bookmark_top);

        btnSearch = findViewById(R.id.btn_search);
        btnBookmark = findViewById(R.id.btn_bookmark);
        btnMore = findViewById(R.id.btn_more);
        viewBookmarkDot = findViewById(R.id.view_bookmark_dot);
        ivBookmarkIcon = findViewById(R.id.iv_bookmark_icon);

        searchCard = findViewById(R.id.layout_search_card);
        etSearch = findViewById(R.id.et_search);
        tvSearchCount = findViewById(R.id.tv_search_count);
        btnSearchUp = findViewById(R.id.btn_search_up);
        btnSearchDown = findViewById(R.id.btn_search_down);
        btnSearchClose = findViewById(R.id.btn_search_close);

        panelBookmark = findViewById(R.id.panel_bookmark_include);
        panelBookmarkHeader = findViewById(R.id.panel_bookmark_header);
        tvPanelBookmarkTitle = findViewById(R.id.tv_panel_bookmark_title);
        btnAddBookmark = findViewById(R.id.btn_add_bookmark);
        btnCloseBookmark = findViewById(R.id.btn_close_bookmark);
        tabBookmarks = findViewById(R.id.tab_bookmarks);
        tabFolderSearch = findViewById(R.id.tab_folder_search);
        layoutBookmarkList = findViewById(R.id.layout_bookmark_list);
        listBookmarks = findViewById(R.id.list_bookmarks);
        tvEmptyBookmarks = findViewById(R.id.tv_empty_bookmarks);
        layoutFolderSearch = findViewById(R.id.layout_folder_search);
        etFolderSearch = findViewById(R.id.et_folder_search);
        btnDoFolderSearch = findViewById(R.id.btn_do_folder_search);
        cbFolderRecursive = findViewById(R.id.cb_folder_recursive);
        tvFolderSearchHint = findViewById(R.id.tv_folder_search_hint);
        listFolderResults = findViewById(R.id.list_folder_results);
        tvEmptyFolderResults = findViewById(R.id.tv_empty_folder_results);

        divDrawer1 = findViewById(R.id.div_drawer_1);
        divDrawer2 = findViewById(R.id.div_drawer_2);
        divOutline1 = findViewById(R.id.div_outline_1);
    }

    private void loadCustomFont() {
        try {
            paperTypeface = Typeface.createFromAsset(getAssets(), "fonts/paper_font.ttf");
            if (paperTypeface != null) {
                etContent.setTypeface(paperTypeface);
                tvTitle.setTypeface(paperTypeface, Typeface.BOLD);
                tvSubtitle.setTypeface(paperTypeface);
                tvEditTitle.setTypeface(paperTypeface, Typeface.BOLD);
                btnEditDone.setTypeface(paperTypeface, Typeface.BOLD);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setDefaultTextEncodingName("UTF-8");

        webView.setBackgroundColor(isDarkMode ? Color.parseColor("#1A1918") : Color.parseColor("#FAF7F0"));

        // 注册桥接：接收点击页面中间唤起/收起菜单
        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                isWebViewReady = true;
                syncWebViewTheme();
                if (!TextUtils.isEmpty(currentMarkdownText)) {
                    renderInWebView(currentMarkdownText);
                } else {
                    showWelcomeDoc();
                }
            }
        });

        webView.loadUrl("file:///android_asset/template.html");
    }

    private class WebAppInterface {
        @JavascriptInterface
        public void toggleMenu() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    toggleControlsMenu();
                }
            });
        }
    }

    private void toggleControlsMenu() {
        if (isEditMode) return;
        if (isControlsMenuVisible) {
            hideControlsMenu(true);
        } else {
            showControlsMenu(true);
        }
    }

    // ==================== 轮盘按钮显示配置 ====================

    private void loadWheelVisible() {
        wheelVisibleKeys.clear();
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = sp.getString(KEY_WHEEL_VISIBLE, null);
        if (json != null && json.trim().startsWith("[")) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    String k = arr.optString(i);
                    if (k != null && !k.trim().isEmpty()) wheelVisibleKeys.add(k.trim());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (wheelVisibleKeys.isEmpty()) {
            for (String k : WHEEL_KEYS) wheelVisibleKeys.add(k); // 默认全部显示
        }
    }

    private void persistWheelVisible() {
        JSONArray arr = new JSONArray();
        for (String k : WHEEL_KEYS) {
            if (wheelVisibleKeys.contains(k)) arr.put(k);
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_WHEEL_VISIBLE, arr.toString())
                .apply();
    }

    private boolean isWheelVisible(String key) {
        return wheelVisibleKeys.contains(key);
    }

    /** 把可见的轮盘按钮按数量均匀分布在圆周上，隐藏的不留空位 */
    private void applyWheelLayout() {
        View[] views = {btnFilesDrawer, btnSearch, btnOutline, btnBookmark, btnTts, btnMore};
        List<View> shown = new ArrayList<>();
        for (int i = 0; i < views.length; i++) {
            if (views[i] == null) continue;
            if (isWheelVisible(WHEEL_KEYS[i])) {
                views[i].setVisibility(View.VISIBLE);
                shown.add(views[i]);
            } else {
                views[i].setVisibility(View.GONE);
            }
        }
        if (shown.isEmpty()) return;

        float density = getResources().getDisplayMetrics().density;
        float cx = (154f / 2f) * density;
        float cy = (154f / 2f) * density;
        float radius = 52f * density;
        int n = shown.size();
        for (int i = 0; i < n; i++) {
            View btn = shown.get(i);
            double angle = (-90 + i * (360.0 / n)) * Math.PI / 180.0;
            float bx = (float) (cx + radius * Math.cos(angle));
            float by = (float) (cy + radius * Math.sin(angle));
            btn.setX(bx - (46f * density) / 2f);
            btn.setY(by - (40f * density) / 2f);
        }
    }

    private void showControlsMenu(boolean animate) {
        if (isControlsMenuVisible) return;
        isControlsMenuVisible = true;

        appBar.setVisibility(View.VISIBLE);
        wheelMenu.setVisibility(View.VISIBLE);
        applyWheelLayout();

        if (animate) {
            Animation fadeIn = new AlphaAnimation(0f, 1f);
            fadeIn.setDuration(200);
            appBar.startAnimation(fadeIn);

            // 右侧圆形轮盘从右侧滑出并微缩放弹出
            Animation slideIn = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 1.0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f);
            slideIn.setDuration(240);
            slideIn.setInterpolator(new DecelerateInterpolator());
            wheelMenu.startAnimation(slideIn);
        }
    }

    private void hideControlsMenu(boolean animate) {
        if (!isControlsMenuVisible) return;
        isControlsMenuVisible = false;

        if (animate) {
            Animation fadeOut = new AlphaAnimation(1f, 0f);
            fadeOut.setDuration(180);
            fadeOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}
                @Override
                public void onAnimationEnd(Animation animation) {
                    appBar.setVisibility(View.GONE);
                }
                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            appBar.startAnimation(fadeOut);

            // 右侧圆形轮盘收回右侧
            Animation slideOut = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 1.0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f);
            slideOut.setDuration(180);
            slideOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}
                @Override
                public void onAnimationEnd(Animation animation) {
                    wheelMenu.setVisibility(View.GONE);
                }
                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            wheelMenu.startAnimation(slideOut);
        } else {
            appBar.setVisibility(View.GONE);
            wheelMenu.setVisibility(View.GONE);
        }
    }

    // --- 日间/夜间模式核心逻辑 ---

    private void toggleTheme() {
        isDarkMode = !isDarkMode;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_DARK_MODE, isDarkMode)
                .apply();
        applyTheme(isDarkMode);
        Toast.makeText(this, isDarkMode ? "已切换至夜间墨色模式" : "已切换至日间宣纸模式", Toast.LENGTH_SHORT).show();
    }

    private void applyTheme(boolean dark) {
        int colorBg = dark ? Color.parseColor("#1A1918") : Color.parseColor("#FAF7F0");
        int colorSurface = dark ? Color.parseColor("#242220") : Color.parseColor("#FFFDF9");
        int colorSurfaceVariant = dark ? Color.parseColor("#2A2825") : Color.parseColor("#F3EFE6");
        int colorTextPrimary = dark ? Color.parseColor("#D6D0C7") : Color.parseColor("#2D2A28");
        int colorTextSecondary = dark ? Color.parseColor("#8C857C") : Color.parseColor("#7A746E");
        int colorBorder = dark ? Color.parseColor("#383532") : Color.parseColor("#E2DBD0");
        int colorAccent = dark ? Color.parseColor("#D48B68") : Color.parseColor("#8C5338");

        // 1. 系统导航栏/状态栏沉浸色
        Window window = getWindow();
        if (window != null) {
            window.setStatusBarColor(colorBg);
            window.setNavigationBarColor(colorBg);
        }

        if (tvReadingStats != null) {
            tvReadingStats.setTextColor(colorTextSecondary);
            GradientDrawable statsBg = new GradientDrawable();
            statsBg.setColor(dark ? Color.parseColor("#262421") : Color.parseColor("#EFEBE2"));
            statsBg.setCornerRadius(12 * getResources().getDisplayMetrics().density);
            statsBg.setStroke((int) (1 * getResources().getDisplayMetrics().density), colorBorder);
            tvReadingStats.setBackground(statsBg);
        }

        // 2. 根布局与编辑器
        rootLayout.setBackgroundColor(colorBg);
        webView.setBackgroundColor(colorBg);
        layoutEdit.setBackgroundColor(colorBg);
        editTopBar.setBackgroundColor(colorSurface);
        editQuickScroll.setBackgroundColor(colorSurface);
        etContent.setTextColor(colorTextPrimary);
        etContent.setHintTextColor(colorTextSecondary);
        tvEditTitle.setTextColor(colorTextPrimary);
        btnEditDone.setTextColor(colorAccent);

        // 3. 顶栏
        appBar.setBackgroundColor(colorSurface);
        tvTitle.setTextColor(colorTextPrimary);
        tvSubtitle.setTextColor(colorTextSecondary);

        // 4. 右侧圆形悬浮轮盘控件
        float density = getResources().getDisplayMetrics().density;
        GradientDrawable wheelDrawable = new GradientDrawable();
        wheelDrawable.setShape(GradientDrawable.OVAL);
        wheelDrawable.setColor(dark ? Color.parseColor("#262421") : Color.parseColor("#FAF7F0"));
        wheelDrawable.setStroke((int) (2f * density), colorBorder);
        wheelMenu.setBackground(wheelDrawable);

        if (tvWheelCenter != null) {
            tvWheelCenter.setTextColor(colorTextSecondary);
        }

        // 5. 分割线
        divDrawer1.setBackgroundColor(colorBorder);
        divDrawer2.setBackgroundColor(colorBorder);
        divOutline1.setBackgroundColor(colorBorder);

        // 6. 标签与按钮文字
        ((TextView) findViewById(R.id.tv_drawer_label)).setTextColor(colorTextPrimary);
        ((TextView) findViewById(R.id.tv_outline_label)).setTextColor(colorTextPrimary);
        ((TextView) findViewById(R.id.tv_edit_label)).setTextColor(colorTextPrimary);
        ((TextView) findViewById(R.id.tv_save_label)).setTextColor(colorAccent);

        // 7. 切换模式按钮图标与标签
        if (dark) {
            ivThemeIcon.setImageResource(R.drawable.ic_mode_day);
            tvThemeLabel.setText("日间");
            tvThemeLabel.setTextColor(colorTextPrimary);
        } else {
            ivThemeIcon.setImageResource(R.drawable.ic_mode_night);
            tvThemeLabel.setText("夜间");
            tvThemeLabel.setTextColor(colorTextPrimary);
        }

        // 8. 抽屉面板
        GradientDrawable drawerDrawable = new GradientDrawable();
        drawerDrawable.setColor(colorBg);
        drawerDrawable.setCornerRadii(new float[]{0, 0, 20 * density, 20 * density, 20 * density, 20 * density, 0, 0});
        drawerDrawable.setStroke((int) (1.5f * density), colorBorder);
        panelFileList.setBackground(drawerDrawable);

        panelFileHeader.setBackgroundColor(colorSurfaceVariant);
        tvPanelFileTitle.setTextColor(colorTextPrimary);
        tvCurrentDir.setTextColor(colorTextSecondary);
        if (btnChangeFolder != null) btnChangeFolder.setTextColor(colorAccent);
        if (tvGoUpLabel != null) {
            tvGoUpLabel.setTextColor(colorAccent);
        }

        // 目录面板搜索条
        if (layoutFileSearch != null) {
            layoutFileSearch.setBackgroundColor(colorSurface);
            etFileSearch.setTextColor(colorTextPrimary);
            etFileSearch.setHintTextColor(colorTextSecondary);
            tvFileSearchHint.setTextColor(colorTextSecondary);
            cbFileRecursive.setTextColor(colorTextSecondary);
            cbFileContent.setTextColor(colorTextSecondary);
            layoutFileBreadcrumb.setBackgroundColor(colorSurface);
            btnFileSearch.setColorFilter(colorAccent);
        }

        // 9. 大纲面板 (右侧抽屉)
        GradientDrawable outlineSheetDrawable = new GradientDrawable();
        outlineSheetDrawable.setColor(colorBg);
        outlineSheetDrawable.setCornerRadii(new float[]{24 * density, 24 * density, 0, 0, 0, 0, 24 * density, 24 * density});
        outlineSheetDrawable.setStroke((int) (1.5f * density), colorBorder);
        panelOutline.setBackground(outlineSheetDrawable);

        panelOutlineHeader.setBackgroundColor(colorSurfaceVariant);
        tvPanelOutlineTitle.setTextColor(colorTextPrimary);

        // 10. 搜索卡片
        if (searchCard != null) {
            GradientDrawable searchCardBg = new GradientDrawable();
            searchCardBg.setColor(colorSurface);
            searchCardBg.setCornerRadius(14 * density);
            searchCardBg.setStroke((int) (1f * density), colorBorder);
            searchCard.setBackground(searchCardBg);
            if (etSearch != null) {
                etSearch.setTextColor(colorTextPrimary);
                etSearch.setHintTextColor(colorTextSecondary);
            }
            tvSearchCount.setBackgroundResource(R.drawable.bg_search_count);
        }

        // 11. 书签面板
        if (panelBookmark != null) {
            GradientDrawable bookmarkSheetBg = new GradientDrawable();
            bookmarkSheetBg.setColor(colorBg);
            bookmarkSheetBg.setCornerRadii(new float[]{24 * density, 24 * density, 0, 0, 0, 0, 24 * density, 24 * density});
            bookmarkSheetBg.setStroke((int) (1.5f * density), colorBorder);
            panelBookmark.setBackground(bookmarkSheetBg);

            panelBookmarkHeader.setBackgroundColor(colorSurfaceVariant);
            tvPanelBookmarkTitle.setTextColor(colorTextPrimary);
            ((View) findViewById(R.id.div_bookmark_1)).setBackgroundColor(colorBorder);
            ((View) findViewById(R.id.div_bookmark_2)).setBackgroundColor(colorBorder);
            tabBookmarks.setTextColor(layoutBookmarkList.getVisibility() == View.VISIBLE ? colorAccent : colorTextSecondary);
            tabFolderSearch.setTextColor(layoutBookmarkList.getVisibility() == View.VISIBLE ? colorTextSecondary : colorAccent);
            tvEmptyBookmarks.setTextColor(colorTextSecondary);
            tvEmptyFolderResults.setTextColor(colorTextSecondary);
            tvFolderSearchHint.setTextColor(colorTextSecondary);
            etFolderSearch.setTextColor(colorTextPrimary);
            etFolderSearch.setHintTextColor(colorTextSecondary);
            btnDoFolderSearch.setTextColor(colorAccent);
            cbFolderRecursive.setTextColor(colorTextSecondary);
            GradientDrawable folderSearchBg = new GradientDrawable();
            folderSearchBg.setColor(colorSurface);
            folderSearchBg.setCornerRadius(14 * density);
            folderSearchBg.setStroke((int) (1f * density), colorBorder);
            etFolderSearch.setBackground(folderSearchBg);
            ivBookmarkIcon.setColorFilter(colorTextPrimary);
            ((ImageView) findViewById(R.id.btn_add_bookmark_top)).setColorFilter(colorAccent);
            btnAddBookmark.setColorFilter(colorAccent);
            ((ImageView) findViewById(R.id.iv_search_icon)).setColorFilter(colorTextPrimary);
            ((ImageView) findViewById(R.id.iv_more_icon)).setColorFilter(colorTextPrimary);
            ((ImageView) findViewById(R.id.iv_outline_icon)).setColorFilter(colorTextPrimary);
            ((ImageView) findViewById(R.id.iv_folder_icon)).setColorFilter(colorTextPrimary);
            if (bookmarkAdapter != null) bookmarkAdapter.notifyDataSetChanged();
            if (searchResultAdapter != null) searchResultAdapter.notifyDataSetChanged();
        }

        updateTtsUi();
        updateStatsButtonUi();
        syncWebViewTheme();
        fileAdapter.notifyDataSetChanged();
        outlineAdapter.notifyDataSetChanged();
    }

    private void syncWebViewTheme() {
        if (!isWebViewReady) return;
        String js = "setDarkMode(" + isDarkMode + ");";
        webView.evaluateJavascript(js, null);
    }

    // --- TTS 模块 ---

    private void initTtsEngine() {
        try {
            textToSpeech = new TextToSpeech(getApplicationContext(), this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            isTtsInitialized = true;
            try {
                int result = textToSpeech.setLanguage(Locale.CHINESE);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    textToSpeech.setLanguage(Locale.getDefault());
                }
                textToSpeech.setSpeechRate(0.95f);
                textToSpeech.setPitch(1.0f);

                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                isSpeaking = true;
                                updateTtsUi();
                            }
                        });
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                isSpeaking = false;
                                updateTtsUi();
                            }
                        });
                    }

                    @Override
                    public void onError(String utteranceId) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                isSpeaking = false;
                                updateTtsUi();
                            }
                        });
                    }
                });

                if (pendingSpeechText != null) {
                    final String toSpeak = pendingSpeechText;
                    pendingSpeechText = null;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            speakText(toSpeak);
                            Toast.makeText(MainActivity.this, "开始语音朗读...", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            isTtsInitialized = false;
        }
    }

    private void toggleTts() {
        if (isSpeaking) {
            stopTts();
            Toast.makeText(this, "已停止朗读", Toast.LENGTH_SHORT).show();
            return;
        }

        String text = isEditMode ? etContent.getText().toString() : currentMarkdownText;
        String cleanText = cleanMarkdownForSpeech(text);
        if (TextUtils.isEmpty(cleanText)) {
            Toast.makeText(this, "当前没有可朗读的文字", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isTtsInitialized || textToSpeech == null) {
            pendingSpeechText = cleanText;
            Toast.makeText(this, "正在连接系统语音服务，请稍候...", Toast.LENGTH_SHORT).show();
            initTtsEngine();
            return;
        }

        speakText(cleanText);
        Toast.makeText(this, "开始语音朗读...", Toast.LENGTH_SHORT).show();
    }

    private void speakText(String text) {
        if (textToSpeech == null) return;
        textToSpeech.stop();

        String[] paragraphs = text.split("\n+");
        for (int i = 0; i < paragraphs.length; i++) {
            String para = paragraphs[i].trim();
            if (!para.isEmpty()) {
                int queueMode = (i == 0) ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
                textToSpeech.speak(para, queueMode, null, "utterance_" + i);
            }
        }
        isSpeaking = true;
        updateTtsUi();
    }

    private void stopTts() {
        if (textToSpeech != null) {
            textToSpeech.stop();
        }
        isSpeaking = false;
        pendingSpeechText = null;
        updateTtsUi();
    }

    private void updateTtsUi() {
        if (isSpeaking) {
            tvTtsLabel.setText("停止");
            tvTtsLabel.setTextColor(isDarkMode ? Color.parseColor("#E59F7D") : Color.parseColor("#8C5338"));
        } else {
            tvTtsLabel.setText("朗读");
            tvTtsLabel.setTextColor(isDarkMode ? Color.parseColor("#D6D0C7") : Color.parseColor("#2D2A28"));
        }
    }

    private String cleanMarkdownForSpeech(String md) {
        if (md == null) return "";
        return md.replaceAll("```[\\s\\S]*?```", "此处是一段代码。\n")
                 .replaceAll("`([^`]+)`", "$1")
                 .replaceAll("!\\[.*?\\]\\(.*?\\)", "")
                 .replaceAll("\\[(.*?)\\]\\(.*?\\)", "$1")
                 .replaceAll("^[#\\s]+", "")
                 .replaceAll("\n[#\\s]+", "\n")
                 .replaceAll("[*_~>\\-]", " ")
                 .replaceAll("[ \\t]+", " ")
                 .trim();
    }

    // --- 快捷栏与适配器 ---

    private void setupQuickToolbar() {
        final String[][] symbols = new String[][]{
                {"#", "# "},
                {"##", "## "},
                {"###", "### "},
                {"粗体", "**", "**"},
                {"斜体", "*", "*"},
                {"引用", "> "},
                {"清单", "- [ ] "},
                {"代码块", "```\n", "\n```"},
                {"行内代码", "`", "`"},
                {"链接", "[", "](url)"},
                {"分割线", "\n---\n"}
        };

        for (int i = 0; i < symbols.length; i++) {
            final String[] sym = symbols[i];
            TextView btn = new TextView(this);
            btn.setText(sym[0]);
            btn.setTextColor(Color.parseColor("#2D2A28"));
            btn.setTextSize(13);
            if (paperTypeface != null) {
                btn.setTypeface(paperTypeface);
            }
            btn.setPadding(26, 12, 26, 12);
            btn.setBackgroundResource(R.drawable.bg_quick_key);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(6, 4, 6, 4);
            btn.setLayoutParams(lp);

            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int start = etContent.getSelectionStart();
                    int end = etContent.getSelectionEnd();
                    Editable editable = etContent.getText();
                    if (start < 0) start = 0;
                    if (end < 0) end = 0;

                    if (sym.length == 2) {
                        editable.insert(start, sym[1]);
                    } else if (sym.length == 3) {
                        if (start != end) {
                            editable.insert(start, sym[1]);
                            editable.insert(end + sym[1].length(), sym[2]);
                        } else {
                            editable.insert(start, sym[1] + sym[2]);
                            etContent.setSelection(start + sym[1].length());
                        }
                    }
                }
            });
            layoutQuickSymbols.addView(btn);
        }
    }

    private void setupAdapters() {
        fileAdapter = new FileAdapter();
        listFiles.setAdapter(fileAdapter);

        outlineAdapter = new OutlineAdapter();
        listOutline.setAdapter(outlineAdapter);

        bookmarkAdapter = new BookmarkAdapter();
        listBookmarks.setAdapter(bookmarkAdapter);

        searchResultAdapter = new SearchResultAdapter();
        listFolderResults.setAdapter(searchResultAdapter);
    }

    private void setupListeners() {
        // 目录抽屉
        btnFilesDrawer.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideControlsMenu(false);
                toggleFileListPanel();
            }
        });

        // 大纲面板
        btnOutline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideControlsMenu(false);
                toggleOutlinePanel();
            }
        });

        // 顶栏书签按钮：一键把当前阅读位置加入书签
        btnAddBookmarkTop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideControlsMenu(false);
                addBookmarkAtCurrentPosition();
            }
        });

        // 搜索（下拉卡片）
        btnSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideControlsMenu(false);
                openSearchCard();
            }
        });

        // 书签面板
        btnBookmark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideControlsMenu(false);
                toggleBookmarkPanel();
            }
        });

        // 更多：夜间 / 保存 / 编辑
        btnMore.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMoreMenu();
            }
        });

        // 朗读
        btnTts.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTts();
            }
        });

        // 日夜间模式切换
        btnThemeToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleTheme();
            }
        });

        btnCloseOutline.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideOutlinePanel();
            }
        });

        // 编辑模式切换
        btnToggleMode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideControlsMenu(false);
                toggleMode();
            }
        });

        btnEditDone.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleMode();
            }
        });

        // 保存当前文档
        btnSave.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentFile(true);
            }
        });

        // 统计开关
        if (btnToggleStats != null) {
            btnToggleStats.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggleReadingStats();
                }
            });
        }

        // 遮罩点击
        maskOverlay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideFileListPanel();
                hideOutlinePanel();
                hideBookmarkPanel();
            }
        });

        if (btnChangeFolder != null) {
            btnChangeFolder.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pickFolder();
                }
            });
        }

        btnNewFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreateFileDialog();
            }
        });

        listFiles.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= fileItemList.size()) return;
                FileItem item = fileItemList.get(position);
                if (item.isDirectory) {
                    // 进入子目录
                    enterDirectory(item);
                } else {
                    stopTts();
                    if (isFileSearchMode) {
                        closeFileSearch();
                    }
                    loadFile(item);
                    hideFileListPanel();
                }
            }
        });

        // 新增：返回上级目录
        btnGoUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goUpDirectory();
            }
        });

        listOutline.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                OutlineItem item = outlineItemList.get(position);
                if (isEditMode) {
                    toggleMode();
                }
                scrollToOutlineIndex(item.index);
                hideOutlinePanel();
                hideControlsMenu(false);
            }
        });
    }

    // ==================== 下拉搜索卡片 ====================

    private void setupSearchUi() {
        if (etSearch == null) return;

        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                scheduleSearch(s.toString());
            }
        });

        etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                stepSearchInPage(1);
                return true;
            }
        });

        btnSearchUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stepSearchInPage(-1);
            }
        });

        btnSearchDown.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stepSearchInPage(1);
            }
        });

        btnSearchClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeSearchCard();
            }
        });
    }

    /** 输入停顿 180ms 后再渲染，避免逐字搜索造成卡顿 */
    private void scheduleSearch(final String keyword) {
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
        }
        pendingSearch = new Runnable() {
            @Override
            public void run() {
                performSearchInPage(keyword);
            }
        };
        searchHandler.postDelayed(pendingSearch, 180);
    }

    private void openSearchCard() {
        if (isEditMode) {
            Toast.makeText(this, "请先退出编辑模式再搜索", Toast.LENGTH_SHORT).show();
            return;
        }
        hideFileListPanel();
        hideOutlinePanel();
        hideBookmarkPanel();
        isSearchOpen = true;

        if (tvReadingStats != null) {
            tvReadingStats.setVisibility(View.GONE);
        }
        searchCard.setVisibility(View.VISIBLE);
        Animation dropIn = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, -1.2f,
                Animation.RELATIVE_TO_SELF, 0f);
        dropIn.setDuration(220);
        dropIn.setInterpolator(new DecelerateInterpolator());
        searchCard.startAnimation(dropIn);

        etSearch.requestFocus();
        etSearch.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(etSearch, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 120);
    }

    private void closeSearchCard() {
        if (!isSearchOpen) return;
        isSearchOpen = false;
        if (pendingSearch != null) {
            searchHandler.removeCallbacks(pendingSearch);
            pendingSearch = null;
        }
        Animation dropOut = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, -1.2f);
        dropOut.setDuration(180);
        dropOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                searchCard.setVisibility(View.GONE);
            }
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        searchCard.startAnimation(dropOut);

        etSearch.setText("");
        etSearch.clearFocus();
        lastSearchKeyword = "";
        searchHitCurrent = 0;
        searchHitTotal = 0;
        tvSearchCount.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(etSearch.getWindowToken(), 0);
        }
        webView.evaluateJavascript("clearSearch();", null);
        updateReadingStatsUi();
    }

    /** 在 WebView 内执行搜索并回写命中数 */
    private void performSearchInPage(final String keyword) {
        lastSearchKeyword = keyword == null ? "" : keyword;
        if (!isWebViewReady) {
            return;
        }
        if (lastSearchKeyword.trim().isEmpty()) {
            searchHitCurrent = 0;
            searchHitTotal = 0;
            tvSearchCount.setText("");
            webView.evaluateJavascript("clearSearch();", null);
            return;
        }

        String js = "searchInPage(" + JSONObject.quote(lastSearchKeyword) + ");";
        webView.evaluateJavascript(js, new android.webkit.ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                int total = 0;
                try {
                    total = (int) Double.parseDouble(value.trim().replace("\"", ""));
                } catch (Exception ignored) {
                }
                searchHitTotal = total;
                searchHitCurrent = total > 0 ? 1 : 0;
                updateSearchCountUi();
            }
        });
    }

    private void stepSearchInPage(final int dir) {
        if (searchHitTotal <= 0) {
            if (!TextUtils.isEmpty(lastSearchKeyword)) {
                performSearchInPage(lastSearchKeyword);
            }
            return;
        }
        webView.evaluateJavascript("stepSearch(" + dir + ");", new android.webkit.ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                if (value == null) return;
                String cleaned = value.replace("\"", "").trim();
                String[] parts = cleaned.split(",");
                if (parts.length == 2) {
                    try {
                        searchHitCurrent = Integer.parseInt(parts[0].trim());
                        searchHitTotal = Integer.parseInt(parts[1].trim());
                    } catch (NumberFormatException ignored) {
                    }
                }
                updateSearchCountUi();
            }
        });
    }

    private void updateSearchCountUi() {
        if (tvSearchCount == null) return;
        if (lastSearchKeyword.trim().isEmpty()) {
            tvSearchCount.setText("");
            tvSearchCount.setTextColor(textSecondaryColor());
        } else if (searchHitTotal == 0) {
            tvSearchCount.setText("无匹配");
            tvSearchCount.setTextColor(textSecondaryColor());
        } else {
            tvSearchCount.setText(searchHitCurrent + "/" + searchHitTotal);
            tvSearchCount.setTextColor(accentColor());
        }
    }

    // ==================== 书签 ====================

    private void setupBookmarkUi() {
        if (panelBookmark == null) return;

        tabBookmarks.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchBookmarkTab(true);
            }
        });
        tabFolderSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchBookmarkTab(false);
            }
        });

        btnAddBookmark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchBookmarkTab(true);
                addBookmarkAtCurrentPosition();
            }
        });

        btnCloseBookmark.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideBookmarkPanel();
            }
        });

        listBookmarks.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= bookmarks.size()) return;
                jumpToBookmark(bookmarks.get(position));
            }
        });

        listBookmarks.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < bookmarks.size()) {
                    showBookmarkOptions(bookmarks.get(position));
                }
                return true;
            }
        });

        btnDoFolderSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startFolderSearch();
            }
        });

        etFolderSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                startFolderSearch();
                return true;
            }
        });

        listFolderResults.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position < 0 || position >= searchResults.size()) return;
                openSearchResult(searchResults.get(position));
            }
        });
    }

    private void switchBookmarkTab(boolean showBookmarks) {
        layoutBookmarkList.setVisibility(showBookmarks ? View.VISIBLE : View.GONE);
        layoutFolderSearch.setVisibility(showBookmarks ? View.GONE : View.VISIBLE);

        if (showBookmarks) {
            tabBookmarks.setTextColor(accentColor());
            tabBookmarks.setBackgroundResource(R.drawable.bg_quick_key);
            tabFolderSearch.setTextColor(textSecondaryColor());
            tabFolderSearch.setBackgroundResource(0);
        } else {
            tabFolderSearch.setTextColor(accentColor());
            tabFolderSearch.setBackgroundResource(R.drawable.bg_quick_key);
            tabBookmarks.setTextColor(textSecondaryColor());
            tabBookmarks.setBackgroundResource(0);
            etFolderSearch.requestFocus();
        }
    }

    private void toggleBookmarkPanel() {
        if (panelBookmark.getVisibility() == View.VISIBLE) {
            hideBookmarkPanel();
        } else {
            hideFileListPanel();
            hideOutlinePanel();
            if (isSearchOpen) {
                closeSearchCard();
            }
            loadBookmarks();
            refreshBookmarkList();
            tvPanelBookmarkTitle.setText("书签 · " + shortDocName());
            switchBookmarkTab(true);
            showPanel(panelBookmark, true);
        }
    }

    private void hideBookmarkPanel() {
        if (panelBookmark == null || panelBookmark.getVisibility() != View.VISIBLE) return;
        showPanel(panelBookmark, false);
    }

    private void showPanel(final View panel, final boolean show) {
        maskOverlay.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            panel.setVisibility(View.VISIBLE);
            Animation slideIn = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 1.0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f);
            slideIn.setDuration(240);
            slideIn.setInterpolator(new DecelerateInterpolator());
            panel.startAnimation(slideIn);
        } else {
            Animation slideOut = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 1.0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f);
            slideOut.setDuration(200);
            slideOut.setAnimationListener(new Animation.AnimationListener() {
                @Override
                public void onAnimationStart(Animation animation) {}
                @Override
                public void onAnimationEnd(Animation animation) {
                    panel.setVisibility(View.GONE);
                    maskOverlay.setVisibility(View.GONE);
                }
                @Override
                public void onAnimationRepeat(Animation animation) {}
            });
            panel.startAnimation(slideOut);
        }
    }

    private String shortDocName() {
        String name = currentFileName == null ? "当前文档" : currentFileName;
        if (name.length() > 12) {
            name = name.substring(0, 11) + "…";
        }
        return name;
    }

    private void updateBookmarkEmptyState() {
        boolean empty = bookmarks.isEmpty();
        tvEmptyBookmarks.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (viewBookmarkDot != null) {
            viewBookmarkDot.setVisibility(bookmarksOfDoc(currentDocKey).isEmpty() ? View.GONE : View.VISIBLE);
        }
    }

    /** 在当前阅读位置添加书签（弹窗可改名） */
    private void addBookmarkAtCurrentPosition() {
        if (isEditMode) {
            Toast.makeText(this, "编辑模式下暂不支持添加书签", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isWebViewReady || TextUtils.isEmpty(currentDocKey)) {
            Toast.makeText(this, "文档尚未就绪，稍后再试", Toast.LENGTH_SHORT).show();
            return;
        }
        webView.evaluateJavascript("captureBookmarkAnchor();", new android.webkit.ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                String raw = value == null ? "" : value;
                // JS 返回 JSON 字符串，带引号与转义
                if (raw.startsWith("\"")) {
                    try {
                        raw = new JSONArray("[" + raw + "]").getString(0);
                    } catch (Exception e) {
                        raw = raw.replace("\"", "");
                    }
                }
                int offset = 0;
                String snippet = raw;
                int bar = raw.indexOf('|');
                if (bar >= 0) {
                    try {
                        offset = Integer.parseInt(raw.substring(0, bar).trim());
                    } catch (NumberFormatException ignored) {
                    }
                    snippet = raw.substring(bar + 1).trim();
                }
                saveNewBookmark(buildAutoBookmarkTitle(snippet), snippet, offset);
            }
        });
    }

    /** 一键添加时自动生成书签名称：优先取所在段落文字，取不到则用时间 */
    private String buildAutoBookmarkTitle(String snippet) {
        if (snippet == null || snippet.trim().isEmpty()) {
            return "书签 · " + formatBookmarkTime(System.currentTimeMillis());
        }
        String title = snippet.trim();
        // 去掉 Markdown 残留符号，避免标题难看
        title = title.replaceAll("[#>*_`\\[\\]()!-]", " ").replaceAll("\\s+", " ").trim();
        if (title.isEmpty()) {
            return "书签 · " + formatBookmarkTime(System.currentTimeMillis());
        }
        return title.length() > 16 ? title.substring(0, 16) + "…" : title;
    }

    private void saveNewBookmark(String title, String snippet, int offsetPx) {
        if (TextUtils.isEmpty(currentDocKey)) return;
        Bookmark bm = new Bookmark(currentDocKey, title, snippet, offsetPx, System.currentTimeMillis());
        loadBookmarks();          // 先同步磁盘上的全量书签，避免覆盖其它文档
        allBookmarks.add(0, bm);
        persistBookmarks();
        refreshBookmarkList();
        Toast.makeText(this, "已加书签「" + title + "」", Toast.LENGTH_SHORT).show();
    }

    private void jumpToBookmark(final Bookmark bm) {
        if (bm == null) return;
        if (!bm.docKey.equals(currentDocKey)) {
            Toast.makeText(this, "这份书签属于其它文档", Toast.LENGTH_SHORT).show();
            return;
        }
        if (isEditMode) {
            toggleMode();
        }
        hideBookmarkPanel();
        hideControlsMenu(false);
        if (!isWebViewReady) return;
        String js = "scrollToBookmark(" + JSONObject.quote(bm.snippet == null ? "" : bm.snippet)
                + ", " + bm.offsetPx + ");";
        webView.evaluateJavascript(js, null);
    }

    private void showBookmarkOptions(final Bookmark bm) {
        final String[] items = new String[]{"跳到此处", "重命名", "删除"};
        showPaperMenu(bm.title, items, new PaperMenuCallback() {
            @Override
            public void onItem(int index) {
                if (index == 0) {
                    jumpToBookmark(bm);
                } else if (index == 1) {
                    renameBookmark(bm);
                } else {
                    confirmDeleteBookmark(bm);
                }
            }
        });
    }

    private void renameBookmark(final Bookmark bm) {
        showPaperInput("重命名书签", "给书签起个好记的名字", bm.title, new PaperInputCallback() {
            @Override
            public void onConfirm(String text) {
                if (text.isEmpty()) return;
                bm.title = text;
                persistBookmarks();
                refreshBookmarkList();
            }
        });
    }

    private void confirmDeleteBookmark(final Bookmark bm) {
        showPaperConfirm("删除书签", "确定删除「" + bm.title + "」吗？", "删除", new Runnable() {
            @Override
            public void run() {
                allBookmarks.remove(bm);
                persistBookmarks();
                refreshBookmarkList();
                Toast.makeText(MainActivity.this, "已删除书签", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void refreshBookmarkList() {
        bookmarks.clear();
        bookmarks.addAll(bookmarksOfDoc(currentDocKey));
        if (bookmarkAdapter != null) {
            bookmarkAdapter.notifyDataSetChanged();
        }
        updateBookmarkEmptyState();
    }

    private List<Bookmark> bookmarksOfDoc(String docKey) {
        List<Bookmark> result = new ArrayList<>();
        if (docKey == null) return result;
        for (Bookmark bm : allBookmarks) {
            if (docKey.equals(bm.docKey)) {
                result.add(bm);
            }
        }
        Collections.sort(result, new Comparator<Bookmark>() {
            @Override
            public int compare(Bookmark a, Bookmark b) {
                return Long.compare(a.createdAt, b.createdAt);
            }
        });
        return result;
    }

    /** 从 SharedPreferences 读取全部书签 */
    private void loadBookmarks() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String json = sp.getString(KEY_BOOKMARKS, "[]");
        List<Bookmark> loaded = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                Bookmark bm = Bookmark.fromJson(arr.optJSONObject(i));
                if (bm != null && !TextUtils.isEmpty(bm.docKey)) {
                    loaded.add(bm);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        allBookmarks.clear();
        allBookmarks.addAll(loaded);
        refreshBookmarkList();
    }

    /** 将全部书签写回 SharedPreferences */
    private void persistBookmarks() {
        JSONArray arr = new JSONArray();
        for (Bookmark bm : allBookmarks) {
            arr.put(bm.toJson());
        }
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_BOOKMARKS, arr.toString())
                .apply();
    }

    private String formatBookmarkTime(long time) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());
        return sdf.format(new java.util.Date(time));
    }

    // ==================== 文稿目录内的文档搜索 ====================

    /** 目录搜索命中项：文档 + 所在文件夹（相对当前目录，空串表示当前目录） */
    private static class FoundDoc {
        final FileItem item;
        final String folderPath;
        FoundDoc(FileItem item, String folderPath) {
            this.item = item;
            this.folderPath = folderPath;
        }
    }

    private void setupFileSearchUi() {
        if (btnFileSearch == null) return;

        btnFileSearch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isFileSearchMode) {
                    closeFileSearch();
                } else {
                    openFileSearch();
                }
            }
        });

        btnFileSearchClose.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                closeFileSearch();
            }
        });

        etFileSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override
            public void afterTextChanged(Editable s) {
                scheduleFileSearch(s.toString());
            }
        });

        etFileSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                performFileSearch(etFileSearch.getText().toString());
                return true;
            }
        });

        View.OnClickListener redo = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                performFileSearch(etFileSearch.getText().toString());
            }
        };
        cbFileRecursive.setOnClickListener(redo);
        cbFileContent.setOnClickListener(redo);
    }

    private void openFileSearch() {
        isFileSearchMode = true;
        layoutFileSearch.setVisibility(View.VISIBLE);
        layoutFileBreadcrumb.setVisibility(View.GONE);
        etFileSearch.requestFocus();
        etFileSearch.postDelayed(new Runnable() {
            @Override
            public void run() {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etFileSearch, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 120);
    }

    /** 退出搜索：清空条件、恢复当前目录列表 */
    private void closeFileSearch() {
        if (!isFileSearchMode) return;
        isFileSearchMode = false;
        if (pendingFileSearch != null) {
            searchHandler.removeCallbacks(pendingFileSearch);
            pendingFileSearch = null;
        }
        layoutFileSearch.setVisibility(View.GONE);
        layoutFileBreadcrumb.setVisibility(View.VISIBLE);
        etFileSearch.setText("");
        etFileSearch.clearFocus();
        tvFileSearchHint.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(etFileSearch.getWindowToken(), 0);

        fileSearchPaths.clear();
        fileItemList.clear();
        fileItemList.addAll(dirItemList);
        if (fileAdapter != null) fileAdapter.notifyDataSetChanged();
        tvEmptyFiles.setText("🍃 该目录下暂无 Markdown 文档");
        tvEmptyFiles.setVisibility(fileItemList.isEmpty() ? View.VISIBLE : View.GONE);
        updateGoUpButton();
    }

    private void scheduleFileSearch(final String keyword) {
        if (pendingFileSearch != null) {
            searchHandler.removeCallbacks(pendingFileSearch);
        }
        pendingFileSearch = new Runnable() {
            @Override
            public void run() {
                performFileSearch(keyword);
            }
        };
        searchHandler.postDelayed(pendingFileSearch, 200);
    }

    private void performFileSearch(final String keyword) {
        final String kw = keyword == null ? "" : keyword.trim();
        if (kw.isEmpty()) {
            // 清空输入即回到目录列表
            fileSearchPaths.clear();
            fileItemList.clear();
            fileItemList.addAll(dirItemList);
            if (fileAdapter != null) fileAdapter.notifyDataSetChanged();
            tvFileSearchHint.setText("");
            tvEmptyFiles.setText("🍃 该目录下暂无 Markdown 文档");
            tvEmptyFiles.setVisibility(fileItemList.isEmpty() ? View.VISIBLE : View.GONE);
            return;
        }

        final boolean recursive = cbFileRecursive.isChecked();
        final boolean searchContent = cbFileContent.isChecked();
        tvFileSearchHint.setText("查找中…");

        searchExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final List<FoundDoc> found = scanFilesForKeyword(kw, recursive, searchContent);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isFileSearchMode) return; // 期间已关闭搜索
                        fileSearchPaths.clear();
                        fileItemList.clear();
                        for (FoundDoc fd : found) {
                            fileItemList.add(fd.item);
                            fileSearchPaths.add(fd.folderPath);
                        }
                        if (fileAdapter != null) fileAdapter.notifyDataSetChanged();
                        tvFileSearchHint.setText("共 " + found.size() + " 篇");
                        tvEmptyFiles.setText("没有找到匹配「" + kw + "」的文档");
                        tvEmptyFiles.setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
                    }
                });
            }
        });
    }

    /** 在当前目录（可选递归子目录）内搜索文件名，可选搜正文；实时枚举不依赖缓存 */
    private List<FoundDoc> scanFilesForKeyword(String keyword, boolean recursive, boolean searchContent) {
        List<FoundDoc> all = new ArrayList<>();
        // 实时枚举，保证拿到最新磁盘状态
        if (currentTreeUri != null && safRootTreeUri != null) {
            String rootDocId = (safCurrentDocId != null) ? safCurrentDocId
                    : DocumentsContract.getTreeDocumentId(safRootTreeUri);
            collectSafFromDocId(rootDocId, "", all, recursive, 0, 3000);
        } else if (currentDirectory != null) {
            collectLocalDocs(currentDirectory, "", all, recursive, 0, 3000);
        }
        // 兜底：实时枚举为空时退回目录缓存列表
        if (all.isEmpty()) {
            collectFoundDocs(all, recursive, 3000);
        }

        String lk = keyword.toLowerCase().trim();
        List<FoundDoc> hits = new ArrayList<>();
        for (FoundDoc fd : all) {
            if (hits.size() >= MAX_SEARCH_RESULTS) break;
            if (fd.item.name != null && fd.item.name.toLowerCase().contains(lk)) {
                hits.add(fd);
                continue;
            }
            if (searchContent) {
                String content = readDocContent(fd.item);
                if (content != null && content.toLowerCase().contains(lk)) {
                    hits.add(fd);
                }
            }
        }
        return hits;
    }

    /** 从 SAF 某个目录 docId 开始枚举文档（含子目录时可递归） */
    private void collectSafFromDocId(String dirDocId, String prefix, List<FoundDoc> out,
                                     boolean recursive, int depth, int limit) {
        if (depth > 6 || out.size() >= limit || safRootTreeUri == null || dirDocId == null) return;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(safRootTreeUri, dirDocId);
        try {
            Cursor cursor = getContentResolver().query(childrenUri, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
            }, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (out.size() >= limit) break;
                    String childDocId = cursor.getString(0);
                    String name = cursor.getString(1);
                    String mime = cursor.getString(2);
                    long size = cursor.getLong(3);
                    long lastModified = cursor.getLong(4);
                    if (name == null) continue;
                    Uri childUri = DocumentsContract.buildDocumentUriUsingTree(safRootTreeUri, childDocId);
                    boolean isDir = mime != null && mime.equals(DocumentsContract.Document.MIME_TYPE_DIR);
                    if (isDir) {
                        if (recursive && !shouldSkipDir(name)) {
                            collectSafFromDocId(childDocId, prefix + "/" + name, out, recursive, depth + 1, limit);
                        }
                    } else if (isMarkdownName(name)) {
                        out.add(new FoundDoc(new FileItem(name, childUri, childDocId, size, lastModified, false), prefix));
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 从当前目录真实列表出发，收集文档（含子目录时可递归） */
    private void collectFoundDocs(List<FoundDoc> out, boolean recursive, int limit) {
        for (FileItem it : dirItemList) {
            if (out.size() >= limit) return;
            if (!it.isDirectory) {
                out.add(new FoundDoc(it, ""));
            } else if (recursive) {
                if (it.uri != null) {
                    collectSafDocs(it, it.name, out, recursive, 1, limit);
                } else if (it.path != null) {
                    collectLocalDocs(new File(it.path), it.name, out, recursive, 1, limit);
                }
            }
        }
    }

    /** 搜索时跳过这些无关大目录，避免拖慢/占满结果 */
    private boolean shouldSkipDir(String name) {
        if (name == null) return false;
        String n = name.toLowerCase();
        return n.equals("node_modules") || n.equals(".git") || n.equals(".cache")
                || n.equals(".gradle") || n.equals(".idea") || n.equals("build")
                || n.equals("gen") || n.equals("bin") || n.equals(".svn") || n.equals("dist");
    }

    private void collectLocalDocs(File dir, String prefix, List<FoundDoc> out, boolean recursive, int depth, int limit) {
        if (depth > 6 || out.size() >= limit) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (out.size() >= limit) return;
            if (f.isDirectory()) {
                if (recursive && !shouldSkipDir(f.getName())) {
                    collectLocalDocs(f, prefix + "/" + f.getName(), out, recursive, depth + 1, limit);
                }
            } else if (isMarkdownName(f.getName())) {
                out.add(new FoundDoc(new FileItem(f), prefix));
            }
        }
    }

    private void collectSafDocs(FileItem dirItem, String prefix, List<FoundDoc> out, boolean recursive, int depth, int limit) {
        if (depth > 6 || out.size() >= limit || safRootTreeUri == null || dirItem.docId == null) return;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(safRootTreeUri, dirItem.docId);
        try {
            Cursor cursor = getContentResolver().query(childrenUri, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
            }, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    if (out.size() >= limit) break;
                    String childDocId = cursor.getString(0);
                    String name = cursor.getString(1);
                    String mime = cursor.getString(2);
                    long size = cursor.getLong(3);
                    long lastModified = cursor.getLong(4);
                    if (name == null) continue;
                    Uri childUri = DocumentsContract.buildDocumentUriUsingTree(safRootTreeUri, childDocId);
                    boolean isDir = mime != null && mime.equals(DocumentsContract.Document.MIME_TYPE_DIR);
                    if (isDir) {
                        if (recursive) {
                            FileItem sub = new FileItem(name, childUri, childDocId, 0, lastModified, true);
                            collectSafDocs(sub, prefix + "/" + name, out, recursive, depth + 1, limit);
                        }
                    } else if (isMarkdownName(name)) {
                        out.add(new FoundDoc(new FileItem(name, childUri, childDocId, size, lastModified, false), prefix));
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ==================== 文件夹内全文查找 ====================

    /** 跨文档搜索结果模型 */
    private static class SearchResult {
        final String fileName;
        final String docKey;
        final String snippet;
        final int hitCount;
        final FileItem itemRef;

        SearchResult(String fileName, String docKey, String snippet, int hitCount, FileItem itemRef) {
            this.fileName = fileName;
            this.docKey = docKey;
            this.snippet = snippet;
            this.hitCount = hitCount;
            this.itemRef = itemRef;
        }
    }

    private void startFolderSearch() {
        final String keyword = etFolderSearch.getText().toString().trim();
        if (keyword.isEmpty()) {
            tvFolderSearchHint.setText("请输入关键词");
            return;
        }
        searchResults.clear();
        searchResultAdapter.notifyDataSetChanged();
        tvEmptyFolderResults.setText("正在翻阅…");
        tvEmptyFolderResults.setVisibility(View.VISIBLE);
        tvFolderSearchHint.setText("");

        final boolean recursive = cbFolderRecursive.isChecked();
        searchExecutor.execute(new Runnable() {
            @Override
            public void run() {
                final List<SearchResult> found = scanFolderForKeyword(keyword, recursive);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        searchResults.clear();
                        searchResults.addAll(found);
                        searchResultAdapter.notifyDataSetChanged();
                        tvEmptyFolderResults.setText(found.isEmpty() ? "没有找到包含「" + keyword + "」的文档" : "");
                        tvEmptyFolderResults.setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
                        tvFolderSearchHint.setText("共 " + found.size() + " 篇");
                    }
                });
            }
        });
    }

    /** 在工作线程中扫描当前目录（可选递归），返回命中文件列表 */
    private List<SearchResult> scanFolderForKeyword(String keyword, boolean recursive) {
        List<SearchResult> found = new ArrayList<>();
        String lowerKey = keyword.toLowerCase();
        List<FileItem> candidates = new ArrayList<>();
        collectDocItems(candidates, recursive, 0);

        for (FileItem item : candidates) {
            if (found.size() >= MAX_SEARCH_RESULTS) break;
            String content = readDocContent(item);
            if (content == null) continue;
            int hits = 0;
            String lowerContent = content.toLowerCase();
            int idx = lowerContent.indexOf(lowerKey);
            if (idx < 0) continue;
            int firstIdx = idx;
            while (idx >= 0 && hits < 999) {
                hits++;
                idx = lowerContent.indexOf(lowerKey, idx + lowerKey.length());
            }
            int from = Math.max(0, firstIdx - 24);
            int to = Math.min(content.length(), firstIdx + keyword.length() + 46);
            String snippet = content.substring(from, to).replaceAll("[#>*`\\-\\[\\]]", "").replaceAll("\\s+", " ").trim();
            String docKey = (item.uri != null) ? item.uri.toString() : (item.path != null ? item.path : item.name);
            found.add(new SearchResult(item.name, docKey, snippet, hits, item));
        }
        return found;
    }

    private void collectDocItems(List<FileItem> out, boolean recursive, int depth) {
        if (depth > 6) return;
        for (FileItem item : new ArrayList<>(dirItemList)) {
            if (!item.isDirectory) {
                out.add(item);
            } else if (recursive) {
                if (item.uri != null) {
                    out.addAll(scanTreeDir(item, depth + 1));
                } else if (item.path != null) {
                    File dir = new File(item.path);
                    File[] files = dir.listFiles();
                    if (files == null) continue;
                    for (File f : files) {
                        if (f.isDirectory()) {
                            if (depth + 1 <= 6) {
                                List<FileItem> sub = new ArrayList<>();
                                collectLocalDirDocs(f, sub, depth + 2);
                                out.addAll(sub);
                            }
                        } else if (isMarkdownName(f.getName())) {
                            out.add(new FileItem(f));
                        }
                    }
                }
            }
        }
    }

    private void collectLocalDirDocs(File dir, List<FileItem> out, int depth) {
        if (depth > 6) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                collectLocalDirDocs(f, out, depth + 1);
            } else if (isMarkdownName(f.getName())) {
                out.add(new FileItem(f));
            }
        }
    }

    private List<FileItem> scanTreeDir(FileItem dirItem, int depth) {
        List<FileItem> out = new ArrayList<>();
        if (depth > 6 || safRootTreeUri == null || dirItem.docId == null) return out;
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(safRootTreeUri, dirItem.docId);
        try {
            Cursor cursor = getContentResolver().query(childrenUri, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
            }, null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String childDocId = cursor.getString(0);
                    String name = cursor.getString(1);
                    String mime = cursor.getString(2);
                    if (name == null) continue;
                    Uri childUri = DocumentsContract.buildDocumentUriUsingTree(safRootTreeUri, childDocId);
                    boolean isDir = mime != null && mime.equals(DocumentsContract.Document.MIME_TYPE_DIR);
                    if (isDir) {
                        FileItem subDir = new FileItem(name, childUri, childDocId, 0, 0, true);
                        out.addAll(scanTreeDir(subDir, depth + 1));
                    } else if (isMarkdownName(name)) {
                        out.add(new FileItem(name, childUri, childDocId, 0, 0, false));
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return out;
    }

    private boolean isMarkdownName(String name) {
        String lower = name.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt");
    }

    private String readDocContent(FileItem item) {
        StringBuilder sb = new StringBuilder();
        try {
            InputStream is;
            if (item.uri != null) {
                is = getContentResolver().openInputStream(item.uri);
            } else if (item.path != null) {
                is = new FileInputStream(new File(item.path));
            } else {
                return null;
            }
            if (is == null) return null;
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            String line;
            int lines = 0;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
                if (++lines > 4000) break; // 防止超大文件拖慢
            }
            reader.close();
            is.close();
        } catch (Exception e) {
            return null;
        }
        return sb.toString();
    }

    private void openSearchResult(final SearchResult result) {
        if (result == null) return;
        hideBookmarkPanel();
        if (result.itemRef != null) {
            stopTts();
            loadFile(result.itemRef);
        }
        // 文档加载并渲染后，再在本文中定位关键词
        final String keyword = etFolderSearch.getText().toString().trim();
        webView.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (TextUtils.isEmpty(keyword)) return;
                openSearchCard();
                etSearch.setText(keyword);
                etSearch.setSelection(etSearch.getText().length());
            }
        }, 320);
    }

    // ==================== 更多菜单 ====================

    // ==================== 纸张风格通用弹窗 ====================

    private interface PaperMenuCallback { void onItem(int index); }
    private interface PaperInputCallback { void onConfirm(String text); }

    /** 纸张风格选项菜单 */
    private void showPaperMenu(String title, String[] items, final PaperMenuCallback cb) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_paper_menu, null);
        TextView tvTitle = view.findViewById(R.id.dlg_title);
        tvTitle.setText(title);
        tvTitle.setTextColor(accentColor());
        if (paperTypeface != null) tvTitle.setTypeface(paperTypeface, Typeface.BOLD);
        LinearLayout container = view.findViewById(R.id.dlg_items);

        final AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView row = new TextView(this);
            row.setText(items[i]);
            row.setTextSize(15);
            row.setTextColor(textPrimaryColor());
            row.setPadding((int) (16 * d), (int) (13 * d), (int) (16 * d), (int) (13 * d));
            row.setBackgroundResource(R.drawable.bg_dialog_item);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, (int) (7 * d));
            row.setLayoutParams(lp);
            if (paperTypeface != null) row.setTypeface(paperTypeface);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                    cb.onItem(idx);
                }
            });
            container.addView(row);
        }
        dialog.show();
    }

    /** 纸张风格输入弹窗 */
    private void showPaperInput(String title, String hint, String defaultText, final PaperInputCallback cb) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_paper_input, null);
        TextView tvTitle = view.findViewById(R.id.dlg_input_title);
        tvTitle.setText(title);
        tvTitle.setTextColor(accentColor());
        if (paperTypeface != null) tvTitle.setTypeface(paperTypeface, Typeface.BOLD);

        final EditText edit = view.findViewById(R.id.dlg_input_edit);
        edit.setHint(hint);
        edit.setTextColor(textPrimaryColor());
        edit.setHintTextColor(textSecondaryColor());
        if (paperTypeface != null) edit.setTypeface(paperTypeface);
        if (defaultText != null) {
            edit.setText(defaultText);
            edit.setSelection(edit.getText().length());
        }

        final AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        view.findViewById(R.id.dlg_input_ok).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String t = edit.getText().toString().trim();
                dialog.dismiss();
                cb.onConfirm(t);
            }
        });
        view.findViewById(R.id.dlg_input_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });
        dialog.show();
        edit.requestFocus();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
    }

    /** 纸张风格确认弹窗 */
    private void showPaperConfirm(String title, final String message, String confirmText, final Runnable onConfirm) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_dialog_paper);
        root.setPadding((int) (18 * d), (int) (16 * d), (int) (18 * d), (int) (14 * d));

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextColor(accentColor());
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(paperTypeface != null ? paperTypeface : Typeface.DEFAULT_BOLD, Typeface.BOLD);
        root.addView(tvTitle);

        TextView tvMsg = new TextView(this);
        tvMsg.setText(message);
        tvMsg.setTextColor(textPrimaryColor());
        tvMsg.setTextSize(14);
        LinearLayout.LayoutParams mLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        mLp.topMargin = (int) (12 * d);
        mLp.bottomMargin = (int) (16 * d);
        tvMsg.setLayoutParams(mLp);
        if (paperTypeface != null) tvMsg.setTypeface(paperTypeface);
        root.addView(tvMsg);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.END);

        final AlertDialog dialog = new AlertDialog.Builder(this).setView(root).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView cancel = new TextView(this);
        cancel.setText("取消");
        cancel.setTextColor(textSecondaryColor());
        cancel.setTextSize(14);
        cancel.setPadding((int) (16 * d), (int) (8 * d), (int) (16 * d), (int) (8 * d));
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { dialog.dismiss(); }
        });
        row.addView(cancel);

        TextView ok = new TextView(this);
        ok.setText(confirmText);
        ok.setTextColor(accentColor());
        ok.setTextSize(14);
        ok.setTypeface(paperTypeface != null ? paperTypeface : Typeface.DEFAULT_BOLD, Typeface.BOLD);
        ok.setPadding((int) (20 * d), (int) (8 * d), (int) (20 * d), (int) (8 * d));
        ok.setBackgroundResource(R.drawable.bg_quick_key);
        LinearLayout.LayoutParams okLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        okLp.leftMargin = (int) (8 * d);
        ok.setLayoutParams(okLp);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (onConfirm != null) onConfirm.run();
            }
        });
        row.addView(ok);

        root.addView(row);
        dialog.show();
    }

    /** 轮盘按钮自定义弹窗：勾选显示，取消勾选收纳，实时生效并持久化 */
    private void showWheelSettingsDialog() {
        final float density = getResources().getDisplayMetrics().density;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_dialog_paper);
        root.setPadding((int) (18 * density), (int) (16 * density), (int) (18 * density), (int) (14 * density));

        TextView tvTitle = new TextView(this);
        tvTitle.setText("轮盘按钮");
        tvTitle.setTextColor(accentColor());
        tvTitle.setTextSize(16);
        tvTitle.setTypeface(paperTypeface != null ? paperTypeface : Typeface.DEFAULT_BOLD, Typeface.BOLD);
        root.addView(tvTitle);

        TextView tvTip = new TextView(this);
        tvTip.setText("勾选的显示在轮盘上，取消勾选即收纳");
        tvTip.setTextColor(textSecondaryColor());
        tvTip.setTextSize(12);
        LinearLayout.LayoutParams tipLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tipLp.topMargin = (int) (10 * density);
        tipLp.bottomMargin = (int) (14 * density);
        tvTip.setLayoutParams(tipLp);
        root.addView(tvTip);

        int[] icons = {
                R.drawable.ic_folder, R.drawable.ic_search, R.drawable.ic_toc,
                R.drawable.ic_bookmark, R.drawable.ic_speaker, R.drawable.ic_more
        };

        final List<CheckBox> boxes = new ArrayList<>();
        for (int i = 0; i < WHEEL_KEYS.length; i++) {
            final String key = WHEEL_KEYS[i];
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(0, 0, 0, 0);

            ImageView iv = new ImageView(this);
            iv.setLayoutParams(new LinearLayout.LayoutParams(
                    (int) (24 * density), (int) (24 * density)));
            iv.setPadding((int) (2 * density), 0, (int) (8 * density), 0);
            iv.setImageResource(icons[i]);
            iv.setColorFilter(accentColor());
            row.addView(iv);

            TextView label = new TextView(this);
            label.setText(WHEEL_LABELS[i]);
            label.setTextColor(textPrimaryColor());
            label.setTextSize(15);
            if (paperTypeface != null) label.setTypeface(paperTypeface);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            row.addView(label);

            CheckBox cb = new CheckBox(this);
            cb.setChecked(isWheelVisible(key));
            cb.setTextColor(textPrimaryColor());
            cb.setTextSize(14);
            cb.setButtonTintList(android.content.res.ColorStateList.valueOf(accentColor()));
            cb.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    boolean checked = cb.isChecked();
                    if (checked) wheelVisibleKeys.add(key);
                    else wheelVisibleKeys.remove(key);
                    persistWheelVisible();
                    applyWheelLayout();
                }
            });
            row.addView(cb);

            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.bottomMargin = (int) (6 * density);
            row.setLayoutParams(rowLp);
            root.addView(row);
            boxes.add(cb);
        }

        // 快捷操作
        LinearLayout quick = new LinearLayout(this);
        quick.setOrientation(LinearLayout.HORIZONTAL);
        quick.setGravity(android.view.Gravity.END);
        LinearLayout.LayoutParams quickLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        quickLp.topMargin = (int) (10 * density);
        quick.setLayoutParams(quickLp);

        quick.addView(makeQuickBtn("全部显示", new View.OnClickListener() {
            @Override public void onClick(View v) {
                wheelVisibleKeys.clear();
                for (String k : WHEEL_KEYS) wheelVisibleKeys.add(k);
                persistWheelVisible();
                applyWheelLayout();
                syncWheelBoxes(boxes);
            }
        }, density));
        quick.addView(makeQuickBtn("全部收纳", new View.OnClickListener() {
            @Override public void onClick(View v) {
                wheelVisibleKeys.clear();
                persistWheelVisible();
                applyWheelLayout();
                syncWheelBoxes(boxes);
            }
        }, density));
        quick.addView(makeQuickBtn("恢复默认", new View.OnClickListener() {
            @Override public void onClick(View v) {
                wheelVisibleKeys.clear();
                for (String k : WHEEL_KEYS) wheelVisibleKeys.add(k);
                persistWheelVisible();
                applyWheelLayout();
                syncWheelBoxes(boxes);
            }
        }, density));
        root.addView(quick);

        final AlertDialog dialog = new AlertDialog.Builder(this).setView(root).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private View makeQuickBtn(String text, final View.OnClickListener listener, float density) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextColor(textSecondaryColor());
        btn.setTextSize(13);
        if (paperTypeface != null) btn.setTypeface(paperTypeface);
        btn.setPadding((int) (14 * density), (int) (6 * density), (int) (14 * density), (int) (6 * density));
        btn.setBackgroundResource(R.drawable.bg_quick_key);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = (int) (8 * density);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        return btn;
    }

    private void syncWheelBoxes(List<CheckBox> boxes) {
        for (int i = 0; i < boxes.size() && i < WHEEL_KEYS.length; i++) {
            boxes.get(i).setChecked(isWheelVisible(WHEEL_KEYS[i]));
        }
    }

    private void showMoreMenu() {
        final String[] items = new String[]{
                isDarkMode ? "☀  切换为日间宣纸" : "🌙  切换为夜间墨色",
                "💾  保存当前文档",
                isEditMode ? "📖  完成阅读" : "✏  进入编辑模式",
                "⚙  轮盘按钮设置"
        };
        showPaperMenu("更多", items, new PaperMenuCallback() {
            @Override
            public void onItem(int index) {
                if (index == 0) {
                    hideControlsMenu(false);
                    toggleTheme();
                } else if (index == 1) {
                    hideControlsMenu(false);
                    saveCurrentFile(true);
                } else if (index == 3) {
                    showWheelSettingsDialog();
                } else {
                    hideControlsMenu(false);
                    toggleMode();
                }
            }
        });
    }

    private int accentColor() {
        return isDarkMode ? Color.parseColor("#D48B68") : Color.parseColor("#8C5338");
    }

    private int textPrimaryColor() {
        return isDarkMode ? Color.parseColor("#D6D0C7") : Color.parseColor("#2D2A28");
    }

    private int textSecondaryColor() {
        return isDarkMode ? Color.parseColor("#8C857C") : Color.parseColor("#7A746E");
    }

    private void loadLastLocationOrDefaults() {
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String lastDir = sp.getString(KEY_CURRENT_DIR, null);
        String lastTreeUri = sp.getString(KEY_SAF_TREE_URI, null);

        if (lastTreeUri != null) {
            try {
                currentTreeUri = Uri.parse(lastTreeUri);
                loadFromTreeUri(currentTreeUri);
                return;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (lastDir != null) {
            File dir = new File(lastDir);
            if (dir.exists() && dir.isDirectory()) {
                currentDirectory = dir;
                loadFromLocalDir(dir);
                return;
            }
        }

        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (downloadDir != null && downloadDir.exists()) {
            currentDirectory = downloadDir;
            loadFromLocalDir(downloadDir);
        } else {
            File defaultDir = new File("/storage/emulated/0/Download");
            if (defaultDir.exists()) {
                currentDirectory = defaultDir;
                loadFromLocalDir(defaultDir);
            } else {
                showWelcomeDoc();
            }
        }
    }

    private void showWelcomeDoc() {
        switchReadingDocument("welcome_doc");
        currentFileName = "欢迎使用素笺.md";
        updateTitleViews(currentFileName, "轻巧优雅的纸质阅读体验");

        currentMarkdownText = "# 素笺 · Paper Markdown\n\n"
                + "> 「素笺淡墨，纸短情长。」\n\n"
                + "欢迎使用 **素笺 Markdown** 阅读器！这是一款专为阅读与专注书写打造的素雅纸质风格应用。\n\n"
                + "## ✨ 核心特色\n\n"
                + "- **纸质书感**：采用柔和暖色纸面底色与古典排版，呵护双眼；\n"
                + "- **右侧圆形轮盘**：轻触屏幕中间唤出右侧圆形悬浮轮盘，单手即可轻松操作各项功能；\n"
                + "- **右侧章节大纲**：轻点「大纲」从右侧滑出层级大纲抽屉，平滑跳转各级标题；\n"
                + "- **日夜模式**：一键切换温润宣纸与夜间墨色，暗光更舒适；\n"
                + "- **语音朗读**：轻点「朗读」享受贴心语音朗读，舒缓双眼；\n"
                + "- **子目录浏览**：支持进入与返回任意层级子目录浏览 Markdown；\n"
                + "- **目录搜索**：文稿目录标题栏点放大镜，按文件名跨文件夹找文档，直接显示所在文件夹；\n"
                + "- **全文搜索**：轻点「搜索」下拉即用的紧凑搜索条，实时高亮并逐个跳转命中处；\n"
                + "- **书签夹页**：轻点「书签」夹住当前位置，随时翻回，还能在文件夹里跨文档查找；\n"
                + "- **即刻编辑**：随时在阅读与编辑模式间无缝切换，附带便捷 Markdown 输入栏。\n\n"
                + "### 快捷操作指南\n\n"
                + "1. **轻触屏幕中间**：唤起或收起右侧悬浮圆形功能轮盘；\n"
                + "2. 点击 **大纲**：右侧滑出章节目录抽屉，直达重点；\n"
                + "3. 点击 **目录**：左侧滑出文稿与子目录浏览器；\n"
                + "4. 点击 **搜索**：下拉搜索条，输入即找，上下箭头逐处跳转；\n"
                + "5. 点击顶栏 **书签按钮**：一键把当前页加入书签，最省事；\n"
                + "6. 点击 **书签**：查看全部书签，或在文件夹里跨文档查找；\n"
                + "7. 点击 **朗读**：启停智能有声朗读；\n"
                + "8. 点击 **更多**：切换夜间/日间、保存文档、进入编辑模式。\n\n"
                + "---\n\n"
                + "祝您阅读与书写愉快！";

        if (isWebViewReady) {
            renderInWebView(currentMarkdownText);
        }
        updateOutlineList();
    }

    private FileItem findDocToOpen(List<FileItem> list) {
        if (list == null || list.isEmpty()) return null;
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String lastFile = sp.getString(KEY_CURRENT_FILE, null);

        if (lastFile != null && !lastFile.trim().isEmpty()) {
            for (FileItem item : list) {
                if (item.isDirectory) continue;
                String key = (item.uri != null) ? item.uri.toString() : (item.path != null ? item.path : item.name);
                if (lastFile.equals(key) || lastFile.equals(item.name) || (item.path != null && lastFile.equals(item.path))) {
                    return item;
                }
            }
        }
        return findFirstDocItem(list);
    }

    private FileItem findFirstDocItem(List<FileItem> list) {
        if (list == null) return null;
        for (FileItem item : list) {
            if (!item.isDirectory) {
                return item;
            }
        }
        return null;
    }

    private void updateTitleViews(String title, String subtitle) {
        tvTitle.setText(title);
        tvSubtitle.setText(subtitle);
        tvEditTitle.setText(title);
    }

    private void loadFromLocalDir(File dir) {
        currentDirectory = dir;
        fileItemList.clear();

        File[] files = dir.listFiles();
        if (files != null) {
            // 先收集子目录与 Markdown 文件（混排）
            List<FileItem> dirs = new ArrayList<>();
            List<FileItem> docs = new ArrayList<>();
            for (int i = 0; i < files.length; i++) {
                File f = files[i];
                if (f.isDirectory()) {
                    dirs.add(new FileItem(f, true));
                } else if (f.isFile() && (f.getName().toLowerCase().endsWith(".md")
                        || f.getName().toLowerCase().endsWith(".markdown")
                        || f.getName().toLowerCase().endsWith(".txt"))) {
                    docs.add(new FileItem(f));
                }
            }
            Collections.sort(dirs, new Comparator<FileItem>() {
                @Override
                public int compare(FileItem a, FileItem b) {
                    return a.name.compareToIgnoreCase(b.name);
                }
            });
            Collections.sort(docs, new Comparator<FileItem>() {
                @Override
                public int compare(FileItem a, FileItem b) {
                    return Long.compare(b.lastModified, a.lastModified);
                }
            });
            fileItemList.addAll(dirs);
            fileItemList.addAll(docs);
        }
        syncDirItemList();

        String label = dir.getName() + "  (含 " + fileItemList.size() + " 项)";
        tvCurrentDir.setText(label);

        fileAdapter.notifyDataSetChanged();
        tvEmptyFiles.setVisibility(fileItemList.isEmpty() ? View.VISIBLE : View.GONE);
        updateGoUpButton();

        // 仅在确实是首次初始化（非目录导航）时自动恢复上次打开文档或打开第一篇文档
        if (!navFromDirectoryList) {
            FileItem targetDoc = findDocToOpen(fileItemList);
            if (targetDoc != null) {
                loadFile(targetDoc);
            } else {
                showWelcomeDoc();
            }
        }
        navFromDirectoryList = false;

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_CURRENT_DIR, dir.getAbsolutePath())
                .remove(KEY_SAF_TREE_URI)
                .apply();
    }

    /** 把当前 fileItemList 复制为真实目录列表（供退出搜索时恢复） */
    private void syncDirItemList() {
        dirItemList.clear();
        dirItemList.addAll(fileItemList);
        fileSearchPaths.clear();
    }

    private void updateGoUpButton() {
        // 本地模式：stack 非空则显示返回；SAF 模式同理
        boolean canGoUp;
        if (currentTreeUri != null) {
            canGoUp = !safPathStack.isEmpty();
        } else {
            canGoUp = !localNavStack.isEmpty();
        }
        if (btnGoUp != null) {
            btnGoUp.setVisibility(canGoUp ? View.VISIBLE : View.GONE);
        }
        if (tvGoUpLabel != null) {
            tvGoUpLabel.setText(canGoUp ? "↑ 返回上级" : "");
        }
    }

    private void loadFromTreeUri(Uri treeUri) {
        loadFromTreeDoc(treeUri, null);
    }

    /**
     * SAF 模式下按“根树 + 当前目录 docId”加载。
     * @param treeRoot 根树 URI（外部目录选择器返回）
     * @param dirDocId 当前浏览子目录的 documentId；若为 null 表示根目录
     */
    private void loadFromTreeDoc(Uri treeRoot, String dirDocId) {
        if (treeRoot == null) return;
        currentTreeUri = treeRoot;
        safRootTreeUri = treeRoot;
        safCurrentDocId = dirDocId;

        String docId = (dirDocId != null) ? dirDocId : DocumentsContract.getTreeDocumentId(treeRoot);
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeRoot, docId);

        tvCurrentDir.setText("已授权文件夹");
        fileItemList.clear();

        List<FileItem> dirs = new ArrayList<>();
        List<FileItem> docs = new ArrayList<>();
        try {
            Cursor cursor = getContentResolver().query(childrenUri, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
            }, null, null, null);

            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String childDocId = cursor.getString(0);
                    String name = cursor.getString(1);
                    String mime = cursor.getString(2);
                    long size = cursor.getLong(3);
                    long lastModified = cursor.getLong(4);

                    if (name != null) {
                        Uri childUri = DocumentsContract.buildDocumentUriUsingTree(treeRoot, childDocId);
                        // 区分目录与文件
                        boolean isDir = (mime != null && mime.equals(DocumentsContract.Document.MIME_TYPE_DIR))
                                || size == -1;
                        if (isDir) {
                            dirs.add(new FileItem(name, childUri, childDocId, 0, lastModified, true));
                        } else {
                            String lower = name.toLowerCase();
                            if (lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".txt")) {
                                docs.add(new FileItem(name, childUri, childDocId, size, lastModified, false));
                            }
                        }
                    }
                }
                cursor.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        Collections.sort(dirs, new Comparator<FileItem>() {
            @Override
            public int compare(FileItem a, FileItem b) {
                return a.name.compareToIgnoreCase(b.name);
            }
        });
        Collections.sort(docs, new Comparator<FileItem>() {
            @Override
            public int compare(FileItem a, FileItem b) {
                return Long.compare(b.lastModified, a.lastModified);
            }
        });
        fileItemList.addAll(dirs);
        fileItemList.addAll(docs);
        syncDirItemList();

        fileAdapter.notifyDataSetChanged();
        tvEmptyFiles.setVisibility(fileItemList.isEmpty() ? View.VISIBLE : View.GONE);
        updateGoUpButton();

        // 仅在确实是首次初始化（非目录导航）时自动恢复上次打开文档或打开第一篇文档
        if (!navFromDirectoryList) {
            FileItem targetDoc = findDocToOpen(fileItemList);
            if (targetDoc != null) {
                loadFile(targetDoc);
            } else {
                showWelcomeDoc();
            }
        }
        navFromDirectoryList = false;

        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_SAF_TREE_URI, treeRoot.toString())
                .remove(KEY_CURRENT_DIR)
                .apply();
    }

    // --- 子目录导航核心逻辑 ---

    private void enterDirectory(FileItem item) {
        navFromDirectoryList = true;
        if (safRootTreeUri != null && currentTreeUri != null) {
            // SAF 模式：记录当前目录 docId（仅为根目录则记录 null 占位），进入子目录
            safPathStack.add(safCurrentDocId);
            loadFromTreeDoc(safRootTreeUri, item.docId);
        } else {
            // 本地模式：记录当前目录到栈，进入子目录
            if (currentDirectory != null) {
                localNavStack.add(currentDirectory);
                currentDirectory = new File(item.path);
                loadFromLocalDir(currentDirectory);
            }
        }
    }

    private void goUpDirectory() {
        if (safRootTreeUri != null && currentTreeUri != null) {
            if (!safPathStack.isEmpty()) {
                String parentDocId = safPathStack.remove(safPathStack.size() - 1);
                navFromDirectoryList = true;
                loadFromTreeDoc(safRootTreeUri, parentDocId);
            }
        } else {
            if (!localNavStack.isEmpty()) {
                File parentDir = localNavStack.remove(localNavStack.size() - 1);
                navFromDirectoryList = true;
                currentDirectory = parentDir;
                loadFromLocalDir(parentDir);
            }
        }
        // 返回上级时不关闭抽屉，保持在目录浏览视图
    }

    private void resetNavStacks() {
        localNavStack.clear();
        safPathStack.clear();
    }

    private void loadFile(FileItem item) {
        String docKey = (item.uri != null) ? item.uri.toString() : (item.path != null ? item.path : item.name);
        switchReadingDocument(docKey);
        currentFileName = item.name;
        updateTitleViews(currentFileName, item.getFormattedDate() + " · " + item.getFormattedSize());

        StringBuilder sb = new StringBuilder();
        try {
            InputStream is;
            if (item.uri != null) {
                currentSafUri = item.uri;
                currentLocalFile = null;
                is = getContentResolver().openInputStream(item.uri);
            } else {
                currentLocalFile = new File(item.path);
                currentSafUri = null;
                is = new FileInputStream(currentLocalFile);
            }

            if (is != null) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                reader.close();
                is.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "读取文件失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        currentMarkdownText = sb.toString();
        etContent.setText(currentMarkdownText);

        if (isWebViewReady) {
            renderInWebView(currentMarkdownText);
        }
        updateOutlineList();
        if (fileAdapter != null) {
            fileAdapter.notifyDataSetChanged();
        }

        // 持久化当前阅读的文档（本地文件存绝对路径，SAF存URI）
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putString(KEY_CURRENT_FILE, item.uri != null ? item.uri.toString() : (item.path != null ? item.path : item.name))
                .apply();
    }

    /** 文档切换后刷新书签徽标与面板 */
    private void refreshBookmarksForCurrentDoc() {
        if (bookmarkAdapter == null) return;
        bookmarks.clear();
        bookmarks.addAll(bookmarksOfDoc(currentDocKey));
        bookmarkAdapter.notifyDataSetChanged();
        updateBookmarkEmptyState();
    }

    private void renderInWebView(String markdown) {
        if (markdown == null) markdown = "";
        try {
            byte[] bytes = markdown.getBytes(StandardCharsets.UTF_8);
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            String js = "renderMarkdown(decodeURIComponent(escape(window.atob('" + base64 + "'))));";
            webView.evaluateJavascript(js, null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void scrollToOutlineIndex(int index) {
        String js = "scrollToHeading(" + index + ");";
        webView.evaluateJavascript(js, null);
    }

    private void updateOutlineList() {
        refreshBookmarksForCurrentDoc();
        allOutlineList.clear();
        String textToParse = isEditMode ? etContent.getText().toString() : currentMarkdownText;
        List<OutlineItem> items = MarkdownParser.extractOutline(textToParse);

        // 标记各个节点是否有子标题（后继层级更深），且 h3 及之后的标题默认折叠
        for (int i = 0; i < items.size(); i++) {
            OutlineItem cur = items.get(i);
            boolean hasChildren = false;
            if (i + 1 < items.size()) {
                OutlineItem next = items.get(i + 1);
                if (next.level > cur.level) {
                    hasChildren = true;
                }
            }
            cur.hasChildren = hasChildren;
            if (cur.level >= 3 && hasChildren) {
                cur.isCollapsed = true; // h3 及更深层级默认折叠
            }
        }

        allOutlineList.addAll(items);
        rebuildVisibleOutlineList();
    }

    private void rebuildVisibleOutlineList() {
        outlineItemList.clear();
        int skipUntilLevel = -1; // 当遇到折叠的节点时，跳过其所有子层级

        for (int i = 0; i < allOutlineList.size(); i++) {
            OutlineItem item = allOutlineList.get(i);

            if (skipUntilLevel != -1) {
                if (item.level > skipUntilLevel) {
                    continue; // 属于被折叠节点的子孙，跳过展示
                } else {
                    skipUntilLevel = -1; // 恢复
                }
            }

            outlineItemList.add(item);

            // h2 及更深层级（level >= 2）且用户点击折叠了，则折叠其子标题
            if (item.level >= 2 && item.hasChildren && item.isCollapsed) {
                skipUntilLevel = item.level;
            }
        }

        if (outlineAdapter != null) {
            outlineAdapter.notifyDataSetChanged();
        }
        if (tvEmptyOutline != null) {
            tvEmptyOutline.setVisibility(allOutlineList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private void toggleMode() {
        stopTts();
        isEditMode = !isEditMode;
        if (isEditMode) {
            hideControlsMenu(false);
            if (tvReadingStats != null) {
                tvReadingStats.setVisibility(View.GONE);
            }
            etContent.setText(currentMarkdownText);
            etContent.setSelection(etContent.getText().length());
            layoutEdit.setVisibility(View.VISIBLE);
            webView.setVisibility(View.GONE);
            Toast.makeText(this, "已进入编辑模式", Toast.LENGTH_SHORT).show();
        } else {
            currentMarkdownText = etContent.getText().toString();
            saveCurrentFile(false);
            renderInWebView(currentMarkdownText);
            layoutEdit.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
            updateReadingStatsUi();
            updateOutlineList();
            refreshBookmarksForCurrentDoc();
            Toast.makeText(this, "已切回沉浸阅读", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveCurrentFile(boolean showToast) {
        String content = isEditMode ? etContent.getText().toString() : currentMarkdownText;
        currentMarkdownText = content;

        boolean success = false;
        try {
            if (currentSafUri != null) {
                OutputStream os = getContentResolver().openOutputStream(currentSafUri, "wt");
                if (os != null) {
                    os.write(content.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.close();
                    success = true;
                }
            } else if (currentLocalFile != null) {
                FileOutputStream fos = new FileOutputStream(currentLocalFile);
                fos.write(content.getBytes(StandardCharsets.UTF_8));
                fos.flush();
                fos.close();
                success = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (showToast) {
                Toast.makeText(this, "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
            return;
        }

        if (success && showToast) {
            Toast.makeText(this, "已保存「" + currentFileName + "」", Toast.LENGTH_SHORT).show();
        }
    }

    private void pickFolder() {
        resetNavStacks();
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            startActivityForResult(intent, REQUEST_CODE_SAF_FOLDER);
        } catch (Exception e) {
            Toast.makeText(this, "无法启动系统目录选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SAF_FOLDER && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            if (treeUri != null) {
                resetNavStacks();
                try {
                    getContentResolver().takePersistableUriPermission(treeUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (Exception ignored) {
                }
                loadFromTreeUri(treeUri);
            }
        }
    }

    private void showCreateFileDialog() {
        showPaperInput("新建笔记", "例如：读书笔记.md", "新笔记.md", new PaperInputCallback() {
            @Override
            public void onConfirm(String text) {
                if (text.isEmpty()) return;
                String name = text;
                if (!name.endsWith(".md")) {
                    name += ".md";
                }
                createNewFile(name);
            }
        });
    }

    private void createNewFile(String name) {
        String initialContent = "# " + name.replace(".md", "") + "\n\n在此开始您的纸质书写...\n";
        try {
            if (currentTreeUri != null && safRootTreeUri != null) {
                // 在当前浏览目录中新建（若在子目录则用该子目录 docId）
                String parentDocId = (safCurrentDocId != null)
                        ? safCurrentDocId
                        : DocumentsContract.getTreeDocumentId(safRootTreeUri);
                Uri parentDocUri = DocumentsContract.buildDocumentUriUsingTree(safRootTreeUri, parentDocId);
                Uri newDocUri = DocumentsContract.createDocument(getContentResolver(), parentDocUri, "text/markdown", name);
                if (newDocUri != null) {
                    OutputStream os = getContentResolver().openOutputStream(newDocUri);
                    if (os != null) {
                        os.write(initialContent.getBytes(StandardCharsets.UTF_8));
                        os.close();
                    }
                    loadFromTreeDoc(safRootTreeUri, safCurrentDocId);
                    return;
                }
            } else if (currentDirectory != null) {
                File file = new File(currentDirectory, name);
                FileOutputStream fos = new FileOutputStream(file);
                fos.write(initialContent.getBytes(StandardCharsets.UTF_8));
                fos.close();
                loadFromLocalDir(currentDirectory);
                return;
            }
        } catch (Exception e) {
            Toast.makeText(this, "创建失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleFileListPanel() {
        if (panelFileList.getVisibility() == View.VISIBLE) {
            hideFileListPanel();
        } else {
            hideOutlinePanel();
            if (isFileSearchMode) {
                closeFileSearch();
            }
            panelFileList.setVisibility(View.VISIBLE);
            maskOverlay.setVisibility(View.VISIBLE);
            Animation slideIn = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, -1.0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f);
            slideIn.setDuration(240);
            slideIn.setInterpolator(new DecelerateInterpolator());
            panelFileList.startAnimation(slideIn);
        }
    }

    private void hideFileListPanel() {
        if (panelFileList.getVisibility() != View.VISIBLE) return;
        Animation slideOut = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, -1.0f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f);
        slideOut.setDuration(200);
        slideOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                panelFileList.setVisibility(View.GONE);
                if (panelOutline.getVisibility() != View.VISIBLE) {
                    maskOverlay.setVisibility(View.GONE);
                }
            }
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        panelFileList.startAnimation(slideOut);
    }

    private void toggleOutlinePanel() {
        if (panelOutline.getVisibility() == View.VISIBLE) {
            hideOutlinePanel();
        } else {
            hideFileListPanel();
            updateOutlineList();
            panelOutline.setVisibility(View.VISIBLE);
            maskOverlay.setVisibility(View.VISIBLE);
            Animation slideIn = new TranslateAnimation(
                    Animation.RELATIVE_TO_SELF, 1.0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f,
                    Animation.RELATIVE_TO_SELF, 0f);
            slideIn.setDuration(240);
            slideIn.setInterpolator(new DecelerateInterpolator());
            panelOutline.startAnimation(slideIn);
            // 打开大纲时，定位到当前阅读位置附近的大纲项
            highlightCurrentOutlineItem();
        }
    }

    // 根据当前阅读位置滚动大纲列表到对应标题附近
    private void highlightCurrentOutlineItem() {
        if (outlineItemList == null || outlineItemList.isEmpty()) return;
        if (isEditMode) return;
        try {
            webView.evaluateJavascript("getCurrentHeadingIndex();", new android.webkit.ValueCallback<String>() {
                @Override
                public void onReceiveValue(String value) {
                    if (value == null) return;
                    // 去除引号得到数字索引（JS 返回形式可能是 "12" 或 null）
                    String cleaned = value.replace("\"", "").trim();
                    int headingIdx;
                    try {
                        headingIdx = Integer.parseInt(cleaned);
                    } catch (NumberFormatException e) {
                        return;
                    }
                    if (headingIdx < 0) return;

                    // 在可见大纲列表中找出 index 与当前标题匹配或最接近的条目
                    int targetPos = -1;
                    int bestDelta = Integer.MAX_VALUE;
                    for (int i = 0; i < outlineItemList.size(); i++) {
                        OutlineItem item = outlineItemList.get(i);
                        if (item.index >= headingIdx) {
                            int delta = Math.abs(item.index - headingIdx);
                            if (delta < bestDelta) {
                                bestDelta = delta;
                                targetPos = i;
                            }
                            break;
                        }
                    }
                    if (targetPos < 0 && !outlineItemList.isEmpty()) {
                        targetPos = outlineItemList.size() - 1;
                    }
                    if (targetPos >= 0) {
                        listOutline.smoothScrollToPosition(targetPos);
                    }
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void hideOutlinePanel() {
        if (panelOutline.getVisibility() != View.VISIBLE) return;
        Animation slideOut = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 1.0f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f);
        slideOut.setDuration(200);
        slideOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {}
            @Override
            public void onAnimationEnd(Animation animation) {
                panelOutline.setVisibility(View.GONE);
                if (panelFileList.getVisibility() != View.VISIBLE) {
                    maskOverlay.setVisibility(View.GONE);
                }
            }
            @Override
            public void onAnimationRepeat(Animation animation) {}
        });
        panelOutline.startAnimation(slideOut);
    }

    @Override
    public void onBackPressed() {
        if (isSearchOpen) {
            closeSearchCard();
            return;
        }
        if (panelBookmark != null && panelBookmark.getVisibility() == View.VISIBLE) {
            hideBookmarkPanel();
            return;
        }
        if (panelOutline.getVisibility() == View.VISIBLE) {
            hideOutlinePanel();
            return;
        }
        if (panelFileList.getVisibility() == View.VISIBLE) {
            if (isFileSearchMode) {
                closeFileSearch();
                return;
            }
            hideFileListPanel();
            return;
        }
        if (isControlsMenuVisible) {
            hideControlsMenu(true);
            return;
        }
        if (isEditMode) {
            toggleMode();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (currentDocKey != null) {
            docSessionStartTime = System.currentTimeMillis();
            statsHandler.removeCallbacks(statsTickerRunnable);
            statsHandler.post(statsTickerRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        persistCurrentSessionTime();
        statsHandler.removeCallbacks(statsTickerRunnable);
    }

    @Override
    protected void onDestroy() {
        persistCurrentSessionTime();
        statsHandler.removeCallbacks(statsTickerRunnable);
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        searchHandler.removeCallbacksAndMessages(null);
        searchExecutor.shutdownNow();
        super.onDestroy();
    }

    // --- 阅读统计持久化核心逻辑 ---

    private void toggleReadingStats() {
        isStatsEnabled = !isStatsEnabled;
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                .putBoolean(KEY_STATS_ENABLED, isStatsEnabled)
                .apply();
        updateStatsButtonUi();
        updateReadingStatsUi();
        Toast.makeText(this, isStatsEnabled ? "已开启右上角阅读统计" : "已关闭右上角阅读统计", Toast.LENGTH_SHORT).show();
    }

    private void updateStatsButtonUi() {
        if (btnToggleStats != null) {
            btnToggleStats.setText(isStatsEnabled ? "统计: 开" : "统计: 关");
            btnToggleStats.setTextColor(isStatsEnabled
                    ? (isDarkMode ? Color.parseColor("#D48B68") : Color.parseColor("#8C5338"))
                    : (isDarkMode ? Color.parseColor("#8C857C") : Color.parseColor("#7A746E")));
        }
    }

    private void switchReadingDocument(String newDocKey) {
        if (newDocKey == null || newDocKey.trim().isEmpty()) return;
        if (currentDocKey != null) {
            persistCurrentSessionTime();
        }
        currentDocKey = newDocKey;
        docSessionStartTime = System.currentTimeMillis();

        // 阅读次数递增 (+1) 并持久化
        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String countKey = PREF_STATS_PREFIX + "count_" + currentDocKey;
        int currentCount = sp.getInt(countKey, 0) + 1;
        sp.edit().putInt(countKey, currentCount).apply();

        statsHandler.removeCallbacks(statsTickerRunnable);
        statsHandler.post(statsTickerRunnable);
    }

    private void persistCurrentSessionTime() {
        if (currentDocKey == null || docSessionStartTime <= 0) return;
        long elapsedSec = (System.currentTimeMillis() - docSessionStartTime) / 1000;
        if (elapsedSec > 0) {
            SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            String timeKey = PREF_STATS_PREFIX + "time_" + currentDocKey;
            long totalTimeSec = sp.getLong(timeKey, 0) + elapsedSec;
            sp.edit().putLong(timeKey, totalTimeSec).apply();
            docSessionStartTime = System.currentTimeMillis();
        }
    }

    private void updateReadingStatsUi() {
        if (tvReadingStats == null || isEditMode || currentDocKey == null || !isStatsEnabled) {
            if (tvReadingStats != null) {
                tvReadingStats.setVisibility(View.GONE);
            }
            return;
        }

        SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String timeKey = PREF_STATS_PREFIX + "time_" + currentDocKey;
        String countKey = PREF_STATS_PREFIX + "count_" + currentDocKey;

        long persistedSec = sp.getLong(timeKey, 0);
        int readCount = sp.getInt(countKey, 1);

        long currentSessionSec = 0;
        if (docSessionStartTime > 0) {
            currentSessionSec = (System.currentTimeMillis() - docSessionStartTime) / 1000;
        }
        long totalSec = persistedSec + currentSessionSec;

        String formattedTime = formatReadingDuration(totalSec);
        String statsText = "读过 " + readCount + " 次 · " + formattedTime;
        tvReadingStats.setText(statsText);
        if (tvReadingStats.getVisibility() != View.VISIBLE) {
            tvReadingStats.setVisibility(View.VISIBLE);
        }
    }

    private String formatReadingDuration(long totalSec) {
        if (totalSec < 60) {
            return totalSec + "秒";
        } else if (totalSec < 3600) {
            long min = totalSec / 60;
            long sec = totalSec % 60;
            return min + "分" + (sec > 0 ? sec + "秒" : "");
        } else {
            long hr = totalSec / 3600;
            long min = (totalSec % 3600) / 60;
            return hr + "小时" + (min > 0 ? min + "分" : "");
        }
    }

    // --- 适配器实现 ---

    private class FileAdapter extends BaseAdapter {
        @Override
        public int getCount() { return fileItemList.size(); }
        @Override
        public Object getItem(int position) { return fileItemList.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_file, parent, false);
            }
            FileItem item = fileItemList.get(position);
            View root = convertView.findViewById(R.id.item_file_root);
            TextView tvName = convertView.findViewById(R.id.tv_file_name);
            TextView tvInfo = convertView.findViewById(R.id.tv_file_info);
            ImageView ivIcon = convertView.findViewById(R.id.iv_file_icon);
            FrameLayout layoutBadge = convertView.findViewById(R.id.layout_icon_badge);
            TextView tvCurrentBadge = convertView.findViewById(R.id.tv_current_badge);

            tvName.setText(item.name);

            int colorTextPrimary = isDarkMode ? Color.parseColor("#D6D0C7") : Color.parseColor("#2D2A28");
            int colorTextSecondary = isDarkMode ? Color.parseColor("#8C857C") : Color.parseColor("#7A746E");
            int colorAccent = isDarkMode ? Color.parseColor("#D48B68") : Color.parseColor("#8C5338");
            int colorBorder = isDarkMode ? Color.parseColor("#383532") : Color.parseColor("#E0D8CB");
            int colorSurface = isDarkMode ? Color.parseColor("#252320") : Color.parseColor("#FAF7F0");
            int colorBadgeBg = isDarkMode ? Color.parseColor("#2F2C28") : Color.parseColor("#EFE9DD");

            float density = getResources().getDisplayMetrics().density;
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(colorSurface);
            cardBg.setCornerRadius(10 * density);

            boolean isCurrentReading = !item.isDirectory && item.name != null && item.name.equals(currentFileName);
            if (isCurrentReading) {
                cardBg.setStroke((int) (1.5f * density), colorAccent);
                tvCurrentBadge.setVisibility(View.VISIBLE);
                tvCurrentBadge.setTextColor(colorAccent);
            } else {
                cardBg.setStroke((int) (1f * density), colorBorder);
                tvCurrentBadge.setVisibility(View.GONE);
            }
            root.setBackground(cardBg);

            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(colorBadgeBg);
            badgeBg.setCornerRadius(8 * density);
            layoutBadge.setBackground(badgeBg);

            if (isFileSearchMode && position < fileSearchPaths.size()) {
                // 搜索模式：重点显示文档所在的文件夹，方便定位
                String folder = fileSearchPaths.get(position);
                String where = (folder == null || folder.trim().isEmpty()) ? "当前目录" : folder;
                ivIcon.setImageResource(R.drawable.ic_file_doc);
                ivIcon.setColorFilter(colorAccent);
                tvInfo.setText("📁 " + where + "  ·  " + item.getFormattedSize());
            } else if (item.isDirectory) {
                ivIcon.setImageResource(R.drawable.ic_folder);
                ivIcon.setColorFilter(colorAccent);
                tvInfo.setText(item.getFormattedDate() + "  ·  文件夹");
            } else {
                ivIcon.setImageResource(R.drawable.ic_file_doc);
                ivIcon.setColorFilter(colorAccent);
                tvInfo.setText(item.getFormattedDate() + "  ·  " + item.getFormattedSize());
            }

            tvName.setTextColor(isCurrentReading ? colorAccent : colorTextPrimary);
            tvInfo.setTextColor(colorTextSecondary);

            if (paperTypeface != null) {
                tvName.setTypeface(paperTypeface, Typeface.BOLD);
                tvInfo.setTypeface(paperTypeface);
            }
            return convertView;
        }
    }

    /** 书签列表适配器 */
    private class BookmarkAdapter extends BaseAdapter {
        @Override
        public int getCount() { return bookmarks.size(); }
        @Override
        public Object getItem(int position) { return bookmarks.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_bookmark, parent, false);
            }
            Bookmark bm = bookmarks.get(position);
            View root = convertView.findViewById(R.id.item_bookmark_root);
            TextView tvTitle = convertView.findViewById(R.id.tv_bookmark_title);
            TextView tvSnippet = convertView.findViewById(R.id.tv_bookmark_snippet);
            TextView tvTime = convertView.findViewById(R.id.tv_bookmark_time);

            float density = getResources().getDisplayMetrics().density;
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(isDarkMode ? Color.parseColor("#252320") : Color.parseColor("#FAF7F0"));
            cardBg.setCornerRadius(10 * density);
            cardBg.setStroke((int) (1f * density), isDarkMode ? Color.parseColor("#383532") : Color.parseColor("#E0D8CB"));
            root.setBackground(cardBg);

            tvTitle.setText(bm.title);
            tvTitle.setTextColor(textPrimaryColor());
            if (bm.snippet != null && !bm.snippet.trim().isEmpty()) {
                tvSnippet.setVisibility(View.VISIBLE);
                tvSnippet.setText(bm.snippet);
            } else {
                tvSnippet.setVisibility(View.GONE);
            }
            tvSnippet.setTextColor(textSecondaryColor());
            tvTime.setText(formatBookmarkTime(bm.createdAt));
            tvTime.setTextColor(textSecondaryColor());

            if (paperTypeface != null) {
                tvTitle.setTypeface(paperTypeface, Typeface.BOLD);
                tvSnippet.setTypeface(paperTypeface);
            }
            return convertView;
        }
    }

    /** 跨文档搜索结果适配器 */
    private class SearchResultAdapter extends BaseAdapter {
        @Override
        public int getCount() { return searchResults.size(); }
        @Override
        public Object getItem(int position) { return searchResults.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_search_result, parent, false);
            }
            SearchResult r = searchResults.get(position);
            View root = convertView.findViewById(R.id.item_search_root);
            TextView tvFile = convertView.findViewById(R.id.tv_result_file);
            TextView tvHits = convertView.findViewById(R.id.tv_result_hits);
            TextView tvSnippet = convertView.findViewById(R.id.tv_result_snippet);

            float density = getResources().getDisplayMetrics().density;
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(isDarkMode ? Color.parseColor("#252320") : Color.parseColor("#FAF7F0"));
            cardBg.setCornerRadius(10 * density);
            cardBg.setStroke((int) (1f * density), isDarkMode ? Color.parseColor("#383532") : Color.parseColor("#E0D8CB"));
            root.setBackground(cardBg);

            tvFile.setText(r.fileName);
            tvFile.setTextColor(accentColor());
            tvHits.setText(r.hitCount + " 处");
            tvHits.setTextColor(textSecondaryColor());
            tvSnippet.setText(r.snippet);
            tvSnippet.setTextColor(textPrimaryColor());

            if (paperTypeface != null) {
                tvFile.setTypeface(paperTypeface, Typeface.BOLD);
                tvSnippet.setTypeface(paperTypeface);
            }
            return convertView;
        }
    }

    private class OutlineAdapter extends BaseAdapter {
        @Override
        public int getCount() { return outlineItemList.size(); }
        @Override
        public Object getItem(int position) { return outlineItemList.get(position); }
        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_outline, parent, false);
            }
            OutlineItem item = outlineItemList.get(position);
            TextView tvCollapse = convertView.findViewById(R.id.tv_collapse_toggle);
            View viewBullet = convertView.findViewById(R.id.view_bullet);
            TextView tvText = convertView.findViewById(R.id.tv_heading_text);

            float density = getResources().getDisplayMetrics().density;

            // 折叠指示器：针对 h2 及更深层级且有子标题的节点
            if (item.level >= 2 && item.hasChildren) {
                tvCollapse.setVisibility(View.VISIBLE);
                tvCollapse.setText(item.isCollapsed ? "▶" : "▼");
                tvCollapse.setTextColor(isDarkMode ? Color.parseColor("#A8A095") : Color.parseColor("#7A746E"));
                final OutlineItem currentOutline = item;
                tvCollapse.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        currentOutline.isCollapsed = !currentOutline.isCollapsed;
                        rebuildVisibleOutlineList();
                    }
                });
            } else {
                tvCollapse.setVisibility(View.GONE);
                tvCollapse.setOnClickListener(null);
            }

            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);

            int dotSizeDp;
            if (item.level == 1) {
                dotSizeDp = 8;
                dot.setColor(isDarkMode ? Color.parseColor("#D48B68") : Color.parseColor("#8C5338"));
                tvText.setTextSize(15.5f);
                if (paperTypeface != null) {
                    tvText.setTypeface(paperTypeface, Typeface.BOLD);
                } else {
                    tvText.setTypeface(Typeface.DEFAULT_BOLD);
                }
            } else if (item.level == 2) {
                dotSizeDp = 6;
                dot.setColor(isDarkMode ? Color.parseColor("#A8A095") : Color.parseColor("#6B645C"));
                tvText.setTextSize(14.5f);
                if (paperTypeface != null) {
                    tvText.setTypeface(paperTypeface, Typeface.NORMAL);
                } else {
                    tvText.setTypeface(Typeface.DEFAULT);
                }
            } else {
                dotSizeDp = 4;
                dot.setColor(isDarkMode ? Color.parseColor("#6B645C") : Color.parseColor("#A8A095"));
                tvText.setTextSize(13.5f);
                if (paperTypeface != null) {
                    tvText.setTypeface(paperTypeface, Typeface.NORMAL);
                } else {
                    tvText.setTypeface(Typeface.DEFAULT);
                }
            }

            int dotSizePx = (int) (dotSizeDp * density + 0.5f);
            ViewGroup.LayoutParams lp = viewBullet.getLayoutParams();
            if (lp != null) {
                lp.width = dotSizePx;
                lp.height = dotSizePx;
                viewBullet.setLayoutParams(lp);
            }
            viewBullet.setBackground(dot);

            int indent = (int) ((item.level - 1) * 20 * density);
            convertView.setPadding(indent + (int)(16 * density), (int)(11 * density), (int)(16 * density), (int)(11 * density));

            tvText.setText(item.title);
            tvText.setTextColor(isDarkMode ? Color.parseColor("#D6D0C7") : Color.parseColor("#2D2A28"));
            return convertView;
        }
    }
}