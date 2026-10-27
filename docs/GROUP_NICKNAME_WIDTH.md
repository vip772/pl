# 解除群成员昵称长度限制

独立开关 group_nickname_width，默认关闭，归属增强并标记新功能。配置 Hchat_group_nickname_width_config / enable，统一备份自动收录；没有新增资源或持久运行缓存。

## 8.0.78 证据

wechat8078_decoded/res/values/styles.xml 的 eq 样式（1528 行起）为聊天昵称设置 wrap_content、maxWidth=240dp 和 singleLine=true。消息布局如 wt.xml 使用 brc / eq，昵称由消息父布局约束可用空间。

smali_classes11/com/tencent/mm/ui/chatting/viewitems/b0.smali：p(h0,gk5.d,e9,String) 是包含 fillingUsername:need getKfInfo 的昵称绑定方法，访问 h0.userTV。gk5.d.x() 返回当前 talker。运行时严格核验微信版本及字段/方法签名。

## 行为和开销

仅对群聊昵称解除像素 maxWidth（设为 Int.MAX_VALUE），保持父布局、单行、省略、文本、颜色、头衔及尾字的原有行为。不强制多行，也不修改正文、气泡或头像。

绑定回调中读取缓存的开关值，关闭时立即返回；开启时使用安装阶段缓存的反射 Field/Method。相同控件连续绑定不重复设置属性。弱键保存原 maxWidth，关闭时主线程恢复；群聊控件复用到私聊时恢复；外部已改写的宽度不强行回滚。开启后消息下次绑定时生效，可重新进入聊天查看。

无全局 TextView Hook，无逐帧/滚动监听，无视图树扫描，无数据库读取，无定时轮询。未验证版本不安装 Hook。

## 验证

- scripts/test_nickname_width.py：生产功能 Android SDK 定向编译；默认关闭、私聊保护、10000 次重复绑定仅一次写属性、控件复用、关闭恢复和外部属性改写保护。
- scripts/check_nickname_width_ui.py：真实 Android/Compose/Miuix 及 Compose 插件定向编译设置页；注册、开关状态统计、路由和搜索静态检查。
- scripts/tests/config_backup/archive.py：真实存储/备份实现配合文件宿主桩完成 enable=true/false 往返、默认关闭与运行缓存排除验证。
- git diff --check。

没有进行完整本地构建、APK 安装、真机显示或帧率验证。按用户要求仅保留本地修改，不提交、不推送。