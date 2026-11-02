import Mathlib

open Finset

theorem o_trig_quartic (x : ℝ) : Real.sin x ^ 4 + Real.cos x ^ 4 = 1 - 2 * Real.sin x ^ 2 * Real.cos x ^ 2 := by
  linear_combination (Real.sin x ^ 2 + Real.cos x ^ 2 + 1) * Real.sin_sq_add_cos_sq x

#print axioms o_trig_quartic
