from pathlib import Path
import tempfile,subprocess
r=Path(__file__).resolve().parents[1]
s=(r/'app/src/main/java/t/mma/hooks/api/contact/WeChatContactApi.java').read_text();a=s.index('    private final java.util.Map<String, String> displayNameMismatchLogs');b=s.index('    private static final int SELF_FIELD',a)
code='public class ContactLogTest {'+s[a:b]+'''public static void main(String[] args){ContactLogTest c=new ContactLogTest();if(!c.shouldLogDisplayNameMismatch("g",32,1,32)||c.shouldLogDisplayNameMismatch("g",32,1,32)||!c.shouldLogDisplayNameMismatch("g",33,1,33))throw new AssertionError();for(int i=0;i<1000;i++)c.shouldLogDisplayNameMismatch("g"+i,2,1,2);if(c.displayNameMismatchLogs.size()!=256)throw new AssertionError();System.out.println("PASS contact log dedup, changed state reported, bounded 256 groups");}}'''
with tempfile.TemporaryDirectory() as d:
 p=Path(d)/'ContactLogTest.java';p.write_text(code);subprocess.run(['javac',str(p)],check=True);subprocess.run(['java','-cp',d,'ContactLogTest'],check=True)
