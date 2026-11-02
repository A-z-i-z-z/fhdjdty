import Mathlib

open Finset

theorem ml_sum_range_id_helper (n : ℕ) : (∑ i ∈ range (n + 1), i) * 2 = (n + 1) * n := by
  induction n with
  | zero => simp
  | succ k ih =>
    rw [Finset.sum_range_succ (fun i : ℕ => i), add_mul, ih]
    ring

theorem ml_sum_cubes (n : ℕ) : ∑ i ∈ range (n + 1), i ^ 3 = (∑ i ∈ range (n + 1), i) ^ 2 := by
  induction n with
  | zero => simp
  | succ k ih =>
    have h := ml_sum_range_id_helper k
    have key : (∑ i ∈ range (k + 1), i) * 2 * (k + 1) = (k + 1) * k * (k + 1) := by
      rw [h]
    rw [Finset.sum_range_succ (fun i : ℕ => i ^ 3), Finset.sum_range_succ (fun i : ℕ => i), ih]
    nlinarith [h, key]

#print axioms ml_sum_cubes
