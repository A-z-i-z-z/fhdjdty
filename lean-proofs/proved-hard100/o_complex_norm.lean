import Mathlib

open Finset

theorem o_complex_norm : ‖(1 + Complex.I) ^ 10‖ = 32 := by
  have h2 : (1 + Complex.I) ^ 2 = 2 * Complex.I := by
    linear_combination Complex.I_sq
  have h10 : (1 + Complex.I) ^ 10 = 32 * Complex.I := by
    have h5 : (1 + Complex.I) ^ 10 = ((1 + Complex.I) ^ 2) ^ 5 := by ring
    rw [h5, h2]
    linear_combination (32 * Complex.I * (Complex.I ^ 2 - 1)) * Complex.I_sq
  rw [h10, norm_mul, Complex.norm_I, mul_one]
  norm_num

#print axioms o_complex_norm
