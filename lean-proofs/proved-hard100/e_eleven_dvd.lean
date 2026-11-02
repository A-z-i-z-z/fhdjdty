import Mathlib

open Finset

theorem e_eleven_dvd (n : ℕ) : 11 ∣ 10 ^ (2 * n) - 1 := by
  have h : 10 ^ (2 * n) % 11 = 1 := by
    rw [pow_mul, Nat.pow_mod]
    norm_num
  have hp : 0 < 10 ^ (2 * n) := by
    positivity
  omega

#print axioms e_eleven_dvd
