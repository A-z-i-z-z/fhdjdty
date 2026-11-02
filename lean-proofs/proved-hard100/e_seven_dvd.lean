import Mathlib

open Finset

theorem e_seven_dvd (n : ℕ) : 7 ∣ 8 ^ n - 1 := by
  induction n with
  | zero => norm_num
  | succ k ih =>
    have h1 : 0 < 8 ^ k := by positivity
    have h2 : 8 ^ (k + 1) - 1 = 8 * (8 ^ k - 1) + 7 := by
      rw [pow_succ]
      omega
    rw [h2]
    exact Nat.dvd_add (ih.mul_left 8) (dvd_refl 7)

#print axioms e_seven_dvd
