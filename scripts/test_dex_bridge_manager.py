"""Compile and exercise the production DexKit generation/lease manager without Gradle or native loading."""
from pathlib import Path
import subprocess
import tempfile

root = Path(__file__).resolve().parents[1]
libs = Path('/tmp/pl-kotlin-libs')
stdlib = next(libs.glob('kotlin-stdlib-[0-9]*.jar'))
dexkit = Path('/tmp/pl-dexkit.jar')
manager = root / 'app/src/main/java/t/mma/dexkit/DexKitBridgeManager.java'
scheduler = root / 'app/src/main/java/t/mma/hooks/core/DexInstallScheduler.kt'

with tempfile.TemporaryDirectory() as td:
    p = Path(td)
    classes = p / 'classes'
    classes.mkdir()
    (p / 'DexKitBridge.java').write_text(
        'package org.luckypray.dexkit; '
        'public class DexKitBridge { public volatile int closes; public volatile boolean failClose; public synchronized void close(){closes++;if(failClose)throw new IllegalStateException("close failure");} }\n'
    )
    (p / 'Log.kt').write_text(
        'package t.mma.utils\n'
        'object HLog { @JvmStatic fun d(s:String){}; @JvmStatic fun i(s:String){}; @JvmStatic fun w(s:String){}; '
        'fun e(s:String,t:Throwable?=null){} }\n'
    )
    (p / 'Handler.kt').write_text(
        'package android.os\n'
        'class Looper { companion object { fun getMainLooper()=Looper() } }\n'
        'class Handler(l:Looper) { fun removeCallbacks(r:Runnable){}; '
        'fun postDelayed(r:Runnable,d:Long)=true }\n'
    )
    cp = f'{classes}:{stdlib}:{dexkit}'
    compiler = [
        'java', '-Xmx256m', '-cp', ':'.join(map(str, libs.glob('*.jar'))),
        'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler', '-nowarn', '-no-stdlib',
        '-no-reflect', '-classpath', cp, '-d', str(classes)
    ]
    subprocess.run(['javac', '-cp', str(stdlib), '-d', str(classes), str(p / 'DexKitBridge.java')], check=True)
    subprocess.run(compiler + [str(scheduler), str(p / 'Log.kt'), str(p / 'Handler.kt')], check=True, timeout=45)
    subprocess.run(['javac', '-cp', cp, '-d', str(classes), str(manager)], check=True, timeout=30)
    (p / 'Test.java').write_text(r'''
import java.util.concurrent.Callable;
import org.luckypray.dexkit.DexKitBridge;
import t.mma.dexkit.DexKitBridgeManager;

public class Test {
  public static void main(String[] args) throws Exception {
    DexKitBridgeManager manager = new DexKitBridgeManager();
    DexKitBridge first = new DexKitBridge();
    DexKitBridgeManager.Generation generation = manager.publish(first, Test.class.getClassLoader(), "first.apk");
    if (generation.getId() != 1L || manager.currentGenerationId() != 1L) throw new AssertionError("generation");
    if (manager.publish(first, null, "ignored").getId() != 1L) throw new AssertionError("reuse");

    DexKitBridgeManager.Lease compatibility = manager.acquire("legacy-script", true);
    if (!manager.hasRawCompatibilityLease() || manager.currentLeaseCount() != 1) throw new AssertionError("lease");
    if (manager.closeIfIdle()) throw new AssertionError("raw lease must block close");
    int result = manager.query(compatibility, new Callable<Integer>() {
      public Integer call() { return 42; }
    });
    if (result != 42 || manager.currentActiveQueryCount() != 0) throw new AssertionError("query");
    compatibility.close(); compatibility.close();
    if (!manager.closeIfIdle() || first.closes != 1) throw new AssertionError("idle current close");

    DexKitBridge second = new DexKitBridge();
    manager.publish(second, Test.class.getClassLoader(), "second.apk");
    DexKitBridge third = new DexKitBridge();
    manager.publish(third, Test.class.getClassLoader(), "third.apk");
    DexKitBridgeManager.Lease lease = manager.acquire("new-api", false);
    if (manager.closeRetiredIfIdle() != 1 || second.closes != 1) throw new AssertionError("retired close");
    boolean rejected = false;
    lease.close();
    if (!manager.closeIfIdle()) throw new AssertionError("idle current close");
    if (third.closes != 1) throw new AssertionError("current close");
    try { manager.withCurrentBridge(new java.util.function.Function<DexKitBridge, Integer>() { public Integer apply(DexKitBridge bridge){ return 1; } }); }
    catch (IllegalStateException expected) { rejected = true; }
    if (!rejected) throw new AssertionError("closed current accepted query");
    // Configure opt-in rebuild, race many concurrent cache misses, and check query/close exclusion.
    java.util.concurrent.atomic.AtomicInteger creates = new java.util.concurrent.atomic.AtomicInteger();
    java.util.concurrent.atomic.AtomicBoolean failCreate = new java.util.concurrent.atomic.AtomicBoolean(true);
    manager.enableIdleRebuild(() -> {
      if(failCreate.getAndSet(false)) throw new IllegalStateException("create failure");
      creates.incrementAndGet(); return new DexKitBridge();
    }, Test.class.getClassLoader(), "rebuilt.apk", 80L);
    try { manager.withCurrentBridge(b -> 1); throw new AssertionError("create failure missing"); }
    catch(IllegalStateException expected) {}
    java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(8);
    java.util.List<java.util.concurrent.Future<Long>> jobs = new java.util.ArrayList<>();
    for(int i=0;i<16;i++) jobs.add(pool.submit(() -> manager.withCurrentBridge(b -> {
      if(b.closes!=0) throw new AssertionError("closed bridge in query");
      return manager.currentGenerationId();
    })));
    long newGeneration=jobs.get(0).get(3, java.util.concurrent.TimeUnit.SECONDS);
    for(java.util.concurrent.Future<Long> job:jobs) if(job.get(3,java.util.concurrent.TimeUnit.SECONDS)!=newGeneration) throw new AssertionError("concurrent generation");
    if(creates.get()!=1) throw new AssertionError("duplicate create");
    manager.withCurrentBridge(b -> {
      if(manager.currentActiveQueryCount()!=1 || manager.closeIfIdle()) throw new AssertionError("active query closed");
      try {Thread.sleep(130);}catch(InterruptedException e){throw new RuntimeException(e);}
      if(b.closes!=0) throw new AssertionError("idle timer closed active query");
      return null;
    });
    long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
    while(manager.currentGenerationId()!=0 && System.nanoTime()<deadline) Thread.sleep(10);
    if(manager.currentGenerationId()!=0) throw new AssertionError("idle timer did not close");
    DexKitBridge raw=manager.rawCompatibilityBridge("escaped-old-script");
    Thread.sleep(160);
    if(raw.closes!=0 || manager.closeIfIdle() || !manager.hasRawCompatibilityLease()) throw new AssertionError("raw escape not pinned");
    if(manager.rawCompatibilityBridge("again")!=raw || manager.currentLeaseCount()!=1) throw new AssertionError("duplicate raw pin");
    DexKitBridge failure=new DexKitBridge(); failure.failClose=true;
    manager.publish(failure, null, "close-failure.apk");
    if(manager.closeIfIdle() || manager.getLastCloseFailure()==null) throw new AssertionError("close failure not reported");
    if(manager.withCurrentBridge(b -> b)==failure) throw new AssertionError("reuse partially closed native object");
    try{manager.withCurrentBridge(b -> {throw new IllegalStateException("query failure");});}catch(IllegalStateException expected){}
    if(manager.currentActiveQueryCount()!=0) throw new AssertionError("exception leaked active count");
    DexKitBridgeManager.Lease normal=manager.acquire("ordinary", false);
    Thread.sleep(160); if(manager.currentGenerationId()==0)throw new AssertionError("normal lease ignored");
    normal.close();normal.close();
    deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
    while(manager.currentGenerationId()!=0 && System.nanoTime()<deadline)Thread.sleep(10);
    if(manager.currentGenerationId()!=0)throw new AssertionError("release did not re-arm idle");
    pool.shutdownNow();
    System.out.println("PASS idle rebuild: concurrent single creation, create/query/close failure recovery, active query exclusion, automatic idle close, normal lease release and raw process pin");
    System.out.println("PASS DexKit manager: generation reuse, raw compatibility lease, query accounting, retired/current idle close, closed query rejection");
  }
}
''')
    subprocess.run(['javac', '-cp', f'{classes}:{stdlib}', '-d', str(classes), str(p / 'Test.java')], check=True)
    subprocess.run(['java', '-cp', f'{classes}:{stdlib}', 'Test'], check=True, timeout=15)
