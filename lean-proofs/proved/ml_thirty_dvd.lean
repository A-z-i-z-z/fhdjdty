import Mathlib

open Finset

theorem ml_thirty_dvd (n : ℤ) : 30 ∣ n ^ 5 - n := by
  have key : ∀ m : ℤ, 0 ≤ m → m < 30 → (30 ∣ m ^ 5 - m) := by
    intro m h0 h1
    interval_cases m <;> norm_num
  have hm := key (n % 30) (Int.emod_nonneg _ (by norm_num)) (Int.emod_lt_of_pos _ (by norm_num))
  have hmod := ((Int.mod_modEq n 30).symm.pow 5).sub (Int.mod_modEq n 30).symm
  have hmod' : (n ^ 5 - n) % 30 = ((n % 30) ^ 5 - n % 30) % 30 := hmod
  rw [Int.dvd_iff_emod_eq_zero, hmod']
  exact Int.dvd_iff_emod_eq_zero.mp hm

#print axioms ml_thirty_dvd
