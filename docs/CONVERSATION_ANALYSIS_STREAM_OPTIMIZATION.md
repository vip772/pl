# 群聊分析总结响应优化

## 范围与原因

原generateStreaming实际发送stream=false、readText等待整篇响应，onUpdate未被调用；采样后每条消息重复解析相同成员昵称。本次保持日期范围、采样上限和均匀抽样算法，优化正文呈现与昵称准备开销。

## 实现

- 请求stream=true、声明Accept为text/event-stream与application/json。SSE解析多行data、CRLF、心跳、[DONE]和finish_reason；增量content累计后回主线程。
- 第一段正文立即交付，随后最快每250ms生成一次显示快照，避免按每个token重建报告View；最终正文由原完成回调完整覆盖。该节流不是设备帧率或内存收益证明。
- 成功结束要求[DONE]或finish_reason；无完成标记的异常断流报错，不能将半篇当成功并启用截图。取消与关闭继续使用请求token、连接断开及页面所有权隔离。
- 对明确返回application/json的接口保留整篇JSON解析；不自动重发请求，避免额外费用或重复分析。只支持非流式且拒绝stream=true的接口仍需服务兼容，未擅自宣称通用兼容。
- 成员昵称用单次请求内Map复用，包括未知昵称回退。Map随请求结束释放，不持久化、不跨群缓存。过期排队请求在取数前校验token退出。

## 验证

- `scripts/test_analysis_stream.py`退出0：真实SSE解析器及生产昵称规范化代码在JVM执行；管道流验证首段回调早于EOF，覆盖中文、CRLF、多行、心跳、完成、断流、取消和解析异常；1000个模拟片段被节流为4次快照；同一成员3000条消息昵称解析1次。JSON路径及UI回调接线为源码契约检查，不是真实HTTP接口验收。
- `scripts/test_analysis_period_linkage.py`退出0：日期边界、查询隔离、生产取数/采样/空数据拦截继续通过。
- `scripts/test_analysis_page_lifecycle.py`退出0：关闭、替换、取消和页面隔离回归通过。
- `/tmp/pl-period-sdk.py`包含新增流解析器，完整报告/AI相关代码直接SDK与已有ABI编译通过。未运行Gradle，不代表Android/R8构建。
- 日志：`/tmp/pl-speed-stream.log`、`/tmp/pl-speed-period.log`、`/tmp/pl-speed-life.log`、`/tmp/pl-speed-sdk.log`。

## 剩余边界

- 仍先查询所选时段全部文本再均匀采样，未修改微信数据库API或全局游标策略；大时段取数仍可能慢。
- 未降低用户配置的消息量、未缩短总结模板，模型读取输入与整篇推理耗时仍取决于服务端。流式优化的是首段可见等待，不保证总生成时间减少。
- 未请求真实AI、未读取密钥、未安装APK，无实机首字/全篇耗时或网络兼容性数据；需新版统一验收。
- 仅修改群聊分析AI文件并新增流解析器/专项测试/本文档。既有日期联动、绘图/弹窗及右侧语音修复均保留。未提交、未推送。
