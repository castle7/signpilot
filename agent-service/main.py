import os
import json
import time
from flask import Flask, request
from flask_sock import Sock
from google.adk.runners import Runner
from google.adk.sessions import InMemorySessionService
from google.genai import types
from dotenv import load_dotenv

from agents.sign_pilot_agent import sign_pilot_agent

load_dotenv()

app = Flask(__name__)
sock = Sock(app)

session_service = InMemorySessionService()
runner = Runner(
    agent=sign_pilot_agent,
    app_name="signpilot-service",
    session_service=session_service
)

@app.route("/health")
def health():
    return {"status": "healthy", "timestamp": time.time()}

@sock.route('/ws')
def websocket(ws):
    session_id = None
    user_id = None

    try:
        while True:
            data = ws.receive()
            if not data:
                break

            message = json.loads(data)
            msg_type = message.get("type")

            if msg_type == "init":
                user_id = message.get("user_id", "anonymous")
                session_id = f"{user_id}_{os.urandom(4).hex()}"
                ws.send(json.dumps({
                    "action": "SESSION_START",
                    "session_id": session_id,
                    "message": "SignPilot ready"
                }))

            elif msg_type == "gesture_input":
                text = message.get("text", "")
                intent = message.get("intent", "")

                content = types.Content(
                    role="user",
                    parts=[types.Part(text=f"[GESTURE] {text} (intent: {intent})")]
                )

                events = runner.run(
                    user_id=user_id or "anonymous",
                    session_id=session_id or "default",
                    new_message=content
                )

                for event in events:
                    if event.is_final_response():
                        response_text = event.content.parts[0].text if event.content.parts else ""
                        ws.send(json.dumps({
                            "action": "SUBTITLE",
                            "text": response_text
                        }))

                        for part in event.content.parts:
                            if hasattr(part, 'function_call'):
                                result = handle_tool_call(part.function_call)
                                ws.send(json.dumps(result))

            elif msg_type == "screen_capture":
                pass

    except Exception as e:
        print(f"WebSocket error: {e}")
    finally:
        pass

def handle_tool_call(func_call):
    name = func_call.name
    args = dict(func_call.args) if func_call.args else {}

    if name == "highlight_element":
        return {
            "action": "HIGHLIGHT",
            "payload": {
                "coordinates": args.get("bbox", [100, 100, 200, 100]),
                "style": {"color": args.get("color", "#4285F4")}
            }
        }
    elif name == "click_element":
        return {
            "action": "CLICK",
            "coordinates": args.get("coords", [0, 0])
        }
    elif name == "generate_sign_sequence":
        return {
            "action": "SIGN_LANGUAGE",
            "gestures": args.get("gestures", [{"name": "neutral", "duration": 1000}])
        }
    elif name == "security_guard":
        if not args.get("passed", True):
            return {
                "action": "WARNING",
                "message": args.get("message", "Risk detected")
            }
        return {"action": "SAFE"}

    return {"action": "UNKNOWN"}

if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", 8080)))
