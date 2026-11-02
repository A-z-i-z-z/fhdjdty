import Mathlib

open Finset

theorem o_sum3_pairs (x y z : ℝ) (h : x + y + z = 3) : x * y + y * z + z * x ≤ 3 := by
  have h2 : (x + y + z) ^ 2 = 9 := by
    rw [h]
    norm_num
  nlinarith [sq_nonneg (x - y), sq_nonneg (y - z), sq_nonneg (z - x), h2]

#print axioms o_sum3_pairs
