import json
import os
import re
import sys
from datetime import date, datetime
from pathlib import Path

import httpx
from openai import OpenAI

DB_FILE = Path("pantri_data.json")
FOODS_FILE = Path("foods.json")
DAYS_DIR = Path("days")
SUPABASE_URL = os.environ.get("PANTRI_SUPABASE_URL")
SUPABASE_KEY = os.environ.get("PANTRI_SECRET_SUPABASE_KEY") or os.environ.get("PANTRI_SUPABASE_KEY")

GOAL_KCAL = 3500
GOAL_PROTEIN = 200.0

SYSTEM_PROMPT = """\
You are a nutritional data assistant. You receive freeform food diary text and return structured nutritional data as JSON. Always respond with ONLY valid JSON, no markdown fences, no commentary.

Rules:
- The input is plain text. It can be in any format: bullet points, comma-separated, one item per line, shorthand like "200g chicken", "chicken 200g", or just "chicken". Be flexible.
- If no quantity is given, assume a typical single serving.
- Estimate calories, macros (protein, carbs, fat in grams), and cost for each item.
- Use realistic average values for common foods.
- For branded or regional items, make your best estimate based on the food category.
- Costs are in EUR, representing typical German supermarket prices.
- A list of known foods with exact nutritional data will be provided. When a food matches or closely matches a known food (e.g. "chicken" -> "chicken-breast", "pb" -> "peanut-butter", "cereal" -> "apfel-zimt-cereal", "topfen" -> "magertopfen"), use the known food's exact values scaled by quantity. Use the known food's name in the output.
- For foods that don't match any known food, estimate as usual.
- If the user groups items (e.g. with headers, blank lines, or labels like "lunch:", "[dinner]"), preserve those groups. Otherwise put everything in a single entry called "entry 1".

- Include a "timestamp" field in each entry. Use the current time provided in the input.

Respond with this exact JSON structure:
{
  "entries": [
    {
      "label": "entry name",
      "timestamp": "HH:MM",
      "items": [
        {
          "name": "food name",
          "quantity": "200g",
          "kcal": 330,
          "protein_g": 62.0,
          "carbs_g": 0.0,
          "fat_g": 7.2,
          "cost_eur": 2.40
        }
      ],
      "totals": {
        "kcal": 330,
        "protein_g": 62.0,
        "carbs_g": 0.0,
        "fat_g": 7.2,
        "cost_eur": 2.40
      }
    }
  ],
  "day_totals": {
    "kcal": 330,
    "protein_g": 62.0,
    "carbs_g": 0.0,
    "fat_g": 7.2,
    "cost_eur": 2.40
  }
}\
"""


def day_file(day: str) -> Path:
    DAYS_DIR.mkdir(exist_ok=True)
    return DAYS_DIR / f"{day}.txt"


def read_input(path: Path) -> tuple[str, str]:
    """Read file and return (content, target_date).

    If the first line is a date (YYYY-MM-DD), use it as the target date
    and strip it from the content. Otherwise default to today.
    """
    if not path.exists():
        print(f"File not found: {path}")
        sys.exit(1)
    raw = path.read_text().strip()
    if not raw:
        print(f"File is empty, add your food: {path}")
        sys.exit(1)

    lines = raw.split("\n", 1)
    first = lines[0].strip()
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", first):
        target = first
        content = lines[1].strip() if len(lines) > 1 else ""
        if not content:
            print(f"File has a date but no food entries: {path}")
            sys.exit(1)
    else:
        target = date.today().isoformat()
        content = raw

    return content, target


def load_foods() -> dict:
    if FOODS_FILE.exists():
        return json.loads(FOODS_FILE.read_text())
    return {}


def strip_formatting(content: str) -> str:
    """Strip formatted output back to simple 'food quantity' lines."""
    lines = []
    for line in content.splitlines():
        line = line.strip()
        if not line:
            continue
        # header like "[entry 1]  @ 14:30" -> "[entry 1]"
        m = re.match(r"(\[.+?\])\s*@.*", line)
        if m:
            lines.append(m.group(1))
            continue
        # formatted item like "chicken 200g  |  312 kcal ..." -> "chicken 200g"
        if "|" in line:
            lines.append(line.split("|")[0].strip())
            continue
        lines.append(line)
    return "\n".join(lines)


def build_prompt(content: str) -> str:
    foods = load_foods()
    now = datetime.now().strftime("%H:%M")
    lines = [f"Current time: {now}", "",
             "Known foods (per 100g) — use these exact values when a food matches:"]
    for name, info in foods.items():
        lines.append(
            f"- {name}: {info['kcal']} kcal, "
            f"{info['protein_g']}P, {info['carbs_g']}C, {info['fat_g']}F, "
            f"EUR {info['cost_eur_per_100g']:.2f}/100g"
        )
    lines.append("")
    lines.append("Food diary:")
    lines.append(content)
    return "\n".join(lines)


