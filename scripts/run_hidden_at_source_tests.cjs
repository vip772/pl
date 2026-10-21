// Isolated production-method regressions. No Gradle, APK build, or device sends.
const fs = require('node:fs');
const path = require('node:path');
const os = require('node:os');
const {spawnSync} = require('node:child_process');
const root = path.resolve(__dirname, '..');
const lib = process.argv[2] || process.env.KOTLIN_LIB_DIR;
if (!lib) throw new Error('Pass Kotlin compiler dependency directory');
const out = fs.mkdtempSync(path.join(os.tmpdir(), 'hidden-at-source-'));
function run(cmd, args) {
    const r = spawnSync(cmd, args, {cwd: root, encoding: 'utf8', timeout: 60000});
    process.stdout.write(r.stdout || ''); process.stderr.write(r.stderr || '');
    if (r.error || r.status !== 0) throw r.error || new Error(`${cmd} failed: ${r.status}`);
}
function fixture(name, text) {
    const p = path.join(out, name); fs.mkdirSync(path.dirname(p), {recursive: true}); fs.writeFileSync(p, text); return p;
}
const jars = fs.readdirSync(lib).filter(x => x.endsWith('.jar')).map(x => path.join(lib, x));
const stdlib = jars.find(x => /kotlin-stdlib-[0-9]/.test(x));
const kt = fixture('SourceTest.kt', `package t.mma.hooks.items.hiddenat
fun main() {
    val users = listOf("alice", "bob")
    val source = HiddenAtSource.merge("<msgsource><bizflag>1</bizflag><atuserlist>old</atuserlist></msgsource>", users)
    check(source.contains("<bizflag>1</bizflag>") && !source.contains("old"))
    check(HiddenAtText.users(source.toByteArray(), "") == users)
    check(HiddenAtText.users(HiddenAtSource.merge(null, users).toByteArray(), "") == users)
    check(HiddenAtSource.merge(source, users) == source)
    check(runCatching { HiddenAtSource.merge("invalid", users) }.isFailure)
    check(runCatching { HiddenAtSource.merge(null, listOf("bad,id")) }.isFailure)
    check(HiddenAtSource.merge(null, listOf("notify@all")).contains("notify@all"))
    var now = 0L; val pending = HiddenAtPending { now }
    val mutable = mutableListOf("alice")
    pending.put("g1", 1, mutable); mutable.clear(); pending.put("g2", 1, users)
    check(pending.get("g1", 1) == listOf("alice"))
    check(pending.get("g1", 1) == listOf("alice")) // retries must not consume association
    check(pending.get("g2", 1) == users && pending.get("g3", 1) == null)
    now = 120000; check(pending.get("g1", 1) == null)
    for (i in 1..129) pending.put("g", i.toLong(), users)
    check(pending.get("g", 1) == null && pending.get("g", 129) == users)
    pending.clear(); check(pending.get("g", 129) == null)
    check(runCatching { pending.put("g", 0, users) }.isFailure)
    println("PASS: source preservation, replacement, retry idempotence, group/ID isolation, TTL, capacity, clear")
}`);
const jar = path.join(out, 'source.jar');
run('java', ['-Xmx256m', '-cp', jars.join(path.delimiter), 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
    '-no-stdlib', '-no-reflect', '-classpath', stdlib, '-d', jar,
    'app/src/main/java/t/mma/hooks/items/hiddenat/HiddenAtState.kt',
    'app/src/main/java/t/mma/hooks/items/hiddenat/HiddenAtText.kt', kt]);
run('java', ['-cp', jar + path.delimiter + stdlib, 't.mma.hooks.items.hiddenat.SourceTestKt']);
const api = fs.readFileSync(path.join(root, 'app/src/main/java/t/mma/hooks/api/message/WeChatMessageApi.java'), 'utf8');
const methods = api.slice(api.indexOf('    private Object[] buildSendAppMsgArgs('), api.indexOf('    private Object newMsgIdTalker('));
const sources = [
    fixture('android/util/Pair.java', 'package android.util; public class Pair {}'),
    fixture('dx0/r.java', 'package dx0; public class r {}'),
    fixture('com/tencent/mm/plugin/msg/MsgIdTalker.java', 'package com.tencent.mm.plugin.msg; public class MsgIdTalker {}'),
    fixture('com/tencent/mm/pluginsdk/model/app/k0.java', `package com.tencent.mm.pluginsdk.model.app;
public class k0 {
 public static android.util.Pair K(dx0.r a,String b,String c,String d,String e,byte[] f,String g,String h,String i,com.tencent.mm.plugin.msg.MsgIdTalker j){return null;}
 public static android.util.Pair M(dx0.r a,String b,String c,String d,String e,byte[] f,String g,String h,String i,com.tencent.mm.plugin.msg.MsgIdTalker j,boolean k,String l,String m){return null;}
}`),
    fixture('ApiTest.java', `import java.lang.reflect.*; import java.util.*; import android.util.Pair;
class KavaReflector {
 static List<Method> declaredMethods(Class<?> c){return Arrays.asList(c.getDeclaredMethods());}
 static boolean isStatic(Method m){return Modifier.isStatic(m.getModifiers());}
}
class WeChatApis {
 static String version="8.0.78"; static WeChatApis version(){return new WeChatApis();} String versionName(){return version;}
}
public class ApiTest {
 String extractAppId(String x){return "";} String extractSource(String x){return "";} byte[] cachedThumbData(String x){return null;}
 Object newMsgIdTalker(Class<?> c,String t){return new com.tencent.mm.plugin.msg.MsgIdTalker();}
 ${methods}
 public static void main(String[] args) throws Exception {
  ApiTest api=new ApiTest(); Class<?> owner=com.tencent.mm.pluginsdk.model.app.k0.class;
  Method shortMethod=Arrays.stream(owner.getDeclaredMethods()).filter(m->m.getName().equals("K")).findFirst().get();
  String source="<msgsource><atuserlist>alice</atuserlist></msgsource>";
  Method full=api.explicitAppMsgMethod(shortMethod,source);
  Object[] actual=api.buildSendAppMsgArgs(full,new dx0.r(),"group","xml",source);
  if(!actual[8].equals("") || !actual[12].equals(source) || !actual[3].equals("group") || !actual[10].equals(false))throw new AssertionError();
  if(api.explicitAppMsgMethod(shortMethod,"")!=shortMethod)throw new AssertionError();
  if(api.buildSendAppMsgArgs(shortMethod,new dx0.r(),"g","x","").length!=10)throw new AssertionError();
  try {api.buildSendAppMsgArgs(shortMethod,new dx0.r(),"g","x",source);throw new AssertionError();}catch(IllegalStateException expected){}
  WeChatApis.version="8.0.77";
  try {api.explicitAppMsgMethod(shortMethod,source);throw new AssertionError();}catch(IllegalStateException expected){}
  System.out.println("PASS: actual Java argument methods compile; full overload, p8 empty, p12 source, short no-source path, unknown-version rejection");
 }
}`)
];
run('javac', ['-d', out, ...sources]);
run('java', ['-cp', out, 'ApiTest']);
console.log('Artifacts: ' + out);
