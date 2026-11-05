# 隐藏条目扩展验证记录

日期：2026-11-05。仓库 `/root/pl`，分支 `dev`。用户要求不提交、不推送，保留此前艾特全体未提交改动。

## 本地定向验证

- `python3 scripts/test_extra_entry_visibility.py`：退出码0。
  - PASS four independent switch combinations and idempotent release
  - PASS microphone hidden, keyboard retained, native GONE/INVISIBLE restored
  - PASS computer tags, tablet passthrough, nested i/k snapshots, resume and shared-header restore
  - PASS partial install rollback/retry, retained-listener failure and unsupported host
- `python3 scripts/test_me_entry_visibility.py`：退出码0。
  - PASS partial hook rollback/retry and scope close
  - PASS no hooks/lookup when all off; enabled install attempt; installed/destroyed guard
  - PASS 32 switch combinations, both gray branches, host-hidden preservation and restore
- `python3 scripts/tests/config_backup/archive.py`：退出码0。
- `python3 scripts/test_mention_all_command.py`：退出码0。
- `python3 scripts/test_hidden_at_owner.py`：退出码0。
- `python3 scripts/test_mention_all_pipeline.py`：退出码0。
- `node scripts/run_hidden_at_source_tests.cjs /tmp/pl-kotlin-libs`：退出码0。

以上为真实生产代码的定向 JVM/stub 编译和回归、生产归档导出导入验证；不是 Android/Compose/R8 完整构建或微信实机测试。

## 静态材料指纹

- `smali_classes3/com/tencent/mm/pluginsdk/ui/chat/ChatFooter.smali` SHA-256：`0b29ca75c2de06ff0a5a7305e5d1cb49f38e695e73fd9a6a45b2e0ba13644c52`
- `res/layout/t8.xml` SHA-256：`f819ab191bce2ce74d0f5da4a623a1d357a8d1cf0660c23047f6075533aebb32`
- `smali_classes8/com/tencent/mm/ui/conversation/banner/l0.smali` SHA-256：`7e3d1e08215de32db81e3e9fbf74be965ae4584582994b89ef61a1f92dcccaff`
- `smali_classes8/qb5/b.smali` SHA-256：`67c772e9f5c51b16e9400bcb5d9fa8dd25249c7518c5b7206f96dd718e5176f5`
- `smali_classes11/com/tencent/mm/ui/conversation/MainUI.smali` SHA-256：`b102b8e719243ebaa7311fade9bd7bb9f3f7b6e24e16a3e7fef800522da4c1b7`
- `smali_classes11/com/tencent/mm/ui/conversation/r.smali` SHA-256：`8dfd9733d53e7d0dac7ca6a0d3702a3d0101c4c614b296ed7fa6229f98c96902`

## 边界与待验收

- 当前代码仅对微信8.0.78安装本次 Hook；其他版本未完成横向确认，不宣称兼容。
- 未运行 Gradle，未构建、安装 APK，未做 Android/R8/Compose 全量验证。
- 实机待验收：冷启动、两项单独及同时启停、重新进入聊天与首页、电脑普通/锁定/退出/多设备切换、语音与键盘返回、原生隐藏状态、滚动流畅度。
- 无实机性能/内存数据，不宣称性能或内存改善；不宣称功能整体验收完成。
- 未提交、未推送，HEAD保持 `620c9f30b865718e63bfec21575ae78adb4c2d80`。
- `git diff --check` 通过。

## 右侧麦克风目标修正（本次未提交）

上一版已按用户后续授权提交为 d7da526；用户确认其他功能验收成功，但指出语音隐藏对象错误。上述旧记录为当时历史状态，不代表本次修正版已验收。本次只修正右侧麦克风，不提交、不推送。

- 本次 runtime 验证：退出码0。
  - PASS four independent switch combinations and idempotent release
  - PASS right microphone only; left/emoji/plus unchanged; native visibility restored; shared search inputs excluded
  - PASS computer tags, tablet passthrough, nested i/k snapshots, resume and shared-header restore
  - PASS partial install rollback/retry, retained-listener failure and unsupported host
- 本次 me 验证：退出码0。
- 本次 backup 验证：退出码0。

已覆盖：右侧目标ID/类及聊天祖先校验，左侧/表情/加号不变，共享搜索控件放行，已打开聊天即时启停，原生GONE/INVISIBLE恢复，四组合及电脑提示回归。备份键未变。

- `smali_classes16/com/tencent/mm/pluginsdk/ui/SpeechInputLayout.smali` SHA-256：`d3bd5b62fed79e1b518ed63107f0bd88f2eaa15a927334623ea24114a02c012f`
- `res/layout/ew_.xml` SHA-256：`990d9a16e12cdd03590e77fc12ed4fd6b298d63b6d3625458e2f9212c2b091de`

未运行Gradle、未构建APK或完成Android/Compose/R8全量编译；定向JVM/stub验证不代替实机验收。当前仅8.0.78有确认依据，未扩张其他版本适配。
HEAD保持 d7da5265cf8f003deba20dbf549d2f8142268803；修正版待新版统一实机验收。