def call_llm(content: str) -> dict:
    client = OpenAI(api_key=os.environ["PANTRI_OPENAI_KEY"])
    response = client.chat.completions.create(
        model="gpt-4o",
        max_tokens=2048,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": content},
        ],
    )
    raw = response.choices[0].message.content.strip()
    # strip markdown fences if present
    if raw.startswith("```"):
        raw = re.sub(r"^```\w*\n?", "", raw)
        raw = re.sub(r"\n?```$", "", raw)
    try:
        return json.loads(raw)
    except json.JSONDecodeError:
        print("Failed to parse LLM response as JSON:")
        print(raw)
        sys.exit(1)


def display(data: dict):
    for entry in data["entries"]:
        print(f"\n  === {entry['label']} ===")
        for item in entry["items"]:
            print(
                f"  {item['name']:<25} {item['quantity']:>8}"
                f"  {item['kcal']:>5} kcal"
                f"  {item['protein_g']:>5.1f}P"
                f"  {item['carbs_g']:>5.1f}C"
                f"  {item['fat_g']:>5.1f}F"
                f"  EUR {item['cost_eur']:.2f}"
            )
        t = entry["totals"]
        print("  " + "─" * 72)
        print(
            f"  {'TOTAL':<25} {'':>8}"
            f"  {t['kcal']:>5} kcal"
            f"  {t['protein_g']:>5.1f}P"
            f"  {t['carbs_g']:>5.1f}C"
            f"  {t['fat_g']:>5.1f}F"
            f"  EUR {t['cost_eur']:.2f}"
        )

    dt = data["day_totals"]
    print("\n  " + "═" * 72)
    print(
        f"  {'DAY TOTAL':<25} {'':>8}"
        f"  {dt['kcal']:>5} kcal"
        f"  {dt['protein_g']:>5.1f}P"
        f"  {dt['carbs_g']:>5.1f}C"
        f"  {dt['fat_g']:>5.1f}F"
        f"  EUR {dt['cost_eur']:.2f}"
    )
    print()


def format_day_file(data: dict, target: str) -> str:
    """Format LLM output as a clean readable text file."""
    lines = [target]
    for entry in data["entries"]:
        ts = entry.get("timestamp", "")
        lines.append(f"[{entry['label']}]  @ {ts}" if ts else f"[{entry['label']}]")
        for item in entry["items"]:
            lines.append(
                f"  {item['name']} {item['quantity']}"
                f"  |  {item['kcal']} kcal  {item['protein_g']}P  {item['carbs_g']}C  {item['fat_g']}F  EUR {item['cost_eur']:.2f}"
            )
        lines.append("")
    return "\n".join(lines)


def _supa_headers():
    return {
        "apikey": SUPABASE_KEY,
        "Authorization": f"Bearer {SUPABASE_KEY}",
        "Content-Type": "application/json",
        "Prefer": "resolution=merge-duplicates",
    }


def sync(target: str, day_data: dict):
    if not SUPABASE_URL or not SUPABASE_KEY:
        return
    try:
        r = httpx.post(
            f"{SUPABASE_URL}/rest/v1/days",
            headers=_supa_headers(),
            json={"date": target, "entries": day_data["entries"], "day_totals": day_data["day_totals"], "raw_text": day_data.get("raw_text", "")},
            timeout=10,
        )
        if r.status_code >= 400:
            print(f"Sync failed: {r.status_code} {r.text}")
        else:
            print("Synced to Supabase")
    except Exception as e:
        print(f"Sync failed: {e}")


def recalc_day_totals(entries: list) -> dict:
    dt = {"kcal": 0, "protein_g": 0, "carbs_g": 0, "fat_g": 0, "cost_eur": 0}
    for entry in entries:
        for key in dt:
            dt[key] += entry["totals"][key]
    return {k: round(v, 1) if k != "cost_eur" else round(v, 2) for k, v in dt.items()}


def save(data: dict, day_path: Path, target: str):
    now = datetime.now().strftime("%H:%M")

    # only stamp entries that don't already have a timestamp
    for entry in data["entries"]:
        if not entry.get("timestamp"):
            entry["timestamp"] = now

    if DB_FILE.exists():
        db = json.loads(DB_FILE.read_text())
    else:
        db = {"days": {}}

    if target in db["days"]:
        existing = db["days"][target]
        # merge: update entries by label, add new ones
        existing_by_label = {e["label"]: i for i, e in enumerate(existing["entries"])}
        for entry in data["entries"]:
            idx = existing_by_label.get(entry["label"])
            if idx is not None:
                # preserve the original timestamp from the existing entry
                old_ts = existing["entries"][idx].get("timestamp")
                if old_ts:
                    entry["timestamp"] = old_ts
                existing["entries"][idx] = entry
            else:
                existing["entries"].append(entry)
        existing["day_totals"] = recalc_day_totals(existing["entries"])
    else:
        data["day_totals"] = recalc_day_totals(data["entries"])
        db["days"][target] = data

    DB_FILE.write_text(json.dumps(db, indent=2, ensure_ascii=False))

    # rewrite the day file with clean formatted output
    raw_text = format_day_file(db["days"][target], target)
    day_path.write_text(raw_text)
    db["days"][target]["raw_text"] = raw_text

    n = len(data["entries"])
    print(f"Saved {n} {'entry' if n == 1 else 'entries'} for {target}.")

    sync(target, db["days"][target])


