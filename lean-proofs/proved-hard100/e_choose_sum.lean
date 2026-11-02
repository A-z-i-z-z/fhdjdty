import Mathlib

open Finset

theorem e_choose_sum (n : ℕ) : ∑ k ∈ range (n + 1), n.choose k = 2 ^ n := by
  first
    | exact Nat.sum_range_choose n
    | simp [Nat.sum_range_choose]

#print axioms e_choose_sum
