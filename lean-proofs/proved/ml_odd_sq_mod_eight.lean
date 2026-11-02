import Mathlib

open Finset

theorem ml_odd_sq_mod_eight (n : ℤ) (hn : Odd n) : n ^ 2 % 8 = 1 := by
  obtain ⟨k, rfl⟩ := hn
  obtain ⟨m, hm⟩ := Int.even_mul_succ_self k
  have h : (2 * k + 1) ^ 2 = 4 * (k * (k + 1)) + 1 := by ring
  rw [h, hm]
  omega

#print axioms ml_odd_sq_mod_eight
