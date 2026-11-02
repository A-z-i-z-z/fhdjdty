import Mathlib

open Finset

theorem s_cassini (n : ℕ) : (Nat.fib (n + 1) : ℤ) ^ 2 - (Nat.fib n : ℤ) * (Nat.fib (n + 2) : ℤ) = (-1) ^ n := by
  induction n with
  | zero => norm_num [Nat.fib_two]
  | succ k ih =>
    have h1 : Nat.fib (k + 2) = Nat.fib k + Nat.fib (k + 1) := Nat.fib_add_two
    have h2 : Nat.fib (k + 1 + 2) = Nat.fib (k + 1) + Nat.fib (k + 2) := Nat.fib_add_two
    have h1' : (Nat.fib (k + 2) : ℤ) = (Nat.fib k : ℤ) + (Nat.fib (k + 1) : ℤ) := by
      exact_mod_cast h1
    have h2' : (Nat.fib (k + 1 + 2) : ℤ) = (Nat.fib (k + 1) : ℤ) + (Nat.fib (k + 2) : ℤ) := by
      exact_mod_cast h2
    show (Nat.fib (k + 2) : ℤ) ^ 2 - (Nat.fib (k + 1) : ℤ) * (Nat.fib (k + 1 + 2) : ℤ) = (-1) ^ (k + 1)
    linear_combination (-1 : ℤ) * ih + (Nat.fib (k + 2) : ℤ) * h1' + (-(Nat.fib (k + 1) : ℤ)) * h2'

#print axioms s_cassini
