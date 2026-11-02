import Mathlib

open Finset

theorem s_fib_add (m n : ℕ) : Nat.fib (m + n + 1) = Nat.fib m * Nat.fib n + Nat.fib (m + 1) * Nat.fib (n + 1) := by
  induction n generalizing m with
  | zero => simp
  | succ n ih =>
    have h := ih (m + 1)
    have e1 : m + (n + 1) + 1 = m + 1 + n + 1 := by omega
    have f1 : Nat.fib (n + 1 + 1) = Nat.fib n + Nat.fib (n + 1) := Nat.fib_add_two
    have f2 : Nat.fib (m + 1 + 1) = Nat.fib m + Nat.fib (m + 1) := Nat.fib_add_two
    rw [e1, h, f1, f2]
    ring

#print axioms s_fib_add