GREEN = "\033[32m"
RED = "\033[31m"
CYAN = "\033[36m"
YELLOW = "\033[33m"
BLUE = "\033[34m"
BOLD = "\033[1m"
DIM = "\033[2m"
RESET = "\033[0m"


def status(day: str | None = None):
    """Print consumed totals and what's remaining."""
    today = day or date.today().isoformat()

    if not DB_FILE.exists():
        print("No data yet.")
        sys.exit(0)

    db = json.loads(DB_FILE.read_text())
    if today not in db["days"]:
        print(f"No entries for {today}.")
        print(f"  Remaining:  {GREEN}{GOAL_KCAL} kcal  {GOAL_PROTEIN:.0f}g protein{RESET}")
        sys.exit(0)

    dt = db["days"][today]["day_totals"]
    kcal_left = GOAL_KCAL - dt["kcal"]
    protein_left = GOAL_PROTEIN - dt["protein_g"]
    kcal_color = RED if kcal_left < 0 else GREEN
    protein_color = RED if protein_left < 0 else GREEN

    print(f"  {DIM}{today}{RESET}")
    print(f"  {BOLD}Eaten:{RESET}      {dt['kcal']:>7.0f} kcal   {BLUE}{dt['protein_g']:>5.1f}g protein{RESET}   {YELLOW}{dt['carbs_g']:>5.1f}g carbs{RESET}   {RED}{dt['fat_g']:>5.1f}g fat{RESET}   {CYAN}EUR {dt['cost_eur']:.2f}{RESET}")
    print(f"  {BOLD}Remaining:{RESET}  {kcal_color}{kcal_left:>7.0f} kcal{RESET}   {protein_color}{protein_left:>5.1f}g protein{RESET}")


def push():
    """Push all local data to Supabase."""
    if not SUPABASE_URL or not SUPABASE_KEY:
        print("Set PANTRI_SUPABASE_URL and PANTRI_SUPABASE_KEY env vars.")
        sys.exit(1)
    if not DB_FILE.exists():
        print("No local data to push.")
        sys.exit(0)

    db = json.loads(DB_FILE.read_text())
    headers = _supa_headers()

    rows = []
    for d, data in db["days"].items():
        raw_text = data.get("raw_text", "")
        if not raw_text:
            df = day_file(d)
            if df.exists():
                raw_text = df.read_text()
        rows.append({"date": d, "entries": data["entries"], "day_totals": data["day_totals"], "raw_text": raw_text})
    if rows:
        r = httpx.post(f"{SUPABASE_URL}/rest/v1/days", headers=headers, json=rows, timeout=30)
        if r.status_code >= 400:
            print(f"Failed to push days: {r.status_code} {r.text}")
            sys.exit(1)

    weight_rows = [{"date": d, "weight_kg": w} for d, w in db.get("weight", {}).items()]
    if weight_rows:
        r = httpx.post(f"{SUPABASE_URL}/rest/v1/weight", headers=headers, json=weight_rows, timeout=30)
        if r.status_code >= 400:
            print(f"Failed to push weight: {r.status_code} {r.text}")
            sys.exit(1)

    print(f"Pushed {len(rows)} days, {len(weight_rows)} weight entries.")


USAGE = """\
pantri — food tracker

usage:
  python main.py                 open/create today's file, process it
  python main.py <file>          process a specific file
  python main.py status           show today's totals and remaining goals
  python main.py status 2026-03-10  show totals for a specific day
  python main.py push             push all local data to Supabase

file format:
  optionally put a date (YYYY-MM-DD) as the first line to
  save entries to that day. otherwise defaults to today.

  example file:
    2026-03-10
    [breakfast]
    cereal 100g
    milk 200ml
    [lunch]
    chicken 200g
    rice 150g
"""


def main():
    if len(sys.argv) > 1 and sys.argv[1] in ("help", "--help", "-h"):
        print(USAGE)
        return

    if len(sys.argv) > 1 and sys.argv[1] == "status":
        status(sys.argv[2] if len(sys.argv) > 2 else None)
        return

    if len(sys.argv) > 1 and sys.argv[1] == "push":
        push()
        return

    if len(sys.argv) > 1:
        path = Path(sys.argv[1])
    else:
        day = date.today().isoformat()
        path = day_file(day)
        if not path.exists():
            path.write_text("")
            print(f"Created {path} — add your food and run again.")
            sys.exit(0)

    content, day = read_input(path)
    content = strip_formatting(content)

    prompt = build_prompt(content)
    print(f"Sending to OpenAI (for {day})...")
    data = call_llm(prompt)

    display(data)

    answer = input("Save these entries? [y/N] ").strip().lower()
    if answer in ("y", "yes"):
        save(data, path, day)
    else:
        print("Discarded.")


if __name__ == "__main__":
    main()
