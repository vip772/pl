# 13746d9聊天返回渲染核查

输入：15:13至15:14运行日志和匹配mapping，已有8078定向Smali。

returnComplete在15:13:42.413/.436以及15:14:37.291等时刻命中，bars=0/0、target=WECHAT。不能将失败归因于返回刷新未执行。此前WXFRAME总额度在小程序下拉期间耗尽，本文件没有返回完成后的完整绘制快照。

APK NewChattingTabUI.g：主页View.draw(Canvas)生成字段s Bitmap，赋给资源0x7f0a541c的ImageView；tag绑定真实主页，再切换真实主页和截图View可见性。f(Z)有隐藏截图、显示主页、退出动画/无动画分支；uf结束隐藏聊天容器。图片覆盖层不由View.background日志反映。

模块setImageSurface在bitmap、slot、opacity、background身份一致时直接返回，未检查Drawable alpha、callback或硬件缓存；目前没有运行证据证明其中哪项失效。图片绘制日志每实例限8次，不能由返回后缺少imageDraw断言未绘制。

本轮仅加入独立返回额度的WXRENDER：恢复前、后、下一帧，检查DecorView、backgroundHost、真实主页(0x7f0a3e2d)、截图ImageView(0x7f0a541c)、会话列表及最多6级父链，记录背景alpha/bounds/callback、image drawable/imageAlpha/tag、visible/alpha/位置/尺寸/layerType及bitmap身份。最多4次返回回调，不依赖WXFRAME总额度。WXIMAGE记录限频背景setAlpha变化及调用栈。不改变已有透明化及下拉小程序行为。

验证：独立Kotlin类型检查与git diff --check，无本地Gradle构建。此版是精准返回诊断，尚未完成失败像素的最终归因，不能宣称修复。