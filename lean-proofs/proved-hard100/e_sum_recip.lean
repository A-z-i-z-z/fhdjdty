import Mathlib

open Finset

theorem e_sum_recip (n : ℕ) : ∑ k ∈ range n, (1 : ℚ) / (((k : ℚ) + 1) * ((k : ℚ) + 2)) = (n : ℚ) / ((n : ℚ) + 1) := by
  induction n with
  | zero => simp
  | succ m ih =>
    rw [Finset.sum_range_succ, ih]
    have h1 : (m : ℚ) + 1 ≠ 0 := by positivity
    have h2 : (m : ℚ) + 2 ≠ 0 := by positivity
    have h3 : (m : ℚ) + 1 + 1 ≠ 0 := by positivity
    push_cast
    field_simp <;> ring

#print axioms e_sum_recip
