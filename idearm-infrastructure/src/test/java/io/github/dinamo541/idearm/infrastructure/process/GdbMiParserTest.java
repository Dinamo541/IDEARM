package io.github.dinamo541.idearm.infrastructure.process;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GdbMiParserTest {

    @Test
    void parsesPrompt() {
        GdbMiRecord record = GdbMiParser.parse("(gdb)");
        assertNotNull(record);
        assertEquals(GdbMiRecord.Type.PROMPT, record.type());

        GdbMiRecord withSpace = GdbMiParser.parse("(gdb) ");
        assertNotNull(withSpace);
        assertEquals(GdbMiRecord.Type.PROMPT, withSpace.type());
    }

    @Test
    void parsesConsoleStream() {
        String line = "~\"Breakpoint 1 at 0x401000: file main.asm, line 14.\\n\"";
        GdbMiRecord record = GdbMiParser.parse(line);
        assertNotNull(record);
        assertEquals(GdbMiRecord.Type.CONSOLE_STREAM, record.type());
        assertEquals("Breakpoint 1 at 0x401000: file main.asm, line 14.\n", record.streamMessage());
    }

    @Test
    void parsesSimpleResults() {
        GdbMiRecord done = GdbMiParser.parse("^done");
        assertNotNull(done);
        assertEquals(GdbMiRecord.Type.RESULT, done.type());
        assertTrue(done.isDone());
        assertNull(done.token());

        GdbMiRecord running = GdbMiParser.parse("42^running");
        assertNotNull(running);
        assertEquals(GdbMiRecord.Type.RESULT, running.type());
        assertTrue(running.isRunning());
        assertEquals(42L, running.token());

        GdbMiRecord error = GdbMiParser.parse("^error,msg=\"No symbol table is loaded.\"");
        assertNotNull(error);
        assertEquals(GdbMiRecord.Type.RESULT, error.type());
        assertTrue(error.isError());
        assertEquals("No symbol table is loaded.", error.getString("msg"));
    }

    @Test
    void parsesRegisterNamesList() {
        String line = "^done,register-names=[\"rax\",\"rbx\",\"rcx\",\"rdx\"]";
        GdbMiRecord record = GdbMiParser.parse(line);
        assertNotNull(record);
        assertTrue(record.isDone());

        List<Object> names = record.getList("register-names");
        assertNotNull(names);
        assertEquals(4, names.size());
        assertEquals("rax", names.get(0));
        assertEquals("rbx", names.get(1));
        assertEquals("rcx", names.get(2));
        assertEquals("rdx", names.get(3));
    }

    @Test
    @SuppressWarnings("unchecked")
    void parsesRegisterValuesList() {
        String line = "100^done,register-values=[{number=\"0\",value=\"0x0000000000401000\"},{number=\"1\",value=\"0x10\"}]";
        GdbMiRecord record = GdbMiParser.parse(line);
        assertNotNull(record);
        assertEquals(100L, record.token());
        assertTrue(record.isDone());

        List<Object> values = record.getList("register-values");
        assertNotNull(values);
        assertEquals(2, values.size());

        Map<String, Object> r0 = (Map<String, Object>) values.get(0);
        assertEquals("0", r0.get("number"));
        assertEquals("0x0000000000401000", r0.get("value"));

        Map<String, Object> r1 = (Map<String, Object>) values.get(1);
        assertEquals("1", r1.get("number"));
        assertEquals("0x10", r1.get("value"));
    }

    @Test
    void parsesStoppedAsyncBreakpointHit() {
        String line = "*stopped,reason=\"breakpoint-hit\",disp=\"keep\",bkptno=\"1\",frame={addr=\"0x0000000000401000\",func=\"main\",args=[],file=\"main.asm\",fullname=\"C:\\\\code\\\\main.asm\",line=\"14\",arch=\"i386:x86-64\"},thread-id=\"1\",stopped-threads=\"all\"";
        GdbMiRecord record = GdbMiParser.parse(line);
        assertNotNull(record);
        assertEquals(GdbMiRecord.Type.EXEC_ASYNC, record.type());
        assertTrue(record.isStopped());
        assertEquals("breakpoint-hit", record.getString("reason"));
        assertEquals("1", record.getString("bkptno"));

        Map<String, Object> frame = record.getMap("frame");
        assertNotNull(frame);
        assertEquals("main", frame.get("func"));
        assertEquals("main.asm", frame.get("file"));
        assertEquals("14", frame.get("line"));
        assertEquals("0x0000000000401000", frame.get("addr"));
        assertEquals("C:\\code\\main.asm", frame.get("fullname"));
    }

    @Test
    void parsesStoppedAsyncExitedNormally() {
        String line = "*stopped,reason=\"exited-normally\"";
        GdbMiRecord record = GdbMiParser.parse(line);
        assertNotNull(record);
        assertEquals(GdbMiRecord.Type.EXEC_ASYNC, record.type());
        assertTrue(record.isStopped());
        assertEquals("exited-normally", record.getString("reason"));
    }
}
