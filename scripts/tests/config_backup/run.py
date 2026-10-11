#!/usr/bin/env python3
"""Compile the production result bridge with JVM stubs; no Gradle/APK/device changes."""
from pathlib import Path
import tempfile, subprocess, os
repo = Path(__file__).resolve().parents[3]
cache = Path.home() / '.gradle/caches/modules-2/files-2.1'
def jar(group, name, version):
    return next((cache/group/name/version).glob('*/'+name+'-'+version+'.jar'))
version = os.environ.get('KOTLIN_VERSION', '2.4.0')
stdlib = jar('org.jetbrains.kotlin', 'kotlin-stdlib', version)
compiler = [jar('org.jetbrains.kotlin', 'kotlin-compiler-embeddable', version), stdlib,
    jar('org.jetbrains.kotlin', 'kotlin-reflect', '2.3.20'),
    jar('org.jetbrains.kotlin', 'kotlin-script-runtime', version),
    jar('org.jetbrains.kotlinx', 'kotlinx-coroutines-core-jvm', '1.8.0'),
    jar('org.jetbrains', 'annotations', '13.0')]
sources = {
'Activity.kt': '''package android.app
import android.content.Intent
open class Activity {
 var isFinishing=false; var isDestroyed=false; var launches=0; var failStarts=0
 open fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) {}
 fun startActivityForResult(intent:Intent,code:Int) { launches++; if(failStarts>0) {failStarts--;error("start failed")} }
 companion object { const val RESULT_OK=-1; const val RESULT_CANCELED=0 }
}
''',
'Intent.kt': '''package android.content
import android.net.Uri
class Intent(val action:String="") {
 var type:String=""; var data:Uri?=null
 fun addCategory(s:String) {} ;fun putExtra(k:String,v:String) {}; fun addFlags(v:Int) {}
 companion object {
 const val ACTION_CREATE_DOCUMENT="create";const val ACTION_OPEN_DOCUMENT="open";const val ACTION_GET_CONTENT="get"
 const val CATEGORY_OPENABLE="openable";const val EXTRA_TITLE="title"
 const val FLAG_GRANT_WRITE_URI_PERMISSION=2;const val FLAG_GRANT_READ_URI_PERMISSION=1
 fun createChooser(i:Intent,title:String)=i
 }
}
''',
'Uri.kt': 'package android.net\nclass Uri\n',
'Toast.kt': '''package android.widget
import android.app.Activity
class Toast {
 fun show() { shown++ }
 companion object { const val LENGTH_SHORT=0;const val LENGTH_LONG=1;var shown=0
 fun makeText(a:Activity,s:String,d:Int)=Toast() }
}
''',
'Xposed.kt': '''package de.robv.android.xposed
import java.lang.reflect.Method
open class XC_MethodHook {
 class MethodHookParam(val thisObject:Any,val args:Array<Any?>)
 protected open fun afterHookedMethod(param:MethodHookParam) {}
 fun fire(p:MethodHookParam)=afterHookedMethod(p)
}
object XposedBridge {
 val hooks=linkedMapOf<Method,MutableList<XC_MethodHook>>()
 fun hookMethod(m:Method,c:XC_MethodHook) { hooks.getOrPut(m){mutableListOf()}.add(c) }
 fun result(a:Any,code:Int,result:Int,data:android.content.Intent?) {
   hooks.entries.toList().reversed().filter { it.key.declaringClass.isInstance(a) }.forEach {
     it.value.toList().forEach { h -> h.fire(XC_MethodHook.MethodHookParam(a,arrayOf(code,result,data))) }
   }
 }
}
''',
'HLog.kt': '''package t.mma.utils
object HLog { fun e(s:String,e:Throwable) {} }
''',
'Backup.kt': '''package t.mma.ui.miuix
import android.app.Activity
import android.content.Intent
import android.net.Uri
internal const val CONFIG_EXPORT_REQUEST_CODE=100
internal const val CONFIG_IMPORT_REQUEST_CODE=101
internal fun Intent.preferSystemDocumentsUi(a:Activity)=this
internal object HchatConfigBackup {
 var exports=0;var imports=0;var count=10
 fun exportToUri(a:Activity,u:Uri):Int {exports++;return count}
 fun importFromUri(a:Activity,u:Uri):Int {imports++;return count}
}
''',
'Regression.kt': '''package t.mma.ui.miuix
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import de.robv.android.xposed.XposedBridge
open class A:Activity() { override fun onActivityResult(r:Int,c:Int,d:Intent?) {} }
open class B:A() { override fun onActivityResult(r:Int,c:Int,d:Intent?) {} }
open class C:B() { override fun onActivityResult(r:Int,c:Int,d:Intent?) {} }
class D:C() { override fun onActivityResult(r:Int,c:Int,d:Intent?) {} }
fun main() {
 val a=D();val other=D();var refreshed=0
 fun result(owner:Activity=a,code:Int=100,status:Int=-1,data:Intent?=Intent().apply{this.data=Uri()}) {
   XposedBridge.result(owner,code,status,data)
 }
 ConfigImportExportBridge.launchExport(a)
 check(XposedBridge.hooks.size==5 && XposedBridge.hooks.values.all{it.size==1})
 result(other);result(code=999);check(HchatConfigBackup.exports==0)
 result();result();check(HchatConfigBackup.exports==1 && Toast.shown==1)
 ConfigImportExportBridge.launchImport(a){refreshed++}
 result(code=101);result(code=101)
 check(HchatConfigBackup.imports==1 && refreshed==1 && Toast.shown==2)
 check(XposedBridge.hooks.values.all{it.size==1})
 ConfigImportExportBridge.launchImport(a){refreshed++}
 result(code=101,status=0);result(code=101)
 check(HchatConfigBackup.imports==1 && refreshed==1)
 ConfigImportExportBridge.launchExport(a);result(data=null);result()
 check(HchatConfigBackup.exports==1)
 ConfigImportExportBridge.launchImport(a){refreshed++}
 result(code=101,data=Intent());result(code=101)
 check(HchatConfigBackup.imports==1)
 a.failStarts=1;ConfigImportExportBridge.launchImport(a){refreshed++}
 result(code=101);check(HchatConfigBackup.imports==2 && refreshed==2)
 a.failStarts=2;ConfigImportExportBridge.launchImport(a){refreshed++}
 val afterFailure=Toast.shown;result(code=101);check(HchatConfigBackup.imports==2)
 ConfigImportExportBridge.launchExport(a);result();check(HchatConfigBackup.exports==2)
 a.failStarts=1;ConfigImportExportBridge.launchExport(a);result();check(HchatConfigBackup.exports==2)
 ConfigImportExportBridge.launchExport(a)
 val before=a.launches;ConfigImportExportBridge.launchExport(a);ConfigImportExportBridge.launchImport(a){}
 check(a.launches==before);result();check(HchatConfigBackup.exports==3)
 HchatConfigBackup.count=0;val t=Toast.shown
 ConfigImportExportBridge.launchImport(a){refreshed++};result(code=101)
 check(Toast.shown==t+1 && refreshed==2)
 check(afterFailure>2)
 // A finished owner must not permanently prevent a new operation.
 ConfigImportExportBridge.launchExport(a);a.isFinishing=true
 ConfigImportExportBridge.launchExport(other);result(a);check(HchatConfigBackup.exports==3)
 result(other);check(HchatConfigBackup.exports==4)
 HchatConfigBackup.count=10;val beforeRefreshFailure=Toast.shown
 ConfigImportExportBridge.launchImport(other){error("refresh failed")}
 result(other,code=101);result(other,code=101)
 check(HchatConfigBackup.imports==4 && Toast.shown==beforeRefreshFailure+1)
 println("PASS: 5-level callback chain executes backup/refresh/Toast once; wrong owner/code, duplicate results, cancellation, missing URI, fallback/failure, repeated launch, failed backup and finished owner")
}
'''
}
with tempfile.TemporaryDirectory(prefix='hchat-config-backup-') as temp:
    root=Path(temp)
    for name,text in sources.items(): (root/name).write_text(text)
    production=repo/'app/src/main/java/t/mma/ui/miuix/ConfigImportExportBridge.kt'
    target=root/'tests.jar'
    subprocess.run(['java','-Xmx256m','-cp',os.pathsep.join(map(str,compiler)),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect',
        '-classpath',str(stdlib),'-d',str(target),str(production),*map(str,root.glob('*.kt'))],check=True,timeout=90)
    subprocess.run(['java','-cp',str(target)+os.pathsep+str(stdlib),
        't.mma.ui.miuix.RegressionKt'],check=True,timeout=30)
