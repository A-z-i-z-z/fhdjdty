import Mathlib

open Finset

theorem o_cubes_zero_sum (a b c : ℝ) (h : a + b + c = 0) : a ^ 3 + b ^ 3 + c ^ 3 = 3 * a * b * c := by
  have hc : c = -a - b := by linarith
  subst hc
  ring

#print axioms o_cubes_zero_sum
