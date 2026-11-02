import Mathlib

open Finset

theorem s_two_sq_mod4_sq (x : ℤ) : x ^ 2 % 4 = 0 ∨ x ^ 2 % 4 = 1 := by
  have h : x % 4 = 0 ∨ x % 4 = 1 ∨ x % 4 = 2 ∨ x % 4 = 3 := by omega
  rw [pow_two, Int.mul_emod]
  rcases h with h | h | h | h <;> norm_num [h]

theorem s_two_sq_mod4 (x y : ℤ) : (x ^ 2 + y ^ 2) % 4 ≠ 3 := by
  have hx := s_two_sq_mod4_sq x
  have hy := s_two_sq_mod4_sq y
  rw [Int.add_emod]
  rcases hx with hx | hx <;> rcases hy with hy | hy <;> rw [hx, hy] <;> norm_num

#print axioms s_two_sq_mod4
