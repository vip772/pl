# 模板子类时间锚点修复（实机诊断驱动）

依据 Hchat-runtime-20261011-213929.log、LSPosed_20261011_213942.zip 及三个会话截图。

## 已确认原因

实际Holder是com.tencent.mm.ui.chatting.viewitems.as，raw type318767153、enabled=true、position=message_bottom。
APK as.smali明确继承cs，初始化调用cs.a；正文bkn沿用cs.b。
上轮仅精确匹配类名cs，漏掉as，实际进入未知卡片评分回退。
日志row8：原生时间br1在0..190，完整卡片外层bkj在190..934；anchor_selected却选中与timeTV同一个br1对象。模块标签module=true最终196..249，落在卡片顶部。这是明确运行证据，不再仅凭截图/类映射推测。
初始0x0的几行出现inserted=false，后来转为true但锚点仍错；持续缺失不能全部归因于初始未附着。

## 修改

- MessageDetailsCardHierarchy.templateHolderClass沿真实继承链定位已验证声明类cs，覆盖as，不新增模糊Hook。
- 从cs自身声明字段b读取LinearLayout正文，防止递归字段查找误选未来子类同名字段；再提升到消息行直接正文分支bkj。
- 分类、已验证Holder禁止回退、绑定等待路径统一使用继承链识别。正文未附着/未测量时保持原有有限布局等待，不降级到原生时间。
- 通用未知卡片候选排除原生timeTV所在直接分支，避免再将时间当内容；已验证完整正文可共享其他分支，仍优先使用正文。
- templateAnchor诊断覆盖as，版本template-inheritance-v2，保留通用坐标诊断供实机确认。
- 不改变时间格式和配置位置，不重挂原生View，不修改网络、会话归属、动态角标或动态撤回。

## 验证

消息布局观察器7项、卡片层级、原始type分类、as/cs真实名称继承链识别与从声明类读取字段测试通过。
预绘制、复用、重附着及清理回归通过，git diff --check通过。
测试模型覆盖继承识别和内容分支，不覆盖完整HchatExtraFeature反射、Android布局规则与每种实机样式；未构建APK或完整编译Android功能，不宣称实机全部修复。
尚未提交推送。

## 实机检查

更新后依次查看微信支付、微信收款助手、云家校，上下滚动和退出重进。message_bottom的模板锚点应为包含bkn的bkj外层，而非br1；标签顶部应在bkj底边之后，需布局完成后的坐标验证。
若持续缺失仍有复现，使用通用诊断区分inserted=false、标签移除、行复用，不把它与已确定的错误锚点混为一谈。
父级仍是主/备用都缺历史群条目，不能猜测恢复；本轮网络138诊断为成功回调，不用旧错误冒充本次复现。