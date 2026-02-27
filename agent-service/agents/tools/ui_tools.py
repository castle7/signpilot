from google.adk.tools import tool
from typing import Dict, Any, List

@tool
def highlight_element(bbox: List[int], color: str = "#4285F4", duration: int = 3) -> Dict[str, Any]:
    """Show AR highlight on screen"""
    return {
        "tool_result": {
            "action": "HIGHLIGHT",
            "bbox": bbox,
            "color": color,
            "duration": duration
        }
    }

@tool
def click_element(x: int, y: int, require_confirmation: bool = True) -> Dict[str, Any]:
    """Execute click (use sparingly, prefer teaching)"""
    if require_confirmation:
        return {
            "tool_result": {
                "action": "CLICK_PENDING",
                "coordinates": [x, y],
                "requires_confirmation": True
            }
        }
    return {
        "tool_result": {
            "action": "CLICK",
            "coordinates": [x, y]
        }
    }
