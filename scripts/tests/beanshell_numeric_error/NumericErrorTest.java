import bsh.Interpreter;
import bsh.Primitive;
import bsh.TargetError;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/** Runs only synthetic scripts: never loads a phone plugin or Android application. */
public final class NumericErrorTest {
    private static int checks;
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
        checks++;
    }
    private static Object eval(String source) throws Exception {
        return Primitive.unwrap(new Interpreter().eval(source));
    }
    private static void rejected(String source) throws Exception {
        try {
            eval(source);
            throw new AssertionError("Expected numeric input rejection: " + source);
        } catch (TargetError error) {
            check(error.getTarget() instanceof NumberFormatException, "Original NumberFormatException preserved");
            check(error.getErrorLineNumber() > 0, "Script line recorded");
            check(error.getErrorText() != null, "Script expression recorded");
        }
    }
    public static void main(String[] args) throws Exception {
        for (String type : new String[]{"byte", "short", "int", "long", "float", "double",
                "Byte", "Short", "Integer", "Long", "Float", "Double"}) {
            rejected(type + " value=1;\nvalue=\"\";");
            check(Boolean.TRUE.equals(eval(type + " value=7; boolean caught=false; "
                    + "try { value=\"\"; } catch(NumberFormatException e) { caught=true; } "
                    + "caught && value == 7;")), type + " catch works and assignment is atomic");
        }
        for (String invalid : new String[]{"", ".", "+", "-", "1.2.3", "--1"}) {
            rejected("int value=7; value=\"" + invalid + "\";");
        }
        rejected("int value=\"\";");
        rejected("int value=(int)\"\";");
        check(Boolean.TRUE.equals(eval("boolean caught=false; void accept(int value) {} "
                + "try { accept(\"\"); } catch(bsh.EvalException e){caught=true;} caught;")),
                "Method overload rejection stays catchable");
        rejected("int result(){return \"\";} result();");
        rejected("int[] values=new int[1]; values[0]=\"\";");
        check(Boolean.TRUE.equals(eval("int value=3; boolean caught=false; boolean finalized=false; "
                + "try { try { value=\"\"; } catch(IllegalArgumentException e) { caught=true; } } "
                + "finally { finalized=true; } caught && finalized && value == 3;")), "Nested catch/finally");
        check(Boolean.TRUE.equals(eval("boolean caught=false; try { int value=\"\"; } "
                + "catch(Exception e) { caught=true; } caught;")), "Exception catch");
        check(Boolean.TRUE.equals(eval("boolean caught=false; try { int value=\"\"; } "
                + "catch(Throwable e) { caught=true; } caught;")), "Throwable catch");
        check(Boolean.TRUE.equals(eval("int value=0; for(int n=0;n<200;n++){ "
                + "try { value=\"\"; } catch(NumberFormatException e){value=value+1;} } value==200;")),
                "Repeated errors do not corrupt call stack");
        check(Boolean.TRUE.equals(eval("int a=\"12\"; double b=\"-1.5\"; Integer c=\"3\"; "
                + "boolean t=\"text\"; boolean f=\"\"; int zero=null; "
                + "a==12 && b==-1.5 && c==3 && t && !f && zero==0;")), "Existing valid conversions");
        check(Boolean.TRUE.equals(eval("int n=0; if(\"x\") n++; if(\"\") n=99; "
                + "boolean shortCircuit=(false && (1/0>0)) || true; n==1 && shortCircuit;")),
                "Boolean conversion and short circuit unchanged");
        check(Boolean.TRUE.equals(eval("int[] a=new int[\"2\"]; a[\"1\"]=4; "
                + "java.util.Map m=new java.util.HashMap(); m.put(\"k\",7); a[1]==4 && m.get(\"k\")==7;")),
                "Array conversion and map access unchanged");
        check(Boolean.TRUE.equals(eval("void oldVoidCallback(){return \"legacy\";} oldVoidCallback(); true;")),
                "Legacy valued return in void callback remains supported");
        check(Boolean.TRUE.equals(eval("boolean caught=false; boolean finalized=false; int value=9; "
                + "void fail(){value=\"\";} try{try{fail();}finally{finalized=true;}}"
                + "catch(NumberFormatException e){caught=true;} caught && finalized && value==9;")),
                "Unmatched inner try still executes finally before outer catch");
        Interpreter snapshots = new Interpreter();
        bsh.snapshot.BshSnapshot snapshot = snapshots.compileSnapshot("boolean caught=false; int value=9; "
                + "try { value=\"\"; } catch(NumberFormatException e){caught=true;} caught && value==9;", "numeric-snapshot.java");
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        javax.crypto.SecretKey key = new javax.crypto.spec.SecretKeySpec(new byte[16], "AES");
        bsh.snapshot.BshSnapshotHelper.writeEncrypted(snapshot, bytes, key);
        check(Boolean.TRUE.equals(Primitive.unwrap(new Interpreter().evalSnapshot(
                new java.io.ByteArrayInputStream(bytes.toByteArray()), key))), "Encrypted snapshot uses corrected path");
        bsh.BshMethod argumentMethod = new Interpreter().getNameSpace().getMethod("notDeclared", new Class<?>[0]);
        check(argumentMethod == null, "Missing method lookup unchanged");
        Interpreter typed = new Interpreter();
        typed.eval("void accept(int value) {}");
        argumentMethod = typed.getNameSpace().getMethod("accept", new Class<?>[]{Integer.TYPE});
        try {
            argumentMethod.invoke(new Object[]{""}, typed);
            throw new AssertionError("Typed direct invocation accepted empty numeric input");
        } catch (TargetError error) {
            check(error.getTarget() instanceof NumberFormatException, "Direct typed invocation preserves target cause");
        }
        Interpreter i = new Interpreter();
        Runnable caught = (Runnable)i.eval("boolean caught=false; boolean finalized=false; "
                + "void inner(){int value=1; try{value=\"\";}catch(Throwable e){caught=true;}finally{finalized=true;}} "
                + "Runnable r=new Runnable(){public void run(){inner();}}; r;");
        caught.run();
        check(Boolean.TRUE.equals(Primitive.unwrap(i.get("caught")))
                && Boolean.TRUE.equals(Primitive.unwrap(i.get("finalized"))), "Proxy honors catch and finally");
        Runnable uncaught = (Runnable)new Interpreter().eval("void inner(){int value=1; value=\"\";} "
                + "Runnable r=new Runnable(){public void run(){inner();}}; r;");
        AtomicReference<Throwable> escaped = new AtomicReference<>();
        ExecutorService pool = Executors.newFixedThreadPool(1, r -> {
            Thread t = new Thread(r, "numeric-error-regression");
            t.setUncaughtExceptionHandler((thread, error) -> escaped.set(error));
            return t;
        });
        try {
            CountDownLatch tail = new CountDownLatch(1);
            pool.execute(uncaught);
            pool.execute(tail::countDown);
            check(tail.await(5, TimeUnit.SECONDS), "execute worker survives callback error");
            check(escaped.get()==null, "No uncaught worker error");
            pool.submit(uncaught).get(5, TimeUnit.SECONDS);
            check(escaped.get()==null, "submit callback isolated");
        } finally {
            pool.shutdownNow();
            check(pool.awaitTermination(5, TimeUnit.SECONDS), "Test executor released");
        }
        check(Boolean.TRUE.equals(eval("int answer=21*2; answer==42;")), "Interpreter remains usable");
        System.out.println("PASS numeric conversion: " + checks + " checks; synthetic JVM only, NOT device acceptance");
    }
}