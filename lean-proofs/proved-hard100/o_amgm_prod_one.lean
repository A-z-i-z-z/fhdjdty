import Mathlib

open Finset

theorem o_amgm_prod_one (a b c : ℝ) (ha : 0 < a) (hb : 0 < b) (hc : 0 < c) (h : a * b * c = 1) : 3 ≤ a + b + c := by
  have hs : 0 < a + b + c := by linarith
  have key : 27 * (a * b * c) ≤ (a + b + c) ^ 3 := by
    nlinarith [mul_nonneg hs.le (sq_nonneg (a - b)), mul_nonneg hs.le (sq_nonneg (b - c)),
      mul_nonneg hs.le (sq_nonneg (c - a)), mul_nonneg ha.le (sq_nonneg (b - c)),
      mul_nonneg hb.le (sq_nonneg (c - a)), mul_nonneg hc.le (sq_nonneg (a - b))]
  rw [h] at key
  by_contra hcon
  push_neg at hcon
  have h3 : 0 < 3 - (a + b + c) := by linarith
  nlinarith [mul_pos hs h3, mul_pos (mul_pos hs hs) h3]

#print axioms o_amgm_prod_one
