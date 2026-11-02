import Mathlib

open Finset

theorem ml_no_sol_mod_three : ¬ ∃ x y : ℤ, x ^ 2 - 3 * y ^ 2 = 2 := by
  have key : ∀ a b : ZMod 3, a ^ 2 - 3 * b ^ 2 ≠ 2 := by
    decide
  rintro ⟨x, y, h⟩
  have h2 := congrArg (Int.cast : ℤ → ZMod 3) h
  push_cast at h2
  exact key (x : ZMod 3) (y : ZMod 3) h2

#print axioms ml_no_sol_mod_three
