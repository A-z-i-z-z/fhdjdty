import Mathlib

open Finset

theorem s_recip_sum (a b : ℝ) (ha : 0 < a) (hb : 0 < b) (h : a + b = 1) : 4 ≤ 1 / a + 1 / b := by
  rw [div_add_div _ _ ha.ne' hb.ne', le_div_iff₀ (mul_pos ha hb)]
  nlinarith [sq_nonneg (a - b), mul_pos ha hb, h]

#print axioms s_recip_sum
