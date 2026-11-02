import Mathlib

open Finset

theorem e_quad_roots (x : ℝ) (h : x ^ 2 - 5 * x + 6 = 0) : x = 2 ∨ x = 3 := by
  have h2 : (x - 2) * (x - 3) = 0 := by
    linear_combination h
  rcases mul_eq_zero.mp h2 with h3 | h3
  · left
    linarith
  · right
    linarith

#print axioms e_quad_roots
