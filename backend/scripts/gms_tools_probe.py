#!/usr/bin/env python3
"""SSAFY GMS 중계가 OpenAI Chat Completions의 `tools`(function calling)·`response_format`(json_schema)을 통과시키는지 확인한다.

"도리에게 묻기"(LLM이 시뮬레이션 API를 골라 호출, S15P21D105-637 AI 채팅 도입 검토) 설계를 가르는 사전 점검이다. 세 요청을 순서대로 보내고 결과만 출력한다.
  1. baseline      — 평문 요청(코치가 지금 쓰는 형태). 토큰·모델·경로가 맞는지 확인
  2. tools         — `tools` + `tool_choice: "required"`. 응답 `choices[0].message.tool_calls`에 함수 호출과 JSON arguments가 오면 통과
  3. json_schema   — `response_format: {type: json_schema, strict: true}`. tools가 막힐 때의 우회 경로가 되는지 확인
추가로 `tool_choice`를 특정 함수로 강제한 4번으로 인자 추출 품질을 본다.

사용(토큰은 환경변수로만 받고 출력하지 않는다. infra/.env 의 OPENAI_API_KEY 는 GMS 중계 토큰이다):
  OPENAI_API_KEY=<gms token> python3 backend/scripts/gms_tools_probe.py
  OPENAI_API_KEY=<gms token> python3 backend/scripts/gms_tools_probe.py --model gpt-4o-mini   # 다른 모델 비교
표준 라이브러리만 쓴다(의존성 없음). 과금: 4회 호출, 각 200토큰 안팎.
"""
import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request

BASE_URL = "https://gms.ssafy.io/gmsapi/api.openai.com/v1/chat/completions"

TOOLS = [
    {
        "type": "function",
        "function": {
            "name": "simulate_spending_cut",
            "description": "카테고리 소비를 월 일정 금액 줄였을 때 목표 달성일 변화를 계산한다.",
            "parameters": {
                "type": "object",
                "properties": {
                    "category": {"type": "string", "description": "카테고리 코드. 예: CAFE, FOOD"},
                    "monthlyReduction": {"type": "integer", "description": "월 감소액(원)"},
                },
                "required": ["category", "monthlyReduction"],
                "additionalProperties": False,
            },
        },
    },
    {
        "type": "function",
        "function": {
            "name": "simulate_loan_prepayment",
            "description": "대출을 추가 상환했을 때 이자 절감과 목표 영향을 계산한다.",
            "parameters": {
                "type": "object",
                "properties": {"amount": {"type": "integer", "description": "추가 상환액(원)"}},
                "required": ["amount"],
                "additionalProperties": False,
            },
        },
    },
]

QUESTION = "카페 5만원 줄이고 대출 100만원 더 갚으면 목표 언제 돼?"


def call(token: str, body: dict, timeout: float = 30.0) -> tuple[int, dict | str, float]:
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    request = urllib.request.Request(
        BASE_URL, data=data, method="POST",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
    )
    started = time.perf_counter()
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            raw = response.read().decode("utf-8")
            status = response.status
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", errors="replace")
        status = error.code
    elapsed = time.perf_counter() - started
    try:
        return status, json.loads(raw), elapsed
    except json.JSONDecodeError:
        return status, raw, elapsed


