import Mathlib

open Finset

theorem s_odd_cubes (n : ℕ) : ∑ i ∈ range n, (2 * i + 1) ^ 3 = n ^ 2 * (2 * n ^ 2 - 1) := by
  induction n with
  | zero => simp
  | succ k ih =>
    rw [Finset.sum_range_succ, ih]
    cases k with
    | zero => norm_num
    | succ m =>
      have h1 : 2 * (m + 1) ^ 2 - 1 = 2 * m ^ 2 + 4 * m + 1 :=
        Nat.sub_eq_of_eq_add (by ring)
      have h2 : 2 * (m + 1 + 1) ^ 2 - 1 = 2 * m ^ 2 + 8 * m + 7 :=
        Nat.sub_eq_of_eq_add (by ring)
      rw [h1, h2]
      ring

#print axioms s_odd_cubes
