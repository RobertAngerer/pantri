import json
import os
import re
import sys
from datetime import date, datetime
from pathlib import Path

import httpx
from openai import OpenAI

PROJECT_DIR = Path(__file__).resolve().parent.parent.parent
DB_FILE = PROJECT_DIR / "pantri_data.json"
FOODS_FILE = PROJECT_DIR / "foods.json"
DAYS_DIR = PROJECT_DIR / "days"
PREPS_DIR = PROJECT_DIR / "mealpreps"
SUPABASE_URL = os.environ.get("PANTRI_SUPABASE_URL")
SUPABASE_KEY = os.environ.get("PANTRI_SECRET_SUPABASE_KEY") or os.environ.get("PANTRI_SUPABASE_KEY")

# Defaults — overridden by settings.json from Supabase
GOAL_KCAL = 3500
GOAL_PROTEIN = 200.0
GOAL_FAT = 100.0
BUDGET_MONTHLY = 600.0


def _load_settings_from_supabase():
    """Load settings from Supabase storage, update globals."""
    global GOAL_KCAL, GOAL_PROTEIN, GOAL_FAT, BUDGET_MONTHLY
    if not SUPABASE_URL:
        return
    try:
        resp = httpx.get(f"{SUPABASE_URL}/storage/v1/object/public/pantri/settings.json", timeout=5)
        if resp.status_code == 200:
            s = resp.json()
            GOAL_KCAL = s.get("goal_kcal", GOAL_KCAL)
            GOAL_PROTEIN = s.get("goal_protein_g", GOAL_PROTEIN)
            GOAL_FAT = s.get("goal_fat_g", GOAL_FAT)
            BUDGET_MONTHLY = s.get("budget_monthly", BUDGET_MONTHLY)
    except Exception:
        pass


_load_settings_from_supabase()

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


ALIASES = {
    "chicken": "chicken-breast",
    "pb": "peanut-butter",
    "peanutbutter": "peanut-butter",
    "peanut butter": "peanut-butter",
    "cereal": "apfel-zimt-cereal",
    "topfen": "magertopfen",
    "quark": "magertopfen",
    "oats": "haferflocken",
    "oatmeal": "haferflocken",
    "eggs": "egg",
    "rice": "basmati-rice",
    "milk": "whole-milk",
    "banana": "banane",
    "bananas": "banane",
}


def _match_known_food(name: str, foods: dict) -> str | None:
    n = name.lower().strip()
    if n in foods:
        return n
    # dash/space normalization
    with_dashes = n.replace(" ", "-")
    if with_dashes in foods:
        return with_dashes
    n_spaces = n.replace("-", " ")
    for key in foods:
        if key.replace("-", " ") == n_spaces:
            return key
    # substring match (only if exactly one candidate)
    candidates = [k for k in foods if k.replace("-", " ") in n_spaces or n_spaces in k.replace("-", " ")]
    if len(candidates) == 1:
        return candidates[0]
    # aliases
    alias = ALIASES.get(n)
    if alias and alias in foods:
        return alias
    return None


