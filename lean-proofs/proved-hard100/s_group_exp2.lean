import Mathlib

open Finset

theorem s_group_exp2 {G : Type*} [Group G] (h : ∀ g : G, g * g = 1) (a b : G) : a * b = b * a := by
  have ha : a⁻¹ = a := inv_eq_of_mul_eq_one_right (h a)
  have hb : b⁻¹ = b := inv_eq_of_mul_eq_one_right (h b)
  have hab : (a * b)⁻¹ = a * b := inv_eq_of_mul_eq_one_right (h (a * b))
  rw [mul_inv_rev, ha, hb] at hab
  exact hab.symm

#print axioms s_group_exp2
