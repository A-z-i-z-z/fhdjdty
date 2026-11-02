import Mathlib

open Finset

theorem o_titu (a b c : ℝ) (ha : 0 < a) (hb : 0 < b) (hc : 0 < c) : a + b + c ≤ a ^ 2 / b + b ^ 2 / c + c ^ 2 / a := by
  have h1 : 2 * a - b ≤ a ^ 2 / b := by
    rw [le_div_iff₀ hb]
    nlinarith [sq_nonneg (a - b)]
  have h2 : 2 * b - c ≤ b ^ 2 / c := by
    rw [le_div_iff₀ hc]
    nlinarith [sq_nonneg (b - c)]
  have h3 : 2 * c - a ≤ c ^ 2 / a := by
    rw [le_div_iff₀ ha]
    nlinarith [sq_nonneg (c - a)]
  linarith [h1, h2, h3]

#print axioms o_titu
