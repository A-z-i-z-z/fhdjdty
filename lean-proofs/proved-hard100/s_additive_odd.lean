import Mathlib

open Finset

theorem s_additive_odd (f : ℝ → ℝ) (h : ∀ x y, f (x + y) = f x + f y) : f 0 = 0 ∧ ∀ x, f (-x) = -f x := by
  have h00 := h 0 0
  rw [add_zero] at h00
  have hf0 : f 0 = 0 := by linarith
  refine ⟨hf0, fun x => ?_⟩
  have hx := h x (-x)
  have hz : x + -x = 0 := by ring
  rw [hz, hf0] at hx
  linarith

#print axioms s_additive_odd
