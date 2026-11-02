import Mathlib

open Finset

theorem ml_nesbitt (a b c : ℝ) (ha : 0 < a) (hb : 0 < b) (hc : 0 < c) : 3 / 2 ≤ a / (b + c) + b / (a + c) + c / (a + b) := by
  have h1 : b + c ≠ 0 := by positivity
  have h2 : a + c ≠ 0 := by positivity
  have h3 : a + b ≠ 0 := by positivity
  have hD : 0 < 2 * ((b + c) * (a + c) * (a + b)) := by positivity
  have key : a / (b + c) + b / (a + c) + c / (a + b) - 3 / 2
      = ((a + b) * (a - b) ^ 2 + (b + c) * (b - c) ^ 2 + (a + c) * (a - c) ^ 2)
        / (2 * ((b + c) * (a + c) * (a + b))) := by
    field_simp
    ring
  have hnn : 0 ≤ ((a + b) * (a - b) ^ 2 + (b + c) * (b - c) ^ 2 + (a + c) * (a - c) ^ 2)
        / (2 * ((b + c) * (a + c) * (a + b))) := by
    positivity
  linarith [key, hnn]

#print axioms ml_nesbitt
