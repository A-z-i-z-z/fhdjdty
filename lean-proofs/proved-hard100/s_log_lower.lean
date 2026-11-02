import Mathlib

open Finset

theorem s_log_lower (x : ℝ) (hx : 0 < x) : 1 - 1 / x ≤ Real.log x := by
  have h1 : Real.log (1 / x) ≤ 1 / x - 1 :=
    Real.log_le_sub_one_of_pos (by positivity)
  have h2 : Real.log (1 / x) = - Real.log x := by
    rw [one_div, Real.log_inv]
  linarith

#print axioms s_log_lower
