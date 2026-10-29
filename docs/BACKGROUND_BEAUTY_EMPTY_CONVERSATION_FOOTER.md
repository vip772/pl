# 清除未读后短列表／空列表显示主题底色

## 现象与依据

用户六张对比图确认：清除前会话不足一屏时仍显示背景；清除后最后一条会话以下显示主题底色，深色黑、浅色白，完全无会话时区域扩大。不是横栏消失或网络问题。

微信8.0.78 smali_classes16/com/tencent/mm/ui/conversation/ConversationListView.smali：

- getEmptyFooter() 创建LinearLayout，内部View保存为T；外层使用0x7f06000c主题颜色作为背景，并设置id=0x7f0a1058。public.xml确认id名称为bp9。
- 初始化时直接addFooterView(getEmptyFooter())；这个固定页脚不经业务adapter.getView。
- h()（checkEmptyFooter）根据adapter数据和可用高度改变T的LayoutParams.height，最后requestLayout。列表变短时固定页脚占据余下区域。
- 2026-10-29手动采集activity-10s.txt中，bp9外层在0,1377—1440,3271，T高1894px，与最后一条会话以下主题底色吻合。

模块此前通过页面初始扫描和行绑定/preDraw处理透明背景。页面preparePage缓存生效后，原来位于屏外的固定页脚可在清除/筛选后进入可见区，却不触发业务行绑定，因而保留原生主题色。

## 修改

BackgroundBeautyFeature在安装会话列表绑定时，同步处理固定页脚，并安装两个事件入口：getEmptyFooter创建完成、h刷新完成。只读取缓存反射字段T，验证其直接父容器id为bp9，随后将两者交给已有setTransparentSurface管理。

要求列表已注册为微信首页、背景美化开启、微信背景bitmap已加载且未回收；其他页面或关闭美化时不处理。沿用surface快照与restoreSurface，不改变页脚高度、会话数据、列表偏移或主题配置。无整页扫描，无每帧／滚动新增Hook。

精确结构只由本机8.0.78反编译和快照核验；其他版本缺对应APK证据，不声称横向验证完成。方法、T或bp9缺失时不选择其他容器兜底。

## 验证范围

scripts/test_conversation_empty_footer.py提取真实生产方法进行JVM测试，覆盖黑白底色、0/1/4/满屏条目高度、页脚重建、错误父容器、关闭美化、bitmap回收、其他页面、销毁。新增生产方法通过Android36 SDK定向编译，宿主状态与Hook注册使用桩；不是完整类或工程构建。

背景chrome归属回归、64种背景渲染开关组合回归通过。git diff --check通过。未运行Gradle、未构建或安装新APK；修复后深浅模式实机验收尚未完成。

本轮只修改背景美化，未改QuickRead的语音提示处理、分组筛选和网络恢复逻辑。