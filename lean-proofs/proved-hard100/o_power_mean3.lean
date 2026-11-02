import Mathlib

open Finset

theorem o_power_mean3 (a b : ℝ) (ha : 0 ≤ a) (hb : 0 ≤ b) : ((a + b) / 2) ^ 3 ≤ (a ^ 3 + b ^ 3) / 2 := by
  nlinarith [mul_nonneg (add_nonneg ha hb) (sq_nonneg (a - b))]

#print axioms o_power_mean3
