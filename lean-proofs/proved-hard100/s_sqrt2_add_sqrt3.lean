import Mathlib

open Finset

theorem sqrt6_irrational_aux : Irrational (Real.sqrt 6) := by
  apply irrational_nrt_of_notint_nrt 2 6
  · have h := Real.sq_sqrt (show (0:ℝ) ≤ 6 by norm_num)
    push_cast
    linarith
  · rintro ⟨y, hy⟩
    have h1 : (y:ℝ)^2 = 6 := by
      rw [← hy]
      exact Real.sq_sqrt (show (0:ℝ) ≤ 6 by norm_num)
    have h2 : y^2 = 6 := by exact_mod_cast h1
    have h3 : -3 < y := by nlinarith
    have h4 : y < 3 := by nlinarith
    interval_cases y <;> norm_num at h2
  · norm_num

theorem s_sqrt2_add_sqrt3 : Irrational (Real.sqrt 2 + Real.sqrt 3) := by
  rintro ⟨q, hq⟩
  have h2 : Real.sqrt 2 ^ 2 = 2 := Real.sq_sqrt (by norm_num)
  have h3 : Real.sqrt 3 ^ 2 = 3 := Real.sq_sqrt (by norm_num)
  have h6eq : Real.sqrt 6 = Real.sqrt 2 * Real.sqrt 3 := by
    rw [← Real.sqrt_mul (show (0:ℝ) ≤ 2 by norm_num)]
    norm_num
  have e : (Real.sqrt 2 + Real.sqrt 3)^2 = 5 + 2 * Real.sqrt 6 := by
    linear_combination h2 + h3 - 2 * h6eq
  refine sqrt6_irrational_aux (Set.mem_range.mpr ⟨(q^2 - 5)/2, ?_⟩)
  push_cast
  rw [hq]
  linear_combination (1/2 : ℝ) * e

#print axioms s_sqrt2_add_sqrt3
