import Mathlib

open Finset

theorem s_sum_k_fact (n : ℕ) : ∑ k ∈ range n, k * Nat.factorial k = Nat.factorial n - 1 := by
  induction n with
  | zero => simp
  | succ m ih =>
    rw [sum_range_succ, ih]
    have h1 : 0 < Nat.factorial m := Nat.factorial_pos m
    have h2 : Nat.factorial (m + 1) = (m + 1) * Nat.factorial m := Nat.factorial_succ m
    have h3 : (m + 1) * Nat.factorial m = m * Nat.factorial m + Nat.factorial m := by ring
    rw [h2, h3]
    generalize Nat.factorial m = a at h1 ⊢
    generalize m * a = b
    omega

#print axioms s_sum_k_fact
