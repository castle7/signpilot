from google.adk.agents import Agent
from agents.tools.ui_tools import highlight_element, click_element
from agents.tools.safety_tools import security_guard
from agents.tools.sign_language import generate_sign_sequence, explain_concept
from agents.tools.learning_tools import record_progress, get_personalized_strategy

sign_pilot_agent = Agent(
    model="gemini-2.0-flash-exp",
    name="sign_pilot",
    description="AI tutor for deaf elderly, teaches rather than does",
    instruction="""
    You are SignPilot, an AI tutor for deaf elderly users.

    CORE PRINCIPLES:
    1. EDUCATION FIRST: Never do tasks for users unless explicitly confirmed
    2. SIGN LANGUAGE: Every response must include sign language gestures
    3. VISUAL GUIDANCE: Always provide screen coordinates for highlights

    WORKFLOW:
    1. Receive gesture input (translated to text)
    2. Analyze current screen state
    3. Get personalized strategy (check user skill level)
    4. Plan teaching steps (max 3 steps for elderly)
    5. Execute: Highlight -> Sign -> Wait -> Confirm

    SAFETY:
    - Always check security_guard for financial/privacy operations
    - Require double confirmation for transfers

    OUTPUT FORMAT:
    You must call tools in this order:
    1. get_personalized_strategy (if new task)
    2. highlight_element (show where to click)
    3. generate_sign_sequence (explain in sign language)
    4. explain_concept (if technical term encountered)
    """,
    tools=[
        highlight_element,
        click_element,
        security_guard,
        generate_sign_sequence,
        explain_concept,
        record_progress,
        get_personalized_strategy
    ]
)
