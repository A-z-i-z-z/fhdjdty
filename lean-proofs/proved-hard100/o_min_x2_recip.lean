import Mathlib

open Finset

theorem o_min_x2_recip (x : ℝ) (hx : 0 < x) : 47 / 25 ≤ x ^ 2 + 1 / x := by
  have key : 0 ≤ 25 * x ^ 3 - 47 * x + 25 := by
    nlinarith [mul_nonneg hx.le (sq_nonneg (x - 4 / 5)), sq_nonneg (x - 63 / 80)]
  have hne : x ≠ 0 := ne_of_gt hx
  have hinv : x * (1 / x) = 1 := mul_one_div_cancel hne
  nlinarith [key, hinv, hx]

#print axioms o_min_x2_recip
