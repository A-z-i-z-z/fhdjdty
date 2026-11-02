import Mathlib

open Finset

theorem e_digits_mod9 (n : ℕ) : n % 9 = (Nat.digits 10 n).sum % 9 := by
  first
    | exact Nat.modEq_nine_digits_sum n
    | simpa [Nat.ModEq] using Nat.modEq_nine_digits_sum n

#print axioms e_digits_mod9
