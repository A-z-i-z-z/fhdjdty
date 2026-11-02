import Mathlib

open Finset

theorem o_sq_sum_third (a b c : ℝ) (h : a + b + c = 1) : 1 / 3 ≤ a ^ 2 + b ^ 2 + c ^ 2 := by
  have hc : c = 1 - a - b := by linarith
  subst hc
  nlinarith [sq_nonneg (a - b), sq_nonneg (a + 2 * b - 1), sq_nonneg (2 * a + b - 1)]

#print axioms o_sq_sum_third
