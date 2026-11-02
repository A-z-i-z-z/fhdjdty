import Mathlib

open Finset

theorem ml_basel_partial (n : ℕ) (hn : 0 < n) : ∑ k ∈ range n, (1 : ℝ) / ((k : ℝ) + 1) ^ 2 ≤ 2 - 1 / (n : ℝ) := by
  induction n with
  | zero => omega
  | succ m ih =>
    rcases Nat.eq_zero_or_pos m with h | h
    · subst h
      norm_num
    · rw [Finset.sum_range_succ]
      have ih' := ih h
      have hm : (0:ℝ) < (m:ℝ) := by exact_mod_cast h
      have hm0 : (m:ℝ) ≠ 0 := ne_of_gt hm
      have hm1 : (m:ℝ) + 1 ≠ 0 := by positivity
      have key : (1:ℝ) / ((m:ℝ) + 1) ^ 2 ≤ 1 / (m:ℝ) - 1 / ((m:ℝ) + 1) := by
        rw [div_sub_div _ _ hm0 hm1, div_le_div_iff₀ (by positivity) (mul_pos hm (by linarith))]
        nlinarith
      push_cast
      linarith

#print axioms ml_basel_partial
