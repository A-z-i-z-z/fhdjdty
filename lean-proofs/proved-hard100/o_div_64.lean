import Mathlib

open Finset

theorem o_div_64 (n : ℕ) : 64 ∣ 9 ^ n - 8 * n - 1 := by
  have h : ∀ m : ℕ, ∃ k : ℕ, 9 ^ m = 64 * k + 8 * m + 1 := by
    intro m
    induction m with
    | zero => exact ⟨0, by norm_num⟩
    | succ m ih =>
      obtain ⟨k, hk⟩ := ih
      refine ⟨9 * k + m, ?_⟩
      rw [pow_succ, hk]
      ring
  obtain ⟨k, hk⟩ := h n
  refine ⟨k, ?_⟩
  rw [hk]
  omega

#print axioms o_div_64
