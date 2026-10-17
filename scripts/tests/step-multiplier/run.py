from pathlib import Path
import subprocess,tempfile
r=Path(__file__).resolve().parents[3]
s=(r/'app/src/main/java/t/mma/hooks/items/stepmultiplier/StepMultiplierFeature.kt').read_text()
a=s.index('object StepMultiplierSettings');b=s.index('/** Verified',a)
code=s[a:b]+'''
fun main(){
 check(StepMultiplierSettings.multiply(10,10,true)==100)
 for(factor in 1..10) for(original in listOf(1,10,100,10000,90000)) {
  check(StepMultiplierSettings.multiply(original,factor,true)==original*factor)
  check(StepMultiplierSettings.multiply(original,factor,false)==original)
 }
 check(StepMultiplierSettings.multiply(123,1,true)==123)
 check(StepMultiplierSettings.multiply(0,10,true)==0)
 check(StepMultiplierSettings.multiply(-1,10,true)==-1)
 check(StepMultiplierSettings.multiply(10,99,true)==100)
 check(StepMultiplierSettings.multiply(10,-1,true)==10)
 check(StepMultiplierSettings.multiply(Int.MAX_VALUE,10,true)==Int.MAX_VALUE)
 check(StepMultiplierSettings.multiply(Int.MAX_VALUE/10,10,true)==(Int.MAX_VALUE/10)*10)
 println("PASS: 步数倍增1-10倍、开关、零负值、非法倍率及整数溢出边界")
}
'''
lib=Path('/tmp/pl-kotlin-libs');stdlib=next(lib.glob('kotlin-stdlib-[0-9]*.jar'))
with tempfile.TemporaryDirectory() as t:
 p=Path(t);(p/'Test.kt').write_text(code)
 subprocess.run(['java','-Xmx256m','-cp',':'.join(map(str,lib.glob('*.jar'))),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(stdlib),'-d',str(p/'test.jar'),str(p/'Test.kt')],check=True,timeout=40)
 subprocess.run(['java','-cp',f'{p}/test.jar:{stdlib}','TestKt'],check=True,timeout=10)
assert 'param.args[4]=' in s and 'version!="8.0.78"' in s
assert 'CATEGORY_ENHANCE,true' in s
assert '.register(t.mma.hooks.items.stepmultiplier.StepMultiplierFeature())' in (r/'app/src/main/java/t/mma/hooks/core/FeatureRegistry.kt').read_text()
assert 'StepMultiplierMiuixPage(context,provider,onBack)' in (r/'app/src/main/java/t/mma/ui/miuix/MiuixSettingsPagePart03.kt').read_text()
print('PASS: 已确认第5参数、8.0.78版本限制、增强/新功能注册及设置路由')