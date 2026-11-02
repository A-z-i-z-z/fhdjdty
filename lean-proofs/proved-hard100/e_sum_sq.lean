import Mathlib

open Finset

theorem e_sum_sq (n : ℕ) : 6 * ∑ i ∈ range (n + 1), i ^ 2 = n * (n + 1) * (2 * n + 1) := by
  induction n with
  | zero => simp
  | succ k ih =>
    rw [sum_range_succ, mul_add, ih]
    ring

#print axioms e_sum_sq
