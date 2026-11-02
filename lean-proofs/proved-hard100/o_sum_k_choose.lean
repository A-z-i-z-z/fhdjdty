import Mathlib

open Finset

theorem key_choose_aux (n k : ℕ) : (k + 1) * (n + 1).choose (k + 1) = (n + 1) * n.choose k := by
  induction n generalizing k with
  | zero =>
    cases k with
    | zero => simp
    | succ j =>
      rw [Nat.choose_eq_zero_of_lt (by omega : 0 + 1 < j + 1 + 1)]
      simp
  | succ n ih =>
    cases k with
    | zero => simp
    | succ j =>
      have h1 := ih j
      have h2 := ih (j + 1)
      have hp1 := Nat.choose_succ_succ (n + 1) (j + 1)
      have hp2 := Nat.choose_succ_succ n j
      rw [hp1, hp2]
      nlinarith [h1, h2]

theorem o_sum_k_choose (n : ℕ) : ∑ k ∈ range (n + 1), k * n.choose k = n * 2 ^ (n - 1) := by
  cases n with
  | zero => simp
  | succ m =>
    have h : ∀ j ∈ range (m + 1), (j + 1) * (m + 1).choose (j + 1) = (m + 1) * m.choose j :=
      fun j _ => key_choose_aux m j
    rw [Finset.sum_range_succ', Finset.sum_congr rfl h, ← Finset.mul_sum, Nat.sum_range_choose]
    simp

#print axioms o_sum_k_choose
