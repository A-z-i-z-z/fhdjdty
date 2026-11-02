import Mathlib

open Finset

theorem e_geom_two (n : ℕ) : ∑ i ∈ range n, 2 ^ i = 2 ^ n - 1 := by
  induction n with
  | zero => simp
  | succ k ih =>
    rw [Finset.sum_range_succ, ih]
    have h : 0 < 2 ^ k := by positivity
    have h2 : 2 ^ (k + 1) = 2 * 2 ^ k := by ring
    rw [h2]
    omega

#print axioms e_geom_two
