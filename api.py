"""Pantri API — exposes food tracking data over HTTP."""

import json
from datetime import date
from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

DB_FILE = Path("pantri_data.json")

GOAL_KCAL = 3500
GOAL_PROTEIN = 200.0

app = FastAPI()
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


def load_db() -> dict:
    if DB_FILE.exists():
        return json.loads(DB_FILE.read_text())
    return {"days": {}}


@app.get("/api/today")
def get_today():
    db = load_db()
    today = date.today().isoformat()
    day = db["days"].get(today)

    if not day:
        return {
            "date": today,
            "entries": [],
            "day_totals": {"kcal": 0, "protein_g": 0, "carbs_g": 0, "fat_g": 0, "cost_eur": 0},
            "goals": {"kcal": GOAL_KCAL, "protein_g": GOAL_PROTEIN},
            "remaining": {"kcal": GOAL_KCAL, "protein_g": GOAL_PROTEIN},
        }

    dt = day["day_totals"]
    return {
        "date": today,
        "entries": day["entries"],
        "day_totals": dt,
        "goals": {"kcal": GOAL_KCAL, "protein_g": GOAL_PROTEIN},
        "remaining": {
            "kcal": GOAL_KCAL - dt["kcal"],
            "protein_g": round(GOAL_PROTEIN - dt["protein_g"], 1),
        },
    }


@app.get("/api/days")
def get_days():
    db = load_db()
    return [
        {"date": d, "day_totals": data["day_totals"], "entry_count": len(data["entries"])}
        for d, data in sorted(db["days"].items(), reverse=True)
    ]


@app.get("/api/days/{day}")
def get_day(day: str):
    db = load_db()
    data = db["days"].get(day)
    if not data:
        return {"date": day, "entries": [], "day_totals": {"kcal": 0, "protein_g": 0, "carbs_g": 0, "fat_g": 0, "cost_eur": 0}}
    return {"date": day, **data}
