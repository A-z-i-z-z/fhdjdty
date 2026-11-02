import Mathlib

open Finset

theorem e_two_pow_le_fact (n : ℕ) : 2 ^ n ≤ Nat.factorial (n + 1) := by
  induction n with
  | zero => simp [Nat.factorial]
  | succ k ih =>
    rw [pow_succ, Nat.factorial_succ (k + 1)]
    have h := Nat.factorial_pos (k + 1)
    nlinarith [ih, h, Nat.zero_le k, Nat.zero_le (k * Nat.factorial (k + 1))]

#print axioms e_two_pow_le_fact
