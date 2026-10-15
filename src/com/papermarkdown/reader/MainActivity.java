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
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
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

public class MainActivity extends Activity implements TextToSpeech.OnInitListener {

    private static final int REQUEST_CODE_SAF_FOLDER = 1001;
    private static final String PREFS_NAME = "paper_md_prefs";
    private static final String KEY_CURRENT_DIR = "current_dir";
    private static final String KEY_CURRENT_FILE = "current_file";
    private static final String KEY_SAF_TREE_URI = "saf_tree_uri";
    private static final String KEY_DARK_MODE = "key_dark_mode";
    private static final String KEY_STATS_ENABLED = "key_stats_enabled";

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
        tvPanelFileTitle = findViewById(R.id.tv_panel_file_title);
        tvCurrentDir = findViewById(R.id.tv_current_dir);
        listFiles = findViewById(R.id.list_files);
        tvEmptyFiles = findViewById(R.id.tv_empty_files);
        btnChangeFolder = findViewById(R.id.btn_change_folder);
        btnNewFile = findViewById(R.id.btn_new_file);
        btnGoUp = findViewById(R.id.btn_go_up);
        tvGoUpLabel = findViewById(R.id.tv_go_up_label);

        panelOutline = findViewById(R.id.panel_outline);
        panelOutlineHeader = findViewById(R.id.panel_outline_header);
        tvPanelOutlineTitle = findViewById(R.id.tv_panel_outline_title);
        listOutline = findViewById(R.id.list_outline);
        tvEmptyOutline = findViewById(R.id.tv_empty_outline);
        btnCloseOutline = findViewById(R.id.btn_close_outline);

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

    private void showControlsMenu(boolean animate) {
        if (isControlsMenuVisible) return;
        isControlsMenuVisible = true;

        appBar.setVisibility(View.VISIBLE);
        wheelMenu.setVisibility(View.VISIBLE);

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
        btnChangeFolder.setTextColor(colorAccent);
        if (tvGoUpLabel != null) {
            tvGoUpLabel.setTextColor(colorAccent);
        }

        // 9. 大纲面板 (右侧抽屉)
        GradientDrawable outlineSheetDrawable = new GradientDrawable();
        outlineSheetDrawable.setColor(colorBg);
        outlineSheetDrawable.setCornerRadii(new float[]{24 * density, 24 * density, 0, 0, 0, 0, 24 * density, 24 * density});
        outlineSheetDrawable.setStroke((int) (1.5f * density), colorBorder);
        panelOutline.setBackground(outlineSheetDrawable);

        panelOutlineHeader.setBackgroundColor(colorSurfaceVariant);
        tvPanelOutlineTitle.setTextColor(colorTextPrimary);

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
            }
        });

        btnChangeFolder.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickFolder();
            }
        });

        btnNewFile.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showCreateFileDialog();
            }
        });

        listFiles.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                FileItem item = fileItemList.get(position);
                if (item.isDirectory) {
                    // 进入子目录
                    enterDirectory(item);
                } else {
                    stopTts();
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
                + "- **即刻编辑**：随时在阅读与编辑模式间无缝切换，附带便捷 Markdown 输入栏。\n\n"
                + "### 快捷操作指南\n\n"
                + "1. **轻触屏幕中间**：唤起或收起右侧悬浮圆形功能轮盘；\n"
                + "2. 点击 **大纲**：右侧滑出章节目录抽屉，直达重点；\n"
                + "3. 点击 **目录**：左侧滑出文稿与子目录浏览器；\n"
                + "4. 点击 **朗读**：启停智能有声朗读；\n"
                + "5. 点击 **夜间/日间**：随心切换暗色与宣纸主题；\n"
                + "6. 点击 **编辑**：进入素雅信笺编辑模式。\n\n"
                + "---\n\n"
                + "祝您阅读与书写愉快！";

        if (isWebViewReady) {
            renderInWebView(currentMarkdownText);
        }
        updateOutlineList();
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

        String label = dir.getName() + "  (含 " + fileItemList.size() + " 项)";
        tvCurrentDir.setText(label);

        fileAdapter.notifyDataSetChanged();
        tvEmptyFiles.setVisibility(fileItemList.isEmpty() ? View.VISIBLE : View.GONE);
        updateGoUpButton();

        // 仅在确实是首次初始化（非目录导航）时自动打开第一篇文档
        if (!navFromDirectoryList) {
            FileItem firstDoc = findFirstDocItem(fileItemList);
            if (firstDoc != null) {
                loadFile(firstDoc);
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

        fileAdapter.notifyDataSetChanged();
        tvEmptyFiles.setVisibility(fileItemList.isEmpty() ? View.VISIBLE : View.GONE);
        updateGoUpButton();

        if (!navFromDirectoryList) {
            FileItem firstDoc = findFirstDocItem(fileItemList);
            if (firstDoc != null) {
                loadFile(firstDoc);
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
        outlineItemList.clear();
        String textToParse = isEditMode ? etContent.getText().toString() : currentMarkdownText;
        List<OutlineItem> items = MarkdownParser.extractOutline(textToParse);
        outlineItemList.addAll(items);
        outlineAdapter.notifyDataSetChanged();
        tvEmptyOutline.setVisibility(outlineItemList.isEmpty() ? View.VISIBLE : View.GONE);
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
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("新建笔记");

        final EditText input = new EditText(this);
        input.setHint("例如：读书笔记.md");
        input.setText("新笔记.md");
        input.setSelection(0, 3);
        input.setPadding(40, 30, 40, 30);
        builder.setView(input);

        builder.setPositiveButton("创建", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                String name = input.getText().toString().trim();
                if (name.isEmpty()) return;
                if (!name.endsWith(".md")) {
                    name += ".md";
                }
                createNewFile(name);
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
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
        if (panelOutline.getVisibility() == View.VISIBLE) {
            hideOutlinePanel();
            return;
        }
        if (panelFileList.getVisibility() == View.VISIBLE) {
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

            if (item.isDirectory) {
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
            View viewBullet = convertView.findViewById(R.id.view_bullet);
            TextView tvText = convertView.findViewById(R.id.tv_heading_text);

            float density = getResources().getDisplayMetrics().density;
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