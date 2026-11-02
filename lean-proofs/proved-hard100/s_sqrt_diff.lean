import Mathlib

open Finset

theorem s_sqrt_diff (x : ℝ) (hx : 0 < x) : Real.sqrt (x + 1) - Real.sqrt x ≤ 1 / (2 * Real.sqrt x) := by
  have hs : 0 < Real.sqrt x := Real.sqrt_pos.mpr hx
  have hs2 : Real.sqrt x ^ 2 = x := Real.sq_sqrt hx.le
  have ht2 : Real.sqrt (x + 1) ^ 2 = x + 1 := Real.sq_sqrt (by linarith)
  have ht0 : 0 ≤ Real.sqrt (x + 1) := Real.sqrt_nonneg _
  rw [le_div_iff₀ (by positivity)]
  nlinarith [sq_nonneg (Real.sqrt (x + 1) - Real.sqrt x)]

#print axioms s_sqrt_diff
