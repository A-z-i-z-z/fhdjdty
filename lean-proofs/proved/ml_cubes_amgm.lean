import Mathlib

open Finset

theorem ml_cubes_amgm (a b c : ℝ) (ha : 0 ≤ a) (hb : 0 ≤ b) (hc : 0 ≤ c) : 3 * a * b * c ≤ a ^ 3 + b ^ 3 + c ^ 3 := by
  nlinarith [mul_nonneg (add_nonneg (add_nonneg ha hb) hc) (add_nonneg (add_nonneg (sq_nonneg (a - b)) (sq_nonneg (b - c))) (sq_nonneg (c - a)))]

#print axioms ml_cubes_amgm
