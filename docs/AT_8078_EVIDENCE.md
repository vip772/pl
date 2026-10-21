# 8.0.78 艾特发送定向证据

用户提供的 wechat8078_decoded 已包含Smali。本次只读取相关文件，按日志字符串扫描DEX常量/方法归属（逐DEX、单进程），未调用全量JADX或apktool解包。提供APK的classes11.dex/classes12.dex与本地既有DEX的CRC一致；目录apktool.yml声明8.0.78/versionCode3180。

- `smali_classes11/com/tencent/mm/storage/f9.smali:19202`：yb(e9)调用Bb(e9, false)，其失败分支直接包含`MsgInfo processAddMsg insert db error`；核心Bb始于849行。
- `smali_classes12/com/tencent/mm/feature/emoji/b0.smali:2222`：sj的六参数发送入口；2427行调用yb获取消息ID；2574行创建c72.y，传入同一ID。
- `smali_classes12/c72/y.smali:130`：d.a.a为pc5.h27，创建pc5.kk0。209行设置p字段；376行附近将项加入h27.e。
- `smali_classes3/pc5/h27.smali`：e为raw LinkedList，无泛型Signature，旧泛型解析必然失败。
- `smali_classes12/pc5/kk0.smali:305`：v5=10；397行附近p通过writer.j(v5, p)写入上行tag10，527行计算对应长度。
- `smali_classes11/com/tencent/mm/storage/e9.smali:4818`：r3设置继承的G（MsgSource）并标记dirty。
- `smali_classes11/im/c8.smali:1037`：b1设置field_content并标记dirty；e9覆盖j读取实际正文。
- `smali_classes12/v51/r0.smali:950`：文本构造设置MsgSource；1131行调用Bb；1805行后doScene读取消息j并写入pc5.pr4.e，随后调用source assembler。

这些证据支持入口、消息时序、正文设置与表情协议字段的当前版本适配。收件端是否接受隐藏艾特仍须双端确认；未据此声明其他微信版本适配。运行时日志只记录阶段、数量和消息ID，不输出正文、群ID或成员名单。

## Type57补充依据

- 提供的geek.apk：GhostAt核心OOoo00o0O0oOoO0O.oOoooOoOoO0OOO0O构造`<title>...<type>57</type>`并调用AppMsg；O000OOOOOoOooo0o数据库分支处理key_ghost_at_on，成功后取消原插入。此结论来自单类反编译和字符串解码；未全量反编译微信。
- k0.smali:1764为M，1849调用U(s0)；true分支返回Pair(0,null)，未提供真实ID；false分支继续构造消息、p8经ma.o写入r3。同步分支返回Pair(code,localId)。本功能仅自身ThreadLocal作用域令U返回false，普通发送不变。
- ev/j.smali:959读取e9.G并在966写入AppMsg原型；源代码上下文包括显式source与assembler两分支。当前实现通过公共API的p8携带原source，不直接Hook通用protobuf.set。
