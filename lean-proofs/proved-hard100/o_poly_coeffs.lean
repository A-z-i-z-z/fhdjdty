import Mathlib

open Finset

theorem o_poly_coeffs (a b : ℝ) (h : ∀ x : ℝ, x ^ 2 + a * x + b = (x - 1) * (x - 2)) : a = -3 ∧ b = 2 := by
  have h0 := h 0
  have h1 := h 1
  norm_num at h0 h1
  constructor <;> linarith

#print axioms o_poly_coeffs
