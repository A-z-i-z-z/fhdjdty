import Mathlib

open Finset

theorem s_sqrt3_irr : Irrational (Real.sqrt 3) := by
  have h : Irrational (Real.sqrt ((3:ℕ):ℝ)) := by
    first
      | exact Nat.Prime.irrational_sqrt (by norm_num)
      | exact irrational_sqrt_natCast_iff.mpr (by decide)
      | exact irrational_sqrt_natCast_iff.mpr (by norm_num)
  first
    | simpa using h
    | (norm_num at h; exact h)

#print axioms s_sqrt3_irr
