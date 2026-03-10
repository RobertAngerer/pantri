#!/usr/bin/env python3
"""Recalculate kcal from macros in foods.json using Atwater factors."""

import json
import sys
from pathlib import Path

KCAL_PER_GRAM = {
    "protein_g": 4,
    "carbs_g": 4,
    "fat_g": 9,
}


def kcal_from_macros(info: dict) -> int:
    return round(sum(info[macro] * factor for macro, factor in KCAL_PER_GRAM.items()))


def cent_per_g_protein(info: dict) -> float | None:
    if info["protein_g"] == 0:
        return None
    return round(info["cost_eur_per_100g"] * 100 / info["protein_g"], 2)


def main():
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("foods.json")
    if not path.exists():
        print(f"File not found: {path}")
        sys.exit(1)

    foods = json.loads(path.read_text())

    for name, info in foods.items():
        old_kcal = info.get("kcal", 0)
        new_kcal = kcal_from_macros(info)
        old_cpp = info.get("cent_per_g_protein")
        new_cpp = cent_per_g_protein(info)

        changes = []
        if old_kcal != new_kcal:
            changes.append(f"kcal {old_kcal} -> {new_kcal}")
            info["kcal"] = new_kcal
        if old_cpp != new_cpp:
            changes.append(f"c/gP {old_cpp} -> {new_cpp}")
            info["cent_per_g_protein"] = new_cpp

        if changes:
            print(f"  {name}: {', '.join(changes)}")

    path.write_text(json.dumps(foods, indent=2, ensure_ascii=False) + "\n")
    print("Done.")


if __name__ == "__main__":
    main()
