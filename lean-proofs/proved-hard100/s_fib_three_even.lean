import Mathlib

open Finset

theorem s_fib_three_even_aux (n : ℕ) :
    Nat.fib (3 * n) % 2 = 0 ∧ Nat.fib (3 * n + 1) % 2 = 1 ∧ Nat.fib (3 * n + 2) % 2 = 1 := by
  induction n with
  | zero => simp [Nat.fib_zero, Nat.fib_one, Nat.fib_two]
  | succ k ih =>
    obtain ⟨A, B, C⟩ := ih
    have e1 := Nat.fib_add_two (n := 3 * k + 1)
    have e2 := Nat.fib_add_two (n := 3 * k + 2)
    have e3 := Nat.fib_add_two (n := 3 * k + 3)
    rw [show 3 * k + 1 + 2 = 3 * k + 3 by omega, show 3 * k + 1 + 1 = 3 * k + 2 by omega] at e1
    rw [show 3 * k + 2 + 2 = 3 * k + 4 by omega, show 3 * k + 2 + 1 = 3 * k + 3 by omega] at e2
    rw [show 3 * k + 3 + 2 = 3 * k + 5 by omega, show 3 * k + 3 + 1 = 3 * k + 4 by omega] at e3
    rw [show 3 * (k + 1) + 2 = 3 * k + 5 by omega, show 3 * (k + 1) + 1 = 3 * k + 4 by omega,
      show 3 * (k + 1) = 3 * k + 3 by omega]
    omega

theorem s_fib_three_even (n : ℕ) : Even (Nat.fib (3 * n)) := by
  exact Nat.even_iff.mpr (s_fib_three_even_aux n).1

#print axioms s_fib_three_even
