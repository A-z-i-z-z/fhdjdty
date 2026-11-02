import Mathlib

open Finset

theorem s_exp_quad (x : ℝ) (hx : 0 ≤ x) : 1 + x + x ^ 2 / 2 ≤ Real.exp x := by
  first
  | exact Real.quadratic_le_exp_of_nonneg hx
  | (have h := Real.sum_le_exp_of_nonneg hx 3; norm_num [Finset.sum_range_succ, Nat.factorial] at h; linarith)

#print axioms s_exp_quad
