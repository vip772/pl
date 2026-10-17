# 步数倍增

独立开关默认关闭，开启后显示1-10整数倍率、默认1；设置即时保存。“增强”和“新功能”复用同一个provider和配置，搜索关键词包含微信运动和倍率。

## 8.0.78逆向依据
本地样本 `/sdcard/Download/Operit/wx8078-analysis/wechat-8.0.78.apk` 提取DEX。classes3.dex的 `com.tencent.mm.plugin.sport.model.j` 构造 `(String,String,int,int,int,String,int)`：日志MicroMsg.Sport.NetSceneUploadDeviceStep，请求URI `/cgi-bin/mmoc-bin/hardware/uploaddevicestep`、type=1261。参数3/4分别写入pc5.b27.f/g时间字段，第5参数写入pc5.b27.h步数，并用于UpdateDeviceStepReqEvent.g.a。

只在该构造执行前乘第5参数，不Hook网络重发，不改传感器累计数、不自行发送新请求。负值/零保持，倍率夹在1-10；Long中间乘法，超过Int上限饱和，不产生整数溢出。关闭/1倍后续请求原样处理；不回滚已上报步数，也不主动刷新排行榜。脚本调用项目已有uploadDeviceStep API同样会经过该原生构造，所以也受倍率影响。

无需新增DexKit查询，使用已确认描述符和KavaReflector签名匹配；后台共享调度器安装、失败有限补装，销毁解除Hook。每次构造动态读取配置，无轮询或逐帧任务。仅允许versionName=8.0.78安装，缺少版本信息则等待补装。其它版本未核查，不安装；当前没有覆盖默认全部历史版本，现有样本中只对8.0.78完成了本功能语义逆向。热更新或同名版本差异仍需实机日志和请求结果验证。

## 验证边界
JVM回归覆盖10×10=100、开关、1倍、倍率异常、零负值和溢出边界；静态核对注册、增强/新功能、搜索和开关聚合入口。未运行本地Gradle/Android构建，未在设备上实际步行或上传步数，服务端接受结果/排行更新尚未验证。此次仅本地修改，不提交推送。
