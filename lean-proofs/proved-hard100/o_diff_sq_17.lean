import Mathlib

open Finset

theorem o_diff_sq_17 (x y : ℕ) (h : x ^ 2 = y ^ 2 + 17) : x = 9 ∧ y = 8 := by
  have hxy : y < x := by
    by_contra hc
    push_neg at hc
    have : x ^ 2 ≤ y ^ 2 := Nat.pow_le_pow_left hc 2
    linarith
  have h1 : y + 1 ≤ x := by omega
  have hsq : (y + 1) ^ 2 ≤ x ^ 2 := Nat.pow_le_pow_left h1 2
  have hy : y ≤ 8 := by
    nlinarith
  have hy2 : y ^ 2 ≤ 64 := by
    have := Nat.pow_le_pow_left hy 2
    norm_num at this
    exact this
  have hx : x ≤ 9 := by
    by_contra hc
    push_neg at hc
    have h10 : 10 ≤ x := by omega
    have : 10 ^ 2 ≤ x ^ 2 := Nat.pow_le_pow_left h10 2
    norm_num at this
    linarith
  interval_cases x <;> interval_cases y <;> revert h <;> norm_num

#print axioms o_diff_sq_17