def try_parse_locally(content: str) -> dict | None:
    """Parse food text locally if all items are known foods with gram quantities.
    Returns the result dict or None to fall back to OpenAI."""
    foods = load_foods()
    if not foods:
        return None

    now = datetime.now().strftime("%H:%M")
    lines = content.strip().split("\n")
    entries = []
    current_label = None
    current_items = []

    def flush():
        nonlocal current_label, current_items
        if not current_items:
            current_label = None
            return
        label = current_label or f"entry {len(entries) + 1}"
        food_items = []
        for key, grams in current_items:
            info = foods[key]
            scale = grams / 100.0
            food_items.append({
                "name": key,
                "quantity": f"{int(grams)}g",
                "kcal": round(info["kcal"] * scale),
                "protein_g": round(info["protein_g"] * scale, 1),
                "carbs_g": round(info["carbs_g"] * scale, 1),
                "fat_g": round(info["fat_g"] * scale, 1),
                "cost_eur": round(info["cost_eur_per_100g"] * scale, 2),
            })
        totals = {
            "kcal": sum(i["kcal"] for i in food_items),
            "protein_g": round(sum(i["protein_g"] for i in food_items), 1),
            "carbs_g": round(sum(i["carbs_g"] for i in food_items), 1),
            "fat_g": round(sum(i["fat_g"] for i in food_items), 1),
            "cost_eur": round(sum(i["cost_eur"] for i in food_items), 2),
        }
        entries.append({"label": label, "timestamp": now, "items": food_items, "totals": totals})
        current_items = []
        current_label = None

    for line in lines:
        line = line.strip()
        if not line:
            continue
        # group headers: [lunch], "lunch:", "# breakfast"
        m = re.match(r"^\[(.+)]$|^#\s*(.+)$|^(.+):$", line)
        if m:
            flush()
            current_label = (m.group(1) or m.group(2) or m.group(3)).strip()
            continue
        # split by comma
        parts = [p.strip() for p in line.split(",") if p.strip()]
        for part in parts:
            gm = re.search(r"(\d+(?:\.\d+)?)\s*g\b", part, re.IGNORECASE)
            if not gm:
                return None  # no gram quantity → can't resolve locally
            grams = float(gm.group(1))
            food_name = part[:gm.start()].strip() + " " + part[gm.end():].strip()
            food_name = food_name.strip().strip("-").strip()
            if not food_name:
                return None
            key = _match_known_food(food_name, foods)
            if key is None:
                return None  # unknown food → fall back to OpenAI
            current_items.append((key, grams))

    flush()
    if not entries:
        return None

    day_totals = {
        "kcal": sum(e["totals"]["kcal"] for e in entries),
        "protein_g": round(sum(e["totals"]["protein_g"] for e in entries), 1),
        "carbs_g": round(sum(e["totals"]["carbs_g"] for e in entries), 1),
        "fat_g": round(sum(e["totals"]["fat_g"] for e in entries), 1),
        "cost_eur": round(sum(e["totals"]["cost_eur"] for e in entries), 2),
    }
    return {"entries": entries, "day_totals": day_totals}


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


G = "\033[32m"
R = "\033[31m"
C = "\033[36m"
Y = "\033[33m"
B = "\033[34m"
BO = "\033[1m"
DI = "\033[2m"
RS = "\033[0m"
BG_SURFACE = "\033[48;5;236m"
BG_DARK = "\033[48;5;234m"


def _macro_line(name, qty, kcal, p, c, f, cost, bold=False):
    b = BO if bold else ""
    r = RS if bold else ""
    return (
        f"  {b}{name:<24}{r} {DI}{qty:>7}{RS}"
        f"  {b}{kcal:>6.0f}{r} {DI}kcal{RS}"
        f"  {B}{b}{p:>5.1f}{r}P{RS}"
        f"  {Y}{b}{c:>5.1f}{r}C{RS}"
        f"  {R}{b}{f:>5.1f}{r}F{RS}"
        f"  {C}{b}€{cost:.2f}{r}{RS}"
    )


