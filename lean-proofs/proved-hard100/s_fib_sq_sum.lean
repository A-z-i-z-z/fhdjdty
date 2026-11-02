import Mathlib

open Finset

theorem s_fib_sq_sum (n : ℕ) : ∑ k ∈ range (n + 1), Nat.fib k ^ 2 = Nat.fib n * Nat.fib (n + 1) := by
  induction n with
  | zero => simp
  | succ m ih =>
    have h : Nat.fib (m + 1 + 1) = Nat.fib m + Nat.fib (m + 1) := Nat.fib_add_two
    rw [Finset.sum_range_succ, ih, h]
    ring

#print axioms s_fib_sq_sum
