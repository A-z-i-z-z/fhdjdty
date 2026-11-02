import Mathlib

open Finset

theorem o_vieta_cubes (x y : ℝ) (h1 : x + y = 5) (h2 : x * y = 6) : x ^ 3 + y ^ 3 = 35 := by
  have h3 : x ^ 3 + y ^ 3 = (x + y) ^ 3 - 3 * (x * y) * (x + y) := by
    ring
  rw [h3, h1, h2]
  norm_num

#print axioms o_vieta_cubes
