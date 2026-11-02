import Mathlib

open Finset

theorem e_gcd_fib (n : ℕ) : Nat.gcd (Nat.fib n) (Nat.fib (n + 1)) = 1 := by
  first
    | exact Nat.fib_coprime_fib_succ n
    | exact Nat.coprime_fib_succ n
    | (induction n with
        | zero => simp
        | succ k ih =>
          have h : Nat.fib (k + 1 + 1) = Nat.fib k + Nat.fib (k + 1) := Nat.fib_add_two
          rw [h, Nat.gcd_add_self_right, Nat.gcd_comm]
          exact ih)

#print axioms e_gcd_fib
