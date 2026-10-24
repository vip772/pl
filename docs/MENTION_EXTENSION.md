# 艾特扩展

增强分组与新功能聚合页共用 `mention_extension` Provider 和设置页。隐藏艾特、表情艾特、艾特高亮的旧独立Provider、页面和路由已移除；原运行模块继续注册安装，不复制Hook，不改变发送或消息渲染行为。

页面提供三个独立开关，并保留艾特高亮颜色编辑、重置及保存；高亮关闭时隐藏颜色设置。直接使用原来的 Hchat_hidden_at_config/enable、Hchat_emoji_at_config/enable、Hchat_mention_highlight_config/mention_highlight_enable 和 mention_highlight_color，无迁移写入、无默认值覆盖。任一子开关开启即在已开启聚合中显示艾特扩展。搜索隐藏艾特、表情艾特、艾特高亮及原高亮关键词均指向新入口。

旧功能类与配置ID用于兼容已有运行状态，不作为独立设置入口。未新增微信内部定位、反射签名或发送逻辑，不扩展版本兼容性结论。验收检查增强/新功能入口一致、旧入口消失、旧配置保留、三个开关独立、高亮颜色保存与隐藏，以及已有能力行为。未操作微信或发送测试消息；完整Compose布局与实机能力待安装验收。
