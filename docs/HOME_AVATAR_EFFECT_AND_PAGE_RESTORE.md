# 头像特效与左右切页恢复

基线50b9d0c。用户实机确认加号菜单透明成功，要求恢复上版左右切换效果，并按视频增加头像昵称外围动态效果。

## 切页恢复

BackgroundBeautyFeature恢复c96b1fd版本内容，完整保留50b9d0c新增的AppGrid条目绑定透明和图标处理。删除四页预热表、fragment独立图片和相邻页面挂图逻辑，回到窗口底图切换。头像磁盘缓存、首页标题栏查询减频保留。对比c96b1fd，此文件仅增加已验证的AppGrid处理块。

## 头像特效

参考视频1792044803989.mp4（6.08秒），提取3fps联系表查看：头像昵称外围是圆角胶囊光环，边缘亮线沿轮廓移动，后段云烟不纳入实现。

入口：侧边栏→主题→基础→头像特效。独立开关avatar_effect_enable默认false，不依赖沉浸式背景开关；需要首页侧边栏启用以显示标题栏头像昵称。三色渐变编辑复用ColorPickerRow(threeColorGradient=true)、DynamicRecallGradient颜色归一化；速度复用speedControl及DynamicBadgeSettings的300～5000ms范围，默认2400ms。颜色与速度保存到本功能独立key，不改变动态撤回参数。关闭时不显示无效参数。

新增AvatarHeaderEffect为标题栏根View的foreground，仅绘制淡胶囊轮廓、沿封闭路径移动的高亮弧和多层柔光，无云烟、无位图捕获或布局修改。每帧复用Path/Paint/Matrix，尺寸或颜色变化才重建轮廓与shader。只invalidate局部Drawable；切页隐藏、进入聊天、窗口失焦、onPause、销毁或关闭开关时停止回调。回到首页恢复。保留头像昵称原点击行为。

## 验证

git diff --check通过，独立JVM背景/反馈/侧边手势回归通过；未运行Gradle、本地应用编译或APK构建。现有回归不覆盖新增Android Drawable和Compose页面，最终显示、光环位置及性能待GitHub构建实机验证。实现为视频光环的原生绘制近似，不宣称逐像素还原。

实机关注：左右切页效果是否恢复；加号菜单透明；默认关闭无特效，开启后围住整个头像昵称；三色编辑、恢复默认、速度即时变化；深浅模式；进入聊天返回、切标签、后台回前台；关闭侧边栏或头像特效立即移除动画。