def summarize(label: str, status: int, parsed, elapsed: float) -> dict:
    print(f"\n=== {label}: HTTP {status} ({elapsed:.2f}s)")
    if not isinstance(parsed, dict):
        print("  non-JSON body:", str(parsed)[:300])
        return {"status": status}
    if "error" in parsed:
        error = parsed["error"]
        print("  error:", json.dumps(error, ensure_ascii=False)[:500])
        return {"status": status, "error": error}
    choice = (parsed.get("choices") or [{}])[0]
    message = choice.get("message", {})
    result = {
        "status": status,
        "finish_reason": choice.get("finish_reason"),
        "content": message.get("content"),
        "tool_calls": message.get("tool_calls"),
        "usage": parsed.get("usage"),
        "model": parsed.get("model"),
    }
    print("  model:", result["model"], "| finish_reason:", result["finish_reason"])
    print("  usage:", json.dumps(result["usage"], ensure_ascii=False))
    if result["tool_calls"]:
        for tool_call in result["tool_calls"]:
            function = tool_call.get("function", {})
            print(f"  tool_call: {function.get('name')}({function.get('arguments')})")
    else:
        print("  tool_calls: (none)")
    if result["content"]:
        print("  content:", str(result["content"])[:300].replace("\n", " "))
    return result


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model", default="gpt-5.4-mini")
    parser.add_argument("--max-tokens-parameter", default="max_completion_tokens",
                        help="4o 계열은 max_tokens")
    args = parser.parse_args()

    token = os.environ.get("OPENAI_API_KEY", "").strip()
    if not token:
        sys.exit("OPENAI_API_KEY(GMS 중계 토큰) 환경변수가 비어 있습니다.")

    system = "너는 재무 코치 도리다. 사용자의 질문에 필요한 시뮬레이션 도구를 고르고 인자를 채워라. 숫자를 스스로 계산하지 마라."
    base = {"model": args.model, args.max_tokens_parameter: 300}

    verdict = {}

    # 1. baseline
    status, parsed, elapsed = call(token, {**base, "messages": [
        {"role": "system", "content": "한 문장으로 답해."}, {"role": "user", "content": "GMS 중계 점검용 인사말 하나."}]})
    r = summarize("1. baseline (plain chat)", status, parsed, elapsed)
    verdict["baseline"] = r.get("status") == 200 and bool(r.get("content"))

    # 2. tools (auto)
    status, parsed, elapsed = call(token, {**base, "messages": [
        {"role": "system", "content": system}, {"role": "user", "content": QUESTION}],
        "tools": TOOLS, "tool_choice": "required"})
    r = summarize("2. tools + tool_choice=required", status, parsed, elapsed)
    verdict["tools_passthrough"] = r.get("status") == 200 and bool(r.get("tool_calls"))
    verdict["tools_two_calls"] = bool(r.get("tool_calls")) and len(r["tool_calls"]) >= 2

    # 3. json_schema fallback
    schema = {
        "name": "simulation_plan",
        "strict": True,
        "schema": {
            "type": "object",
            "properties": {
                "calls": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "tool": {"type": "string", "enum": ["simulate_spending_cut", "simulate_loan_prepayment"]},
                            "arguments": {"type": "string", "description": "JSON 문자열 인자"},
                        },
                        "required": ["tool", "arguments"],
                        "additionalProperties": False,
                    },
                }
            },
            "required": ["calls"],
            "additionalProperties": False,
        },
    }
    status, parsed, elapsed = call(token, {**base, "messages": [
        {"role": "system", "content": system + " 답은 JSON으로만."}, {"role": "user", "content": QUESTION}],
        "response_format": {"type": "json_schema", "json_schema": schema}})
    r = summarize("3. response_format json_schema (fallback path)", status, parsed, elapsed)
    parsed_ok = False
    if r.get("status") == 200 and r.get("content"):
        try:
            parsed_ok = isinstance(json.loads(r["content"]).get("calls"), list)
        except (json.JSONDecodeError, AttributeError):
            parsed_ok = False
    verdict["json_schema_passthrough"] = parsed_ok

    # 4. forced single function
    status, parsed, elapsed = call(token, {**base, "messages": [
        {"role": "system", "content": system}, {"role": "user", "content": "카페를 한 달에 5만원 덜 쓰면?"}],
        "tools": TOOLS, "tool_choice": {"type": "function", "function": {"name": "simulate_spending_cut"}}})
    r = summarize("4. tool_choice forced to simulate_spending_cut", status, parsed, elapsed)
    verdict["forced_tool_choice"] = r.get("status") == 200 and bool(r.get("tool_calls"))

    print("\n=== VERDICT")
    for key, value in verdict.items():
        print(f"  {key:24s} {'PASS' if value else 'FAIL'}")
    if verdict["tools_passthrough"]:
        print("  -> GMS는 tools/tool_calls를 그대로 통과시킨다. tool calling 설계로 진행 가능.")
    elif verdict["json_schema_passthrough"]:
        print("  -> tools는 막히거나 무시되지만 json_schema는 통과한다. 구조화 출력 우회 설계.")
    else:
        print("  -> 둘 다 실패. 위 error 본문(GMS가 어떤 필드를 거부하는지)을 확인한다.")
    return 0 if verdict["baseline"] else 1


if __name__ == "__main__":
    sys.exit(main())
