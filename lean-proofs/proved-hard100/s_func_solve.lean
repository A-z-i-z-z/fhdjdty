import Mathlib

open Finset

theorem s_func_solve (f : ℝ → ℝ) (h : ∀ x, f x + 2 * f (1 - x) = x ^ 2) : ∀ x, f x = (x ^ 2 - 4 * x + 2) / 3 := by
  intro x
  have h1 := h x
  have h2 : f (1 - x) + 2 * f x = (1 - x) ^ 2 := by
    have h3 := h (1 - x)
    rw [sub_sub_cancel] at h3
    exact h3
  linear_combination (-1/3 : ℝ) * h1 + (2/3 : ℝ) * h2

#print axioms s_func_solve
