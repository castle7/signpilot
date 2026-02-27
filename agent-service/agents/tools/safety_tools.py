from google.adk.tools import tool
from typing import Dict, Any

@tool
def security_guard(screen_text: str, action_type: str = "default") -> Dict[str, Any]:
    """Security check for fraud prevention"""
    risk_keywords = {
        "high": ["安全账户", "涉嫌洗钱", "法院传票", "转账验证", "中奖领取"],
        "medium": ["验证码", "密码", "身份证号"]
    }

    text_lower = screen_text.lower()
    risk_score = 0.0
    detected = []

    for level, keywords in risk_keywords.items():
        for kw in keywords:
            if kw in text_lower:
                detected.append(kw)
                risk_score += 0.3 if level == "high" else 0.2

    return {
        "tool_result": {
            "passed": risk_score < 0.6,
            "risk_level": "high" if risk_score > 0.6 else "medium" if risk_score > 0.3 else "low",
            "score": risk_score,
            "triggers": detected,
            "action": "BLOCK" if risk_score > 0.6 else "WARN",
            "message": "检测到风险: " + ", ".join(detected) if detected else "安全"
        }
    }
