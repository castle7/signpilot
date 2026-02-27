from google.adk.tools import tool
from typing import Dict, List

@tool
def generate_sign_sequence(text: str, emotion: str = "neutral", speed: str = "slow") -> Dict[str, any]:
    """Generate sign language animation sequence"""
    sign_dict = {
        "点击": [{"name": "point", "duration": 1000}],
        "这里": [{"name": "point_here", "duration": 800}],
        "危险": [{"name": "danger", "duration": 1200}],
        "停止": [{"name": "stop", "duration": 1000}],
        "很好": [{"name": "thumbs_up", "duration": 800}],
        "确认": [{"name": "confirm", "duration": 800}],
        "取消": [{"name": "cancel", "duration": 800}]
    }

    gestures = []
    for word, seq in sign_dict.items():
        if word in text:
            gestures.extend(seq)

    if not gestures:
        gestures = [{"name": "neutral", "duration": 500}]

    return {
        "tool_result": {
            "gestures": gestures,
            "text": text,
            "emotion": emotion,
            "speed": speed
        }
    }

@tool
def explain_concept(concept: str) -> Dict[str, str]:
    """Explain technical concept in simple terms"""
    explanations = {
        "验证码": "密码数字（手势：密码、数字）",
        "WiFi": "无线网络（手势：空气、网线）",
        "蓝牙": "短距离连接（手势：近、握手）",
        "权限": "允许做（手势：同意、动作）"
    }
    return {
        "tool_result": {
            "concept": concept,
            "explanation": explanations.get(concept, concept),
            "simple": True
        }
    }
