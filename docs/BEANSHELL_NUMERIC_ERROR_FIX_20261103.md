# BeanShell 数字转换异常修复记录

## 范围与证据

- 历史崩溃：2026-11-03 22:03:13，微信 8.0.78 (3180)，模块 10.9.4 (1173)，进程 20618，线程 `pool-128-thread-2`。
- 附件与手机 crash buffer 一致：`InterpreterError: cannot cast string "" to number`，根因链末端为 `NumberFormatException: empty String`。
- 附件 SHA-256：`f1e2491f00257e50efb65e439ae632d8d032aff9c6f8308fea637ff943d24cce`。
- 未确认具体插件与语句：对应时段的模块运行日志已覆盖，异常栈没有脚本来源。线程池默认序号不能证明某插件泄漏，也不能唯一归属插件。
- `03226a2` 至修复前 `b654ba9` 的 BeanShell、脚本运行时与崩溃处理源码无变化。本修复不归因于此前菜单、卡片、撤回或文字底色改动。

## 修复机制

1. `Primitive.castWrapper` 的非法字符串数字转换改抛 `UtilTargetError`，保留原始 `NumberFormatException`。空值不会被伪装成数字 0；真正的解释器内部不变量异常仍保持原语义。
2. `NameSpace` 的已有变量赋值保留 target error 身份与变量上下文，不再降级为普通 `UtilEvalError`。
3. `BshMethod` 的普通参数和方法回调参数转换保留 target error；`LHS` 数组赋值不再吞掉此类转换失败并继续尝试另一条写入路径。
4. 更新全部受 checked exception 影响的表达式、条件、索引及数学反射边界，携带节点和调用栈转成 `TargetError`。
5. 复用既有脚本 try/catch 和 `This.Handler` 的 TargetError 处理。脚本可按 `NumberFormatException`、`IllegalArgumentException`、`Exception`、`Throwable` 捕获；未捕获的 Runnable 数字转换错误按现有代理策略输出脚本位置与调用链，而不是逃逸到线程池。

不修改全局未捕获异常处理器，不全局吞 Throwable，不对未调查的所有脚本异常承诺隔离。未新增线程、任务、缓存、配置、文件监听或周期日志，无需配置备份迁移。错误输出复用 BeanShell 现有通道，不新增正常执行日志。

## 验证

命令：`python3 scripts/test_beanshell_numeric_error.py`

- 直接 javac 编译全部 146 个生产 BeanShell 源文件，使用 SDK37 android.jar 与既有 dalvik-dx 依赖；不调用 Gradle，不依赖旧解释器 class 作为测试实现。
- 新建临时输出目录，测试结束自动删除，不修改项目构建产物。
- 显式启用 `java -ea`，核心检查另使用显式 `AssertionError`，不依赖默认关闭的断言。
- 97项断言：12种数字基本/包装类型、空字符串与不合法数字、声明/赋值/强制转换/返回/数组/直接方法参数、失败写入保留原值、嵌套 catch/finally、200次重复失败、合法数字/布尔/短路/数组/Map、旧 void 回调返回值兼容、加密快照、Runnable execute/submit 后续任务可运行与线程退出。
- 测试只执行合成脚本，不运行手机真实插件、不调用微信业务、不主动制造手机崩溃。
- 纯 JVM 测试所需的 `--add-opens` 仅用于宿主 JDK 模块反射限制，不进入 Android 产品配置。

## 验收边界

代码与定向验证完成不代表 Android/R8 构建或实机验收。未运行 Gradle、未生成/安装 APK。需要后续修复版构建并由用户统一实机确认脚本行为和微信稳定性；本次不承诺已修复未知插件自身的数据来源错误，也不宣称消除一切脚本崩溃。