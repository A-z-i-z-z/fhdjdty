import Mathlib

open Finset

theorem e_pow5_last_digit (n : ℕ) : n ^ 5 % 10 = n % 10 := by
  rw [Nat.pow_mod]
  have hlt : n % 10 < 10 := Nat.mod_lt _ (by norm_num)
  generalize n % 10 = m at hlt ⊢
  interval_cases m <;> norm_num

#print axioms e_pow5_last_digit
