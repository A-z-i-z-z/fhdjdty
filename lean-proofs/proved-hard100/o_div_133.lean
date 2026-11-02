import Mathlib

open Finset

theorem o_div_133 (n : ℕ) : 133 ∣ 11 ^ (n + 2) + 12 ^ (2 * n + 1) := by
  induction n with
  | zero => norm_num
  | succ k ih =>
    have h : 11 ^ (k + 1 + 2) + 12 ^ (2 * (k + 1) + 1)
        = 11 * (11 ^ (k + 2) + 12 ^ (2 * k + 1)) + 133 * 12 ^ (2 * k + 1) := by
      ring
    rw [h]
    exact dvd_add (dvd_mul_of_dvd_right ih 11) (dvd_mul_right 133 _)

#print axioms o_div_133
