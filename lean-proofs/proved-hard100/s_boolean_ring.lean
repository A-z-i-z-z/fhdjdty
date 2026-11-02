import Mathlib

open Finset

theorem s_boolean_ring {R : Type*} [Ring R] (h : ∀ x : R, x * x = x) (a : R) : a + a = 0 := by
  have h0 : (a + a) * (a + a) = a + a := h (a + a)
  have h1 : (a + a) * (a + a) = (a + a) + (a + a) := by
    simp only [add_mul, mul_add, h a]
  rw [h1] at h0
  have h2 : (a + a) + (a + a) = (a + a) + 0 := by
    rw [add_zero]
    exact h0
  exact add_left_cancel h2

#print axioms s_boolean_ring
