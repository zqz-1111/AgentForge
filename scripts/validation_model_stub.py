"""Local protocol fixture. No model, remote network, or semantic evaluation."""
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import hashlib
import json
import time
from pathlib import Path

STATE = {"chat": 0, "stream": 0, "embedding": 0}
FAIL = Path(__file__).resolve().parents[1] / ".local-runtime" / "fail-embeddings"

class Handler(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass  # Do not log prompts, credentials, or headers.

    def reply(self, status, value):
        data = json.dumps(value).encode()
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self.reply(200, {"fixture": True, **STATE})

    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", 0))))
        if self.path == "/v1/embeddings":
            STATE["embedding"] += 1
            if FAIL.exists() or "EMBED_FAIL" in str(body.get("input", "")):
                self.reply(400, {"error": {"message": "fixture embedding failure", "type": "fixture"}})
                return
            inputs = body["input"]
            if isinstance(inputs, str):
                inputs = [inputs]
            data = []
            for i, text in enumerate(inputs):
                vector = [0.0] * 1024
                vector[int.from_bytes(hashlib.sha256(str(text).encode()).digest()[:2], "big") % 1024] = 1.0
                data.append({"object": "embedding", "index": i, "embedding": vector})
            self.reply(200, {"object": "list", "data": data, "model": "fixture", "usage": {"prompt_tokens": 1, "total_tokens": 1}})
            return
        if self.path != "/v1/chat/completions":
            self.reply(404, {"error": {"message": "unknown fixture route"}})
            return
        prompt = "\n".join(str(m.get("content", "")) for m in body.get("messages", []))
        tail = prompt.rsplit("User query:", 1)[-1]
        if "VERIFY_TOOL" in tail and "Observation:" not in tail:
            answer = "Thought: Use the calculator.\nAction: calculate\nAction Input: 2+3*4"
        elif "VERIFY_TOOL" in tail:
            answer = "Thought: Tool returned.\nFinal Answer: 14"
        else:
            answer = "Thought: Local fixture.\nFinal Answer: hello world"
        if body.get("stream"):
            STATE["stream"] += 1
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Cache-Control", "no-cache")
            self.end_headers()
            chunks = ["Thought: Local fixture.\nFinal An", "swer: ", "hello ", "world"] if "hello world" in answer else [answer]
            try:
                for index, text in enumerate(chunks):
                    event = {"id": "fixture", "object": "chat.completion.chunk", "created": 1, "model": "fixture",
                             "choices": [{"index": 0, "delta": {"content": text}, "finish_reason": None}]}
                    self.wfile.write(("data: " + json.dumps(event) + "\n\n").encode())
                    self.wfile.flush()
                    time.sleep(0.30 if index < len(chunks) - 1 else 0.70)
                self.wfile.write(b'data: {"id":"fixture","choices":[{"index":0,"delta":{},"finish_reason":"stop"}]}\n\ndata: [DONE]\n\n')
                self.wfile.flush()
            except (BrokenPipeError, ConnectionResetError):
                pass
        else:
            STATE["chat"] += 1
            self.reply(200, {"id": "fixture", "object": "chat.completion", "created": 1, "model": "fixture",
                             "choices": [{"index": 0, "message": {"role": "assistant", "content": answer}, "finish_reason": "stop"}],
                             "usage": {"prompt_tokens": 1, "completion_tokens": 1, "total_tokens": 2}})

if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 18085), Handler).serve_forever()
