import Mathlib

open Finset

theorem s_three_sq_mod8 (x y z : ℤ) : (x ^ 2 + y ^ 2 + z ^ 2) % 8 ≠ 7 := by
  have key : ∀ t : ℤ, t ^ 2 % 8 = 0 ∨ t ^ 2 % 8 = 1 ∨ t ^ 2 % 8 = 4 := by
    intro t
    have h : t % 8 = 0 ∨ t % 8 = 1 ∨ t % 8 = 2 ∨ t % 8 = 3 ∨ t % 8 = 4 ∨ t % 8 = 5 ∨ t % 8 = 6 ∨ t % 8 = 7 := by
      omega
    rcases h with h | h | h | h | h | h | h | h <;>
      simp [pow_two, Int.mul_emod, h]
  intro hcontra
  have hx := key x
  have hy := key y
  have hz := key z
  generalize x ^ 2 = a at *
  generalize y ^ 2 = b at *
  generalize z ^ 2 = c at *
  omega

#print axioms s_three_sq_mod8
