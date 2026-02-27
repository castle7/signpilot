from google.adk.tools import tool
from typing import Dict

mock_db = {}

@tool
def record_progress(user_id: str, task: str, step: int, success: bool) -> Dict[str, any]:
    """Record learning progress"""
    key = f"{user_id}_{task}"
    if key not in mock_db:
        mock_db[key] = {"steps": [], "mistakes": []}
    mock_db[key]["steps"].append({"step": step, "success": success})
    return {"tool_result": {"recorded": True, "progress": len(mock_db[key]["steps"])}}

@tool
def get_personalized_strategy(user_id: str, task: str) -> Dict[str, any]:
    """Get personalized teaching strategy"""
    # Simplified - would query Firebase in production
    return {
        "tool_result": {
            "pace": "slow",
            "max_steps": 3,
            "require_confirmation": True,
            "pre_explain": True
        }
    }
