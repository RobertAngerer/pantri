import json
import os
import re
import subprocess
import sys
from datetime import date, datetime
from pathlib import Path

from openai import OpenAI

DB_FILE = Path("pantri_data.json")
FOODS_FILE = Path("foods.json")
DAYS_DIR = Path("days")
REMOTE = os.environ.get("PANTRI_REMOTE")  # e.g. "ubuntu@1.2.3.4:/home/ubuntu/pantri/"

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
- If known nutritional data is provided for specific foods, use those exact values (scale by quantity). For all other foods, estimate as usual.
- If the user groups items (e.g. with headers, blank lines, or labels like "lunch:", "[dinner]"), preserve those groups. Otherwise put everything in a single entry called "entry 1".

Respond with this exact JSON structure:
{
  "entries": [
    {
      "label": "entry name",
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


def today_file() -> Path:
    DAYS_DIR.mkdir(exist_ok=True)
    return DAYS_DIR / f"{date.today().isoformat()}.txt"


def read_input(path: Path) -> str:
    if not path.exists():
        print(f"File not found: {path}")
        sys.exit(1)
    content = path.read_text().strip()
    if not content:
        print(f"File is empty, add your food: {path}")
        sys.exit(1)
    return content


def load_foods() -> dict:
    if FOODS_FILE.exists():
        return json.loads(FOODS_FILE.read_text())
    return {}


def find_known_foods(content: str, foods: dict) -> dict:
    lower = content.lower()
    return {name: info for name, info in foods.items() if name.lower() in lower}


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
    known = find_known_foods(content, foods)
    if not known:
        return content
    lines = ["Known nutritional data (per 100g):"]
    for name, info in known.items():
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


def format_day_file(data: dict) -> str:
    """Format LLM output as a clean readable text file."""
    lines = []
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


def sync():
    if not REMOTE:
        return
    try:
        subprocess.run(
            ["rsync", "-az", str(DB_FILE), REMOTE],
            check=True, timeout=10,
        )
        print(f"Synced to {REMOTE}")
    except Exception as e:
        print(f"Sync failed: {e}")


def recalc_day_totals(entries: list) -> dict:
    dt = {"kcal": 0, "protein_g": 0, "carbs_g": 0, "fat_g": 0, "cost_eur": 0}
    for entry in entries:
        for key in dt:
            dt[key] += entry["totals"][key]
    return {k: round(v, 1) if k != "cost_eur" else round(v, 2) for k, v in dt.items()}


def save(data: dict, day_file: Path):
    today = date.today().isoformat()
    now = datetime.now().strftime("%H:%M")

    # stamp each incoming entry
    for entry in data["entries"]:
        entry["timestamp"] = now

    if DB_FILE.exists():
        db = json.loads(DB_FILE.read_text())
    else:
        db = {"days": {}}

    if today in db["days"]:
        existing = db["days"][today]
        # merge: update entries by label, add new ones
        existing_by_label = {e["label"]: i for i, e in enumerate(existing["entries"])}
        for entry in data["entries"]:
            idx = existing_by_label.get(entry["label"])
            if idx is not None:
                existing["entries"][idx] = entry
            else:
                existing["entries"].append(entry)
        existing["day_totals"] = recalc_day_totals(existing["entries"])
    else:
        data["day_totals"] = recalc_day_totals(data["entries"])
        db["days"][today] = data

    DB_FILE.write_text(json.dumps(db, indent=2, ensure_ascii=False))

    # rewrite the day file with clean formatted output
    day_file.write_text(format_day_file(db["days"][today]))

    n = len(data["entries"])
    print(f"Saved {n} {'entry' if n == 1 else 'entries'} for {today}.")

    sync()


def status():
    """Print today's consumed totals and what's remaining."""
    today = date.today().isoformat()

    if not DB_FILE.exists():
        print("No data yet.")
        sys.exit(0)

    db = json.loads(DB_FILE.read_text())
    if today not in db["days"]:
        print(f"No entries for {today}.")
        print(f"  Remaining:  {GOAL_KCAL} kcal  {GOAL_PROTEIN:.0f}g protein")
        sys.exit(0)

    dt = db["days"][today]["day_totals"]
    kcal_left = GOAL_KCAL - dt["kcal"]
    protein_left = GOAL_PROTEIN - dt["protein_g"]

    print(f"  Eaten:      {dt['kcal']:>5} kcal   {dt['protein_g']:>5.1f}g protein   {dt['carbs_g']:>5.1f}g carbs   {dt['fat_g']:>5.1f}g fat   EUR {dt['cost_eur']:.2f}")
    print(f"  Remaining:  {kcal_left:>5} kcal   {protein_left:>5.1f}g protein")


def main():
    if len(sys.argv) > 1 and sys.argv[1] == "status":
        status()
        return

    if len(sys.argv) > 1:
        path = Path(sys.argv[1])
    else:
        path = today_file()
        if not path.exists():
            path.write_text("")
            print(f"Created {path} — add your food and run again.")
            sys.exit(0)

    content = read_input(path)
    content = strip_formatting(content)

    prompt = build_prompt(content)
    print("Sending to OpenAI...")
    data = call_llm(prompt)

    display(data)

    answer = input("Save these entries? [y/N] ").strip().lower()
    if answer in ("y", "yes"):
        save(data, path)
    else:
        print("Discarded.")


if __name__ == "__main__":
    main()
