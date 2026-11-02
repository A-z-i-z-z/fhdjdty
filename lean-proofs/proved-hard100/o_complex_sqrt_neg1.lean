import Mathlib

open Finset

theorem o_complex_sqrt_neg1 (z : ℂ) (h : z ^ 2 = -1) : z = Complex.I ∨ z = -Complex.I := by
  have hI : Complex.I ^ 2 = -1 := Complex.I_sq
  have h2 : (z - Complex.I) * (z + Complex.I) = 0 := by
    linear_combination h - hI
  rcases mul_eq_zero.mp h2 with h3 | h3
  · left
    linear_combination h3
  · right
    linear_combination h3

#print axioms o_complex_sqrt_neg1
