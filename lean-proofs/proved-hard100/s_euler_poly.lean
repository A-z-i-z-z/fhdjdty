import Mathlib

open Finset

theorem s_euler_poly : ¬ ∀ n : ℕ, Nat.Prime (n ^ 2 + n + 41) := by
  intro h
  have h40 := h 40
  norm_num at h40

#print axioms s_euler_poly
