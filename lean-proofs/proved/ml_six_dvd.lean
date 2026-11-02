import Mathlib

open Finset

theorem ml_six_dvd (n : ℕ) : 6 ∣ n * (n + 1) * (2 * n + 1) := by
  have h : n % 6 = 0 ∨ n % 6 = 1 ∨ n % 6 = 2 ∨ n % 6 = 3 ∨ n % 6 = 4 ∨ n % 6 = 5 := by
    omega
  apply Nat.dvd_of_mod_eq_zero
  rcases h with h | h | h | h | h | h <;>
    first
    | simp [Nat.mul_mod, Nat.add_mod, h]
    | norm_num [Nat.mul_mod, Nat.add_mod, h]

#print axioms ml_six_dvd
