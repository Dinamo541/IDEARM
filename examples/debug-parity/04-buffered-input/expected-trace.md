# Expected Trace: 04-buffered-input

This document specifies the behavior of INT 21h function 0Ah buffered keyboard input,
including character queuing, backspace erasure, and Enter termination.

## Scenario A: Typed String with Backspace

Input keystrokes fed to `DebugSession.sendInput`: `"T\bTest\r"`

1. `T` is read into buffer (`sb = "T"`).
2. Backspace `\b` erases `T` from buffer (`sb = ""`).
3. `T`, `e`, `s`, `t` are read (`sb = "Test"`).
4. Carriage return `\r` (Enter) terminates buffered input.
5. Result in memory:
   - `in_count` (offset `+1`): `4`
   - `in_data[0..3]`: `'T', 'e', 's', 't'`
   - `in_data[4]`: `0Dh ('\r')`

## Scenario B: Pasted String with Excess Characters

Input keystrokes: `"1234567890EXTRA\r"` (Buffer maximum length is 10, meaning maxChars = 9)

1. First 9 characters (`"123456789"`) are accepted into buffer.
2. Character `'0'` and `"EXTRA"` exceed buffer limit and are discarded.
3. `\r` is consumed and terminates input without leaking to subsequent reads.
4. Result in memory:
   - `in_count`: `9`
   - `in_data[0..8]`: `"123456789"`
   - `in_data[9]`: `0Dh ('\r')`

## Scenario C: Stop While Blocked

When the program reaches `INT 21h AH=0Ah` and no input has been provided:
- Emits `DebugEvent.WaitingForInput`.
- Session state remains `RUNNING`, thread waits on `inputQueue`.
- Calling `session.stop()` immediately interrupts the worker thread, sets session state to `STOPPED`, and terminates without deadlocking.
