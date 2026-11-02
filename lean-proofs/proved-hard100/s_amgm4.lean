import Mathlib

open Finset

theorem s_amgm4 (a b c d : ℝ) : 4 * a * b * c * d ≤ a ^ 4 + b ^ 4 + c ^ 4 + d ^ 4 := by
  nlinarith [sq_nonneg (a ^ 2 - b ^ 2), sq_nonneg (c ^ 2 - d ^ 2), sq_nonneg (a * b - c * d)]

#print axioms s_amgm4
