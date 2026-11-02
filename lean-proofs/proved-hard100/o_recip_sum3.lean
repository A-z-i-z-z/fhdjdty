import Mathlib

open Finset

theorem o_recip_sum3 (a b c : ℝ) (ha : 0 < a) (hb : 0 < b) (hc : 0 < c) (h : a + b + c = 3) : 3 ≤ 1 / a + 1 / b + 1 / c := by
  rw [div_add_div _ _ ha.ne' hb.ne', div_add_div _ _ (mul_pos ha hb).ne' hc.ne',
    le_div_iff₀ (mul_pos (mul_pos ha hb) hc)]
  obtain rfl : c = 3 - a - b := by linarith
  nlinarith [mul_nonneg ha.le (sq_nonneg (b - (3 - a - b))),
    mul_nonneg hb.le (sq_nonneg (a - (3 - a - b))),
    mul_nonneg hc.le (sq_nonneg (a - b))]

#print axioms o_recip_sum3
