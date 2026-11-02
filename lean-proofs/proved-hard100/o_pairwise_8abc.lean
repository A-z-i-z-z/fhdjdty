import Mathlib

open Finset

theorem o_pairwise_8abc (a b c : ℝ) (ha : 0 < a) (hb : 0 < b) (hc : 0 < c) : 8 * a * b * c ≤ (a + b) * (b + c) * (c + a) := by
  nlinarith [mul_nonneg hb.le (sq_nonneg (a - c)), mul_nonneg ha.le (sq_nonneg (b - c)), mul_nonneg hc.le (sq_nonneg (a - b))]

#print axioms o_pairwise_8abc
