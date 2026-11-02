import Mathlib

open Finset

theorem e_cube_mod9 (n : ℤ) : n ^ 3 % 9 = 0 ∨ n ^ 3 % 9 = 1 ∨ n ^ 3 % 9 = 8 := by
  have h : n % 9 = 0 ∨ n % 9 = 1 ∨ n % 9 = 2 ∨ n % 9 = 3 ∨ n % 9 = 4 ∨ n % 9 = 5 ∨ n % 9 = 6 ∨ n % 9 = 7 ∨ n % 9 = 8 := by
    omega
  rcases h with h | h | h | h | h | h | h | h | h <;>
    simp [pow_succ, Int.mul_emod, h]

#print axioms e_cube_mod9
