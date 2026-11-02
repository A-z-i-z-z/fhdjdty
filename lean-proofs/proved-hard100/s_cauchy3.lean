import Mathlib

open Finset

theorem s_cauchy3 (a b c x y z : ℝ) : (a * x + b * y + c * z) ^ 2 ≤ (a ^ 2 + b ^ 2 + c ^ 2) * (x ^ 2 + y ^ 2 + z ^ 2) := by
  nlinarith [sq_nonneg (a * y - b * x), sq_nonneg (a * z - c * x), sq_nonneg (b * z - c * y)]

#print axioms s_cauchy3
