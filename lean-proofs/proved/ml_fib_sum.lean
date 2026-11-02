import Mathlib

open Finset

theorem ml_fib_sum (n : ℕ) : ∑ k ∈ range n, Nat.fib k + 1 = Nat.fib (n + 1) := by
  induction n with
  | zero => simp
  | succ m ih =>
    rw [sum_range_succ]
    have h : Nat.fib (m + 1 + 1) = Nat.fib m + Nat.fib (m + 1) := Nat.fib_add_two
    omega

#print axioms ml_fib_sum
