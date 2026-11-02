import Mathlib

open Finset

theorem o_cos36_cos72 : Real.cos (Real.pi / 5) - Real.cos (2 * Real.pi / 5) = 1 / 2 := by
  have hpos : 0 < Real.cos (Real.pi / 5) := by
    apply Real.cos_pos_of_mem_Ioo
    constructor <;> linarith [Real.pi_pos]
  have h2 : Real.cos (2 * Real.pi / 5) = 2 * Real.cos (Real.pi / 5) ^ 2 - 1 := by
    rw [show 2 * Real.pi / 5 = 2 * (Real.pi / 5) by ring, Real.cos_two_mul]
  have h3 : Real.cos (3 * (Real.pi / 5)) = -Real.cos (2 * Real.pi / 5) := by
    rw [show 3 * (Real.pi / 5) = Real.pi - 2 * Real.pi / 5 by ring, Real.cos_pi_sub]
  have h4 : Real.cos (3 * (Real.pi / 5))
      = 4 * Real.cos (Real.pi / 5) ^ 3 - 3 * Real.cos (Real.pi / 5) :=
    Real.cos_three_mul _
  have key : (Real.cos (Real.pi / 5) + 1)
      * (4 * Real.cos (Real.pi / 5) ^ 2 - 2 * Real.cos (Real.pi / 5) - 1) = 0 := by
    linear_combination (-1 : ℝ) * h4 + h3 - h2
  rcases mul_eq_zero.mp key with h | h
  · linarith
  · rw [h2]
    linarith

#print axioms o_cos36_cos72