def display(data: dict):
    print()
    for i, entry in enumerate(data["entries"]):
        ts = entry.get("timestamp", "")
        ts_str = f" {DI}@ {ts}{RS}" if ts else ""
        print(f"  {BO}{G}▌{RS} {BO}{entry['label'].upper()}{RS}{ts_str}")
        print(f"  {DI}{'─' * 68}{RS}")

        for item in entry["items"]:
            print(_macro_line(
                item["name"], item["quantity"],
                item["kcal"], item["protein_g"], item["carbs_g"], item["fat_g"],
                item["cost_eur"],
            ))

        t = entry["totals"]
        print(f"  {DI}{'·' * 68}{RS}")
        print(_macro_line("", "", t["kcal"], t["protein_g"], t["carbs_g"], t["fat_g"], t["cost_eur"], bold=True))
        print()

    dt = data["day_totals"]
    kcal_left = GOAL_KCAL - dt["kcal"]
    prot_left = GOAL_PROTEIN - dt["protein_g"]
    kc = G if kcal_left >= 0 else R
    pc = G if prot_left >= 0 else R

    print(f"  {BO}{'━' * 68}{RS}")
    print(
        f"  {BO}DAY TOTAL{RS}               "
        f"  {BO}{dt['kcal']:>6.0f}{RS} {DI}kcal{RS}"
        f"  {B}{BO}{dt['protein_g']:>5.1f}{RS}P"
        f"  {Y}{BO}{dt['carbs_g']:>5.1f}{RS}C"
        f"  {R}{BO}{dt['fat_g']:>5.1f}{RS}F"
        f"  {C}{BO}€{dt['cost_eur']:.2f}{RS}"
    )
    print(
        f"  {DI}REMAINING{RS}               "
        f"  {kc}{BO}{kcal_left:>6.0f}{RS} {DI}kcal{RS}"
        f"  {pc}{BO}{prot_left:>5.1f}{RS}P"
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

    # deduplicate labels within incoming entries so merges don't collide
    seen_labels = {}
    for entry in data["entries"]:
        label = entry["label"]
        if label in seen_labels:
            seen_labels[label] += 1
            entry["label"] = f"{label} {seen_labels[label]}"
        else:
            seen_labels[label] = 1

    if DB_FILE.exists():
        db = json.loads(DB_FILE.read_text())
    else:
        db = {"days": {}}

    if target in db["days"]:
        existing = db["days"][target]
        # preserve timestamps from previous entries where labels match
        old_timestamps = {e["label"]: e.get("timestamp") for e in existing["entries"]}
        for entry in data["entries"]:
            old_ts = old_timestamps.get(entry["label"])
            if old_ts:
                entry["timestamp"] = old_ts

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
    update_mealpreps(db)


def status(day: str | None = None):
    """Print consumed totals and what's remaining."""
    today = day or date.today().isoformat()

    if not DB_FILE.exists():
        print("No data yet.")
        sys.exit(0)

    db = json.loads(DB_FILE.read_text())
    if today not in db["days"]:
        print(f"No entries for {today}.")
        print(f"  Remaining:  {G}{GOAL_KCAL} kcal  {GOAL_PROTEIN:.0f}g protein{RS}")
        sys.exit(0)

    dt = db["days"][today]["day_totals"]
    entries = db["days"][today].get("entries", [])
    kcal_left = GOAL_KCAL - dt["kcal"]
    prot_left = GOAL_PROTEIN - dt["protein_g"]
    kc = G if kcal_left >= 0 else R
    pc = G if prot_left >= 0 else R

    print()
    print(f"  {DI}{today}{RS}  {DI}({len(entries)} meals){RS}")
    print()
    for entry in entries:
        ts = entry.get("timestamp", "")
        ts_str = f" {DI}@ {ts}{RS}" if ts else ""
        t = entry["totals"]
        print(
            f"  {G}▌{RS} {BO}{entry['label']:<18}{RS}{ts_str}"
            f"  {t['kcal']:>5.0f} kcal"
            f"  {B}{t['protein_g']:>5.1f}P{RS}"
            f"  {C}€{t['cost_eur']:.2f}{RS}"
        )
    print()
    print(f"  {BO}{'━' * 56}{RS}")
    print(
        f"  {BO}EATEN{RS}       "
        f"  {BO}{dt['kcal']:>6.0f}{RS} kcal"
        f"  {B}{BO}{dt['protein_g']:>5.1f}{RS}P"
        f"  {Y}{BO}{dt['carbs_g']:>5.1f}{RS}C"
        f"  {R}{BO}{dt['fat_g']:>5.1f}{RS}F"
        f"  {C}{BO}€{dt['cost_eur']:.2f}{RS}"
    )
    print(
        f"  {DI}REMAINING{RS}   "
        f"  {kc}{BO}{kcal_left:>6.0f}{RS} kcal"
        f"  {pc}{BO}{prot_left:>5.1f}{RS}P"
    )
    print()


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


def push_foods():
    """Upload foods.json to Supabase Storage."""
    if not SUPABASE_URL or not SUPABASE_KEY:
        print("Set PANTRI_SUPABASE_URL and PANTRI_SUPABASE_KEY env vars.")
        sys.exit(1)
    if not FOODS_FILE.exists():
        print("No foods.json found.")
        sys.exit(1)
    r = httpx.post(
        f"{SUPABASE_URL}/storage/v1/object/pantri/foods.json",
        headers={
            "apikey": SUPABASE_KEY,
            "Authorization": f"Bearer {SUPABASE_KEY}",
            "Content-Type": "application/json",
            "x-upsert": "true",
        },
        content=FOODS_FILE.read_bytes(),
        timeout=10,
    )
    if r.status_code >= 400:
        print(f"Failed: {r.status_code} {r.text}")
    else:
        print("Uploaded foods.json to Supabase Storage.")


def pull_foods():
    """Download foods.json from Supabase Storage."""
    if not SUPABASE_URL or not SUPABASE_KEY:
        print("Set PANTRI_SUPABASE_URL and PANTRI_SUPABASE_KEY env vars.")
        sys.exit(1)
    r = httpx.get(
        f"{SUPABASE_URL}/storage/v1/object/pantri/foods.json",
        headers={
            "apikey": SUPABASE_KEY,
            "Authorization": f"Bearer {SUPABASE_KEY}",
        },
        timeout=10,
    )
    if r.status_code >= 400:
        print(f"Failed: {r.status_code} {r.text}")
    else:
        FOODS_FILE.write_text(r.text)
        print("Downloaded foods.json from Supabase Storage.")


def sync_foods():
    """Pull foods.json from storage if it differs from local."""
    if not SUPABASE_URL or not SUPABASE_KEY:
        return
    try:
        r = httpx.get(
            f"{SUPABASE_URL}/storage/v1/object/pantri/foods.json",
            headers={
                "apikey": SUPABASE_KEY,
                "Authorization": f"Bearer {SUPABASE_KEY}",
            },
            timeout=5,
        )
        if r.status_code >= 400:
            print(f"Foods sync failed: {r.status_code} {r.text}")
            return
        remote = r.text
        local = FOODS_FILE.read_text() if FOODS_FILE.exists() else ""
        if remote != local:
            FOODS_FILE.write_text(remote)
            local_count = len(json.loads(local)) if local.strip() else 0
            remote_count = len(json.loads(remote))
            print(f"Foods updated from storage ({local_count} → {remote_count} items).")
    except Exception as e:
        print(f"Foods sync failed: {e}")


def parse_grams(qty: str) -> float:
    m = re.match(r"([\d.]+)\s*(g|kg|ml|l)", qty.lower())
    if not m:
        return 0.0
    val = float(m.group(1))
    if m.group(2) in ("kg", "l"):
        val *= 1000
    return val


def prep():
    """Process a mealprep file and sync items to Supabase."""
    if not SUPABASE_URL or not SUPABASE_KEY:
        print("Set PANTRI_SUPABASE_URL and PANTRI_SUPABASE_KEY env vars.")
        sys.exit(1)

    if len(sys.argv) < 3:
        print("Usage: python main.py prep <file>")
        print("  File format: one food per line, e.g. 'gulaschmeat 2000g'")
        return

    path = Path(sys.argv[2])
    if not path.exists():
        print(f"File not found: {path}")
        sys.exit(1)

    today = date.today().isoformat()
    foods = load_foods()
    items = []

    for line in path.read_text().strip().splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        m = re.match(r"(.+?)\s+([\d.]+\s*(?:g|kg|ml|l))", line, re.IGNORECASE)
        if not m:
            print(f"  {Y}!{RS} Skipping: {line}")
            continue
        food_name = m.group(1).strip().lower().replace(" ", "-")
        qty_str = m.group(2).strip()
        grams = parse_grams(qty_str)
        if grams <= 0:
            continue
        items.append({"food": food_name, "initial_g": grams, "remaining_g": grams, "created": today, "active": True})
        marker = f"{G}✓{RS}" if food_name in foods else f"{Y}?{RS}"
        print(f"  {marker} {food_name} {grams:.0f}g")

    if not items:
        print("No valid items found.")
        return

    headers = _supa_headers()
    del headers["Prefer"]
    r = httpx.post(f"{SUPABASE_URL}/rest/v1/mealpreps", headers=headers, json=items, timeout=10)
    if r.status_code >= 400:
        print(f"Sync failed: {r.status_code} {r.text}")
        sys.exit(1)
    print(f"Logged {len(items)} mealprep items.")


def update_mealpreps(db: dict):
    """Recompute remaining_g for active mealpreps based on all day entries."""
    if not SUPABASE_URL or not SUPABASE_KEY:
        return
    try:
        r = httpx.get(
            f"{SUPABASE_URL}/rest/v1/mealpreps?active=eq.true&order=created.asc",
            headers={"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"},
            timeout=10,
        )
        if r.status_code >= 400:
            return
        preps = r.json()
        if not preps:
            return

        for prep in preps:
            food = prep["food"]
            created = prep["created"]
            initial = prep["initial_g"]

            consumed = 0.0
            for day_key, day_data in db["days"].items():
                if day_key < created:
                    continue
                for entry in day_data.get("entries", []):
                    for item in entry.get("items", []):
                        if item["name"] == food:
                            consumed += parse_grams(item["quantity"])

            remaining = max(0.0, round(initial - consumed, 1))
            old_remaining = prep["remaining_g"]

            if remaining == old_remaining:
                continue

            update = {"remaining_g": remaining}
            if remaining <= 0:
                update["active"] = False
            httpx.patch(
                f"{SUPABASE_URL}/rest/v1/mealpreps?id=eq.{prep['id']}",
                headers=_supa_headers(),
                json=update,
                timeout=10,
            )

            if remaining <= 0 and old_remaining > 0:
                created_date = date.fromisoformat(created)
                days_lasted = (date.today() - created_date).days
                servings = sum(
                    1 for d, dd in db["days"].items() if d >= created
                    for e in dd.get("entries", [])
                    for it in e.get("items", []) if it["name"] == food
                )
                print(f"  {G}✓{RS} {food} finished! {initial:.0f}g over {days_lasted} days ({servings} servings)")
            else:
                print(f"  {DI}{food}: {remaining:.0f}g remaining ({initial - remaining:.0f}g used){RS}")
    except Exception as e:
        print(f"Mealprep update failed: {e}")


def prep_status():
    """Show active mealpreps."""
    if not SUPABASE_URL or not SUPABASE_KEY:
        print("Set PANTRI_SUPABASE_URL and PANTRI_SUPABASE_KEY env vars.")
        sys.exit(1)
    r = httpx.get(
        f"{SUPABASE_URL}/rest/v1/mealpreps?active=eq.true&order=created.asc",
        headers={"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"},
        timeout=10,
    )
    if r.status_code >= 400:
        print(f"Failed: {r.status_code} {r.text}")
        sys.exit(1)
    preps = r.json()
    if not preps:
        print("No active mealpreps.")
        return
    print()
    for p in preps:
        pct = p["remaining_g"] / p["initial_g"] * 100
        color = G if pct > 30 else (Y if pct > 10 else R)
        bar_w = 20
        filled = int(pct / 100 * bar_w)
        bar = f"{color}{'█' * filled}{DI}{'░' * (bar_w - filled)}{RS}"
        print(f"  {BO}{p['food']:<20}{RS}  {bar}  {color}{p['remaining_g']:.0f}g{RS} / {p['initial_g']:.0f}g  {DI}(since {p['created']}){RS}")
    print()


def pull_all():
    """Pull all data from Supabase into local DB."""
    if not SUPABASE_URL or not SUPABASE_KEY:
        print("Set PANTRI_SUPABASE_URL and PANTRI_SUPABASE_KEY env vars.")
        sys.exit(1)

    headers = {"apikey": SUPABASE_KEY, "Authorization": f"Bearer {SUPABASE_KEY}"}

    # Pull days
    r = httpx.get(
        f"{SUPABASE_URL}/rest/v1/days?order=date",
        headers=headers,
        timeout=30,
    )
    if r.status_code >= 400:
        print(f"Failed to pull days: {r.status_code} {r.text}")
        sys.exit(1)
    remote_days = r.json()

    # Pull weight
    r = httpx.get(
        f"{SUPABASE_URL}/rest/v1/weight?order=date",
        headers=headers,
        timeout=10,
    )
    weight_entries = r.json() if r.status_code < 400 else []

    # Load or create local DB
    if DB_FILE.exists():
        db = json.loads(DB_FILE.read_text())
    else:
        db = {"days": {}}

    new_days = 0
    updated_days = 0
    for row in remote_days:
        d = row["date"]
        if d not in db["days"]:
            new_days += 1
        else:
            updated_days += 1

        db["days"][d] = {
            "entries": row["entries"],
            "day_totals": row["day_totals"],
            "raw_text": row.get("raw_text", ""),
        }

        # Recreate day file
        DAYS_DIR.mkdir(exist_ok=True)
        raw = row.get("raw_text", "")
        if raw:
            day_file(d).write_text(raw)
        else:
            day_file(d).write_text(format_day_file(db["days"][d], d))

    if weight_entries:
        db["weight"] = {w["date"]: w["weight_kg"] for w in weight_entries}

    DB_FILE.write_text(json.dumps(db, indent=2, ensure_ascii=False))

    sync_foods()

    print(f"Pulled {len(remote_days)} days ({new_days} new, {updated_days} updated), {len(weight_entries)} weight entries.")


def new_day():
    """Create today's day file with empty meal sections."""
    today = date.today().isoformat()
    path = day_file(today)
    if path.exists():
        print(f"{path} already exists.")
        return
    path.write_text(f"{today}\n[breakfast]\n")
    print(f"Created {path}")


USAGE = """\
pantri — food tracker

usage:
  pantri                          open/create today's file, process it
  pantri <file>                   process a specific file
  pantri status                   show today's totals and remaining goals
  pantri status 2026-03-10        show totals for a specific day
  pantri sync                     pull all data from Supabase to local
  pantri push                     push all local data to Supabase
  pantri push-foods               upload foods.json to Supabase Storage
  pantri pull-foods               download foods.json from Supabase Storage
  pantri prep <file>              log a mealprep (file: one food per line, e.g. 'gulaschmeat 2000g')
  pantri prep-status              show active mealpreps

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

    if len(sys.argv) > 1 and sys.argv[1] == "sync":
        pull_all()
        return

    if len(sys.argv) > 1 and sys.argv[1] == "push":
        push()
        return

    if len(sys.argv) > 1 and sys.argv[1] == "push-foods":
        push_foods()
        return

    if len(sys.argv) > 1 and sys.argv[1] == "pull-foods":
        pull_foods()
        return

    if len(sys.argv) > 1 and sys.argv[1] == "prep":
        prep()
        return

    if len(sys.argv) > 1 and sys.argv[1] == "prep-status":
        prep_status()
        return

    if len(sys.argv) > 1 and sys.argv[1] == "new":
        new_day()
        return

    sync_foods()

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

    data = try_parse_locally(content)
    if data:
        print(f"All foods known — parsed locally (for {day})")
    else:
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
