// Run with: npm test   (uses Node's built-in test runner + TypeScript type stripping)
import { test } from "node:test";
import assert from "node:assert/strict";

import { createSseParser, type SseEvent } from "./sse.ts";

function collect(chunks: string[]): SseEvent[] {
  const events: SseEvent[] = [];
  const parser = createSseParser((e) => events.push(e));
  for (const chunk of chunks) parser.push(chunk);
  parser.flush();
  return events;
}

// The exact wire format produced by the backend's SseEmitter (see ChatStreamHandlerTest).
const BACKEND_STREAM =
  'event:user_message\ndata:{"id":"u1","role":"USER","content":"q","citations":[]}\n\n' +
  'event:token\ndata:"Hello"\n\n' +
  'event:token\ndata:" world\\nline2"\n\n' +
  'event:assistant_message\ndata:{"id":"a1","role":"ASSISTANT","content":"Hello world\\nline2","citations":[{"filePath":"src/A.java"}]}\n\n' +
  "event:done\ndata:[DONE]\n\n";

test("parses the backend event sequence and JSON tokens", () => {
  const events = collect([BACKEND_STREAM]);
  assert.deepEqual(
    events.map((e) => e.event),
    ["user_message", "token", "token", "assistant_message", "done"]
  );
  const tokens = events.filter((e) => e.event === "token").map((e) => JSON.parse(e.data));
  assert.deepEqual(tokens, ["Hello", " world\nline2"]);
});

test("handles events split across arbitrary chunk boundaries", () => {
  const chunks: string[] = [];
  for (let i = 0; i < BACKEND_STREAM.length; i += 7) {
    chunks.push(BACKEND_STREAM.slice(i, i + 7));
  }
  assert.deepEqual(collect(chunks), collect([BACKEND_STREAM]));
});

test("normalizes CRLF line endings", () => {
  const events = collect(['event: token\r\ndata: "x"\r\n\r\n']);
  assert.deepEqual(events, [{ event: "token", data: '"x"' }]);
});

test("joins multi-line data and strips only one leading space", () => {
  const events = collect(["data:  a\ndata:b\n\n"]);
  assert.deepEqual(events, [{ event: "message", data: " a\nb" }]);
});

test("flushes a trailing event without a blank line and ignores comments", () => {
  const events = collect([": keep-alive\n\n", 'event:error\ndata:{"message":"boom"}']);
  assert.deepEqual(events, [{ event: "error", data: '{"message":"boom"}' }]);
});
