import Mathlib

open Finset

theorem ml_prod_telescope (n : ℕ) : ∏ k ∈ range n, ((1 : ℚ) + 1 / ((k : ℚ) + 1)) = (n : ℚ) + 1 := by
  induction n with
  | zero => simp
  | succ m ih =>
    rw [Finset.prod_range_succ, ih]
    have h : (m : ℚ) + 1 ≠ 0 := by positivity
    push_cast
    field_simp
    try ring

#print axioms ml_prod_telescope
