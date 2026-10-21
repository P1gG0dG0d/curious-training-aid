"""Tiny OpenAI-compatible stub used to verify the diet auto-estimate flow without paying for calls."""
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer
from pathlib import Path

LOG = Path('.android-test/reports/fake-llm-calls.log')
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8765


class Handler(BaseHTTPRequestHandler):
    def _send(self, payload, code=200):
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        if self.path.endswith('/models'):
            self._send({"object": "list", "data": [
                {"id": "deepseek-flash", "input_modalities": ["text", "image"]}
            ]})
        else:
            self._send({"error": "not found"}, 404)

    def do_POST(self):
        length = int(self.headers.get('Content-Length', 0))
        raw = self.rfile.read(length).decode('utf-8', 'replace')
        LOG.parent.mkdir(parents=True, exist_ok=True)
        with LOG.open('a', encoding='utf-8') as fh:
            fh.write(raw.replace('\n', ' ') + '\n')
        try:
            body = json.loads(raw)
        except json.JSONDecodeError:
            body = {}
        content = body.get('messages', [{}])[-1].get('content', '')
        text = content if isinstance(content, str) else json.dumps(content, ensure_ascii=False)
        # 只认「份量」那一行里的数字，避免把示例里的 150g 也算进来
        grams = 100
        for line in text.splitlines():
            if line.startswith('份量'):
                digits = ''.join(c for c in line if c.isdigit())
                if digits:
                    grams = int(digits[:4])
                break
        foods = [{"name": "占位", "portion": f"{grams}g",
                  "kcal": grams, "protein": round(grams / 10, 1),
                  "fat": round(grams / 20, 1), "carbs": round(grams / 5, 1)}]
        payload = {"id": "stub", "object": "chat.completion",
                   "choices": [{"index": 0, "finish_reason": "stop",
                                "message": {"role": "assistant",
                                            "content": json.dumps({"foods": foods}, ensure_ascii=False)}}]}
        self._send(payload)


if __name__ == '__main__':
    print(f'stub listening on {PORT}', flush=True)
    HTTPServer(('127.0.0.1', PORT), Handler).serve_forever()
