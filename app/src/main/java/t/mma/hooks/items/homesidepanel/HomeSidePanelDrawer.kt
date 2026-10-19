package t.mma.hooks.items.homesidepanel

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.net.Uri
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.Window
import android.view.animation.PathInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import t.mma.hooks.api.core.WeChatApis
import t.mma.ui.miuix.MiuixSettingsPage

/** OKK-style native drawer. Keeps the view tree cached to avoid reopen jank. */
internal class HomeSidePanelDrawer(
    private val activity: Activity,
    private val onNavigate: () -> Unit
) {
    private val density = activity.resources.displayMetrics.density
    private val panelWidth = minOf(activity.resources.displayMetrics.widthPixels - dp(16), minOf((activity.resources.displayMetrics.widthPixels * .78f).toInt(), dp(300)) + dp(50))
    private val root = FrameLayout(activity)
    private val scrim = View(activity)
    private val panel = LinearLayout(activity)
    private val edge = View(activity)
    private var host: ViewGroup? = null
    private var enabled = false
    private var opened = false
    private var dragging = false
    private var downX = 0f
    private var lastX = 0f
    private var profileAvatar: ImageView? = null
    private var avatarBitmap: Bitmap? = null
    private val prefs = HomeSidePanelSettings.preferences(activity)
    private var lunarLabel: TextView? = null
    private var lunarReceiverRegistered = false
    private val lunarReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) { if (opened) refreshLunar() }
    }
    private fun refreshLunar() { lunarLabel?.text = SidePanelLunar.today() }
    private var clock: android.widget.TextClock? = null
    private var dateLabel: android.widget.TextClock? = null
    private var settingsPage = false
    private var settingsSubBack: (() -> Boolean)? = null
    private var settingsScrollY = 0
    private var settingsCleanup: (() -> Unit)? = null
    private var contentScroll: ScrollView? = null
    private var cardsBody: LinearLayout? = null
    private val cardViews = linkedMapOf<String, View>()
    private val photoViews = linkedMapOf<String,SidePanelMediaView>()
    private var shortcutDrag: HomeSidePanelSettings.Shortcut? = null
    private var backgroundBitmap: Bitmap? = null
    private var imageGeneration = 0
    private val imageLoader = java.util.concurrent.Executors.newSingleThreadExecutor { task -> Thread(task, "Hchat-PanelImages").apply { isDaemon = true } }
    private var effects: SidePanelEffects? = null
    private var avatarEffects: AvatarHeaderEffect? = null
    private var active = true
    private var dragCard: View? = null
    private var renderedNight: Boolean? = null
    private val screen = FrameLayout(activity)
    fun setActive(value: Boolean) { active = value; if(value && opened) refreshLunar(); if (value && renderedNight != night()) renderPage(); updateEffects() }
    private fun updateEffects() {
        photoViews.values.forEach { it.setPlayback(opened && active && !settingsPage) }
        effects?.running = opened && active && prefs.getBoolean(SidePanelAppearance.EFFECT, false)
        if (opened && active && !settingsPage && prefs.getBoolean(SidePanelAppearance.LIGHTNING, false)) {
            avatarEffects?.update(prefs.getString(SidePanelAppearance.COLORS, t.mma.hooks.items.antirecall.DynamicRecallGradient.DEFAULT).orEmpty(),2400,prefs.getFloat(SidePanelAppearance.WIDTH,1.5f),true,scaleLightningWidth = true, avatarInsetDp = 5f)
        } else avatarEffects?.stop()
    }
    private var edgeEnabled = false
    fun setEdgeEnabled(value: Boolean) {
        edgeEnabled = value
        edge.visibility = if (enabled && value && !opened) View.VISIBLE else View.GONE
        root.visibility = if (opened || (enabled && value)) View.VISIBLE else View.GONE
    }

    fun attachTo(parent: ViewGroup) {
        if (host === parent && root.parent === parent) return
        host = parent
        buildPanel()
        if (!lunarReceiverRegistered) {
            val filter = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_TIME_TICK);addAction(android.content.Intent.ACTION_DATE_CHANGED)
                addAction(android.content.Intent.ACTION_TIME_CHANGED);addAction(android.content.Intent.ACTION_TIMEZONE_CHANGED)
            }
            if (android.os.Build.VERSION.SDK_INT >= 33) activity.registerReceiver(lunarReceiver,filter,Context.RECEIVER_NOT_EXPORTED)
            else activity.registerReceiver(lunarReceiver,filter)
            lunarReceiverRegistered = true
        }
        root.visibility = View.GONE
        root.elevation = dp(100).toFloat()
        root.isClickable = false
        parent.addView(root, ViewGroup.LayoutParams(dp(72), -1))
        root.bringToFront()
        resizeRoot(open = false)

    }

    fun detach() {
        if(lunarReceiverRegistered) { activity.unregisterReceiver(lunarReceiver);lunarReceiverRegistered=false }
        screen.animate().withEndAction(null).cancel()
        settingsCleanup?.invoke(); settingsCleanup = null
        opened = false; updateEffects()
        photoViews.values.forEach { it.dispose() };photoViews.clear()
        imageGeneration++
        imageLoader.shutdownNow()
        panel.animate().cancel()
        scrim.animate().cancel()
        (root.parent as? ViewGroup)?.removeView(root)
        host = null
        opened = false
    }

    fun setAvatar(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        avatarBitmap = bitmap
        profileAvatar?.setImageBitmap(bitmap)
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        root.visibility = if (opened || (value && edgeEnabled)) View.VISIBLE else View.GONE
        edge.visibility = if (value && edgeEnabled && !opened) View.VISIBLE else View.GONE
        if (!value && opened) close(false)
    }

    fun isOpen(): Boolean = opened
    fun toggle() { if (opened) close(true) else show(true) }

    private fun prepareMainPage() {
        screen.animate().withEndAction(null).cancel()
        screen.alpha = 1f
        screen.translationX = 0f
        if (settingsPage) {
            settingsPage = false
            renderPage()
        }
        contentScroll?.scrollTo(0,0)
    }
    fun show(animated: Boolean) {
        if (!enabled || opened) return
        prepareMainPage()
        panel.animate().withEndAction(null).cancel()
        scrim.animate().cancel()
        opened = true
        refreshLunar()
        if (renderedNight != night()) renderPage()
        updateEffects()
        resizeRoot(open = true)
        edge.visibility = View.GONE
        root.visibility = View.VISIBLE
        scrim.visibility = View.VISIBLE
        panel.visibility = View.VISIBLE
        if (!animated) {
            panel.translationX = 0f
            scrim.alpha = .42f
            return
        }
        panel.translationX = -panelWidth.toFloat()
        scrim.alpha = 0f
        panel.animate().translationX(0f).setDuration(280L).setInterpolator(PathInterpolator(.05f, .7f, .1f, 1f)).start()
        scrim.animate().alpha(.42f).setDuration(280L).start()
    }

    fun close(animated: Boolean) {
        photoViews.values.forEach { it.setPlayback(false) }
        screen.animate().withEndAction(null).cancel()
        screen.alpha = 1f; screen.translationX = 0f
        effects?.running = false
        avatarEffects?.stop()
        opened = false
        if (!animated) {
            panel.animate().cancel(); scrim.animate().cancel()
            panel.translationX = -panelWidth.toFloat(); scrim.alpha = 0f; scrim.visibility = View.GONE
            panel.visibility = View.GONE
            edge.visibility = if (enabled && edgeEnabled) View.VISIBLE else View.GONE
            resizeRoot(open = false)
            root.visibility = if (enabled && edgeEnabled) View.VISIBLE else View.GONE
            return
        }
        panel.animate().translationX(-panelWidth.toFloat()).setDuration(180L).setInterpolator(PathInterpolator(.3f, 0f, .8f, .15f)).withEndAction {
            if (opened) return@withEndAction
            scrim.visibility = View.GONE
            panel.visibility = View.GONE
            edge.visibility = if (enabled && edgeEnabled) View.VISIBLE else View.GONE
            resizeRoot(open = false)
            root.visibility = if (enabled && edgeEnabled) View.VISIBLE else View.GONE
        }.start()
        scrim.animate().alpha(0f).setDuration(180L).start()
    }

    private fun buildPanel() {
        panel.orientation = LinearLayout.VERTICAL
        panel.setBackgroundColor(color(if (night()) "#1A1C1A" else "#F7F8F6"))
        panel.clipChildren = true
        panel.setPadding(0, 0, 0, 0)
        panel.addView(View(activity), LinearLayout.LayoutParams(-1, statusBarHeight()))
        panel.addView(screen, LinearLayout.LayoutParams(-1, 0, 1f))
        renderPage()
        loadImages()
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        root.addView(panel, FrameLayout.LayoutParams(panelWidth, -1, Gravity.START))
        root.addView(edge, FrameLayout.LayoutParams(dp(72), -1, Gravity.START))
        panel.translationX = -panelWidth.toFloat()
        scrim.setBackgroundColor(Color.BLACK)
        scrim.alpha = 0f; scrim.visibility = View.GONE
        scrim.setOnClickListener { close(true) }
        edge.setOnTouchListener { _, event -> onEdgeTouch(event) }
        panel.setOnTouchListener { _, event -> onPanelTouch(event) }
        root.isFocusable = true; root.isFocusableInTouchMode = true
        root.setOnKeyListener { _, key, ev ->
            if (key == android.view.KeyEvent.KEYCODE_BACK && ev.action == android.view.KeyEvent.ACTION_UP && opened) { if (settingsPage) { if(settingsSubBack?.invoke()!=true) switchPage(false) } else close(true); true } else false
        }
    }

    private fun loadImages() {
        val generation = ++imageGeneration
        imageLoader.execute {
            val backdrop = SidePanelImages.load(activity.applicationContext, "background")
            activity.runOnUiThread {
                if (generation != imageGeneration || host == null) return@runOnUiThread
                backgroundBitmap = backdrop
                loadPhotoMedia()
                applyPanelBackground()
            }
        }
    }
    private fun loadPhotoMedia() {
        photoViews.forEach { (id,view) ->
            view.load(SidePanelImages.photoFile(activity,id),prefs.getLong(SidePanelAppearance.photoRevision(id),0L))
            view.setPlayback(opened && active && !settingsPage,restart=true)
        }
    }
    private fun pickImage(key: String) {
        SidePanelImagePicker.launch(activity, key) { result ->
            if (result == SidePanelPickResult.SAVED && host != null) loadImages()
            else if (result == SidePanelPickResult.FAILED) toast("图片导入失败")
        }
    }
    private fun applyPanelBackground() {
        val drawable: android.graphics.drawable.Drawable = backgroundBitmap?.let {
            android.graphics.drawable.BitmapDrawable(activity.resources, it).apply { gravity = Gravity.FILL }
        } ?: android.graphics.drawable.ColorDrawable(color(if (night()) "#1A1C1A" else "#F7F8F6"))
        drawable.alpha = (prefs.getFloat(SidePanelAppearance.OPACITY, 1f).coerceIn(0f,1f) * 255).toInt()
        panel.background = drawable
    }
    private fun switchPage(settings: Boolean) {
        if (settingsPage == settings) return
        if(settings) photoViews.values.forEach { it.setPlayback(false) }
        screen.animate().cancel()
        screen.animate().alpha(0f).translationX(if (settings) -dp(32).toFloat() else dp(32).toFloat()).setDuration(130).withEndAction {
            settingsPage = settings
            renderPage()
            screen.translationX = if (settings) dp(32).toFloat() else -dp(32).toFloat()
            screen.alpha = 0f
            screen.animate().alpha(1f).translationX(0f).setDuration(180).start()
        }.start()
    }
    private fun renderPage() {
        settingsCleanup?.invoke(); settingsCleanup = null
        effects?.running = false; avatarEffects?.stop()
        screen.removeAllViews(); cardViews.clear()
        avatarEffects = null
        profileAvatar = null
        photoViews.values.forEach { it.dispose() };photoViews.clear()
        lunarLabel = null
        renderedNight = night()
        val column = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        screen.addView(column, FrameLayout.LayoutParams(-1,-1))
        val scroll = ScrollView(activity).apply {
            isFillViewport = true; isVerticalScrollBarEnabled = false; isHorizontalScrollBarEnabled = false
        }
        contentScroll = scroll
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),0,dp(12),dp(20)) }
        cardsBody = body
        if (!settingsPage) {
            column.addView(buildProfile(), LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(6) })
            val timeCard = cardBox()
            clock = android.widget.TextClock(activity).apply {
                format12Hour = "HH:mm:ss"; format24Hour = "HH:mm:ss"; textSize = 36f
                gravity = Gravity.CENTER; setTextColor(themeTitle()); typeface = Typeface.DEFAULT_BOLD
            }
            timeCard.addView(clock, LinearLayout.LayoutParams(-1,-2))
            dateLabel = android.widget.TextClock(activity).apply {
                format12Hour = "yyyy年M月d日 EEEE"; format24Hour = "yyyy年M月d日 EEEE"
                gravity = Gravity.CENTER; textSize = 13f; setTextColor(themeSub())
            }
            timeCard.addView(dateLabel, LinearLayout.LayoutParams(-1,-2))
            lunarLabel = TextView(activity).apply { textSize=12f;gravity=Gravity.CENTER;setTextColor(themeSub());setPadding(0,dp(5),0,0) }
            timeCard.addView(lunarLabel, LinearLayout.LayoutParams(-1,-2))
            refreshLunar()
            timeCard.addView(TextView(activity).apply {
                text = HomeSidePanelSettings.signature(activity); textSize = 13f; setTextColor(themeSub())
                gravity = Gravity.CENTER; setPadding(0,dp(12),0,0); setOnClickListener { showSignatureEditor(this) }
            }, LinearLayout.LayoutParams(-1,-2))
            cardViews["clock"] = timeCard
            (listOf("photo") + SidePanelAppearance.extraPhotos(activity)).forEach { id ->
                if (!prefs.getBoolean(SidePanelAppearance.cardEnabled(id), true)) return@forEach
                val height=dp(SidePanelAppearance.photoHeight(activity,id))
                val photoCard=FrameLayout(activity).apply {
                    background=rounded(if(night()) "#242724" else "#FFFFFF",16)
                    clipToOutline=true;outlineProvider=ViewOutlineProvider.BACKGROUND
                }
                photoCard.addView(TextView(activity).apply { text="＋ 添加 / 更换图片或视频";gravity=Gravity.CENTER;setTextColor(themeSub()) },FrameLayout.LayoutParams(-1,height))
                val media=SidePanelMediaView(activity)
                photoViews[id]=media
                photoCard.addView(media,FrameLayout.LayoutParams(-1,height))
                photoCard.setOnClickListener { pickImage(id) }
                cardViews[id]=photoCard
            }
            loadPhotoMedia()
            val shortcuts = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            shortcutCard(shortcuts); cardViews["shortcuts"] = shortcuts
            val hchat = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
            menuCard(hchat,listOf(
                Row("⚙","模块设置","全部功能开关 · 配置") { MiuixSettingsPage.show(activity); onNavigate() },
                Row("🎨","主题","主界面背景与透明度") { MiuixSettingsPage.showFeature(activity,"background_beauty"); onNavigate() },
                Row("📍","虚拟定位","设置微信定位位置") { MiuixSettingsPage.showFeature(activity,"fake_location"); onNavigate() },
                Row("▢","悬浮底栏","底部导航栏样式") { MiuixSettingsPage.showFeature(activity,"custom_bottom_bar"); onNavigate() }
            ));cardViews["hchat"] = hchat
            if (prefs.getBoolean(SidePanelAppearance.cardEnabled("weather"), false)) {
                cardViews["weather"] = t.mma.hooks.items.homeextension.HomeWeather.card(activity, settings = prefs)
            }
            SidePanelAppearance.order(activity).filter { prefs.getBoolean(SidePanelAppearance.cardEnabled(it), it != "weather") }.forEach { id ->
                val card = cardViews.getValue(id); card.tag = id
                body.addView(card,LinearLayout.LayoutParams(-1,-2).apply { bottomMargin = dp(6) })
                applyCardOpacity(card,id)
                installCardDrag(card)
            }
        } else {
            body.addView(TextView(activity).apply {
                text = "‹  侧边栏设置"; textSize = 19f; setTextColor(themeTitle()); setPadding(0,dp(12),0,dp(18))
                setOnClickListener { if(settingsSubBack?.invoke()!=true) switchPage(false) }
            })
            settingsCleanup = t.mma.ui.miuix.SidePanelSettingsEditor.mount(activity,body,
                backgroundChanged = { applyPanelBackground() },
                pickBackground = { pickImage("background") },
                clearBackground = { SidePanelImages.clear(activity,"background");backgroundBitmap=null;applyPanelBackground() },
                editShortcuts = { showShortcutPicker() },
                effectsChanged = { updateEffects() },
                registerBack = { settingsSubBack=it },
                pageChanged = { entering ->
                    if(entering) { settingsScrollY=scroll.scrollY;scroll.scrollTo(0,0) }
                    else scroll.postDelayed({ scroll.scrollTo(0,settingsScrollY) },240L)
                })
        }
        scroll.addView(body)
        column.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        effects = SidePanelEffects(activity)
        screen.addView(effects,FrameLayout.LayoutParams(-1,-1))
        applyPanelBackground();updateEffects()
    }
    private fun applyCardOpacity(card: View, id: String) {
        val opacity = (prefs.getFloat(SidePanelAppearance.cardOpacity(id),1f).coerceIn(0f,1f)*255).toInt()
        fun surfaces(view: View) {
            // Menu row ripple/base surfaces must fade along with the containing card.
            if (view === card || view is ViewGroup) view.background?.mutate()?.alpha = opacity
            if (view is ViewGroup) for (index in 0 until view.childCount) surfaces(view.getChildAt(index))
        }
        surfaces(card)
        if (SidePanelAppearance.isPhoto(id)) photoViews[id]?.alpha = opacity / 255f
    }
    private fun cardBox() = LinearLayout(activity).apply {
        orientation = LinearLayout.VERTICAL;setPadding(dp(12),dp(16),dp(12),dp(16))
        background = rounded(if(night()) "#242724" else "#FFFFFF",16)
    }
    private fun installCardDrag(card:View) {
        fun bind(view:View) {
            if ((view === card || view.hasOnClickListeners()) && view.getTag(SHORTCUT_TAG) == null) view.setOnLongClickListener {
                dragCard=card;card.alpha=.7f
                contentScroll?.requestDisallowInterceptTouchEvent(true)
                val started = card.startDragAndDrop(android.content.ClipData.newPlainText("side-panel-card",card.tag.toString()),View.DragShadowBuilder(card),card,0)
                if (!started) { dragCard = null; card.alpha = 1f; contentScroll?.requestDisallowInterceptTouchEvent(false) }
                started
            }
            if(view.getTag(SHORTCUT_TAG) == null && view is ViewGroup) for(i in 0 until view.childCount) bind(view.getChildAt(i))
        }
        bind(card)
        cardsBody?.setOnDragListener { _,event ->
            val moving=dragCard
            when(event.action) {
                android.view.DragEvent.ACTION_DRAG_STARTED -> moving != null && shortcutDrag == null
                android.view.DragEvent.ACTION_DRAG_LOCATION -> {
                    val body=cardsBody
                    if(moving!=null && body!=null){
                        val candidates=(0 until body.childCount).map{body.getChildAt(it)}
                        val target=candidates.firstOrNull{event.y < it.top+it.height/2f} ?: candidates.lastOrNull()
                        if(target!=null && target!==moving){val index=body.indexOfChild(target);body.removeView(moving);body.addView(moving,index)}
                        val scroll=contentScroll
                        if(scroll!=null){val y=event.y-scroll.scrollY;if(y<dp(56))scroll.smoothScrollBy(0,-dp(24)) else if(y>scroll.height-dp(56))scroll.smoothScrollBy(0,dp(24))}
                    };true
                }
                android.view.DragEvent.ACTION_DROP -> {
                    cardsBody?.let { body -> prefs.edit().putString(SidePanelAppearance.ORDER,(0 until body.childCount).joinToString(","){body.getChildAt(it).tag.toString()}).apply() };true
                }
                android.view.DragEvent.ACTION_DRAG_ENDED -> {
                    cardsBody?.let { body -> prefs.edit().putString(SidePanelAppearance.ORDER,(0 until body.childCount).joinToString(","){body.getChildAt(it).tag.toString()}).apply() }
                    moving?.alpha=1f;dragCard=null;contentScroll?.requestDisallowInterceptTouchEvent(false);true }
                else -> true
            }
        }
    }

    private fun buildProfile(): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(18), dp(16), dp(18))

        }
        val avatarWrap = FrameLayout(activity).apply {
            clipChildren = false; clipToPadding = false
        }
        val avatar = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = oval("#D0D0D0")
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
        }
        profileAvatar = avatar
        avatarBitmap?.let { avatar.setImageBitmap(it) }
        avatarWrap.addView(avatar, FrameLayout.LayoutParams(-1, -1).apply { setMargins(dp(7),dp(7),dp(7),dp(7)) })
        avatarEffects = AvatarHeaderEffect(avatarWrap)
        avatarWrap.foreground = avatarEffects
        avatarWrap.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> updateEffects() }
         card.addView(avatarWrap, LinearLayout.LayoutParams(dp(68), dp(68)))
        val text = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), 0, 0, 0) }
        text.addView(TextView(activity).apply { this.text = accountName(); textSize = 18f; setTextColor(themeTitle()); typeface = Typeface.DEFAULT_BOLD; maxLines = 1 })
        text.addView(TextView(activity).apply {
            this.text = "● 在线"; textSize = 12f; setTextColor(Color.rgb(7,193,96)); setPadding(0,dp(5),0,0)
        })
        card.addView(text, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(SidePanelSettingsIcon(activity).apply {
            contentDescription = "侧边栏设置"; setOnClickListener { switchPage(true) }
        }, LinearLayout.LayoutParams(dp(40), dp(48)))
        return card
    }

    private val SHORTCUT_TAG get() = 0x7f0f6e11
    private fun shortcutCard(body: LinearLayout) {
        val values=HomeSidePanelSettings.shortcuts(activity)
        values.chunked(4).forEachIndexed { rowIndex,items ->
            val row=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL }
            items.forEachIndexed { index,item ->
                val tile=LinearLayout(activity).apply {
                    orientation=LinearLayout.VERTICAL;gravity=Gravity.CENTER
                    setPadding(dp(2),dp(10),dp(2),dp(10))
                    background=ripple(if(night()) "#242724" else "#FFFFFF")
                    setTag(SHORTCUT_TAG,item.id)
                    setOnClickListener {
                        if(!HomeSidePanelPageLauncher.openShortcut(activity,item)) toast("无法打开「${item.title}」")
                        else { close(false); if(item!=HomeSidePanelSettings.Shortcut.CLEAR_UNREAD) onNavigate() }
                    }
                    setOnLongClickListener {
                        shortcutDrag=item
                        val started=startDragAndDrop(android.content.ClipData.newPlainText("panel-shortcut",item.id),View.DragShadowBuilder(this),item,0)
                        if(!started) shortcutDrag=null
                        started
                    }
                    setOnDragListener { _,event ->
                        val moving=shortcutDrag
                        when(event.action) {
                            android.view.DragEvent.ACTION_DRAG_STARTED -> moving!=null
                            android.view.DragEvent.ACTION_DROP -> {
                                if(moving!=null && moving!=item) {
                                    val order=HomeSidePanelSettings.shortcuts(activity).toMutableList()
                                    order.remove(moving);order.add(order.indexOf(item).coerceAtLeast(0),moving)
                                    HomeSidePanelSettings.saveShortcuts(activity,order)
                                };true
                            }
                            android.view.DragEvent.ACTION_DRAG_ENDED -> {
                                if(shortcutDrag!=null) { shortcutDrag=null;body.post { if(!settingsPage && opened) { body.removeAllViews();shortcutCard(body);applyCardOpacity(body,"shortcuts");installCardDrag(body) } } };true
                            }
                            else -> moving!=null
                        }
                    }
                }
                tile.addView(SidePanelShortcutIcon(activity,item),LinearLayout.LayoutParams(dp(26),dp(26)))
                tile.addView(TextView(activity).apply { text=item.title;textSize=11f;gravity=Gravity.CENTER;setTextColor(themeTitle());setPadding(0,dp(6),0,0);maxLines=1 },LinearLayout.LayoutParams(-1,-2))
                row.addView(tile,LinearLayout.LayoutParams(0,dp(82),1f).apply { if(index>0) leftMargin=dp(5) })
            }
            body.addView(row,LinearLayout.LayoutParams(-1,-2).apply { if(rowIndex>0) topMargin=dp(5) })
        }
    }
    private data class Row(val icon: String, val title: String, val subtitle: String, val click: () -> Unit)

    private fun menuCard(parent: LinearLayout, rows: List<Row>) {
        val card = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; background = rounded(if (night()) "#242724" else "#FFFFFF", 16); clipToOutline = true; outlineProvider = ViewOutlineProvider.BACKGROUND; elevation = dp(2).toFloat() }
        rows.forEachIndexed { index, rowData ->
            if (index > 0) card.addView(View(activity).apply { setBackgroundColor(if (night()) color("#14FFFFFF") else color("#08000000")) }, LinearLayout.LayoutParams(-1, 1).apply { leftMargin = dp(62) })
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(12), dp(14), dp(12)); isClickable = true; isFocusable = true; background = RippleDrawable(ColorStateList.valueOf(if (night()) color("#33FFFFFF") else color("#1F000000")), null, android.graphics.drawable.ColorDrawable(Color.WHITE)); setOnClickListener { rowData.click() } }
            row.addView(TextView(activity).apply { text = rowData.icon; gravity = Gravity.CENTER; textSize = 17f; background = rounded(if (night()) "#227FBF90" else "#182F8A4E", 11) }, LinearLayout.LayoutParams(dp(38), dp(38)))
            val labels = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, dp(6), 0) }
            labels.addView(TextView(activity).apply { text = rowData.title; textSize = 14.5f; setTextColor(themeTitle()); typeface = Typeface.create("sans-serif-medium", Typeface.BOLD) })
            labels.addView(TextView(activity).apply { text = rowData.subtitle; textSize = 11.5f; setTextColor(themeSub()); setPadding(0, dp(2), 0, 0) })
            row.addView(labels, LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(TextView(activity).apply { text = "›"; textSize = 18f; setTextColor(themeSub()) })
            card.addView(row)
        }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2))
    }

    private fun showSignatureEditor(target: TextView) {
        val input = EditText(activity).apply { setText(HomeSidePanelSettings.signature(activity)); setSingleLine(true); textSize = 15f; setTextColor(themeTitle()); background = rounded(if (night()) "#242724" else "#FFFFFF", 10); setPadding(dp(12), 0, dp(12), 0) }
        val box = dialogBox("侧栏签名", "显示在时间日期卡片下方", input)
        addDialogActions(box.first, box.second) { HomeSidePanelSettings.saveSignature(activity, input.text.toString()); target.text = input.text.toString().trim().ifEmpty { HomeSidePanelSettings.DEFAULT_SIGNATURE }; toast("签名已保存") }
    }

    private fun showShortcutPicker() {
        val selected = HomeSidePanelSettings.shortcuts(activity).toMutableList()
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(18), dp(18), dp(16)); background = rounded(if (night()) "#1A1C1A" else "#F7F8F6", 20) }
        root.addView(TextView(activity).apply { text = "编辑快捷"; textSize = 18f; setTextColor(themeTitle()); typeface = Typeface.DEFAULT_BOLD })
        root.addView(TextView(activity).apply { text = "最多选择 ${HomeSidePanelSettings.MAX_SHORTCUTS} 项"; textSize = 12f; setTextColor(themeSub()); setPadding(0, dp(4), 0, dp(12)) })
        val dialog = Dialog(activity).apply { requestWindowFeature(Window.FEATURE_NO_TITLE); setContentView(root); setCanceledOnTouchOutside(true) }
        HomeSidePanelSettings.Shortcut.entries.forEach { shortcut ->
            val line = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(12), dp(10), dp(8), dp(10)); isClickable = true; background = ripple(if (night()) "#242724" else "#FFFFFF") }
            line.addView(SidePanelShortcutIcon(activity,shortcut), LinearLayout.LayoutParams(dp(28), dp(28)).apply { rightMargin=dp(6) })
            line.addView(TextView(activity).apply { text = "${shortcut.title}\n${shortcut.subtitle}"; textSize = 14f; setTextColor(themeTitle()) }, LinearLayout.LayoutParams(0, -2, 1f))
            val check = TextView(activity).apply { textSize = 18f; setTextColor(themeAccent()) }
            line.addView(check, LinearLayout.LayoutParams(dp(28), dp(28)))
            fun update() { check.text = if (shortcut in selected) "✓" else "" }
            update()
            line.setOnClickListener {
                if (shortcut in selected) { if (selected.size > 1) selected.remove(shortcut) else toast("至少保留 1 个快捷") }
                else if (selected.size < HomeSidePanelSettings.MAX_SHORTCUTS) selected.add(shortcut) else toast("最多选择 ${HomeSidePanelSettings.MAX_SHORTCUTS} 项")
                update()
            }
            root.addView(line)
        }
        val actions = LinearLayout(activity).apply { gravity = Gravity.END; setPadding(0, dp(12), 0, 0) }
        actions.addView(TextView(activity).apply { text = "取消"; gravity = Gravity.CENTER; setPadding(dp(18), dp(10), dp(18), dp(10)); setOnClickListener { dialog.dismiss() } })
        actions.addView(TextView(activity).apply { text = "保存"; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = rounded(themeAccent(), 10); setPadding(dp(18), dp(10), dp(18), dp(10)); setOnClickListener { HomeSidePanelSettings.saveShortcuts(activity, selected); dialog.dismiss(); rebuild() } }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        root.addView(actions)
        dialog.show(); dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .86f).toInt(), -2)
    }

    private fun dialogBox(title: String, subtitle: String, input: View): Pair<LinearLayout, Dialog> {
        val root = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(18), dp(20), dp(16)); background = rounded(if (night()) "#1A1C1A" else "#F7F8F6", 18) }
        root.addView(TextView(activity).apply { text = title; textSize = 18f; setTextColor(themeTitle()); typeface = Typeface.DEFAULT_BOLD })
        root.addView(TextView(activity).apply { text = subtitle; textSize = 12f; setTextColor(themeSub()); setPadding(0, dp(5), 0, dp(14)) })
        root.addView(input, LinearLayout.LayoutParams(-1, dp(48)))
        val dialog = Dialog(activity).apply { requestWindowFeature(Window.FEATURE_NO_TITLE); setContentView(root); setCanceledOnTouchOutside(true) }
        dialog.show(); dialog.window?.setBackgroundDrawableResource(android.R.color.transparent); dialog.window?.setLayout((activity.resources.displayMetrics.widthPixels * .82f).toInt(), -2)
        return root to dialog
    }

    private fun addDialogActions(root: LinearLayout, dialog: Dialog, save: () -> Unit) {
        val actions = LinearLayout(activity).apply { gravity = Gravity.END; setPadding(0, dp(14), 0, 0) }
        actions.addView(TextView(activity).apply { text = "取消"; setPadding(dp(18), dp(10), dp(18), dp(10)); setOnClickListener { dialog.dismiss() } })
        actions.addView(TextView(activity).apply { text = "保存"; setTextColor(Color.WHITE); background = rounded(themeAccent(), 10); setPadding(dp(18), dp(10), dp(18), dp(10)); setOnClickListener { save(); dialog.dismiss() } }, LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(8) })
        root.addView(actions)
    }

    private fun rebuild() { renderPage() }
    private fun onEdgeTouch(event: MotionEvent): Boolean {
        if (!enabled || !edgeEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { if (!opened) prepareMainPage(); downX = event.rawX; lastX = downX; dragging = false; return true }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                if (!opened && dx > dp(8)) {
                    panel.animate().withEndAction(null).cancel()
                    scrim.animate().cancel()
                    panel.visibility = View.VISIBLE
                    opened = true
        refreshLunar()
                    resizeRoot(open = true)
                    root.visibility = View.VISIBLE
                    panel.translationX = -panelWidth.toFloat()
                    scrim.visibility = View.VISIBLE
                    dragging = true
                }
                if (opened && (dragging || dx < -dp(8))) {
                    dragging = true
                    edge.visibility = View.GONE
                    panel.translationX = (panel.translationX + event.rawX - lastX).coerceIn(-panelWidth.toFloat(), 0f)
                    scrim.alpha = .42f * (1f + panel.translationX / panelWidth)
                    lastX = event.rawX
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (opened && dragging) { if (panel.translationX > -panelWidth * .55f) settleOpen() else close(true); dragging = false; return true }
            }
        }
        return true
    }

    private fun onPanelTouch(event: MotionEvent): Boolean {
        if (!opened) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = event.rawX; lastX = downX; dragging = false; return false }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                if (!dragging && dx < -dp(8)) dragging = true
                if (dragging) {
                    panel.translationX = (panel.translationX + event.rawX - lastX).coerceIn(-panelWidth.toFloat(), 0f)
                    scrim.alpha = .42f * (1f + panel.translationX / panelWidth)
                    lastX = event.rawX
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) { if (panel.translationX > -panelWidth * .55f) settleOpen() else close(true); dragging = false; return true }
            }
        }
        return false
    }

    private fun accountName(): String = WeChatApis.account()?.selfName()?.takeIf { it.isNotBlank() } ?: "我"

    private fun settleOpen() {
        opened = true
        refreshLunar()
        if (renderedNight != night()) renderPage()
        updateEffects()
        scrim.visibility = View.VISIBLE
        panel.animate().translationX(0f).setDuration(200L).setInterpolator(PathInterpolator(.4f, 0f, .2f, 1f)).start()
        scrim.animate().alpha(.42f).setDuration(200L).start()
    }
    private fun openUrl(url: String) { runCatching { activity.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))) }.onFailure { toast("无法打开链接") } }
    private fun resizeRoot(open: Boolean) {
        val params = root.layoutParams ?: return
        val targetWidth = if (open) ViewGroup.LayoutParams.MATCH_PARENT else dp(72)
        if (params.width != targetWidth) {
            params.width = targetWidth
            root.layoutParams = params
        }
    }
    private fun toast(message: String) { Toast.makeText(activity, message, Toast.LENGTH_SHORT).show() }
    private fun dp(value: Int): Int = (value * density + .5f).toInt()
    private fun statusBarHeight(): Int { val id = activity.resources.getIdentifier("status_bar_height", "dimen", "android"); return if (id > 0) activity.resources.getDimensionPixelSize(id) else dp(28) }
    private fun night(): Boolean = (activity.resources.configuration.uiMode and 0x30) == 0x20
    private fun color(value: String): Int = Color.parseColor(value)
    private fun themeTitle(): Int = color(if (night()) "#F2F4F2" else "#1C1F1C")
    private fun themeSub(): Int = color(if (night()) "#9AA39A" else "#6B736C")
    private fun themeAccent(): Int = color(if (night()) "#7FBF90" else "#2F8A4E")
    private fun rounded(fill: String, radius: Int, stroke: String? = null): GradientDrawable = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radius).toFloat(); setColor(color(fill)); stroke?.let { setStroke(dp(1), color(it)) } }
    private fun rounded(fill: Int, radius: Int): GradientDrawable = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(radius).toFloat(); setColor(fill) }
    private fun oval(fill: Int): GradientDrawable = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(fill) }
    private fun oval(fill: String): GradientDrawable = oval(color(fill))
    private fun ripple(fill: String): RippleDrawable = RippleDrawable(ColorStateList.valueOf(if (night()) color("#33FFFFFF") else color("#1F000000")), rounded(fill, 16), null)
}
