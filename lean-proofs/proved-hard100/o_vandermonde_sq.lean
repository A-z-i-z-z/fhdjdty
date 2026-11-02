import Mathlib

open Finset

theorem o_vandermonde_sq (n : ℕ) : ∑ k ∈ range (n + 1), (n.choose k) ^ 2 = (2 * n).choose n := by
  rw [two_mul, Nat.add_choose_eq, Finset.Nat.sum_antidiagonal_eq_sum_range_succ_mk]
  dsimp only
  apply Finset.sum_congr rfl
  intro k hk
  have hk' : k ≤ n := Nat.lt_succ_iff.mp (Finset.mem_range.mp hk)
  simp only [Nat.choose_symm hk', sq]

#print axioms o_vandermonde_sq
