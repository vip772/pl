# 三段式悬浮标题栏

当前微信8.0.78增加圆形返回、头像与名称胶囊、圆形更多三个独立区域。无副标题或在线人数；群聊保留微信原标题中的成员人数（群名称与人数同一行），单聊保持原聊天名称，不显示原返回区未读数字。使用半透明浅/深色表面和细描边，不是真实背景模糊。

仅在已核对的actionbar_up_indicator、obn标题、obr尾部文字、coy内单一WXActionMenuView点击入口存在时应用；无法确认则恢复旧悬浮整栏，其他版本不沿用混淆资源名。原生子控件不重挂、不替换ID和点击监听，显示层转发performClick；原标题可点击时胶囊转发原点击。关闭/销毁恢复原透明度、无障碍状态和背景。

头像通过项目联系人getAvatarUrl接口在后台获取。连接/读取超时4秒，下载上限2MiB，采样最长边不超过256px，内存缓存上限2MiB。按会话及代次校验异步回调，避免串图；获取不到时保留中性占位，不伪造头像。

验证：Android SDK和API依赖桩定向编译，接入及恢复源码检查。未执行Gradle完整构建或微信实机视觉、点击验收。此前隐藏艾特和消息时间圆角本地修改保持不变。
## 定向定位修正
上一版误用通用Activity的android.R.id.text1。聊天组件smali_classes11/com/tencent/mm/ui/chatting/component/fg.smali的I0在4072行加载0x7f0e0068（ba.xml）；该布局实际使用obn（0x7f0a6b16）显示标题，obr显示尾部文字，osp显示返回未读数，不包含android.R.id.text1。当前删除错误定位，拼接可见obr以保留原生人数。
菜单依据：smali_classes16/com/tencent/mm/ui/ha.smali:4876/5140加载0x7f0e006a（bc.xml），取得WXActionMenuView并调用a绑定；该类构造器163行在自身安装点击监听。fg.Y0(IIILandroid/view/MenuItem$OnMenuItemClickListener;)经Z0传递菜单ID、图标资源和点击监听；8.0.78设置项使用string/cm与raw/actionbar_setting_icon。运行时仅从coy内收集可见、启用且可点击的WXActionMenuView，并要求文字或图标资源核验唯一后转发。
验证：scripts/check_floating_header_8078.py交叉核查Smali/XML与生产定位，Android SDK依赖桩编译通过。未进行新包实机视觉或点击验收，不声明已验证实际显示。

## 聊天状态区顶部占位修复（2026-10-22）
实机 HEADER_STATUS_LAYERS：status inset=129px，ChattingUILayout 顶部 padding=129px，bki/bqg/bm6/bp0 顶部均为 y=286；原生标题栏高157px。该快照采于标题栏重排前。ss.xml 确认标题栏与 bqh 在垂直聊天布局中依次排列；重排移走标题栏后，旧实现仍保留状态栏顶部占位，消息列表无法进入状态区。
现在仅在聊天结构验证且标题栏成功成为覆盖层后清零聊天布局顶部 padding，保存最新非零原值，关闭、脱离窗口或销毁时恢复。左右和底部 padding 保留，重复应用不写布局。保留有界层级诊断，快照改到标题栏重排及顶部修正后调用（布局坐标仍需下一次布局才更新）。
验证：Android 36 SDK 定向编译（项目依赖桩）、8.0.78 Smali/XML 绑定静态检查、输入栏回归、生产顶部占位方法 JVM 回归通过。完整 APK 构建及状态区实际透字效果未实机验收。
