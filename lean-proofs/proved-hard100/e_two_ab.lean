import Mathlib

open Finset

theorem e_two_ab (a b : ℝ) : 2 * a * b ≤ a ^ 2 + b ^ 2 := by
  nlinarith [sq_nonneg (a - b)]

#print axioms e_two_ab
