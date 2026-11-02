import Mathlib

open Finset

theorem e_lin_sys (x y : ℝ) (h1 : 2 * x + 3 * y = 7) (h2 : x - y = 1) : x = 2 ∧ y = 1 := by
  have hy : y = 1 := by
    linarith
  have hx : x = 2 := by
    linarith
  exact ⟨hx, hy⟩

#print axioms e_lin_sys
