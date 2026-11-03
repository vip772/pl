# 天气与朋友圈卡片针对性修复（2026-11-03）

## 范围与现场
- 仓库 `/root/pl`，分支 `dev`，修改前 HEAD `08d55e1`，修改前工作区干净。
- 用户要求：定位图标在地区名称左侧；朋友圈开启卡片后应能看到独立卡片。禁止本地构建。
- 设备只读包信息：微信 `8.0.78` / `3180`。本轮以当前设备版本核查，不扩展或声称其它版本兼容。

## 1. 天气（代码修复）
`HomeWeather.render` 原先先加入地区/更新时间，再把 ImageButton 追加到整个 heading 末尾，直接导致截图中的右侧图标。现在新增地区横排，依次加入按钮和地区文字，再按标准/紧凑布局放置更新时间。首页与侧边栏调用同一实现；保留点击刷新、禁用与清理逻辑。

## 2. 朋友圈（代码修复及依据）
### 已证实的缺陷
1. 原 `MomentsCardDrawable` 只画白色圆角矩形，两侧及底部间隙透明；截图2的时间线背景同为白色，默认关闭描边时圆角和2dp间距没有底色对比。新背景先画完整条目的浅灰底色，再画白色卡片；深色对应深底和较浅卡片。没有修改列表、头像、正文、图片、评论区或点击监听。
2. 现代入口 `fk4.m2.h(vv5.s0,lk4.b,int):boolean` 的返回值不是“绑定成功”。本轮从APK DEX重新定向反汇编，确认它调用 `p(lk4.b)`，该方法含原生 `isItemChanged` 标记，结果经 `v13` / `v18` 返回。旧代码的 `param.result != true` 会跳过未变化条目的正常绑定；现仅排除关闭/异常，不再排除 false。

### 原生结构和签名核对
- 旧列表 `BaseTimeLineItem.t(Activity,BaseViewHolder,int,et,SnsInfo):View` 创建 `cuf/cug` 根视图；holder.h 指向根，`n(BaseViewHolder,int,SnsInfo,TimeLineObject,int,et,rt):void` 绑定。
- 资源 `cuf/cug` 为 RelativeLayout，首个子项是 AdFrameLayout，内容 padding 在该子项；此次不包装/移动这些子项。
- 现代 `fk4.b2` 继承 LinearLayout，`fk4.m2` 继承 b2，已有 `getDivider`；调用方 `com.tencent.mm.plugin.sns.ui.improve.component.y0.h(vv5.s0,vv5.c,int,int,boolean,List):void` 经接口 `fk4.w0.h` 绑定。
- 本次未替换 Hook 入口、未加类名猜测或全局 View/滚动钩子。

### 可复核证据
APK `/root/menu-research/wechat8078-current/wechat8078.apk` 中 classes6.dex 与 `/tmp/wechat8078_dex/classes6.dex` SHA256 均为：
`e9a547606fdc471cb73dc2e33697e6be20488a677af99e8a3dfe7a3f1bb988bf`。
本轮使用 baksmali 从该 DEX 定向导出到 `/tmp/pl-card-native-fresh`，没有使用构建工具：
- `fk4/m2.smali` SHA256 `7869e923ee8c0ea5920c2051b6bf5d29e4fbedc193f505d4f53297a58373d561`。
- `com/tencent/mm/plugin/sns/ui/improve/component/y0.smali` SHA256 `a48520f75b451c0fcc3621da2a0da8ffc207625dede16289a8334b7589f4fcbf`。
旧解码资源位于 `/sdcard/Download/wechat8078_decoded/res/layout/cuf.xml`、`cug.xml`；不把该目录下历史反汇编替代本次新导出的方法返回值证据。

## 验证结果和限制
- `python3 scripts/test_weather_moments_surface.py --native-dir /tmp/pl-card-native-fresh` 通过：共享布局顺序、原刷新动作、现代false绑定放行、底色绘制顺序、2dp几何、绘制阶段无shader分配、既有恢复/192项上限保留，以及上述原生签名/返回值依据。此脚本是静态/证据检查，不执行Android代码。
- 更新 `scripts/test_moments_card.py` 的Canvas桩和明暗底色断言；由于它调用Kotlin编译器，本轮不执行，仅做Python语法解析。
- 未运行Gradle、SDK/Kotlin/Java编译、打包、安装、实机视觉/滚动/内存测试；不能声称截图现象已实机消失，也不能把这两个源码缺陷当作已证实的运行时唯一原因。
- 代码修复2/2；实机验收0/2。后续统一验收：首页/侧栏地区图标及定位刷新，朋友圈无描边/描边、明暗主题、滚动复用和关闭恢复。
- 本任务与资源生命周期七阶段的完成判定无关，不推进其进度。
