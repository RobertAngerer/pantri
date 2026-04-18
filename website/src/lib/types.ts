export type Totals = {
  kcal: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
  cost_eur: number;
};

export type FoodItem = {
  name: string;
  quantity: string;
  kcal: number;
  protein_g: number;
  carbs_g: number;
  fat_g: number;
  cost_eur: number;
};

export type Entry = {
  label: string;
  timestamp?: string;
  items: FoodItem[];
  totals: Totals;
};

export type Day = {
  date: string;
  entries: Entry[];
  day_totals: Totals;
  raw_text?: string;
};

export type WeightEntry = {
  date: string;
  weight_kg: number;
};

export type PantryItem = {
  food: string;
  quantity_g: number;
  min_quantity_g: number;
  updated_at: string;
};

export type MealPrep = {
  id: number;
  food: string;
  initial_g: number;
  remaining_g: number;
  created: string;
  active: boolean;
};

export const EMPTY_TOTALS: Totals = {
  kcal: 0,
  protein_g: 0,
  carbs_g: 0,
  fat_g: 0,
  cost_eur: 0
};

export const GOALS = {
  kcal: 3500,
  protein_g: 250,
  fat_g: 100,
  budget_monthly_eur: 600
};

export function goalCarbs(): number {
  return (GOALS.kcal - GOALS.protein_g * 4 - GOALS.fat_g * 9) / 4;
}
