import Mathlib

open Finset

theorem s_cubes_four : ¬ ∃ x y : ℤ, x ^ 3 + y ^ 3 = 4 := by
  have key : ∀ a : ℤ, a ^ 3 % 9 = 0 ∨ a ^ 3 % 9 = 1 ∨ a ^ 3 % 9 = 8 := by
    intro a
    have h : a % 9 = 0 ∨ a % 9 = 1 ∨ a % 9 = 2 ∨ a % 9 = 3 ∨ a % 9 = 4 ∨
        a % 9 = 5 ∨ a % 9 = 6 ∨ a % 9 = 7 ∨ a % 9 = 8 := by omega
    rcases h with h | h | h | h | h | h | h | h | h <;>
      norm_num [pow_succ, Int.mul_emod, h]
  rintro ⟨x, y, h⟩
  have hx := key x
  have hy := key y
  have h4 : (x ^ 3 + y ^ 3) % 9 = 4 := by
    rw [h]
    norm_num
  rw [Int.add_emod] at h4
  rcases hx with hx | hx | hx <;> rcases hy with hy | hy | hy <;>
    rw [hx, hy] at h4 <;> norm_num at h4

#print axioms s_cubes_four
